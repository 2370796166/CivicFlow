CREATE TABLE appointment_order (
    id BIGINT UNSIGNED NOT NULL,
    reservation_id CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    slot_id BIGINT UNSIGNED NOT NULL,
    outlet_id BIGINT UNSIGNED NOT NULL,
    item_id BIGINT UNSIGNED NOT NULL,
    service_date DATE NOT NULL,
    slot_start_time TIME(0) NOT NULL,
    slot_end_time TIME(0) NOT NULL,
    outlet_name_snapshot VARCHAR(128) NOT NULL,
    item_name_snapshot VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    confirm_deadline DATETIME(3) NOT NULL,
    confirmed_at DATETIME(3) NULL,
    cancelled_at DATETIME(3) NULL,
    expired_at DATETIME(3) NULL,
    checked_in_at DATETIME(3) NULL,
    serving_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    no_show_at DATETIME(3) NULL,
    cancel_reason VARCHAR(256) NULL,
    qr_nonce_hash BINARY(32) NULL,
    qr_expires_at DATETIME(3) NULL,
    qr_key_id VARCHAR(64) NULL,
    checkin_claim_id CHAR(36) NULL,
    queue_ticket_id BIGINT UNSIGNED NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_appointment_reservation (reservation_id),
    UNIQUE KEY uk_appointment_checkin_claim (checkin_claim_id),
    UNIQUE KEY uk_appointment_queue_ticket (queue_ticket_id),
    KEY idx_appointment_user_date (user_id, service_date, status, id),
    KEY idx_appointment_slot_status (slot_id, status, id),
    KEY idx_appointment_timeout (status, confirm_deadline, id),
    KEY idx_appointment_outlet_date (outlet_id, service_date, status, id),
    CONSTRAINT ck_appointment_slot_window CHECK (slot_end_time > slot_start_time),
    CONSTRAINT ck_appointment_qr_envelope CHECK (
        (qr_nonce_hash IS NULL AND qr_expires_at IS NULL AND qr_key_id IS NULL)
        OR (qr_nonce_hash IS NOT NULL AND qr_expires_at IS NOT NULL AND qr_key_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE active_booking_guard (
    id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    item_id BIGINT UNSIGNED NOT NULL,
    service_date DATE NOT NULL,
    reservation_id CHAR(36) NOT NULL,
    appointment_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_active_user_item_date (user_id, item_id, service_date),
    UNIQUE KEY uk_active_reservation (reservation_id),
    UNIQUE KEY uk_active_appointment (appointment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE appointment_operation_log (
    id BIGINT UNSIGNED NOT NULL,
    appointment_id BIGINT UNSIGNED NOT NULL,
    reservation_id CHAR(36) NOT NULL,
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
    KEY idx_appointment_log (appointment_id, occurred_at, id),
    KEY idx_appointment_log_request (request_id, id)
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

CREATE TABLE stock_release_record (
    id BIGINT UNSIGNED NOT NULL,
    reservation_id CHAR(36) NOT NULL,
    slot_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    reason VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL,
    released_at DATETIME(3) NULL,
    last_error_code VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_release_reservation (reservation_id),
    KEY idx_stock_release_retry (status, next_attempt_at, id),
    KEY idx_stock_release_slot (slot_id, status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stock_reconciliation_run (
    id BIGINT UNSIGNED NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    slot_scope_json JSON NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    actor_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_reconciliation_run_status (status, started_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stock_reconciliation_detail (
    id BIGINT UNSIGNED NOT NULL,
    run_id BIGINT UNSIGNED NOT NULL,
    slot_id BIGINT UNSIGNED NOT NULL,
    config_version BIGINT UNSIGNED NOT NULL,
    configured_total INT UNSIGNED NOT NULL,
    persisted_consumed INT UNSIGNED NOT NULL,
    pending_reserved INT UNSIGNED NOT NULL,
    expected_remaining INT UNSIGNED NOT NULL,
    actual_remaining INT UNSIGNED NULL,
    diff INT NULL,
    classification VARCHAR(64) NOT NULL,
    repair_status VARCHAR(32) NOT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_reconciliation_run_slot (run_id, slot_id),
    KEY idx_reconciliation_slot (slot_id, created_at, id),
    KEY idx_reconciliation_repair (repair_status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
