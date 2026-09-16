# CivicFlow 领域模型与状态机

本项目使用贫血 Entity + Service 业务编排，不以本文为理由引入 DDD 代码层。所有 64 位 ID 对外为字符串。

## 1. 核心实体

| 所属服务 | 实体 | 关键字段 | 说明 |
| --- | --- | --- | --- |
| auth | User | id, username, mobileCipher, mobileHash, mobileKeyVersion, passwordHash, status, tokenVersion | 登录主体；手机号只以 HMAC 摘要等值检索 |
| auth | Role/UserRole | roleCode, status, userId, deleted | 首版 USER/STAFF/ADMIN；授权关系可删除后重建 |
| auth | RefreshToken | tokenHash, userId, familyId, status, expiresAt, revokedAt | 只保存摘要，轮换并检测重放 |
| auth | AuthAdminIdempotency | actorUserId, operation, idempotencyKey, payloadHash, resourceId | 管理员写操作幂等绑定；不保存敏感明文 |
| auth | AuthSecurityAudit | actorUserId, targetUserId, action, outcome, requestId, before/after | 高影响认证管理操作的不可变审计事实 |
| resource | ServiceOutlet | id, code, name, address, contactPhoneCipher, contactPhoneKeyVersion, status, version | 服务网点；联系电话不参与检索 |
| resource | ServiceItem | id, code, name, status, defaultDurationMinutes, version | 办理事项；ID 全局唯一 |
| resource | ServiceWindow | id, outletId, code, name, status, version | 物理/虚拟窗口 |
| resource | WindowItemRel | windowId, itemId | 窗口可办理事项 |
| resource | StaffWindowScope | staffUserId, outletId, windowId | `windowId=0` 表示整个网点授权 |
| resource | ResourceSlot | id, outletId, itemId, serviceDate, startTime, endTime, totalQuota, releaseAt, checkInStart, checkInEnd, status, configVersion | 配置事实；时间窗口属 Asia/Shanghai |
| resource | ResourceAdminIdempotency | actorUserId, operation, idempotencyKey, payloadHash, resourceId | 管理写操作幂等绑定；payloadHash 使用部署 HMAC secret |
| resource | ResourceAdminAudit | actorUserId, resourceType/id, action, requestId, before/after | 资源配置变更的不可变白名单审计事实 |
| appointment | AppointmentOrder | id, reservationId, userId, slot/resource 快照, status, confirmDeadline, confirmedAt, cancelledAt, qrNonceHash/qrExpiresAt, version | 消费事实；保留名称快照供历史查询 |
| appointment | ActiveBookingGuard | userId, itemId, serviceDate, reservationId, appointmentId | 活跃预约唯一占位 |
| appointment | StockReleaseRecord | reservationId, slotId, reason, status, attempts | 一次性库存释放事实/任务 |
| appointment | MessageConsumeRecord/OutboxEvent | consumer+idempotencyKey/eventId, payloadHash/status | 至少一次消息的去重与可靠发布事实 |
| appointment | StockReconciliationRun/Detail | runId, slotId, configVersion, expected/actual, classification, repairStatus | 库存对账与修复审计 |
| queue | QueueTicket | id, appointmentId, userId, outletId, itemId, serviceDate, ticketNo, priority, status, checkedInAt, calledWindowId, version | 排队事实 |
| queue | WindowWorkSession | id, windowId, staffUserId, status, startedAt, endedAt | 窗口工作会话 |
| queue | ActiveWindowSessionGuard | windowId, sessionId, staffUserId | 活动窗口会话唯一占位 |
| queue | CheckinReconciliationRecord | appointmentId, ticketId, claimId, status, attempts | claim 已成功但建票/回告未完成的补偿事实 |

## 2. 枚举

| 枚举 | 值 |
| --- | --- |
| UserStatus | ENABLED, DISABLED, LOCKED |
| RoleCode | USER, STAFF, ADMIN |
| RoleStatus | ENABLED, DISABLED |
| RefreshTokenStatus | ACTIVE, ROTATED, REVOKED, EXPIRED |
| ResourceStatus | ENABLED, DISABLED |
| SlotStatus | DRAFT, SCHEDULED, OPEN, SUSPENDED, CLOSED |
| ReservationProjectionStatus | CREATING, FAILED, PENDING_CONFIRM, CONFIRMED, CANCELLED, EXPIRED, CHECKED_IN, SERVING, COMPLETED, NO_SHOW |
| AppointmentStatus（持久化） | PENDING_CONFIRM, CONFIRMED, CANCELLED, EXPIRED, CHECKED_IN, SERVING, COMPLETED, NO_SHOW |
| QueueTicketStatus | WAITING, CALLED, MISSED, SERVING, COMPLETED |
| WindowSessionStatus | ACTIVE, ENDED |
| StockReleaseReason | PUBLISH_FAILED, DUPLICATE_GUARD_REJECTED, APPOINTMENT_EXPIRED, USER_CANCELLED, ADMIN_CANCELLED, RECONCILIATION |
| CompensationStatus | PENDING, SUCCEEDED, NEED_RECONCILE, FAILED |
| MessageConsumeStatus | PROCESSING, PROCESSED, FAILED |
| OutboxStatus | PENDING, PUBLISHING, PUBLISHED, FAILED |
| AppointmentActorType / QueueActorType | USER, STAFF, ADMIN, SERVICE, SYSTEM |
| AppointmentOperation | CREATE, CONFIRM, CANCEL, EXPIRE, CHECK_IN, LINK_QUEUE_TICKET, START_SERVING, COMPLETE, MARK_NO_SHOW |
| QueueOperation | CHECK_IN, CALL, RECALL, START_SERVING, MARK_MISSED, COMPLETE |
| ReconciliationTriggerType | SCHEDULED, ADMIN, ALERT |
| ReconciliationRunStatus | RUNNING, COMPLETED, FAILED |
| ReconciliationClassification | CONSISTENT, TRANSIENT_PENDING, DB_ORDER_REDIS_ACTIVE_MISSING, RELEASE_RECORDED_STOCK_NOT_RETURNED, REDIS_STOCK_MISMATCH, CONFIG_VERSION_GAP, EVIDENCE_MISSING |
| ReconciliationRepairStatus | NOT_REQUIRED, PENDING, REPAIRED, MANUAL_REQUIRED, FAILED |
| CheckinReconciliationStatus | PENDING, TICKET_CREATED, LINKED, NEED_RECONCILE, FAILED |

所有持久化 enum 均以枚举名称写入 `VARCHAR`，由 MyBatis `EnumTypeHandler` 处理；禁止 ordinal/数字状态。appointment 与 queue 之间不建立 Maven 依赖，queue 只在本地用 `QueueAppointmentStatus.CHECKED_IN` 表达收到的最小预约状态快照。

`CREATING` 只存在于查询投影：reservation 在 Redis 已成功且 appointment_order 尚不可见。它不是数据库预约状态，理由与替代方案见 ADR-006。

`FAILED` 也只用于 reservation 查询，表示预占最终补偿、建单被最终拒绝或进入需人工处理的明确失败；响应同时给稳定 failureCode，不把它写入 appointment_order。

auth 会话状态补充：refresh token 仅允许 `ACTIVE -> ROTATED`（成功刷新）或 `ACTIVE|ROTATED -> REVOKED`（登出、禁用、角色变更、重放处置）；过期 token 不再签发 access token。重放已轮换 token 时撤销同 family 并增加用户 `tokenVersion`。账号禁用和角色替换也增加 `tokenVersion`、撤销活动 refresh token；access token 的签名有效不等于高风险管理操作仍获授权，auth Service 会在线复核当前版本与角色。

“有效预约”统一定义为持久化状态 `PENDING_CONFIRM|CONFIRMED|CHECKED_IN|SERVING`，加上尚未持久化的有效 Redis reservation。`COMPLETED|NO_SHOW` 保留消费历史但不占 active guard；`CANCELLED|EXPIRED` 终止并触发可适用的库存释放。

## 3. 预约状态机

```text
[Redis reservation] --查询投影--> CREATING
          |
          | 消费建单成功
          v
 PENDING_CONFIRM --确认(<=deadline)--> CONFIRMED --签到--> CHECKED_IN
        |                   |                         |
        |超时               |合法取消                 |叫号/开始办理
        v                   v                         v
     EXPIRED             CANCELLED                 SERVING
        ^                                             |  \
        |                                             |   \过号/未到
        +--（无回退）                                  v    v
                                                  COMPLETED NO_SHOW

PENDING_CONFIRM --用户/管理员取消--> CANCELLED
```

| 当前状态 | 命令/事件 | 目标状态 | CAS 条件与副作用 |
| --- | --- | --- | --- |
| PENDING_CONFIRM | confirm | CONFIRMED | `deadline >= DB_NOW`；记录 confirmedAt；保留 active guard，不释放库存 |
| PENDING_CONFIRM | timeout | EXPIRED | `deadline <= DB_NOW`；删除 guard；建立唯一 release 记录 |
| PENDING_CONFIRM | cancel | CANCELLED | 本人/管理员且符合取消策略；删除 guard；建立 release 记录 |
| CONFIRMED | cancel | CANCELLED | 未签到且在取消截止前；删除 guard；建立 release 记录 |
| CONFIRMED | check-in claim | CHECKED_IN | appointmentId/user/outlet/date/current nonce 校验；同事务 CAS，保留 guard |
| CHECKED_IN | serving event | SERVING | 合法 queue ticket/window；保留 guard |
| CHECKED_IN | no-show event | NO_SHOW | 合法 MISSED；删除 guard；不把已过时段库存重新售卖 |
| SERVING | completed event | COMPLETED | 合法完成；删除 guard |
| SERVING | no-show event | NO_SHOW | 仅纠错权限可用，常规流程不允许 |

重复命令若目标状态相同且幂等键/载荷一致，返回当前资源；否则返回状态冲突。所有终态禁止回退。确认与超时、取消与超时并发时，仅第一个满足原状态/截止条件的 SQL 影响一行。

### 非法预约转换（非穷举）

- CREATING 不能由 API 直接确认/取消数据库订单；客户端需按 reservationId 查询，取消预占另设内部补偿流程。
- PENDING_CONFIRM 不能签到、开始办理或完成。
- CONFIRMED 不能重复扣库存、不能直接 SERVING/COMPLETED。
- CHECKED_IN 不能再次取消或回到 CONFIRMED。
- SERVING 不能取消、过期或回到等待。
- CANCELLED、EXPIRED、COMPLETED、NO_SHOW 均为终态；任何迁出均返回 `APPT_409_STATE_CONFLICT`。

## 4. 排队状态机

```text
WAITING --叫下一号(锁定领取)--> CALLED --开始办理--> SERVING --完成--> COMPLETED
                              |  |
                              |  +--重呼--> CALLED（自转换，增加 callCount）
                              +--过号--> MISSED
```

首版 `MISSED` 为终态并异步映射 appointment `CHECKED_IN -> NO_SHOW`；不提供自动重新入队。若后续引入人工回队，必须新增 ADR、权限和排序规则，不得复用普通状态更新。

| 当前状态 | 操作 | 目标 | 规则 |
| --- | --- | --- | --- |
| WAITING | call-next | CALLED | ACTIVE 窗口会话；事务中 `FOR UPDATE SKIP LOCKED`；写 window/session/calledAt |
| CALLED | recall | CALLED | 仅领取该票的当前窗口；callCount + 1；幂等键防重复点击 |
| CALLED | start-serving | SERVING | 当前窗口/员工；写 servingAt；发可靠事件 |
| CALLED | mark-missed | MISSED | 当前窗口/员工；写 missedAt；发 no-show 事件 |
| SERVING | complete | COMPLETED | 当前窗口/员工；写 completedAt；发 completed 事件 |

领取排序：`priority DESC, checked_in_at ASC, id ASC`。锁范围限候选票与单次短事务；网络/MQ 调用不得位于锁事务内。

### 非法排队转换

- WAITING 不允许重呼、过号、办理完成。
- CALLED 不允许被其他窗口开始/过号，不允许再次执行 call-next 覆盖领取关系。
- MISSED/COMPLETED 不允许迁出；重复相同终态操作仅在同一幂等键下返回原结果。
- 未开启或已结束的窗口会话不能执行任何领取/办理写操作。

## 5. Slot 与库存生命周期

- DRAFT：未排期，不预热；SCHEDULED：可预热但 releaseAt 前 Lua 拒绝；OPEN：可抢；SUSPENDED：暂停售；CLOSED：服务结束。
- Redis 初始 `remaining=totalQuota`，附 `configVersion`。已放号后只能应用可证明的增量：`remaining += newTotal-oldTotal`，且结果不得小于 0；版本不连续则暂停并对账。
- 预占成功即计入消费；发布失败、预约取消/过期才释放。确认、签到、办理、完成不再次扣减。

资源主数据规则：网点、事项、窗口创建后默认为 `ENABLED`；停用前以 Asia/Shanghai 当天为边界检查 `SCHEDULED|OPEN|SUSPENDED` 的当前/未来号源，窗口通过当前 `window_item_rel` 映射到网点/事项号源。逻辑删除还要求没有活动窗口/绑定/scope 等配置引用。所有变更以 version CAS 裁决；窗口事项集合是窗口聚合的一部分，替换绑定会增加窗口 version。

## 6. 时间与幂等语义

- `confirmDeadline = appointment.createdAt + 5 minutes`；数据库时间是最终裁决，前端倒计时仅展示。
- 5 分钟是业务硬截止：即使 MQ/扫描延迟导致表中短暂仍为 PENDING_CONFIRM，确认 CAS 也必须失败；查询层按 DB 时间返回有效投影 EXPIRED，并触发/等待同一过期 Service 完成持久化与释放。
- 服务日期、签到窗口按 `Asia/Shanghai` 判定；token 的 iat/exp 使用 UTC instant。
- 同一写接口的 `Idempotency-Key` 必须绑定 `actor + operation + canonical payload hash`；同键不同载荷返回 `COMMON_409_IDEMPOTENCY_CONFLICT`。
- 抢号业务幂等键为 `reservationId`；建单消费幂等键也是 `reservationId`；超时/队列事件用 `eventId` 去重且用状态 CAS 判定副作用。
