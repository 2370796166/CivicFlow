# CivicFlow 架构决策记录（ADR）

状态含义：Accepted 为当前约束；Proposed 待确认；Superseded 保留历史但不再生效。替代决策必须新增 ADR 并指向旧编号，不直接删除旧内容。

## ADR-001：Java 17 与 Spring 版本兼容基线

- 状态：Accepted（设计基线，工程骨架阶段需运行验证）
- 日期：2026-09-10
- 决策：采用 Java 17；计划集中属性为 Spring Boot `3.5.16`、Spring Cloud `2025.0.3`、Spring Cloud Alibaba `2025.0.0.0`。根 POM 依次以明确属性导入 Boot、Cloud、Alibaba BOM；子模块不写组件版本。Gateway 使用 2025.0 新名称的 WebFlux starter/config prefix。
- 依据：Spring Boot 3.5.16 官方系统要求最低 Java 17；Spring Cloud 官方支持矩阵将 2025.0 对应 Boot 3.5.x；Spring Cloud Alibaba 官方仓库将 2025.0.x 对应 Cloud 2025.0.x + Boot 3.5.x + JDK 17+。Alibaba `2025.0.0.0` 标签自身以 Boot 3.5.0、Cloud 2025.0.0 构建，证明家族基线；这里仅在相同维护线内提升安全/修复 patch。
- 官方来源：[Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[Spring Cloud 支持矩阵](https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions)、[Spring Cloud 2025.0 发布记录](https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes)、[Spring Cloud Alibaba 分支关系](https://github.com/alibaba/spring-cloud-alibaba)、[SCA 2025.0.0.0 构建 POM](https://github.com/alibaba/spring-cloud-alibaba/blob/2025.0.0.0/pom.xml)。
- 风险：SCA 的该 GA 标签直接验证的是 3.5.0/2025.0.0，组合到最新 patch 虽位于官方兼容家族，仍必须验证 Nacos Config/Discovery、Sentinel Gateway、Feign 与 graceful shutdown；已知 Nacos 配置方式应使用 `spring.config.import`，不沿用旧 bootstrap 假设。
- 门禁：下一阶段运行 `mvn dependency:tree`、全模块编译、最小应用上下文与 Testcontainers/Compose 冒烟。若发现不兼容，先查官方 release/BOM；优先升级同 release train 的 SCA GA。不得单独降级某 starter。
- 替换方式：修改根 POM 的 `spring-boot.version`、`spring-cloud.version`、`spring-cloud-alibaba.version` 三个属性，新增 ADR，附官方矩阵和实测记录。大 release train 升级单独分支完成迁移/回归；禁止 Maven 范围、`LATEST`、SNAPSHOT。

## ADR-002：固定三层架构而非 DDD

- 状态：Accepted
- 决策：业务服务采用 `controller -> service -> mapper`，允许 `service.impl`；DTO/VO/Entity 分离。Service 是业务规则、事务、幂等和状态机边界。
- 原因：首版团队/业务规模更需要一致、可检索的结构与较低认知成本；用户明确禁止 DDD/Repository。
- 后果：复杂流程仍由明确命名的 Service 方法和私有协作方法完成；event/client/task 是技术入口，必须调用 Service，不能形成绕过层。

## ADR-003：每服务数据库隔离

- 状态：Accepted
- 决策：auth/resource/appointment/queue 使用独立 schema 和账号；禁止跨库查询、外键和共享 Mapper。跨服务只用 OpenFeign/事件。
- 原因：保证数据所有权、独立演进和故障隔离。
- 后果：界面所需跨域数据使用 ID + 必要历史快照；一致性靠本地事务、outbox、幂等、补偿/对账，接受短暂陈旧。

## ADR-004：不用分布式事务，采用最终一致性

- 状态：Accepted
- 决策：Redis 预占到 MQ 依靠 publisher confirm + pending recovery + 幂等补偿；数据库派生事件依靠 transactional outbox + publisher confirm；消费至少一次并由消息去重、业务唯一约束、状态 CAS 共同保证。
- 原因：Redis、RabbitMQ、多 MySQL schema 不存在一个可靠轻量的全局事务；业务可接受短暂 CREATING/状态同步延迟。
- 后果：API 明确返回 202/处理中；所有部分失败必须有可重试记录、DLQ/告警和对账，不能依赖“先查再改”。

## ADR-005：确认超时采用 RabbitMQ TTL + DLX，加数据库扫描兜底

- 状态：Accepted
- 决策：PENDING_CONFIRM 的 `confirmDeadline=createdAt+5m`。建单事务写 timeout outbox；消息进入固定 300000ms delay queue，经 DLX 到 timeout check。消费者使用 DB 时间和 `status=PENDING_CONFIRM AND deadline<=now` CAS，胜者建立唯一释放记录。定时扫描同条件兜底。
- 原因：无需依赖非标准 delayed-message 插件；outbox/confirm/扫描覆盖消息丢失和延迟。
- 后果：TTL 只触发，不裁决；早到重排、晚到直接 CAS；队列积压不会错误覆盖已确认订单。未来若采用 RabbitMQ delayed exchange/可靠调度器，状态 CAS 与扫描仍保留。

## ADR-006：CREATING 是查询投影，不是持久化状态

- 状态：Accepted
- 决策：`CREATING` 属 `ReservationProjectionStatus`。Redis 预占存在而 appointment_order 尚不存在时，`GET /reservations/{reservationId}` 返回 CREATING；数据库 appointment status 从 `PENDING_CONFIRM` 开始。
- 原因：消息消费者无法在同一数据库事务前可靠创建 CREATING 行；持久化它会把 Redis->MQ 的不确定性伪装成订单事实，并增加孤儿行。Redis reservation/pending ZSET 已准确表达未决阶段。
- 后果：查询服务先查 DB，未命中再按本人/业务分片查 reservation；Redis 证据失效且 DB 无单时返回 FAILED 业务结果（实现时需在幂等结果中保留 24h），而不是 404 模糊状态。对外投影状态集合包含 CREATING/FAILED 和全部预约状态。

## ADR-007：有效预约占位表作为数据库唯一防线

- 状态：Accepted
- 决策：`active_booking_guard` 用唯一 `(user_id,item_id,service_date)` 表达 PENDING_CONFIRM/CONFIRMED/CHECKED_IN/SERVING。建单同事务插入，迁入 CANCELLED/EXPIRED/COMPLETED/NO_SHOW 的 CAS 胜者同事务删除。
- 原因：MySQL 普通唯一索引不能只约束一组“有效状态”；在 order 表使用含 status 的唯一索引会允许多个不同有效状态并存，生成列对多状态和后续演进脆弱。
- 后果：Redis active key 仅前置；唯一冲突必须区分相同 reservation 幂等与不同 reservation 业务拒绝，后者补偿自己的库存。占位表删除不丢审计，因为订单/日志保留事实。

## ADR-008：并发叫号使用 MySQL SKIP LOCKED

- 状态：Accepted
- 决策：queue 在本地短事务以 MySQL 8 `SELECT ... FOR UPDATE SKIP LOCKED` 按 `priority DESC, checked_in_at ASC, id ASC` 领取 WAITING ticket，然后条件更新 CALLED/窗口关系。
- 原因：排队票事实已在 queue MySQL；数据库锁可让选择与状态更新在单事务完成，避免另建 Redis 队列的双写/重建复杂度。
- 后果：必须有覆盖排序的索引；事务内禁止远程/MQ 调用；高并发压测锁等待与饥饿。若规模证明 DB 成瓶颈，Redis Lua 方案需新 ADR 并保留 DB CAS 最终防线。

## ADR-009：库存事实与对账模型

- 状态：Accepted
- 决策：resource slot 是配置事实；Redis remaining 是运行视图；appointment order/release 是消费事实。对账按配置版本和未决 reservation 计算 expected，自动修复必须使用预期版本 CAS Lua。
- 原因：三者承担不同生命周期，任何一方都不足以单独重建真实状态。
- 后果：配置变更版本化；证据缺失/版本跳跃时暂停而非盲修。历史完成/NO_SHOW 是已消费号源，不在服务时段结束后回补可售库存。

## ADR-010：二维码采用短时签名令牌与当前 nonce

- 状态：Accepted
- 决策：二维码使用独立 key 的短时 JWS，随机 nonce 摘要持久化；重新签发使旧 nonce 失效。queue 调 appointment claim 接口先用 CAS 完成 CONFIRMED->CHECKED_IN 并写 claimed outbox，再建 ticket；appointmentId 唯一约束是重放/双路径最终防线。
- 原因：裸 ID 可枚举/篡改，单纯签名但长期有效仍易截图重放。
- 后果：queue 需通过 service-authenticated API 校验 appointment 当前 nonce/归属/窗口；claim 后建票失败由同键重试和 claimed 事件补偿，预约不回退。Redis used-key 只优化，不替代数据库唯一约束。

## 待后续 ADR 的议题

- 生产 secret/KMS 供应商与 mTLS 落地。
- STAFF 错过号码后是否允许人工回队（首版不允许）。
- 热点事项单 `{itemId:serviceDate}` Redis hash slot 的容量阈值与再分片方案。
- 法规要求的数据保留期、实名认证字段和管理员 MFA。
