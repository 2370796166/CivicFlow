# 智序微信小程序市民端

基于仓库 main 提交 `6538b39` 开发。微信小程序上传、体验版和正式发布均未执行；提交代码到 GitHub 不代表已完成微信发布或验收。

本次类型检查、lint、20 项单元测试、微信目标构建及真实预约/签到/员工 Web/通知闭环均已通过。微信模拟器与真机尚未验证，具体范围和证据见 [VALIDATION.md](VALIDATION.md)。

工程采用 DCloud 官方 `uni-preset-vue#vite-ts` 模板的 Vue 3 / Vite 编译配置与统一编译器版本 `3.0.0-5020420260813003`，加入 TypeScript、Pinia 和微信兼容二维码编码器。依赖版本及 `package-lock.json` 已锁定。全部业务请求走现有 Gateway `/api/v1`。

## 启动与微信导入

需要 Node.js 22 或 24、npm，微信开发者工具已在本机安装。

```powershell
cd C:\Users\lenevo\.codex\worktrees\1206\CivicFlow\civicflow-miniapp
npm ci
if (!(Test-Path .env)) { Copy-Item .env.example .env }
npm run dev:mp-weixin
```

1. 编辑 `.env` 的 `VITE_API_BASE_URL`，指向要联调的网关，例如 `http://127.0.0.1:8080/api/v1`。更改地址后重启编译。
2. 在 `src/manifest.json` 的 `mp-weixin.appid` 填写自己的小程序 AppID。默认 `touristappid` 仅供工具支持的游客项目导入，不代表已具备真机或发布能力。无需 AppSecret。
3. 开发者工具导入 **`civicflow-miniapp/dist/dev/mp-weixin`**；构建后导入 **`civicflow-miniapp/dist/build/mp-weixin`**。不要导入源码目录，也不需要在工具中额外构建 npm。
4. 本地 HTTP 调试需要在项目“详情 → 本地设置”按需关闭域名、TLS、HTTPS 校验。源工程默认 `urlCheck: true`；正式验证要重新启用并配置合法 HTTPS 网关域名。
5. 使用具有 `USER` 权限的已有账号登录。STAFF、ADMIN 单角色账号会被拒绝；账号同时具有 USER 时可使用市民端。

```powershell
npm run typecheck
npm run lint
npm test
npm run build:mp-weixin
# 一次执行全部检查
npm run check
```

编译器提示未配置 uni 统计 AppID，不影响微信目标构建；本工程未接入统计服务。

## 页面与业务

底部导航为首页、我的预约、我的。工程有 13 个页面：登录、首页、网点事项、日期时段、预约进度、预约列表、预约详情、签到、排队、通知列表、通知偏好、我的、使用说明。

- 首页按关键词和真实页码查询网点；有效预约按服务端状态查询并提供快捷入口。通知中心不显示虚构未读总数。
- 网点展示实际地址、事项描述和参考办理时长；有效经纬度打开地图，缺少坐标时复制地址。不拨打脱敏电话。
- 时段查询使用 `serviceDate`，余量查询使用 `date`。通过配置 `id` 与实时结果 `configuration.slotId` 对齐。配置总量不作为余量，未知余量明确提示，可尝试由服务端裁决。
- 使用规则接口的提前预约天数、停办日期和取消提前量。未放号、暂停、截止、满额和未知余量分别显示。放号/截止边界重新查询；未收到新配置时不会仅凭本地倒计时开放时段。
- 创建预约先保存原请求键和 slotId；已受理则保存 reservationId。503 带有效 reservationId 时继续查询。写请求网络失败不自动重试；没有受理编号时，用户可用原键“核实原预约请求”。结果未知不会显示为预约失败。
- 详情展示所有订单状态，确认/取消携带版本号和请求键。取消前弹窗；版本/状态冲突重新查询。倒计时不会在客户端变更订单状态。
- 签到码用纯 JavaScript 编码器和 `uni.createCanvasContext` 绘制，凭证只在内存。刷新、到期、页面隐藏、卸载、退出、订单状态变化均清理。自助签到使用既有 outletId + checkInToken 校验，不含定位核验。
- 排队显示真实号码、前方人数、当前叫号及叫号窗口编号。后端当前仅返回顺序或状态提示，不生成预计分钟数。完成和过号停止轮询。
- 通知只使用 notification 服务的 id/title/body/status/时间字段。仅已送达记录允许标记已读；失败与死信单独显示。无关联字段时不从正文提取预约编号。旧 `/user/messages` 接口不参与通知中心。
- 可选通知偏好保存后重新读取；必要通知不受此开关影响。

## 会话与可靠性

访问令牌只放 Pinia 内存。刷新令牌保存在小程序本地私有存储，默认最多保存 1 天（`VITE_REFRESH_STORAGE_DAYS`，最大 7 天；设置 0 可关闭持久化，还受服务端实际令牌有效期限制）。小程序本地存储不是额外加密保险箱，因此不保存密码、签到码或用户手机号；项目不记录令牌日志。令牌轮换后更新刷新令牌及本地有效期，过期、刷新失败和退出均清理。

并发 401 共用一次刷新，迟到的旧令牌响应不再重复轮换。刷新请求不用普通拦截链。退出期间返回的请求不能恢复旧会话或返回用户数据。GET 的网络/429/502/503/504 最多退避重试一次；写请求仅在未授权响应后更新认证并重试一次，且保留原操作键与版本，不进行网络自动重试。

待定预约与确认/取消请求上下文按用户归属保存，最多 24 小时，退出时删除。冷启动先恢复会话再核对待定请求，避免启动时清空可恢复上下文。错误区显示真实业务失败与追踪查询码；轮询不反复弹 Toast。

64 位标识均按字符串传递。服务日期/时间保留服务器原值；Instant 展示固定加北京时间偏移，不依赖运行设备时区。列表防止旧响应覆盖新选择；页面显示立即刷新，隐藏/卸载/退出停止轮询和清理敏感缓存。

## 后端版本与隔离联调

需要包含上述基线能力的网关及六个 Java 应用，尤其是 notification 服务和 `/user/slots/availability`。不能假定旧运行目录的容器具备这些接口。

对应迁移为预约 V1–V7、排队 V1–V5、通知 V1。已有 `.env` 升级需要 `CIVICFLOW_NOTIFICATION_DB_APP_USERNAME`、`CIVICFLOW_NOTIFICATION_DB_APP_PASSWORD`、`CIVICFLOW_NOTIFICATION_DB_MIGRATION_USERNAME`、`CIVICFLOW_NOTIFICATION_DB_MIGRATION_PASSWORD`。原数据库升级必须先核对 Flyway 历史，不能套用另一运行目录的配置。

本次发现并修复 Compose 首次启动问题：MySQL 初始化目录只挂载 `01-create-service-databases.sh`；使用 TCP 的 `02-ensure-notification-database.sh` 由独立健康后任务运行。没有更改 Java 业务或数据库迁移文件。

可复现的隔离测试：

```powershell
# 仓库根目录，当前 Java 17 + Maven
mvn -B -DskipTests package
# 员工 Web UI 联调依赖
npm --prefix civicflow-web ci
# 需要 Docker Linux 引擎及 Python requests/bcrypt/cryptography/PyJWT/PyYAML
python civicflow-miniapp/tools/live_backend.py
```

此脚本不读写真实 `.env`，每次生成 `cf-mini-随机值` 的容器、网络和数据卷。应用仅绑定 `127.0.0.1:18080–18085`，基础设施端口为 33306、16379、15674、25674、18848、19848，员工 Web 使用 15173；检查占用后才启动。生成合成用户、网点、事项、窗口、号源，并在真实 Gateway 上运行 `tests/live.integration.test.ts` 中的小程序请求层。号源先按计划创建，在放号前预热，再开放。

测试链路为登录 → 查询网点/事项/余量 → 异步预约 → 确认 → 凭证 → 自助签到 → 员工 Web UI 叫号/开始/完成 → 订单同步 → 创建/确认/叫号通知送达 → 已读 → 偏好保存重读 → 新预约取消 → 刷新恢复 → 注销。员工动作用 Playwright 在真实 Web 工作台上点击并确认，小程序侧同时查询 CALLED/SERVING/COMPLETED。浏览器使用已安装的 Playwright Chromium，或 Windows Edge/Chrome；没有浏览器时需准备 Playwright Chromium。真实请求层和员工 Web UI 验证不等于微信模拟器验收。

通知与订单同步均有最多 45 秒的有界等待。常规 `npm test` 不启动后端，单独跳过真实链路测试；上述脚本才执行真实链路。运行日志在根目录 `.local/cf-mini-随机值`。结束时停止本次 Java 子进程、移除本次容器及独立卷，删除生成的凭据与配置，保留诊断日志。

## 微信开发者工具与真机

本机工具路径：`D:\微信开发者工具\微信web开发者工具\cli.bat`。已读取本机 CLI 的 `--help`、`open --help`、`auto --help`；不上传或发布。

如果需要 CLI 自动化，在开发者工具“设置 → 安全设置 → 服务端口”启用服务端口。这是持久安全设置变更，本次自动审批未允许修改；当前尚未通过微信模拟器验证。启用后可以执行：

```powershell
& 'D:\微信开发者工具\微信web开发者工具\cli.bat' open --project 'C:\Users\lenevo\.codex\worktrees\1206\CivicFlow\civicflow-miniapp\dist\build\mp-weixin'
& 'D:\微信开发者工具\微信web开发者工具\cli.bat' auto --project 'C:\Users\lenevo\.codex\worktrees\1206\CivicFlow\civicflow-miniapp\dist\build\mp-weixin'
# 若工具提供不同自动化端口，设置 WECHAT_AUTOMATOR_WS 后运行
npm run wechat:smoke
```

smoke 脚本只连接已启用的自动化会话，检查登录/说明页面并保存真实截图；它不改变工具安全设置，也不能替代真实后端或完整微信交互验证。检查登录、表单错误、返回原页面、三个 Tab、预约进度恢复、签到码刷新/到期、页面隐藏恢复及排队终态后，再安排真机验收。

真机使用自己的 `localhost` 无法访问电脑。推荐配置可访问的 HTTPS 网关并添加 request 合法域名；开发局域网调试时，只暴露网关入口：

```powershell
# 在管理员 PowerShell 中按需执行；将 IP 换成电脑实际局域网地址
netsh interface portproxy add v4tov4 listenaddress=192.168.1.10 listenport=18080 connectaddress=127.0.0.1 connectport=8080
New-NetFirewallRule -DisplayName CivicFlow-Miniapp-Gateway -Direction Inbound -Action Allow -Protocol TCP -LocalPort 18080 -Profile Private -RemoteAddress LocalSubnet
# .env: VITE_API_BASE_URL=http://192.168.1.10:18080/api/v1，然后重启编译。
# 仅用于当前工具/真机调试模式允许的 HTTP 环境。
# 完成后恢复
netsh interface portproxy delete v4tov4 listenaddress=192.168.1.10 listenport=18080
Remove-NetFirewallRule -DisplayName CivicFlow-Miniapp-Gateway
```

这些命令本次未执行；不开放 MySQL、Redis、RabbitMQ、Nacos 和内部应用端口。游客 AppID 的预览、自动化和真机能力以当前工具实际提示为准；可能需要真实 AppID、开发者身份及扫码。

## 发布准备和后续扩展

正式环境需提供真实 AppID、合法 HTTPS 网关域名和有效证书、后台 request 域名配置，以及符合实际数据处理的隐私说明和服务类目。隐私声明应覆盖账号登录、预约记录、本地会话存储、通知等实际处理内容。涉及微信隐私接口时再按平台要求接入授权。发布前在当前微信后台复核规则并完成真机测试；本次未上传体验版或发布。

微信登录后续由小程序 `uni.login` 获取 code，交给后端新增账号关联接口；AppSecret 和 code2Session 仅放后端。手机号授权需要账号资质和后端换取接口；订阅消息需要真实模板 ID、用户授权和服务端事件投递。当前没有假入口、假推送，核心闭环不依赖这些能力。

参考官方资料：[uni-app CLI](https://uniapp.dcloud.net.cn/quickstart-cli.html)、[Vue 3 官方模板](https://github.com/dcloudio/uni-preset-vue/tree/vite-ts)、[微信 CLI](https://developers.weixin.qq.com/miniprogram/dev/devtools/cli.html)、[微信网络能力](https://developers.weixin.qq.com/miniprogram/dev/framework/ability/network.html)、[微信隐私说明](https://developers.weixin.qq.com/miniprogram/dev/framework/user-privacy/miniprogram-intro.html)。本次微信文档在线读取受限，CLI 参数以本机帮助为验证依据；发布要求仍需在当前官方后台复核。
