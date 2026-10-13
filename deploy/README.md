# 部署、升级与业务配置

首次在 Windows 上体验项目，按 [根 README](../README.md)操作即可。本文保留 Linux 运行、已有数据库升级和业务配置细节；本机源码调试见 [本机开发说明](local/README.md)。

## 1. Linux 服务器启动

服务器需要 **Docker Engine、Compose 2.20.3+、Git 和 OpenSSL**。安装方法见 [Docker 官方文档](https://docs.docker.com/engine/install/)。**（待补充：本项目已验证的最低 Docker Engine 版本和服务器硬件配置。）**

以下前三步在服务器的 **Bash 终端**执行，不要复制到 Windows PowerShell。

### 1.1 下载项目并进入根目录

```bash
git clone --branch main --single-branch https://github.com/2370796166/CivicFlow.git
cd CivicFlow
```

**成功标志：** 当前目录包含 `compose.yaml`、`README.md` 和 `deploy/compose/.env.example`。

### 1.2 生成配置

在项目根目录复制执行整段。自动生成随机密码，已有 `.env` 时不会覆盖：

```bash
if [ -f deploy/compose/.env ]; then
  echo '已有配置，保留原文件。'
else
  (
    set -e
    umask 077
    command -v openssl >/dev/null
    while IFS= read -r line || [ -n "$line" ]; do
      key=${line%%=*}
      case "$key" in
        *_PASSWORD|NACOS_AUTH_IDENTITY_VALUE)
          secret=$(openssl rand -hex 24)
          printf '%s=%s\n' "$key" "$secret" ;;
        NACOS_AUTH_TOKEN)
          secret=$(openssl rand -base64 48)
          printf '%s=%s\n' "$key" "$secret" ;;
        *) printf '%s\n' "$line" ;;
      esac
    done < deploy/compose/.env.example > deploy/compose/.env.generated
    mv deploy/compose/.env.generated deploy/compose/.env
    echo '配置已创建。'
  )
fi
```

**成功标志：** 显示“配置已创建”或“已有配置”。端口冲突时先按根 README 的配置表修改 `.env`。

### 1.3 构建、启动并查看账号

在项目根目录依次执行：

```bash
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
docker compose ps -a
cat .local/dev/login.txt
```

**成功标志：** 12 个长期服务健康，4 个初始化任务退出码为 0；首次管理员登录信息写入 `.local/dev/login.txt`。已有账号不会被重置。

### 1.4 从自己的电脑访问

默认端口仅绑定服务器的 `127.0.0.1`，直接打开 `服务器IP:5173` 不会访问到网页。可以通过 SSH 将服务器网页端口转到自己电脑：

在**自己的电脑**打开 PowerShell 或终端执行，将 `USER`、`SERVER_IP` 换成服务器 SSH 用户名与地址：

```bash
ssh -N -L 5173:127.0.0.1:5173 USER@SERVER_IP
```

按提示完成登录，窗口没有新输出是正常的，保持它开启。在自己电脑打开 [http://localhost:5173](http://localhost:5173)，使用服务器上查到的账号登录。本机 `5173` 端口需要空闲；修改过网页端口时同步修改转发目标。

本方式用于个人开发和演示，没有配置面向公众的域名、HTTPS 或生产发布流程。**（待补充：生产环境的域名、证书、资源容量和发布验收方案。）** 正常停止用 `docker compose stop`，保留 `.env`、`.local/dev` 和数据卷。

## 2. 已有项目升级

### 2.1 保留现有配置和数据

保留原 `deploy/compose/.env`、`.local/dev` 密钥和数据库卷，不要重新生成密钥，也不要通过 `docker compose down -v` 清理数据库。

从不含独立通知服务的版本升级时，在现有 `.env` 中补齐 `.env.example` 列出的四个变量：

| 变量 | 用途 |
| --- | --- |
| `CIVICFLOW_NOTIFICATION_DB_APP_USERNAME` | 通知服务运行账号；示例为 `civicflow_notification_app` |
| `CIVICFLOW_NOTIFICATION_DB_APP_PASSWORD` | 该账号密码；填写自行生成并保管的值 |
| `CIVICFLOW_NOTIFICATION_DB_MIGRATION_USERNAME` | 通知数据库建表/升级账号；示例为 `civicflow_notification_migration` |
| `CIVICFLOW_NOTIFICATION_DB_MIGRATION_PASSWORD` | 该账号密码；填写自行生成并保管的值 |

Compose 中的 `notification-db-init` 会在已有 MySQL 健康后准备通知数据库和账号。本机 `prepare` 也包含该步骤。随后按原来的启动方式重建应用；Docker 模式在项目根目录执行：

```bash
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
docker compose ps -a
```

### 2.2 先核对数据库迁移历史

Flyway 是项目自动创建和升级数据库表的工具。当前已发布迁移保持原样：预约服务沿用 V6 站内消息迁移并追加 V7 管理查询迁移；排队服务沿用 V4 同步租约迁移并追加 V5 审计索引；认证和资源服务分别追加 V4、V5 审计索引；独立通知服务从 V1 初始化。

如果旧运行目录已经使用另一套未发布迁移历史，必须先核对其 Flyway 历史和数据库结构，再决定升级方案；不能直接套用不同历史或修改已执行迁移。遇到迁移校验失败时，不要删除数据库或自行改历史记录来绕过检查。

## 3. 业务配置和接口说明

### 3.1 预约默认规则

| 配置 | 当前默认值 | 用途 |
| --- | --- | --- |
| `CIVICFLOW_BOOKING_ADVANCE_DAYS` | `30` | 可提前预约的天数 |
| `CIVICFLOW_CANCELLATION_NOTICE` | `0m` | 取消预约需要的提前量 |
| `CIVICFLOW_REMINDER_LEAD` | `1h` | 办理开始前提醒的提前量，用于旧预约消息接口 |
| `CIVICFLOW_APPOINTMENT_CONFIRM_DEADLINE` | `5m` | 创建预约后需要确认的期限 |
| `CIVICFLOW_APPOINTMENT_ASYNC_PUBLISH` | `true` | 后台发布预约事件；兼容同步方式可设为 `false` |
| `civicflow.appointment.rules.closed-dates` | 通过后端配置填写 | 停办日期，没有在 `.env.example` 中提供独立环境变量 |

以上值来自预约服务的 `application.yml`。办理开始前提醒与独立通知服务的确认截止前提醒不是同一项配置；当前确认提醒事件安排在 `confirmDeadline` 前 60 秒发送，见 `AppointmentNotificationOutbox`。

这是后端配置，不能只修改前端页面就改变规则。Docker 的应用配置由启动程序生成；新增业务环境变量需保证传入预约容器并被其配置读取，不能假定任意写入 `.env` 的变量都会自动生效。

预约提交成功只表示请求已受理，最终建单结果需要查询。请求限流、后台重试、库存余量和排队恢复由现有后端负责；不要在客户端把总名额当作剩余名额。当前预约入口按用户、用户加时段、时段三层限流，限额按网关实例计算，生产多实例的共享限额和容量仍需在目标环境验证。

预约规范事件先持久化，再由带租约的后台任务发布，等待消息确认不占用 HTTP 请求线程。后端还包含未签到自动记录未到场、对账历史、安全修复、跨服务审计、窗口效率统计和同步异常重试能力；这些不需要首次启动者逐项配置。

### 3.2 独立通知和旧预约消息

`civicflow-notification` 是独立通知服务，本机模式默认端口 `8085`，使用自己的 `civicflow_notification` 数据库。预约、排队服务通过事务 outbox（先在本地可靠保存、再向外发送的事件记录）向它提供通知事件。

用户的“站内通知”使用 `/api/v1/user/notifications`，包括预约创建、确认/取消、确认截止前提醒、叫号、即将到号和过号等。可选通知偏好使用 `/api/v1/user/notification-preferences`。必要通知不因关闭可选通知而停止。

旧 `/api/v1/user/messages` 接口与“预约消息记录”页面保留。两类消息的 ID、分页、已读和来源不同，不应合并成同一份列表或混用未读数量。短信、邮件和微信订阅消息未接入外部供应商。

### 3.3 管理查询与审计

管理员预约查询支持关键词、事项、号源和日期范围、用户姓名及办理时间线；保留 `serviceDate` 查询参数和 `/api/v1/admin/appointment-operation-logs`。

跨服务日志提供 `/api/v1/admin/operation-logs/{auth|resource|appointment|queue|stock}`。整合后的对账查询使用 `/api/v1/admin/reconciliations`，旧分页明细接口保留在 `/api/v1/admin/operations/reconciliations`。监控接入见 [监控说明](monitoring/README.md)。

## 4. 开发检查与诊断工具

Java 后端使用 JDK 17；本机开发工具和检查命令见 [本机开发说明](local/README.md#5-运行开发检查)。GitHub Actions 会运行后端、迁移和容器集成测试，以及 Web 检查。

`deploy/tests` 中的合成数据和诊断工具需要运行环境的受控凭据，不会在正常应用启动时自动执行，不要将真实凭据写进源码。单元测试、隔离容器测试和本机验证均不能代替生产发布验收。
