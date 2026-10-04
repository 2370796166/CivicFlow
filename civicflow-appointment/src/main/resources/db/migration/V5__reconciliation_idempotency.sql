ALTER TABLE stock_reconciliation_run
    ADD COLUMN idempotency_key_hash BINARY(32) NULL,
    ADD COLUMN request_hash BINARY(32) NULL,
    ADD COLUMN report_json JSON NULL,
    ADD UNIQUE KEY uk_reconciliation_actor_key (actor_id, idempotency_key_hash);

ALTER TABLE appointment_reservation_request
    ADD KEY idx_reservation_request_slot (slot_id, reserved_at, id),
    ADD KEY idx_reservation_request_slot_updated (slot_id, updated_at, id);
