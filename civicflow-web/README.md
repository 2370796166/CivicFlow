# civicflow-web

Vue 3 单应用的 USER、STAFF、ADMIN 三入口。普通用户端已接入网点、事项、预约详情、确认/取消、签到二维码和排队进度；窗口人员端已接入授权窗口、工作会话、叫号与办理操作；管理员端已接入网点、事项、窗口、号源、用户与角色管理，以及单号源对账和安全修复。

## 本地运行

使用 Node.js 20.19+ 或 22.12+。复制 `.env.example` 为 `.env.local`，按需设置本地 gateway 地址、3–5 秒的 `VITE_QUEUE_POLL_MS` 和 3–10 秒的 `VITE_STAFF_POLL_MS`。使用 `npm ci` 安装锁定依赖，运行 `npm run dev`。检查命令为 `npm run typecheck`、`npm run lint`、`npm run test`、`npm run build`。

当前后端没有实现普通用户 `GET /api/v1/user/slots`。网点详情可以选择事项和日期，但无法展示号源、选择时段或从页面发起抢号；页面会明确提示这一缺口。前端不会调用 ADMIN 号源接口或推测库存。

窗口人员接口当前只有 `ACTIVE/ENDED` 工作会话，没有暂停/恢复；STAFF 查询只返回本人当前会话与当前票，没有等待人数、已叫号或过号列表。工作台会明确标出这些数据不可用，不借用 ADMIN 概况接口或以本地记录冒充完整队列。

管理员预约查询和操作日志查询尚无可调用的服务端 GET 接口；对账仅有单号源 POST，没有历史列表或详情 GET；窗口事项绑定仅有整体替换 PUT，没有读取当前绑定的 GET。页面会明确标明这些限制。窗口绑定提交前再次提示替换范围；号源批量生成先展示日期范围，确认后提交并显示逐日结果。

浏览器只访问同源 `/api/v1`；Vite 在本地把 `/api` 代理到 gateway。生产部署应由同源反向代理转发，且不暴露 `/internal/**`。

## 真实 E2E

完整 Docker Compose 的安装和测试命令见 [根 README](../README.md)。运行中的 Compose 使用 `python -u scripts/e2e_docker.py`，经 Nginx 执行三角色流程、真实 5 分钟超时和对账，保留唯一前缀合成数据。另有 `python -u scripts/e2e_v1.py` 启动隔离基础设施、五个真实 JAR 和 Vite，须先完成 Maven 构建并释放 8080–8084/5173，不能与完整 Compose 同时运行。浏览器驱动 `e2e/live-flow.mjs` 由脚本通过 stdin 接收一次性合成账号，不使用固定密码或保存 token。

## 会话与请求

现有 auth 接口通过 JSON 返回 `{accessToken,expiresIn,refreshToken,user}`，refresh 接收 `{refreshToken}` 并轮换。当前两种 token 都仅保存在 Pinia 内存，页面刷新需重新登录。没有使用 localStorage、sessionStorage、URL 或日志持久化凭证。若以后改为 HttpOnly Cookie，必须先在 auth/gateway 实现 Cookie、CSRF 防护并更新安全契约。

Axios 对并发 401 共享一次刷新 Promise；刷新失败时清除内存会话并跳到登录。GET 去重为显式 `dedupe: true`，仅取消同一方法、路径和参数的上一个请求，不影响写操作。服务端返回的 `requestId` 是追踪依据；网关会重新生成客户端传入的 `X-Request-Id`。角色菜单只改善导航体验，所有接口仍由网关和业务服务鉴权。
