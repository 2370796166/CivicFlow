# CivicFlow Redis 契约

## 1. 定位与约定

Redis 是运行时库存、前置防重和短期流程协调，不是号源配置或预约事实源。当前实现的 Key 前缀为 `cf:v1:{environment}:`（如 `cf:v1:dev:`），环境维度只接受受限的小写标识；生产仍应配合独立 Redis 实例/ACL 隔离。所有写 key 必须设置可解释 TTL 或明确为 slot 生命周期 key。

同一次 Lua 的 key 使用 Redis Cluster hash tag `{itemId:serviceDate}` 共槽；例如 `{1903:20260912}`。这使某事项某服务日成为原子分片，既能访问 slot 库存又能访问跨 slot 用户占位。热点事项/日期的单分片容量必须压测，迁移分片模型需 ADR。

## 2. Key 清单

| Key 模板 | 类型 | 值/字段 | TTL |
| --- | --- | --- | --- |
| `cf:v1:{env}:stock:{itemId:yyyyMMdd}:slot:{slotId}` | HASH | total, remaining, status, releaseAtEpochMs, closeAtEpochMs, configVersion, mutationSeq, updatedAtEpochMs | `closeAt + 2d` |
| `cf:v1:{env}:active:{itemId:yyyyMMdd}:user:{userId}` | STRING | reservationId | 服务日结束 + 2d；终态由比较删除 |
| `cf:v1:{env}:reservation:{itemId:yyyyMMdd}:{reservationId}` | HASH | userId,slotId,state,createdAt,publishDeadline,configVersion | RESERVED 初始 10m；PERSISTED 延至 slot close+2d |
| `cf:v1:{env}:reservation-pending:{itemId:yyyyMMdd}:slot:{slotId}` | ZSET | member=reservationId, score=nextRecoveryEpochMs | 集合到服务日结束+2d；成员完成即删 |
| `cf:v1:{env}:compensated:{itemId:yyyyMMdd}:{reservationId}` | STRING | reason/releasedAt | slot close + 2d，且不短于 48h |
| `cf:v1:{env}:idem:{operation}:{actorId}:{keyHash}` | HASH/STRING | payloadHash,resultRef,status | 24h；抢号至少覆盖 reservation 恢复窗口 |
| `cf:v1:{env}:qr-used:{appointmentId}:{nonceHash}` | STRING | ticketId 或 PROCESSING | token exp + 10m；DB ticket 唯一约束最终兜底 |
| `cf:v1:{env}:rate:{dimension}:{bucket}` | STRING/HASH | 计数/令牌桶 | bucket 时长 + 抖动 |
| `cf:v1:{env}:lock:{task}:{shard}` | STRING | ownerToken | 短租约，可续期；仅定时任务协调 |

不能通过生产 `KEYS`/全库 SCAN 找 reservation。当前主恢复入口是 appointment 数据库中带分页索引和短租约的 `appointment_reservation_request`；pending ZSET 是按 slot 分片的 Redis 运行时证据和后续对账入口。slot 索引/候选来自 resource 分页数据。

## 3. `reserve_stock.lua`

### 输入

`KEYS`（必须同 hash slot）：

1. stock key
2. active user key
3. reservation key
4. pending ZSET key

`ARGV`：`reservationId,userId,slotId,itemId,serviceDate,nowEpochMs,publishDeadlineEpochMs,expectedConfigVersion,reservationExpiresAtEpochMs,activeExpiresAtEpochMs`。

### 原子步骤

1. 若 reservation key 已存在且 userId/slotId 一致，返回原成功结果；若 reservationId 异载荷，返回冲突。
2. 校验 stock key 存在、slotId/configVersion 匹配、status=OPEN（或 SCHEDULED 且服务端时间已过 releaseAt）、`now < closeAt`。
3. active user key 存在时：值等于 reservationId 返回幂等成功；否则返回 DUP_ACTIVE。
4. 校验 `remaining > 0`；原子 `HINCRBY remaining -1`。
5. `SET active reservationId NX PX activeTtlMs`；理论上因脚本串行不会竞态，如失败则回滚本脚本内扣减并返回冲突。
6. 写 reservation HASH state=RESERVED、业务标识/时间/版本并设置 TTL；`ZADD pending pendingScore reservationId` 并设置集合 TTL。
7. 返回结构化数组 `{code,reservationId,remaining,configVersion}`。

返回码：`OK`、`IDEMPOTENT_OK`、`SLOT_NOT_FOUND`、`NOT_RELEASED`、`SLOT_NOT_OPEN`、`SLOT_CLOSED`、`CONFIG_VERSION_MISMATCH`、`DUP_ACTIVE`、`OUT_OF_STOCK`、`RESERVATION_CONFLICT`。业务层映射 API 稳定错误码，不把 Lua 文本直接暴露。

原子性边界仅覆盖上述 Redis keys；不覆盖 RabbitMQ/MySQL。Lua 禁止调用外部系统、禁止大循环，执行目标 < 5ms，脚本 SHA/版本纳入指标。

## 4. 发布/持久化标记 Lua

### `mark_published.lua`

输入 reservation key、pending ZSET、stock key，ARGV 为 reservationId/eventId/nowEpochMs/nextRecoveryEpochMs。仅当 reservation 匹配且 state=RESERVED 时改为 PUBLISHED；已 PUBLISHED/PERSISTED 幂等成功。publisher confirm 未明确成功前不得调用。

### `mark_persisted.lua`

输入 reservation、pending、stock，ARGV 为 reservationId/appointmentId/nowEpochMs/retentionUntilEpochMs。匹配后 state=PERSISTED、写 appointmentId、延长 TTL、ZREM pending。已持久化且 appointmentId 相同幂等成功；不同则 CONFLICT。

## 5. `compensate_stock.lua`

`KEYS`：stock、active user、reservation、pending ZSET、compensated marker，均使用同 `{itemId:date}` tag。`ARGV`：reservationId,userId,slotId,reason,now,markerTtl,expectedConfigVersion。

原子规则：

1. compensated marker 存在，返回 `ALREADY_RELEASED`，绝不再次加库存。
2. reservation 必须存在、业务字段匹配、state 属于 RESERVED/PUBLISHED/PERSISTED/RELEASE_PENDING；缺失返回 `NEED_RECONCILE`，禁止猜测加库存。
3. stock key/slot/version必须匹配；不存在或版本不匹配返回 `NEED_RECONCILE`。
4. `remaining < total` 才 `HINCRBY +1`；若已等于 total，返回 `INVARIANT_BROKEN` 交对账，不能加成超量。
5. 仅当 active key 值等于 reservationId 时删除；不同值绝不删除别人的占位。
6. 删除 reservation、ZREM pending，写 compensated marker 并 TTL，返回 `{RELEASED,newRemaining}`。

数据库订单取消/过期先 CAS 并插入唯一 `stock_release_record`，事务提交后调用本脚本。发布失败尚无订单时，以 Redis reservation 证据调用。数据库 record 成功而 Lua NEED_RECONCILE 时由对账根据完整事实修复。

## 6. 预热与额度调整

- 首次预热使用 `init_stock.lua`：仅当 key 不存在时写 total=remaining 和配置快照；存在时不覆盖。
- 同一 configVersion 重放幂等；新版本只能由 `adjust_stock.lua` 按 `delta=newTotal-oldTotal` 调整，要求 `incomingVersion=current+1`、`remaining+delta>=0`。失败暂停 slot 变更并对账。
- 状态 SUSPENDED/CLOSED 可带版本更新，但不得删除消费/预占证据。重建必须计算 resource 配置、DB 消费和有效 pending 的一致快照，并使用预期旧版本 CAS Lua，禁止直接 `SET remaining`。
- Redis key 到期不改变 MySQL 事实；服务日后的历史查询只读 DB。

### 办理终态占位清理

`release_active.lua` 输入单个 active user key，`ARGV[1]=reservationId`；值匹配才删除并返回 `1`，不存在或属于其他 reservation 返回 `0`。COMPLETED/NO_SHOW 仍计入已消费库存，所以此脚本不修改 stock、reservation 或补偿标记。appointment 的办理终态事务提交后执行；Redis 失败向 queue 返回失败，由持久化 `queue_state_sync` 重试。相同目标状态重放也执行比较删除，不重复状态变更或审计，旧预约重试不能清掉新预约的占位。

## 7. 缓存一致性与降级

- resource 查询缓存采用 cache-aside：写 DB 提交后删缓存；短 TTL + 随机抖动；缓存空值短 TTL 防穿透。关键抢号校验不依赖普通查询缓存。
- JWT 公钥按 kid 缓存；未知 kid 主动刷新一次。权限归属缓存短 TTL，写操作在不确定时失败关闭。
- Redis 不可用时禁止降级为 MySQL“先查库存再扣”；抢号返回 503。已持久化预约的查询/确认可按 DB 能力继续，但涉及库存释放时写可靠 release record，异步补偿。
- Redis 客户端超时不是 Lua 未执行的证据。2026-09-28 pause/unpause 实测中，已发送命令在恢复后可能继续扣减；请求必须保留 CREATED 恢复记录，以同 reservationId 重放收敛。503 不能作为无预占的最终判断，客户端应以 reservation 查询/恢复结果为准，不能盲目改 ID 重试或直接加库存。
- 限流 Redis 故障的策略按接口：登录/抢号失败关闭或本地保守限流；公开只读可本地限流。必须打指标。

## 8. 对账分类

- `TRANSIENT_PENDING`：reservation 未超过一致性宽限期，仅观察。
- `UNRESOLVED_RESERVATION`：宽限期过后仍有无订单预占或 pending ZSET 成员，告警并交恢复任务处理。
- `COMPENSATION_PENDING`：唯一释放记录未成功，库存仍视为已扣；交补偿任务重试，不在对账中直接加库存。
- `DB_ORDER_REDIS_ACTIVE_MISSING`：有效订单的 active key 缺失/被其他 reservation 占用，告警，不自动补键。
- `REDIS_STOCK_MISMATCH`：expected != actual；首版只自动下调 `actual > expected` 的错误增量。要求 DB 交叉式成立、有效订单的 active guard 和 Redis active 键核查完整、无未决预占/补偿、超过 2 分钟宽限期、版本与总量一致；修复前再读一次 DB 聚合事实，变化则重算。`actual < expected` 涉及是否真实释放的歧义，只告警。
- `CONFIG_VERSION_GAP`、`EVIDENCE_MISSING`：不自动修复，记录告警并交运维处理；缺失 stock key 的新预占会被现有 reserve Lua 拒绝。

`init_stock.lua` 初始化 `mutationSeq=0`；成功的 reserve、compensate、adjust 及对账修复均递增该序号。`reconciliation_snapshot.lua` 原子读取 total/remaining/configVersion/mutationSeq/pending ZSET 数量；`reconciliation_repair.lua` 只在这些快照条件及 pending 为空时写 `remaining=expected` 并递增序号，返回 `0 APPLIED`、`1 STALE`、`2 MISSING_OR_UNSAFE`。STALE 时重新计算报告，不立即重试写入。stock key 丢失或缺少变更序号绝不重建；运行中预占导致的同值 ABA 由序号发现。
