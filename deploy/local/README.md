# 在电脑上运行源码、用 IDEA 调试

这份说明适合需要修改代码的人。只想打开网页体验功能，请用 [根 README 的 Docker 启动方式](../../README.md#4-启动步骤)，准备工作更少。

这里有两种运行方式，选一种即可：

| 你的需求 | 操作路线 |
| --- | --- |
| 修改源码后，自动启动整套应用 | 准备第 1 节的工具，再按第 2 节操作 |
| 在 IDEA 中打断点、逐步调试 | 准备第 1 节的工具，再按第 3 节操作 |

不要同时启动这两种方式，也不要与 Docker 全量方式同时运行，否则会争用同一组端口。

下面以 Windows 为例。先下载并解压项目，方法见 [根 README](../../README.md#22-下载项目进入正确的文件夹)。打开 PowerShell，进入同时包含 `pom.xml`、`compose.yaml` 和 `deploy` 文件夹的目录，后文称它为“项目根目录”：

```powershell
# 换成自己的项目路径；后面的命令都在这个目录执行
cd "D:\projects\CivicFlow-main"
```

## 1. 准备开发工具

| 工具 | 需要的版本或设置 | 用来做什么 |
| --- | --- | --- |
| Docker Desktop | Linux 容器模式，Compose 2.20.3+ | 运行数据库、缓存和消息程序 |
| JDK | 17，见根目录 `pom.xml`；需要 `javac`、`java` 和 `jar` | 编译和运行 Java 后端 |
| Maven | 原启动说明要求 3.9+；Docker 构建镜像使用 3.9.9 | 下载 Java 依赖、构建后端 |
| Node.js / npm | Web 的 `package.json` 要求 `^22.20.0` 或 `>=24.12.0`；可选 Node.js 24.12+ | 下载网页依赖、运行网页 |
| Python | 3.10+，见 `deploy/local/dev.ps1` | 运行项目自带的启动器 |
| IntelliJ IDEA | 仅第 3 节需要 | 编辑代码、打断点调试 |

先打开 Docker Desktop，再确认工具可用：

```powershell
javac -version
mvn.cmd -version
node -v
npm.cmd -v
python --version
docker version
docker compose version
```

**成功标志：** 都能显示版本，Docker 同时有 `Client` 和 `Server`。安装工具后找不到命令时，先关闭并重新打开 PowerShell，再回到项目根目录重试。

本机方式会在 `.local` 下准备 Python 环境和本地启动器；Python 依赖版本在 `deploy/local/requirements.txt` 中锁定，不需要手动安装到全局环境。

## 2. 用启动器运行整套应用

如果之前启动了 Docker 全量模式，先在项目根目录执行 `docker compose stop`。首次使用、没有旧实例时跳过停止步骤。

工具准备好后，在项目根目录复制下面这一条命令执行：

```powershell
powershell -NoProfile -File deploy/local/dev.ps1 up
```

也可以双击根目录的 `start-local.cmd`，效果相同。首次运行会准备配置、启动四个基础程序、构建六个 Java 后端、安装网页依赖，最后启动网页。下载和构建需要等待，启动期间不要重复执行启动命令。

**成功标志：** 显示 `Ready. Web: http://localhost:5173`。

打开 [http://localhost:5173](http://localhost:5173)，在项目根目录查看登录信息：

```powershell
Get-Content .local/dev/login.txt
```

把 `Username:` 后面的内容填入网页的“用户名或手机号”，把 `Password:` 后面的内容填入“密码”。登录后看到管理员菜单，就可以开始使用；首次安装没有业务数据，列表为空很正常。

| 后续操作 | 项目根目录命令 |
| --- | --- |
| 查看状态 | `powershell -NoProfile -File deploy/local/dev.ps1 status` |
| 停止启动器及其应用 | `powershell -NoProfile -File deploy/local/dev.ps1 stop`，或双击 `stop-local.cmd` |
| 再次启动 | `powershell -NoProfile -File deploy/local/dev.ps1 up` |

停止启动器后，数据库等四个基础程序仍会保留，数据也保留。日志在 `.local/dev/launcher.log` 和各服务对应的 `.log` 文件中。需要停止基础程序时，使用第 3.5 节的 `docker stop` 命令。

## 3. 用 IDEA 调试后端

这一方式需要分别启动三部分：**先用 Docker 准备数据库等基础程序，再用 IDEA 启动后端，最后在 PowerShell 中启动网页。** 按下面的顺序做即可。

### 3.1 打开项目

在 IDEA 中选择 **Open**，打开根目录 `pom.xml`，按提示作为 Maven 项目导入。把 **File → Project Structure → Project SDK** 设为 JDK 17，等待导入完成。

如果之前已运行 Docker 全量模式，先执行 `docker compose stop`；如果启动器已运行整套本机应用，先执行第 2 节的 `stop` 命令。

### 3.2 准备基础服务和本地配置

在项目根目录执行：

```powershell
powershell -NoProfile -File deploy/local/dev.ps1 prepare
docker ps --filter "name=civicflow-"
```

`prepare` 意思是“只做准备”。它会启动数据库等基础程序，生成或保留 `.env`，准备登录和服务通信所需的密钥，并创建 IDEA 运行配置。无需先启动 Docker 全量应用，也不用手动创建数据库。

**成功标志：** 显示 `Ready for IDEA Local run configurations.`，`civicflow-mysql`、`civicflow-redis`、`civicflow-rabbitmq`、`civicflow-nacos` 四个容器在运行并为 `healthy`。此时尚未启动网页和业务后端。

以后每次 IDEA 调试前，先执行 `prepare`。首次准备成功后，也可在 IDEA 中运行 `CivicFlow - Prepare IDEA`。准备程序还会在后台维护后端互相访问时需要的凭据，所以调试期间让它继续运行，不要执行 `stop-local.cmd`。

### 3.3 按顺序启动六个后端应用

在 IDEA 顶部的运行配置下拉框中选择下列名称，点击运行按钮；要打断点时，点击调试按钮。一个服务启动成功后，再启动下一个。使用下面的 `Local ...` 配置，它们包含准备步骤生成的本地设置。

| 顺序 | IDEA 配置 | 用途 | 默认电脑端口 |
| --- | --- | --- | --- |
| 1 | `Local Auth` | 登录和账号 | `8081` |
| 2 | `Local Resource` | 网点、事项、窗口和名额 | `8082` |
| 3 | `Local Appointment` | 预约 | `8083` |
| 4 | `Local Queue` | 排队与叫号 | `8084` |
| 5 | `Local Notification` | 站内通知 | `8085` |
| 6 | `Local Gateway` | 统一接收请求 | `8080` |

**成功标志：** 每个 Run 窗口出现 `Started ...Application`，且没有随后退出或报错。Auth 启动成功后，后台准备程序会创建初始管理员；已有账号不会被重置。

如果六个配置都能启动，直接进入 3.4。找不到配置时，再展开下面的手动设置说明。

<details>
<summary>IDEA 没有 Local 配置时，如何手动创建</summary>

在 **Run → Edit Configurations → + → Application** 中手动创建，参考 [IDEA 官方说明](https://www.jetbrains.com/help/idea/run-debug-configuration-java-application.html)。JDK 选 17，模块选对应服务，工作目录选项目根目录：

| 模块 | Main class（主类） | `.local/dev` 下的配置文件 |
| --- | --- | --- |
| `civicflow-auth` | `com.civicflow.auth.AuthApplication` | `auth.properties` |
| `civicflow-resource` | `com.civicflow.resource.ResourceApplication` | `resource.properties` |
| `civicflow-appointment` | `com.civicflow.appointment.AppointmentApplication` | `appointment.properties` |
| `civicflow-queue` | `com.civicflow.queue.QueueApplication` | `queue.properties` |
| `civicflow-notification` | `com.civicflow.notification.NotificationApplication` | `notification.properties` |
| `civicflow-gateway` | `com.civicflow.gateway.GatewayApplication` | `gateway.properties` |

在 **Modify options → Add VM options** 中填写以下参数。路径要换成自己的项目路径，文件名要与服务对应；这是 **VM options**，不是 Program arguments：

```text
-Dspring.config.additional-location="file:D:/projects/CivicFlow-main/.local/dev/auth.properties"
```

</details>

### 3.4 启动网页并登录

另开一个 PowerShell，先用 `cd` 进入同一项目根目录，再按顺序执行下面两条命令。第一条安装网页依赖，第二条启动网页：

```powershell
npm.cmd --prefix civicflow-web ci
npm.cmd --prefix civicflow-web run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

**成功标志：** 显示 `http://127.0.0.1:5173/`。第二条命令会一直运行，保持这个窗口开启，浏览器打开 [http://localhost:5173](http://localhost:5173)。

再开一个 PowerShell，进入项目根目录，查看账号并按第 2 节的方法登录：

```powershell
Get-Content .local/dev/login.txt
```

Web 默认代理到 `http://127.0.0.1:8080` 网关。按需调整时，在 `civicflow-web/.env.local` 设置 `.env.example` 中的 `VITE_PROXY_TARGET`，修改后重启前端。队列刷新间隔配置也见 [Web README](../../civicflow-web/README.md)。

登录文件尚未出现时，检查 Auth 已启动，并查看 `.local/dev/launcher.log`。已有账号改过密码时使用当前密码，不用重新生成密钥。演示预约步骤见 [根 README](../../README.md#53-可选体验一次预约和办理)。

### 3.5 停止和下次启动

1. 在 IDEA 中停止自己启动的六个后端服务。
2. 在运行前端的 PowerShell 中按 `Ctrl+C`。
3. 在项目根目录执行 `powershell -NoProfile -File deploy/local/dev.ps1 stop`，停止后台准备程序。

数据和基础容器会保留。如还需要停止四个基础容器，在项目根目录执行：

```powershell
docker stop civicflow-mysql civicflow-redis civicflow-rabbitmq civicflow-nacos
```

下次打开 Docker Desktop，再按 **3.2 → 3.3 → 3.4** 操作。

## 4. 切换模式和基础容器重启

准备成功后，只重启基础容器时先取得它们实际所属的 Compose 项目名。这不能替代首次 `prepare`：

```powershell
$ideaComposeProject = docker inspect civicflow-mysql --format '{{ index .Config.Labels "com.docker.compose.project" }}'
docker compose -p $ideaComposeProject up -d --wait --wait-timeout 300 mysql redis rabbitmq nacos
```

切换回 Docker 全量模式时，先停止 IDEA 应用、前端、准备程序和基础容器，再将根 README 中的 `COMPOSE_PROJECT_NAME` 设为实际查询到的项目名，避免重复容器。保留 `.env`、`.local/dev` 和数据卷。

## 5. 运行开发检查

在项目根目录执行；这些检查不是首次体验项目的必做步骤：

```powershell
mvn.cmd verify
npm.cmd --prefix civicflow-web ci
npm.cmd --prefix civicflow-web run lint
npm.cmd --prefix civicflow-web run typecheck
npm.cmd --prefix civicflow-web run test
npm.cmd --prefix civicflow-web run build
```

**成功标志：** 后端显示 `BUILD SUCCESS`，网页代码检查和类型检查没有错误，测试通过，构建命令正常结束。

部分后端测试会通过 Testcontainers 自动创建临时数据库和消息容器，需要 Docker 可用。小程序检查命令及隔离联调见 [小程序 README](../../civicflow-miniapp/README.md)。

## 6. 常见问题

| 问题 | 先这样处理 |
| --- | --- |
| 提示找不到 `javac`、`mvn.cmd`、`node` 或 `python` | 核对第 1 节的工具是否安装、版本是否符合要求，重新打开 PowerShell 再查版本 |
| PowerShell 提示禁止运行脚本 | 当前系统策略阻止了脚本运行，需要按电脑的管理要求允许执行项目脚本；不要忽略提示继续启动 |
| 提示端口或同名容器已被占用 | 核对是否还在运行 Docker 全量模式或另一个启动器，先按原方式停止；切换步骤见第 4 节 |
| IDEA 启动提示缺少配置或密钥 | 先执行 `prepare`，再使用 `Local ...` 配置；手动配置时核对 3.3 的文件路径 |
| 网页能打开，但登录或接口访问失败 | 核对六个后端是否已启动，尤其是 Auth 和 Gateway；查看对应服务的运行窗口或日志 |
| 登录文件没生成 | 检查 Auth 是否启动成功，并查看 `.local/dev/launcher.log`；已有账号时用原密码 |
| 关闭网页终端后网页打不开 | IDEA 模式下网页命令需要持续运行，重新按 3.4 启动即可 |

启动器模式失败时，在项目根目录执行下面两条命令，查看状态和最近的启动日志：

```powershell
powershell -NoProfile -File deploy/local/dev.ps1 status
Get-Content .local/dev/launcher.log -Tail 100
```
