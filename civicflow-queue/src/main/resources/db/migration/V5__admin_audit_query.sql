CREATE INDEX idx_queue_audit_admin_time ON queue_operation_log (occurred_at, id);
