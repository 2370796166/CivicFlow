# 在服务器上启动、更新项目

2026-10-14 更新提示：web/gateway 增加独立入口网络，默认 `172.30.250.0/29`，web 使用 `172.30.250.2`，gateway 使用 `172.30.250.3`。若与宿主机现有网络冲突，在 `.env` 同时设置 `CIVICFLOW_INGRESS_SUBNET` 和该子网内不同的 `CIVICFLOW_INGRESS_WEB_IP`、`CIVICFLOW_INGRESS_GATEWAY_IP`；应用更新时正常重建 web/gateway 即可，业务数据卷不迁移。gateway 保持 `server.forward-headers-strategy=none`，限流代码仅信任指定的 web /32。外置代理部署需自行配置精确 `CIVICFLOW_GATEWAY_TRUSTED_PROXIES`，代理必须覆盖单值 X-Forwarded-For。

管理写操作现在在线依赖 auth：停用/撤销 ADMIN/修改 tokenVersion 后旧令牌不能继续写；auth 不可用时管理写返回 503，恢复后沿用原幂等键重试。通知发布使用独立线程与 5 秒批次预算，不影响普通补偿调度。

升级 appointment/queue 时先停止旧版本通知 publisher，再启动新版，避免旧版不带 attempts 校验的完成更新与新版并行；未完成 outbox 行会由新版按原租约恢复，eventId 不变。多实例管理写权限策略应在所有业务实例更新完成后验收，不把混合版本当作已完成撤权加固。

这份说明用于把项目运行在 **Linux 服务器**上。第一次在自己的 Windows 电脑上体验，请按 [根 README](../README.md)操作；需要改代码或用 IDEA 调试，请看 [本机开发说明](local/README.md)。

按你的情况阅读：

| 你想做什么 | 从哪里开始 |
| --- | --- |
| 第一次在服务器上启动 | 第 1 节，按 1.1 → 1.2 → 1.3 → 1.4 操作 |
| 更新已经运行的项目 | 第 2 节，先保留原配置和数据，再更新 |
| 调整预约规则或查接口 | 第 3 节，启动成功后按需阅读 |
| 运行代码检查 | 第 4 节，首次启动不用执行 |

“部署”在这里就是：下载项目、准备配置、启动程序，再确认网页能登录。本文的 Docker 方式会自动准备数据库，不需要手动建表。

## 1. Linux 服务器启动

服务器需要 **Docker Engine、Compose 2.20.3+、Git 和 OpenSSL**，并且能联网下载程序和依赖。Docker 负责运行应用；Git 负责下载和更新代码；OpenSSL 用来生成随机密码。Docker 安装方法见 [官方文档](https://docs.docker.com/engine/install/)。**（待补充：本项目已验证的最低 Docker Engine 版本和服务器硬件配置。）**

先通过 SSH 登录服务器。下面 1.1–1.3 的命令都在**服务器的 Bash 终端**执行；1.4 才回到自己的电脑操作。

安装工具后，在服务器执行：

```bash
docker version
docker compose version
git --version
openssl version
```

**成功标志：** 都能显示版本，`docker version` 同时有 `Client` 和 `Server`，Compose 版本至少为 2.20.3。若提示 Docker 权限不足，先按安装文档为当前用户配置使用权限。

### 1.1 下载项目并进入根目录

```bash
git clone --branch main --single-branch https://github.com/2370796166/CivicFlow.git
cd CivicFlow
```

**成功标志：** 当前目录包含 `compose.yaml`、`README.md` 和 `deploy/compose/.env.example`。

从现在开始，服务器上的命令都在这个 `CivicFlow` 文件夹里执行，后文称它为“项目根目录”。重新登录服务器后，要先回到此文件夹。

### 1.2 生成配置

在项目根目录复制下面整段，一次粘贴并执行。它会生成配置文件 `deploy/compose/.env`，自动填好随机密码，无需逐个手填。如果文件已经存在，就保留原文件：

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

**成功标志：** 显示“配置已创建”或“已有配置”，没有报错。首次安装且默认端口可用时，无需再改配置；需要修改时，用你熟悉的文本编辑器打开 `deploy/compose/.env`，参考 [配置说明](../README.md#32-哪些配置需要自己改)。这个文件含密码，不要上传到 GitHub。

### 1.3 构建、启动并查看账号

**先检查配置。** 在项目根目录执行：

```bash
docker compose config --quiet
```

**成功标志：** 没有报错，通常也没有输出。如果提示文件或变量缺失，先检查上一步的配置，再继续。

**再构建并启动。** 仍在项目根目录执行：

```bash
docker compose up -d --build --wait --wait-timeout 300
```

首次运行会下载程序并打包源码，进度输出较多，等待命令结束。下载或构建失败时，先查看报错和网络情况；服务就绪等待超时时，参考文末的排错说明。

**最后检查状态、查看账号。** 仍在项目根目录执行：

```bash
docker compose ps -a
cat .local/dev/login.txt
```

**成功标志：** 12 个长期服务显示 `healthy`，4 个准备任务显示 `Exited (0)`。`Exited (0)` 表示准备工作正常结束，不是失败；完整名单见 [状态说明](../README.md#51-看服务状态)。

登录文件会显示 `Username:` 和 `Password:`，记下冒号后面的内容，下一步登录网页要用。已有账号不会被重置；后来改过密码时，使用新密码。

### 1.4 从自己的电脑访问

默认网页只接受来自服务器本机的访问，直接打开 `服务器IP:5173` 无法访问。下面用 SSH 建立一个连接，让自己的电脑可以打开服务器上的网页，无需修改项目端口或公开数据库。

在**自己的电脑**打开 PowerShell 或终端执行，将 `USER`、`SERVER_IP` 换成服务器 SSH 用户名与地址：

```bash
ssh -N -L 5173:127.0.0.1:5173 USER@SERVER_IP
```

按提示输入服务器的登录信息。窗口没有新输出是正常的，保持它开启。在自己电脑打开 [http://localhost:5173](http://localhost:5173)，使用 1.3 查到的项目账号登录。

**成功标志：** 网页能打开，登录后看到管理员菜单。首次安装没有业务数据，列表为空是正常的；可按 [演示步骤](../README.md#53-可选体验一次预约和办理)创建数据。

如果自己电脑的 `5173` 已被占用，可以把转发命令中的第一个 `5173` 改成 `5174`，然后打开 `http://localhost:5174`。如果修改过服务器的 `WEB_PORT`，则把最后一个 `5173` 改为该端口。关闭 SSH 窗口会断开网页访问连接，服务器上的项目仍在运行。

本方式适合个人开发和演示。要给其他人通过域名长期访问，还需要单独配置域名、HTTPS 和对外入口。**（待补充：生产环境的域名、证书、资源容量和发布验收方案。）**

### 1.5 停止和再次启动

回到**服务器的项目根目录**执行，不是在自己电脑执行：

| 想做什么 | 命令 |
| --- | --- |
| 停止项目，保留数据 | `docker compose stop` |
| 下次重新启动 | `docker compose up -d --wait --wait-timeout 300` |
| 查看状态 | `docker compose ps -a` |
| 查看最近的报错 | `docker compose logs --tail 100` |

保留 `.env`、`.local/dev` 和 Docker 数据卷。“数据卷”就是 Docker 存放数据库等持久数据的位置，不会因为正常停止服务而清空。

## 2. 已有项目升级

### 2.1 保留现有配置和数据

升级前先备份数据库，并保留原 `deploy/compose/.env`、`.local/dev` 和 Docker 数据卷。`.env` 里有连接数据库所用的密码，`.local/dev` 里有登录和服务通信所用的密钥；换成一套新值可能让旧数据无法正常使用。**（待补充：本项目配套的数据库备份与恢复操作流程。）**

不要重新生成这些配置，也不要执行 `docker compose down -v`，它会删除数据卷。

从不含独立通知服务的版本升级时，在现有 `.env` 中补齐 `.env.example` 列出的四个变量：

| 变量 | 用途 |
| --- | --- |
| `CIVICFLOW_NOTIFICATION_DB_APP_USERNAME` | 通知服务运行账号；示例为 `civicflow_notification_app` |
| `CIVICFLOW_NOTIFICATION_DB_APP_PASSWORD` | 该账号密码；填写自行生成并保管的值 |
| `CIVICFLOW_NOTIFICATION_DB_MIGRATION_USERNAME` | 通知数据库建表/升级账号；示例为 `civicflow_notification_migration` |
| `CIVICFLOW_NOTIFICATION_DB_MIGRATION_PASSWORD` | 该账号密码；填写自行生成并保管的值 |

填写后，`notification-db-init` 准备任务会在 MySQL 启动成功后自动创建通知数据库和账号。本机开发方式的 `prepare` 命令也会做这件事，无需手动建库。

如果项目是按 1.1 用 Git 下载的，先在**服务器的项目根目录**更新代码：

```bash
git pull --ff-only
```

**成功标志：** 命令正常结束，显示更新内容或 `Already up to date.`。如果提示本地修改冲突或不能快进更新，先处理本地改动，不要强制覆盖。

然后检查配置、重新构建、查看状态。Docker 模式依次执行：

```bash
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
docker compose ps -a
```

**成功标志：** 长期服务为 `healthy`，准备任务为 `Exited (0)`，网页能用原账号登录。源码开发模式仍按 [本机开发说明](local/README.md)重新启动。

### 2.2 先核对数据库迁移历史

Flyway 是项目自动建表和升级表结构的工具。它会记录已经执行过哪些升级脚本，所以旧脚本执行后不能随意修改。

<details>
<summary>给维护人员：查看当前数据库升级脚本版本</summary>

当前已发布迁移保持原样：预约服务沿用 V6 站内消息迁移并追加 V7 管理查询迁移；排队服务沿用 V4 同步租约迁移并追加 V5 审计索引；认证和资源服务分别追加 V4、V5 审计索引；独立通知服务从 V1 初始化。

</details>

如果旧运行目录已经使用另一套未发布迁移历史，必须先核对其 Flyway 历史和数据库结构，再决定升级方案；不能直接套用不同历史或修改已执行迁移。遇到迁移校验失败时，不要删除数据库或自行改历史记录来绕过检查。

## 3. 业务配置和接口说明

本节供需要调整业务规则或对接接口的人查阅。首次启动到网页登录成功即可，无需逐项配置本节内容。

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

这些规则由后端执行。Docker 的应用配置由启动程序生成；如果要增加配置，需同时检查它是否传给预约服务、预约服务是否读取它。只在 `.env` 里增加一行，不保证规则会生效。

预约提交成功只表示请求已受理，最终建单结果需要查询。请求限流、后台重试、库存余量和排队恢复由现有后端负责；不要在客户端把总名额当作剩余名额。当前预约入口按用户、用户加时段、时段三层限流，限额按网关实例计算，生产多实例的共享限额和容量仍需在目标环境验证。

预约事件先保存在数据库，再由后台任务发送，网页请求无需一直等待消息确认。后台任务会记录自己正在处理的事件，避免其他任务重复处理。后端还会记录未到场、核对库存和订单、记录各服务的操作日志、统计窗口效率，并重试同步失败的状态；首次启动不需要逐项设置这些功能。

### 3.2 独立通知和旧预约消息

`civicflow-notification` 专门处理通知，本机模式默认端口 `8085`，使用自己的 `civicflow_notification` 数据库。预约和排队服务先把待发送通知保存在各自数据库，再在后台发送给它，减少因临时故障而丢失通知的情况。这种机制在代码中称为 outbox。

用户的“站内通知”使用 `/api/v1/user/notifications`，包括预约创建、确认/取消、确认截止前提醒、叫号、即将到号和过号等。可选通知偏好使用 `/api/v1/user/notification-preferences`。必要通知不因关闭可选通知而停止。

旧 `/api/v1/user/messages` 接口与“预约消息记录”页面保留。两类消息的 ID、分页、已读和来源不同，不应合并成同一份列表或混用未读数量。短信、邮件和微信订阅消息未接入外部供应商。

### 3.3 管理查询与审计

管理员预约查询支持关键词、事项、号源和日期范围、用户姓名及办理时间线；保留 `serviceDate` 查询参数和 `/api/v1/admin/appointment-operation-logs`。

跨服务日志提供 `/api/v1/admin/operation-logs/{auth|resource|appointment|queue|stock}`。整合后的对账查询使用 `/api/v1/admin/reconciliations`，旧分页明细接口保留在 `/api/v1/admin/operations/reconciliations`。监控接入见 [监控说明](monitoring/README.md)。

## 4. 开发检查与诊断工具

Java 后端使用 JDK 17；本机开发工具和检查命令见 [本机开发说明](local/README.md#5-运行开发检查)。GitHub Actions 会运行后端、迁移和容器集成测试，以及 Web 检查。

`deploy/tests` 中的合成数据和诊断工具需要运行环境的受控凭据，不会在正常应用启动时自动执行，不要将真实凭据写进源码。单元测试、隔离容器测试和本机验证均不能代替生产发布验收。

## 5. 启动失败时怎么查

在服务器的项目根目录先执行 `docker compose ps -a`，找到没有启动成功的服务，再看日志。例如网关有问题时执行：

```bash
docker compose logs --tail 100 gateway
```

| 看到的情况 | 下一步 |
| --- | --- |
| `starting` | 程序还在准备，稍后再查状态 |
| `unhealthy`、反复重启、`Exited` 后为非零数字 | 查看对应服务日志，处理其中的报错 |
| 等待就绪超时，但程序还在正常准备 | 执行 `docker compose up -d --wait --wait-timeout 600` 延长等待 |
| 网页打不开，但服务器服务健康 | 检查 1.4 的 SSH 窗口是否仍开着，网页端口是否与转发命令一致 |
| 更新后数据库认证失败 | 核对是否保留了与原数据库配套的 `.env`，不要随机更换数据库密码 |
| 更新后数据库升级脚本校验失败 | 按 2.2 核对历史，不要删库或改历史记录来绕过校验 |

更多常见报错见 [根 README 的常见问题](../README.md#6-常见问题)。
