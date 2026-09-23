CREATE TABLE appointment_command_idempotency (
    id BIGINT UNSIGNED NOT NULL,
    actor_user_id BIGINT UNSIGNED NOT NULL,
    appointment_id BIGINT UNSIGNED NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idempotency_key_hash BINARY(32) NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_appt_command_key (actor_user_id, operation, idempotency_key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
