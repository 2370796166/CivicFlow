# CivicFlow 本地开发基础设施

本地 Compose 提供 MySQL 8、Redis、RabbitMQ（含管理界面）和 Nacos。全部宿主机端口只绑定 `127.0.0.1`，数据写入命名卷；它不是生产部署模板。

## 1. 准备环境变量

仓库只提交 `deploy/compose/.env.example`，其中的口令仅用于说明本地配置格式。复制后必须修改所有 `change-me` 值，并为 `NACOS_AUTH_TOKEN` 生成新的 Base64 密钥。真实 `.env` 已被 `.gitignore` 排除。

Windows PowerShell：

```powershell
Copy-Item deploy/compose/.env.example deploy/compose/.env
$bytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

通用 shell：

```bash
cp deploy/compose/.env.example deploy/compose/.env
openssl rand -base64 48
```

把生成值写入 `.env` 的 `NACOS_AUTH_TOKEN`。密码建议使用密码管理器生成；示例文件保持简单字符是为了能安全地被 PowerShell 和 POSIX shell 导入。

## 2. 启动与初始化

先确认 Docker Desktop/Engine 正在运行。两种 shell 使用同一套 Compose 项目和命名卷。

Windows PowerShell：

```powershell
docker compose --env-file deploy/compose/.env -f deploy/compose/compose.yaml up -d --wait mysql redis rabbitmq nacos
docker compose --env-file deploy/compose/.env -f deploy/compose/compose.yaml --profile init run --rm nacos-init
```

通用 shell：

```bash
docker compose --env-file deploy/compose/.env -f deploy/compose/compose.yaml up -d --wait mysql redis rabbitmq nacos
docker compose --env-file deploy/compose/.env -f deploy/compose/compose.yaml --profile init run --rm nacos-init
```

第二条命令初始化 Nacos 管理员密码，并幂等创建 `NACOS_NAMESPACE`（默认 `dev`）。Nacos 2.4+ 不再自带管理员默认密码，因此不能省略首次初始化。若命名卷已存在，Nacos 会保留原密码；此时应把 `.env` 恢复为原密码，不要删除卷。

MySQL 初始化脚本仅在 `civicflow-mysql-data` 为空时执行。它创建四个 `utf8mb4` schema：

| schema | 运行账号权限 | Flyway 账号权限 |
| --- | --- | --- |
| `civicflow_auth` | 本 schema 的 SELECT/INSERT/UPDATE/DELETE | 本 schema 的 DML + CREATE/ALTER/DROP/INDEX/REFERENCES |
| `civicflow_resource` | 同上 | 同上 |
| `civicflow_appointment` | 同上 | 同上 |
| `civicflow_queue` | 同上 | 同上 |

应用运行账号不能修改表结构，迁移账号也不能访问其他服务 schema。MySQL server 和 JDBC 会话固定为 UTC，保证 `DATETIME(3)` 存 UTC instant；`LocalDate`/`LocalTime` 业务字段仍按 `Asia/Shanghai` 解释。

## 3. 健康与连通性检查

查看 Compose 健康状态：

```powershell
docker compose --env-file deploy/compose/.env -f deploy/compose/compose.yaml ps
```

PowerShell 逐项检查：

```powershell
docker compose --env-file deploy/compose/.env -f deploy/compose/compose.yaml exec mysql sh -c 'mysqladmin ping --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" --silent'
docker compose --env-file deploy/compose/.env -f deploy/compose/compose.yaml exec redis sh -c 'redis-cli --no-auth-warning -a "$REDIS_PASSWORD" ping'
docker compose --env-file deploy/compose/.env -f deploy/compose/compose.yaml exec rabbitmq rabbitmq-diagnostics -q ping
Invoke-RestMethod http://localhost:8848/nacos/v1/console/health/readiness
```

通用 shell 逐项检查：

```bash
docker compose --env-file deploy/compose/.env -f deploy/compose/compose.yaml exec mysql sh -c 'mysqladmin ping --protocol=socket -uroot -p"$MYSQL_ROOT_PASSWORD" --silent'
docker compose --env-file deploy/compose/.env -f deploy/compose/compose.yaml exec redis sh -c 'redis-cli --no-auth-warning -a "$REDIS_PASSWORD" ping'
docker compose --env-file deploy/compose/.env -f deploy/compose/compose.yaml exec rabbitmq rabbitmq-diagnostics -q ping
curl -fsS http://localhost:8848/nacos/v1/console/health/readiness
```

控制台地址：

- RabbitMQ：`http://localhost:15672`，使用 `.env` 的 `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD`。
- Nacos：`http://localhost:8848/nacos/`，使用 `.env` 的 `NACOS_USERNAME` / `NACOS_PASSWORD`。

Compose 只创建 RabbitMQ 用户和 vhost，不创建业务 exchange、queue 或 binding。`docs/ai/EVENTS.md` 中的 durable 拓扑必须由 appointment/queue 应用启动时声明，并开启 publisher confirm、returns 和 mandatory；不允许依赖管理控制台点击。

## 4. 启动应用服务

Compose 的 `.env` 不会自动成为 Maven 进程环境变量。启动服务前，将它导入当前终端。

Windows PowerShell：

```powershell
Get-Content deploy/compose/.env | Where-Object { $_ -match '^[^#][^=]*=' } | ForEach-Object {
  $name, $value = $_ -split '=', 2
  [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}
mvn -pl civicflow-auth -am spring-boot:run
```

通用 shell：

```bash
set -a
. ./deploy/compose/.env
set +a
mvn -pl civicflow-auth -am spring-boot:run
```

其余服务把模块名替换为 `civicflow-resource`、`civicflow-appointment`、`civicflow-queue` 或 `civicflow-gateway`。应用健康端点为 `http://localhost:8080..8084/actuator/health`。

示例文件使用默认宿主机端口，与 `application-dev.yml` 的连接默认值一致。若修改 `MYSQL_PORT`、`REDIS_PORT`、`RABBITMQ_PORT` 或 `NACOS_PORT`，还应在当前终端覆盖对应的 `CIVICFLOW_<SERVICE>_DB_URL`、`REDIS_PORT`、`RABBITMQ_PORT` 或 `NACOS_SERVER_ADDR`。

## 5. Nacos 配置边界与命名

本地约定使用两个维度隔离配置：

| 环境 | Namespace ID | Group | Data ID |
| --- | --- | --- | --- |
| 本地开发 | `dev` | `CIVICFLOW_GROUP` | `civicflow-gateway.yaml`、`civicflow-auth.yaml`、`civicflow-resource.yaml`、`civicflow-appointment.yaml`、`civicflow-queue.yaml` |
| 测试/生产 | 独立 `test` / `prod` namespace | 可继续使用 `CIVICFLOW_GROUP` | 与服务名一致 |

`application-dev.yml` 通过 `spring.config.import=optional:nacos:${spring.application.name}.yaml` 读取当前 namespace/group，所以服务名决定 Data ID、namespace 决定环境。禁止让 dev/test/prod 共用 namespace。

以下内容保留在本地文件或环境变量：

- `spring.application.name`、profile、Nacos import、默认端口和 Actuator 暴露边界；
- 数据库/Redis/RabbitMQ/Nacos 地址的环境变量占位；
- 所有密码、JWT/JWS 私钥、pepper、Nacos auth token 等 secret。

以下非敏感、需要动态调整的配置可按服务逐步迁移到 Nacos：

- Gateway 路由、CORS 白名单和 Sentinel 限流规则；
- Feign 超时/熔断参数、任务批次大小、重试和对账阈值；
- 业务开关及非敏感的 TTL/窗口参数（仍须与 `REDIS.md`、`EVENTS.md` 契约同步）。

任何服务能启动所必需的安全基线应继续有本地默认或失败关闭行为；Nacos 不可用时不能用陈旧配置伪造业务成功。生产 secret 不以明文发布到 Nacos。

## 6. 停止与数据保留

停止容器但保留全部数据卷：

```powershell
docker compose --env-file deploy/compose/.env -f deploy/compose/compose.yaml down
```

通用 shell 命令相同。不要附加 `--volumes` 或 `-v`；这样 MySQL、Redis、RabbitMQ 和 Nacos 的已有数据会保留。仅暂停而不移除容器可使用 `docker compose ... stop`，恢复使用 `docker compose ... start`。

如果修改了已初始化卷中的账号密码，单纯重启不会重放初始化脚本。应在对应服务内显式轮换密码并保留现有卷，或为一次性、确认无价值的本地环境另行创建新的 Compose project/卷；本指南不执行卷删除。

## 7. 配置语法校验

配置校验不需要启动容器：

```powershell
docker compose --env-file deploy/compose/.env.example -f deploy/compose/compose.yaml config --quiet
```

通用 shell 命令相同。该命令只验证变量展开和 Compose 结构，不验证镜像可拉取或容器运行健康。
