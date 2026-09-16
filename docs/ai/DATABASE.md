# CivicFlow 数据库基线

## 1. 通用约定

- MySQL 8，四个独立 schema：`civicflow_auth`、`civicflow_resource`、`civicflow_appointment`、`civicflow_queue`；每个服务仅获本 schema 权限。
- 主键 `BIGINT UNSIGNED`，由应用雪花算法生成；API/消息序列化为字符串。时间 `DATETIME(3)` 存 UTC instant；服务日期 `DATE`、本地时段 `TIME(0)` 表达 Asia/Shanghai 业务语义。
- 每张表含 `created_at`、`updated_at`。可变聚合含 `version INT UNSIGNED`；配置主数据/关系使用 `deleted BIGINT UNSIGNED`，`0` 表示有效，删除时写本行 `id`，从而允许同一业务键被删除、重建多次且不碰撞历史行。订单、日志、消息、补偿与对账事实不得逻辑删除；guard 表以“行存在”表达当前占位，按 ADR-007/008 在本地事务中物理删除。
- 状态使用可读 `VARCHAR(32)`，Java 字段使用 enum，并由 MyBatis `EnumTypeHandler` 按枚举名称持久化；不使用数据库 ENUM，追加状态必须通过代码与追加式迁移共同演进。
- `created_at/updated_at`、截止时间、事件时间均为 UTC `DATETIME(3)`；不可变日志的 `updated_at` 初始等于 `created_at` 且业务代码不得更新。服务日期和时段仍分别使用 `DATE`、`TIME(0)` 表达 Asia/Shanghai 语义。
- JSON 只用于不可检索快照/扩展信息，不替代规范字段。索引遵循最左前缀和真实查询；列表强制分页。
- 下表与四个服务的 `V1` Flyway DDL 及 auth 的追加式 `V2/V3` 一致；后续字段、索引和状态变更必须新增迁移并同步本文，不得改写已发布迁移。

## 2. `civicflow_auth`

### `sys_user`

| 字段 | 类型/约束 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | 用户 ID |
| username | VARCHAR(64) NOT NULL | 规范化登录名 |
| mobile_cipher | VARBINARY(512) NULL | 手机号信封加密密文 |
| mobile_hash | BINARY(32) NULL | `HMAC-SHA-256(normalized mobile)` 等值检索 |
| mobile_key_version | SMALLINT UNSIGNED NULL | 解密密钥版本；与密文/哈希同空同非空 |
| password_hash | VARCHAR(100) NOT NULL | BCrypt/自适应哈希 |
| display_name | VARCHAR(64) | 展示名 |
| status | VARCHAR(32) NOT NULL | ENABLED/DISABLED/LOCKED |
| token_version | INT NOT NULL DEFAULT 0 | 全局撤销 access token |
| last_login_at | DATETIME(3) NULL | 审计 |
| version, created_at, updated_at, deleted | 通用 | `deleted=0` 有效，删除写 `id` |

约束/索引：`UNIQUE uk_user_username(username,deleted)`；`UNIQUE uk_user_mobile_hash(mobile_hash,deleted)`（NULL 可重复）；`INDEX idx_user_status(status,deleted,id)`。用户名和手机号哈希在写入前规范化。

### `sys_role`

`id PK, role_code VARCHAR(32), role_name, status, version, created_at, updated_at`；`UNIQUE uk_role_code(role_code)`；`INDEX idx_role_status(status,id)`。`V1` 以固定 ID 1/2/3 创建 USER/STAFF/ADMIN 种子，应用雪花 ID 不会与之碰撞。

### `sys_user_role`

`id PK, user_id, role_id, created_at, updated_at, deleted`；`UNIQUE uk_user_role(user_id,role_id,deleted)`；`INDEX idx_user_role_role(role_id,deleted,user_id)`。外键不跨服务，服务内关系由事务维护；删除写本行 ID 后可安全重新授权。

### `refresh_token`

`id PK, token_hash BINARY(32), user_id, family_id CHAR(36), status, issued_at, expires_at, revoked_at NULL, replaced_by_hash BINARY(32) NULL, client_fingerprint_hash BINARY(32) NULL, created_at, updated_at`。状态为 `ACTIVE|ROTATED|REVOKED|EXPIRED`，原 token 轮换后保留摘要用于重放检测。

约束/索引：`UNIQUE uk_refresh_hash(token_hash)`；`INDEX idx_refresh_user(user_id,status,expires_at)`；`INDEX idx_refresh_family(family_id,issued_at)`；`INDEX idx_refresh_expiry(status,expires_at,id)`。不保存 token 明文。

### `auth_admin_idempotency`（V2）

`id PK, actor_user_id, operation VARCHAR(64), idempotency_key VARCHAR(128), payload_hash BINARY(32), resource_id NULL, created_at, updated_at`。

- `UNIQUE uk_auth_admin_idempotency(actor_user_id,operation,idempotency_key)`：管理员创建用户、变更状态、替换角色先以 `INSERT IGNORE` 竞争记录，再 `SELECT ... FOR UPDATE` 绑定/复用结果，防止超时重试重复执行。
- `payload_hash` 是规范化请求的 SHA-256 摘要；密码先以部署 HMAC secret 和独立用途前缀生成不可离线枚举的中间摘要，再参与规范化载荷，表中不保存密码、手机号、refresh/access token 或响应快照。
- `resource_id` 为目标用户 ID；同 key 异载荷返回幂等冲突。同 key 同载荷在原事务提交后返回当前目标用户投影。

### `auth_security_audit`（V3）

`id PK, actor_user_id NULL, target_user_id NULL, action VARCHAR(64), outcome VARCHAR(32), request_id VARCHAR(64), before_json JSON NULL, after_json JSON NULL, occurred_at, created_at, updated_at`。

- 管理员创建用户、状态变更、角色替换以及 refresh token 重放处置与审计记录在同一本地事务提交；索引覆盖 actor、target 和 requestId 的分页追溯。
- before/after 只记录状态、版本和角色代码白名单，不记录用户名、手机号、密码、token、密文或摘要。审计事实不可更新/删除。

## 3. `civicflow_resource`

### 配置主表

| 表 | 关键字段 | 约束与索引 |
| --- | --- | --- |
| service_outlet | id, code, name, address, longitude, latitude, status, contact_phone_cipher, contact_phone_key_version, version, deleted | `UNIQUE(code,deleted)`；`INDEX(status,deleted,id)`；电话密文与 key version 同空同非空 |
| service_item | id, code, name, description, default_duration_minutes, status, version, deleted | `UNIQUE(code,deleted)`；`INDEX(status,deleted,id)` |
| service_window | id, outlet_id, code, name, status, version, deleted | `UNIQUE(outlet_id,code,deleted)`；`INDEX(outlet_id,status,deleted,id)` |
| window_item_rel | id, window_id, item_id, created_at, updated_at, deleted | `UNIQUE(window_id,item_id,deleted)`；`INDEX(item_id,deleted,window_id)` |
| staff_window_scope | id, staff_user_id, outlet_id, window_id, created_at, updated_at, deleted | `window_id=0` 明确表示整个网点；`UNIQUE(staff_user_id,outlet_id,window_id,deleted)`，不依赖 MySQL NULL 唯一性 |

### `resource_slot`

字段：`id PK, outlet_id, item_id, service_date DATE, start_time TIME, end_time TIME, total_quota INT UNSIGNED, release_at DATETIME(3), check_in_start DATETIME(3), check_in_end DATETIME(3), status VARCHAR(32), config_version BIGINT, consumed_hint INT NULL, version INT, created_by, updated_by, created_at, updated_at, deleted`。

约束/索引：

- `CHECK(end_time > start_time AND total_quota >= 0 AND check_in_end > check_in_start)`。
- `UNIQUE uk_slot_exact(outlet_id,item_id,service_date,start_time,end_time,deleted)`；重叠时段无法仅靠普通唯一索引防止，Service 在事务内对同一 `(outlet,item,date)` 使用命名锁/父行锁后做重叠查询，且数据库异常归一为冲突。
- `INDEX idx_slot_calendar(outlet_id,item_id,service_date,status,deleted,start_time)`。
- `INDEX idx_slot_release(status,release_at,id)` 用于分页预热。
- `config_version` 每次影响 Redis 的配置变更单调递增；同步只接受连续版本。

### 管理幂等与审计（V2）

- `resource_admin_idempotency`：`id, actor_user_id, operation, idempotency_key, payload_hash BINARY(32), resource_id NULL, created_at, updated_at`；`UNIQUE(actor_user_id,operation,idempotency_key)`。payloadHash 使用部署 secret 的 HMAC-SHA-256 计算，避免联系电话等低熵字段形成可离线枚举的裸摘要；表中不保存请求/响应明文。
- `resource_admin_audit`：`id, actor_user_id, resource_type, resource_id, action, request_id, before_json, after_json, occurred_at, created_at, updated_at`。before/after 只记录 code/name/status/version/outletId/itemIds 等白名单配置，不记录联系电话、联系电话密文/密钥、JWT 或完整请求体；审计事实不可更新或删除。
- 网点、事项、窗口的更新、状态变更、逻辑删除均以 `id + version + deleted=0` 条件更新；状态 SQL 额外限制当前状态属于 `ENABLED|DISABLED`。窗口事项关系以窗口 `version` 作为聚合并发控制，替换时旧关系写 `deleted=id` 后再插入新关系。

## 4. `civicflow_appointment`

### `appointment_order`

字段：

`id PK, reservation_id CHAR(36) NOT NULL, user_id BIGINT NOT NULL, slot_id BIGINT NOT NULL, outlet_id BIGINT NOT NULL, item_id BIGINT NOT NULL, service_date DATE NOT NULL, slot_start_time TIME NOT NULL, slot_end_time TIME NOT NULL, outlet_name_snapshot VARCHAR(128), item_name_snapshot VARCHAR(128), status VARCHAR(32) NOT NULL, confirm_deadline DATETIME(3) NOT NULL, confirmed_at/cancelled_at/expired_at/checked_in_at/serving_at/completed_at/no_show_at DATETIME(3) NULL, cancel_reason VARCHAR(256) NULL, qr_nonce_hash BINARY(32) NULL, qr_expires_at DATETIME(3) NULL, qr_key_id VARCHAR(32) NULL, checkin_claim_id CHAR(36) NULL, queue_ticket_id BIGINT NULL, version INT, created_at, updated_at`。

约束/索引：

- `UNIQUE uk_appointment_reservation(reservation_id)`：MQ 建单幂等最终防线。
- `UNIQUE uk_appointment_checkin_claim(checkin_claim_id)`：claim 重放绑定同一预约；NULL 可重复。
- `UNIQUE uk_appointment_queue_ticket(queue_ticket_id)`：同一排队票不能被并发/错误事件关联到两张预约；NULL 可重复。
- `INDEX idx_appt_user_date(user_id,service_date,status,id)`：我的预约。
- `INDEX idx_appt_slot_status(slot_id,status,id)`：消费事实/对账。
- `INDEX idx_appt_timeout(status,confirm_deadline,id)`：超时补偿扫描。
- `INDEX idx_appt_outlet_date(outlet_id,service_date,status,id)`：管理查询。

### `active_booking_guard`

`id PK, user_id, item_id, service_date, reservation_id CHAR(36), appointment_id BIGINT, created_at, updated_at`。

- `UNIQUE uk_active_user_item_date(user_id,item_id,service_date)`：同一用户、事项、服务日最多一个有效预约。
- `UNIQUE uk_active_reservation(reservation_id)`；`UNIQUE uk_active_appointment(appointment_id)`。
- 与 appointment_order 在同一事务创建；只有 CAS 成功迁入 `CANCELLED|EXPIRED|COMPLETED|NO_SHOW` 时删除。它是可删除的并发占位，不是审计事实。
- 消费者并发插入唯一冲突时：若 reservationId 相同返回幂等成功；若不同则该 reservation 标记业务拒绝并触发其库存释放，不能覆盖已有预约。

### 可靠性与审计表

| 表 | 关键字段 | 唯一约束/用途 |
| --- | --- | --- |
| appointment_operation_log | id, appointment_id, reservation_id, actor_type/id, operation, from_status, to_status, request_id, occurred_at, detail_json, created_at, updated_at | `INDEX(appointment_id,occurred_at,id)` 与 `INDEX(request_id,id)`，不可变 |
| message_consume_record | id, consumer_name, idempotency_key, event_id, payload_hash, status, first_seen_at, processed_at, created_at, updated_at | `UNIQUE(consumer_name,idempotency_key)` 与 `UNIQUE(consumer_name,event_id)`；同键异载荷告警并拒绝 |
| outbox_event | id, event_id, aggregate_type/id, event_type, event_version, routing_key, payload_json, status, attempts, next_attempt_at, published_at, last_error_code, created_at, updated_at | `UNIQUE(event_id)`；`INDEX(status,next_attempt_at,id)` |
| stock_release_record | id, reservation_id, slot_id, user_id, reason, status, attempts, next_attempt_at, released_at, last_error_code, created_at, updated_at | `UNIQUE(reservation_id)` 保证一次库存返还；`INDEX(status,next_attempt_at,id)` |
| stock_reconciliation_run | id, trigger_type, slot_scope_json, status, started_at, completed_at, actor_id, created_at, updated_at | `INDEX(status,started_at,id)` 运行摘要 |
| stock_reconciliation_detail | id, run_id, slot_id, config_version, configured_total, persisted_consumed, pending_reserved, expected_remaining, actual_remaining, diff, classification, repair_status, before_json, after_json, created_at, updated_at | `UNIQUE(run_id,slot_id)`；审计修复依据 |

`message_consume_record` 与业务变更同一事务提交。失败可重试时不得先永久写 PROCESSED；使用 PROCESSING + 超时接管或仅在成功时插入，并由唯一业务约束兜并发。check-in claim 以 `appointment_id + qr_nonce_hash` 绑定幂等结果；`checked_in_at` CAS 成功后不因 queue 暂时建票失败而回退。

## 5. `civicflow_queue`

### `queue_ticket`

字段：`id PK, appointment_id BIGINT, user_id, outlet_id, item_id, service_date DATE, ticket_no VARCHAR(16), priority SMALLINT DEFAULT 0, status VARCHAR(32), checked_in_at, called_at, serving_at, missed_at, completed_at, called_window_id NULL, work_session_id NULL, call_count INT DEFAULT 0, result_code NULL, version INT, created_at, updated_at`。

约束/索引：

- `UNIQUE uk_ticket_appointment(appointment_id)`：重复/并发签到返回同一票。
- `UNIQUE uk_ticket_display(outlet_id,service_date,ticket_no)`：网点日内可读排队号唯一。
- `INDEX idx_ticket_next(outlet_id,item_id,service_date,status,priority,checked_in_at,id)`：叫下一号；查询按 priority DESC，余项 ASC。
- `INDEX idx_ticket_user(user_id,service_date,status,id)`；`INDEX idx_ticket_window(called_window_id,status,called_at,id)`。

### `window_work_session`

`id PK, window_id, outlet_id, staff_user_id, status, started_at, ended_at, version, created_at, updated_at`。

MySQL 不能用部分唯一索引表达 ACTIVE；新增 `active_window_session_guard(window_id PK, session_id UNIQUE, staff_user_id, created_at, updated_at)`，开启会话时同事务插入，结束时 CAS 后删除。另建 `INDEX(staff_user_id,status,started_at,id)`。

### 可靠性与审计表

- `queue_operation_log`：含 `ticket_id,appointment_id,actor_type/id,operation,from_status,to_status,request_id,occurred_at,detail_json` 与统一时间字段，索引 `(ticket_id,occurred_at,id)`、`(request_id,id)`。
- `message_consume_record`：同 appointment 结构，唯一 `(consumer_name,idempotency_key)` 和 `(consumer_name,event_id)`。
- `outbox_event`：同 appointment 结构；用于 checked-in/serving/completed/no-show 可靠通知。
- `checkin_reconciliation_record`：`appointment_id UNIQUE, ticket_id UNIQUE NULL, claim_id UNIQUE, appointment_status_snapshot, status, attempts, next_attempt_at,last_error_code,created_at,updated_at`，处理 appointment 已 claim 但 queue 尚未建票/尚未回告 ticketId。

## 6. 条件更新模板

所有状态更新由 Mapper 提供显式 SQL，并检查影响行数，例如：

```sql
UPDATE appointment_order
SET status = #{toStatus}, version = version + 1, updated_at = UTC_TIMESTAMP(3)
WHERE id = #{id} AND status = #{fromStatus} AND version = #{version};
```

超时额外带 `confirm_deadline <= UTC_TIMESTAMP(3)`；确认带 `confirm_deadline >= UTC_TIMESTAMP(3)`。领取下一号在 queue 本地事务先 `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1`，再以 `status='WAITING'` 更新；不得持锁调用远程服务或 RabbitMQ。

## 7. 敏感字段策略

- 密码只保存自适应哈希；refresh token/JWT jti/客户端指纹只保存 SHA-256/HMAC 摘要。
- 手机、证件号若首版采集：先做确定性规范化（手机号为 E.164/中国大陆约定格式，证件号为去空白并统一字母大小写），密文用 KMS/信封加密 AES-256-GCM，密文封装 nonce/tag 并单列 key version；等值查询只使用 `HMAC-SHA-256(pepper, normalized value)` 的 32 字节索引。禁止模糊查询、前缀查询、无盐 SHA-256 和可逆“加密后直接比对”。
- `sys_user.mobile_cipher/mobile_hash/mobile_key_version` 必须同空同非空；网点联系电话只需展示、不参与查询，因此仅保存 `contact_phone_cipher/contact_phone_key_version`，不建立哈希索引。未来采集个人证件号时必须以追加迁移新增 `id_card_cipher/id_card_hash/id_card_key_version`，不得借用 JSON 字段。
- 联系电话、个人联系方式按最小权限解密并在响应中脱敏；公共服务网点地址是公开业务主数据，可明文保存。Entity 仅生成 getter/setter，不生成 `toString`；SQL 参数日志、审计 detail 禁止带明文、完整密文或摘要。
- QR nonce 只存 HMAC/SHA-256 摘要；JWS 私钥、JWT 私钥、pepper 不入数据库/仓库/Nacos 明文，来自密钥管理或部署 secret。
- 备份加密、传输 TLS、生产账号最小权限；非生产数据使用脱敏/合成数据。
