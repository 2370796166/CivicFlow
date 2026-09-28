# 首版发布前质量加固实测（2026-09-28）

## 范围与环境

按 `AGENTS.md`、TESTING、SECURITY、EVENTS、REDIS、DOMAIN 和 PROGRESS 先测试再修复。初始 Git 工作树无修改。测试仅创建合成预约、账号标识与独立 Testcontainers；未对正在运行的开发库、用户账号或现有 Compose 容器注入故障。

开发机为 Windows / AMD Ryzen 7 7745HX（8 核 16 线程），Docker Desktop 29.6.2 给容器分配 16 CPU、8,164,560,896 字节内存。Java 17，MySQL 8.4.11、Redis 7.4.11、RabbitMQ 4.1.8、k6 0.52.0。k6 从隔离容器访问随机端口的 appointment HTTP；resource 快照模拟可信 Feign 响应，JWT decoder 委托真实 RSA 验签。此环境、调度器、连接池和路由均非生产配置，不据此宣布生产容量。

## 先测后修复的结论

| 证据 | 实测失效 | 修复与回归 |
| --- | --- | --- |
| `latePublisherAckMustNotOverwriteCommittedConsumerProjection` | 消费者已把 request 持久化为 `PERSISTED`，迟到的 publisher ACK 使用整个旧 Entity 更新，退回 `PUBLISHED` | `recordPublication` 只条件更新发布字段与 `RESERVED/PUBLISH_UNKNOWN`；旧 ACK 不覆写终态；回归通过 |
| `concurrentSameKeyPublishesOneCanonicalEvent` | 同一 reservationId 两线程各生成 `reservedAt`，发出不同 hash 的事件 | `saveReservedEvent` 以 `CREATED` CAS 保存首个 event JSON，竞争者重读原事件；回归通过 |
| `duplicateDeliveryConsumerRestartAckLossAndPoisonDlq` | 手动 ACK 模式中默认 recoverer 重试耗尽后毒消息没有进入 DLQ | 显式 `rejectManual=true`、`requeue=false`；真实 RabbitMQ DLQ 回归通过 |
| `rejectedMessageMustNotLeakBodyOrHeaders` | 默认 recoverer 日志包含完整 JSON body 和 Authorization header canary | 仅记录异常类名，拒绝异常不携带原 cause；日志 canary 回归通过 |
| quota=100/1000 VU 首轮压测 | 100 个请求在 Tomcat 连接等待阶段被拒绝；当轮仍仅 100 个受理，没有超卖，但预期业务结果率只有 90% | 先在测试中对照验证 `server.tomcat.accept-count=2048` 后 100/900/0，再写入 appointment 正式配置并移除测试覆盖；最终全量回归仍为 100/900/0。此参数只增大等待队列，不是吞吐或生产容量保证。 |

无公开 API、持久化表、routing key、Redis key、状态枚举和 Flyway 迁移变更。只修复上述证实问题；初轮测试配置错误（Testcontainers JDBC 未设 UTC、Lua 初始化在放号后）和 k6 0.52 脚本不支持 `??` 已作为测试夹具问题纠正，不计业务缺陷。

## 覆盖矩阵

| 主题 | 验证与边界 |
| --- | --- |
| 超卖/单用户 | quota=100、1000 独立 JWT、1000 VU 实际 HTTP；订单和有效状态数、DB active guard 均 100、Redis 最低采样 0；同用户 100 并发只建 1 单；同用户同幂等键 100 并发验证 1 单和 canonical event。20ms 库存采样不是连续观测，Lua 单脚本原子性与 DB 唯一索引补强证明。 |
| MQ | 真实持久 broker 的重复投递、消费事务提交后关闭未 ACK channel、监听器 stop/start、晚到/早到超时、TTL/DLX、非法消息最终 DLQ、broker `stop_app/start_app`、模拟“broker 已收而发布 confirm 丢失”后同 eventId 重发。断言 consume record、订单唯一、outbox 唯一与库存。 |
| 状态竞态 | MySQL 空 schema Flyway 后，各 100 轮 confirm/timeout 与 cancel/timeout，断言唯一状态/释放；双窗口 `FOR UPDATE SKIP LOCKED` 无重复领取。正常过期与重复消息只释放一次。 |
| 依赖中断/恢复 | Redis pause/unpause 期间新抢号 503、已提交取消的唯一 release record，恢复后重试一次；超时 Lua 可能在恢复后执行，CREATED 请求能原 ID 恢复。RabbitMQ 暂断保留 `PUBLISH_UNKNOWN` 与库存，恢复后订单唯一。新 JVM 重新载入 CREATED 持久记录并用真实恢复扫描建立唯一订单。 |
| 安全与输入 | MockMvc 的真实 RSA 篡改/过期拒绝、伪造 `X-User-Id`、他人预约查/确认/取消 404、USER 调管理接口 403、超大页、非法枚举及注入串 400、同键异载荷 409；二维码签名/过期既有单测，真实 MySQL nonce 轮换、用户/网点归属、100 次重放只一次 claim/outbox。网关身份头清洗及内部路由由既有 WebTestClient/过滤器回归。 |
| 敏感日志 | 本轮测试日志、独立恢复进程日志及五业务容器近两小时日志做本地模式扫描；MQ body/header canary 为专用运行时断言。仅返回类型与行号，正则覆盖不等于完整 DLP。 |

## 性能数据

成功率分两层：`HTTP 202` 为抢到库存并进入异步受理；业务结果识别率计 202/预期 409。k6 的 `http_req_failed` 默认把预期 409 计为 HTTP 失败，因此不能直接当作系统故障率。P50/P95/P99 和吞吐只针对同一批 HTTP 响应，不含异步最终建单等待；最终订单数另用 MySQL 校验。错误分类保留预期库存不足、预期重复占位和非预期响应。

| 场景 | 并发/请求 | 抢号受理 | 预期业务拒绝 | 非预期响应 | P50/P95/P99 | 吞吐 |
| --- | --- | --- | --- | --- | --- | --- |
| 100 库存 | 1000 VU / 1000 | 100 (10%) | 库存不足 900 (90%) | 0，预期结果率 100% | 5241.63 / 8619.94 / 8755.75 ms | 108.09 req/s |
| 同用户不同键 | 100 VU / 100 | 1 (1%) | 活跃预约冲突 99 (99%) | 0，预期结果率 100% | 532.86 / 690.03 / 708.07 ms | 124.58 req/s |
| 同用户同键 | 100 VU / 100 | 100 (100%，同一幂等结果) | 0 | 0，预期结果率 100% | 22.13 / 118.14 / 118.37 ms | 624.88 req/s |

原始 k6 摘要与事实保存在 Git 忽略的 `civicflow-appointment/target/release-hardening/`；临时 token/夹具不提交。

最终 MySQL 订单数依次为 100、1、1，三个场景最终 Redis 剩余库存均为 0，采样最低值均为 0；其中同键场景的 100 个 202 是同一个 reservation 的幂等受理，不是 100 个预约。初轮默认 Tomcat 队列发生的 100 个连接拒绝属于环境下的可复现容量缺陷，不能并入 900 个业务库存不足。上表来自移除测试覆盖、仅使用正式配置后的最终 `mvn verify`；本机调度和容器资源使延迟有显著波动。

## 门禁与发布决定

**发布阻断项（需目标部署单独闭环）**：本轮不具备生产多实例、完整 gateway/Nacos 链路的 1000 用户并发容量证明；没有证明真实进程在 Redis 成功/MQ confirm 各切点被强杀、不同节点同时恢复、原 eventId 的受审计 DLQ 人工重放；TLS/KMS 与实际生产日志采集链路未验收。当前开发 Compose 仍运行先前镜像，代码提交不是部署完成。不能把这次单机结果用作正式放量依据。

**非阻断项**：前端 lint 既有 311 warning、构建主 chunk 大于 500 kB；MySQL 8.4 的 Flyway 版本提示；高并发下开发机 P95 高于普通交互量级，但尚无经确认的生产 SLA。上述不改变本轮一致性与数据保护门禁判断。

## 已执行门禁

- `mvn -q spotless:apply verify`：退出 0；41 个 JUnit 5 suite、144 项测试，0 失败/错误/跳过，含全新 MySQL schema Flyway 迁移、真实 Redis/RabbitMQ/Testcontainers 和 k6 三场景。`spotless:apply` 仅格式化本轮 Java 改动。
- 前端 `npm run lint`：退出 0，311 条既有 warning；`npm run typecheck`、`npm run test`（14 文件、25 项）、`npm run build`：均退出 0，build 仍提示主 chunk 约 1.19 MB。
- 启动器 Python 单测 8 项通过；`docker compose config --quiet` 通过。敏感日志扫描与提交前暂存扫描的最终结果记录在 PROGRESS。
