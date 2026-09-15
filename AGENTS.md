# CivicFlow AI 开发契约

本文件是仓库级稳定约束。开始任何工作前，先读本文件、`docs/ai/PROGRESS.md` 和本次任务直接相关的契约文档。若实现与契约冲突，先修订 ADR/契约并说明迁移影响，禁止静默偏离。

## 1. 产品与范围

- 项目：智序（CivicFlow），城市公共服务预约排队平台。
- 首版模块：`civicflow-common`、`civicflow-gateway`、`civicflow-auth`、`civicflow-resource`、`civicflow-appointment`、`civicflow-queue`、`civicflow-web`。
- 本轮目录规划（后续阶段创建，不在设计阶段制造空代码）：

```text
/
├─ pom.xml
├─ civicflow-common/
├─ civicflow-gateway/
├─ civicflow-auth/
├─ civicflow-resource/
├─ civicflow-appointment/
├─ civicflow-queue/
├─ civicflow-web/
├─ deploy/compose/
└─ docs/ai/
```

## 2. 稳定技术约束

- Java 17；Spring Boot 3；Spring Cloud Gateway、OpenFeign；Spring Cloud Alibaba Nacos、Sentinel。
- MySQL 8、MyBatis-Plus、Flyway；Redis + Lua；RabbitMQ；JWT + Spring Security；Docker Compose。
- Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios。
- Maven 多模块单仓库。根 POM 通过属性集中声明 Java、Spring Boot、Spring Cloud、Spring Cloud Alibaba 及插件版本，并导入 BOM；子模块不得散落版本号。
- 版本基线和升级门禁以 `docs/ai/DECISIONS.md` ADR-001 为准。不得凭经验单独升级一个 BOM。
- 每个可运行服务拥有独立数据库/schema 与独立数据库账号；禁止跨库 SQL、跨服务 Mapper、共享业务表和分布式数据库事务。
- 跨服务协作只用受保护的 OpenFeign API 或版本化 RabbitMQ 事件。resource 配置、appointment 消费事实、queue 排队事实各有唯一所有者。

## 3. 分层与依赖边界

- 服务内固定为 `controller -> service -> mapper`，允许 `service.impl`。禁止引入 DDD aggregate/domain service、Repository、UseCase、CommandBus 等额外业务层。
- Controller 只做协议适配、Bean Validation、权限入口、调用 Service 和响应映射；不得直接调用 Mapper、写事务或承载业务状态机。
- Service 负责业务规则、状态机、事务、幂等、跨服务编排；事务边界必须在 Service。
- Mapper 只负责本服务数据访问；简单 CRUD 用 `BaseMapper`，复杂 SQL 用 XML。并发写必须用唯一约束、条件更新、乐观锁或数据库锁，禁止“先查再改”。
- 请求 DTO、响应 VO、Entity 分离。Entity 不得直接作为 API 或消息体；转换器放 `convert`，不得用反射式复制掩盖字段语义。
- common 仅承载真正跨模块稳定的响应、异常、追踪和安全上下文模型；不得依赖 Web/MyBatis/Security 全家桶，不得存放服务业务枚举或业务错误码。
- gateway 可依赖 common 的轻量契约；业务服务可依赖 common；业务服务之间禁止 Maven 代码依赖。

## 4. 编码与数据规则

- 根包名 `com.civicflow`；类名表达职责；状态、错误码、routing key、Redis key 禁止魔法字符串。
- API 前缀 `/api`；用户 `/api/user/**`，窗口 `/api/staff/**`，管理员 `/api/admin/**`，内部 `/internal/**`。
- 统一响应包含 `code`、`message`、`data`、`requestId`；HTTP 状态码表达协议结果，业务错误码表达稳定原因。
- ID 使用 MyBatis-Plus `ASSIGN_ID` 或等价雪花 ID；所有 API/事件中的 64 位 ID 序列化为字符串。`reservationId`、`eventId`、`requestId` 使用 UUID/ULID 字符串。
- 服务日期/时段使用 `LocalDate`、`LocalTime`，业务时区固定 `Asia/Shanghai`；审计时间、截止时间按 UTC instant 存储和传输（ISO-8601 带偏移），展示时转换。
- MySQL 使用 `utf8mb4`、`DATETIME(3)`；表名 snake_case。Flyway 迁移一经进入共享分支不得改写，只能追加。
- 所有状态写入使用 `UPDATE ... WHERE status IN (...)` 并校验影响行数；Redis 多键原子写必须由 Lua 完成。
- 写接口必须评估：认证、授权、归属校验、参数边界、幂等键与载荷绑定、并发竞态、审计、超时、补偿和敏感日志。
- 日志禁止输出密码、refresh token、完整 JWT、二维码令牌/签名、密钥、完整手机号/证件号。数据库和日志脱敏规则见 `SECURITY.md`。

## 5. API、消息与缓存演进

- 公开 REST 首版为 `/api/v1`；破坏性变更新增主版本，新增可选字段保持向后兼容。
- 消息必须含 `eventId`、`eventType`、`eventVersion`、`occurredAt`、`traceId`、`producer` 和业务幂等键；消费者按文档中指定键落库去重。
- 消息至少一次投递，消费者先设计重复、乱序和部分失败，再写正常路径；可靠发布使用 publisher confirm，数据库派生消息使用 transactional outbox。
- Redis 不是配置事实源或预约事实源。Key、TTL、Lua 返回码只能以 `REDIS.md` 为准；禁止在业务代码拼接未登记 key。
- 状态、表、API、事件、Redis key 的改动必须在同一变更中同步相关文档。

## 6. 安全规则

- 网关和服务双层防护：网关校验令牌、限流和清洗伪造头；业务服务仍校验 JWT/服务身份并执行方法级 RBAC 与资源归属检查。
- 浏览器传来的 `X-User-*`、`X-Service-*`、`X-Request-Id` 一律不可信；网关先删除再生成。内部接口不得暴露公网路由。
- 用户只能操作自己的预约与排队票；窗口人员必须绑定网点/窗口；管理员操作也需明确权限与审计。
- 签到二维码必须是短时 JWS，包含 appointmentId、userId、outletId、nonce、iat、exp、purpose，且服务端校验当前 nonce、归属、状态、日期、网点与重复签到。

## 7. 验证与完成定义

- Java：格式/静态检查、`mvn test`，涉及基础设施时用 Testcontainers 或等价隔离集成测试；最终阶段执行 `mvn verify`。
- Web：lint、typecheck、unit test、build；关键角色流程补 E2E。
- 并发与可靠性改动必须有可复现测试，至少覆盖库存不为负、单用户单有效预约、CAS 竞态、消息重复、发布失败补偿、超时/确认竞态和双窗口叫号。
- 涉及 SQL 必须验证全新 schema Flyway 迁移；涉及 Compose 必须执行配置校验。不得声称未执行的测试已通过。
- 完成每个阶段必须更新 `docs/ai/PROGRESS.md`：真实修改、命令/结果、遗留风险、下一步。

## 8. AI 工作规则

1. 先检查仓库与工作树，保留用户已有修改；不得覆盖、回滚或清理无关文件。
2. 单轮只完成用户授权范围。设计任务不生成业务代码，诊断任务不擅自修复。
3. 优先最小充分修改；不为“完整”提前创建后续模块或空接口海洋。
4. 不确定且会改变公开契约、数据模型或安全边界时，记录为待确认问题；其余采用可逆且明确记录的假设继续。
5. 新依赖需说明必要性、许可证/维护性，并通过 BOM 或集中属性管理；禁止 SNAPSHOT、动态版本和无依据版本组合。
6. 代码和文档冲突时，以最新已接受 ADR 为决策来源；若 ADR 被替代，保留历史并标记 superseded。
7. 完成前运行文档/代码搜索，核对状态名、表名、routing key、Redis key、错误码和接口路径一致性。

