# 智序（CivicFlow）AI 分阶段开发提示词

> 适用场景：使用 Codex、Claude Code、Cursor 等代码代理，在同一 Git 仓库中逐步完成项目。
>
> 核心方法：仓库即记忆。第 0 阶段把稳定信息写入 `AGENTS.md` 和 `docs/ai/`；以后每个新会话只执行一个阶段，并让 AI 先读取相关文档和现有代码。

## 一、推荐执行方式

1. 建立空仓库后，先执行“提示词 0”。
2. 每完成一个阶段，提交一次 Git，例如 `feat(resource): complete outlet and item management`。
3. 下一阶段建议开启新会话，只粘贴对应提示词。不要重复粘贴整份项目需求。
4. AI 每轮必须更新 `docs/ai/PROGRESS.md`，记录已完成内容、验证结果、遗留问题和下一步。
5. 单轮只实现一个可验收目标。若一个阶段仍然过大，使用文末的“单功能实现模板”进一步拆分。
6. 不要让 AI 一次生成全部服务。先契约、再基础设施、再按业务纵向打通。

## 二、全局架构约束

这些约束应由提示词 0 写入仓库，后续不必每次重复。

- 项目名：智序（CivicFlow）。
- 后端：Java 17、Spring Boot 3、Spring Cloud Gateway、Spring Cloud Alibaba、Nacos、OpenFeign、Sentinel、MySQL、MyBatis-Plus、Redis、Lua、RabbitMQ、JWT、Spring Security。
- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios。
- 首版模块：`civicflow-common`、`civicflow-gateway`、`civicflow-auth`、`civicflow-resource`、`civicflow-appointment`、`civicflow-queue`、`civicflow-web`。通知服务后续实现。
- Maven 多模块单仓库。每个微服务独立数据库或独立 schema，禁止跨服务直接访问对方数据表。
- 服务内部使用三层架构：`controller -> service -> mapper`。允许设置 `service.impl`，但不引入 DDD 聚合、Repository、UseCase 等额外架构层。
- Controller 仅处理协议适配、参数校验、权限入口和响应封装，不写业务规则，不直接调用 Mapper。
- Service 承担业务编排、状态机、事务和幂等；事务边界位于 Service。
- Mapper 使用 MyBatis-Plus；普通 CRUD 使用 `BaseMapper`，复杂查询使用 XML。禁止在 Controller 拼 SQL。
- DTO 与 Entity 分离；请求 DTO、响应 DTO、Entity 不得混用。禁止把数据库实体直接返回给前端。
- 跨服务只通过 OpenFeign 或消息事件协作。禁止分布式数据库事务，采用本地事务、幂等消费、状态机和补偿保证最终一致性。
- API 统一前缀 `/api`；管理端 `/api/admin/**`，窗口端 `/api/staff/**`，用户端 `/api/user/**`。
- 所有金额之外的业务时间统一使用 `LocalDate`、`LocalTime`、`LocalDateTime`；数据库保存北京时间业务语义，接口使用 ISO-8601。
- ID 使用雪花算法或 MyBatis-Plus `ASSIGN_ID`，JSON 中的 Long 类型序列化为字符串，防止 JavaScript 精度丢失。
- 数据库迁移使用 Flyway；生产环境禁用自动建表。
- 所有状态字段使用明确枚举；禁止用魔法数字或散落字符串。
- 所有写接口考虑鉴权、参数校验、幂等、并发、审计和异常映射。
- 日志不得输出密码、完整身份证号、完整手机号、JWT、二维码签名密钥等敏感信息。
- 不允许伪造“测试已通过”。执行过什么命令就报告什么命令；无法执行时说明原因。

## 三、提示词 0：初始化 AI 开发契约与项目文档

将下面内容作为第一个会话的完整提示词：

```text
你是一名资深 Java 微服务架构师和全栈工程师。请在当前空仓库中为“智序（CivicFlow）——城市公共服务预约排队平台”建立可持续的 AI 开发契约和设计文档。本轮只做设计文档、目录规划和决策，不生成业务实现代码。

产品角色：
1. 普通用户：预约、取消、确认、签到、查看排队进度。
2. 窗口人员：登录工作台、叫号、重呼、过号、开始办理、完成业务。
3. 管理员：管理网点、事项、窗口、时间段、号源，并查看号源对账结果。

核心链路：管理员配置号源 -> 定时预热 Redis -> 用户抢号 -> 限流与防重复 -> Redis Lua 原子扣减 -> RabbitMQ 异步创建预约单 -> 5 分钟内确认 -> 生成签到二维码；超时则自动取消并释放号源；到店后签到、排队、叫号。

首版模块：
- civicflow-common：统一响应、异常、工具、通用安全模型。
- civicflow-gateway：路由、JWT 前置鉴权、限流、请求追踪。
- civicflow-auth：登录、用户、角色、JWT 签发与刷新。
- civicflow-resource：网点、事项、窗口、排班时间段、号源配置。
- civicflow-appointment：抢号、预约单、确认、取消、超时释放、号源对账。
- civicflow-queue：签到、排队、叫号、过号、办理完成。
- civicflow-web：Vue 3 用户端、窗口端、管理端。

硬性架构：Java 17、Spring Boot 3、Spring Cloud Gateway、Nacos、OpenFeign、Sentinel、MySQL、MyBatis-Plus、Redis + Lua、RabbitMQ、JWT + Spring Security、Flyway、Docker Compose；前端 Vue 3 + TypeScript + Vite + Element Plus。采用 Maven 多模块单仓库。服务内部必须采用 controller -> service -> mapper 三层架构，可使用 service.impl；DTO、VO、Entity 分离；禁止引入 DDD、Repository 或跨库查询。

请先检查当前仓库，避免覆盖已有文件。然后创建：
1. 根目录 AGENTS.md：记录稳定技术约束、分层边界、编码规范、验证要求和 AI 工作规则。
2. docs/ai/PROJECT.md：产品范围、角色、核心用例、首版范围和非目标。
3. docs/ai/ARCHITECTURE.md：模块职责、依赖方向、同步调用、异步事件、故障与补偿策略。
4. docs/ai/DOMAIN.md：核心实体、关键字段、枚举、预约状态机、排队状态机和非法状态转换。
5. docs/ai/DATABASE.md：按服务划分的数据表草案、主键、唯一约束、索引和敏感字段策略。
6. docs/ai/API.md：首版 REST API 清单，列出方法、路径、角色、幂等要求、主要请求/响应和错误码。
7. docs/ai/EVENTS.md：RabbitMQ exchange、queue、routing key、消息体、幂等键、重试、死信和补偿规则。
8. docs/ai/REDIS.md：Key 命名、数据类型、TTL、Lua 输入输出、原子性边界和缓存一致性策略。
9. docs/ai/SECURITY.md：JWT、RBAC、网关与服务双层防护、二维码签名、数据脱敏、越权防护。
10. docs/ai/TESTING.md：单元、集成、并发、状态机、消息幂等、权限和端到端测试矩阵。
11. docs/ai/DECISIONS.md：初始 ADR，包含版本兼容策略、三层架构、数据库隔离、最终一致性和超时方案。
12. docs/ai/PROGRESS.md：阶段清单，当前只标记设计初始化完成。

关键设计必须明确：
- 抢号请求由应用生成 reservationId，并由 Lua 原子检查库存、防重复预约、扣减库存、写入短期预占记录。
- Redis 成功后发布 RabbitMQ；启用 publisher confirm。发布最终失败时执行幂等补偿 Lua，返还库存并删除用户占用键。
- 预约创建消息至少一次投递；消费者按 reservationId 幂等，数据库必须有唯一约束。
- 预约状态至少包含 CREATING、PENDING_CONFIRM、CONFIRMED、CANCELLED、EXPIRED、CHECKED_IN、SERVING、COMPLETED、NO_SHOW；请判断 CREATING 是查询投影视图还是持久化状态并写入 ADR。
- PENDING_CONFIRM 仅有 5 分钟。采用 RabbitMQ TTL + DLX 或可靠延迟消息实现超时检查，超时消费者必须使用数据库条件更新完成状态 CAS，再执行幂等库存释放。
- 不能依靠“先查再改”处理并发；数据库更新必须带原状态条件，Redis 操作必须由 Lua 保证原子性。
- 同一用户、同一事项、同一服务日期只能存在一个有效预约。Redis 做前置拦截，数据库用有效预约占位表或其他可落地唯一约束做最终防线。
- 号源配置以 resource 数据库为配置事实源；Redis 是运行时可用库存；预约单是消费事实；对账任务能检测并修复差异。
- 签到二维码不得只包含 appointmentId，必须是短期有效、可校验签名、包含随机 nonce 的令牌；签到接口还要做预约归属、状态、日期、网点和重复签到校验。
- 窗口叫号必须考虑多个窗口并发，领取下一号需用数据库锁或 Redis Lua 保证同一排队票不被重复领取。

版本要求：不要凭印象随意组合依赖。为 Java 17 选择相互兼容的 Spring Boot 3、Spring Cloud、Spring Cloud Alibaba BOM，并在 DECISIONS.md 记录选择依据和替换方式；使用属性集中管理版本，不散落硬编码。

成功标准：文档之间无明显矛盾；每个服务边界明确；状态机、幂等键、唯一约束、Redis Key 和消息补偿能首尾对应；后续 AI 可以只读这些文件继续实现。

完成后只汇报：新增/修改文件、最重要的架构决策、仍需确认的问题、下一阶段建议。不要在聊天中重复全文。
```

## 四、提示词 1：创建 Maven 多模块骨架

```text
请实现 CivicFlow 的 Maven 多模块后端骨架。本轮只完成可编译骨架和公共约定，不实现具体业务。

开始前读取：AGENTS.md、docs/ai/PROJECT.md、ARCHITECTURE.md、DECISIONS.md、PROGRESS.md。检查仓库现状并保留已有正确内容。

目标：创建父 pom 和 civicflow-common、civicflow-gateway、civicflow-auth、civicflow-resource、civicflow-appointment、civicflow-queue 六个后端模块。为每个可运行服务建立启动类、最小配置、dev/test profile 和健康检查。使用 BOM 管理兼容版本。

要求：
- 包名统一为 com.civicflow。
- 业务服务建立 controller、service、service.impl、mapper、entity、dto/request、dto/response、convert、config 包；仅放必要占位文件，不生成空接口海洋。
- common 建立统一响应 ApiResponse、分页响应 PageResponse、业务异常 BusinessException、全局错误码接口和通用异常模型。不要把各服务的业务错误码全部塞进 common。
- 建立 Checkstyle 或 Spotless 中的一种，并配置 Maven 编译、测试插件。
- 各服务配置使用环境变量占位，不提交真实密码。
- 增加 `.gitignore`、根 README 的启动说明和环境变量清单。
- 不要在 common 中引入 Web、MyBatis、Security 等所有服务都不需要的重依赖；按职责拆分依赖。

验证：执行父项目编译和测试；若依赖下载或外部环境导致失败，定位到具体原因。更新 docs/ai/PROGRESS.md。

停止条件：所有模块可被 Maven 正确识别且基础测试通过。本轮不要创建业务表、业务接口或前端。
```

## 五、提示词 2：Docker Compose 与本地开发基础设施

```text
请为 CivicFlow 实现本地开发基础设施。本轮只处理 Docker Compose、初始化配置和连通性文档。

读取 AGENTS.md、docs/ai/ARCHITECTURE.md、DATABASE.md、EVENTS.md、REDIS.md、DECISIONS.md、PROGRESS.md，以及现有 pom/config。

目标：用 docker-compose 启动 MySQL、Redis、RabbitMQ（带管理界面）和 Nacos。为 auth、resource、appointment、queue 提供独立数据库或 schema，并提供最小权限账号方案。增加健康检查、持久化卷、网络和示例 `.env.example`。

要求：
- 不把真实密码写入 Git；开发默认值只能放 `.env.example` 并明确仅用于本地。
- MySQL 使用 utf8mb4 和明确时区。
- Redis 开启密码配置示例。
- RabbitMQ 预留 exchange/queue 由应用声明，不依赖手工点击控制台。
- Nacos 配置按服务和环境命名，说明哪些配置属于本地文件、哪些可迁移到 Nacos。
- 增加 `docs/LOCAL_DEVELOPMENT.md`，给出 Windows PowerShell 和通用 shell 的启动、健康检查、停止方法。

验证 docker compose 配置语法；若环境允许，启动并检查容器健康状态，但不要删除用户已有卷。更新 PROGRESS.md。
```

## 六、提示词 3：数据库 DDL、枚举与错误码基线

```text
请根据已确认的设计，为四个业务服务创建 Flyway 数据库迁移、Java 枚举和服务级错误码。本轮不写 Controller 业务接口。

读取 AGENTS.md、docs/ai/DOMAIN.md、DATABASE.md、API.md、SECURITY.md、DECISIONS.md、PROGRESS.md。

至少覆盖：
- auth：sys_user、sys_role、sys_user_role、refresh_token 或等效表。
- resource：service_outlet、service_item、service_window、window_item_rel、resource_slot。
- appointment：appointment_order、active_booking_guard、appointment_operation_log、message_consume_record、stock_compensation_record 或文档中的等效设计。
- queue：queue_ticket、queue_operation_log、window_work_session 或文档中的等效设计。

要求：
- 每张表包含必要的创建/更新时间、逻辑删除或状态字段；审计字段规则统一。
- 为所有业务唯一性和高频查询建立真实可用的唯一索引/联合索引。
- 明确手机号、证件号等敏感字段的存储、加密/散列和查询策略。
- Entity 与表字段一一对应；状态使用枚举和 MyBatis 类型处理，不在代码中散落数字。
- Flyway 脚本可重复在全新数据库上执行；迁移文件一旦已有版本不得随意改写，应追加新版本。
- 根据最终 DDL 同步修订 DATABASE.md 和 DOMAIN.md，避免文档漂移。

验证：运行相关模块测试；环境允许时对空数据库执行 Flyway migration。更新 PROGRESS.md，并列出每个关键唯一约束防止的具体并发问题。
```

## 七、提示词 4：认证服务 civicflow-auth

```text
请完整实现 civicflow-auth 的首版认证闭环，严格使用 controller -> service -> mapper 三层架构。

读取 AGENTS.md、docs/ai/API.md、DATABASE.md、SECURITY.md、DOMAIN.md、PROGRESS.md，并检查 auth 模块现状。

本轮范围：
- 用户名/手机号 + 密码登录。
- Access Token 与 Refresh Token。
- 当前用户信息查询。
- 管理员创建/禁用用户、分配角色。
- USER、STAFF、ADMIN 三类角色授权。
- 密码使用 BCrypt；Refresh Token 可撤销、可轮换，数据库只保存其安全摘要。
- 提供给网关/其他服务使用的 JWT 公钥或统一校验配置；优先使用非对称签名，并支持密钥轮换方案。

要求：
- Controller 只做 DTO 校验、调用 Service、返回统一响应。
- Service 处理登录失败、禁用账号、令牌刷新、撤销与角色变更事务。
- Mapper 仅负责数据访问；复杂角色查询写 XML。
- JWT 中只放必要的 userId、roles、tokenVersion/必要声明，不放手机号、证件号。
- 失败响应不泄露“账号存在但密码错误”等可枚举信息。
- 管理员接口必须做方法级权限校验。
- 提供单元测试和带 Testcontainers 或等效隔离环境的集成测试，覆盖成功、错误密码、禁用、刷新、重复撤销和越权。

同步更新 API.md、SECURITY.md、PROGRESS.md。运行 auth 相关测试并报告结果。本轮不要实现网关逻辑和前端页面。
```

## 八、提示词 5：资源服务基础管理

```text
请实现 civicflow-resource 的网点、事项、窗口管理功能，严格采用 controller -> service -> mapper。

读取 AGENTS.md、docs/ai/DOMAIN.md、DATABASE.md、API.md、SECURITY.md、PROGRESS.md，并检查现有 resource 代码和迁移。

本轮范围：
- 管理员：网点 CRUD/启停、事项 CRUD/启停、窗口 CRUD/启停、窗口与事项绑定。
- 用户：分页查询可用网点、网点详情、网点下可预约事项。
- 窗口人员只能查看被授权网点和窗口，不允许修改资源配置。

业务规则：
- 停用网点前检查是否有未来有效号源；默认拒绝并返回明确错误。
- 停用事项或窗口时检查关联关系和未来排班。
- 网点编码、事项编码在合理业务范围内唯一。
- 删除优先使用逻辑删除；存在业务引用时不得物理删除。
- 列表接口必须分页，支持必要的状态、关键词和网点条件，避免无界查询。

要求：DTO/Entity 分离，使用 Bean Validation；Service 中完成校验和事务；Mapper 查询避免 N+1。补充权限、参数、唯一冲突和并发更新测试。若需要乐观锁，明确 version 字段和冲突响应。

更新 API.md 和 PROGRESS.md，运行 resource 模块测试。
```

## 九、提示词 6：时间段与号源配置

```text
请实现 civicflow-resource 的时间段和号源配置，不涉及抢号。

读取 AGENTS.md、docs/ai/DOMAIN.md、DATABASE.md、API.md、REDIS.md、PROGRESS.md，以及 resource 模块现有代码。

本轮范围：
- 管理员按网点、事项、服务日期、开始时间、结束时间配置 resource_slot。
- 支持单日创建和按日期范围批量生成，但必须防止重复和重叠。
- 支持设置 totalQuota、releaseTime、checkInStart、checkInEnd、status。
- 支持查询日历、某日号源明细、启用、停用和调整总量。

关键规则：
- endTime > startTime；服务日期不能早于允许配置日期；releaseTime 必须合理。
- 同一网点 + 事项 + 日期 + 时间段不得重复；重叠策略必须与 DOMAIN.md 一致。
- 已开始放号或已有预约后，不允许直接把总量调到已消费量以下。
- 已放号后的增量调整要产生可供 appointment 服务同步的领域事件或明确的管理接口；不得静默覆盖 Redis 库存。
- 批量创建要有数量上限，并返回成功、跳过、失败明细。

实现三层代码、Flyway 增量迁移、测试和接口文档。不要在 resource 服务直接实现 Redis 抢号。更新 PROGRESS.md。
```

## 十、提示词 7：网关鉴权、路由与限流

```text
请实现 civicflow-gateway 的路由、JWT 鉴权、身份透传和基础限流。

读取 AGENTS.md、docs/ai/ARCHITECTURE.md、API.md、SECURITY.md、DECISIONS.md、PROGRESS.md，以及 auth 服务令牌实现。

目标：
- 配置 auth、resource、appointment、queue 路由和 Nacos 服务发现。
- 白名单仅包含登录、刷新令牌、健康检查和明确的公开查询接口。
- 网关校验 JWT 签名、过期时间、issuer、audience、tokenVersion/撤销策略。
- 向下游透传可信的 X-User-Id、X-User-Roles、X-Request-Id；先删除客户端伪造的同名头。
- 按 IP 对登录接口限流，按 userId + slotId 对抢号接口限流；使用 Sentinel 或 Redis 限流，并定义降级响应。
- 统一处理 401、403、429 和网关异常。

安全要求：下游服务仍保留方法级权限检查，不能只信任公网可伪造 Header；设计服务间可信边界或内部签名。CORS 使用配置化白名单，不允许凭证模式下使用通配符。日志脱敏。

测试覆盖伪造头、过期令牌、角色不匹配、白名单、限流和路由。更新 SECURITY.md、API.md、PROGRESS.md，并执行 gateway 测试。
```

## 十一、提示词 8：号源预热与 Redis Lua

```text
请在 civicflow-appointment 中实现号源预热、Redis Key 管理和 Lua 原子预占能力。本轮暂不创建 RabbitMQ 预约单消费链路。

读取 AGENTS.md、docs/ai/ARCHITECTURE.md、DOMAIN.md、API.md、REDIS.md、EVENTS.md、PROGRESS.md，以及 resource 的号源接口。

范围：
- 通过 OpenFeign 从 resource 获取即将放号的 slot 快照。
- Spring Task 定时预热未来窗口内的号源；接口和时间窗口配置化。
- 提供管理员受保护的单 slot 重建/预热接口。
- Lua 脚本原子完成：校验 slot 存在、已到 releaseTime、未停售、剩余库存 > 0、用户当天同事项无有效预约；扣减库存；写 reservationId 预占记录；写用户防重复键；设置合理 TTL。
- 实现对应的幂等补偿 Lua：只有 reservationId 与当前占用一致时才返还一次库存并删除关联键。
- Lua 返回结构化业务结果码，Java 映射为明确异常，不依赖字符串解析。

Key 必须包含版本前缀和环境/业务维度，避免 big key。不要用分布式锁包围 Lua。预热必须幂等，不能覆盖已经发生的扣减；对“首次初始化”和“运行中重建”分别设计安全策略。

测试使用真实 Redis 容器或等效集成环境，覆盖 100 个并发请求只成功 quota 个、同用户重复请求、未到放号时间、库存为 0、重复补偿、错误 reservationId 不补偿。

同步更新 REDIS.md、API.md、PROGRESS.md，报告并发测试结果。
```

## 十二、提示词 9：抢号 API、RabbitMQ 异步建单与查询投影

```text
请打通 civicflow-appointment 的抢号主链路：API -> Lua 预占 -> RabbitMQ -> 幂等创建预约单 -> 客户端查询结果。

读取 AGENTS.md、docs/ai/ARCHITECTURE.md、DOMAIN.md、DATABASE.md、API.md、EVENTS.md、REDIS.md、PROGRESS.md，以及已有 Lua 与预约表。

实现：
1. POST /api/user/appointments/reservations：请求包含 slotId 和客户端幂等键；应用生成 reservationId，调用 Lua 预占。
2. 预占成功后发布 appointment.create 消息，启用 mandatory、publisher confirm/return 和 correlation data。
3. 确认发布最终失败时调用补偿 Lua；补偿本身必须幂等，并记录可审计结果。
4. 消费者在本地事务内按 reservationId 幂等创建 PENDING_CONFIRM 预约单，写 5 分钟确认截止时间，并建立用户有效预约数据库唯一防线。
5. 若数据库唯一冲突或业务校验失败，发送/执行可重试的库存补偿，不能吞掉异常。
6. GET /api/user/appointments/reservations/{reservationId} 返回 CREATING、PENDING_CONFIRM 或失败结果，使前端可以轮询异步建单状态。
7. GET /api/user/appointments 和详情接口只能访问本人数据。

消息要求：定义 schemaVersion、eventId、reservationId、occurredAt、traceId 和必要快照；消费者手动确认；区分可重试异常与永久业务失败；有限重试后进入死信队列；消费幂等必须由数据库唯一约束支撑。

不要声称 Redis + MQ 形成原子事务。把所有故障窗口及恢复路径写进 EVENTS.md。测试覆盖重复 HTTP 请求、重复 MQ 消息、发布失败补偿、消费者崩溃重投、数据库唯一冲突和越权查询。

更新文档与 PROGRESS.md，执行 appointment 测试。本轮不实现确认和超时。
```

## 十三、提示词 10：预约确认、取消、超时与库存释放

```text
请实现预约确认、主动取消、超时关闭和库存释放完整状态机。

读取 AGENTS.md、docs/ai/DOMAIN.md、DATABASE.md、API.md、EVENTS.md、REDIS.md、SECURITY.md、PROGRESS.md，以及 appointment 现有代码。

范围：
- 用户在 confirmDeadline 前确认 PENDING_CONFIRM 预约。
- 用户主动取消允许取消的预约。
- 创建预约单后投递 5 分钟超时检查消息，使用 RabbitMQ TTL + DLX 或项目 ADR 已选择的可靠方案。
- 超时消费者执行 `UPDATE ... WHERE status = PENDING_CONFIRM AND confirm_deadline <= now`，只有更新成功者才释放库存。
- 确认与超时并发时，依靠数据库条件更新保证只有一个状态转换成功。
- 取消/超时后删除 active_booking_guard，并执行幂等库存补偿。
- 库存释放失败写入补偿记录，由定时任务重试；禁止把订单状态回滚为未取消。
- 每次状态变化写 operation_log，包括操作者类型、操作者 ID、原状态、新状态、原因、traceId。

接口应支持 Idempotency-Key；重复确认返回当前确认结果，重复取消返回当前终态，不重复释放库存。明确 CONFIRMED 后允许取消的截止规则，并按 DOMAIN.md 实现。

测试覆盖：确认与超时竞态、取消与超时竞态、重复超时消息、重复取消、Redis 临时不可用后的补偿重试、非法状态转换、非本人操作。

更新 DOMAIN.md、API.md、EVENTS.md、PROGRESS.md，并运行测试。
```

## 十四、提示词 11：签到二维码与签到

```text
请实现预约确认后的签到二维码令牌，以及 civicflow-queue 的签到闭环。

读取 AGENTS.md、docs/ai/ARCHITECTURE.md、DOMAIN.md、API.md、SECURITY.md、DATABASE.md、EVENTS.md、PROGRESS.md，并检查 appointment 与 queue 模块。

范围：
- appointment 为 CONFIRMED 预约生成短期有效签到 token；载荷至少包含 appointmentId、userId、outletId、nonce、issuedAt、expiresAt、purpose，使用独立密钥/密钥对签名。
- 二维码接口返回 token 或二维码内容，不在后端仓库保存图片文件。
- queue 提供扫码签到和用户自助签到接口。
- queue 通过可信 OpenFeign 接口向 appointment 校验预约存在、归属、状态、服务日期、网点、签到时间窗口和 token nonce；完成签到后通知 appointment 做 CONFIRMED -> CHECKED_IN 的幂等条件更新。
- 创建唯一 queue_ticket 和可读排队号，例如 A023；同一 appointment 只能有一个排队票。
- 重复签到返回原排队票，不重复取号。

安全：防止篡改、跨网点使用、过期重放、枚举 appointmentId 和窗口人员越权。nonce 的一次性/重复读取策略要明确。服务间调用要传递 traceId 和服务身份。

一致性：queue 建票成功但 appointment 状态更新失败时，必须有明确的重试或对账恢复机制；不得用跨库事务。

测试覆盖签名篡改、过期、错误用户、错误网点、窗口外签到、重复签到、并发签到和跨服务部分失败。更新文档和 PROGRESS.md。
```

## 十五、提示词 12：排队、叫号、过号与完成业务

```text
请实现 civicflow-queue 的窗口工作台和排队状态机，严格采用 controller -> service -> mapper。

读取 AGENTS.md、docs/ai/DOMAIN.md、DATABASE.md、API.md、SECURITY.md、PROGRESS.md，以及 queue/resource/auth 相关接口。

范围：
- 用户查看自己的排队票、前方等待人数、当前叫号和预计状态；预计时间可先给简单规则，不虚构精确预测。
- 窗口人员选择被授权的网点和窗口开始/结束工作会话。
- 叫下一号、重呼、过号、开始办理、完成业务。
- 管理员查看网点实时队列概况。

状态建议：WAITING -> CALLED -> SERVING -> COMPLETED；CALLED -> MISSED；MISSED 是否允许重新入队由 DOMAIN.md 明确。所有转换必须校验窗口、人员权限和原状态。

并发要求：多个窗口同时叫下一号时，同一 ticket 只能被一个窗口领取。优先使用 MySQL 8 `SELECT ... FOR UPDATE SKIP LOCKED` + 短事务，或采用文档中已经决定的 Redis Lua；写清选择理由。领取顺序按优先级、签到时间、ticketId 稳定排序，避免饥饿。

完成/过号后通过幂等事件或 OpenFeign 更新 appointment 状态；跨服务失败进入重试/对账。所有操作写 queue_operation_log。

测试覆盖两个窗口并发叫号、重复叫号、非授权窗口、窗口未开工、非法转换、过号重排、完成消息重复投递。更新文档与 PROGRESS.md。
```

## 十六、提示词 13：号源对账与故障修复

```text
请实现 civicflow-appointment 的号源对账与安全修复能力。

读取 AGENTS.md、docs/ai/ARCHITECTURE.md、DATABASE.md、DOMAIN.md、REDIS.md、EVENTS.md、SECURITY.md、PROGRESS.md。

对每个已放号且仍在业务窗口内的 slot，对比：resource 配置总量、appointment 有效消费事实、Redis 当前可用库存、未完成 reservation 预占、已执行补偿记录。定义可计算的不变量，并处理消息处理中短暂不一致的容忍窗口。

实现：
- 定时扫描和管理员手动触发单 slot 对账。
- 对账报告：EXPECTED、ACTUAL、DIFF、原因分类、检测时间、是否可自动修复。
- 自动修复只能采用带版本/快照条件的 Lua 或安全 CAS；如果运行中状态已变化则放弃并重算，不能直接 SET 覆盖库存。
- 不确定问题只告警并记录，不盲目修复。
- 所有修复写审计记录，包含修复前后值、依据和操作者。
- 扫描分页、限速，避免压垮 MySQL/Redis。

构造故障测试：MQ 发布失败但补偿成功、补偿暂时失败、重复消费、Redis 数据丢失、数据库有单但用户键缺失、库存被错误增加。验证对账能够检测，安全场景能够修复。

更新 REDIS.md、EVENTS.md、TESTING.md、PROGRESS.md，并报告对账不变量。
```

## 十七、提示词 14：前端工程与通用能力

```text
请创建 civicflow-web 前端基础工程。本轮只搭建通用能力、路由和页面骨架，不批量填充所有业务页面。

读取 AGENTS.md、docs/ai/PROJECT.md、API.md、SECURITY.md、PROGRESS.md。检查后端真实接口，禁止凭空创造不一致的字段。

技术：Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios。建立清晰的 api、stores、router、views、components、types、utils 目录。

实现：
- 登录页、主布局、按 USER/STAFF/ADMIN 生成的路由和菜单守卫。
- Access Token 内存/安全持久化策略、Refresh Token 刷新、并发 401 只刷新一次、刷新失败回登录页。
- Axios 请求 ID、统一错误处理、取消重复请求的合理策略。
- 通用分页类型、空状态、错误状态、加载状态、403/404 页面。
- 三套入口布局：用户端、窗口工作台、管理端；保持统一设计令牌和响应式布局。
- `.env.example` 和本地代理配置。

安全：不把角色判断仅当作安全边界；前端隐藏菜单只是体验，后端仍鉴权。不要在日志打印 token。避免把敏感信息长期放 localStorage；若后端方案允许，优先安全 Cookie 或明确权衡。

验证 TypeScript、lint、build 和基础组件测试。更新 PROGRESS.md。
```

## 十八、提示词 15：普通用户端页面

```text
请实现 civicflow-web 的普通用户端完整首版流程，并只调用已存在的后端 API。

读取 AGENTS.md、docs/ai/API.md、DOMAIN.md、SECURITY.md、PROGRESS.md，以及前端现有 api/types/router/store。

页面与交互：
- 网点列表/详情、事项选择、日期和时间段选择。
- 号源显示：未放号、可预约、已约满、已停售；放号倒计时只作展示，服务端时间和规则为准。
- 抢号按钮防重复提交；收到 reservationId 后轮询创建结果，设置超时和停止条件。
- 预约待确认页展示 5 分钟倒计时；确认、取消都需防重复提交，状态以服务端返回为准。
- 我的预约列表和详情，按状态显示可执行操作。
- 已确认预约展示签到二维码和刷新逻辑。
- 签到后显示排队号、前方人数、当前状态；采用 3~5 秒可配置轮询，页面隐藏时降低频率或暂停。

必须处理：网络超时但服务端可能已成功、重复点击、预约已超时、库存刚被抢完、轮询终态、401 刷新、403、空列表和移动端布局。

不要在前端自行推导预约最终状态，不要把前端倒计时作为超时裁决。补齐类型、API 封装和组件测试，执行 lint/typecheck/build，更新 PROGRESS.md。
```

## 十九、提示词 16：窗口人员工作台

```text
请实现 civicflow-web 的窗口人员工作台。

读取 AGENTS.md、docs/ai/API.md、DOMAIN.md、SECURITY.md、PROGRESS.md，以及真实 queue/resource/auth 接口。

功能：
- 选择授权网点和窗口，开始工作、暂停/结束工作。
- 展示当前办理票、等待人数、已叫号、过号列表。
- 叫下一号、重呼、开始办理、过号、完成。
- 每次危险状态操作要有清晰确认和按钮互斥，提交期间禁用重复操作。
- 接口出现状态冲突时刷新服务端最新状态并给出可理解提示。
- 适合大厅工作台的大字号、高对比、键盘可操作界面。

轮询实时状态先用可配置间隔；如果后端已有 SSE/WebSocket 契约可按契约使用，但本轮不要为了“实时”擅自扩大后端范围。

严格按角色守卫路由，但不把前端权限当安全边界。执行 lint/typecheck/build 和核心组件测试，更新 PROGRESS.md。
```

## 二十、提示词 17：管理员后台

```text
请实现 civicflow-web 的管理员后台。

读取 AGENTS.md、docs/ai/API.md、DOMAIN.md、PROGRESS.md 和现有前后端实现。

页面：
- 网点管理、事项管理、窗口管理、窗口事项绑定。
- 时间段和号源日历、单日新增、批量生成、启停、额度调整。
- 用户与角色管理。
- 预约查询与操作日志查看。
- 号源对账列表、差异详情、手动重跑和有权限控制的安全修复。

要求：
- 表格都使用服务端分页、查询条件和明确空状态。
- 编辑表单做前端即时校验，但以后端业务校验为准。
- 批量生成先展示影响范围，确认后提交，并展示逐项结果。
- 启停、调整号源、自动修复等高影响操作需二次确认。
- 处理乐观锁冲突、唯一冲突、已有预约导致不可调整等业务错误。

不要展示或记录敏感凭据。执行 lint/typecheck/build 和关键页面测试，更新 PROGRESS.md。
```

## 二十一、提示词 18：跨服务联调与端到端闭环

```text
请对 CivicFlow 首版五个服务和前端进行端到端联调。本轮以修复真实链路为目标，不新增产品功能。

读取 AGENTS.md 和 docs/ai 下全部文档，但优先只打开与失败链路有关的代码。检查 Docker Compose、配置、Flyway、Nacos、RabbitMQ、Redis、前端代理和网关路由。

建立并执行最小 E2E 场景：
1. 管理员登录，创建网点、事项、窗口和未来几分钟放号的 slot。
2. 预热 Redis，验证库存与配置一致。
3. 用户登录并抢号，异步建单后确认。
4. 获取签到二维码，在允许窗口完成签到并生成 queue ticket。
5. 窗口人员开始工作，叫号、开始办理、完成。
6. 验证预约、排队、日志、库存与对账结果一致。
7. 再执行一个不确认预约，验证 5 分钟超时、库存释放和用户可再次预约。

为测试建立可重复的初始化数据或脚本，不依赖手工修改数据库。修复发现的契约、序列化、时区、Long 精度、路由、权限、状态同步和消息配置问题。不要为通过测试绕过鉴权或删除业务约束。

输出 E2E 执行记录：命令、关键请求、实际结果、修复项、仍未验证项。同步 API.md、LOCAL_DEVELOPMENT.md、TESTING.md、PROGRESS.md。
```

## 二十二、提示词 19：高并发、可靠性与安全测试

```text
请对 CivicFlow 做首版发布前质量加固，重点是抢号并发、消息可靠性、状态竞态、越权与数据泄露。本轮先测试和诊断，再只修复已证实的问题。

读取 AGENTS.md、docs/ai/TESTING.md、SECURITY.md、EVENTS.md、REDIS.md、DOMAIN.md、PROGRESS.md。

至少验证：
- quota=100 时 1000 个不同用户并发抢同一 slot，成功预占和有效预约均不得超过 100，不得出现负库存。
- 同一用户 100 个并发请求只产生一个有效预约。
- MQ 重复投递、乱序、消费者重启、确认丢失和死信处理。
- 确认与超时、取消与超时、两个窗口叫下一号的竞态。
- Redis 暂时不可用、RabbitMQ 暂时不可用、服务重启后的恢复。
- JWT 篡改、过期、伪造身份头、水平越权、角色越权、二维码重放和敏感日志扫描。
- SQL 注入、超大分页、非法枚举、重复幂等键载荷不一致。

使用适合项目的 JUnit 5、Testcontainers、Awaitility、MockMvc/WebTestClient 和 k6/JMeter 之一。性能报告必须区分机器环境、并发数、成功率、P50/P95/P99、吞吐和错误分类；不要把开发机结果宣传成生产容量。

修复后重跑对应回归测试。更新 TESTING.md、SECURITY.md 和 PROGRESS.md，列出发布阻断项与非阻断项。
```

## 二十三、提示词 20：可观测性、部署与首版收口

```text
请完成 CivicFlow 首版的可观测性、部署配置和发布检查，不新增通知服务。

读取 AGENTS.md、docs/ai/ARCHITECTURE.md、SECURITY.md、TESTING.md、PROGRESS.md 和现有部署文件。

实现：
- Actuator 健康检查、readiness/liveness、必要 Micrometer 指标。
- traceId 在网关、Feign、RabbitMQ 消息和日志间传递。
- 结构化日志；敏感字段脱敏；明确日志级别和保留建议。
- 关键业务指标：抢号成功/失败分类、Lua 库存不足、MQ 发布失败、消费重试/DLQ、超时关闭、补偿失败、对账差异、排队等待量。
- 每个服务的 Dockerfile，非 root 运行、合理 JVM 参数和健康检查。
- 生产配置模板、环境变量清单、密钥管理说明、备份与恢复说明。
- README 一键本地启动、初始化、测试和常见故障排查。
- 发布检查清单和回滚策略。

执行完整 Maven 测试/打包、前端 lint/typecheck/build、Compose 配置校验；环境允许时做最终冒烟测试。不得把本地开发密码或私钥打进镜像。

更新所有发生漂移的 docs/ai 文档，并将 PROGRESS.md 的首版状态改为“完成”或明确列出尚未完成的发布阻断项。
```

## 二十四、提示词 21：后续通知服务

```text
请在首版主链路稳定后新增 civicflow-notification 服务，不修改已有核心状态机语义。

读取 AGENTS.md、docs/ai/ARCHITECTURE.md、API.md、EVENTS.md、SECURITY.md、PROGRESS.md，以及现有消息事件定义。

范围：消费预约成功、待确认提醒、确认成功、预约取消、即将到号、过号等事件；首版可实现站内消息，并为短信/邮件提供可替换 Provider 接口。采用 controller -> service -> mapper 三层架构。

要求：
- 通知消费按 eventId + channel 幂等。
- 模板版本化，变量白名单校验，避免模板注入和敏感信息泄露。
- 发送失败有限重试，永久失败进入死信并可人工重放。
- 通知失败不得回滚预约或排队主业务。
- 用户通知偏好和必要事务通知的边界明确。
- 提供通知记录查询和管理员失败记录查看接口。

补充 Flyway、测试、Compose、Nacos 配置和前端站内消息入口。更新架构、事件、接口和进度文档。
```

## 二十五、日常使用的短提示词模板

### 1. 单功能实现模板

当某阶段过大时，每轮只填一个功能：

```text
请在 CivicFlow 当前仓库实现【功能名称】。

先读取 AGENTS.md、docs/ai/PROGRESS.md，以及与本功能直接相关的 DOMAIN/API/DATABASE/EVENTS/REDIS/SECURITY 文档；然后检查现有实现，不要重复生成或改写无关模块。

本轮目标：
- 【行为 1】
- 【行为 2】
- 【行为 3】

验收标准：
- 【可观察结果 1】
- 【可观察结果 2】
- 【并发/权限/幂等要求】

约束：继续使用 controller -> service -> mapper；Controller 不直接访问 Mapper；DTO 与 Entity 分离；跨服务不跨库；状态变更用条件更新；写接口考虑幂等；不要添加本功能之外的新框架。

完成后运行最相关测试，更新 PROGRESS.md 和发生变化的契约文档。最终只汇报修改文件、验证结果、关键决策和遗留问题。
```

### 2. 修复 Bug 模板

```text
请诊断并修复 CivicFlow 的以下问题：
【现象、复现步骤、期望结果、日志】

先读取 AGENTS.md、PROGRESS.md 和相关契约，再定位代码。先用测试或最小复现证明根因，不要根据异常表象盲改。

要求：
- 说明根因和受影响范围。
- 添加能在修复前失败、修复后通过的回归测试。
- 只做最小充分修改，不顺手重构无关代码。
- 检查同类问题是否存在于相邻路径。
- 运行相关测试并更新 PROGRESS.md；若契约变化，同步更新对应文档。
```

### 3. 代码审查模板

```text
请审查当前 CivicFlow 工作区相对基线分支的改动。只做审查，不修改代码。

读取 AGENTS.md 和相关 docs/ai 契约。重点检查：业务正确性、状态机、并发竞态、事务边界、Redis Lua 原子性、MQ 幂等与补偿、数据库唯一约束、越权、敏感信息、接口兼容性、三层架构违规和测试缺口。

按严重度输出真实可触发的问题，给出文件和行号、触发条件、影响、建议修法。不要把代码风格偏好冒充缺陷；没有问题就明确说明，并列出尚未覆盖的风险。
```

### 4. 会话交接/压缩模板

如果一个会话已经很长，让 AI 执行：

```text
本轮停止新增代码。请把当前真实状态压缩写入 docs/ai/PROGRESS.md：已完成内容、修改文件、已执行验证及结果、未完成项、已知问题、重要临时决策、下一步第一条命令。若架构/API/数据库/事件契约发生变化，同步更新对应文档。不要只在聊天中总结。
```

开启新会话后使用：

```text
请读取 AGENTS.md、docs/ai/PROGRESS.md 和其中指向的相关文档与代码，从“下一步”继续。先验证仓库真实状态，不要假设上一会话的口头描述正确。本轮只完成 PROGRESS.md 中的下一个未完成验收项，测试通过后更新进度文件并停止。
```

### 5. 防止 AI 一次改太多的约束追加句

```text
修改范围限制：本轮最多完成一个业务闭环。若预计需要修改超过 12 个业务文件（迁移、测试、配置和文档不计），先把任务拆成两个可独立验收的小阶段，只实施第一个。禁止为了“完整性”顺手生成后续模块。
```

## 二十六、建议的 Git 提交节奏

```text
docs: establish civicflow architecture contracts
chore: scaffold multi-module backend
chore: add local infrastructure
feat(auth): implement authentication and rbac
feat(resource): implement outlet item and window management
feat(resource): implement slot configuration
feat(gateway): add jwt authentication and rate limiting
feat(appointment): add redis stock reservation
feat(appointment): add async reservation creation
feat(appointment): add confirmation timeout and cancellation
feat(queue): add qr check-in
feat(queue): add calling workflow
feat(appointment): add stock reconciliation
feat(web): scaffold role-based frontend
feat(web): implement user booking flow
feat(web): implement staff console
feat(web): implement admin console
test: add end-to-end and concurrency coverage
chore: add observability and deployment packaging
```

## 二十七、首版完成定义（Definition of Done）

- 五个服务均通过网关访问，Nacos 注册发现正常。
- USER、STAFF、ADMIN 权限隔离，服务端能阻止横向和纵向越权。
- 管理员可配置网点、事项、窗口、时间段和号源。
- 定时任务可安全预热 Redis，运行中不会覆盖已扣减库存。
- 抢号无超卖，同一用户同事项同日无重复有效预约。
- RabbitMQ 重复投递不会产生重复预约；发布失败和消费失败有补偿/死信路径。
- 5 分钟确认与超时竞态只产生一个终态，库存最多释放一次。
- 签到二维码可验签、会过期、不能跨用户/网点使用。
- 并发叫号不会让两个窗口拿到同一个排队票。
- 对账能发现库存、预占和预约事实差异，并只在安全条件下修复。
- 前端完整覆盖用户、窗口人员、管理员三类主流程和失败状态。
- Flyway 可在空库完成迁移；Docker Compose 能启动本地依赖。
- 后端测试/打包、前端 lint/typecheck/build、核心 E2E 和并发测试有真实执行记录。
- 文档、接口、数据库、消息和实现保持一致，`PROGRESS.md` 没有未解释的发布阻断项。

