# CivicFlow 架构

## 1. 总体结构与依赖方向

```text
civicflow-web
    |
    v
civicflow-gateway ----> civicflow-auth
    |           |           ^
    |           +-----------+ JWT/JWKS、服务身份
    v
resource <---Feign-- appointment --events/Feign--> queue
  |                    |                         |
MySQL(resource)    MySQL(appointment)        MySQL(queue)
                       |                         |
                    Redis + RabbitMQ (协作基础设施)

所有 Java 模块 --> civicflow-common（仅轻量稳定契约）
业务服务之间无 Maven 依赖、无跨库访问。
```

外部请求只经 gateway；`/internal/**` 不配置公网路由。服务发现与配置使用 Nacos，隔离 dev/test/prod namespace/group。Sentinel 在网关做入口限流，在 Feign 调用侧做超时/熔断；降级不得伪造业务成功。

## 2. 模块职责

| 模块 | 拥有的数据/职责 | 不负责 |
| --- | --- | --- |
| common | 响应壳、通用异常接口、requestId、安全上下文只读模型、Long 序列化约定 | 业务状态/表/重依赖、远程调用 |
| gateway | 路由、JWT 粗粒度鉴权、清洗可信头、限流、CORS、追踪 | 业务归属、业务事务、持久化 |
| auth | 用户、角色、refresh token、access/service JWT、JWKS/密钥版本 | 网点/预约/窗口授权事实以外的业务数据 |
| resource | 网点、事项、窗口、窗口事项绑定、slot 配置和版本 | Redis 扣库存、预约订单 |
| appointment | Redis 运行库存、reservation、订单、有效预约占位、确认/取消/超时、二维码签发、库存对账 | resource 配置写、排队领取 |
| queue | 签到结果、排队票、窗口会话、叫号与办理状态 | 预约库存、账号角色配置 |
| web | 三角色 UI、API 适配、客户端体验状态 | 最终权限和业务状态裁决 |

## 3. 服务内部目录约定

```text
com.civicflow.<service>/
├─ controller/
├─ service/
│  └─ impl/
├─ mapper/
├─ entity/
├─ dto/request/
├─ dto/response/       # API VO
├─ convert/
├─ enums/
├─ event/              # 消息 DTO/生产/消费适配，业务仍调用 Service
├─ client/             # Feign 接口与 DTO
├─ config/
├─ task/
└─ support/            # 技术性组件；不得形成第四业务层
```

Gateway 按 WebFlux 结构精简；web 使用 `src/{api,stores,router,views,components,types,utils}`。Lua 放 appointment 的 `src/main/resources/lua`；Flyway 放各拥有数据库服务的 `db/migration`。

## 4. 同步调用

| 调用方 -> 被调用方 | 用途 | 超时/降级原则 |
| --- | --- | --- |
| gateway -> auth/JWKS | 获取缓存可轮换公钥（常规 JWT 本地验签） | 缓存已知 key；未知 kid 才刷新；失败返回 401/503，不放行 |
| appointment -> resource | 拉取 slot 配置快照、配置版本、对账基线 | 短超时；预热失败保留旧快照并告警，不凭空补量 |
| queue -> appointment internal | claim 签到：校验 token/预约窗口并 CAS 为 CHECKED_IN；必要的幂等状态查询 | service JWT；失败不建新票或返回可重试，不绕过校验 |
| queue -> resource internal | 校验窗口、网点、事项和员工授权快照 | 短缓存；写操作授权失败关闭，不使用过期缓存放行 |
| 各服务 -> auth internal/JWKS | 角色版本/服务身份所需信息 | access JWT 正常本地验签；高风险 tokenVersion 可查/缓存 |

Feign 请求携带 `traceparent`/`X-Request-Id`、服务 JWT；用户语境携带原 access JWT 或网关签名上下文。禁止级联调用超过两跳；查询聚合由调用方明确编排。

## 5. 核心异步链路

### 5.1 Redis 预占到预约订单

1. appointment 生成 `reservationId`，执行 `reserve_stock.lua`。
2. Lua 原子扣减并写 reservation、用户 active key 和 pending ZSET。
3. 应用发布 `appointment.reservation.requested.v1`，启用 mandatory + publisher confirm。
4. ACK 后 Lua 标记 `PUBLISHED`；NACK/returned/超时在有限重试耗尽后执行 `compensate_stock.lua`。
5. 进程崩溃可能停在 2~4 之间；恢复任务读取 pending ZSET：未到截止重发，相同 `eventId=reservationId`；超期则补偿。消费者幂等使“ACK 未记录后的重发”安全。
6. 消费者本地事务插入消息去重、有效占位、`appointment_order(PENDING_CONFIRM)`、操作日志和 timeout outbox。`reservationId` 唯一。
7. 提交后确认 Redis reservation 为 `PERSISTED` 并移出 pending；失败由重试/对账恢复，数据库仍是预约事实。

### 5.2 预约超时

建单事务写 `appointment.confirm-timeout.requested.v1` outbox。发布器确认后标记已发；消息进入固定 5 分钟 TTL 队列，无消费者，过期通过 DLX 到 timeout check queue。消费者以 `reservationId` 去重但不能因早先失败永久跳过；实际副作用以订单 CAS 为幂等边界：

```text
UPDATE appointment_order
SET status='EXPIRED', ...
WHERE reservation_id=? AND status='PENDING_CONFIRM' AND confirm_deadline <= DB_NOW
```

影响 1 行才删除 active guard、记录 `stock_release_record`；事务后执行幂等 Redis 释放。影响 0 行读取结果：已确认/取消等视为成功空操作；截止未到则按剩余时间重新调度。

### 5.3 签到与办理状态同步

queue 先携带 token、userId、outletId 和 claimId 调用 appointment check-in claim。appointment 验证当前 nonce 后，以 `CONFIRMED -> CHECKED_IN` CAS 与取消竞争；同一 nonce/claim 的已成功结果幂等返回，并在同一事务写 `appointment.check-in.claimed.v1` outbox。随后 queue 在本地事务创建唯一 ticket/outbox，提交后 `queue.ticket.checked-in.v1` 回告 ticketId。claim 成功但同步建票失败时，客户端同键重试会复用 claim，且 claimed 事件消费者可补建同一唯一票；对账处理长期未关联项，绝不回滚预约状态。

叫号开始办理、完成、过号分别发送版本化事件。appointment 以 `eventId` 去重并校验合法原状态。消息暂时失败不回滚 queue 事实，进入重试/DLQ，并由跨服务对账补齐。

## 6. 一致性不变量

- `resource_slot.total_quota` 是配置总量；appointment 的 reservation/订单/释放记录是消费事实；Redis `remaining` 是运行时派生值。
- 同一 `(user_id,item_id,service_date)` 最多一条 `active_booking_guard`；Redis active key 只是前置拦截。
- 同一 `reservation_id` 最多一张 appointment_order；同一 `appointment_id` 最多一张 queue_ticket。
- 任一订单状态转换由带原状态的单 SQL 决定；副作用只由 CAS 胜者触发。
- 对可释放预约，库存最多返还一次：数据库 `stock_release_record(reservation_id)` 唯一 + Redis compensation marker/Lua 双保险。
- 同一 WAITING ticket 只能被一个窗口领取：queue 数据库锁定并在短事务中更新。

对账基础式（排除可配置的一致性宽限期内未决项）：

```text
expectedRemaining = configuredTotal
                  - persistedConsumptionsWithoutRelease
                  - validRedisReservationsNotYetPersisted
actualRemaining   = Redis stock.remaining
```

已取消/过期且存在唯一 release 事实不再计为消费；已确认、已签到、办理中、已完成、未到号均是历史消费，不在 slot 过期后强行恢复可售库存。

## 7. 故障与补偿

| 故障 | 立即行为 | 最终恢复 |
| --- | --- | --- |
| Redis 预占失败 | 返回明确库存/重复/未放号/不可用错误 | 不发 MQ；基础设施失败允许客户端同幂等键重试 |
| Redis 成功、MQ 发布失败 | 有限重试；结果不确定时查询 confirm 回调状态 | 幂等补偿；pending 扫描兜住进程崩溃；补偿失败入记录/告警/对账 |
| MQ 重复/ACK 丢失 | 消费者按 reservationId/eventId 去重 | 唯一约束处理并发重复 |
| DB 建单失败 | MQ nack/requeue 有限次数 | 可重试错误重试；永久错误 DLQ；若始终未建单则恢复任务/人工重放或补偿 |
| timeout 消息丢失/延迟 | outbox + confirm；数据库 deadline 是裁决事实 | 补偿扫描分页查过期 PENDING_CONFIRM，执行同一 CAS |
| DB 已终态、Redis 释放失败 | API 返回业务终态并标注补偿处理中 | `stock_release_record` 重试，CAS Lua/对账修复 |
| appointment 已 claim、queue 未建票 | 同 token/幂等键重试；预约保持 CHECKED_IN，阻止取消 | queue 补建唯一票；签到对账告警/恢复 |
| resource 不可用 | 禁止凭旧数据新增/调整关键事实 | 预热重试；已有 Redis slot 可按配置化短容忍继续，过版本边界则关闭抢号 |
| Redis 数据丢失 | 暂停受影响 slot 抢号 | 按 resource + appointment + pending 事实安全重建，禁止无条件 SET |
| 对账时数据变化 | 版本不匹配则放弃修复 | 重新读取快照并计算 |

## 8. 可用性与观测

- 所有入口/Feign/MQ 传播 requestId、traceId；MQ header 保留 causationId/correlationId。
- 关键指标：Lua 返回码、预占成功、MQ confirm 失败、pending 年龄、消费重试/DLQ、CAS 冲突、超时延迟、补偿积压、对账差异、WAITING 票数。
- 管理操作、状态转换、库存修复必须写不可变审计记录；日志是观测数据，不是业务事实。
- 定时任务需要分布式互斥或数据库抢占，所有扫描分页限速且可重入。
