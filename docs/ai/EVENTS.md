# CivicFlow RabbitMQ 事件契约

## 1. 拓扑与命名

所有资源由应用以声明式、durable 配置创建；生产禁止依赖控制台手工创建。消息持久化，发布启用 `publisher-confirm-type=correlated`、publisher returns、`mandatory=true`，每次尝试使用包含 eventId 的 `CorrelationData`。确认超时、连接异常或 future 异常都不等于 NACK：这些结果统一记为 `PUBLISH_UNKNOWN`，按同一 eventId 恢复重发，绝不据此释放库存。只有所有尝试均得到明确 NACK/return 时，才进入幂等补偿。

已落地 reservation create、确认超时、签到 claimed outbox/queue；queue 办理状态事件仍是后续契约。

| 类型 | 名称 | 用途 |
| --- | --- | --- |
| topic exchange | `cf.appointment.x` | reservation/appointment 事件 |
| topic exchange | `cf.queue.x` | 签到与办理事件 |
| topic exchange | `cf.timeout.x` | 超时投递/检查 |
| topic exchange | `cf.dlx` | 最终死信 |
| queue | `cf.appointment.reservation.create.q` | 异步建单 |
| delay queue | `cf.appointment.confirm.delay.5m.q` | `x-message-ttl=300000`，无消费者，DLX 到 `cf.timeout.x` |
| queue | `cf.appointment.confirm.timeout.q` | 超时 CAS 检查 |
| queue | `cf.appointment.queue-state.q` | queue -> appointment 状态同步 |
| queue | `cf.queue.checkin-claim.q` | appointment claim -> queue 补建排队票 |
| DLQ | `<source-queue>.dlq` | 永久失败隔离，绑定 `cf.dlx` |

绑定：

- `cf.appointment.reservation.create.q` <- `cf.appointment.x` / `appointment.reservation.requested.v1`
- `cf.appointment.confirm.delay.5m.q` <- `cf.timeout.x` / `appointment.confirm.timeout.schedule.v1`；其 DLX routing key 为 `appointment.confirm.timeout.check.v1`
- `cf.appointment.confirm.timeout.q` <- `cf.timeout.x` / `appointment.confirm.timeout.check.v1`
- `cf.appointment.queue-state.q` <- `cf.queue.x` / `queue.ticket.*.v1`
- `cf.queue.checkin-claim.q` <- `cf.appointment.x` / `appointment.check-in.claimed.v1`

## 2. 通用消息信封

```json
{
  "schemaVersion": 1,
  "eventId": "uuid",
  "eventType": "appointment.reservation.requested",
  "eventVersion": 1,
  "occurredAt": "2026-09-10T06:30:00.123Z",
  "producer": "civicflow-appointment",
  "traceId": "trace-id",
  "correlationId": "reservation-or-appointment-id",
  "causationId": "request-or-parent-event-id",
  "payload": {}
}
```

- `schemaVersion` 描述信封 schema，`eventVersion` 描述业务事件版本；64 位 ID 在 JSON 中为字符串；未知字段必须忽略；必填字段缺失/版本不支持进入 DLQ，不无限重试。
- `eventType` 不含版本，routing key 含 `.v1`；破坏性变更新 routing key + eventVersion，并提供双发/双读迁移窗口。
- `payloadHash=SHA-256(canonical envelope business fields)`；同一幂等键却不同 hash 是数据冲突，告警并 DLQ。
- trace header 同时写 `traceparent`；不得在 header/payload 放 JWT、手机号、二维码 token。

## 3. 事件目录

### `appointment.reservation.requested.v1`

- 生产：appointment 抢号入口（Redis 成功后直接发布，publisher confirm）。
- 消费：`cf.appointment.reservation.create.q` / appointment 建单消费者。
- `eventId = reservationId`，消费幂等键 `reservationId`。

```json
{
  "reservationId": "uuid",
  "userId": "190000000000000001",
  "slotId": "190000000000000100",
  "outletId": "190000000000000200",
  "itemId": "190000000000000300",
  "outletName": "东城政务中心",
  "itemName": "户籍服务",
  "serviceDate": "2026-09-12",
  "slotStartTime": "09:00:00",
  "slotEndTime": "10:00:00",
  "totalQuota": 100,
  "releaseAt": "2026-09-10T00:00:00.000Z",
  "closeAt": "2026-09-12T02:00:00.000Z",
  "slotStatus": "OPEN",
  "slotConfigVersion": 7,
  "reservedAt": "2026-09-10T06:30:00.123Z",
  "reservationExpiresAt": "2026-09-10T06:40:00.123Z"
}
```

生产者先把受保护 resource API 返回的完整快照写入 `appointment_reservation_request`，再由该可信快照生成并持久化事件信封。消费者必须逐字段校验消息与该本地快照一致，不能直接信任消息中的名称、额度或 ID。建单事务插入 message record、active guard、`PENDING_CONFIRM` 订单、操作日志和确认超时 outbox，并把 request 投影改为 `PERSISTED`。若 active guard 被另一个 reservation 占用，将本 reservation 记为拒绝并以 `DUPLICATE_GUARD_REJECTED` 触发库存补偿。

### `appointment.confirm.timeout.requested.v1`（outbox 逻辑事件）

outbox payload：`{reservationId,appointmentId,confirmDeadline}`。发布到 routing key `appointment.confirm.timeout.schedule.v1`，进入 5 分钟 delay queue。`eventId` 为独立 UUID，幂等键 `timeout:{reservationId}`。

发布器按剩余时间设置消息 TTL，且 delay queue 固定上限 300000ms；若 outbox 发布时已到截止点，直接路由 check queue。消费者校验本地订单的 reservationId、appointmentId、deadline 后，仅用 `status=PENDING_CONFIRM AND confirm_deadline<=DB_NOW` CAS 裁决；提前送达则按剩余时间重新投递，重复送达直接 ACK。数据库扫描同一 Service CAS 兜底，库存释放记录的 reservationId 唯一，Redis 失败由补偿扫描重试。

### `appointment.check-in.claimed.v1`

appointment 在 check-in claim 的 CAS 事务内写 outbox。payload 除通用信封外含 `{idempotencyKey,claimId,appointmentId,userId,outletId,itemId,serviceDate,checkedInAt}`，业务幂等键 `appointmentId`，不包含二维码 token/nonce。publisher confirm 后标记已发；不确定结果复用 eventId 重发。queue 同步请求路径可直接建票；该事件负责补偿“claim 已提交但同步建票失败”，`queue_ticket.appointment_id UNIQUE` 使两条路径并发安全。queue 建票后通过受保护 Feign 接口关联 ticketId；关联失败由 `checkin_reconciliation_record` 定时重试。

### queue 状态事件

| Routing key | payload 关键字段 | appointment 目标转换 | 幂等键 |
| --- | --- | --- | --- |
| `queue.ticket.checked-in.v1` | eventId,ticketId,appointmentId,claimId,userId,outletId,checkedInAt | 幂等关联 ticketId；状态通常已 CHECKED_IN | eventId；业务唯一 appointmentId |
| `queue.ticket.serving.v1` | ticketId,appointmentId,windowId,sessionId,servingAt | CHECKED_IN -> SERVING | eventId |
| `queue.ticket.completed.v1` | ticketId,appointmentId,windowId,completedAt,resultCode? | SERVING -> COMPLETED | eventId |
| `queue.ticket.no-show.v1` | ticketId,appointmentId,windowId,missedAt | CHECKED_IN -> NO_SHOW | eventId |

上述 queue 状态事件仍为后续事件化契约；阶段 12 的办理状态改用受保护 OpenFeign `POST /internal/v1/appointments/{id}/queue-state` 同步。queue 状态事务写 `queue_state_sync`，定时按同票顺序发送 `ticketId,status,syncId`，失败记录重试；appointment 校验关联票并以原状态 CAS 更新，同票同目标重复请求直接成功。该路径不发布 `queue.ticket.serving/completed/no-show.v1`，不得同时启用两种生产者造成双写。签到票关联继续使用受保护 Feign + `checkin_reconciliation_record` 重试。

### resource slot 变更

resource 已在号源配置事务内写入 `resource.slot.changed.v1` transactional outbox，payload 为 `{slotId,oldTotalQuota,newTotalQuota,oldStatus,newStatus,configVersion,changedAt}`，完整 envelope 仍包含 eventId/eventType/eventVersion/occurredAt/traceId/producer。outbox routing key 为 `resource.slot.changed.v1`，目标 exchange 为 `cf.resource.x`；RabbitMQ 发布器与消费者在后续预约/库存阶段接入，当前不得绕过 outbox 直接改 Redis。appointment 只接受 `configVersion=current+1` 的增量；首次版本或版本跳跃转内部快照拉取与对账，不直接 SET 库存。

## 4. 重试、死信与确认

| 类别 | 处理 |
| --- | --- |
| 瞬时错误（DB 连接、锁超时、补偿暂败） | reservation create consumer 由容器有限重试，默认最多 3 次；异常继续抛出，不提前 ACK |
| 业务幂等/目标已达 | ACK，返回/记录原结果 |
| 业务乱序 | 短期重试；仍缺前置状态则 DLQ + 对账 |
| 非法 schema/版本/同键异载荷 | 不重试，直接 DLQ 并告警 |
| DB 唯一冲突 | 判别同 reservation/event 为幂等；不同业务占位为确定拒绝并补偿 |
| 消费代码未知异常 | 有限重试后 DLQ；不得无限 requeue 热循环 |

`cf.appointment.reservation.create.q` 使用手动 ACK：数据库事务和 Redis `mark_persisted.lua` 均成功后才 ACK；永久业务/协议异常不重试，瞬时异常有限重试耗尽后由自定义 recoverer 抛出 `AmqpRejectAndDontRequeueException(rejectManual=true)`，明确拒绝并进入 `cf.appointment.reservation.create.q.dlq`。2026-09-28 真实 broker 测试发现原默认 recoverer 在 MANUAL 模式下留下未 ACK 消息，且会输出完整 body/header；现仅日志记录异常类名，拒绝异常不携带可能包含敏感值的 cause。当前采用容器内有限重试，没有声称已经落地 5s/30s/2m retry queue。后续若增加延迟重试队列，不得改变手动 ACK、有限次数和最终 DLQ 语义。DLQ 重放必须是管理员受审计操作，保留原 eventId，不允许通过改 ID 绕过幂等。

## 5. Redis 预占发布补偿协议

1. HTTP 入口先以 `(userId,idempotencyKeyHash)` 唯一记录 `appointment_reservation_request(CREATED)`，同键必须绑定相同 payload hash；应用生成稳定 reservationId。
2. Lua 把 reservation 写入按 slot 分片的 pending ZSET，状态 RESERVED；应用把原始事件 JSON 落在 request 行后发布。
   同键并发以 `WHERE status='CREATED'` CAS 首次保存 reservedAt、expiresAt 和原始 JSON；后到线程重读已保存事件，不能给同一个 eventId 重新生成时间字段。发布结果只条件更新 RESERVED/PUBLISH_UNKNOWN 的发布列；迟到 ACK/UNKNOWN 不覆盖消费者已提交的 PERSISTED，也不改写原始事件快照。以上是既有语义的修复，无新增 schema、事件版本或 API 字段。
3. 生产者用 `eventId=reservationId` 发布；ACK 后 `mark_published.lua` 标记 PUBLISHED。只有明确 returned/NACK 且从未出现不确定结果，才执行 `compensate_stock.lua`。
4. HTTP 线程/进程在 CREATED、RESERVED、ACK 未落库、`PUBLISH_UNKNOWN` 或 `COMPENSATION_PENDING` 阶段退出时，数据库恢复扫描按 `next_recovery_at` 找回。扫描用 `recovery_owner + recovery_lease_until` 条件更新抢短租约，多实例只有一个恢复者；恢复始终复用数据库里的同一 eventId/事件 JSON。
5. ACK 与 `mark_published.lua` 之间崩溃会导致恢复任务重发；消费者依赖 `appointment_order.reservation_id`、message consume 唯一约束以及 payload hash 幂等。
6. 消费建单事务提交后调用 `mark_persisted.lua` 延长证据 TTL并移出 pending；调用失败抛出可重试异常，不 ACK，数据库唯一约束使重投安全。
7. 补偿 Lua 返回 ALREADY_RELEASED 视为成功；返回 NEED_RECONCILE/临时失败时保留 `stock_release_record` 与 `COMPENSATION_PENDING`，恢复扫描继续尝试，禁止盲目 `INCR`。

Redis Lua 与 RabbitMQ 发布不是原子事务，也没有被实现伪装成原子事务。各故障窗口与恢复路径如下：

| 故障窗口 | 可见状态 | 恢复路径 |
| --- | --- | --- |
| request 行已建、Lua 未执行 | CREATED | DB 租约扫描重新执行幂等 reserve Lua |
| Lua 成功、事件尚未发布 | RESERVED | DB 租约扫描读取已保存事件并发布；Lua 重放返回幂等成功 |
| broker 已收、confirm 丢失/连接断开 | PUBLISH_UNKNOWN/CREATING | 绝不补偿；同 eventId 重发，消费者 DB 唯一约束去重；明确 ACK 后收敛 |
| 明确 NACK/return，补偿成功 | FAILED | `stock_release_record` 和 compensation marker 审计一次释放，客户端查询失败结果 |
| 明确发布失败，补偿暂败 | COMPENSATION_PENDING/FAILED 投影 | DB 扫描重试幂等补偿；长期 NEED_RECONCILE 交告警/对账 |
| broker ACK，写 PUBLISHED 或 Redis mark 前崩溃 | RESERVED/PUBLISHED | 扫描重发同 eventId；重复消息幂等建单 |
| DB 建单提交、消费者 ACK 前崩溃 | PERSISTED/PENDING_CONFIRM | RabbitMQ 重投；reservationId/eventId 唯一约束返回原订单，再手动 ACK |
| DB 唯一 guard 冲突或永久业务校验失败 | FAILED | 先执行/记录可重试库存补偿；补偿成功后异常继续抛出并最终进 DLQ，不吞异常 |
| DB 建单提交、Redis persisted mark 失败 | PERSISTED/PENDING_CONFIRM | 不 ACK，重投复用原订单并重试 mark；数据库订单不回滚 |

## 6. 超时可靠性补充（已实现）

Rabbit TTL/DLX 是触发器，不是时间事实或唯一保障。以下三层共同防丢：建单事务 outbox、publisher confirm、appointment 定时补偿扫描 `PENDING_CONFIRM AND confirm_deadline <= now`。三者调用同一个 Service CAS，因此重复执行安全。

## 7. 库存对账与消息窗口（已实现）

对账不产生新的 MQ 事件。`reservation.requested` 在 Redis 预占与 DB request 更新、MQ confirm、订单提交、Redis markPersisted 之间允许短暂状态差；2 分钟内有 request/order/release 更新时标为 `TRANSIENT_PENDING`，不自动修复。明确发布失败且补偿成功应满足 `charged=successfulRelease=1`、订单数 0、库存回到放号总量；补偿暂败记录为 `COMPENSATION_PENDING`，仍计入扣减。重复消费只形成一张订单和一个消费事实。`PUBLISH_UNKNOWN` 不能假定为发布失败并释放；长期无订单预占标为 `UNRESOLVED_RESERVATION` 并告警。对账 Lua CAS 与 reserve/compensate/adjust 共用 `mutationSeq`，并发变化后放弃写入、重算最新报告。
