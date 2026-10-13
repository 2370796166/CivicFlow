# CivicFlow（智序）

一个公共服务预约与排队项目：普通用户预约和签到，员工叫号和办理，管理员配置业务并查看记录。

微信小程序市民端位于 [`civicflow-miniapp`](civicflow-miniapp/README.md)，支持预约、确认、签到、排队和独立通知中心。微信导入目录、配置及验证说明见该目录的 README。

本轮增强保留已有预约、用户签到、窗口人员授权和管理员查询，新增库存余量展示、站内状态消息与办理提醒、未签到自动爽约、对账历史、跨服务脱敏审计、窗口效率统计及同步异常重试。

实际预约入口支持用户、用户加号源、号源三级限流（当前按网关实例计算）。预约先持久化规范事件，再由带租约的后台任务发布，MQ 确认等待不占用 HTTP 请求线程。指标与告警接入方法见 [监控说明](deploy/monitoring/README.md)。

升级时 Flyway 自动执行预约服务 V6 和排队服务 V4 新迁移，已有迁移未修改。提前预约默认 30 天（`CIVICFLOW_BOOKING_ADVANCE_DAYS`），取消提前量默认 0 分钟（`CIVICFLOW_CANCELLATION_NOTICE`），提醒提前 1 小时（`CIVICFLOW_REMINDER_LEAD`）；停办日期通过 `civicflow.appointment.rules.closed-dates` 配置。需要兼容同步发布时设置 `CIVICFLOW_APPOINTMENT_ASYNC_PUBLISH=false`。

受理成功表示预占请求已持久化，最终建单结果需通过预约结果页查询。GitHub Actions 执行后端迁移、真实容器集成测试和前端检查；生产多实例共享限额和容量仍需在目标环境验收。

**第一次使用，按下面 5 步操作，就能在自己的 Windows 电脑上运行项目。** Docker 会负责构建和运行网页、后端及数据库，不用另外安装 Java、Maven、Node.js 或 Python，也不用手动建数据库。

操作顺序：**安装 Docker → 下载并解压 → 生成配置 → 启动 → 打开网页登录**。只想先看看项目，完成第 1 节即可；想体验预约和叫号，再看第 2 节。

**想第一次就用 IDEA 启动、调试后端？** 下载解压后，直接看第 5 节的“从零开始，用 IDEA 启动”，不必先启动整套 Docker 应用。

## 1. 启动项目

### ① 安装 Docker

1. 按 [Docker Desktop 的 Windows 安装说明](https://docs.docker.com/desktop/setup/install/windows-install/) 下载并安装。如果提示选择 WSL 2，使用该选项；如果要求重启电脑，先重启。
2. 从开始菜单打开 **Docker Desktop**，等待它启动。项目使用 **Linux 容器**。
3. 在开始菜单搜索并打开 **Windows PowerShell**。这是输入命令的窗口，后面会一直用到。

复制下面两行命令，粘贴到 PowerShell，按回车：

```powershell
docker version
docker compose version
```

**检查结果：** 第一条应同时显示 `Client` 和 `Server` 的版本，第二条应显示 Compose 版本。若报错或只有 `Client`，先确认 Docker Desktop 已启动；安装后找不到命令时，关闭 PowerShell，再重新打开。

### ② 下载项目

打开 [GitHub 项目页面](https://github.com/2370796166/CivicFlow)，确认分支是 **main**，点击绿色的 **Code → Download ZIP**，下载后右键 ZIP 文件，选择“全部提取”。不要直接在压缩包里运行。

打开解压后的文件夹，找到能看到 `README.md`、`compose.yaml` 和 `start-docker.cmd` 的那一层。点击资源管理器顶部的地址栏，复制完整路径。

回到 PowerShell，用 `cd` 进入这个文件夹。例如：

```powershell
cd "D:\projects\CivicFlow-main"
```

把引号里的路径换成刚才复制的路径，保留引号。`cd` 的意思就是“进入这个文件夹”。再执行：

```powershell
Get-Item .\compose.yaml
```

**检查结果：** 能看到 `compose.yaml` 的文件信息，就说明位置正确。若提示文件不存在，回到解压目录，找到包含这个文件的那一层。

**后面的命令都在这个 PowerShell 窗口中执行。** 复制代码框中的命令即可，不要复制代码框外的说明文字。

### ③ 准备配置（只做一次）

首次启动需要一个配置文件，里面保存数据库连接信息和初始密码。**不用自己填写密码**：展开下方内容，复制整个代码框，粘贴到 PowerShell，按回车。

命令会自动创建 `deploy/compose/.env`。如果已经有这个文件，就保留它，继续下一步。

<details>
<summary>点击这里，展开需要复制的配置命令</summary>

这段命令会自动生成随机密码，已有配置时不会覆盖。

```powershell
if (Test-Path 'deploy/compose/.env') {
    Write-Host '已有配置，保留 deploy/compose/.env，请继续下一步。'
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

</details>

**检查结果：** 看到“配置已创建”或“已有配置，保留……”即可。这里生成的是随机密码，登录时到第 ⑤ 步查看，不需要记住。

### ④ 启动项目

确认 Docker Desktop 仍在运行，然后在同一个 PowerShell 窗口执行：

```powershell
docker compose up -d --build --wait --wait-timeout 300
```

第一次会下载依赖并构建项目，耗时取决于网络和电脑性能。看到 `Downloading`、`Building` 等进度时，继续等待，不要关闭窗口。

命令执行完成、再次出现可以输入命令的提示符后，检查状态：

```powershell
docker compose ps -a
```

| 看到的状态 | 是什么意思 |
| --- | --- |
| 长期运行的服务显示 `Up`，并带有 `healthy` | 服务已启动并通过健康检查 |
| `bootstrap`、`nacos-init`、`admin-init` 显示 `Exited (0)` | 初始化完成，正常现象 |
| `starting` | 还在启动，稍等后再查 |
| `unhealthy`、反复 `Restarting` 或退出码不是 `0` | 启动有问题，按第 4 节查看日志 |

**检查结果：** 11 个长期运行的服务健康，3 个初始化任务正常完成，就可以登录了。以后也可以双击项目文件夹里的 `start-docker.cmd` 启动。

### ⑤ 打开网页并登录

在运行项目的这台电脑上，用浏览器打开 **[http://localhost:5173](http://localhost:5173)**。请打开这个地址，不是数据库或后端的端口。

启动成功后，系统会自动在项目文件夹中生成 `.local/dev/login.txt`，保存初始管理员的登录信息。这个目录不用从 GitHub 下载，也不用自己创建。回到 PowerShell 查看：

```powershell
Get-Content .local/dev/login.txt
```

将 `Username:` 后面的内容填到网页的“用户名或手机号”，将 `Password:` 后面的内容填到“密码”，点击“登录”。**密码是第 ③ 步随机生成的，不要直接用 `1234`。** 如果后来改过账号密码，以修改后的为准。

也可以在项目文件夹中打开 `.local/dev/login.txt` 查看。若文件还没生成，先检查第 ④ 步的 `admin-init` 是否正常完成。

**检查结果：** 登录后看到管理员的“网点”“事项”“窗口”等菜单，项目就启动好了。此时列表为空是正常的，下面会教你添加演示数据。启动成功后可以关闭 PowerShell，Docker Desktop 继续保持运行。

## 2. 试用一次完整流程

系统不会自动添加网点和预约。先用管理员按下面顺序配置，再切换账号体验。

### 管理员：准备演示数据

1. **建两个账号**：进入“用户与角色”，新增普通用户（例如 `demo_user`，角色选“普通用户”）和员工（例如 `demo_staff`，角色选“窗口人员”）。每个新账号选择一个角色，密码设置为 8–72 位，记下自己填写的用户名和密码。
2. **建网点、事项和窗口**：分别进入对应菜单，点击“新增”。可以用下面的例子填写，其余必填项按页面提示填写。

   | 菜单 | 编码示例 | 名称示例 | 需要注意 |
   | --- | --- | --- | --- |
   | 网点 | `DEMO_OUTLET` | 演示服务中心 | 按页面提示填写地址等必填项 |
   | 事项 | `DEMO_ITEM` | 业务咨询 | 预计办理时间可用默认值 |
   | 窗口 | `DEMO_WINDOW` | 1 号窗口 | 所属网点从下拉框选择“演示服务中心” |

   编码可以按上面的例子填写。新增窗口和号源时，直接从下拉框选择网点或事项，可以搜索名称或编码，系统会自动关联对应的 ID，不用复制数字。资源状态保持 `ENABLED`（启用）。

3. **给窗口分配业务和员工**：在窗口列表点击“绑定事项”，勾选刚创建的事项并保存；点击“人员授权”，授权刚创建的员工。
4. **添加可预约时段**：进入“号源日历”，点击“单日新增”。“号源”就是某个时段允许预约的名额，按下面的例子填写。

假如现在是北京时间 **10:00**，可以这样配置。**这只是时间示例**，实际操作时根据当前时间顺延；放号时间安排在几分钟后，办理时段安排在更晚的时间，签到范围要包含你准备签到的时间：

| 设置 | 示例 |
| --- | --- |
| 服务日期、网点和事项 | 今天，以及下拉框中的演示服务中心、业务咨询 |
| 办理时段、总额度 | `10:30–11:00`，`10` 个名额 |
| 放号时间 | 今天 `10:05` |
| 签到时间 | 今天 `09:50–11:00` |
| 初始状态 | `SCHEDULED` |

保存后，**等到放号时间，再点击该行的 `OPEN` 并确认，才会开放预约**。`SCHEDULED` 表示已安排，`OPEN` 表示开放预约。如果提示库存正在准备，稍等约一分钟再试。

### 切换账号：预约、签到和办理

每次切换账号，先点击右上角的“退出登录”，再用刚创建的账号登录。

1. **普通用户**：登录 `demo_user` → 选择网点、事项、日期和时段 → 预约 → 五分钟内点击“确认预约” → 在签到时间内点击“获取二维码”和“现场签到”。没有扫码枪也能完成签到。
2. **员工**：登录 `demo_staff` → 选择授权窗口 → “开始工作” → “叫下一号” → “开始办理” → “完成办理”。
3. **管理员**：重新登录管理员，在“预约查询”和“预约操作日志”查看刚才的办理结果。

## 3. 平时怎么用

下次使用时，先打开 Docker Desktop。需要执行命令时，按第 ② 步打开 PowerShell 并进入**原来的项目文件夹**：

| 想做什么 | 怎么操作 |
| --- | --- |
| 停止项目 | 双击 `stop-docker.cmd`，或执行 `docker compose stop` |
| 下次启动 | `docker compose up -d --wait --wait-timeout 300` |
| 更新代码后启动 | `docker compose up -d --build --wait --wait-timeout 300` |
| 查看运行状态 | `docker compose ps -a` |
| 查看报错 | `docker compose logs --tail 100` |

正常停止会保留账号和数据，不用重新生成配置。保留项目中的 `deploy/compose/.env` 和 `.local/`；数据库数据保存在 Docker 数据卷中。**不要用 `docker compose down -v` 处理报错，也不要在 Docker Desktop 里删除项目的数据卷**，否则会丢失数据库数据。

默认网页只能在运行项目的电脑上访问。服务器上运行的办法见第 5 节。

## 4. 启动失败怎么办

| 遇到的问题 | 先这样处理 |
| --- | --- |
| 找不到 `docker`，或连接不上 Docker | 打开 Docker Desktop；安装后重新打开 PowerShell，再检查 `docker version` |
| 提示 `no configuration file provided` | 当前文件夹不对，按第 ② 步进入有 `compose.yaml` 的文件夹 |
| 提示找不到 `.env` 或缺少密码 | 回到第 ③ 步生成配置；文件应位于 `deploy/compose/.env`，不要放在项目根目录 |
| 提示端口被占用 | 停掉之前运行的项目或占用端口的程序；不要同时使用 Docker 和本机启动 |
| 下载超时或失败 | 检查网络及 Docker 的代理设置，重新执行第 ④ 步的构建启动命令；Maven 代理说明见下方 |
| 网页打不开、服务不健康 | 确认地址是 `http://localhost:5173`；用下面的状态和日志命令查看报错 |
| 提示用户名或密码错误 | 按第 ⑤ 步查看 `login.txt`，复制冒号后面的内容；改过密码则用新密码 |
| 能登录，但没有可预约时段 | 核对窗口绑定、资源状态、日期，以及号源是否已切换为 `OPEN` |
| 员工看到“暂无授权窗口” | 管理员进入“窗口”列表，通过“人员授权”给该员工授权 |

先查看状态和最近的日志，**不用删数据库或重新生成密码**：

```powershell
docker compose ps -a
docker compose logs --tail 100
```

如果只是启动慢、等待超时，并且构建已完成，可以执行下面的命令多等一会：

```powershell
docker compose up -d --wait --wait-timeout 600
```

若提示不支持 `include`，请更新 Docker Desktop。项目使用的 [Compose include 功能](https://docs.docker.com/compose/how-tos/multiple-compose-files/include/) 需要 Compose 2.20.3 或更新版本。已有账号不会因修改 `.env` 中的初始密码而重置。

<details>
<summary>已经开了网络代理，后端依赖还是下载失败</summary>

如果报错中出现 Maven、Java 依赖下载超时，而你已经在电脑上开启了代理，可以为构建单独指定代理。先打开代理软件，找到 **HTTP 代理端口**；下面的 `7897` 只是例子，要换成你实际使用的端口：

```powershell
docker compose build --build-arg MAVEN_PROXY_HOST=host.docker.internal --build-arg MAVEN_PROXY_PORT=7897
docker compose up -d --wait --wait-timeout 300
```

`host.docker.internal` 表示运行 Docker 的电脑。这两个参数只用于构建时下载 Java 依赖；不使用代理时，仍按前面的普通启动命令操作。

</details>

## 5. 其他使用方式（可选）

<details>
<summary>从零开始，用 IDEA 启动（包含 MySQL、Redis 等容器）</summary>

这种方式分三部分：**Docker 运行数据库等基础服务，IDEA 运行后端，PowerShell 运行前端**。第一次使用也可以直接按下面的步骤操作。

### ① 准备工具，打开项目

安装 Docker Desktop、**JDK 17**、Maven 3.9+、**Node.js 24.12+（推荐）或 22.20+**、Python 3.10+，再安装 IntelliJ IDEA。打开 Docker Desktop，等待 Linux 容器引擎启动。

按照第 1 节第 ② 步下载、解压项目，并在 PowerShell 中进入项目文件夹。执行下面的命令，确认每个工具都能显示版本；找不到哪个命令，就先安装对应工具并重新打开 PowerShell：

```powershell
javac -version
mvn.cmd -version
node -v
npm.cmd -v
python --version
docker version
```

在 IDEA 的欢迎页点击 **Open（打开）**，选择项目根目录的 `pom.xml`，按提示作为项目打开。将 **File → Project Structure → Project SDK** 设置为 JDK 17，等待右下角的 Maven 导入完成。

如果之前已经启动了整套 Docker 应用，先在项目目录执行 `docker compose stop`，腾出后端和前端端口。**第一次启动的用户跳过这句。**

### ② 启动 MySQL、Redis 等基础容器

在项目根目录的 PowerShell 执行下面这一行：

```powershell
powershell -NoProfile -File deploy/local/dev.ps1 prepare
```

**这一步会自动启动下面四个容器**，还会生成 `.env`、开发密钥、IDEA 配置和本地启动器。没有 `.env` 也能执行，不需要先走 Docker 全量启动步骤，不用手动建数据库或复制数据库密码。

| 容器名 | 用途 | 默认本机端口 |
| --- | --- | --- |
| `civicflow-mysql` | 保存用户、网点、预约等数据 | `3306` |
| `civicflow-redis` | 缓存、库存和幂等控制 | `6379` |
| `civicflow-rabbitmq` | 处理异步消息 | `5672`，管理页面 `15672` |
| `civicflow-nacos` | 服务注册和配置 | `8848`，通信端口 `9848` |

第一次需要下载镜像和 Python 依赖，请等待。看到 **`Ready for IDEA Local run configurations.`** 后，执行：

```powershell
docker ps --filter "name=civicflow-"
```

**检查结果：** 上面四个容器都在运行，并显示 `healthy`。此时还没有启动网页和业务后端，接着做下一步。

以后每次用 IDEA 启动项目，都先执行一次 `prepare`；也可以在 IDEA 顶部的运行配置中选择 **`CivicFlow - Prepare IDEA`**，点击运行。准备程序会在后台保持运行，为后端刷新内部通信凭据，调试期间不要执行 `stop-local.cmd`。

如果只是想单独重启这四个基础容器，**准备成功后**可以执行下面两行。第一行取得它们实际所属的 Compose 项目名，避免创建重复容器；这两行不能替代首次的 `prepare`：

```powershell
$ideaComposeProject = docker inspect civicflow-mysql --format '{{ index .Config.Labels "com.docker.compose.project" }}'
docker compose -p $ideaComposeProject up -d --wait --wait-timeout 300 mysql redis rabbitmq nacos
```

### ③ 在 IDEA 启动后端

打开 IDEA 顶部的运行配置下拉框，选择下表中的配置，逐个点击绿色运行按钮（调试时点击小虫子）。**一个服务启动成功后，再启动下一个。** 不要直接使用没有本地配置的默认 `Application` 启动项。

| 顺序 | IDEA 配置 | 用途 |
| --- | --- | --- |
| 1 | `Local Auth` | 登录和用户管理 |
| 2 | `Local Resource` | 网点、事项、窗口和号源 |
| 3 | `Local Appointment` | 预约 |
| 4 | `Local Queue` | 排队叫号 |
| 5 | `Local Notification` | 站内通知 |
| 6 | `Local Gateway` | 统一访问后端 |

**检查结果：** 每个服务的 Run 窗口出现 `Started ...Application`，且没有随后退出或报错。`Local Auth` 启动后，后台准备程序会自动创建初始管理员；已有账号和密码会保留。

如果 IDEA 没有这些配置或提示不支持 Spring Boot 配置，可以按 [IDEA 的 Application 配置说明](https://www.jetbrains.com/help/idea/run-debug-configuration-java-application.html) 在 **Run → Edit Configurations → + → Application** 中手动新建。JDK 选 17，模块选对应的 `civicflow-...`，工作目录选项目根目录，主类和配置文件对应如下：

| 服务 | Main class（主类） | 配置文件 |
| --- | --- | --- |
| Auth | `com.civicflow.auth.AuthApplication` | `auth.properties` |
| Resource | `com.civicflow.resource.ResourceApplication` | `resource.properties` |
| Appointment | `com.civicflow.appointment.AppointmentApplication` | `appointment.properties` |
| Queue | `com.civicflow.queue.QueueApplication` | `queue.properties` |
| Gateway | `com.civicflow.gateway.GatewayApplication` | `gateway.properties` |

点击 **Modify options → Add VM options**，为每个服务填写下面这一行。将示例路径换成自己的项目路径，并将 `auth.properties` 换成该服务对应的文件名；这是 **VM options**，不是 Program arguments：

```text
-Dspring.config.additional-location="file:D:/projects/CivicFlow-main/.local/dev/auth.properties"
```

### ④ 安装前端依赖，启动网页

另开一个 PowerShell 窗口，`cd` 进入同一个项目文件夹，依次执行：

```powershell
npm.cmd --prefix civicflow-web ci
npm.cmd --prefix civicflow-web run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

第一行安装前端依赖，第一次使用必须执行；依赖没有变化时，下次可以只执行第二行。看到 `http://127.0.0.1:5173/` 后，保持这个窗口开启。

### ⑤ 打开网页，查看账号

浏览器打开 **[http://localhost:5173](http://localhost:5173)**。另开 PowerShell，在项目目录执行：

```powershell
Get-Content .local/dev/login.txt
```

用文件里的用户名和密码登录，后续操作见第 2 节。`.local` 是启动时自动生成的，不需要提交到 GitHub。文件暂未出现时，确认 `Local Auth` 已启动成功，并稍等几秒；准备日志在 `.local/dev/launcher.log`。如果以前修改过账号密码，使用修改后的密码。

### ⑥ 停止项目，下次再启动

1. 在 IDEA 中停止自己启动的所有后端服务。
2. 在运行前端的 PowerShell 中按 `Ctrl+C`。
3. 在项目目录执行下面一行，停止后台准备程序；数据库容器和数据会保留：

```powershell
powershell -NoProfile -File deploy/local/dev.ps1 stop
```

下次打开 Docker Desktop，再按 **② → ③ → ④ → ⑤** 操作即可。想同时停止基础容器，再执行：

```powershell
docker stop civicflow-mysql civicflow-redis civicflow-rabbitmq civicflow-nacos
```

如果要切换回整套 Docker 启动，先按上面步骤停止本机应用和四个基础容器，再执行第 1 节的 Docker 启动命令。不要同时运行两种模式。若提示同名容器已存在，查看原 Compose 项目名：

```powershell
docker inspect civicflow-mysql --format '{{ index .Config.Labels "com.docker.compose.project" }}'
```

例如输出 `civicflow-local`，就在当前 PowerShell 执行 `$env:COMPOSE_PROJECT_NAME = 'civicflow-local'`，再运行 Docker 启动命令。以后新开终端也要先设置这个项目名。

</details>

<details>
<summary>我要在 Linux 服务器上运行</summary>

下面用于在 Linux 服务器上运行并通过自己的电脑访问。服务器需先装好 **Docker Engine、Compose 2.20.3+、Git 和 OpenSSL**；Docker 安装方法见 [官方文档](https://docs.docker.com/engine/install/)。这些命令在服务器的 Bash 终端执行，不要复制到 Windows PowerShell。

**1. 下载项目：**

```bash
git clone --branch main --single-branch https://github.com/2370796166/CivicFlow.git
cd CivicFlow
```

**2. 生成配置：** 复制整段执行，会自动生成随机密码。已有配置时不会覆盖。

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

**3. 启动并查看账号：**

```bash
docker compose up -d --build --wait --wait-timeout 300
docker compose ps -a
cat .local/dev/login.txt
```

**4. 从自己的电脑打开网页：** 默认只允许服务器本机访问，直接打开 `服务器IP:5173` 无法访问。在**自己的电脑**另开 PowerShell 或终端，执行下面的命令，将 `USER` 和 `SERVER_IP` 换成你登录服务器使用的用户名与地址：

```bash
ssh -N -L 5173:127.0.0.1:5173 USER@SERVER_IP
```

例如用 `ubuntu` 登录地址为 `203.0.113.10` 的服务器，就把末尾换成 `ubuntu@203.0.113.10`。按提示完成 SSH 登录；连接后窗口没有新输出是正常的，保持它开启。

在自己电脑的浏览器打开 [http://localhost:5173](http://localhost:5173)，使用第 3 步查到的账号登录。本机 5173 端口需要空闲。这种方式适合个人演示；本教程没有配置对公众开放的域名和 HTTPS。

</details>

<details>
<summary>我要运行测试</summary>

开发工具和 Docker 准备好后，在项目文件夹执行：

```powershell
mvn.cmd verify
npm.cmd --prefix civicflow-web ci
npm.cmd --prefix civicflow-web run test
npm.cmd --prefix civicflow-web run build
```

后端部分测试会启动真实数据库和消息服务的容器。只想体验项目的话，不需要运行测试。

</details>

## main 整合版本：通知与管理查询

本版本在预约规则、实时库存投影、异步建单和排队恢复的基础上，整合独立的 `civicflow-notification` 服务（本机端口 `8085`）。通知拥有自己的 `civicflow_notification` 数据库，通过预约和排队的事务 outbox 接收事件，提供站内通知、可选通知偏好、失败记录和管理员审计重放。短信、邮件和微信订阅消息没有接入外部供应商。

用户的「站内通知」使用 `/api/v1/user/notifications`，包含预约创建、确认/取消、确认截止前提醒、叫号、即将到号和过号等通知。既有 `/api/v1/user/messages` 预约消息接口与「预约消息记录」页面保留，兼容原客户端；两者分页、已读和来源不同，不应在客户端混成同一份列表或合并未读数量。

管理员预约查询增加关键词、事项、号源和日期范围筛选、用户姓名及办理时间线；既有 `serviceDate` 查询参数和 `/api/v1/admin/appointment-operation-logs` 保持兼容。跨服务日志提供 `/api/v1/admin/operation-logs/{auth|resource|appointment|queue|stock}`；整合后的对账查询使用 `/api/v1/admin/reconciliations`，旧的分页对账明细接口保留在 `/api/v1/admin/operations/reconciliations`。

从五服务版本升级时，保留现有 `.env`、`.local/dev` 密钥和数据库卷，在 `.env` 中补齐 `.env.example` 列出的四个 `CIVICFLOW_NOTIFICATION_DB_*` 账号/密码变量。Compose 的 `notification-db-init` 为已有 MySQL 卷创建独立通知库和账号；本机 `prepare` 也包含该步骤。随后按既有启动方式重建应用。不要删除原有数据卷或重新生成密钥。

已发布迁移保持原样：预约服务沿用 V6 站内消息迁移并追加 V7 管理查询迁移；排队服务沿用 V4 同步租约迁移并追加 V5 审计索引；auth/resource 分别追加 V4/V5 审计索引；通知服务从 V1 初始化。若运行环境已经使用另一套未发布迁移历史，须先核对其 Flyway 历史和数据库结构，不能直接套用本版本或篡改已执行迁移。

开发工具位于 `deploy/local`；新增本地合成数据及诊断脚本位于 `deploy/tests`，需要运行环境中的受控凭据，不在源码中存放真实凭据。这些工具不会在正常应用启动时自动执行。隔离测试使用 Testcontainers；不能把单元测试或本机验证当作生产发布验收。
