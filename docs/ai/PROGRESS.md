# CivicFlow 开发进度

最后更新：2026-09-16

## 阶段清单

- [x] 阶段 0：AI 开发契约与设计初始化
- [x] 阶段 1：Maven 多模块可编译骨架
- [x] 阶段 2：Docker Compose 本地基础设施
- [x] 阶段 3：Flyway DDL、枚举与错误码基线
- [x] 阶段 4：认证与 RBAC
- [x] 阶段 5：资源主数据管理
- [x] 阶段 6：时间段与号源配置
- [ ] 阶段 7：网关路由、双层鉴权与限流
- [ ] 阶段 8：号源预热、Redis Lua 预占/补偿
- [ ] 阶段 9：RabbitMQ 异步建单与查询投影
- [ ] 阶段 10：确认、取消、TTL/DLX 超时
- [ ] 阶段 11：二维码与签到
- [ ] 阶段 12：排队与窗口叫号
- [ ] 阶段 13：库存/签到对账与安全修复
- [ ] 阶段 14：Vue 3 工程与三角色页面
- [ ] 阶段 15：跨服务 E2E 联调
- [ ] 阶段 16：并发、可靠性、安全与发布收口

## 阶段 6 完成内容

- 完成 `civicflow-resource` 号源配置的 controller -> service -> mapper 闭环：ADMIN 日历分页/某日明细、单日创建、最长 31 日批量生成、DRAFT 完整编辑、显式状态转换与 configVersion quota 调整；所有写接口延续 ADMIN RBAC、最长 128 字符 `Idempotency-Key`、事务审计和稳定错误码。
- 单日与批量请求统一校验 Asia/Shanghai 当天至未来 365 天、`endTime > startTime`、releaseAt 早于 slotStart、`checkInStart <= slotStart <= checkInEnd <= slotEnd`；新建仅允许 DRAFT/SCHEDULED，SCHEDULED 的 releaseAt 必须仍在未来。父网点/事项必须启用，且该事项至少绑定网点的一个启用窗口。
- 追加 Flyway `V3__create_resource_slot_control.sql`：`resource_slot_day_lock` 以 `(outlet,item,serviceDate)` 唯一 guard 将精确重复和部分重叠检查串行化；固定网点 -> 事项 -> 业务日锁顺序，跨日更新再按三元组排序，避免“先查再插”和反向锁序。相邻半开区间允许，真正交叠统一返回 `RESOURCE_409_SLOT_OVERLAP`。
- 批量生成把完全相同时段计为 skipped，把交叠/逐日校验问题返回 failed 明细，并用 `resource_slot_batch_result` 绑定管理幂等记录，确保同键同载荷重试返回首次 `{created,skipped,failed[]}`，不因当前数据变化重新解释结果。
- quota 以 configVersion CAS，完整编辑/状态/quota SQL 均带允许状态条件并校验影响行数。已到 releaseAt 或 OPEN/SUSPENDED 后，降额必须有 `consumedHint` 证据且不得低于消费量；证据缺失返回 `RESOURCE_409_CONSUMPTION_UNKNOWN`，低于消费返回 `RESOURCE_409_QUOTA_BELOW_CONSUMED`。
- 新增 `resource_slot_outbox`，SCHEDULED 创建、状态变化和 quota 变化与配置及审计在同一本地事务写入完整 `resource.slot.changed.v1` envelope，包含连续 configVersion，等待后续 publisher 投递 `cf.resource.x`。本阶段没有引入 Redis 客户端、Lua 抢号或直接覆盖库存。
- 同步更新 API、DATABASE、DOMAIN、EVENTS 契约与 H2 隔离 schema；新增 DDL/Entity 契约和集成测试，覆盖创建/查询、相邻与重叠、批量 created/skipped/failed 及结果重放、状态事件、放号后降额保护、outbox payload/configVersion，以及两个线程重叠创建时仅一个成功。

## 阶段 6 验证

- `mvn -pl civicflow-resource -am test`：成功；common 2 个、resource 13 个测试，共 15 个，0 失败、0 错误、0 跳过。
- `mvn verify`：成功；全仓 7 个 reactor 模块全部通过，共 36 个测试，0 失败、0 错误、0 跳过；Spotless、编译、测试、JAR/repackage 均成功。
- `docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yaml config --quiet`：成功。
- `docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yaml ps`：失败；Docker Desktop Linux engine named pipe 仍拒绝连接，因此没有声称 MySQL 8 Flyway 冒烟通过。
- 完成状态名、表名、API 路径、routing key、错误码与 Redis 边界搜索；`git diff --check` 仅报告 Windows 工作树 LF/CRLF 提示时不视为空白错误，提交前再次复核。

## 阶段 6 已知风险/后续

- 本机 Docker engine 不可用，`civicflow_resource` 的真实 MySQL 8 `V1+V2+V3` 空 schema Flyway、JSON 列与 `INSERT IGNORE + SELECT FOR UPDATE` 竞争语义仍需环境恢复后冒烟；H2 MySQL-mode 测试不能替代该验证。
- 本轮只可靠地产生 PENDING transactional outbox；RabbitMQ publisher confirm、失败重试、appointment 消费/版本跳跃回源，以及 Redis 预热/增量 Lua 属于后续阶段，不得把 outbox 行误报为已经投递。`consumedHint` 也要由后续受保护的同步/对账链路维护。
- `/api/v1/user/slots` 可预约投影和 `/internal/v1/resource/slots/**` 快照/预热候选仍按既有 API 契约留给 appointment/库存联调阶段；本轮“日历/某日明细”只开放 ADMIN 配置视图，没有提前暴露库存派生值。
- 下一步进入阶段 7：网关路由、双层鉴权、伪造头清洗与限流；不在网关实现号源业务或 Redis 库存状态机。

## 阶段 5 完成内容

- 完成 `civicflow-resource` 的 controller -> service -> mapper 首版闭环：ADMIN 网点/事项/窗口分页、详情、创建、完整更新、启停、逻辑删除及窗口事项集合替换；USER 可用网点分页/详情与网点可办理事项分页；STAFF 仅能从已验证 JWT `sub` 查看本人获授权的启用网点、窗口与事项，不存在 STAFF 配置写入口。
- Controller 只承担 Bean Validation、JWT 主体适配、方法级 `@PreAuthorize` 与统一响应；资源校验、引用检查、事务、幂等、审计、逻辑删除和 version CAS 均位于 Service；复杂分页、授权 join、未来号源检查和条件更新位于 Mapper XML，用户事项与 STAFF scope 查询避免 N+1。
- resource 增加 Spring Security Resource Server 双层防护，使用 JWKS 本地验签并限制 RS256、issuer、audience、nbf/exp，`roles` 映射方法级 RBAC。新增依赖均由现有 Spring Boot BOM 管理；Spring Security starter/security-test 为 Spring 官方维护、Apache-2.0 许可，未引入独立版本号。
- 管理端所有写接口强制最长 128 字符 `Idempotency-Key`，追加 Flyway `V2__create_resource_admin_control.sql`，以 `(actor,operation,key)` 唯一绑定独立 secret 的 HMAC-SHA-256 载荷摘要和资源 ID；同键同载荷复用结果，同键异载荷返回 409。资源变更在同一事务写白名单 before/after 审计，不记录联系电话、密文、摘要、JWT 或完整请求体。
- 网点联系电话使用 AES-256-GCM、随机 nonce、keyVersion AAD 加密，生产/开发缺 key 启动失败，接口仅返回末四位。网点/事项编码及同网点窗口编码依赖数据库唯一约束兜并发，统一映射 `RESOURCE_409_CODE_EXISTS`。
- 停用按 Asia/Shanghai 当天检查 `SCHEDULED|OPEN|SUSPENDED` 当前/未来号源；窗口通过当前窗口事项关系映射检查。删除额外拒绝活动窗口、窗口事项关系和 STAFF scope 引用，并统一使用 `deleted=id` 逻辑删除。更新/状态/删除均显式 `id + version + deleted=0` CAS；窗口事项集合以窗口 version 控制并发。
- 同步更新 API、DATABASE、DOMAIN、SECURITY 契约；新增 H2 MySQL-mode 隔离 schema 和集成测试，覆盖 RBAC、参数/幂等键、同键异载荷、编码唯一冲突、版本冲突、公开可见性、STAFF scope、未来号源阻止停用、审计，以及两个线程以相同 version 更新时仅一个成功。

## 阶段 5 验证

- `mvn -pl civicflow-resource -am test`：成功；common 2 个、resource 7 个测试，共 9 个，0 失败、0 错误、0 跳过。
- `mvn verify`：成功；全仓 7 个 reactor 模块全部通过，共 30 个测试，0 失败、0 错误、0 跳过；Spotless、编译、测试、JAR/repackage 均成功。
- `docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yaml config --quiet`：成功。
- `docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yaml ps`：失败；Docker Desktop Linux engine named pipe 权限/连接不可用，因此未执行真实 MySQL 8 Flyway 冒烟。
- 完成路径、状态、表名与错误码搜索及 `git diff --check`；未发现契约漂移或空白错误，仅有 Windows 工作树 LF/CRLF 转换提示。

## 阶段 5 已知风险/后续

- 本机 Docker engine 仍不可用，`civicflow_resource` 的 MySQL 8 `V1+V2` 空 schema Flyway、JSON 列、`INSERT IGNORE` 和唯一冲突语义尚未真实冒烟；当前 H2 只验证事务/API/映射行为，不能替代 MySQL 8。
- （阶段 5 结束时记录，已由阶段 6 解决）创建/变更号源必须沿用并明确父资源锁顺序，使“检查未来号源后停用”与并发新建号源串行化；不得退化为无锁先查再写；当时尚无 slot 写 API。
- STAFF scope 本轮只实现按既有授权关系读取；授权关系的管理员维护入口尚未列入提示词 5，未擅自扩展。网关 header 清洗、路由和限流仍在阶段 7。
- （阶段 5 结束时计划，现已完成）下一步进入阶段 6，仅实现时间段与号源配置、重叠防护和批量边界，不提前实现 Redis 预热或预约流程。

## 阶段 4 完成内容

- 完成 `civicflow-auth` 的 controller -> service -> mapper 首版闭环：用户名/手机号 + BCrypt 密码登录、RS256 access JWT、随机不透明 refresh token、轮换/撤销/family 重放处置、当前用户查询，以及 ADMIN 用户分页、创建、启停和 USER/STAFF/ADMIN 角色替换。
- Controller 仅负责 Bean Validation、JWT 主体协议适配、方法级 `@PreAuthorize` 和统一 `ApiResponse`；登录、账号状态、tokenVersion、refresh 生命周期、幂等与角色变更事务均位于 Service；用户/角色/refresh/CAS 查询写入 Mapper，复杂角色 join 和状态更新均在 XML。
- JWT 仅含标准声明、字符串 userId、roles、tokenVersion 和 kid；支持活动私钥 + 多历史公钥的 JWKS 发布与按 kid 本地验签。生产缺少 RSA/手机号保护 key 时启动失败，test profile 才允许临时密钥。
- auth 新增 `spring-boot-starter-security` 与 `spring-boot-starter-oauth2-resource-server`，版本完全由既有 Spring Boot BOM 管理；二者属于 Spring 官方维护、Apache-2.0 许可，是方法级 RBAC、Bearer JWT 验签及 Nimbus JOSE 编解码所需依赖，未引入独立版本号。
- refresh token 数据库仅保存 SHA-256 摘要；手机号使用 AES-256-GCM 密文 + HMAC-SHA-256 等值索引；未知账号仍执行 dummy BCrypt，未知账号/错误密码/禁用/无角色统一返回 `AUTH_401_UNAUTHORIZED`。
- 角色/状态失效与 token 刷新统一采用“先锁用户、再锁 refresh token”的顺序；刷新先无锁定位 userId，取得用户锁后重新锁定并判定 token，避免反向锁序死锁且不依赖初次读取结果裁决。
- 新增追加式 Flyway `V2__create_auth_admin_idempotency.sql`，以 `(actor,operation,Idempotency-Key)` 唯一绑定 payload hash 和目标用户；新增 `V3__create_auth_security_audit.sql`，使管理员创建/状态/角色 before-after 及 refresh 重放处置审计与业务事务同提交。同键同载荷复用结果，同键异载荷返回 409。同步更新 API、SECURITY、DATABASE、DOMAIN。
- 增加 H2 MySQL-mode 的独立内存 schema 集成测试（不连接/污染开发库），覆盖登录成功、手机号登录、错误密码/未知/禁用统一失败、当前用户、JWT 最小声明、JWKS 无私钥、refresh 轮换与重放、family 撤销、重复登出、普通用户越权、管理员创建/重复幂等/分配角色/禁用；另有手机号加密单测和 DDL/Entity 契约测试。

## 阶段 4 验证

- `mvn -pl civicflow-auth -am test`：成功；common + auth 共 14 个测试，0 失败、0 错误、0 跳过，其中 auth 12 个。
- `mvn -pl civicflow-auth -am -DskipTests compile`：成功。
- `mvn -pl civicflow-auth -am verify`：成功；Spotless、编译、common 2 个测试、auth 12 个测试、JAR/repackage 全部通过，0 失败、0 错误、0 跳过。
- `mvn verify`：成功；全仓 7 个 reactor 模块全部通过，共 27 个测试，0 失败、0 错误、0 跳过，确认 common 分页字段统一为契约中的 `items` 后未破坏其他模块。
- `docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yaml ps`：失败；Docker Desktop Linux engine named pipe 不存在，因此未执行真实 MySQL Flyway 冒烟。

## 阶段 4 已知风险/后续

- 本机 Docker engine 仍不可用，因此本轮集成测试使用每次测试上下文初始化的 H2 MySQL-mode 隔离 schema；它覆盖事务/RBAC/API 行为，但不能代替 MySQL 8 对 `V1+V2` 的最终 Flyway 冒烟。Docker 恢复后仍需补跑全新 `civicflow_auth` schema。
- 网关 JWT 校验、限流、header 清洗与前端 cookie/CSRF 接入明确不在本轮；本轮仅提供 JWKS 和统一验证配置能力。
- 首个生产 ADMIN 仍需由部署初始化流程以合成/受控凭据安全预置；仓库不提交默认管理员密码。管理员安全审计表与 MFA/KMS 供应商仍属于后续安全收口。

## 阶段 3 历史完成内容

- 四个业务服务各新增一个首次 Flyway 迁移 `V1`，共创建 25 张表：auth 4、resource 6、appointment 8、queue 7。仓库此前没有迁移文件，因此没有改写已发布版本；后续结构变更只能追加 `V2+`。
- auth 创建用户、角色、用户角色、refresh token 表和 USER/STAFF/ADMIN 种子；resource 创建网点、事项、窗口、窗口事项关系、人员窗口授权、号源表；appointment/queue 同步创建 guard、操作日志、消费去重、outbox、库存释放/对账、签到补偿等已确认可靠性表。
- 所有表统一使用 UTC `DATETIME(3)` 的 `created_at/updated_at`；可变聚合使用 `version`。配置主数据/关系用 `deleted=0|id` 支持多次删除重建，订单和可靠性事实不做逻辑删除，guard 行按 ADR 在本地事务中物理创建/删除。
- 为 25 张表建立一一对应的 MyBatis-Plus Entity。所有状态/操作/原因字段使用服务内 Java enum，配置及字段映射显式采用 `EnumTypeHandler` 按名称持久化，不使用 ordinal/魔法数字。
- 新增 auth/resource/appointment/queue 服务级错误码枚举，并把 common 成功/错误码改为 `API.md` 已确认的 `OK`、`COMMON_...` 格式。没有创建或修改 Controller 业务接口。
- 手机号只保存 AES-GCM 信封密文、密钥版本与 HMAC-SHA-256 等值索引；refresh token、客户端指纹、QR nonce 只保存摘要。网点联系电话只保存密文与密钥版本，不支持检索。Entity 使用 Lombok getter/setter 且不生成 `toString`，避免默认打印敏感字段。
- 新增 Lombok 编译期依赖以避免 25 个纯字段映射 Entity 的机械访问器；版本由 Spring Boot BOM 集中管理，依赖为 MIT License，不进入运行时制品。
- 新增四个数据库契约测试，自动比较每张表 DDL 列集合与 Entity 字段集合，并锁定关键唯一键、查询索引、敏感字段形态与稳定错误码。
- 按最终 DDL 同步更新 `DATABASE.md` 和 `DOMAIN.md`，补齐逻辑删除、敏感字段、技术状态枚举、可靠性表和跨服务状态快照约定。

## 关键唯一约束与防止的并发问题

### auth

- `uk_user_username(username,deleted)`、`uk_user_mobile_hash(mobile_hash,deleted)`：防止并发创建两个有效的同名/同手机号账号；删除标记写行 ID 后允许以后重建，不与多条历史记录碰撞。
- `uk_role_code(role_code)`：防止并发初始化或管理操作创建重复角色代码。
- `uk_user_role(user_id,role_id,deleted)`：防止并发重复授权；历史授权删除后可重新授予。
- `uk_refresh_hash(token_hash)`：防止同一 refresh token 摘要被并发写成两条会话事实，保证轮换/重放检测定位唯一记录。
- `uk_auth_admin_idempotency(actor_user_id,operation,idempotency_key)`：防止管理员写操作因并发/超时重试重复执行，并以 payload hash 拒绝同键异载荷。

### resource

- `uk_outlet_code`、`uk_item_code`、`uk_window_outlet_code`：防止并发创建两个有效的同编码网点、事项或同网点窗口。
- `uk_window_item(window_id,item_id,deleted)`：防止并发重复绑定窗口与事项。
- `uk_staff_window_scope(staff_user_id,outlet_id,window_id,deleted)`：防止同一人员窗口授权重复；`window_id=0` 消除 NULL 在唯一索引中可重复的漏洞。
- `uk_slot_exact(outlet_id,item_id,service_date,start_time,end_time,deleted)`：防止完全相同号源时段的并发重复创建；非完全相同但重叠的时段仍必须由 Service 锁定同业务日范围后检查并返回 `RESOURCE_409_SLOT_OVERLAP`。

### appointment

- `uk_appointment_reservation(reservation_id)`：防止 MQ 重投或并发消费者为同一 reservation 建两张订单。
- `uk_appointment_checkin_claim(checkin_claim_id)`、`uk_appointment_queue_ticket(queue_ticket_id)`：防止签到 claim 重放和同一排队票被错误并发关联到多张预约。
- `uk_active_user_item_date(user_id,item_id,service_date)`：数据库最终防线，防止同一用户同事项同服务日出现两个有效预约；`uk_active_reservation`、`uk_active_appointment` 防止同一 reservation/订单拥有多个 guard。
- `uk_message_consumer_idempotency`、`uk_message_consumer_event`：防止相同业务幂等键或 eventId 的至少一次消息被重复执行；payload hash 用于识别同键异载荷。
- `uk_outbox_event(event_id)`：防止同一事件被并发写入多个待发布任务。
- `uk_stock_release_reservation(reservation_id)`：防止取消、超时、发布失败或对账竞态对同一次预占返还两次库存。
- `uk_reconciliation_run_slot(run_id,slot_id)`：防止同一次对账并发生成同一 slot 的重复修复明细。

### queue

- `uk_ticket_appointment(appointment_id)`：防止同步签到与 claimed 补偿事件并发创建两张排队票。
- `uk_ticket_display(outlet_id,service_date,ticket_no)`：防止并发发号生成重复的网点日内展示号。
- `active_window_session_guard.window_id PK`：防止一个窗口并发开启两个 ACTIVE 会话；`uk_active_session(session_id)` 防止同一会话占据多个窗口。
- queue 的 `uk_message_consumer_idempotency`、`uk_message_consumer_event` 与 `uk_outbox_event`：分别防止消费副作用重放和可靠事件重复建任务。
- `uk_checkin_reconciliation_appointment`、`uk_checkin_reconciliation_claim`、`uk_checkin_reconciliation_ticket`：防止同一预约、claim 或排队票在同步/异步补偿竞态中产生多条未决修复事实。

## 阶段 3 历史验证

- `mvn spotless:apply`：成功；新增 Java 文件已按仓库格式规范化。
- `mvn -pl civicflow-auth,civicflow-resource,civicflow-appointment,civicflow-queue -am test`：成功；reactor 6 个项目全部成功，共 18 个测试，0 失败、0 错误、0 跳过。四个业务模块各执行 1 个应用上下文测试和 3 个数据库契约测试。
- `mvn verify`：成功；全仓 7 个项目构建、测试和打包成功，共 19 个测试，0 失败、0 错误、0 跳过。
- 契约测试已确认 25 张表的 DDL 列与 Entity 字段逐表相等，并确认关键唯一键/高频索引及服务错误码常量存在。
- `docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yaml ps`：失败，Docker Desktop Linux engine 的 named pipe 不存在；本机也未发现 `mysql/mysqld`，因此本轮无法对四个真实 MySQL 8.4 空 schema 执行 Flyway migration。没有用 H2 结果冒充 MySQL DDL 验证，也没有操作任何已有卷。

## 已知待确认/风险

- Docker engine 可用后仍必须用四个 migration 账号对全新 schema 逐一启动 Flyway，并检查 `flyway_schema_history`、表/索引、角色种子及第二次启动无待执行迁移；当前只完成静态 DDL/映射契约验证。
- 产品需确认 CONFIRMED 预约允许用户取消的截止规则；当前契约采用“签到前且取消截止前可取消”，截止时长尚未定。
- 产品需确认 STAFF 过号后是否允许人工回队；当前首版 MISSED/NO_SHOW 为终态。
- 上线前需确定实名字段、数据保留期限、管理员 MFA、KMS/secret 与服务间 mTLS 方案。当前未创建证件号列；若产品确认采集，必须追加迁移并使用 cipher/hash/key-version 三列模式。
- （阶段 3 结束时风险，已由阶段 6 解决）`resource_slot` 的普通唯一索引只能阻止完全相同时段；重叠区间必须在 Service 实现中锁定 `(outlet,item,date)` 后检查，不能退化为“先查再插”。
- MyBatis 在 mapper 尚未进入实现阶段时仍会报告“未发现 Mapper”警告，符合本轮不创建空接口/Controller 的范围。
- Redis `{itemId:serviceDate}` 热点分片阈值仍需由压测确定。

## 下一阶段

Docker engine 可用时先补做 auth `V1+V2+V3` 及其余三个全新 MySQL schema 的 Flyway 冒烟并记录结果；随后进入阶段 5，只实现 resource 主数据管理，不提前实现预约或排队业务流程。
