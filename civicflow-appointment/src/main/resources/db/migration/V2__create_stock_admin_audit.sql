CREATE TABLE stock_admin_audit (
    id BIGINT UNSIGNED NOT NULL,
    actor_user_id BIGINT UNSIGNED NOT NULL,
    slot_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    detail_json JSON NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_stock_admin_audit_slot (slot_id, occurred_at, id),
    KEY idx_stock_admin_audit_actor (actor_user_id, occurred_at, id),
    KEY idx_stock_admin_audit_request (request_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
