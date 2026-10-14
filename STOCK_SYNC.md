# 号源库存同步与恢复

资源服务在配置事务中已有 `resource_slot_outbox`。发布器按同一号源的 outbox 顺序发送 `resource.slot.changed.v1` 到 `cf.resource.x`；使用 publisher confirm、mandatory return 和 attempts CAS，确认未知时复用同一 eventId 重试，迟到确认不能覆盖新租约。

预约服务消费 `cf.appointment.slot-config.q`，通过受保护 resource snapshot 接口读取当前完整配置。因此旧 outbox 和重复、乱序通知可沿用相同信封，不改写已发布数据库迁移。暂时依赖故障经 `cf.resource.retry.x` → `cf.appointment.slot-config.retry.q` 延迟 5 秒后重试；非法事件或库存证据冲突，确认写入 `cf.appointment.slot-config.dlq` 后才确认原消息。

`sync_stock_configuration.lua` 使用既有 stock/pending key，原子处理完整配置：低版本忽略，同版本同配置幂等，高版本按 `newTotal-oldTotal` 调整 remaining，保留已扣额度，递增 mutationSeq。允许完整权威快照跨版本收敛；原 `init_stock.lua` / `adjust_stock.lua` 的连续版本规则保持不变。已有预占、补偿和 active 用户 key 不被清除。

预约入口在插入首条请求前同步当前配置，避免预约请求抢先于 MQ 到达。放号后 Redis stock 缺失时，只有 appointment 的请求、订单、释放记录全部没有历史，并且 Lua 重新确认 stock 不存在、pending ZSET 为空，才允许首次补建。任何预约历史存在都拒绝重建满额度，返回 `STOCK_REBUILD_UNSAFE`；存在 Redis 预占证据也拒绝补建。Redis 暂时不可用时继续失败关闭，恢复后延迟消息重试。

**已有消费的库存丢失不能自动补回总配额。** 运维应保留数据库/Redis 证据，暂停号源并按对账流程核查；DLQ 不是可以无条件重放并补库存的授权。新建且未曾预约的号源错过预热，与使用过的号源丢失库存是不同的恢复条件。

resource 现在需要 RabbitMQ 配置；本地与容器使用既有 `RABBITMQ_HOST/PORT/USERNAME/PASSWORD/VHOST`，生产 profile 使用 TLS。所有新参数从已有部署配置读取，不提交本机凭据。无需新增或改写 Flyway SQL。

前端迁移了会话 generation 保护：迟到 login/refresh/logout 和旧账号的 HTTP 响应不能覆盖新会话或恢复退出状态。令牌仍只保存在内存；未改变小程序接口或通知逻辑。

验证包括真实 Redis 配置/扣减/乱序/缺失恢复测试、资源 outbox 认领与租约 fencing、消息确认/延迟重试/死信、Feign ISO 日期的实际 HTTP 请求、前端会话与拦截器竞态，以及完整 Maven/Web/小程序和全栈业务回归。执行结果见提交说明。

压力测试保持原 1000 VU、同用户与同幂等键场景及所有业务断言；容器到测试 JVM 的连接采用 [Testcontainers 官方宿主机端口转发](https://java.testcontainers.org/features/networking/#exposing-host-ports-to-the-container)，避免依赖 Docker Desktop 的宿主机 NAT 路径。首次执行需要本机缓存或拉取 testcontainers/sshd:1.3.0 镜像。
