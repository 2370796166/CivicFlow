# CivicFlow 开发进度

最后更新：2026-09-28

## 发布前质量加固（2026-09-28）

- 先运行基线与隔离复现，再仅修复已证实的五处问题：同 reservation 并发生成不同事件、迟到 publisher 覆盖 PERSISTED、手动 ACK 毒消息未进入 DLQ、默认 recoverer 记录完整 body/header、1000 VU 下默认 Tomcat 等待队列导致 100 个连接拒绝。后者对照试验通过后将 `accept-count=2048` 写入正式 appointment 配置，移除测试覆盖再回归。业务数据库无新迁移或新 API；新增真实 MySQL/Redis/RabbitMQ/k6、双窗口、新 JVM 恢复、QR 重放、安全输入回归。
- 初轮测试夹具的 UTC/JDBC、Lua 放号前预热、k6 0.52 语法限制均单独纠正。真实 Redis 故障提示客户端 503 后 Lua 仍可能执行，恢复必须按原请求收敛。代码、契约和完整量化记录见 [发布加固报告](RELEASE_HARDENING_2026-09-28.md)。
- `mvn -q spotless:apply verify` 退出 0，41 suite/144 测试，0 失败、错误或跳过；三轮最终 k6 为 1000 VU 受理 100/库存不足 900、100 VU 同用户不同键 1/活跃冲突 99、100 VU 同键 100 次同一幂等受理，均 0 非预期，最终订单 100/1/1、最低采样库存 0。前端 lint 0 错/311 既有警告，typecheck、25 单测、build 通过；启动器 8 单测、Compose config 通过。最终测试/恢复/五容器共 7 份日志模式扫描 0 命中；暂存 17 个文件的 JWT/JWS/私钥模式扫描 0 命中，均不等于完整 DLP 审计。性能环境、延迟和错误分类见报告。
- 发布阻断：生产多实例/gateway 全链路容量、进程切点强杀与跨节点恢复、DLQ 审计重放、TLS/KMS 和真实日志采集尚未在目标环境验收。非阻断：前端既有 lint 与 chunk 提示、Flyway MySQL 8.4 提示；本机 P95 不能外推生产 SLA。原有开发 Compose 未在本轮重建部署。下一步完成上述目标环境门禁，再决定首版放量。

## Gitee 交付（2026-09-27）

- 按用户要求仅提交必要代码、部署/IDEA 配置、迁移、测试脚本及两份 README；沿用此前 docs/AGENTS 本地保留约定，未提交设计文档或执行记录。补全缓存/构建产物忽略规则，README 改为可独立使用的启动与验证说明。
- 提交 `ddfa18c`，236 文件；暂存检查、敏感值/令牌/私钥及生成物检查通过，仅保留原有公开示例占位配置。本轮没有业务代码修改，复用上一阶段已完成的 Maven/Web/Compose/E2E 验证。
- `git fetch origin` 确认 main 无远程增量；首次 push 连接重置失败且远程未接收，使用单次 HTTP/1.1 重试推送成功（2f9cf0c→ddfa18c）。目标为用户指定的 gitee.com/projectOu/civic-flow main，未强制推送。

## 完整 Docker Compose 部署（2026-09-27）

- 根据用户明确要求，根 Compose 现在构建/运行五服务及 Nginx 前端；新增 backend/web/bootstrap Dockerfile、健康检查、依赖顺序、独立配置卷与初始化/服务身份刷新容器。原数据卷、密钥和 admin 账号保留，ADR-011 记录部署迁移；旧本机 IDEA 模式保留作为互斥选项。
- 实际 `docker compose up -d --build --wait --wait-timeout 300` 退出 0，11 常驻容器全部 healthy、3 初始化容器正常退出。密钥摘要未变，业务服务仅容器内监听，web/gateway 经回环端口访问。前端现为 Nginx 静态服务，不依赖 Vite 进程。
- 修复 Linux 构建副本 CRLF、Python 包元数据下载失败、Web lockfile 337 条缺失 resolved/integrity；包版本未变。Maven 下载多次截断，本机将已验证依赖导入 BuildKit cache 后容器编译成功，未声称空缓存联网构建通过。
- 本轮 Maven verify 117 测试、启动器 8 项回归、前端 25 单测通过；前端 lint 0 错误/311 条既有警告，容器内 npm ci/typecheck/build、Compose config 通过。
- 完整容器七步 E2E run 36BC8E97 通过：Nginx 三角色浏览器闭环、实际 300.72 秒超时、释放一次、再次预约、对账 expected=actual=1。随后 stop/up --build 全栈重启成功，再次冒烟通过；独立只读核验三单 COMPLETED/EXPIRED/CONFIRMED、排队 COMPLETED、业务日志、Redis 库存 1/configVersion 2 及原密钥均保留。新增可重复 `e2e_docker.py` 与 `smoke_docker.py`，详细命令与证据见 `DOCKER_EXECUTION.md`。
- 仍未验证实际 Windows 重启、全新机器/空镜像与 Maven 空缓存联网构建、生产密钥配送及全容器压力容量；已有本机模式压力记录不冒充容器容量。当前交付环境保持全容器运行，下一步只按目标环境补上述验收。

## 遗留验证补全（2026-09-27）

- IDEA 五个 Local 原生 Spring Boot 配置均逐项点击运行，五服务 health 200/UP；进程归属于 IDEA，完整 HTTP 冒烟经 Vite/gateway 通过，当前轮换服务令牌访问内部接口 200。Sentinel Dashboard 8858 仍未启动，控制台存在非阻塞 WARN，未验证控制台功能。
- 结束 IDEA 测试进程后恢复一键环境，五服务和前端全部 ready，最终 HTTP 冒烟再次通过；启动器 7 项回归、Compose config、Python 语法和 diff 检查通过。IDEA 中断 Gateway 时仍有 Nacos 销毁 WARN，已记录，未将优雅关闭标为无警告。

- 再次执行全仓 `mvn -q verify`：117 测试全部通过。七步完整前后端 E2E run 044CF999 通过，实际等待 300.7 秒过期、库存释放及再次预约成功，最终对账 diff=0。
- 新增可重复 `--load` 测试与 k6 夹具。run A48B162C：200 用户/50 VU 抢 50 库存，50 单、150 库存不足、0 非预期响应；100 次重复请求仍同一预约、库存为 0。内部 service JWT 6 种合法/拒绝场景通过。无降低鉴权或业务约束。
- 冷构建改用独立源码/target，E2E 检查可执行 JAR manifest；完善压力数据的窗口绑定及 Windows UTF-8 子进程输出。独立空 npm cache 安装和构建成功；无全局包/无 pip cache 的 Python 固定依赖安装成功。Maven 官方源两次插件下载超时，未标记通过。
- 新增实际重启证据脚本，before 成功、未重启的 after 正确拒绝。实际重启、全新机器/空镜像层及生产 secret 配送尚需用户安排或提供目标环境。详细命令、证据和范围见 `VALIDATION_2026-09-27.md`。

## 本地管理员账号调整（2026-09-27）

- 按用户明确要求，将本机合成管理员 `local_admin` 改名为 `admin` 并设置指定口令，保留原 userId、角色和业务关联。使用 BCrypt cost 12、version CAS，增加 tokenVersion，撤销活动 refresh token，并写入不含密码/摘要的安全审计。凭据只同步至 Git 忽略的 keys.json/login.txt。
- 启动器与冒烟脚本支持持久 `adminUsername`，缺省兼容旧配置；重启复用账号，不重新创建原用户名。登录 DTO 改为非空及长度上限验证，再校验已存哈希；创建用户仍保持最少 8 字符，契约同步 API/SECURITY。无新增 schema 或公开密码重置功能。
- `mvn -q -pl civicflow-auth -am spotless:apply verify` 最终成功：common 2、auth 13 测试全部通过。新增回归验证已有短密码正确登录、错误密码拒绝、空密码拒绝和创建用户仍拒绝短密码；首次回归的空密码断言误用仅支持 401 的 helper，修正为直接断言 400 后通过。
- 重启本地五服务和前端成功。真实 Chromium 从 `/login?redirect=/403` 使用更新后的账号登录返回 200，跳转 `/admin/outlets` 并显示网点管理。未修改前端业务代码，未重跑全仓/七步 E2E；本轮验证聚焦认证变更。

## IDEA 与常驻一键启动修复（2026-09-27）

- 接续 IDEA 导入修复，确认剩余启动问题为旧 Run 项缺少基础设施凭据/安全密钥、本机 MySQL 3307 与默认端口不一致、IPv6 localhost 与 Docker IPv4 绑定不一致。本机现有四容器凭据已与 `.env` 一致，实际 MySQL SELECT 1 成功；保留既有 `compose` project 和全部卷。
- 新增 `start-local.cmd`、`stop-local.cmd`、`scripts/dev.ps1/dev.py` 和 `.run` 共享配置。生成 Git 忽略的持久开发密钥、各服务配置与合成管理员；构建后按健康状态启动五服务/Vite，复制 JAR 避免 Windows 文件锁。已有库缺少密钥时拒绝静默重新生成。监督进程仅管理自己的子进程，重复启动不复制进程，支持准备 IDEA 单服务调试。
- service JWT 保持 300 秒期限，每 60 秒由本地监督进程向独立 Nacos group 刷新，保留签名、scope、serviceName 校验。原生 IDEA `Local Auth` 实际启动并返回 health 200/UP。IDEA 不支持初版 Shell Script 配置，改用内置 JAR runner；重新载入后四个共享入口不再显示红叉。
- Compose 加入 10 MB × 3 日志轮转、MySQL 真实认证健康检查，nacos-init 复用既有 Nacos 镜像，初始化可重复执行。Vite 默认代理和示例统一为 IPv4。
- 实测 prepare、完整 up、重复 up、stop、再次完整 up：五服务和前端均 UP，stop 后六个应用端口释放、四容器仍健康；重启前后密钥摘要相同，原管理员登录成功，四库 Flyway 共 15 条迁移全部成功。短时服务令牌已连续轮换超过 5 分钟，受保护 resource API 返回 200。
- 验证：`mvn -q verify` 退出 0，116 测试、0 失败/错误/跳过（真实 MySQL/Redis 隔离测试已执行）；启动器 7 项单测通过；真实 HTTP 冒烟验证 Vite→gateway→auth/resource/queue、401/403、内部路由隔离；Chromium 管理员登录及网点管理页成功。前端 lint 0 错误/311 条已有警告，typecheck、25 单测和 build 通过（主 chunk 仍大于 500 kB）。命令、结果与中间失败修复见 `LOCAL_STARTUP_EXECUTION.md`。
- 最终状态检查发现 Windows 读取 state.json 时偶发拒绝原子替换，原心跳线程退出而应用仍运行；增加重试、线程异常恢复、主循环独立停止检查，并以文件锁判定监督进程存活。实机持有状态文件 3 秒后心跳恢复、stop 成功，再次启动，新增两个回归覆盖该竞态及无心跳时的进程所有权。
- 本轮聚焦启动，不增加产品 API、数据库结构或业务功能，未重跑完整七步业务 E2E（阶段 15 的两次结果保留）。未验证电脑重启后的首次启动、网络全空缓存下载安装、五个服务分别在 IDEA 点击启动；已验证原生 IDEA Auth 和一键 JAR 启动全部。接下来按后续授权处理产品契约缺口；无需通过关闭鉴权启动。

## IDEA 本地启动排查（2026-09-26）

以下为历史排查记录，其中常驻运行配置与凭据待办已由上节完成。

- 后续直接操作 IDEA 复核并修复：13:02 完成 Maven 同步，gateway/queue 依赖从 0 恢复至 170/200，QueueApplication 的 12 个编辑器错误变为绿色检查。13:03 在 IDEA 执行“构建项目”，实际 javac 17.0.16 编译六个 Java 模块及测试源码，354 源文件，0 错误；1 个警告提示旧 backward reference index 格式更新，非业务代码错误。
- 将本项目构建脚本自动同步从 SELECTIVE 改为 ALL，已回读 `.idea/workspace.xml` 验证。未手工拼依赖、清空缓存、删除工作树或修改业务逻辑。IDEA 另有 EmmyLua 1.4.26 插件配置异常、镜像缺少部分 sources 附件的记录；当前不阻塞编译，未擅自卸载插件或改全局 Maven 设置。
- 13:06 索引更新后再次 IDEA 构建，日志确认 0 错误、0 警告。IDEA 仍提示默认注解处理配置未启用，已通过其“启用注解处理”按钮补齐默认配置（原六模块 Maven profile 已启用）。本轮只处理 IDE 导入/编译配置，五服务运行凭据问题仍按下述记录待完成。
- 2026-09-27 中断恢复后回读确认：IDEA 重启后 gateway/queue 仍保留 170/200 个依赖条目，自动同步 ALL 和 Default/Maven 两个已启用注解处理 profile 均已持久化。最后一次已执行的 IDEA 编译仍为 2026-09-26 13:06 的 0 错误/0 警告；本次没有将历史编译当作新运行结果。`git diff --check` 成功。

- 用户日志确认 Nacos 客户端访问 `localhost/[::1]:9848` 被拒绝；实测 IPv4 8848/9848 开放、IPv6 两端口拒绝，既有 Nacos 容器健康且仅绑定 127.0.0.1。五个 dev 配置、Compose 示例及本机 `.env` 的 Nacos 地址统一 IPv4，Redis/RabbitMQ 本地地址同样统一。
- 本机 MySQL 映射 3307，原 dev JDBC 固定 3306；四个服务默认 JDBC 改为读取 `MYSQL_PORT`，仍保留完整 URL 覆盖能力，未改数据库/卷/口令。
- 初次排查时 IDEA 外部模块配置中 gateway/queue 的依赖条目为 0，其他业务模块有 132–207 个；IDEA 日志记录编译 100 个错误。JDK 17、Lombok、模块注解处理正常；命令行 `mvn -q -DskipTests compile` 成功。该导入问题已由上述 IDEA 同步修复，未直接改 IDE 缓存。
- LOCAL_DEVELOPMENT 修正 reactor 中直接 `-am spring-boot:run` 的启动说明，补 IDEA 环境变量、密钥和临时 E2E 与常驻开发环境的区别。五个既有 Run 配置未注入凭据，应用安全密钥仍需配置；本轮未声称 IDEA 五服务全部启动成功。
- 连接配置修改后执行 `mvn -q -DskipTests compile`、Compose 示例 `config --quiet`、`git diff --check` 均成功；后续已补 IDEA 重新导入及编译验证。未重跑完整 E2E，未验证常驻 IDEA 全服务启动。

## 阶段 15：真实服务与前端联调（2026-09-26）

- 保留既有未提交实现，只修复实链阻断：gateway/appointment/queue 补 BOM 管理的 LoadBalancer；修正 Nacos 2.5.4 管理员初始化路径；补 gateway 的 STAFF 扫码路由；修复 COMPLETED/NO_SHOW 后 Redis active 占位残留。终态提交后比较删除，失败沿既有 queue_state_sync 重试，不返还已消费库存、不误删新 reservation，也不重复数据库审计。
- 修复用户详情未观察外部签到、异步完成的问题：对非终态详情持续读取，页面隐藏时降频、卸载时停止；queue 先完成时仍等待 appointment 收敛。补真实 Redis 旧清理/新占位测试、已提交终态 Redis 故障重试测试及前端延迟同步测试。
- 实链逐条审计发现办理同步 SERVICE 的 actor_id 为空；按既有契约补为 0，并增加 HTTP/事务集成断言。该字段在实链运行中的旧 JAR 仍为空，随后构建及回归验证修复；未手工改写历史日志。
- 新增 `scripts/e2e_v1.py`、集中 Python 测试依赖和 `civicflow-web/e2e/live-flow.mjs`：独立临时四基础设施、四空 schema/独立账号、五真实 JAR、三角色登录、API 初始化业务数据、Vite 代理/真实 Chromium。只初始化合成账号、角色与人员 scope；不手工改订单、库存或截止时间。服务令牌采用 5 分钟 TTL，并由隔离 Nacos 动态轮换；生产签发/secret 配送仍未收口。
- 第一轮完整运行 `7BEFB324` 通过：管理员配置、预热 3、异步抢号、浏览器确认/二维码/窗口流程、同用户再预约、真实 5 分钟超时、释放一次、再次预约确认、对账 `expected=actual=1,diff=0,CONSISTENT`；预约日志 5 条、排队票日志 4 条。最终脚本增加只读数据库等待后台超时、原始 Compose 初始化脚本重复执行、RBAC 拒绝、迁移/消息/Redis 证据断言，结果记录于 `E2E_EXECUTION.md`。
- 最终实链运行 `6549E847` 退出码 0：Nacos 初始化脚本连跑两次、四 schema 全新 Flyway、权限 401/403、三角色浏览器和全部七步通过。只读 SQL 观察后台过期；期限精确 300 秒，RabbitMQ timeout consumer 在本次 DB 截止后 8ms 持久化 EXPIRED。释放 1 次、再次预约确认成功；DONE 同步 2、建单消费 3、未发布 outbox 0、全部 DLQ 0，最终 CONSISTENT。临时容器已清理，脱敏结果为 `target/e2e/6549E847.json`。
- 审计字段追加后的最终 `mvn verify -q` 已再次通过（116 测试、0 失败/错误/跳过，全部模块打包成功）。中间一次 repackage 被 Windows 正在使用的 JAR 阻止，结束 E2E 后完整重跑通过；脚本已改用临时 JAR 副本，复制调整只做语法检查，未再执行整套长场景。
- 验证：`mvn verify -q`（Docker Desktop named pipe）成功，116 测试，0 失败/错误/跳过；真实 MySQL/Redis Testcontainers 已执行。前端 lint 成功（311 条已有 warnings）、typecheck 成功、14 文件/25 单测成功、build 成功（主 JS chunk 约 1.19 MB 提示仍在）。Compose config 与 `git diff --check` 通过。Windows Python 传 shell 文本会转换 CRLF，测试脚本已改为 UTF-8 原始字节。
- 已同步 API、LOCAL_DEVELOPMENT、TESTING、REDIS、ARCHITECTURE 和执行记录。新依赖的用途、集中版本和许可记录于 LOCAL_DEVELOPMENT。
- 遗留范围：普通用户号源查询 Controller 未实现，首笔抢号通过真实 gateway API，不能声称全 UI 点选号源闭环；管理员历史查询等既有缺口未新增。未执行生产规模压力、MISSED 浏览器分支或正式服务密钥轮换。旧 Compose 项目标签/密码不一致时采用隔离实例，未改旧卷。Docker 中断后的 socket 故障通过保留运行时目录备份恢复，未恢复出厂。
- 下一步：以本轮可重复脚本作为回归入口，按后续授权补 USER 号源查询与正式服务身份方案，再进行多实例故障/压力和发布门禁。

## 阶段 14：管理员后台现有 API 页面（2026-09-25）

- `civicflow-web` 新增 ADMIN 路由与导航，接入 resource 网点、事项、窗口的服务端分页查询、创建、编辑、启停、删除和窗口事项整体绑定；接入时间段/号源按日期分页查询、单日新增、草稿编辑、最多 31 天批量生成、状态切换和额度调整。号源日历作为日期选择器，实际数据按当日服务端分页结果显示；没有使用前端推算的库存。
- 接入 auth 用户分页查询、创建、禁用/启用和角色替换。表格有查询条件、明确空状态；表单即时检查格式、范围与必填项，最终业务规则仍由服务端裁决。批量生成提交前显示网点、事项、时段、日期和天数并要求确认，完成后展示 created/skipped/逐日 failed。高影响状态、额度、角色、高权限账号创建、绑定替换及安全修复操作使用确认框，提交期间互斥；409 版本、唯一、占用、时段重叠和消费额度冲突展示明确提示并刷新列表。网点详情只返回脱敏电话，编辑空值会清除现有电话，故前端在保存前明确提示并再次确认。管理写请求带 UUID `Idempotency-Key`，不记录或展示密码、JWT、密钥及未脱敏手机号。
- 接入 appointment 已实现的 `POST /api/v1/admin/reconciliations`：管理员可按 slotId 手动重跑，展示本次报告差异，只有本次报告标记 `autoRepairable` 时才允许二次确认后请求安全修复；是否修复由服务端 CAS 和权限检查决定。管理员路由要求 ADMIN，前端守卫仅用于界面导航。
- **后端契约缺口**：当前 appointment 无管理员预约查询 Controller；各服务无对外操作日志分页查询；对账只有单 slot POST，无报告历史列表/详情 GET；窗口事项绑定有整体替换 PUT，但无现有绑定 GET。相应入口明确提示不可查询，窗口绑定明确提示未选旧绑定会被移除；未伪造数据、调用未实现路径或扩大后端范围。补齐这些 API、权限、脱敏和分页契约后才能完成对应列表页及历史详情。未在真实 gateway/服务集群运行管理员 E2E。
- 验证：管理员资源、批量号源、单 slot 对账的组件测试以及时间转换/校验测试已补。`npm run lint` 成功（0 errors、311 条既有 Vue 模板格式 warnings）；`npm run typecheck` 成功；`npm run test` 成功（14 文件、24 测试）；`npm run build` 成功。`git diff --check` 通过（仅其他模块已有文件的 Windows 换行提示）。构建提示主 JS chunk 约 1.18 MB、超过 500 kB，后续可按路由拆包。未执行真实服务 E2E；后续需补齐上述 API、验证权限及变更冲突，再完成管理员端联调。

## 阶段 14：窗口人员工作台（2026-09-25）

- `civicflow-web` 增加 STAFF 专属 `/staff` 工作台，按 resource `GET /api/v1/staff/scopes` 返回的授权范围选择网点/窗口，读取 queue 本人当前会话/当前票。页面以大字号、高对比票号展示工作状态，使用原生可键盘操作的选择框/按钮与清晰焦点样式；移动布局按窄屏重排。前端路由继续要求 STAFF 角色，服务端仍承担 JWT、授权范围和票据归属校验。
- 接入 queue 已实现的开始/结束会话、叫下一号、重呼、开始办理、过号和完成接口。每项状态写入先显示明确确认文案，确认框打开和提交期间互斥禁用其他操作；请求使用 UUID `Idempotency-Key`，结果不确定时同一载荷保留原 key，先回读服务端状态。409 冲突回读当前票/会话并给出中文提示；回读失败时锁定操作，直至刷新成功。当前状态默认 4 秒轮询，可用 `VITE_STAFF_POLL_MS` 在 3–10 秒配置；页面隐藏时改为 15 秒。没有发现 STAFF SSE/WebSocket 契约。
- **后端接口缺口**：queue 的窗口会话状态只有 `ACTIVE/ENDED`，无暂停/恢复；STAFF 查询仅返回当前会话和当前票，无等待人数、已叫号历史或过号列表。ADMIN `/api/v1/admin/queues/overview` 只提供聚合且要求 ADMIN，不能替代 STAFF 明细。工作台明确标注这些数据不可用，没有伪造列表、人数或把 `ENDED` 误称为暂停。本阶段未修改后端契约/实现；补齐这些功能需先明确数据归属、分页与权限后增加后端 API。
- 新增 staff 类型、API 封装、状态操作规则及核心组件/接口测试，未引入新依赖。`npm run lint` 成功（0 errors、338 条已有与新增 Vue 模板格式 warnings）；`npm run typecheck` 成功；`npm run test` 成功（10 文件、18 测试）；`npm run build` 成功。构建仍提示主 JS chunk 超过 500 kB。未在真实 gateway/resource/queue/appointment 同启环境跑 STAFF E2E，仍需联调权限变更、双窗口竞争和服务 JWT 轮换。

## 阶段 14：普通用户端现有 API 流程（2026-09-25）

- `civicflow-web` 增加普通用户网点列表/详情、事项选择和日期选择；我的预约分页/状态筛选/详情；reservationId 结果查询页；待确认倒计时、确认/取消防重复提交；已确认预约的本地二维码生成、过期刷新；签到后排队号、前方人数和当前叫号展示。排队默认 4 秒轮询，可用 `VITE_QUEUE_POLL_MS` 在 3–5 秒内配置；页面隐藏后改为 15 秒。倒计时只作提示，操作与终态均以服务端响应为准。
- 确认/取消用稳定 `Idempotency-Key` 绑定当前版本，请求失败后先回读详情；结果未知时保留原 key 供重试。reservation 查询在服务端终态停止，最多自动查询 45 次；网络故障或达到次数后可用同一 reservationId 手动继续。Axios 继续共享一次 401 refresh，并将 403 导向无权限页。所有二维码 token 只在内存中转为本地 data URL，不进入日志、URL 或浏览器存储。新增 `qrcode`/`@types/qrcode`（本地安装包均标注 MIT 许可，用于浏览器本地生成二维码及其类型）并生成 `package-lock.json`。
- **未完成的阻断项**：`API.md` 声明了 `GET /api/v1/user/slots`，但当前 `civicflow-resource` 没有对应 Controller，也没有其他 USER 号源投影接口。按本轮“只调用已存在后端 API”的要求，前端不请求该未实现路径、不编造库存或放号状态；选定事项和日期后明确展示暂不可查询。因此时段选择、未放号/可预约/已约满/已停售状态、放号倒计时及从号源按钮发起抢号尚不能连成实际流程。`reserve` API 封装已按现有预约 Controller 接口准备，但没有在缺少 slotId 来源时暴露伪造入口。待后端提供普通用户号源投影后，需核对其真实响应 DTO，再接通号源卡片、按钮和 503 携带 reservationId 的跳转。阶段 14 总项保持未勾选。
- 验证：`npm install --offline=false --registry=https://registry.npmjs.org --fetch-retries=0 --fetch-timeout=15000` 成功；`npm run typecheck` 成功；`npm run lint` 成功（0 errors、271 条 Vue 模板格式 warnings）；`npm run test` 成功（7 文件、13 测试）；`npm run build` 成功。Vitest/Vite 的 esbuild 子进程在默认沙箱因 `spawn EPERM` 失败，允许本地 helper 运行后通过。构建提示主 JS chunk 超过 500 kB；后续可按路由拆包。安装输出提示 2 个 moderate npm audit 项及当前 Node 24.14.0 与 `nopt`/`abbrev` 的 engine 范围略有差距，需在发布门禁中复核。
- 下一步：补齐后端已声明的 USER 号源投影并提供明确可预约状态、剩余量及 releaseAt 字段后，完成选择时段和抢号入口；在真实网关/Redis/MQ/queue 环境下跑普通用户端到端流程。当前网关阶段和跨服务联调仍未完成，不能声称前端已通过 E2E。

## 阶段 14：前端基础工程（2026-09-25）

- 新建 `civicflow-web` Vue 3 + TypeScript + Vite 工程，接入 Element Plus、Pinia、Vue Router、Axios；建立 api、stores、router、views、components、types、utils 目录及统一设计令牌、响应式三角色入口骨架、登录页、403/404 和加载/空/错误状态组件。未填充业务页面。
- 对照 auth 真实 Controller/DTO 实现 `loginName/password` 登录、`refreshToken` JSON 轮换和 `/api/v1/user/me` 类型；access/refresh token 仅留内存，刷新页面重新登录。Axios 并发 401 共享刷新 Promise，旧 token 请求在刷新后到达时直接重试；失败清会话并回登录。GET 去重仅显式 opt-in；服务端 `requestId` 为权威追踪 ID。前端角色守卫与菜单只是体验层，服务端仍负责 RBAC 与归属检查。
- 提供 `.env.example`、本地 `/api` gateway 代理、基础状态组件与安全跳转测试、工程 README。前端依赖为 Vue/Element Plus/Pinia/Vue Router/Axios 及 Vite/TypeScript/ESLint/Vitest 等构建测试工具，均声明稳定 semver 范围，无 SNAPSHOT/动态版本；这些包由各自开源项目维护，许可证需在依赖安装后通过 lockfile 进一步审计。
- 验证受阻：`npm install` 因环境 npm 离线缓存缺少 `@eslint/js` 元数据返回 `ENOTCACHED`；`pnpm install --offline` 同样缺此元数据；尝试联网时代理指向 `127.0.0.1:9` 返回 `ECONNREFUSED`，绕过代理的请求也未能完成。因此尚无 `package-lock.json`。已尝试 `npm run typecheck`、`npm run lint`、`npm run test`、`npm run build`，分别因 `vue-tsc`、`eslint`、`vitest` 未安装而失败；`git diff --check` 通过（仅已有后端文件的 Windows 换行提示）。不能宣称四项检查通过。环境网络恢复后先运行 `npm install` 生成并提交 lockfile，再运行四项检查并修正任何工具链/类型问题。
- 遗留：auth 现阶段没有 HttpOnly Cookie/CSRF 方案，因此刷新页面后会失去登录状态；如需跨刷新保持登录，需要先修订安全契约并实现服务端 Cookie 方案。阶段 14 整体三角色业务页面尚未完成，清单保持未勾选。

## 阶段清单

- [x] 阶段 0：AI 开发契约与设计初始化
- [x] 阶段 1：Maven 多模块可编译骨架
- [x] 阶段 2：Docker Compose 本地基础设施
- [x] 阶段 3：Flyway DDL、枚举与错误码基线
- [x] 阶段 4：认证与 RBAC
- [x] 阶段 5：资源主数据管理
- [x] 阶段 6：时间段与号源配置
- [ ] 阶段 7：网关路由、双层鉴权与限流
- [x] 阶段 8：号源预热、Redis Lua 预占/补偿
- [x] 阶段 9：RabbitMQ 异步建单与查询投影
- [x] 阶段 10：确认、取消、TTL/DLX 超时
- [ ] 阶段 11：二维码与签到（实现完成，MySQL/RabbitMQ 实际联调待验证）
- [ ] 阶段 12：排队与窗口叫号（实现完成，跨服务联调待验证）
- [ ] 阶段 13：库存/签到对账与安全修复
- [ ] 阶段 14：Vue 3 工程与三角色页面
- [x] 阶段 15：跨服务 E2E 联调（现有 API 与页面闭环；USER 号源选择缺口仍属阶段 14）
- [ ] 阶段 16：并发、可靠性、安全与发布收口

## 阶段 13：appointment 号源对账与安全修复（2026-09-25）

- appointment 增加定时和管理员单 slot 对账。resource 内部 `reconciliation-candidates` 只返回已放号、仍在业务窗口的 slot，按 ID 游标分页；定时扫描默认每页 50、单轮最多 500、slot 间 50ms，并跨轮继续游标。resource V4 和 appointment V5 补逐 slot 查询索引。管理员 `POST /api/v1/admin/reconciliations` 强制 ADMIN 与 Idempotency-Key，按操作者/载荷绑定并复用已完成报告。
- 对账不变量：`expected = configuredTotal - chargedRequests + successfulReleases`，`diff = Redis.remaining - expected`；交叉校验 `chargedRequests - successfulReleases = persistedConsumed + pendingReserved + pendingRelease`，同时核对有效订单的 active guard、Redis active key、stock 版本/总量与 pending ZSET。报告含 expected/actual/diff、分类、检测时间、可修复标记，并写 run/detail。2 分钟内的状态变化只观察；长期未决预占、补偿、证据缺失、版本跳跃或用户键缺失留告警。
- 只自动下调证据完整且 `actual > expected` 的错误增量。Lua 比较 configVersion、原 remaining、total、mutationSeq 和 pending 为空后才写入，运行中变化则放弃并重算；不重建丢失的 stock key，不自动补 active key，不对库存偏低盲目加回。所有尝试先写 ATTEMPTED 管理审计，成功后写 APPLIED 与前后值、依据、操作者；中断遗留 ATTEMPTED 待核查。reserve/compensate/adjust 脚本均递增 mutationSeq。
- 真实 Redis 7.4 故障测试覆盖 MQ 发布失败且补偿成功、补偿暂败、重复消费事实、stock key 丢失、有效订单 active key/DB guard 丢失、错误增加库存、CAS 并发变更、消息宽限期，以及管理员权限/幂等。MySQL 8.4 全新 schema 验证 resource V1–V4 与 appointment V1–V5。`mvn -q -pl civicflow-resource,civicflow-appointment -am verify`（`DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`）：通过；resource 18、appointment 54 个测试，均 0 失败/错误/跳过。追加的 `ResourceReconciliationCandidateTest` 用 H2 实际执行候选 SQL，验证分页、OPEN/SUSPENDED 入选和 DRAFT 排除；`mvn -q -pl civicflow-resource -am test '-Dtest=ResourceReconciliationCandidateTest' '-Dsurefire.failIfNoSpecifiedTests=false'` 通过。resource 新增 Testcontainers JUnit/MySQL 测试依赖，由根 BOM 管版本，MIT 许可，专用于空库迁移验证。`git diff --check` 无空白错误（仅 Windows 换行提示）。
- 遗留：阶段 13 的签到/queue 跨服务对账未在本任务范围内，因此阶段总项保持未完成；阶段 7 网关低频限流尚未落地。多实例定时扫描可能重复读取同一 slot，但 Lua CAS 保证修复不会双写；上线前应按实例数配置调度或租约。`docs/` 仍受 `.gitignore` 忽略，提交这些文档需显式加入。下一步完成签到对账与真实跨服务 E2E，并验证网关管理限流。

## 阶段 12 实现与验证（2026-09-25）

- queue 增加本人今日排队票/进度、窗口会话、叫下一号、重呼、过号、开始办理、完成业务，以及管理员网点队列聚合概况。进度只给 `WAITING_ORDER_ONLY` 与前方人数，不虚构分钟预测。resource 内部 scope 返回当前启用窗口与事项，queue 每次窗口写操作按工作人员、窗口、事项和票归属复核。
- 叫下一号在 queue 本地 `READ COMMITTED` 短事务执行 `SELECT ... FOR UPDATE SKIP LOCKED`，按 `priority DESC, checked_in_at ASC, id ASC` 领取，随后用原状态/version CAS 写 CALLED 并同事务写操作日志。真实 MySQL 8 双连接测试发现默认 `REPEATABLE READ` 下两个不同票的状态索引更新仍会锁等待，改为 `READ COMMITTED` 后通过。工作会话以 `active_window_session_guard` 防双开，尚有 CALLED/SERVING 票时不能关闭或领取下一号。`MISSED` 保持终态，不支持重排。
- V3 新增窗口命令幂等表和 `queue_state_sync`；同键同载荷复用结果、同键异载荷冲突。办理状态和同步记录同一事务落库；定时任务按同票顺序经 service JWT Feign 调用 appointment 内部接口，appointment 校验关联 ticketId 并对 `CHECKED_IN -> SERVING/NO_SHOW`、`SERVING -> COMPLETED` 做 CAS，重复目标状态直接成功。失败保留待重试记录，不持 queue 票锁调用网络。会话操作与空队列叫号也记 `queue_operation_log`。
- `mvn -q -pl civicflow-queue -am verify`：通过（H2 功能/并发测试；未传 DOCKER_HOST 时 MySQL Testcontainers 自动跳过）。设置当前 Docker Desktop 的 `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine` 后重新运行：通过，MySQL 8.4 Testcontainers 全新 schema Flyway V1–V3 和双连接 `SKIP LOCKED`/状态更新实际执行。`QueueStateSyncTaskTest` 覆盖预约接口失败后重试。
- 补充 appointment 内部办理状态 HTTP/JWT 集成测试，覆盖服务身份、scope、票号关联、非法转换、重复 SERVING/COMPLETED/MISSED 和有效预约 guard 清理；resource 内部 STAFF scopes 集成测试覆盖服务鉴权、启用窗口及事项过滤。新增 queue Feign 本地 HTTP 契约测试，验证 resource/appointment 两个接口路径、JSON 字符串 ID、各自 Bearer token 和追踪头。修正此前与本轮实际请求 ID 重建规则不符的预约测试，并把依赖固定日期的号源测试改为北京时间未来日期及毫秒精度；预约办理状态 SQL 使用与现有 mapper 一致的 `CURRENT_TIMESTAMP(3)`。
- `mvn -q -pl civicflow-resource,civicflow-appointment,civicflow-queue -am verify`（设置上述 DOCKER_HOST）：通过；resource、appointment、queue 及其依赖模块的完整检查、测试和打包完成，appointment/queue 的 MySQL Testcontainers 实际运行。`mvn -q -pl civicflow-queue -am test '-Dtest=QueueFeignContractIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：通过。尚未在同时运行的真实三服务与签名 service JWT 上做端到端联调。
- 部署阻断项：当前 queue 从环境变量读取固定 service JWT；`SECURITY.md` 要求令牌寿命 1–5 分钟，而 auth 尚无服务令牌签发/轮换接口。固定令牌到期后，resource 授权查询与 appointment 状态同步都会失败。需要在安全契约中决定短期令牌签发/轮换方式，并在真实服务联调验证；不能用长期有效的静态 JWT 绕过要求。部署时 queue→appointment JWT 还需 `appointments.queue-state` scope，queue→resource JWT 需 `resource.windows.read` scope，并监控 `queue_state_sync` 积压。固定优先级排序在持续高优先级到达下仍可能使低优先级长期等待，需按 ADR-008 压测并另立 ADR 才可引入老化策略。
- 下一步：落实 service JWT 的短期签发/轮换和真实三服务联调、故障恢复/积压监控，再决定勾选阶段 12；随后进入阶段 13 对账与安全收口。

## 阶段 11 完成内容（2026-09-24）

- appointment 增加本人 CONFIRMED 预约签到 token 接口。短时 HS256 JWS 使用独立 256 bit secret，载荷含预约/用户/网点、随机 192 bit nonce、iat/exp/purpose；仅保存 nonce HMAC、到期与 kid，重签使旧 token 失效。resource 内部 slot 快照追加 checkInStart/checkInEnd，签发及 claim 均校验北京时间服务日期、网点和签到窗口。非 test 环境缺少专用签名密钥启动失败。
- queue 增加 USER 自助签到和 STAFF 扫码签到；STAFF 先以 service JWT 查询 resource 网点授权。queue 调用 appointment 受保护内部 claim，appointment 以 `CONFIRMED -> CHECKED_IN` CAS 与取消竞争，并在同一事务写版本化 claimed outbox。重复有效 token 复用 claim 与原排队票。
- queue 追加 V2 `queue_ticket_counter`，在建票本地事务内锁定网点/日期计数行，生成 `A001` 等可读票号；`queue_ticket.appointment_id` 与展示号唯一约束兜并发。票与 `checkin_reconciliation_record` 同事务落库，关联 appointment 的 Feign 失败会定时重试。同步 claim 成功但建票失败或响应丢失时，appointment claimed outbox 经 publisher confirm 发往 queue 补建；queue consumer 重复投递仍复用唯一票。
- queue 加 JWT 双层校验和服务身份 Feign 传递，公开请求 ID 在服务内重新生成，内部调用传递 traceId。`/internal/v1/appointments/**` 限定 service JWT 的 `appointments.checkin` scope 和 `serviceName=civicflow-queue`；resource 授权接口限定 `resource.windows.read` scope 与同一服务身份。新增 queue Spring Security 与 Spring Retry 依赖，均由 Spring Boot BOM 管理，Apache-2.0 许可。
- 同步更新 ARCHITECTURE、DOMAIN、API、SECURITY、DATABASE、EVENTS；本仓库 `/docs/` 被 `.gitignore` 忽略，文档虽已在工作区更新，提交时需显式加入或调整忽略规则。

## 阶段 11 验证与遗留风险

- `mvn -q -pl civicflow-appointment,civicflow-queue,civicflow-resource -am verify -Dtest=CheckInTokenServiceTest,QueueApplicationTest,CheckInTicketIntegrationTest,CheckInLinkRecoveryTest,ResourceInternalSlotIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`：通过；覆盖签名篡改、过期、错误用户/网点、窗口外签到、重签失效、重复 claim、H2 唯一票与并发发号、关联失败重试和 resource 内部接口鉴权。
- `mvn -q -pl civicflow-queue -am test -Dtest=CheckInAuthorizationTest -Dsurefire.failIfNoSpecifiedTests=false`：通过；验证 STAFF 无网点授权时不发起预约 claim。
- `mvn -q -pl civicflow-appointment,civicflow-queue spotless:apply` 与 resource Spotless apply：已执行。完整 reactor `mvn test` 曾因 Docker engine named pipe 不可用而无法完成 Testcontainers；MySQL 全新 schema Flyway V2、RabbitMQ 真 broker outbox/consumer 和跨服务 E2E 仍需可用基础设施验证。
- 2026-09-24 再次执行 `docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yaml ps`，Docker Desktop Linux engine named pipe 不存在；未能补做 MySQL/RabbitMQ 真环境验证。
- 当前二维码 key 只接受单个 kid；生产轮换前须支持旧 key 在未过期窗口内验签。claimed 事件消费有限重试后进入 DLQ，需运维告警和重放；queue 关联重试记录应监控积压。阶段 12 的叫号、办理状态事件未在本轮实现。
- 下一步：Docker 恢复后在全新 MySQL 8 queue schema 执行 Flyway V1+V2 并复启校验；用真实 RabbitMQ 验证 claimed outbox publisher confirm、同步响应丢失补建、重复投递与 DLQ/重放，再完成跨服务 API E2E。通过前阶段 11 保持未勾选。

## 阶段 10 完成内容

- 实现本人确认/主动取消接口，`Idempotency-Key` 按用户、操作、预约、version 和规范化载荷绑定；同键异载荷拒绝。确认仅允许未到数据库确认截止的 PENDING_CONFIRM，重复确认返回 CONFIRMED。主动取消允许未过确认截止的 PENDING_CONFIRM，或在北京时间预约时段开始前且未签到的 CONFIRMED；重复取消返回当前 CANCELLED/EXPIRED 终态。
- 确认、取消、超时均使用带原状态、version/截止条件的数据库 CAS。取消和超时赢家同事务删除 active guard、写操作日志及唯一库存释放记录；事务提交后执行 Lua 返还，Redis 暂败写入失败/待对账状态并由定时扫描重试，订单不会回滚。CREATE、CONFIRM、CANCEL、EXPIRE 日志记录操作者类型/ID、原/新状态、原因与 traceId。
- 建单事务新增完整 `appointment.confirm.timeout.requested` outbox 信封；声明 300000ms RabbitMQ TTL delay queue、DLX check queue 和 DLQ。publisher confirm/return 后标记 outbox，未知结果以同一 eventId 重发；早到按剩余 TTL 重排，晚到直接检查。消费者校验事件与本地订单，执行 `status=PENDING_CONFIRM AND confirm_deadline<=DB_NOW` CAS，另有 DB 扫描兜底。
- 追加 Flyway V4 `appointment_command_idempotency`，同步更新 DOMAIN、DATABASE、API、EVENTS；H2 隔离 schema 和契约测试补齐新表/outbox。

## 阶段 10 验证

- `mvn -q -pl civicflow-appointment -am verify`：成功；common 2 个、appointment 36 个测试，0 失败、0 错误、0 跳过。包含 MySQL 8.4 Testcontainers 空 schema Flyway V1–V4、Redis 7.4 Lua 和确认/取消/超时状态机测试。
- 状态机测试覆盖确认与超时竞态、取消与超时竞态、重复超时消息、重复取消、超期确认/取消、Redis 暂败后的补偿重试、非法状态转换、非本人操作、确认后取消截止、TTL/DLX 队列声明，以及 outbox 发布器 ACK 后状态落库。`git diff --check` 无空白错误（仅 Windows LF/CRLF 提示）。
- `mvn -q verify`：未通过；在无关 resource 模块的两处已有格式差异被 Spotless 拦截，未运行到 appointment。未改写该模块已有工作树修改。

## 阶段 10 遗留风险/后续

- RabbitMQ 拓扑与 publisher/consumer 由配置和隔离测试验证，尚未执行真实 RabbitMQ broker 的 TTL/DLX 端到端投递测试；部署联调需核验消息到期、publisher confirm/return 和故障重发。库存释放的 Redis Lua 使用现有 Redis 集成测试验证。
- CONFIRMED 取消截止本阶段定为北京时间时段开始；若产品需要更早截止，须新增 ADR 并迁移公开契约。全仓 verify 的 resource 格式差异需由该模块变更方处理。

## 阶段 8-9 完成内容

- 打通 `POST /api/user/appointments/reservations`（并兼容 `/api/v1`）到 Lua 预占、RabbitMQ 发布、异步建单和 reservationId 轮询。`appointment_reservation_request` 用 `(userId,idempotencyKeyHash)` 唯一约束绑定 payload hash 和稳定 reservationId，保存可信 resource 快照、原始事件 JSON、发布状态与恢复租约；它是技术投影，不是预约订单。
- 抢号消息使用完整版本化信封与 `eventId=reservationId`，持久化消息、mandatory、publisher confirm/return 和 CorrelationData。明确 NACK/return 才执行有审计记录的幂等补偿；confirm 超时、连接/future 异常统一为 `PUBLISH_UNKNOWN`，不误补偿，并由数据库短租约扫描复用同一事件恢复发布。
- reservation consumer 手动 ACK，容器有限重试后进入 `cf.appointment.reservation.create.q.dlq`；不支持的 schema、消息/可信快照冲突和已补偿的永久业务拒绝不重试。建单 Service 在单一本地事务创建 `PENDING_CONFIRM` 订单、5 分钟 deadline、`active_booking_guard`、操作日志、消费幂等记录并更新 request 投影，`appointment_order.reservation_id` 和 message 唯一约束共同支撑重投幂等。
- 数据库有效预约唯一冲突和永久业务校验失败先执行/记录可重试库存补偿，再继续抛异常，未吞掉失败。`stock_release_record.reservation_id` 与 compensation marker/Lua 双重防重；补偿暂败进入 `COMPENSATION_PENDING` 并由恢复扫描重试。
- 实现 reservation 查询、本人预约分页和详情；查询同时以 JWT userId 限定，访问他人 reservation/appointment 返回 404。`CREATING`、`FAILED` 仅为查询投影，订单创建后返回 `PENDING_CONFIRM` 及订单内容。
- resource 内部 slot 快照增加网点/事项名称，供 appointment 保存不可变历史快照。新增 Spring Retry 仅用于 Rabbit listener 的有限重试，版本由 Spring Boot BOM 管理，Spring Retry 为 Spring 官方 Apache-2.0 依赖。
- 同步更新 ARCHITECTURE、DOMAIN、DATABASE、API、EVENTS、REDIS 与 ADR-006；EVENTS 明确列出 Redis 与 MQ 非原子边界、每个故障窗口和恢复路径。本轮按要求未实现确认、取消或确认超时调度/outbox。

## 阶段 8-9 验证

- `mvn -pl civicflow-appointment -am test`：成功；common 2 个、appointment 24 个测试，0 失败、0 错误、0 跳过。包含 MySQL 8.4 Testcontainers 全新 schema Flyway V1-V3、Redis 7.4 Lua 集成测试，以及 8 个主链路集成场景。
- 主链路测试覆盖重复 HTTP、发布明确失败补偿、发布结果未知不误补偿、数据库恢复租约单赢家且复用 eventId、重复 MQ、消费者事务提交后 ACK 崩溃重投、数据库有效预约唯一冲突补偿，以及本人/他人越权查询。
- `mvn -pl civicflow-resource -am test`：成功；common 2 个、resource 13 个测试，共 15 个，0 失败、0 错误、0 跳过，确认内部快照新增名称未破坏资源服务。

## 阶段 8-9 已知风险/后续

- 本轮以 mock publisher/listener 直调覆盖应用可靠性分支，并以配置/代码验证 confirm、return、mandatory、手动 ACK 和 DLQ；尚未增加真实 RabbitMQ Testcontainers 的 broker 级 confirm/return/DLQ 集成测试，留待阶段 16 收口。
- 长期 `PUBLISH_UNKNOWN` 必须保持库存而不能自动补偿，以避免消息其实已到 broker 时超卖。恢复会持续同 eventId 重发；超过 Redis reservation 证据 TTL 仍未收敛时需要阶段 13 对账/人工裁决，不能猜测释放。
- 阶段 7 网关路由、伪造头清洗与限流仍未完成；业务服务已做 JWT 与本人归属防护，但公开部署前仍须完成网关双层防护。
- 阶段 10 的确认、取消、TTL/DLX 超时 outbox 与过期扫描没有在本轮提前实现；当前只写 `confirm_deadline=createdAt+5m`。

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
- （阶段 3 时待定，阶段 10 已解决）CONFIRMED 预约用户取消截止规则已定为北京时间预约时段开始前。
- 产品需确认 STAFF 过号后是否允许人工回队；当前首版 MISSED/NO_SHOW 为终态。
- 上线前需确定实名字段、数据保留期限、管理员 MFA、KMS/secret 与服务间 mTLS 方案。当前未创建证件号列；若产品确认采集，必须追加迁移并使用 cipher/hash/key-version 三列模式。
- （阶段 3 结束时风险，已由阶段 6 解决）`resource_slot` 的普通唯一索引只能阻止完全相同时段；重叠区间必须在 Service 实现中锁定 `(outlet,item,date)` 后检查，不能退化为“先查再插”。
- MyBatis 在 mapper 尚未进入实现阶段时仍会报告“未发现 Mapper”警告，符合本轮不创建空接口/Controller 的范围。
- Redis `{itemId:serviceDate}` 热点分片阈值仍需由压测确定。

## 下一阶段

Docker engine 可用时先补做 auth `V1+V2+V3` 及其余三个全新 MySQL schema 的 Flyway 冒烟并记录结果；随后进入阶段 5，只实现 resource 主数据管理，不提前实现预约或排队业务流程。
