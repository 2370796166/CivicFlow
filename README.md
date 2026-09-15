# CivicFlow（智序）

CivicFlow 是城市公共服务预约排队平台。本仓库当前提供 Java 17、Spring Boot 3 的 Maven 多模块后端骨架；本阶段不包含业务接口、业务表或前端实现。

## 模块

- `civicflow-common`：轻量响应与异常契约，不依赖 Web、MyBatis 或 Security。
- `civicflow-gateway`：WebFlux 网关、Nacos、Sentinel 与健康检查。
- `civicflow-auth`：认证服务骨架与独立数据源配置。
- `civicflow-resource`：资源服务骨架与独立数据源配置。
- `civicflow-appointment`：预约服务骨架，预留 Redis、RabbitMQ、Feign 与持久化依赖。
- `civicflow-queue`：排队服务骨架，预留 RabbitMQ、Feign 与持久化依赖。

## 本地要求

- JDK 17
- Maven 3.9+
- Docker Desktop（Windows）或 Docker Engine + Compose v2，用于启动 MySQL 8、Nacos、Redis 和 RabbitMQ。

完整启动和连通性检查见 [`docs/LOCAL_DEVELOPMENT.md`](docs/LOCAL_DEVELOPMENT.md)。测试 profile 仍使用 H2，并关闭外部服务连接。

## 编译与测试

```bash
mvn test
mvn verify
```

`verify` 会执行 Spotless 格式检查、编译和测试。各服务的测试 profile 使用 H2，并关闭 Nacos、Sentinel 与消息监听器的外部连接。

## 启动服务

先设置下方环境变量并启动所需基础设施，再从根目录执行，例如：

```bash
mvn -pl civicflow-gateway -am spring-boot:run
mvn -pl civicflow-auth -am spring-boot:run
```

默认 profile 为 `dev`。健康检查地址为 `http://localhost:<端口>/actuator/health`。默认端口：gateway `8080`、auth `8081`、resource `8082`、appointment `8083`、queue `8084`。

## 环境变量

所有敏感值只从环境注入，仓库不保存真实密码。

| 变量 | 使用方 | 默认值/说明 |
| --- | --- | --- |
| `NACOS_SERVER_ADDR` | 全部服务 | `localhost:8848` |
| `NACOS_NAMESPACE` | 全部服务 | `dev` |
| `NACOS_GROUP` | 全部服务 | `CIVICFLOW_GROUP` |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | 全部服务 | 空；按本地 Nacos 配置填写 |
| `SENTINEL_DASHBOARD` | gateway、appointment、queue | `localhost:8858` |
| `CIVICFLOW_<SERVICE>_DB_URL` | auth/resource/appointment/queue | 各服务独立 schema，连接会话固定 UTC |
| `CIVICFLOW_<SERVICE>_DB_APP_USERNAME` / `..._APP_PASSWORD` | 四个持久化服务 | 运行时 DML 账号 |
| `CIVICFLOW_<SERVICE>_DB_MIGRATION_USERNAME` / `..._MIGRATION_PASSWORD` | 四个持久化服务 | 仅本 schema 的 Flyway DDL+DML 账号 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | appointment | `localhost` / `6379` / 必填密码 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` / `RABBITMQ_VHOST` | appointment、queue | 本地 vhost 为 `civicflow`；账号密码来自 `.env` |
| `CIVICFLOW_GATEWAY_PORT` | gateway | `8080` |
| `CIVICFLOW_AUTH_PORT` | auth | `8081` |
| `CIVICFLOW_RESOURCE_PORT` | resource | `8082` |
| `CIVICFLOW_APPOINTMENT_PORT` | appointment | `8083` |
| `CIVICFLOW_QUEUE_PORT` | queue | `8084` |

## 依赖边界与维护性

版本统一由根 POM 的 BOM/属性管理，子模块不声明版本。Spring、Spring Cloud Alibaba、MyBatis-Plus、Flyway、Spotless 与测试工具均选择其官方维护的稳定发布；主体为 Apache-2.0 生态。MySQL Connector/J 遵循 GPLv2 with FOSS Exception，H2 为测试范围依赖。新增依赖前需重新审查职责、许可证和维护状态。
