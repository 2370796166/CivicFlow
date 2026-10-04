CREATE TABLE resource_admin_idempotency (
    id BIGINT UNSIGNED NOT NULL,
    actor_user_id BIGINT UNSIGNED NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    resource_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_resource_admin_idempotency (actor_user_id, operation, idempotency_key),
    KEY idx_resource_admin_idempotency_resource (operation, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resource_admin_audit (
    id BIGINT UNSIGNED NOT NULL,
    actor_user_id BIGINT UNSIGNED NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_resource_audit_actor (actor_user_id, occurred_at, id),
    KEY idx_resource_audit_target (resource_type, resource_id, occurred_at, id),
    KEY idx_resource_audit_request (request_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
