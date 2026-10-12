# CivicFlow 监控接入

五个服务提供 Prometheus 格式指标。业务 API 不开放管理指标；指标端点要求有效 ADMIN JWT，Nginx 仍禁止公开访问 /actuator/。在与应用服务相同的私有网络中部署 Prometheus，将 prometheus.yml 和 alerts.yml 只读挂载到 /etc/prometheus/。

监控令牌通过 secret 挂载到 /run/secrets/civicflow-monitoring-token，采集器必须使用受控身份按 access-token TTL 轮换该文件。不要把真实 JWT 写进配置、镜像或 Git，也不要延长令牌有效期绕过轮换。模板不包含令牌，不会自动启动监控容器。

主要指标：

- civicflow_reservation_requests_total：按 accepted、business_rejection、dependency_unavailable、error 分类的入口结果；accepted 是异步受理。
- civicflow_reservation_request_seconds：受理耗时。
- civicflow_reservation_stage_seconds：资源调用和消息发布耗时。
- civicflow_reservation_pending：预占和补偿状态积压。
- civicflow_reservation_oldest_seconds：最老未决请求年龄。
- civicflow_recovery_pending：Outbox、库存补偿、有效占位清理积压。
- civicflow_queue_sync_pending：PENDING、PROCESSING、NEEDS_ATTENTION 状态数量。
- civicflow_metrics_refreshed_timestamp：业务指标最近成功刷新时间。

业务积压默认每 30 秒刷新；用 alerts.yml 对长期未决、采集停止、需要人工处理的同步任务告警。对指标本身的不可达同时配置 Prometheus up 告警。

受理延迟和最终建单延迟分别验收。生产集群的共享限额、数据库容量、MQ 积压及真实进程故障恢复仍需在目标环境压测。
