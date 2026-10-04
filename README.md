# CivicFlow（智序）

CivicFlow 是城市公共服务预约排队平台，包含五个 Java 17 / Spring Boot 3 服务和 Vue 前端。

## Docker Compose 启动全部容器

只需 Docker Desktop（Linux containers）及 Compose v2.20+。首次克隆先复制 `deploy/compose/.env.example` 为同目录 `.env`，修改基础设施密码；已有 `.env` 与 `.local/dev/keys.json` 必须保留。

```bash
docker compose up -d --build --wait --wait-timeout 300
```

从仓库根目录运行。Compose 在镜像内构建五个 Java 服务和 Vue 前端，启动 MySQL、Redis、RabbitMQ、Nacos、五服务、Nginx 前端，以及初始化/服务身份刷新容器。应用运行无需本机 Java、Maven、Node 或 Python。访问 **http://localhost:5173**，本机管理员 **admin / 1234**；新建本地环境默认同名账号，已有账号不会被重置。

- 主入口：[`compose.yaml`](compose.yaml)，服务定义：[`deploy/compose/compose.yaml`](deploy/compose/compose.yaml)。Dockerfile 位于 [`deploy/docker`](deploy/docker)。
- Windows 双击 `start-docker.cmd` 等价于上面的 Compose 命令；`stop-docker.cmd` 停止全部容器。
- 查看：`docker compose ps -a`；日志：`docker compose logs -f gateway appointment`；停止：`docker compose stop`。
- 更新代码后重新执行带 `--build` 的启动命令。不要用 `down -v` 处理启动失败，密钥文件须与数据库同时保留。
- 首次从之前的本机运行模式切换：先 `powershell -NoProfile -File scripts/dev.ps1 stop`，并停止 IDEA 启动的服务，以释放 8080/5173。两种模式不要同时运行。
- 容器用 DNS 名和内部端口通信；只有 web/gateway 和本地基础设施管理端口绑定宿主机回环地址，auth/resource/appointment/queue 不发布宿主机端口。

## Windows / IDEA 本机调试模式（可选）

准备 Java 17、Maven 3.9+、Node、Python 3.10+、Docker Desktop（Linux containers），在项目根目录双击 **`start-local.cmd`**。

首次会安装本地启动器依赖、构建 JAR、启动 Compose、生成持久开发密钥、执行 Flyway，并启动五个服务和前端。打开 **http://localhost:5173**；本地管理员账号保存在 `.local/dev/login.txt`，不输出到日志或提交 Git。

新克隆的项目先执行一次上述入口（只准备调试环境可执行 `powershell -NoProfile -File scripts/dev.ps1 prepare`），生成 IDEA 所需的本地启动 JAR。

- 停止应用：双击 `stop-local.cmd`，保留 Docker 容器和数据卷。
- 状态：`powershell -NoProfile -File scripts/dev.ps1 status`。
- IDEA 单服务调试：先运行 `CivicFlow - Prepare IDEA`，再运行/调试 `Local Auth`、`Local Resource`、`Local Appointment`、`Local Queue`、`Local Gateway`。这些共享配置自动加载各自的本地配置文件。
- IDEA 一键运行全部：选择 `CivicFlow - Start all`。准备完成后，重复启动会报告已运行，不重复占端口。
- 密钥位于 `.local/dev/keys.json`，与开发数据库一起保留；不要通过删除 `.local` 处理报错。应用日志位于 `.local/dev/*.log`。

一键模式通过本机 Java/Vite 进程启动应用，Docker 负责四个基础设施，适合 IDEA 调试。`Prepare IDEA` 只启动基础设施和短时服务令牌刷新进程，不抢占应用端口。旧的无配置 `*Application` 运行项请切换到上述 `Local ...` 项。

## 模块

- `civicflow-common`：轻量响应与异常契约，不依赖 Web、MyBatis 或 Security。
- `civicflow-gateway`：WebFlux 网关、Nacos、Sentinel 与健康检查。
- `civicflow-auth`：登录、JWT、用户与角色管理。
- `civicflow-resource`：网点、事项、窗口、号源与人员授权。
- `civicflow-appointment`：库存预占、异步建单、确认、超时和对账。
- `civicflow-queue`：签到、排队票、窗口工作台和预约状态同步。

## 本地要求

- JDK 17
- Maven 3.9+
- Docker Desktop（Windows）或 Docker Engine + Compose v2，用于启动 MySQL 8、Nacos、Redis 和 RabbitMQ。

启动步骤见上方 Docker Compose 和本机调试章节。测试 profile 使用 H2；MySQL/Flyway、Redis 等集成测试使用真实 Testcontainers，需要 Docker 可用。

## 编译与测试

```bash
mvn test
mvn verify
```

`verify` 会执行 Spotless 格式检查、编译和测试。各服务的测试 profile 使用 H2，并关闭 Nacos、Sentinel 与消息监听器的外部连接。

前端检查：

```bash
npm --prefix civicflow-web ci
npm --prefix civicflow-web run lint
npm --prefix civicflow-web run typecheck
npm --prefix civicflow-web run test
npm --prefix civicflow-web run build
```

可选完整容器 E2E 测试客户端需 Python 3.10+、Node.js 20.19+ 或 22.12+；先安装 `scripts/requirements-e2e.txt` 中的 Python 依赖、前端 npm 依赖，再在 `civicflow-web` 中执行 `npx playwright install chromium`。完整 Compose 运行后，从仓库根目录执行：

```bash
python scripts/smoke_docker.py
python -u scripts/e2e_docker.py
```

冒烟检查容器健康、登录、代理和权限；E2E 自动创建唯一合成账号/资源，执行确认、签到、办理、真实五分钟超时、库存释放及对账，约需 8 分钟。合成数据保留用于审计，不需要手工修改数据库。结构化结果写入 Git 忽略的 `target/e2e/`。首次 Maven/镜像下载需要可访问依赖源；已验证本机完整容器运行和重启，但未验证全新机器空缓存下载及生产部署。

## 启动服务

先设置下方环境变量并启动所需基础设施，再从根目录执行，例如：

```bash
mvn -pl civicflow-auth -am install -DskipTests
mvn -pl civicflow-auth spring-boot:run
```

默认 profile 为 `dev`。健康检查地址为 `http://localhost:<端口>/actuator/health`。默认端口：gateway `8080`、auth `8081`、resource `8082`、appointment `8083`、queue `8084`。

## 环境变量

手动启动需注入所有凭据与应用密钥；一键启动器生成的配置位于 Git 忽略的 `.local/dev`，仓库不提交真实密码。

| 变量 | 使用方 | 默认值/说明 |
| --- | --- | --- |
| `NACOS_SERVER_ADDR` | 全部服务 | `127.0.0.1:8848` |
| `NACOS_NAMESPACE` | 全部服务 | `dev` |
| `NACOS_GROUP` | 全部服务 | `CIVICFLOW_GROUP` |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | 全部服务 | 空；按本地 Nacos 配置填写 |
| `SENTINEL_DASHBOARD` | gateway、appointment、queue | `localhost:8858` |
| `CIVICFLOW_<SERVICE>_DB_URL` | auth/resource/appointment/queue | 各服务独立 schema，连接会话固定 UTC |
| `CIVICFLOW_<SERVICE>_DB_APP_USERNAME` / `..._APP_PASSWORD` | 四个持久化服务 | 运行时 DML 账号 |
| `CIVICFLOW_<SERVICE>_DB_MIGRATION_USERNAME` / `..._MIGRATION_PASSWORD` | 四个持久化服务 | 仅本 schema 的 Flyway DDL+DML 账号 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | appointment | `127.0.0.1` / `6379` / 必填密码 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` / `RABBITMQ_VHOST` | appointment、queue | 本地 vhost 为 `civicflow`；账号密码来自 `.env` |
| `CIVICFLOW_GATEWAY_PORT` | gateway | `8080` |
| `CIVICFLOW_AUTH_PORT` | auth | `8081` |
| `CIVICFLOW_RESOURCE_PORT` | resource | `8082` |
| `CIVICFLOW_APPOINTMENT_PORT` | appointment | `8083` |
| `CIVICFLOW_QUEUE_PORT` | queue | `8084` |

## 依赖边界与维护性

版本统一由根 POM 的 BOM/属性管理，子模块不声明版本。Spring、Spring Cloud Alibaba、MyBatis-Plus、Flyway、Spotless 与测试工具均选择其官方维护的稳定发布；主体为 Apache-2.0 生态。MySQL Connector/J 遵循 GPLv2 with FOSS Exception，H2 为测试范围依赖。新增依赖前需重新审查职责、许可证和维护状态。
