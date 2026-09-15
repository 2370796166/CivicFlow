# CivicFlow RabbitMQ 事件契约

## 1. 拓扑与命名

所有资源由应用以声明式、durable 配置创建；生产禁止依赖控制台手工创建。消息持久化，发布启用 `publisher-confirm-type=correlated`、publisher returns、`mandatory=true`。确认超时不等于 NACK：先按同一 eventId 重试，达到明确失败阈值才补偿；结果不确定由恢复任务裁决。

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

- 64 位 ID 在 JSON 中为字符串；未知字段必须忽略；必填字段缺失/版本不支持进入 DLQ，不无限重试。
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
  "serviceDate": "2026-09-12",
  "slotConfigVersion": 7,
  "reservedAt": "2026-09-10T06:30:00.123Z",
  "reservationExpiresAt": "2026-09-10T06:40:00.123Z"
}
```

消费者不得相信名称/额度；用可信资源快照或消息中的已验证 ID/版本建单。事务插入 message record、active guard、订单、日志和 timeout outbox。若 active guard 被另一个 reservation 占用，将本 reservation 记为拒绝并以 `DUPLICATE_GUARD_REJECTED` 触发库存补偿。

### `appointment.confirm.timeout.requested.v1`（outbox 逻辑事件）

outbox payload：`{reservationId,appointmentId,confirmDeadline}`。发布到 routing key `appointment.confirm.timeout.schedule.v1`，进入 5 分钟 delay queue。`eventId` 为独立 UUID，幂等键 `timeout:{reservationId}`。

如果消息进入 delay queue 已晚于 `confirmDeadline`，发布器可直接路由 check queue；超时消费者始终以 DB deadline + status CAS 裁决。早到则按剩余时间重新调度，不能提前过期。

### `appointment.check-in.claimed.v1`

appointment 在 check-in claim 的 CAS 事务内写 outbox。payload：`{claimId,appointmentId,userId,outletId,itemId,serviceDate,checkedInAt}`，幂等键 `appointmentId`，不包含二维码 token/nonce。queue 同步请求路径可直接建票；该事件负责补偿“claim 已提交但同步建票失败”，`queue_ticket.appointment_id UNIQUE` 使两条路径并发安全。

### queue 状态事件

| Routing key | payload 关键字段 | appointment 目标转换 | 幂等键 |
| --- | --- | --- | --- |
| `queue.ticket.checked-in.v1` | eventId,ticketId,appointmentId,claimId,userId,outletId,checkedInAt | 幂等关联 ticketId；状态通常已 CHECKED_IN | eventId；业务唯一 appointmentId |
| `queue.ticket.serving.v1` | ticketId,appointmentId,windowId,sessionId,servingAt | CHECKED_IN -> SERVING | eventId |
| `queue.ticket.completed.v1` | ticketId,appointmentId,windowId,completedAt,resultCode? | SERVING -> COMPLETED | eventId |
| `queue.ticket.no-show.v1` | ticketId,appointmentId,windowId,missedAt | CHECKED_IN -> NO_SHOW | eventId |

这些事件由 queue 本地事务写 outbox 后发布；appointment 消费者对 eventId 去重并执行原状态 CAS。重复目标状态且来源 ticket 相同可视为成功；乱序造成前置状态缺失时有限延迟重试，超过窗口进 DLQ/对账，禁止跳跃状态。

### resource slot 变更

首版优先由 appointment 通过 Feign 拉取/轮询配置快照。若实现事件，则使用 `resource.slot.changed.v1`：`{slotId,oldTotalQuota,newTotalQuota,oldStatus,newStatus,configVersion,changedAt}`，outbox 发布到 `cf.resource.x`。appointment 只接受 `configVersion=current+1` 的增量；版本跳跃转全量拉取与对账，不直接 SET 库存。

## 4. 重试、死信与确认

| 类别 | 处理 |
| --- | --- |
| 瞬时错误（连接、锁超时、下游 503） | 指数退避 + jitter，建议 5s/30s/2m，最多 3 次；使用重试队列，不在消费线程 sleep |
| 业务幂等/目标已达 | ACK，返回/记录原结果 |
| 业务乱序 | 短期重试；仍缺前置状态则 DLQ + 对账 |
| 非法 schema/版本/同键异载荷 | 不重试，直接 DLQ 并告警 |
| DB 唯一冲突 | 判别同 reservation/event 为幂等；不同业务占位为确定拒绝并补偿 |
| 消费代码未知异常 | 有限重试后 DLQ；不得无限 requeue 热循环 |

每个源 queue 配套 retry queue（TTL 后 DLX 回源）和 DLQ；具体退避队列可按 `5s/30s/2m` 三档。DLQ 重放必须是管理员受审计操作，保留原 eventId，不允许通过改 ID 绕过幂等。

## 5. Redis 预占发布补偿协议

1. Lua 把 reservation 写入 pending ZSET，状态 RESERVED。
2. 生产者用 `eventId=reservationId` 发布；ACK 后 `mark_published.lua` 标记 PUBLISHED。returned/NACK/重试耗尽执行 `compensate_stock.lua`。
3. ACK 与 mark 脚本之间崩溃会导致恢复任务重发；消费者幂等。
4. Lua 后、publish 前崩溃由 pending ZSET 扫描恢复。扫描先用短租约 claim reservation；仍在发布期限内重发，已过期补偿。
5. 消费建单提交后可调用 `mark_persisted.lua` 延长证据 TTL；调用失败不回滚数据库，由对账补齐。
6. 补偿 Lua 返回 ALREADY_RELEASED 视为成功；返回 NEED_RECONCILE 写/更新 `stock_release_record` 并告警，禁止盲目 `INCR`。

## 6. 超时可靠性补充

Rabbit TTL/DLX 是触发器，不是时间事实或唯一保障。以下三层共同防丢：建单事务 outbox、publisher confirm、appointment 定时补偿扫描 `PENDING_CONFIRM AND confirm_deadline <= now`。三者调用同一个 Service CAS，因此重复执行安全。
