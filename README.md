# CivicFlow（智序）

## 1. 项目简介

智序是一个公共服务预约与排队项目，可以体验一次完整的办理流程：**用户预约 → 确认预约 → 到场签到 → 员工叫号 → 办理完成**。

| 使用者 | 可以做什么 |
| --- | --- |
| 普通用户 | 选择网点和事项、预约时段、签到、查看排队进度和站内通知 |
| 窗口人员 | 在授权窗口叫号、开始办理、完成办理 |
| 管理员 | 管理账号、网点、事项、窗口和预约名额，查询办理记录 |

**第一次使用，推荐用 Docker 启动整套网页和后端。** Docker 会运行项目需要的程序和数据库，你不用另外安装 Java、Maven、Node.js 或 Python，也不用手动建数据库。

下面以 **Windows + PowerShell** 为例。只想先把项目启动起来，按第 2–5 节依次操作即可。IDEA 调试见 [本机开发说明](deploy/local/README.md)，Linux 运行和升级见 [部署说明](deploy/README.md)，微信小程序见 [小程序 README](civicflow-miniapp/README.md)。

## 2. 环境准备

### 2.1 安装并打开 Docker

按 [Docker Desktop 的 Windows 安装说明](https://docs.docker.com/desktop/setup/install/windows-install/) 安装 Docker Desktop，按安装提示启用 WSL 2，并在需要时重启电脑。然后打开 Docker Desktop，等待它启动，使用 **Linux 容器**模式。

在开始菜单打开 **Windows PowerShell**，依次执行：

```powershell
docker version
docker compose version
```

**成功标志：** 第一条同时显示 `Client` 和 `Server` 的版本；第二条显示 Compose 版本。项目使用 `include` 功能，要求 **Compose 2.20.3 或更新版本**，依据见 [Docker 官方说明](https://docs.docker.com/compose/how-tos/multiple-compose-files/include/)。

还需要能下载 Docker 镜像和项目依赖的网络。**（待补充：本项目整套服务已验证的最低 CPU、内存和可用磁盘要求。）** 操作系统和 Docker 自身要求以安装说明为准。

### 2.2 下载项目，进入正确的文件夹

打开 [GitHub 项目页面](https://github.com/2370796166/CivicFlow)，选择 **main** 分支，点击 **Code → Download ZIP**，下载后解压。不要直接在压缩包中运行。

找到同时包含 `README.md`、`compose.yaml`、`start-docker.cmd` 的文件夹，这就是后文所说的**项目根目录**。复制它的完整路径，在 PowerShell 中执行：

```powershell
# 把引号里的路径换成你解压后的项目路径
cd "D:\projects\CivicFlow-main"
Get-Item .\compose.yaml
```

**成功标志：** 能看到 `compose.yaml` 的文件信息。

**后面所有 Docker 和配置命令，都在这个项目根目录执行。** 不要进入 `deploy/compose` 再照抄命令；根目录的 `compose.yaml` 已经关联了实际部署文件。

### 2.3 了解项目会自动启动哪些依赖

下表用于查找端口冲突。Docker 会按配置下载和启动它们，**不需要手动安装**。

| 程序 | 用途 | 配置中的镜像版本 | 默认电脑端口 |
| --- | --- | --- | --- |
| MySQL | 保存账号、网点和预约等数据 | `8.4.11` | `3306` |
| Redis | 保存预约余量等临时数据 | `7.4.11-alpine` | `6379` |
| RabbitMQ | 在后台传递预约、排队和通知消息 | `4.1.8-management-alpine` | `5672`，管理页面 `15672` |
| Nacos | 让后端服务找到彼此并读取配置 | `v2.5.4` | `8848`，通信端口 `9848` |
| 网页 | 浏览器中的操作页面 | 从本仓库构建 | `5173` |
| Gateway（网关） | 统一接收后端请求 | 从本仓库构建 | `8080` |

六个后端应用和内部通信配置也会自动准备。默认端口只绑定 `127.0.0.1`，即运行项目的这台电脑；不需要将数据库端口开放到公网。

## 3. 配置修改

### 3.1 首次运行：自动生成配置

项目需要 `deploy/compose/.env` 文件来保存启动账号、密码和端口。`.env.example` 只是配置示例，直接复制会保留示例密码；首次使用请在**项目根目录的 PowerShell**中复制并执行下面整段命令。

这段命令会生成随机密码和 Nacos 配置值，**不会覆盖已有 `.env`**。无需逐个填写数据库密码。

```powershell
if (Test-Path 'deploy/compose/.env') {
    Write-Host '已有配置，保留 deploy/compose/.env，请继续启动。'
} else {
    $configLines = Get-Content 'deploy/compose/.env.example' -ErrorAction Stop
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $newConfig = foreach ($line in $configLines) {
            if ($line -match '^([A-Z][A-Z0-9_]*PASSWORD|NACOS_AUTH_IDENTITY_VALUE)=') {
                $configKey = $Matches[1]
                $secretBytes = New-Object byte[] 24
                $random.GetBytes($secretBytes)
                $secretValue = [BitConverter]::ToString($secretBytes).Replace('-', '')
                "$configKey=$secretValue"
            } elseif ($line -match '^NACOS_AUTH_TOKEN=') {
                $tokenBytes = New-Object byte[] 48
                $random.GetBytes($tokenBytes)
                'NACOS_AUTH_TOKEN=' + [Convert]::ToBase64String($tokenBytes)
            } else {
                $line
            }
        }
        $configPath = Join-Path (Get-Location).Path 'deploy/compose/.env'
        [IO.File]::WriteAllLines($configPath, [string[]]$newConfig, [Text.UTF8Encoding]::new($false))
        Write-Host '配置已创建。启动成功后，到 .local/dev/login.txt 查看登录账号和密码。'
    } finally {
        $random.Dispose()
    }
}
```

**成功标志：** 看到“配置已创建”或“已有配置”，且下面命令能找到文件：

```powershell
Get-Item .\deploy\compose\.env
```

`.env` 应在 `deploy/compose` 文件夹中，不是项目根目录。不要把它或后续生成的 `.local` 目录提交到 GitHub。

### 3.2 哪些配置需要自己改

**全新安装、默认端口没有冲突时，不需要再改配置，直接进入第 4 节。** 只有确实需要调整时，才编辑刚生成的 `deploy/compose/.env`：

| 配置项 | 默认值或生成方式 | 何时需要改 |
| --- | --- | --- |
| `CIVICFLOW_LOCAL_ADMIN_USERNAME` | `admin` | 仅首次创建前修改；3–64 位，以小写字母开头，只用小写字母、数字和下划线 |
| `CIVICFLOW_LOCAL_ADMIN_PASSWORD` | 上一步生成的随机密码 | 仅在首次建账号前按需设置；已有账号不会因此重置密码 |
| `WEB_PORT` | `5173` | 网页端口已被其他程序占用时，例如改成 `5174` |
| `GATEWAY_PORT` | `8080` | 网关端口冲突时，例如改成 `8086` |
| `MYSQL_PORT`、`REDIS_PORT`、`RABBITMQ_PORT`、`RABBITMQ_MANAGEMENT_PORT` | 见第 2.3 节 | 对应电脑端口冲突时 |
| `NACOS_PORT`、`NACOS_GRPC_PORT` | `8848`、`9848` | 端口冲突时；两个端口须保持相差 `1000` |
| `MYSQL_ROOT_PASSWORD`、各服务数据库密码、`REDIS_PASSWORD`、`RABBITMQ_PASSWORD`、`NACOS_PASSWORD` | 上一步生成 | 首次运行无需再改；已有数据时不能随意换成新值 |
| `NACOS_AUTH_TOKEN`、`NACOS_AUTH_IDENTITY_VALUE` | 上一步生成 | 首次运行无需再改 |

这里的端口是“电脑如何访问容器”的端口，容器内部连接仍由项目配置管理。修改 `WEB_PORT` 后，浏览器也要使用新的端口。

**如果是升级已有项目，请保留原 `.env`、`.local/dev` 和数据库数据卷。** 缺少 `CIVICFLOW_NOTIFICATION_DB_*` 配置时，按 [升级说明](deploy/README.md#2-已有项目升级)补齐四个变量，不要用新配置覆盖旧文件。

## 4. 启动步骤

### 4.1 检查配置

确认 Docker Desktop 已启动。在**项目根目录**执行：

```powershell
docker compose config --quiet
```

**成功标志：** 没有报错，命令执行结束。若提示缺少变量或文件，先按第 3 节检查 `.env` 的位置和内容。

### 4.2 首次构建并启动

继续在同一个窗口执行：

```powershell
docker compose up -d --build --wait --wait-timeout 300
```

第一次会下载依赖和构建项目。看到下载或构建进度时继续等待，不要关闭窗口；不承诺固定启动耗时。

`--build` 表示先构建应用，`-d` 表示启动后在后台运行，`--wait` 表示等待服务就绪，`300` 是等待服务就绪的秒数。

**成功标志：** 命令正常结束，且第 5 节的服务状态和网页登录检查通过。默认配置下，以后也可以双击根目录的 `start-docker.cmd` 启动。

### 4.3 停止、再次启动和更新

这些命令仍在**项目根目录**执行：

| 想做什么 | 命令 |
| --- | --- |
| 停止服务，保留数据 | `docker compose stop`，也可以双击 `stop-docker.cmd` |
| 下次启动 | `docker compose up -d --wait --wait-timeout 300` |
| 更新代码后重新构建并启动 | `docker compose up -d --build --wait --wait-timeout 300` |
| 查看运行状态 | `docker compose ps -a` |

停止服务不会清空数据库。保留 `.env`、`.local/dev` 和 Docker 数据卷，不要把 `docker compose down -v` 当成排错或重启命令，它会删除数据卷。

### 4.4 需要调试源码或启动小程序时

| 目的 | 按哪份说明操作 |
| --- | --- |
| 用启动器在电脑上运行后端和网页 | [本机开发说明](deploy/local/README.md#2-用启动器运行整套应用)；需要本机 JDK、Maven、Node.js 和 Python |
| 用 IDEA 逐个调试后端 | [IDEA 启动说明](deploy/local/README.md#3-用-idea-调试后端)；Docker 仍负责数据库等依赖 |
| 在 Linux 服务器运行，再从自己的电脑访问 | [Linux 部署说明](deploy/README.md#1-linux-服务器启动) |
| 编译微信小程序 | [小程序启动与导入说明](civicflow-miniapp/README.md#启动与微信导入)；需要 Node.js 和微信开发者工具 |

Docker 启动命令构建的是网页和后端，**不会自动编译或导入微信小程序**。小程序需要网关地址及 AppID 配置；微信登录、手机号授权和订阅消息尚未接入。已有的账号密码登录、预约、签到和站内通知使用现有后端接口。

## 5. 验证启动成功

### 5.1 看服务状态

在**项目根目录**执行：

```powershell
docker compose ps -a
```

完整启动后，应看到以下两类状态：

| 类型 | 服务名 | 成功标志 |
| --- | --- | --- |
| 12 个长期运行的服务 | `mysql`、`redis`、`rabbitmq`、`nacos`、`auth`、`resource`、`appointment`、`queue`、`notification`、`gateway`、`web`、`service-identity` | 正在运行，健康状态为 `healthy` |
| 4 个只执行一次的初始化任务 | `nacos-init`、`notification-db-init`、`bootstrap`、`admin-init` | `Exited (0)`，表示正常完成，不需要一直运行 |

`starting` 表示还在准备；`unhealthy`、反复重启或非零退出码表示需要排查，按第 6 节查看日志。

### 5.2 打开网页并登录

在运行项目的电脑上打开 **[http://localhost:5173](http://localhost:5173)**。如果改了 `WEB_PORT`，使用实际端口。

启动成功后，项目会生成初始管理员登录文件。在**项目根目录**执行：

```powershell
Get-Content .local/dev/login.txt
```

把 `Username:` 后面的内容填到“用户名或手机号”，把 `Password:` 后面的内容填到“密码”。上面生成的是随机密码，**不要照填示例中的 `1234`**。已有账号后来改过密码时，使用新密码。

**成功标志：** 能登录，并看到管理员的网点、事项、窗口等菜单。首次启动没有业务数据，列表为空是正常的。

### 5.3 可选：体验一次预约和办理

系统不会自动生成演示网点和预约。先用管理员完成下面的配置，新增资源保持“启用”（`ENABLED`）：

1. 在“用户与角色”新建普通用户 `demo_user` 和窗口人员 `demo_staff`，分别选择对应角色，并记下自己设置的密码。密码长度为 8–72 位。
2. 新建一个网点、一个办理事项和一个窗口。编码可分别填 `DEMO_OUTLET`、`DEMO_ITEM`、`DEMO_WINDOW`；名称可填“演示服务中心”“业务咨询”“1 号窗口”。窗口所属网点从下拉框选择。
3. 在窗口列表“绑定事项”，选择刚创建的事项并保存；再用“人员授权”给 `demo_staff` 授权。
4. 在“号源日历”添加时段。“号源”就是某个办理时段可预约的名额数。新增状态选 `SCHEDULED`（已计划），放号时间安排在当前时间之后，并早于办理时段。

下面只是时间示例，**假设当前北京时间为 10:00**。实际操作请按当前时间顺延：

| 配置 | 示例 |
| --- | --- |
| 服务日期 | 今天 |
| 网点、事项 | 演示服务中心、业务咨询 |
| 办理时段、总名额 | `10:30–11:00`，`10` 个名额 |
| 放号时间 | 今天 `10:05` |
| 可签到范围 | 今天 `09:50–11:00` |
| 初始状态 | `SCHEDULED` |

**等到放号时间，再将该时段切换为 `OPEN`（开放预约）。** 如果提示名额还在准备，等待后台任务处理后刷新；默认名额准备任务每 60 秒检查一次。签到开始时间不能晚于办理开始时间，签到结束时间须在办理开始与结束之间。

然后逐次退出登录并切换账号：

1. 普通用户：登录 `demo_user` → 选择网点、事项和时段 → 提交预约 → 等待预约结果 → 在默认 5 分钟确认期限内确认 → 在可签到时间内获取二维码并“现场签到”。没有扫码枪也可以自助签到。
2. 窗口人员：登录 `demo_staff` → 选择授权窗口 → “开始工作” → “叫下一号” → “开始办理” → “完成办理”。
3. 管理员：重新登录，在“预约查询”和“预约操作日志”核对办理结果。

预约提交后显示“已受理”时，还需要查询最终建单结果；通知可能稍后送达，以预约详情和排队状态为准。

## 6. 常见问题

### 6.1 先查看状态，再看对应日志

以下命令在**项目根目录**执行，不会删除数据：

```powershell
docker compose ps -a
docker compose logs --tail 100
```

也可以只查一个服务，例如登录文件没有生成时：

```powershell
docker compose logs --tail 100 auth bootstrap admin-init
```

| 问题 | 处理方法 |
| --- | --- |
| 找不到 `docker`，或没有 `Server` 信息 | 打开 Docker Desktop；安装后重新打开 PowerShell；确认 Linux 容器引擎已启动 |
| 不支持 `include` | 更新 Docker Desktop / Compose，Compose 需为 2.20.3 或更新版本 |
| `no configuration file provided` | 当前目录不对，进入包含根目录 `compose.yaml` 的项目文件夹 |
| 找不到 `.env` 或提示变量缺失 | 按第 3 节准备 `deploy/compose/.env`；旧项目缺少通知库变量时看升级说明 |
| 端口被占用 | 确认是否已启动另一种模式；停止自己启动的旧实例，或按第 3.2 节修改对应电脑端口 |
| 同名容器已存在 | 不要直接删容器或数据卷；可能之前用本机启动器运行过，按下方方式核对 Compose 项目名 |
| 构建下载失败 | 检查网络和 Docker 代理设置，再运行构建启动命令；Java 依赖下载的代理参数见第 6.3 节 |
| 网页打不开 | 检查 `web` 和 `gateway` 的状态、日志及实际 `WEB_PORT`；默认地址是 `http://localhost:5173` |
| `.local/dev/login.txt` 没有生成 | 检查 `auth` 是否健康、`admin-init` 是否成功；已有账号时不会重新创建或重置，使用原账号密码 |
| 用户名或密码错误 | 只复制登录文件冒号后面的内容；改过账号密码时用新值。修改 `.env` 的初始密码不会重置已有账号 |
| 已有数据，但提示缺少密钥或数据库认证失败 | 保留数据卷，恢复与该数据库配套的 `.env`、`.local/dev`；不要重新生成密钥或随机替换数据库密码 |
| 能登录，但没有可预约时段 | 核对网点和事项是否启用、窗口是否绑定事项、日期是否可办理、时段是否已放号并为 `OPEN` |
| 员工看到“暂无授权窗口” | 管理员在窗口列表给该员工做“人员授权”；同时确认账号具有窗口人员角色 |
| 手机或另一台电脑打不开网页 | 默认只允许本机访问；服务器访问看部署说明，手机联调看小程序 README |

### 6.2 启动慢、等待超时或切换模式

如果构建已完成，只是等待服务就绪超时，可以延长等待。在**项目根目录**执行：

```powershell
docker compose up -d --wait --wait-timeout 600
```

如果服务已经退出或持续报错，延长等待不能解决问题，应先查看对应日志。

不要同时运行 Docker 全量模式和本机/IDEA 模式。切换前先按原模式的停止步骤停止服务，保留数据和密钥。

如果之前用本机启动器运行过，查看基础容器所属的 Compose 项目名：

```powershell
docker inspect civicflow-mysql --format '{{ index .Config.Labels "com.docker.compose.project" }}'
```

例如输出 `civicflow-local`，切换回 Docker 全量模式时，在当前 PowerShell 设置：

```powershell
$env:COMPOSE_PROJECT_NAME = 'civicflow-local'
docker compose up -d --build --wait --wait-timeout 300
```

项目名要以实际查询结果为准，避免重复创建同名容器；换终端后也要设置同一项目名。更多步骤见 [本机开发说明](deploy/local/README.md)。

### 6.3 Java 依赖下载失败时，按需指定构建代理

如果已开启电脑上的代理，但构建仍提示 Maven 下载超时，可以在**项目根目录**执行下面两条命令。`7897` 只是 HTTP 代理端口示例，必须换成你的代理软件实际端口：

```powershell
docker compose build --build-arg MAVEN_PROXY_HOST=host.docker.internal --build-arg MAVEN_PROXY_PORT=7897
docker compose up -d --wait --wait-timeout 300
```

`host.docker.internal` 表示运行 Docker 的电脑。以上参数只处理 Java 依赖构建下载；Docker 镜像拉取失败需检查 Docker 自身的网络与代理。没有使用代理时，按第 4 节正常启动即可。

业务配置、通知接口和数据库升级细节见 [部署说明](deploy/README.md)，运行监控见 [监控说明](deploy/monitoring/README.md)。
