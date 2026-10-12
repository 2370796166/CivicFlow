CREATE TABLE user_message (
    id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    appointment_id BIGINT UNSIGNED NOT NULL,
    source_key VARCHAR(96) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512) NOT NULL,
    read_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_message_source (source_key),
    KEY idx_message_user (user_id, created_at, id),
    KEY idx_message_unread (user_id, read_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE appointment_active_release (
    appointment_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    KEY idx_active_release_due (status, appointment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_appointment_noshow ON appointment_order(status,service_date,slot_end_time,id);
