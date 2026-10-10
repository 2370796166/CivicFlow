# CivicFlow（智序）

CivicFlow 是一个公共服务预约与排队项目。普通用户可以预约、确认和签到；窗口人员可以叫号、办理；管理员可以配置网点、事项、窗口和号源，查询预约及操作记录。

**第一次使用，建议先按下面的 Docker 方式启动。** 它会自动构建和启动前端、五个后端服务，以及数据库等配套服务。你不需要先安装 Java、Maven、Node.js 或 Python，也不用手动建数据库。

## 先选一种启动方式

| 你的目的 | 使用方式 | 需要安装什么 |
| --- | --- | --- |
| 先把项目跑起来，看看页面和功能 | **Docker 启动，推荐新手使用** | Docker Desktop；Git 可选 |
| 修改 Java 代码，用 IDEA 打断点 | Windows 本机调试 | Docker Desktop、JDK 17、Maven、Node.js、Python |

一次只使用一种方式。两种方式会使用相同的网页和接口端口，同时启动会发生端口冲突。

- [一、在 Windows 上用 Docker 启动](#一在-windows-上用-docker-启动)
- [二、登录后跑通一次预约](#二登录后跑通一次预约)
- [三、停止、再次启动和更新项目](#三停止再次启动和更新项目)
- [四、启动失败时怎么排查](#四启动失败时怎么排查)
- [五、Windows 本机启动与 IDEA 调试](#五windows-本机启动与-idea-调试)
- [六、在 Linux 服务器上运行演示环境](#六在-linux-服务器上运行演示环境)
- [七、开发与测试](#七开发与测试)

## 一、在 Windows 上用 Docker 启动

### 第 1 步：安装并打开 Docker Desktop

1. 从 [Docker 官方 Windows 安装页面](https://docs.docker.com/desktop/setup/install/windows-install/) 下载安装程序。
2. 按安装提示操作，使用 **WSL 2 / Linux containers**。如果提示安装或更新 WSL，按官方页面的步骤处理；需要重启时先重启电脑。
3. 安装完成后，从开始菜单打开 **Docker Desktop**，等待引擎启动。

Docker Desktop 已包含 Docker Compose，它用来一次启动项目所需的多个服务。项目使用 `include` 配置，需要 **Compose 2.20.3 或更新版本**，参见 [Docker 官方说明](https://docs.docker.com/compose/how-tos/multiple-compose-files/include/)。

从开始菜单打开 **Windows PowerShell**，逐行执行：

```powershell
docker version
docker compose version
docker info --format '{{.OSType}}'
```

检查结果：

- `docker version` 同时有 `Client` 和 `Server` 信息，说明 Docker 引擎能连接。
- `docker compose version` 显示版本号，并满足上面的要求。
- 最后一条输出 `linux`。如果输出 `windows`，切换 Docker Desktop 到 Linux containers。

后面的 Windows 命令也在 PowerShell 中执行。**复制代码框里的命令即可，不要复制终端前面的 `PS C:\...>`。**

### 第 2 步：下载项目，进入项目根目录

可以在 [GitHub 项目页面](https://github.com/2370796166/CivicFlow) 点击 **Code → Download ZIP**，然后解压到一个容易找到的目录，例如 `D:\projects\CivicFlow`。

也可以安装 [Git for Windows](https://git-scm.com/install/windows)，在用于存放项目的目录中执行：

```powershell
git clone https://github.com/2370796166/CivicFlow.git
cd CivicFlow
```

**项目根目录**就是同时能看到 `README.md`、`compose.yaml`、`start-docker.cmd` 和 `deploy` 文件夹的那一层。ZIP 解压后的文件夹可能叫 `CivicFlow-main`，也可能还有一层同名文件夹，认准这些文件即可。

如果使用 ZIP 下载，在 PowerShell 中用 `cd` 进入你实际解压的目录。例如：

```powershell
cd D:\projects\CivicFlow
```

路径里有空格时，用双引号包起来。后面的命令默认都在项目根目录执行。

### 第 3 步：生成首次启动配置

配置保存在 `deploy/compose/.env`。它记录数据库等服务的连接信息和首次创建的管理员账号。

**第一次启动时，在项目根目录完整复制下面这段 PowerShell 命令执行。** 它会从模板创建配置，并自动生成随机密码。已有 `.env` 时只提示保留，不会覆盖。

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

不用记住随机密码，启动成功后会生成登录说明文件。如果希望自定义首次管理员密码，可以在**第一次启动之前**打开 `.env`，修改 `CIVICFLOW_LOCAL_ADMIN_PASSWORD=` 后面的值，建议使用至少 12 位字母和数字。管理员用户名默认为 `admin`。

已有环境请继续使用原来的 `.env`。修改这个文件里的管理员密码**不会重置数据库中已经存在的账号**。

### 第 4 步：检查配置，然后启动

先检查配置：

```powershell
docker compose config --quiet
```

没有报错、直接回到命令提示符，就可以启动：

```powershell
docker compose up -d --build --wait --wait-timeout 300
```

也可以双击项目根目录的 **`start-docker.cmd`**，作用相同。建议第一次使用命令启动，这样更容易看清报错。

这一步会自动下载镜像和依赖、构建前后端、创建数据库表、生成应用密钥，并创建首次管理员账号。第一次通常比以后慢，耗时取决于网络和电脑性能；终端持续输出下载、构建进度时，请等待。

命令里的 `-d` 表示后台运行，`--build` 表示构建项目，`--wait` 表示等待服务就绪。最后的 `300` 是等待服务就绪的秒数，不是整个首次下载和构建的总时限。

### 第 5 步：确认启动成功，打开网页

执行：

```powershell
docker compose ps -a
```

正常情况下：

- `web`、`gateway`、`auth`、`resource`、`appointment`、`queue`，以及 `mysql`、`redis`、`rabbitmq`、`nacos`、`service-identity` 都在运行，健康状态为 `healthy`。
- `nacos-init`、`bootstrap`、`admin-init` 是只执行一次的初始化任务。它们显示 **`Exited (0)` 是正常完成**，不需要一直运行。

用浏览器打开 **[http://localhost:5173](http://localhost:5173)**，应能看到登录页面。不要在浏览器地址栏输入 `0.0.0.0` 或 Docker 容器名称。

查看管理员账号和密码：

```powershell
Get-Content .local/dev/login.txt
```

文件中的 `Username` 是用户名，`Password` 是密码。也可以在资源管理器中直接打开这个文件。**以上配置命令已生成随机密码，请以文件内容为准，不要直接尝试 `admin / 1234`。**

这里的 `localhost` 指当前电脑。默认部署只允许本机访问，手机或另一台电脑不能直接访问你的 `localhost:5173`。

## 二、登录后跑通一次预约

新环境会创建管理员账号，但不会自动填充演示网点、事项或号源。登录后列表为空是正常的。按以下顺序准备一次演示，不需要手工修改数据库。

### 1. 管理员准备账号和资源

用管理员登录，依次操作左侧菜单：

| 菜单 | 要做的事 | 示例 |
| --- | --- | --- |
| 用户与角色 | 点击“新增用户”，创建普通用户，角色只选 `USER` | 用户名 `demo_user`，密码自行填写，8–72 位 |
| 用户与角色 | 再创建窗口人员，角色只选 `STAFF` | 用户名 `demo_staff`，保存时确认授权 |
| 网点 | 新增网点，保存后复制列表中的 **ID** | 编码 `DEMO_OUTLET`，名称“演示服务大厅”，填写地址 |
| 事项 | 新增事项，保存后复制列表中的 **ID** | 编码 `DEMO_ITEM`，名称“资料办理”，预计办理 30 分钟 |
| 窗口 | 新增窗口，填写刚才复制的网点 ID | 编码 `DEMO_WINDOW`，名称“一号窗口” |
| 窗口 | 点击“绑定事项”，选中刚创建的事项并保存 | 让窗口能办理这个事项 |
| 窗口 | 点击“人员授权”，勾选 `demo_staff` 并保存 | 让员工能使用这个窗口 |

**ID 与编码不同。** ID 是系统生成的一长串数字，后续填“网点 ID”“事项 ID”时复制这串数字，不要填 `DEMO_OUTLET` 或名称。网点、事项和窗口应保持 `ENABLED`（启用）状态。

### 2. 管理员配置当天号源

进入“号源日历”，点击“单日新增”。所有业务时间按**北京时间（UTC+8）**填写。

假设你现在是当天 **10:00**，可以这样填写；实际操作时按当前时间顺延，尽量选当天、不跨午夜：

| 字段 | 示例 | 含义 |
| --- | --- | --- |
| 网点 ID / 事项 ID | 刚才复制的两串 ID | 这批名额属于哪个网点和事项 |
| 服务日期 | 今天 | 用户在哪一天办理 |
| 开始时间 / 结束时间 | `10:30` / `11:00` | 办理时段 |
| 总额度 | `10` | 这个时段总共提供多少个名额 |
| 放号时间 | 今天 `10:05` | 必须晚于创建时刻、早于时段开始；提前留几分钟让系统准备库存 |
| 签到开始 / 签到结束 | `09:50` / `11:00` | 包含演示时刻，才能现场签到 |
| 状态 | `SCHEDULED` | 已安排放号；`DRAFT` 是草稿，用户看不到 |

保存后，**等到放号时间，再在该行操作中点击 `OPEN` 并确认**。当前项目需要这一步，不能只等待时间到就认为已经开放。打开后如果提示库存尚未准备好，稍等约一分钟再重试，让后台同步最新状态。

时间必须满足：放号时间早于时段开始；签到开始不晚于时段开始；签到结束在时段开始与结束之间，并晚于签到开始。不要直接照抄已经过去的示例时间。

### 3. 普通用户预约和签到

1. 点击右上角“退出登录”，用刚创建的 `demo_user` 登录。
2. 在“服务网点”选择演示大厅，再选择事项和今天的日期。
3. 选择已开放的时段，点击预约；进入预约详情后，**五分钟内点击确认预约**。
4. 确认成功后，先在详情页点击“获取二维码”，再点击出现的“现场签到”按钮。也可以出示二维码，让员工通过扫码设备提交二维码内容。
5. 在“我的预约”查看排队和办理状态。

没有扫码枪也能演示，使用用户详情里的“现场签到”即可。一个人切换账号时，先完成用户签到，再退出并登录员工账号。

### 4. 窗口人员完成办理

用 `demo_staff` 登录“窗口工作台”，选择授权网点和窗口，按页面顺序执行：**开始工作 → 叫下一号 → 开始办理 → 完成办理**。需要当前已有签到的排队票才能叫号。

最后切回管理员，在“预约查询”和“预约操作日志”中查看本次预约。到这里，预约、确认、签到、排队和办理的主要流程就跑通了。

## 三、停止、再次启动和更新项目

以下命令用于 **Docker 启动方式**，在项目根目录执行。

| 操作 | 命令 | 说明 |
| --- | --- | --- |
| 停止全部服务 | `docker compose stop` | 也可双击 `stop-docker.cmd`；保留账号和业务数据 |
| 下次再次启动 | `docker compose up -d --wait --wait-timeout 300` | Docker Desktop 需先启动；无需重新生成 `.env` |
| 更新代码后构建并启动 | `docker compose up -d --build --wait --wait-timeout 300` | 克隆下载的项目可先执行 `git pull`；ZIP 下载需自行更新代码 |
| 查看运行状态 | `docker compose ps -a` | 查看是否运行、是否健康 |
| 查看最近日志 | `docker compose logs --tail 100 gateway appointment` | 不会修改项目或数据 |
| 持续查看日志 | `docker compose logs -f gateway appointment` | 按 `Ctrl+C` 退出日志查看，服务继续后台运行 |

项目数据不在 GitHub 里，而在本机 Docker 数据卷中。`deploy/compose/.env` 保存连接配置，`.local/dev/keys.json` 保存应用密钥，`.local/dev/login.txt` 保存首次登录信息。**备份或迁移环境时，数据库、配置和密钥需要一起保留。**

遇到报错时，不要删除 `.local`、重新覆盖 `.env` 或执行 `docker compose down -v`。`-v` 会删除数据卷；重新生成密钥也不能代替原来的密钥。它们都不是普通重启步骤。

## 四、启动失败时怎么排查

先确认你在项目根目录、Docker Desktop 已打开，然后按报错对应的情况处理。

| 现象或报错 | 通俗解释 | 处理方法 |
| --- | --- | --- |
| 找不到 `docker`，或提示“不是内部或外部命令” | Docker 没装好，或终端还没识别新安装的软件 | 完成安装后，关闭并重新打开 PowerShell，再运行 `docker version` |
| `Cannot connect`、`docker_engine` 或只有 `Client` 信息 | Docker 引擎没有启动 | 打开 Docker Desktop，等待引擎就绪，再重试 |
| `include` 不支持，或提示配置属性不认识 | Compose 版本过旧 | 更新 Docker Desktop，确认 Compose ≥ 2.20.3 |
| `Set MYSQL_ROOT_PASSWORD` 或找不到 `.env` | 配置没有生成，或目录放错了 | 检查 `deploy/compose/.env`，回到第一章第 3 步；不是根目录的 `.env` |
| `port is already allocated` / 端口被占用 | 已有程序使用项目需要的端口 | 停止以前运行的 CivicFlow 或占用该端口的程序，不要同时开两种启动方式 |
| 镜像拉取失败、下载超时、`npm` / Maven 下载失败 | 依赖下载没有成功 | 检查网络、Docker 的代理或镜像源配置；网络恢复后重新执行启动命令 |
| `unhealthy` 或等待超过 300 秒 | 某个服务没就绪，或启动较慢 | 先看下面的状态和日志；如果只是启动慢，可用 600 秒再等待 |
| 初始化任务 `Exited (0)` | 初始化已经成功完成 | 正常现象；只有非零退出码或错误日志才需要处理 |
| 网页打不开 | 前端未运行，或访问端口改过 | 检查 `web` 是否 `healthy`，默认访问 `http://localhost:5173` |
| 登录失败 | 用错账号/密码，或当前数据库已有旧账号 | 查看 `.local/dev/login.txt`；如果后来改过密码，使用修改后的密码，重新改 `.env` 不会重置账号 |
| `keys.json is missing` | 原数据库已有数据，但对应密钥丢失 | 恢复该环境原来的 `.local/dev/keys.json`，不要创建新密钥覆盖 |
| 能登录，但没有可预约事项或时段 | 业务配置尚未完成 | 核对窗口绑定、资源启用状态、日期、放号时间与 `OPEN` 状态，按第二章准备数据 |

**判断是哪一个服务失败：**

```powershell
docker compose ps -a
docker compose logs --tail 100 nacos-init bootstrap admin-init
docker compose logs --tail 100 auth resource appointment queue gateway web
```

例如 `appointment` 不健康，就重点查看它的日志；如果它依赖的 `resource` 也没启动，则先排查 `resource`。基础设施异常时，查看：

```powershell
docker compose logs --tail 100 mysql redis rabbitmq nacos
```

如果没有实际报错、只是电脑启动较慢，可以重新等待：

```powershell
docker compose up -d --wait --wait-timeout 600
```

不要把 `.env`、密钥文件或登录密码贴到公开问题中。反馈故障时，提供失败服务名称和相关错误日志即可。

### Docker 模式的端口冲突怎么处理

如果你不想停掉本机已经运行的数据库等服务，可以在记事本中打开 `deploy/compose/.env`，修改冲突的**宿主机端口**。宿主机端口就是电脑对外提供的端口，容器内部端口会保持原样。

例如本机已安装 MySQL，占用了 3306，可以把 `MYSQL_PORT=3306` 改为 `MYSQL_PORT=13306`，保存后再次执行带 `--build` 的启动命令。容器中的应用仍连接内部数据库端口，网页仍访问 `http://localhost:5173`。

同类配置还有 `REDIS_PORT`、`RABBITMQ_PORT`、`RABBITMQ_MANAGEMENT_PORT`、`NACOS_PORT`、`NACOS_GRPC_PORT`。若修改 Nacos，两个端口必须一起改，满足 **`NACOS_GRPC_PORT = NACOS_PORT + 1000`**。新手建议保留网页端口 `5173`，先停止占用它的程序；当前生成的接口访问来源配置也使用这个端口，单独修改 `WEB_PORT` 不等于完成全部配置。

这套改端口方法用于 Docker 模式。本机调试的网页和五个服务仍固定使用 `5173`、`8080`–`8084`，应先释放这些端口。

## 五、Windows 本机启动与 IDEA 调试

需要改代码、调试后端时再使用这一章。数据库等基础设施仍由 Docker 提供；Java 服务和前端在 Windows 上运行。

### 1. 安装开发工具

| 工具 | 版本/选择 | 检查命令 |
| --- | --- | --- |
| Docker Desktop | Linux containers，Compose ≥ 2.20.3 | `docker version` |
| [JDK](https://adoptium.net/temurin/releases/?version=17) | **JDK 17**，包含 `javac`，不要只装 JRE | `java -version`、`javac -version` |
| [Maven](https://maven.apache.org/download.cgi) | Maven 3.9+，解压后把 `bin` 目录加入系统 `Path` | `mvn.cmd -version` |
| [Node.js](https://nodejs.org/en/download) | Node.js 24 LTS，或 22.12+；安装时保留 npm | `node -v`、`npm.cmd -v` |
| [Python](https://www.python.org/downloads/windows/) | Python 3.10+；新安装可选 3.12，勾选加入 `PATH` | `python --version` |

`Path` 是 Windows 查找命令的位置。装好工具或修改 `Path` 后，要重新打开 PowerShell/IDEA。先确认这些检查命令都能执行，再继续。

### 2. 停止 Docker 模式中的应用，再启动本机模式

如果之前按第一章启动过整个项目，先执行：

```powershell
docker compose stop
```

保留已有 `.env` 和 `.local/dev/keys.json`，再双击 **`start-local.cmd`**，或在项目根目录执行：

```powershell
.\start-local.cmd
```

本机启动器会安装 Python 依赖、编译 Java 项目、安装前端依赖、启动基础设施，然后依次启动五个服务和前端。首次没有 `.env` 时，启动器会自动生成；已存在时直接使用。

看到 **`Ready. Web: http://localhost:5173`** 后，打开网页。查看 `.local/dev/login.txt` 获取账号；本机模式新建环境的管理员通常是 `local_admin`，不要把 Docker 模式的默认用户名和密码直接套过来。

常用操作：

```powershell
# 查看启动状态
powershell.exe -NoProfile -File deploy/local/dev.ps1 status

# 停止本机启动器管理的服务
.\stop-local.cmd
```

`stop-local.cmd` 会停止启动器创建的应用进程和令牌刷新进程，保留 Docker 基础设施、数据，以及由 IDEA 单独启动的进程。要完全停止，先在 IDEA 中停止正在运行的服务，再停止项目的四个基础设施容器；以下命令不会删除数据：

```powershell
docker stop civicflow-mysql civicflow-redis civicflow-rabbitmq civicflow-nacos
```

如果从本机模式切换回 Docker 模式，先执行上述停止步骤。若提示同名容器已存在，说明原环境使用了不同的 Compose 项目名。用下面的命令查看原名称：

```powershell
docker inspect civicflow-mysql --format '{{ index .Config.Labels "com.docker.compose.project" }}'
```

如果输出 `civicflow-local`，先执行 `$env:COMPOSE_PROJECT_NAME = 'civicflow-local'`，再在同一个 PowerShell 窗口执行第一章的 Docker 启动命令；其他名称按实际输出替换。以后也在这个项目名下执行启动和停止命令，避免创建第二套同名容器。

日志在 `.local/dev/`：`launcher.log` 是启动总日志，`build.log` 是后端构建日志，`npm.log` 是前端依赖安装日志，`auth.log` 等是对应服务的日志。PowerShell 如拦截脚本执行，可先运行 `Unblock-File deploy/local/dev.ps1`；单位电脑的管理策略限制需由管理员处理。

### 3. 使用 IDEA 调试单个服务

1. 用 IDEA 打开项目根目录，等待 Maven 导入，设置项目 SDK 为 JDK 17。
2. **新克隆的项目先执行一次 `start-local.cmd`**，它会生成 IDEA 启动配置需要的本地启动器 JAR；启动成功后执行 `stop-local.cmd`。
3. 在 IDEA 运行配置中选择 **`CivicFlow - Prepare IDEA`**，或者先在 PowerShell 执行：

```powershell
powershell.exe -NoProfile -File deploy/local/dev.ps1 prepare
```

4. 依次运行或调试 **`Local Auth` → `Local Resource` → `Local Appointment` → `Local Queue` → `Local Gateway`**，等前一个服务就绪再启动下一个。
5. 在另一个 PowerShell 窗口中启动前端：

```powershell
npm.cmd --prefix civicflow-web run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

前端终端需要保持开启，按 `Ctrl+C` 停止。不要同时再运行 `start-local.cmd`，否则会争用端口。

如果只想从 IDEA 一键启动而不打断点，可以使用 **`CivicFlow - Start all`**。单服务调试请使用上述 `Local ...` 配置，它们会加载启动器生成的凭据和密钥；直接运行无配置的 `*Application` 可能因缺少配置失败。

## 六、在 Linux 服务器上运行演示环境

本节适用于已有 Linux 服务器、能够 SSH 登录，并已安装 **Docker Engine、Compose ≥ 2.20.3 和 Git** 的用户。Docker 安装方式请按服务器发行版参考 [Docker 官方文档](https://docs.docker.com/engine/install/)；下方命令需要当前用户有 Docker 操作权限。

1. 在服务器终端下载项目：

```bash
git clone https://github.com/2370796166/CivicFlow.git
cd CivicFlow
```

2. 首次创建配置；已有配置时跳过，不覆盖：

```bash
if [ ! -f deploy/compose/.env ]; then
  cp deploy/compose/.env.example deploy/compose/.env
fi
nano deploy/compose/.env
```

将所有名字以 `PASSWORD` 结尾的配置项改为自己的密码，包括管理员密码；同时替换 `NACOS_AUTH_IDENTITY_VALUE`。密码建议使用长的随机字母和数字。`NACOS_AUTH_TOKEN` 需要 Base64 格式的随机值，可以在另一个终端执行 `openssl rand -base64 48`，把输出复制到该项等号后面。如果没有 `nano` 或 `openssl`，使用服务器已有的文本编辑器，或先安装这些工具。

3. 检查并启动：

```bash
docker compose config --quiet
docker compose up -d --build --wait --wait-timeout 300
docker compose ps -a
cat .local/dev/login.txt
```

**当前 Compose 默认绑定 `127.0.0.1`，直接访问“服务器 IP:5173”不会打开页面。** 个人远程演示可以用 SSH 转发，无需修改 Compose。在你自己的电脑上另外打开终端，将下面的 `USER` 和 `SERVER_IP` 替换为实际登录用户名与服务器地址：

```bash
ssh -N -L 5173:127.0.0.1:5173 USER@SERVER_IP
```

保持这个终端开启，再在自己电脑浏览器打开 `http://localhost:5173`；密码用服务器上 `login.txt` 的内容。关闭 SSH 终端会断开转发，但服务器容器仍在运行。本机 5173 端口需要空闲；如果服务器修改过网页端口，也要修改转发命令中右侧的目标端口。

这是一套个人演示部署方式。要开放公网网站，还需要针对域名、HTTPS、访问入口和运行凭据配置部署环境，不能把默认演示配置直接当作生产部署方案。

## 七、开发与测试

**只想启动项目，不需要执行这一章。** 测试用于修改代码后检查功能是否被破坏，需要先安装相应开发工具。

后端在项目根目录执行：

```powershell
mvn.cmd verify
```

它会检查 Java 格式、编译并运行测试。部分测试需要真实 MySQL、Redis、RabbitMQ 和 k6 容器，因此 Docker 引擎必须可用。

前端检查：

```powershell
npm.cmd --prefix civicflow-web ci
npm.cmd --prefix civicflow-web run lint
npm.cmd --prefix civicflow-web run typecheck
npm.cmd --prefix civicflow-web run test
npm.cmd --prefix civicflow-web run build
```

本机额外的端到端测试与压测工具不随仓库发布。仓库保留后端和前端常规测试；后端并发测试所需的 k6 夹具位于 `civicflow-appointment/src/test/resources/load/`，运行 Maven 测试时会自动读取。

### 项目目录怎么看

| 路径 | 用途 |
| --- | --- |
| `civicflow-web/` | Vue 前端和三种角色的页面 |
| `civicflow-gateway/` | 接口入口，负责路由、鉴权与限流 |
| `civicflow-auth/` | 登录、用户和角色 |
| `civicflow-resource/` | 网点、事项、窗口、号源及人员授权 |
| `civicflow-appointment/` | 预约、库存、确认、超时处理及对账 |
| `civicflow-queue/` | 签到、排队、窗口办理 |
| `civicflow-common/` | 各后端服务共用的响应与异常等代码 |
| `deploy/`、`compose.yaml` | Docker 构建和运行配置 |
| `deploy/local/` | 项目必需的本机启动代码；`dev.py` 也被 Docker 初始化容器使用 |
| `deploy/local/requirements.txt` | 本机启动器和 Docker 初始化使用的 Python 依赖 |
| `.local/` | 本机生成的配置、密钥、登录信息和日志，不提交 Git |

预约流程使用 Redis 原子预占、异步建单、幂等处理、数据库约束、状态版本校验、超时释放和库存对账。项目已验证本机启动、重启和端到端演示；全新机器的首次下载仍依赖网络环境，个人演示部署不代表已完成生产部署验证。
