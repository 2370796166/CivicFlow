CREATE TABLE queue_ticket (
    id BIGINT UNSIGNED NOT NULL,
    appointment_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    outlet_id BIGINT UNSIGNED NOT NULL,
    item_id BIGINT UNSIGNED NOT NULL,
    service_date DATE NOT NULL,
    ticket_no VARCHAR(16) NOT NULL,
    priority SMALLINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    checked_in_at DATETIME(3) NOT NULL,
    called_at DATETIME(3) NULL,
    serving_at DATETIME(3) NULL,
    missed_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    called_window_id BIGINT UNSIGNED NULL,
    work_session_id BIGINT UNSIGNED NULL,
    call_count INT UNSIGNED NOT NULL DEFAULT 0,
    result_code VARCHAR(64) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_appointment (appointment_id),
    UNIQUE KEY uk_ticket_display (outlet_id, service_date, ticket_no),
    KEY idx_ticket_next (outlet_id, item_id, service_date, status, priority DESC, checked_in_at, id),
    KEY idx_ticket_user (user_id, service_date, status, id),
    KEY idx_ticket_window (called_window_id, status, called_at, id),
    KEY idx_ticket_outlet_date (outlet_id, service_date, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE queue_operation_log (
    id BIGINT UNSIGNED NOT NULL,
    ticket_id BIGINT UNSIGNED NOT NULL,
    appointment_id BIGINT UNSIGNED NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id BIGINT UNSIGNED NULL,
    operation VARCHAR(64) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    detail_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_queue_log (ticket_id, occurred_at, id),
    KEY idx_queue_log_request (request_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE window_work_session (
    id BIGINT UNSIGNED NOT NULL,
    window_id BIGINT UNSIGNED NOT NULL,
    outlet_id BIGINT UNSIGNED NOT NULL,
    staff_user_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    ended_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_session_staff (staff_user_id, status, started_at, id),
    KEY idx_session_window (window_id, status, started_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE active_window_session_guard (
    window_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    staff_user_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (window_id),
    UNIQUE KEY uk_active_session (session_id),
    KEY idx_active_session_staff (staff_user_id, window_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE message_consume_record (
    id BIGINT UNSIGNED NOT NULL,
    consumer_name VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    event_id CHAR(36) NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    first_seen_at DATETIME(3) NOT NULL,
    processed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_consumer_idempotency (consumer_name, idempotency_key),
    UNIQUE KEY uk_message_consumer_event (consumer_name, event_id),
    KEY idx_message_processing (status, first_seen_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE outbox_event (
    id BIGINT UNSIGNED NOT NULL,
    event_id CHAR(36) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INT UNSIGNED NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL,
    published_at DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event (event_id),
    KEY idx_outbox_publish (status, next_attempt_at, id),
    KEY idx_outbox_aggregate (aggregate_type, aggregate_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE checkin_reconciliation_record (
    id BIGINT UNSIGNED NOT NULL,
    appointment_id BIGINT UNSIGNED NOT NULL,
    ticket_id BIGINT UNSIGNED NULL,
    claim_id CHAR(36) NOT NULL,
    appointment_status_snapshot VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL,
    last_error_code VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_checkin_reconciliation_appointment (appointment_id),
    UNIQUE KEY uk_checkin_reconciliation_claim (claim_id),
    UNIQUE KEY uk_checkin_reconciliation_ticket (ticket_id),
    KEY idx_checkin_reconciliation_retry (status, next_attempt_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
