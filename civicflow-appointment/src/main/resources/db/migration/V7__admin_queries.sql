ALTER TABLE stock_reconciliation_run
    ADD COLUMN slot_id BIGINT UNSIGNED NULL,
    ADD COLUMN failure_code VARCHAR(64) NULL;
UPDATE stock_reconciliation_run
SET slot_id = CAST(JSON_UNQUOTE(JSON_EXTRACT(slot_scope_json, '$.slotId')) AS UNSIGNED)
WHERE JSON_EXTRACT(slot_scope_json, '$.slotId') IS NOT NULL;
CREATE INDEX idx_reconciliation_admin_slot ON stock_reconciliation_run (slot_id, started_at, id);
CREATE INDEX idx_reconciliation_admin_time ON stock_reconciliation_run (started_at, id);
CREATE INDEX idx_appointment_admin_date ON appointment_order (service_date, slot_start_time, id);
CREATE INDEX idx_appointment_log_time ON appointment_operation_log (occurred_at, id);
CREATE INDEX idx_stock_audit_time ON stock_admin_audit (occurred_at, id);
