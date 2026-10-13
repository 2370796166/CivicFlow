# 本机开发与 IDEA 调试

本说明用于需要调试源码的人。只想体验项目时，优先使用 [根 README 的 Docker 启动方式](../../README.md#4-启动步骤)。两种方式不要同时运行。

下面的命令在 **Windows PowerShell 的项目根目录**执行。这个目录包含 `pom.xml`、`compose.yaml` 和 `deploy` 文件夹。

## 1. 准备开发工具

| 工具 | 版本依据 |
| --- | --- |
| Docker Desktop | Linux 容器模式，Compose 2.20.3+；仍用它运行数据库等依赖 |
| JDK | 17，见根目录 `pom.xml`；需要 `javac`、`java` 和 `jar` |
| Maven | 原启动说明要求 3.9+；Docker 构建镜像使用 3.9.9 |
| Node.js / npm | Web 的 `package.json` 要求 `^22.20.0` 或 `>=24.12.0`；可选 Node.js 24.12+ |
| Python | 3.10+，见 `deploy/local/dev.ps1`；依赖由脚本安装 |
| IntelliJ IDEA | 仅在采用第 3 节的调试方式时需要 |

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

**成功标志：** 都能显示版本，Docker 同时有 `Client` 和 `Server`。安装工具后找不到命令时，重新打开 PowerShell。

本机方式会在 `.local` 下准备 Python 环境和本地启动器；Python 依赖版本在 `deploy/local/requirements.txt` 中锁定，不需要手动安装到全局环境。

## 2. 用启动器运行整套应用

如果之前启动了 Docker 全量模式，先在项目根目录执行 `docker compose stop`。首次使用、没有旧实例时跳过停止步骤。

在项目根目录执行：

```powershell
powershell -NoProfile -File deploy/local/dev.ps1 up
```

也可以双击根目录的 `start-local.cmd`，它调用同一个脚本。启动器会准备配置和四个基础容器、构建六个 Java 应用、首次安装 Web 依赖，并启动网页。

**成功标志：** 显示 `Ready. Web: http://localhost:5173`。随后打开该地址，在项目根目录执行 `Get-Content .local/dev/login.txt`，使用生成的管理员账号登录。

| 后续操作 | 项目根目录命令 |
| --- | --- |
| 查看状态 | `powershell -NoProfile -File deploy/local/dev.ps1 status` |
| 停止启动器及其应用 | `powershell -NoProfile -File deploy/local/dev.ps1 stop`，或双击 `stop-local.cmd` |
| 再次启动 | `powershell -NoProfile -File deploy/local/dev.ps1 up` |

停止启动器不会删除 Docker 数据卷，也不会停止 IDEA 自己启动的进程。数据库等基础容器仍会保留；日志在 `.local/dev/launcher.log` 和各服务对应的 `.log` 文件中。

## 3. 用 IDEA 调试后端

这一方式分三部分：**Docker 运行四个基础服务，IDEA 运行六个后端应用，PowerShell 运行 Web 前端**。

### 3.1 打开项目

在 IDEA 中选择 **Open**，打开根目录 `pom.xml`，按提示作为 Maven 项目导入。把 **File → Project Structure → Project SDK** 设为 JDK 17，等待导入完成。

如果之前已运行 Docker 全量模式，先执行 `docker compose stop`；如果启动器已运行整套本机应用，先执行第 2 节的 `stop` 命令。

### 3.2 准备基础服务和本地配置

在项目根目录执行：

```powershell
powershell -NoProfile -File deploy/local/dev.ps1 prepare
docker ps --filter "name=civicflow-"
```

`prepare` 会保留已有 `.env`；没有配置时自动生成，并准备开发密钥、IDEA 配置和后台启动器。无需先运行 Docker 全量应用，也不用手动创建数据库。

**成功标志：** 显示 `Ready for IDEA Local run configurations.`，`civicflow-mysql`、`civicflow-redis`、`civicflow-rabbitmq`、`civicflow-nacos` 四个容器在运行并为 `healthy`。此时尚未启动网页和业务后端。

以后每次 IDEA 调试前先执行 `prepare`。首次准备成功后，也可使用 IDEA 中的 `CivicFlow - Prepare IDEA` 配置。准备程序在后台刷新服务间通信凭据，调试期间不要执行 `stop-local.cmd`。

### 3.3 按顺序启动六个后端应用

在 IDEA 顶部选择下列运行配置，逐个运行或调试。一个服务启动成功后，再启动下一个；不要使用缺少本地配置的默认 `Application` 启动项。

| 顺序 | IDEA 配置 | 用途 | 默认电脑端口 |
| --- | --- | --- | --- |
| 1 | `Local Auth` | 登录和账号 | `8081` |
| 2 | `Local Resource` | 网点、事项、窗口和名额 | `8082` |
| 3 | `Local Appointment` | 预约 | `8083` |
| 4 | `Local Queue` | 排队与叫号 | `8084` |
| 5 | `Local Notification` | 站内通知 | `8085` |
| 6 | `Local Gateway` | 统一接收请求 | `8080` |

**成功标志：** 每个 Run 窗口出现 `Started ...Application`，且没有随后退出或报错。Auth 启动成功后，后台准备程序会创建初始管理员；已有账号不会被重置。

如果 IDEA 没有这些配置，或不支持现成的 Spring Boot 配置，可在 **Run → Edit Configurations → + → Application** 中手动创建，参考 [IDEA 官方说明](https://www.jetbrains.com/help/idea/run-debug-configuration-java-application.html)。JDK 选 17，模块选对应服务，工作目录选项目根目录：

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

### 3.4 启动网页并登录

另开 PowerShell，进入同一项目根目录，按顺序执行：

```powershell
npm.cmd --prefix civicflow-web ci
npm.cmd --prefix civicflow-web run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

**成功标志：** 显示 `http://127.0.0.1:5173/`。保持这个窗口开启，浏览器打开 [http://localhost:5173](http://localhost:5173)。再在项目根目录另开窗口查看 `Get-Content .local/dev/login.txt`。

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

部分后端测试会通过 Testcontainers 启动真实数据库和消息容器，需要 Docker 可用。小程序检查命令及隔离联调见 [小程序 README](../../civicflow-miniapp/README.md)。
