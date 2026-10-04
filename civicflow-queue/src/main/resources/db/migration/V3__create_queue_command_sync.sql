CREATE TABLE queue_command_idempotency (
    id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    staff_user_id BIGINT UNSIGNED NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    result_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_queue_command (staff_user_id, operation, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE queue_operation_log MODIFY ticket_id BIGINT UNSIGNED NULL,
    MODIFY appointment_id BIGINT UNSIGNED NULL, MODIFY to_status VARCHAR(32) NULL;

CREATE TABLE queue_state_sync (
    id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    ticket_id BIGINT UNSIGNED NOT NULL,
    appointment_id BIGINT UNSIGNED NOT NULL,
    target_status VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL,
    last_error_code VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_queue_state_sync (ticket_id, target_status),
    KEY idx_queue_state_due (status, next_attempt_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
