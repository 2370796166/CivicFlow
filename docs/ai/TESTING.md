# CivicFlow 测试策略与矩阵

## 1. 测试原则

- 测试金字塔：Service/状态机单元测试为主，MySQL/Redis/RabbitMQ 集成测试验证真实语义，少量经 gateway 的 E2E 验证角色闭环。
- 使用 JUnit 5、AssertJ、Mockito（纯单元）、Spring Boot Test、Testcontainers（MySQL 8/Redis/RabbitMQ）、Awaitility；Gateway 用 WebTestClient；前端用 Vitest + Vue Test Utils，E2E 用 Playwright；并发压测用 k6 或 JMeter 二选一。
- 不用 H2 替代 MySQL 验证唯一索引、`SKIP LOCKED`、Flyway 或时间 CAS；Lua 必须在真实 Redis 执行；MQ TTL/DLX/confirm 必须在真实 RabbitMQ 容器执行。
- 每个故障测试记录环境、种子、并发数、命令、实际结果；性能结果报告 P50/P95/P99、吞吐、成功/失败分类，不能宣传为生产容量。

## 2. 分层测试

| 层级 | 范围 | 合格门槛 |
| --- | --- | --- |
| 单元 | DTO 校验、转换、Service 分支、状态机、payload hash、脱敏、JWT/JWS 校验 | 每个合法转换和非法转换至少一例；错误码稳定 |
| Mapper/Flyway | 条件更新、唯一约束、索引查询、SKIP LOCKED | 空库迁移成功；并发影响行数符合预期 |
| Redis 集成 | reserve/mark/compensate/adjust Lua、TTL、幂等、共槽 | 不超卖、不负库存、不误删他人 active、不重复释放 |
| RabbitMQ 集成 | confirm/return、重复、retry/DLQ、TTL+DLX、outbox | 至少一次下业务结果恰好一次；失败可观察/可重放 |
| 安全集成 | JWT/RBAC/归属/伪造头/QR | 未授权无数据泄露；服务内部接口不可由用户 token 调用 |
| E2E | admin 配置 -> 用户抢号确认签到 -> staff 办理 -> 对账 | 状态/库存/日志首尾一致；超时支路也通过 |

## 3. 状态机矩阵

### 预约

| 起点 | confirm | timeout | cancel | check-in | serving | complete | no-show |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PENDING_CONFIRM | CONFIRMED | EXPIRED | CANCELLED | 拒绝 | 拒绝 | 拒绝 | 拒绝 |
| CONFIRMED | 幂等/冲突按 key | 空操作 | 截止前 CANCELLED | CHECKED_IN | 拒绝 | 拒绝 | 拒绝 |
| CHECKED_IN | 拒绝 | 空操作 | 拒绝 | 幂等返回票 | SERVING | 拒绝 | NO_SHOW |
| SERVING | 拒绝 | 空操作 | 拒绝 | 拒绝 | 幂等 | COMPLETED | 常规拒绝 |
| 终态 | 全部拒绝或相同幂等读取 | 空操作 ACK | 相同幂等读取 | 拒绝 | 拒绝 | 相同幂等读取 | 相同幂等读取 |

必须加真实竞态：confirm vs timeout、cancel vs timeout、confirm vs cancel、check-in vs cancel。断言仅一个 CAS 影响 1 行、active guard 与 release record 符合胜者、库存最多释放一次。

### 排队

覆盖 WAITING->CALLED、CALLED 自重呼、CALLED->MISSED、CALLED->SERVING、SERVING->COMPLETED 及所有直接跳跃/终态迁出。断言未授权窗口和已结束 session 均失败，失败不改变 ticket/version。

## 4. 核心并发矩阵

| 场景 | 负载 | 必须断言 |
| --- | --- | --- |
| 单 slot 抢号 | quota=100，1000 不同用户同时请求 | OK=100；remaining=0；无负数；最终有效+未决不超过 100 |
| 单用户重复抢 | 同用户对同事项同日不同 slot 100 并发 | 仅一个 Redis active；仅一个 DB active guard/有效订单；其余 DUP_ACTIVE/幂等 |
| 同 reservation 重放 | 100 次相同 reservationId/载荷 | 只扣 1 次，只建 1 单；返回相同结果 |
| 同 key 异载荷 | 相同 reservationId 或 Idempotency-Key，不同 slot/payload | 409 conflict；不发生第二副作用 |
| 发布失败补偿 | 强制 returned/NACK/confirm timeout | 库存最多 +1；active 只比较删除；comp marker/record 唯一 |
| confirm/timeout | deadline 边界并发各 100 次 | DB 时间裁决；CONFIRMED 或 EXPIRED 唯一终态；仅 EXPIRED 释放 |
| 双窗口叫号 | 2~20 个 ACTIVE 窗口争抢同队列 | 每票最多一个 calledWindow；稳定排序；无长事务/死锁泄露 |
| 并发签到 | 同 token/appointment 100 次 | 单 queue_ticket；重复返回同 ticket；预约最终 CHECKED_IN |
| 额度调整与抢号 | adjust Lua 与 reserve 并发 | configVersion 连续；remaining 不负；不覆盖扣减 |

## 5. 消息可靠性与幂等

- `appointment.reservation.requested`：重复、乱序、消费者在事务提交前/后崩溃、ACK 丢失、active guard 被其他 reservation 占用。
- Publisher：Redis 成功后进程在 publish 前、confirm ACK 前、ACK 后 mark 前崩溃；pending recovery 能重发/补偿且不重复订单。
- Timeout：outbox 重复、delay queue 重启、晚到/早到、confirm 同时发生、定时补偿扫描与 MQ 同时执行。
- Check-in/Queue events：claim 与取消竞态、claim 提交后同步建票失败、claimed 事件与同步路径并发建票、checked-in 回告重复、serving 在 ticket 关联前到达、completed 重复、消费者停机后恢复、DLQ 原 eventId 重放。
- 对每个消费者断言 `message_consume_record`、业务唯一约束、状态 CAS 三层结果一致；不能只断言消费方法被调用。

## 6. 故障与恢复

| 故障注入 | 预期 |
| --- | --- |
| Redis 短时不可用 | 抢号不降级到 DB；已落库取消生成 release record；恢复后补偿 |
| RabbitMQ 不可用 | 预占有限重试后补偿；DB outbox 保留重试；无假成功 |
| MySQL 在消费中断开 | MQ 重试；无半事务订单/guard/outbox |
| resource 超时 | 新预热/变更失败关闭；既有 slot 超过容忍版本后暂停 |
| Redis stock key 丢失 | slot 暂停；对账分类 EVIDENCE_MISSING；不盲目 INCR/SET |
| 补偿 Lua 在 DB 终态后失败 | API 状态已终止；record PENDING；恢复后仅释放一次 |
| appointment claim 后 queue 建票失败 | 预约保持 CHECKED_IN；同键重试/claimed outbox 只补建一票；最终关联 ticketId |

## 7. 权限与安全矩阵

- JWT：缺失、过期、nbf、错误 iss/aud、篡改、未知 kid、alg=none、旧 tokenVersion、USER 冒充 ADMIN。
- Gateway：客户端伪造 `X-User-Id/Roles/Service` 被清除；白名单最小化；登录和抢号 429；CORS 非白名单拒绝。
- 水平越权：用户 A 查询/确认/取消用户 B 预约；查询 B ticket；QR userId 不匹配，均不泄露资源存在性。
- STAFF：未授权 outlet/window、session 属他人、ticket 属其他窗口、结束后操作。
- Internal：用户 access JWT 调内部 API、service JWT 错 audience/scope/过期、内部端点意外经 gateway。
- QR：改 appointment/outlet/user/exp/purpose/签名、旧 nonce、过期、跨网点、签到窗口外、截图重放。
- 输入：SQL 注入/XSS、非法枚举、超长 reason、负 quota、超大 page、批量上限、同幂等键异载荷。
- 日志/产物扫描：JWT、refresh token、二维码、私钥、手机号/证件号、`.env` 不得出现。

## 8. E2E 场景

1. ADMIN 登录，创建并启用网点、事项、窗口、绑定、未来 slot。
2. 预热后校验 configVersion/total/remaining。
3. USER 抢号得到 CREATING，轮询到 PENDING_CONFIRM，在 5 分钟内确认。
4. 获取短时二维码并签到，重复签到得到同一 ticket。
5. STAFF 开 session、call-next、recall、start、complete；预约最终 COMPLETED。
6. 对账 expected=actual，操作日志/消息记录完整。
7. 新建第二个 PENDING_CONFIRM 不确认；TTL/DLX 或补偿扫描使其 EXPIRED，库存回补一次，用户可重新预约。
8. 另测 CALLED->MISSED 对应 appointment NO_SHOW。

## 9. 阶段验证命令基线

后续存在工程后按实际脚本执行并记录：

```text
mvn -U -DskipTests dependency:tree
mvn test
mvn verify
docker compose config
npm run lint
npm run typecheck
npm run test
npm run build
```

依赖骨架阶段还必须启动最小上下文，验证 Gateway WebFlux、OpenFeign、Nacos discovery/config、Sentinel 与 Boot/Cloud/Alibaba BOM 组合；仅 dependency:tree 无冲突不等于运行兼容。
