# CivicFlow（智序）

一个公共服务预约与排队项目：普通用户预约和签到，员工叫号和办理，管理员配置业务并查看记录。

**第一次使用，按下面 5 步操作，就能在自己的 Windows 电脑上运行项目。** Docker 会负责构建和运行网页、后端及数据库，不用另外安装 Java、Maven、Node.js 或 Python，也不用手动建数据库。

操作顺序：**安装 Docker → 下载并解压 → 生成配置 → 启动 → 打开网页登录**。只想先看看项目，完成第 1 节即可；想体验预约和叫号，再看第 2 节。

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

回到 PowerShell，查看初始管理员的登录信息：

```powershell
Get-Content .local/dev/login.txt
```

将 `Username:` 后面的内容填到网页的“用户名或手机号”，将 `Password:` 后面的内容填到“密码”，点击“登录”。**密码是第 ③ 步随机生成的，不要直接用 `1234`。** 如果后来改过账号密码，以修改后的为准。

也可以在项目文件夹中打开 `.local/dev/login.txt` 查看。若文件还没生成，先检查第 ④ 步的 `admin-init` 是否正常完成。

**检查结果：** 登录后看到管理员的“网点”“事项”“窗口”等菜单，项目就启动好了。此时列表为空是正常的，下面会教你添加演示数据。启动成功后可以关闭 PowerShell，Docker Desktop 继续保持运行。

## 2. 试用一次完整流程

系统不会自动添加网点和预约。先用管理员按下面顺序配置，再切换账号体验。

### 管理员：准备演示数据

1. **建两个账号**：进入“用户与角色”，新增普通用户（例如 `demo_user`，角色选 `USER`）和员工（例如 `demo_staff`，角色选 `STAFF`）。密码设置为 8–72 位，记下自己填写的用户名和密码。
2. **建网点、事项和窗口**：分别进入对应菜单，点击“新增”。可以用下面的例子填写，其余必填项按页面提示填写。

   | 菜单 | 编码示例 | 名称示例 | 需要注意 |
   | --- | --- | --- | --- |
   | 网点 | `DEMO_OUTLET` | 演示服务中心 | 保存后复制列表中的数字 ID |
   | 事项 | `DEMO_ITEM` | 业务咨询 | 预计办理时间可用默认值，保存后复制数字 ID |
   | 窗口 | `DEMO_WINDOW` | 1 号窗口 | 网点 ID 填刚才复制的数字 |

   **编码和 ID 是两回事：** 编码由你填写，ID 是保存后系统生成的数字。后面要求填 ID 时，复制数字，不要填名称或编码。资源状态保持 `ENABLED`（启用）。

3. **给窗口分配业务和员工**：在窗口列表点击“绑定事项”，勾选刚创建的事项并保存；点击“人员授权”，授权刚创建的员工。
4. **添加可预约时段**：进入“号源日历”，点击“单日新增”。“号源”就是某个时段允许预约的名额，按下面的例子填写。

假如现在是北京时间 **10:00**，可以这样配置。**这只是时间示例**，实际操作时根据当前时间顺延；放号时间安排在几分钟后，办理时段安排在更晚的时间，签到范围要包含你准备签到的时间：

| 设置 | 示例 |
| --- | --- |
| 服务日期、网点和事项 | 今天，以及刚才复制的 ID |
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
<summary>我要修改代码，用 IDEA 调试</summary>

准备 Docker Desktop、JDK 17、Maven 3.9+、Node.js 24 或 22.12+、Python 3.10+，并确保这些工具的命令可以执行。

1. 如果已经用了 Docker 启动，先执行 `docker compose stop`。
2. 双击 `start-local.cmd`，等待出现 `Ready. Web: http://localhost:5173`。账号仍在 `.local/dev/login.txt`。
3. 停止本机应用用 `stop-local.cmd`；它会保留数据库等容器，IDEA 单独启动的服务需在 IDEA 中停止。
4. 要调试单个服务，先成功执行一次本机启动，再停止它；在 IDEA 运行 `CivicFlow - Prepare IDEA`，依次调试 `Local Auth`、`Local Resource`、`Local Appointment`、`Local Queue`、`Local Gateway`。
5. 前端另开 PowerShell，在项目文件夹执行：

```powershell
npm.cmd --prefix civicflow-web run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

前端终端保持开启，按 `Ctrl+C` 停止。本机启动日志在 `.local/dev/`，启动代码在 `deploy/local/`。不要同时再启动 Docker 模式。

如果从本机模式切换回 Docker，先停止本机应用、IDEA 服务和四个基础设施容器：

```powershell
docker stop civicflow-mysql civicflow-redis civicflow-rabbitmq civicflow-nacos
```

若提示同名容器已存在，执行下面的命令查看原 Compose 项目名：

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
