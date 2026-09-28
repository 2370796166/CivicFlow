# CivicFlow 测试策略与矩阵

## 2026-09-28 发布加固门禁

本轮先复现、后修复；完整范围、性能口径、阻断项和命令结果见 [发布加固报告](RELEASE_HARDENING_2026-09-28.md)。新增测试不使用 `disabledWithoutDocker`，Docker 不可访问时门禁失败，不能把跳过记为通过。

```powershell
mvn -q verify
# 只重跑加固测试（PowerShell 中完整引用 -D 参数）
mvn -q -pl civicflow-appointment,civicflow-queue -am test '-Dtest=ReleaseHardeningIntegrationTest,AppointmentStateMySqlIntegrationTest,QueueWorkbenchMySqlIntegrationTest,AppointmentReservationFlowIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

- `ReleaseHardeningIntegrationTest`：随机端口 HTTP、真实 MySQL 8.4.11/Flyway、Redis 7.4.11、RabbitMQ 4.1.8、k6 0.52.0。仅 resource 快照和 JWT decoder 的装配点为测试替身；后者委托真实 RSA 验签器。不是 gateway/Nacos/完整 Compose 容量测试。
- k6 1000 用户/1000 VU/quota=100；另有同用户不同幂等键 100 VU，以及同用户同键 100 VU。断言库存、订单、active guard 和 PERSISTED 投影；20ms 采样不能证明任意瞬间都无负值，Lua 原子约束及最终事实共同构成证据。
- 首轮 1000 VU 暴露默认 Tomcat 连接等待队列的 100 次拒绝；测试配置 A/B 验证 `accept-count=2048` 后，写入 appointment 正式配置并移除测试覆盖。最终 `mvn verify` 中 1000 请求为 100 受理、900 库存不足、0 非预期；这是本机突发接入回归，不是生产容量认证。P50/P95/P99、吞吐和另外两组错误分类见报告。
- 真实 broker：消费事务提交后 channel 关闭而未 ACK、停止/启动监听器、重复消息、早到超时经 TTL/DLX、broker stop_app/start_app、毒消息 DLQ。发布 confirm 丢失通过“实际发送到 broker，但不完成调用方 future”注入，明确区别于网络代理丢包。
- Redis pause/unpause：HTTP 503、已落库取消后的 FAILED 释放任务、恢复后一次返还；超时 Lua 可能延后执行，因此还验证 CREATED 请求恢复为唯一订单。新 JVM 独立加载持久化 CREATED 快照并由真实扫描器恢复；不是操作系统重启或全故障窗口进程 kill 矩阵。
- `AppointmentStateMySqlIntegrationTest` 在 UTC JDBC 会话执行原状态矩阵，并额外各重复 100 次 confirm/timeout、cancel/timeout；`QueueWorkbenchMySqlIntegrationTest` 使用真实 `SKIP LOCKED` 和空库迁移执行双窗口及越权/非法转换回归。
- 二维码在真实 MySQL 验证 nonce 轮换、归属/网点拒绝、100 次并发重放只有一次 claim/outbox；签名篡改与过期另由既有 QR 单测覆盖。JWT 篡改/过期、伪造身份头、水平/角色越权、超大 size、非法枚举、注入字符串和异载荷同键在 MockMvc/既有 WebTestClient 组合验证。
- 敏感扫描入口 `scripts/scan_release_logs.py --logs <本轮日志> --staged --output <报告.json>`，可加 `--docker <容器名...>`。只输出路径、类型、行号，不回显秘密；正则扫描不等于完整 DLP 审计。专门的 MQ canary 测试断言 rejected body/header 不进入日志。

产物在 `civicflow-appointment/target/release-hardening/`（k6 摘要、库存事实、新进程恢复记录），不提交临时 JWT、测试库凭据或完整运行日志。使用的 JUnit 5/Testcontainers/Awaitility/MockMvc 均来自现有依赖与 BOM，无新增应用依赖。

## 全容器部署回归

```powershell
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
docker compose ps -a
.local/venv/Scripts/python.exe scripts/smoke_docker.py
.local/venv/Scripts/python.exe scripts/e2e_docker.py
```

`e2e_docker.py` 针对运行中的完整 Compose，经 Nginx→gateway 执行三角色浏览器/接口闭环、真实 5 分钟 MQ 超时、库存释放、再次预约与对账，也覆盖初始服务令牌到期后的自动刷新。每次建立唯一 DOCKER_ 前缀合成资源/账号并保留审计，不删除既有数据。只有缺少管理入口的人员 scope 使用初始化 SQL；业务状态/库存/期限不能直接改表。可选宿主机 Python/Node/Playwright 仅作为测试客户端，应用运行不依赖它们。结果为 `target/e2e/docker-<run>.json`。

`smoke_docker.py` 检查 11 healthy/3 init exit 0、深链接、原管理员、401/403 与当前轮换服务 JWT 的内部请求；除正常登录会话外只读。`e2e_docker.verify_facts(slotId,ticketId)` 可在同一运行后只读复核订单、排队、操作日志、库存及配置版本，便于停止/重启后的持久化验收。

启动器回归现为 8 项，增加容器 DNS/内部端口与按服务密钥隔离断言；native 配置仍使用宿主机端口。2026-09-27 run 36BC8E97 完整容器 E2E、stop/up --build 与重启后冒烟/事实核验全部通过；构建及运行实测记录见 `DOCKER_EXECUTION.md`。

## 2026-09-27 遗留项补测

详细命令、样本规模、失败重试和外部环境边界见 [验证记录](VALIDATION_2026-09-27.md)。新增 `python scripts/e2e_v1.py --load`：独立临时基础设施、200 个真实用户、50 VU/50 库存，断言异步订单数、去重、库存和 service JWT 拒绝行为。运行前释放 8080–8084 与 5173，不能与主目录打包同时执行。首个通过 run 为 A48B162C。

实际 Windows 重启使用 `scripts/verify-reboot.ps1 before`，用户保存工作并重启后执行 `after`。同次开机拒绝记录成功，证据保存在 Git 忽略的 `.local/validation`。本机空依赖缓存不等同全新 OS/空镜像环境；生产密钥配送需独立目标环境验收。

## 1. 测试原则

- 测试金字塔：Service/状态机单元测试为主，MySQL/Redis/RabbitMQ 集成测试验证真实语义，少量经 gateway 的 E2E 验证角色闭环。
- 使用 JUnit 5、AssertJ、Mockito（纯单元）、Spring Boot Test、Testcontainers（MySQL 8/Redis/RabbitMQ）、Awaitility；Gateway 用 WebTestClient；前端用 Vitest + Vue Test Utils，E2E 用 Playwright；并发压测用 k6 或 JMeter 二选一。
- 不用 H2 替代 MySQL 验证唯一索引、`SKIP LOCKED`、Flyway 或时间 CAS；Lua 必须在真实 Redis 执行；MQ TTL/DLX/confirm 必须在真实 RabbitMQ 容器执行。
- 每个故障测试记录环境、种子、并发数、命令、实际结果；性能结果报告 P50/P95/P99、吞吐、成功/失败分类，不能宣传为生产容量。

## 2. 分层测试

| 层级 | 范围 | 合格门槛 |
| --- | --- | --- |
| 单元 | DTO 校验、转换、Service 分支、状态机、payload hash、脱敏、JWT/JWS 校验 | 每个合法转换和非法转换至少一例；错误码稳定 |
| Mapper/Flyway | 条件更新、唯一约束、索引查询、SKIP LOCKED | 空库迁移成功；并发影响行数符合预期 |
| Redis 集成 | reserve/mark/compensate/adjust Lua、TTL、幂等、共槽 | 不超卖、不负库存、不误删他人 active、不重复释放 |
| RabbitMQ 集成 | confirm/return、重复、retry/DLQ、TTL+DLX、outbox | 至少一次下业务结果恰好一次；失败可观察/可重放 |
| 安全集成 | JWT/RBAC/归属/伪造头/QR | 未授权无数据泄露；服务内部接口不可由用户 token 调用 |
| E2E | admin 配置 -> 用户抢号确认签到 -> staff 办理 -> 对账 | 状态/库存/日志首尾一致；超时支路也通过 |

## 3. 状态机矩阵

### 预约

| 起点 | confirm | timeout | cancel | check-in | serving | complete | no-show |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PENDING_CONFIRM | CONFIRMED | EXPIRED | CANCELLED | 拒绝 | 拒绝 | 拒绝 | 拒绝 |
| CONFIRMED | 幂等/冲突按 key | 空操作 | 截止前 CANCELLED | CHECKED_IN | 拒绝 | 拒绝 | 拒绝 |
| CHECKED_IN | 拒绝 | 空操作 | 拒绝 | 幂等返回票 | SERVING | 拒绝 | NO_SHOW |
| SERVING | 拒绝 | 空操作 | 拒绝 | 拒绝 | 幂等 | COMPLETED | 常规拒绝 |
| 终态 | 全部拒绝或相同幂等读取 | 空操作 ACK | 相同幂等读取 | 拒绝 | 拒绝 | 相同幂等读取 | 相同幂等读取 |

必须加真实竞态：confirm vs timeout、cancel vs timeout、confirm vs cancel、check-in vs cancel。断言仅一个 CAS 影响 1 行、active guard 与 release record 符合胜者、库存最多释放一次。

### 排队

覆盖 WAITING->CALLED、CALLED 自重呼、CALLED->MISSED、CALLED->SERVING、SERVING->COMPLETED 及所有直接跳跃/终态迁出。断言未授权窗口和已结束 session 均失败，失败不改变 ticket/version。

## 4. 核心并发矩阵

| 场景 | 负载 | 必须断言 |
| --- | --- | --- |
| 单 slot 抢号 | quota=100，1000 不同用户同时请求 | OK=100；remaining=0；无负数；最终有效+未决不超过 100 |
| 单用户重复抢 | 同用户对同事项同日不同 slot 100 并发 | 仅一个 Redis active；仅一个 DB active guard/有效订单；其余 DUP_ACTIVE/幂等 |
| 同 reservation 重放 | 100 次相同 reservationId/载荷 | 只扣 1 次，只建 1 单；返回相同结果 |
| 同 key 异载荷 | 相同 reservationId 或 Idempotency-Key，不同 slot/payload | 409 conflict；不发生第二副作用 |
| 发布失败补偿 | 强制 returned/NACK/confirm timeout | 库存最多 +1；active 只比较删除；comp marker/record 唯一 |
| confirm/timeout | deadline 边界并发各 100 次 | DB 时间裁决；CONFIRMED 或 EXPIRED 唯一终态；仅 EXPIRED 释放 |
| 双窗口叫号 | 2~20 个 ACTIVE 窗口争抢同队列 | 每票最多一个 calledWindow；稳定排序；无长事务/死锁泄露 |
| 并发签到 | 同 token/appointment 100 次 | 单 queue_ticket；重复返回同 ticket；预约最终 CHECKED_IN |
| 额度调整与抢号 | adjust Lua 与 reserve 并发 | configVersion 连续；remaining 不负；不覆盖扣减 |

## 5. 消息可靠性与幂等

- `appointment.reservation.requested`：重复、乱序、消费者在事务提交前/后崩溃、ACK 丢失、active guard 被其他 reservation 占用。
- Publisher：Redis 成功后进程在 publish 前、confirm ACK 前、ACK 后 mark 前崩溃；pending recovery 能重发/补偿且不重复订单。
- Timeout：outbox 重复、delay queue 重启、晚到/早到、confirm 同时发生、定时补偿扫描与 MQ 同时执行。
- Check-in/Queue events：claim 与取消竞态、claim 提交后同步建票失败、claimed 事件与同步路径并发建票、checked-in 回告重复、serving 在 ticket 关联前到达、completed 重复、消费者停机后恢复、DLQ 原 eventId 重放。
- 对每个消费者断言 `message_consume_record`、业务唯一约束、状态 CAS 三层结果一致；不能只断言消费方法被调用。

## 6. 故障与恢复

| 故障注入 | 预期 |
| --- | --- |
| Redis 短时不可用 | 抢号不降级到 DB；已落库取消生成 release record；恢复后补偿 |
| RabbitMQ 不可用 | 预占有限重试后补偿；DB outbox 保留重试；无假成功 |
| MySQL 在消费中断开 | MQ 重试；无半事务订单/guard/outbox |
| resource 超时 | 新预热/变更失败关闭；既有 slot 超过容忍版本后暂停 |
| Redis stock key 丢失 | slot 暂停；对账分类 EVIDENCE_MISSING；不盲目 INCR/SET |
| 补偿 Lua 在 DB 终态后失败 | API 状态已终止；record PENDING；恢复后仅释放一次 |
| appointment claim 后 queue 建票失败 | 预约保持 CHECKED_IN；同键重试/claimed outbox 只补建一票；最终关联 ticketId |

## 7. 权限与安全矩阵

账号兼容回归 `existingShortPasswordCanLogInWithoutWeakeningUserCreation`：已存短密码正确登录 200、错误密码 401、空密码 400，ADMIN 创建用户使用短密码仍返回 400。验证登录校验与新建口令政策分离，不添加特殊用户名的认证旁路。

- JWT：缺失、过期、nbf、错误 iss/aud、篡改、未知 kid、alg=none、旧 tokenVersion、USER 冒充 ADMIN。
- Gateway：客户端伪造 `X-User-Id/Roles/Service` 被清除；白名单最小化；登录和抢号 429；CORS 非白名单拒绝。
- 水平越权：用户 A 查询/确认/取消用户 B 预约；查询 B ticket；QR userId 不匹配，均不泄露资源存在性。
- STAFF：未授权 outlet/window、session 属他人、ticket 属其他窗口、结束后操作。
- Internal：用户 access JWT 调内部 API、service JWT 错 audience/scope/过期、内部端点意外经 gateway。
- QR：改 appointment/outlet/user/exp/purpose/签名、旧 nonce、过期、跨网点、签到窗口外、截图重放。
- 输入：SQL 注入/XSS、非法枚举、超长 reason、负 quota、超大 page、批量上限、同幂等键异载荷。
- 日志/产物扫描：JWT、refresh token、二维码、私钥、手机号/证件号、`.env` 不得出现。

## 8. E2E 场景

### 常驻开发环境启动冒烟

`powershell -NoProfile -File scripts/dev.ps1 up` 启动常驻环境，随后执行 `.local/venv/Scripts/python.exe scripts/smoke_local.py`。该脚本只创建登录会话和读取数据，不改变订单/库存；验证五服务健康、Vite 代理、ADMIN 登录、管理查询、401/403，以及从开发 Nacos 取出的短时服务 JWT 访问 resource 内部接口。内部接口隔离必须直接测 8080 网关；Vite 的 `/internal` 不在代理范围，SPA fallback 的 HTML 200 不能当成 API 放行。

启动器回归：`.local/venv/Scripts/python.exe -m unittest discover -s scripts -p test_dev.py -v`，7 项测试覆盖已有密钥复用、已有库缺密钥拒绝、配置转义、失效心跳、Windows 状态文件占用重试与进程锁所有权。人工/实机门禁还需重复 up、stop 后端口释放、up 后原账号可登录、密钥摘要不变、Flyway 无新增失败记录。2026-09-27 本机上述检查均通过，并实际持有 state.json 3 秒验证心跳恢复；详细记录见 `LOCAL_STARTUP_EXECUTION.md`。此短冒烟不代替下述七步业务 E2E。

可执行入口：`python -u scripts/e2e_v1.py`；安装及端口见 `docs/LOCAL_DEVELOPMENT.md`，实际结果见 `docs/ai/E2E_EXECUTION.md`。脚本创建独立临时 MySQL/Redis/RabbitMQ/Nacos，真实启动五个 JAR，使用 Vite 代理及 Playwright Chromium；无 test profile、模拟 HTTP 或手改数据库。仅合成账号/角色与人员窗口授权由脚本初始化，其余业务资源通过 ADMIN API 创建。

Browser 验证三角色登录、资源可见、字符串雪花 ID、确认、二维码、外部 STAFF 扫码和 USER 重放同票、用户状态自动刷新、开工/叫号/办理/完成。由于既有 USER 号源查询尚未实现，第一次抢号使用同一 gateway 的真实 API；不声称完整点选号源 UI 已通过。第二笔不确认预约等待实际 300 秒，仅只读 SQL 观察后台过期，避免查询投影触发过期掩盖调度问题；再预约确认并等对账宽限期结束。

新增回归：`terminalCleanupKeepsConsumedStockAndCannotDeleteNewReservation` 在真实 Redis 验证终态不加库存、旧清理不删新占位；`committedCompletionRetriesRedisCleanupWithoutRepeatingDatabaseEffects` 验证 Redis 失败后已提交终态可重放。用户详情组件测试覆盖外部签到和 queue 已完成、appointment 尚未同步的延迟场景。

1. ADMIN 登录，创建并启用网点、事项、窗口、绑定、未来 slot。
2. 预热后校验 configVersion/total/remaining。
3. USER 抢号得到 CREATING，轮询到 PENDING_CONFIRM，在 5 分钟内确认。
4. 获取短时二维码并签到，重复签到得到同一 ticket。
5. STAFF 开 session、call-next、recall、start、complete；预约最终 COMPLETED。
6. 对账 expected=actual，操作日志/消息记录完整。
7. 新建第二个 PENDING_CONFIRM 不确认；TTL/DLX 或补偿扫描使其 EXPIRED，库存回补一次，用户可重新预约。
8. 另测 CALLED->MISSED 对应 appointment NO_SHOW。

## 9. 阶段验证命令基线

阶段 13 库存对账故障矩阵：

| 注入 | 报告/动作 |
| --- | --- |
| MQ 明确发布失败、Redis 补偿成功 | charged=1、successfulRelease=1、订单=0，CONSISTENT |
| 补偿暂败 | COMPENSATION_PENDING，不自动增加库存 |
| 重复消费 | 一张订单、一次消费，CONSISTENT |
| Redis stock key 丢失 | EVIDENCE_MISSING，不重建 |
| DB 有有效订单、Redis active key 缺失 | DB_ORDER_REDIS_ACTIVE_MISSING，仅告警 |
| DB 有有效订单、active guard 缺失 | EVIDENCE_MISSING，禁止修复 |
| 库存被错误增加 | 无未决事实且超宽限期时，Lua CAS 回正并写前后值审计 |
| 快照后发生 reserve/compensate/adjust | mutationSeq 不匹配，Lua 返回 STALE，重算报告 |

`StockReconciliationIntegrationTest` 使用真实 Redis 7.4 Testcontainers、H2 隔离业务表验证流程与权限/幂等；`AppointmentFlywayMySqlIntegrationTest` 使用全新 MySQL 8.4 schema 验证 V1–V5。`ResourceReconciliationCandidateTest` 实际执行候选 SQL 验证窗口过滤和游标分页；resource 的 MySQL 空库测试验证 V1–V4 与扫描索引。若 Docker 不可用，Redis/MySQL 用例跳过时不得宣称故障测试通过。调度扫描的 pageSize/maxSlotsPerRun/slotDelay 和跨轮游标推进尚需在部署规模下观察，避免重复扫描造成负载。

后续存在工程后按实际脚本执行并记录：

```text
mvn -U -DskipTests dependency:tree
mvn test
mvn verify
docker compose config
npm run lint
npm run typecheck
npm run test
npm run build
```

依赖骨架阶段还必须启动最小上下文，验证 Gateway WebFlux、OpenFeign、Nacos discovery/config、Sentinel 与 Boot/Cloud/Alibaba BOM 组合；仅 dependency:tree 无冲突不等于运行兼容。
