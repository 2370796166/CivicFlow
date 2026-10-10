# CivicFlow（智序）

一个公共服务预约与排队项目：用户预约和签到，员工叫号和办理，管理员配置业务并查看记录。

**第一次使用，按下面的 Docker 方式启动即可。** 不用安装 Java、Maven、Node.js 或 Python，也不用手动建数据库。

## 1. 启动项目

### ① 安装 Docker

从 [Docker 官网](https://docs.docker.com/desktop/setup/install/windows-install/) 安装 **Docker Desktop**，按提示启用 WSL 2、使用 Linux 容器。安装后打开 Docker Desktop，等它启动完成。

### ② 下载项目

打开 [GitHub 项目页面](https://github.com/2370796166/CivicFlow)，点击 **Code → Download ZIP**，下载后解压。

找到能看到 `README.md`、`compose.yaml` 和 `start-docker.cmd` 的文件夹。打开 Windows PowerShell，用 `cd` 进入这个文件夹，例如：

```powershell
cd D:\projects\CivicFlow-main
```

上面的路径换成你自己的解压路径。**后面的命令都在这个文件夹中执行。**

### ③ 准备配置（只做一次）

首次启动需要创建 `deploy/compose/.env`，它保存数据库连接信息和初始密码。展开下面的内容，复制整段命令到 PowerShell 执行即可。

已有 `.env` 就跳过这一步，不要覆盖原配置。

<details>
<summary>点击展开：生成配置的命令</summary>

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

### ④ 启动并登录

执行：

```powershell
docker compose up -d --build --wait --wait-timeout 300
```

也可以双击 **`start-docker.cmd`**。第一次要下载依赖和构建项目，比较慢；看到下载或构建进度时，继续等待。

启动成功后，浏览器打开 **[http://localhost:5173](http://localhost:5173)**。

用下面的命令查看登录账号和密码，也可以直接打开这个文件：

```powershell
Get-Content .local/dev/login.txt
```

`Username` 是用户名，`Password` 是密码。使用文件中的内容登录；如果后来改过账号密码，以修改后的为准。

## 2. 试用一次完整流程

第一次登录后，业务列表为空是正常的，先用管理员准备演示数据。

1. **建账号**：在“用户与角色”创建普通用户（角色 `USER`）和员工（角色 `STAFF`），密码至少 8 位。
2. **建资源**：创建网点、事项和窗口。填写“网点 ID”“事项 ID”时，从列表复制系统生成的数字 ID；资源保持 `ENABLED`（启用）。
3. **配窗口**：在窗口列表点击“绑定事项”和“人员授权”，绑定刚创建的事项与员工。
4. **建号源**：在“号源日历”点击“单日新增”，按下面的示例填写。

假如现在是北京时间 **10:00**，可以这样配置；实际操作时按当前时间顺延，不要照抄过去的时间：

| 设置 | 示例 |
| --- | --- |
| 服务日期、网点和事项 | 今天，以及刚才复制的 ID |
| 办理时段、总额度 | `10:30–11:00`，`10` 个名额 |
| 放号时间 | 今天 `10:05` |
| 签到时间 | 今天 `09:50–11:00` |
| 初始状态 | `SCHEDULED` |

**等到放号时间，再点击该行的 `OPEN` 并确认，才会开放预约。** 如果提示库存正在准备，稍等约一分钟再试。

接下来退出管理员账号，按顺序体验：

- **普通用户**：选择网点、事项、日期和时段 → 预约 → 五分钟内“确认预约” → “获取二维码” → “现场签到”。没有扫码枪也能完成签到。
- **员工**：选择授权窗口 → “开始工作” → “叫下一号” → “开始办理” → “完成办理”。
- **管理员**：在“预约查询”和“预约操作日志”查看结果。

## 3. 平时怎么用

先打开 Docker Desktop，再在项目文件夹中执行：

| 想做什么 | 怎么操作 |
| --- | --- |
| 停止项目 | 双击 `stop-docker.cmd`，或执行 `docker compose stop` |
| 下次启动 | `docker compose up -d --wait --wait-timeout 300` |
| 更新代码后启动 | `docker compose up -d --build --wait --wait-timeout 300` |
| 查看运行状态 | `docker compose ps -a` |
| 查看报错 | `docker compose logs --tail 100` |

正常停止会保留账号和数据。**不要删除 `.local/`、覆盖原 `.env`，也不要用 `docker compose down -v` 处理报错**，否则可能丢失数据或密钥。

默认网页只能在运行项目的电脑上访问。

## 4. 启动失败怎么办

| 遇到的问题 | 先这样处理 |
| --- | --- |
| 找不到 `docker`，或连接不上 Docker | 打开 Docker Desktop；安装后重新打开 PowerShell |
| 提示找不到 `.env` 或缺少密码 | 回到“准备配置”，确认文件位于 `deploy/compose/.env` |
| 提示端口被占用 | 停掉之前运行的项目或占用端口的程序；不要同时使用 Docker 和本机启动 |
| 下载超时或失败 | 检查网络及 Docker 的代理设置，再重新启动 |
| 网页打不开、服务不健康 | 用上面的状态和日志命令，查看哪个服务报错 |
| 能登录，但没有可预约时段 | 核对窗口绑定、资源状态、日期，以及号源是否已切换为 `OPEN` |

初始化任务显示 **`Exited (0)` 是正常完成**。如果只是启动慢，可以用以下命令多等一会：

```powershell
docker compose up -d --wait --wait-timeout 600
```

若提示不支持 `include`，请更新 Docker Desktop，项目需要 Compose 2.20.3 或更新版本。已有账号不会因修改 `.env` 中的密码而重置。

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

服务器需先装好 Docker Engine、Compose 2.20.3+ 和 Git，安装方法见 [Docker 官方文档](https://docs.docker.com/engine/install/)。

下载项目，首次复制配置；已有 `.env` 时保留原文件：

```bash
git clone https://github.com/2370796166/CivicFlow.git
cd CivicFlow
if [ ! -f deploy/compose/.env ]; then
  cp deploy/compose/.env.example deploy/compose/.env
fi
```

用文本编辑器打开 `.env`，替换所有 `PASSWORD` 配置及 `NACOS_AUTH_IDENTITY_VALUE`。用 `openssl rand -base64 48` 生成 `NACOS_AUTH_TOKEN` 的值，再启动：

```bash
docker compose up -d --build --wait --wait-timeout 300
cat .local/dev/login.txt
```

默认只允许服务器本机访问。个人演示时，在自己的电脑上执行下面的命令，将 `USER` 和 `SERVER_IP` 换成服务器登录用户名与地址：

```bash
ssh -N -L 5173:127.0.0.1:5173 USER@SERVER_IP
```

保持 SSH 终端开启，在自己电脑打开 `http://localhost:5173`。本机 5173 端口需要空闲。这是个人演示方式，公网部署还需要域名、HTTPS 等配置。

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
