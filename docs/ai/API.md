# CivicFlow 首版 REST API

## 1. 通用协议

- 外部基路径 `/api/v1`；内部路径 `/internal/v1` 且不经公网 gateway。
- 成功/失败统一响应：`{ "code": "OK", "message": "success", "data": ..., "requestId": "..." }`。分页 `data={items,page,size,total}`。
- 所有 Long ID 为 JSON 字符串；时间使用 ISO-8601，instant 带 `Z`/offset，serviceDate 为 `yyyy-MM-dd`。
- 写接口接受 `Idempotency-Key`。标注“必需”时缺少返回 `COMMON_400_IDEMPOTENCY_REQUIRED`；服务保存/推导 payload hash，同键异载荷返回 409。
- 认证使用 `Authorization: Bearer <access-token>`；角色只是第一层，仍做本人/网点/窗口归属。
- GET 列表默认 `page=1,size=20`，上限 100；禁止无界导出。

下表“主要请求/响应”仅列业务字段，不重复响应壳、审计字段。

## 2. 认证与用户（auth）

| 方法与路径 | 角色 | 幂等 | 主要请求 -> 响应 |
| --- | --- | --- | --- |
| POST `/api/v1/auth/login` | 公开 | 否；IP 限流 | `{loginName,password}` -> `{accessToken,expiresIn,refreshToken,user}` |
| POST `/api/v1/auth/refresh` | 公开 | refresh token family 内轮换 | `{refreshToken}` -> 新 token 对；旧 token 撤销 |
| POST `/api/v1/auth/logout` | 已登录 | 推荐 | `{refreshToken}` -> 空；重复撤销成功 |
| GET `/api/v1/user/me` | USER/STAFF/ADMIN | N/A | -> `{id,displayName,roles}` |
| GET `/api/v1/admin/users` | ADMIN | N/A | 筛选/分页 -> 脱敏用户列表 |
| POST `/api/v1/admin/users` | ADMIN | 必需 | `{username,mobile,password,displayName,roles}` -> 用户 VO |
| PATCH `/api/v1/admin/users/{userId}/status` | ADMIN | 必需 | `{status,version}` -> 用户 VO |
| PUT `/api/v1/admin/users/{userId}/roles` | ADMIN | 必需 | `{roles,version}` -> 用户 VO；tokenVersion 增加 |

认证接口实现约定：

- `loginName` 可为规范化用户名或中国大陆手机号（接受 `+86`/`86` 前缀）；用户名统一转小写。账号不存在、密码错误、账号禁用和账号无有效角色均返回相同的 `AUTH_401_UNAUTHORIZED`，不得通过 code/message 区分。
- `expiresIn` 单位为秒。access JWT 为 RS256，`sub` 是字符串 userId，claims 仅含标准时间/签发声明、`roles`、`tokenVersion` 和当前 `kid`，不含用户名、手机号或证件号。
- refresh token 是 256 bit 随机不透明串，数据库仅存 SHA-256 摘要。刷新成功后原 token 变为 `ROTATED`；再次使用已轮换 token 返回 `AUTH_401_REFRESH_REUSED`，撤销整个 family 并增加用户 `tokenVersion`。登出按 family 撤销，重复登出仍返回成功。
- 管理员用户列表支持 `keyword`（用户名/展示名模糊匹配；完整手机号仅做 HMAC 等值匹配）、`status`、`page`、`size`，手机号只返回脱敏值。`size` 上限 100。
- 管理员创建、状态变更、角色替换必须带最长 128 字符的 `Idempotency-Key`。服务将 key 绑定 `actor + operation + payloadHash`；同键同载荷返回既有目标用户，同键异载荷返回 `COMMON_409_IDEMPOTENCY_CONFLICT`。
- 管理员状态接口首版只接受 `ENABLED|DISABLED`，`LOCKED` 仅供后续风控流程；禁用和角色替换都增加 `tokenVersion` 并撤销活动 refresh token。禁止管理员禁用自己或移除自己的 ADMIN 角色。

## 3. 资源公开查询与管理（resource）

| 方法与路径 | 角色 | 幂等 | 主要请求 -> 响应 |
| --- | --- | --- | --- |
| GET `/api/v1/user/outlets` | USER | N/A | keyword/page -> 可用网点分页 |
| GET `/api/v1/user/outlets/{outletId}` | USER | N/A | -> 网点详情（联系方式脱敏） |
| GET `/api/v1/user/outlets/{outletId}/items` | USER | N/A | page -> 可办理事项 |
| GET `/api/v1/user/slots` | USER | N/A | `outletId,itemId,date` -> slot 可预约投影，不暴露内部库存键 |
| GET `/api/v1/staff/scopes` | STAFF | N/A | -> 获授权网点/窗口及事项 |
| GET/POST `/api/v1/admin/outlets` | ADMIN | POST 必需 | 分页 / 创建 DTO -> 网点 VO |
| GET/PUT/DELETE `/api/v1/admin/outlets/{id}` | ADMIN | PUT/DELETE 必需 | 详情 / 完整更新 `{...,version}` / 逻辑删除 |
| PATCH `/api/v1/admin/outlets/{id}/status` | ADMIN | 必需 | `{status,version}` -> 网点 VO |
| GET/POST `/api/v1/admin/items` | ADMIN | POST 必需 | 分页 / 创建 -> 事项 VO |
| GET/PUT/DELETE `/api/v1/admin/items/{id}` | ADMIN | PUT/DELETE 必需 | 详情 / 更新 / 逻辑删除 |
| PATCH `/api/v1/admin/items/{id}/status` | ADMIN | 必需 | `{status,version}` -> 事项 VO |
| GET/POST `/api/v1/admin/windows` | ADMIN | POST 必需 | `outletId` 分页 / 创建 -> 窗口 VO |
| GET/PUT/DELETE `/api/v1/admin/windows/{id}` | ADMIN | PUT/DELETE 必需 | 详情 / 更新 / 逻辑删除 |
| PATCH `/api/v1/admin/windows/{id}/status` | ADMIN | 必需 | `{status,version}` -> 窗口 VO |
| PUT `/api/v1/admin/windows/{id}/items` | ADMIN | 必需 | `{itemIds,version}` -> 当前绑定 |
| GET/POST `/api/v1/admin/slots` | ADMIN | POST 必需 | 日历分页 / 单 slot 创建 -> slot VO |
| POST `/api/v1/admin/slots/batch` | ADMIN | 必需 | 日期范围/模板（有限上限）-> `{created,skipped,failed[]}` |
| GET/PUT `/api/v1/admin/slots/{id}` | ADMIN | PUT 必需 | 详情 / 更新 `{...,version}`；放号后按规则限制 |
| PATCH `/api/v1/admin/slots/{id}/status` | ADMIN | 必需 | `{status,version}` -> slot VO |
| PATCH `/api/v1/admin/slots/{id}/quota` | ADMIN | 必需 | `{totalQuota,configVersion}` -> slot VO；产生配置变更事件/同步任务 |

资源基础管理实现约定：

- 网点、事项、窗口列表均支持 `keyword/status/page/size`，窗口额外支持 `outletId`；默认 `page=1,size=20`，`size` 上限 100。创建默认 `ENABLED`，编码在写入前转大写。
- 网点写 DTO 包含 `code,name,address,longitude?,latitude?,contactPhone?`；联系电话只以 AES-256-GCM 密文持久化，所有角色的 VO 仅返回末四位脱敏值。事项写 DTO 包含 `code,name,description?,defaultDurationMinutes`；窗口写 DTO 包含 `outletId,code,name`。
- PUT、状态 PATCH 与窗口事项替换携带当前 `version`；DELETE 使用 `?version=`。所有管理写操作必须带最长 128 字符 `Idempotency-Key`，绑定 `actor + operation + HMAC(canonical payload)`；同键同载荷复用结果，同键异载荷返回 `COMMON_409_IDEMPOTENCY_CONFLICT`。
- 停用网点/事项/窗口前检查当天及未来的 `SCHEDULED|OPEN|SUSPENDED` 号源；窗口按当前网点及事项绑定判断。存在未来号源返回 `RESOURCE_409_IN_USE`。删除还检查活动窗口、窗口事项关系和 STAFF scope，只做逻辑删除。
- `GET /api/v1/staff/scopes` 从 JWT `sub` 取 staffUserId，仅返回该人员获授权且当前启用的网点、窗口与事项；不存在修改资源配置的 STAFF 路由。用户查询只返回启用网点以及至少被一个启用窗口绑定的启用事项。

号源配置实现约定：

- `GET /api/v1/admin/slots` 支持 `outletId,itemId,dateFrom,dateTo,status,page,size`，`dateFrom/dateTo` 相同即为某日明细；单日创建请求为 `{outletId,itemId,serviceDate,startTime,endTime,totalQuota,releaseAt,checkInStart,checkInEnd,status}`。创建只接受 `DRAFT|SCHEDULED`，事项必须已被该网点至少一个启用窗口绑定。
- `POST /api/v1/admin/slots/batch` 使用 `{outletId,itemId,startDate,endDate,startTime,endTime,totalQuota,releaseDaysBefore,releaseTime,checkInStart,checkInEnd,status}`，按 Asia/Shanghai 为每个服务日计算 instant；日期范围最多 31 天。完全相同时段计入 `skipped`，非完全相同的交叠时段进入 `failed[{serviceDate,code,message}]`，同一幂等键重试返回首次完整结果。
- 可配置日期为 Asia/Shanghai 当天至未来 365 天；必须满足 `endTime > startTime`、`releaseAt < slotStart`、`checkInStart <= slotStart <= checkInEnd <= slotEnd`。`SCHEDULED` 的 releaseAt 必须仍在未来。完整 PUT 只允许编辑 `DRAFT`；状态按 `DRAFT -> SCHEDULED/OPEN/CLOSED`、`SCHEDULED -> OPEN/SUSPENDED/CLOSED`、`OPEN -> SUSPENDED/CLOSED`、`SUSPENDED -> SCHEDULED/OPEN/CLOSED` 转换，并结合 releaseAt/slotEnd 校验。
- 创建/更新时间段按“网点行 -> 事项行 -> `(outlet,item,serviceDate)` guard 行”加锁后检查 `existing.start < new.end && existing.end > new.start`。相邻边界不算重叠；`uk_slot_exact` 是最后一道完全重复防线。
- quota 调整以 `configVersion` CAS。降额不得低于 `consumedHint`；已到 releaseAt 或处于 `OPEN|SUSPENDED` 时，如果消费事实尚未同步则拒绝降额并返回 `RESOURCE_409_CONSUMPTION_UNKNOWN`。有效 quota/状态变化与审计、`resource.slot.changed.v1` outbox 在同一本地事务提交；resource 不直接 SET/INCR Redis。

## 4. 预约（appointment）

| 方法与路径 | 角色 | 幂等 | 主要请求 -> 响应 |
| --- | --- | --- | --- |
| POST `/api/v1/user/reservations` | USER | `Idempotency-Key` 必需；应用据此生成/稳定返回 reservationId | `{slotId}` -> 202 `{reservationId,status:"CREATING",pollAfterMs}` |
| GET `/api/v1/user/reservations/{reservationId}` | USER | N/A | -> `{reservationId,status,appointment?,failureCode?}`；状态可为 CREATING/FAILED/订单投影，仅本人 |
| GET `/api/v1/user/appointments` | USER | N/A | date/status/page -> 本人预约分页 |
| GET `/api/v1/user/appointments/{appointmentId}` | USER | N/A | -> 本人预约详情 |
| POST `/api/v1/user/appointments/{id}/confirm` | USER | 必需 | `{version}` -> 最新预约；仅 PENDING_CONFIRM 且未过 deadline |
| POST `/api/v1/user/appointments/{id}/cancel` | USER | 必需 | `{reason,version}` -> 最新预约；按状态/截止规则 |
| POST `/api/v1/user/appointments/{id}/check-in-token` | USER | 推荐 | `{outletId}` -> `{token,expiresAt}`；仅本人 CONFIRMED 且在可签发窗口 |
| GET `/api/v1/admin/appointments` | ADMIN | N/A | user/outlet/item/date/status/page -> 脱敏分页 |
| GET `/api/v1/admin/reconciliations` | ADMIN | N/A | slot/status/page -> 对账运行/明细 |
| POST `/api/v1/admin/reconciliations` | ADMIN | 必需 | `{slotId?,date?}` -> 202 runId |
| POST `/api/v1/admin/reconciliations/{detailId}/repair` | ADMIN | 必需 | `{expectedSnapshotHash,configVersion}` -> 修复结果/需重算 |

抢号接口若客户端超时，必须用相同 Idempotency-Key 重试或按 reservationId 查询，不能生成无限新 reservation。202 不表示订单已创建；`CREATING` 只为投影状态。

## 5. 签到、排队与窗口工作台（queue）

| 方法与路径 | 角色 | 幂等 | 主要请求 -> 响应 |
| --- | --- | --- | --- |
| POST `/api/v1/user/check-ins` | USER | 必需 | `{checkInToken,outletId}` -> `{ticket}`；重复签到返回原票；claim 已成但建票暂败时返回 503 + claimId，随后可重试/查询 |
| GET `/api/v1/user/queue-tickets/current` | USER | N/A | `appointmentId?` -> 本人票、前方人数、当前叫号 |
| GET `/api/v1/user/queue-tickets/{ticketId}` | USER | N/A | -> 本人票进度 |
| POST `/api/v1/staff/work-sessions` | STAFF | 必需 | `{windowId}` -> ACTIVE session；校验 scope |
| DELETE `/api/v1/staff/work-sessions/{sessionId}` | STAFF | 必需 | `{version}` -> ENDED session |
| GET `/api/v1/staff/work-sessions/current` | STAFF | N/A | `windowId` -> 当前会话/当前票 |
| POST `/api/v1/staff/work-sessions/{id}/call-next` | STAFF | 必需 | `{itemIds?}` -> 原子领取的 CALLED ticket 或队列空 |
| POST `/api/v1/staff/queue-tickets/{id}/recall` | STAFF | 必需 | `{sessionId,version}` -> CALLED ticket |
| POST `/api/v1/staff/queue-tickets/{id}/miss` | STAFF | 必需 | `{sessionId,version}` -> MISSED ticket |
| POST `/api/v1/staff/queue-tickets/{id}/start` | STAFF | 必需 | `{sessionId,version}` -> SERVING ticket |
| POST `/api/v1/staff/queue-tickets/{id}/complete` | STAFF | 必需 | `{sessionId,version,resultCode?}` -> COMPLETED ticket |
| GET `/api/v1/admin/queues/overview` | ADMIN | N/A | outletId/date -> 按事项/窗口汇总，不做无界明细 |

## 6. 内部 API

内部 API 必须使用短期 service JWT（audience + scope）并位于隔离网络；不接受仅有 `X-User-Id` 的请求。

| 方法与路径 | 调用者 | 用途 |
| --- | --- | --- |
| GET `/internal/v1/resource/slots/{slotId}/snapshot` | appointment | 预热/校验/对账，返回 configVersion 与时间字段 |
| GET `/internal/v1/resource/slots/preheat-candidates` | appointment | 游标分页拉取预热候选 |
| GET `/internal/v1/resource/windows/{windowId}/authorization` | queue | 校验 staff/outlet/window/item scope |
| POST `/internal/v1/appointments/check-in/claim` | queue | `{token,userId,outletId,claimId}` -> 原子校验并执行/复用 CHECKED_IN claim；取消与签到由 appointment CAS 决胜 |
| POST `/internal/v1/appointments/{id}/queue-ticket-link` | queue | `{claimId,ticketId,eventId}` -> 幂等记录排队票关联，供对账 |
| GET `/internal/v1/appointments/{id}/status` | queue/reconciler | 最小状态快照 |
| GET `/.well-known/jwks.json` | gateway/services | access/service JWT 公钥；公网暴露策略由部署决定，可缓存 |

JWKS 同时发布活动 key 和仍处于验证窗口的历史公钥，不包含私钥参数。验证方必须按 `kid` 选择 key、仅允许 RS256，并校验 issuer、audience、nbf/exp；轮换流程见 `SECURITY.md`。

## 7. 错误码

| HTTP | 错误码 | 含义/客户端动作 |
| --- | --- | --- |
| 400 | COMMON_400_VALIDATION | 参数错误；展示字段级提示 |
| 400 | COMMON_400_IDEMPOTENCY_REQUIRED | 缺少必需幂等键 |
| 401 | AUTH_401_UNAUTHORIZED | 未登录、签名/issuer/audience/exp 无效 |
| 401 | AUTH_401_REFRESH_REUSED | refresh token 重放，撤销 token family |
| 403 | AUTH_403_FORBIDDEN | 角色不足 |
| 409 | AUTH_409_USER_EXISTS | 管理员创建的用户名或手机号已被使用 |
| 409 | AUTH_409_VERSION_CONFLICT | 用户状态/角色版本已过期，刷新后重试 |
| 403 | SECURITY_403_RESOURCE_SCOPE | 本人/网点/窗口归属不匹配 |
| 404 | COMMON_404_NOT_FOUND | 资源不存在或为防枚举而隐藏 |
| 409 | COMMON_409_IDEMPOTENCY_CONFLICT | 同幂等键载荷不同 |
| 409 | RESOURCE_409_VERSION_CONFLICT | 配置乐观锁冲突，刷新后重试 |
| 409 | RESOURCE_409_CODE_EXISTS | 网点/事项编码或同网点窗口编码已存在 |
| 409 | RESOURCE_409_SLOT_OVERLAP | 时段重复/重叠 |
| 409 | RESOURCE_409_SLOT_STATE_CONFLICT | 当前号源状态、父资源或状态转换不允许操作 |
| 409 | RESOURCE_409_QUOTA_BELOW_CONSUMED | 新总量低于已同步消费量 |
| 409 | RESOURCE_409_CONSUMPTION_UNKNOWN | 已放号但消费事实未同步，禁止降额 |
| 409 | RESOURCE_409_IN_USE | 有未来号源/业务引用，不可停用删除 |
| 409 | APPT_409_DUP_ACTIVE | 同用户同事项同日已有有效预约 |
| 409 | APPT_409_STOCK_EMPTY | 库存不足 |
| 409 | APPT_409_STATE_CONFLICT | 非法状态或 CAS 已被其他请求赢得 |
| 409 | APPT_409_CONFIRM_TIMEOUT | 已过确认截止；以查询状态为准 |
| 409 | QUEUE_409_SESSION_ACTIVE | 窗口已有活动会话 |
| 409 | QUEUE_409_STATE_CONFLICT | 排队状态/窗口归属冲突 |
| 410 | APPT_410_RESERVATION_GONE | 预占已补偿/过期且未建单 |
| 410 | SECURITY_410_QR_EXPIRED | 二维码过期或 nonce 已失效，可重新签发 |
| 422 | APPT_422_SLOT_NOT_OPEN | 未放号、停售或已关闭 |
| 422 | QUEUE_422_CHECKIN_WINDOW | 不在签到窗口/日期/网点不匹配 |
| 429 | GATEWAY_429_RATE_LIMITED | 限流；按 Retry-After 重试 |
| 503 | COMMON_503_DEPENDENCY_UNAVAILABLE | Redis/MQ/依赖不可用，结果可能未知时附 reservationId/requestId |

安全相关的“他人资源”默认返回 404，避免枚举；管理审计仍记录真实拒绝原因。错误 message 可本地化，客户端逻辑只依赖稳定 code。
