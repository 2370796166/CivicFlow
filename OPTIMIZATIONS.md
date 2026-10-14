# 运行优化与验证（2026-10-14）

本次优化覆盖代理限流、管理写鉴权、通知发布隔离、发布租约保护、小程序 CI 和 Web 页面加载。未改变业务表、Flyway、公开 API 路径、事件版本或 Redis key。

## 行为与部署

- **代理限流**：gateway 保持 `server.forward-headers-strategy=none`。`CIVICFLOW_GATEWAY_TRUSTED_PROXIES` 默认空，只信任配置数字 IP/CIDR 的连接。该连接的 X-Forwarded-For 必须是单个数字 IPv4/IPv6；域名、端口、多头、多跳和 zone ID 回退连接 IP，不做 DNS 查询。Nginx 覆盖客户端转发头。
- **入口网络**：Compose 仅 web/gateway 使用专用 ingress 网络，web/gateway 分别固定为 `172.30.250.2`/`172.30.250.3`。如有冲突，一起调整 `CIVICFLOW_INGRESS_SUBNET`、`CIVICFLOW_INGRESS_WEB_IP`、`CIVICFLOW_INGRESS_GATEWAY_IP`。gateway 只信任 web /32；生产需配置精确代理并限制直连。原业务卷不迁移。
- **管理写**：resource/appointment/queue/notification 的 ADMIN POST/PUT/PATCH/DELETE，在本地 JWT 验签后用原认证 Bearer 调用 auth 已有 `GET /api/v1/user/me`，核验当前账号、tokenVersion、身份及 ADMIN 角色。撤权/禁用/失效返回各服务既有 403；依赖故障返回 `COMMON_503_DEPENDENCY_UNAVAILABLE`/503，不执行写入。权限不缓存，普通读接口沿用现有验签/RBAC；已核验的在途写仍可能与随后撤权存在短暂竞态。
- **通知发布**：appointment/queue 使用独立通知 scheduler，与普通补偿线程隔离。每轮默认预算 5 秒（`civicflow.<service>.notification-batch-budget`），在每条认领前检查；在途确认保持原 3 秒超时。认领绑定候选 attempts，完成更新绑定 `id + PUBLISHING + 本次 attempts` 并检查影响行数；旧实例不能覆盖新租约结果。CorrelationData 使用 `eventId:attempts`，消息 eventId 不变，重复消费仍按原键去重。
- **升级顺序**：先停止旧通知 publisher 再启动新 appointment/queue，避免旧完成更新与新版混跑；新实例恢复未完成 outbox。管理写权限加固在所有相关实例更新后验收。auth 依赖异常恢复后使用原幂等键重试。完整部署说明见 [deploy/README.md](deploy/README.md)。
- **依赖**：resource/notification 新增 Spring OpenFeign/LoadBalancer，用于受保护 API 调用与服务发现，由既有 Cloud BOM 管理，Spring 官方维护，Apache-2.0；无独立版本号。
- **Web**：Element Plus 按页面局部导入组件/样式，布局懒加载，表格行类型明确。入口主 JS 为 114.89 kB（gzip 43.27 kB），基线为 402.85 kB（gzip 138.68 kB），约减少 71%；不是全部首屏资源口径。
- **CI**：缓存 Web/小程序两份锁文件，小程序执行 `npm ci` 和 `npm run check`。真实后端联调需要 fixture，微信模拟器/真机是独立验收，不把构建当作微信验收。

## 本机验证

- 全仓 Maven verify：219 项，失败/错误/跳过均 0，含全新 MySQL/Flyway、1000-VU、库存不为负、唯一有效预约、确认/超时竞态、消息故障及双窗口叫号。
- 新增测试覆盖四服务当前 ADMIN/撤权/身份错配/auth 故障、实际 Feign HTTP/SecurityFilterChain 拒绝写入、真实 MySQL 并发认领与迟到完成、通知预算和调度隔离。
- 收尾统一成功码常量引用后，鉴权定向 verify 13 项再次通过并重建最终 JAR。
- Web：按锁文件 npm ci，lint 0 错误（28 警告）、typecheck、68 单测、生产 build 通过。
- 小程序：typecheck/lint、20 单测、微信目标 build 通过，默认真实后端测试 1 项跳过；随后隔离六服务环境中该真实请求层测试 1 项通过。
- 真实浏览器：三角色资源/授权、预约、确认、签到、叫号、办理、管理查询/审计、站内通知及已读完整通过。
- 真实 Nginx：两个独立客户端分别得到 200/200/429，非可信直连伪造头仍为 429。示例/本机 Compose 配置检查及语法/敏感字面量检查通过。

可重复入口：

```powershell
mvn -B verify
npm.cmd --prefix civicflow-web run lint
npm.cmd --prefix civicflow-web run typecheck
npm.cmd --prefix civicflow-web test
npm.cmd --prefix civicflow-web run build
npm.cmd --prefix civicflow-miniapp run check
python deploy/tests/proxy_rate_limit.py
$env:CIVICFLOW_WEB_E2E='1'
python civicflow-miniapp/tools/live_backend.py
```

代理测试需要本地 `civicflow/web:local`、`civicflow/gateway:local` 运行镜像和当前打包的 gateway JAR。业务联调使用唯一项目名、独立端口和数据卷，结束后清理自身进程、容器及生成凭据；不操作原部署。微信工具/真机和生产目标环境门禁仍待验收。
