# CivicFlow 安全设计

## 1. 信任边界

```text
Browser/App (不可信)
    -> TLS/WAF
Gateway (认证、粗粒度授权、限流、清洗 header)
    -> 内网 TLS + 原 access JWT/签名上下文
Business service (再次验签、方法 RBAC、资源归属、状态规则)
    -> service JWT + audience/scope
Internal API (不暴露 gateway 路由)
```

Gateway 先删除客户端所有 `X-User-Id`、`X-User-Roles`、`X-Service-*`、`X-Request-Id`、`traceparent` 非法值，再生成可信追踪头。业务服务不得仅信这些头；外部链路再次校验 access JWT，内部链路校验 service JWT 与 audience/scope。生产服务间启用 TLS，条件允许时升级 mTLS。

## 2. JWT 与会话

- Access JWT 使用非对称签名（首版 RS256，2048 位以上；可迁移 ES256），私钥只在 auth；gateway/services 通过 JWKS 公钥本地验签。
- 必需 claims：`iss`,`sub=userId`,`aud`,`iat`,`nbf`,`exp`,`jti`,`roles`,`tokenVersion`,`kid`。不得放手机号、证件号、密码、详细权限或预约信息。
- access token 建议 15 分钟；允许时钟偏差最多 30 秒。校验算法白名单、kid、签名、issuer、audience、nbf/exp；禁止 `alg=none` 和算法混淆。
- Refresh token 为至少 256 bit 随机不透明串，建议 7 天；数据库仅存 hash。每次刷新轮换，同 family 旧 token 重放则撤销整个 family并提升风险事件。
- 禁用用户/角色变更增加 `tokenVersion`。高风险管理接口可在线/短缓存校验版本；普通 access token 最长在 15 分钟内自然失效。
- Service JWT 使用独立 key/audience，寿命 1~5 分钟，claim 含 `serviceName,scope,jti`，不能冒充用户 JWT。
- 浏览器优先 refresh token 放 Secure/HttpOnly/SameSite cookie，access token 仅内存；若部署限制必须持久化，需单独 ADR 与 XSS 风险接受。

auth 首版落地细节：

- auth 既签发也再次验证 access JWT；`/api/v1/admin/**` 除方法级 `hasRole('ADMIN')` 外，Service 还在线校验账号状态、数据库角色和 `tokenVersion`，避免已移除管理员权限的旧 token 在 15 分钟窗口内继续执行高影响操作。`/api/v1/user/me` 同样在线校验版本。
- RSA key 通过 `civicflow.auth.jwt.keys[]` 配置，每项包含 `kid`、PEM/DER Base64 的 X.509 公钥和可选 PKCS#8 私钥；`active-key-id` 必须指向唯一带私钥的项。生产/开发环境缺少 key 时启动失败，只有 test profile 显式允许进程内临时 key。
- 手机 AES-256-GCM key 和 HMAC-SHA-256 key 以 32-byte Base64 secret 注入；缺少时启动失败。密文使用随机 96 bit nonce、128 bit tag，并以 keyVersion 作为 AAD；手机号检索只比较 HMAC，响应仅返回 `前三位****后四位`。
- BCrypt strength 默认 12，可按部署压测调整；仍强制 BCrypt 的 72-byte 上限。错误密码、未知账号、禁用账号和无角色账号共用同一 401 code/message，并对未知账号执行一次 dummy BCrypt 校验，降低账号枚举与明显计时差异。
- 管理员写操作的幂等摘要只包含规范化载荷摘要；密码先经过带独立用途前缀的 HMAC 再参与摘要，不形成可离线枚举的裸 SHA-256。表中不保存明文密码/手机号；refresh token、access token 和私钥不进入审计记录或响应日志。

resource 首版落地细节：

- resource 服务不信任浏览器 `X-User-*` 头，使用 auth JWKS 在本地仅按 RS256 校验 access JWT 的签名、issuer、audience、nbf/exp，并把 `roles` 映射为方法级 RBAC；STAFF scope 查询的 staffUserId 只取已验证 JWT `sub`。
- 联系电话使用独立 256-bit AES-GCM key、随机 96-bit nonce、128-bit tag 与 keyVersion AAD；数据库不建联系电话检索哈希，API 只返回末四位。生产/开发缺少加密 key 或资源幂等 HMAC key 时启动失败，只有 test profile 可生成进程内临时 key。
- 所有 ADMIN 写操作强制幂等键、version CAS，并在同一本地事务写资源审计。幂等 payload 使用独立 256-bit HMAC key，审计 before/after 不含联系电话、密文、摘要、JWT 或完整请求体。

## 3. RBAC 与越权防护

| 入口 | 必须校验 |
| --- | --- |
| 用户预约/排队 | `sub == resource.userId`；查询他人资源返回 404；slot/outlet/item 关系有效 |
| 签到 | token userId 与 JWT sub 一致；appointment 属本人；outlet、date、窗口、status、nonce 全匹配 |
| STAFF 写操作 | STAFF 角色；resource 授权 scope；ACTIVE session 属本人和窗口；ticket 被该窗口领取 |
| ADMIN 写操作 | ADMIN + 具体 permission；乐观锁/幂等；高影响操作记录 before/after、actor、requestId |
| internal API | service JWT audience/scope；网络隔离；需要用户语境时同时传递/验证用户 token |

前端菜单守卫仅改善体验，不是安全控制。数据库查询必须把 userId/outletId/windowId 作为条件，不可“查出后只在前端过滤”。批量接口逐条校验 scope，并设数量上限。

## 4. 签到二维码

- 内容为短时 JWS compact token，不是图片 URL 中的裸 appointmentId。claims：`appointmentId,userId,outletId,nonce,iat,exp,purpose="CHECK_IN",kid`。
- 使用独立于 access JWT 的签名 key（首版 ES256/RS256 均可，计划采用 ES256），key 按 kid 轮换；token 建议有效 2 分钟且不得超过该预约签到窗口。
- 签发时生成至少 128 bit nonce，只在 appointment 保存 `HMAC(nonce)`、到期和 kid；重新签发使旧 nonce 失效。API/日志/埋点不记录完整 token。
- queue 不自行只验签后放行；通过受保护 claim API 让 appointment 校验当前 nonce 摘要、签名、purpose、归属、状态 CONFIRMED、serviceDate、outlet 和 check-in window，并以 CAS 先占定 CHECKED_IN，使取消与签到不会双赢。
- 签到本地事务以 `queue_ticket.appointment_id UNIQUE` 防并发重放。Redis `qr-used` 只做快速拦截；重复合法签到返回原 ticket，不生成第二张。不同用户/网点重放返回 404/422 并审计。
- token 放在二维码画面中时前端禁止第三方分析脚本读取；页面设严格 CSP、Referrer-Policy，避免截图长期缓存提示。

## 5. 限流与滥用防护

- 登录：IP + loginName 哈希维度，固定/滑动窗口，连续失败渐进退避；响应不区分账号不存在/密码错误。
- 抢号：网关 `userId+slotId` 和 IP 维度；Lua active key 是业务防重，不替代限流。限流响应 429 + Retry-After。
- 二维码签发/签到：userId+appointmentId/outlet 维度；nonce 错误次数告警。
- 管理批量创建、对账、修复：低频限流、幂等键、数量上限、审计；高风险修复可增加二次确认/MFA（首版部署策略）。
- Sentinel 降级必须返回 429/503，不缓存或伪造“成功”。

## 6. 数据保护

- TLS 覆盖客户端、服务间、MySQL、Redis、RabbitMQ、Nacos；本地 compose 可例外但不得复用生产凭据。
- BCrypt cost 按部署压测；手机号/证件号 AES-GCM 信封加密 + HMAC 等值索引；密钥/pepper 由 secret/KMS 注入并版本化。
- 响应默认手机号 `138****0000`、证件号仅末四位；STAFF 仅见办理所需最小字段，管理员也不默认见明文。
- 禁止提交 `.env`、私钥、密码、token；Nacos 不存生产明文 secret。配置样例只用占位符。
- 审计日志不记录请求完整 body；对 reason/detail 做字段白名单。日志访问最小权限并设保留期限。
- 备份加密且恢复演练；测试数据为合成数据。数据保留/删除期限需在上线前由合规确认。

## 7. Web 安全

- CORS 仅允许配置化明确 origin；credentials=true 时禁止 `*`。启用 CSP、frame-ancestors、nosniff、Referrer-Policy。
- 若 refresh token 使用 cookie，启用 CSRF token/双重提交保护；SameSite 不是唯一防线。所有 HTML 文本转义，禁止任意 HTML 渲染。
- Axios 并发 401 只触发一次 refresh；失败清理内存态并回登录。不要把 token 写日志、URL、localStorage（除非 ADR 接受）。
- 管理高影响操作要求版本号、幂等键、明确确认文案；防止点击劫持。

## 8. 密钥轮换与应急

- JWT/JWS key 先发布新公钥，再用新 kid 签发，保留旧公钥至最长 token TTL + 时钟偏差，最后撤销。
- 泄露时立即停止旧 key 签发、发布撤销、提升用户 tokenVersion/撤销 refresh family，审计受影响 jti；二维码 key 泄露则使当前 nonce 失效并重新签发。
- 密钥轮换、管理员角色变更、对账修复、DLQ 重放均写安全审计并告警。

首版 JWT 轮换操作顺序：先在 `keys[]` 加入新公钥/私钥但保持旧 `active-key-id`，确认 JWKS 消费方已取到新 `kid`；再切换 `active-key-id`；至少保留旧公钥 15 分 30 秒（access TTL + clock skew）后移除。历史 key 配置只保留公钥。refresh token 为不透明会话凭证，不依赖 RSA key，泄露处置需另行撤销 family/提升 `tokenVersion`。
