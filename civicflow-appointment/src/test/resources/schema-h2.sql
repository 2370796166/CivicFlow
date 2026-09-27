DROP TABLE IF EXISTS stock_release_record;
DROP TABLE IF EXISTS stock_reconciliation_detail;
DROP TABLE IF EXISTS stock_reconciliation_run;
DROP TABLE IF EXISTS stock_admin_audit;
DROP TABLE IF EXISTS outbox_event;
DROP TABLE IF EXISTS appointment_command_idempotency;
DROP TABLE IF EXISTS message_consume_record;
DROP TABLE IF EXISTS appointment_operation_log;
DROP TABLE IF EXISTS active_booking_guard;
DROP TABLE IF EXISTS appointment_order;
DROP TABLE IF EXISTS appointment_reservation_request;

CREATE TABLE appointment_reservation_request (
    id BIGINT NOT NULL PRIMARY KEY,
    reservation_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    idempotency_key_hash BINARY(32) NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    slot_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    service_date DATE NOT NULL,
    slot_start_time TIME NOT NULL,
    slot_end_time TIME NOT NULL,
    outlet_name_snapshot VARCHAR(128) NOT NULL,
    item_name_snapshot VARCHAR(128) NOT NULL,
    total_quota INT NOT NULL,
    release_at TIMESTAMP(3) NOT NULL,
    close_at TIMESTAMP(3) NOT NULL,
    slot_status VARCHAR(32) NOT NULL,
    slot_config_version BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    failure_code VARCHAR(64),
    event_json CLOB,
    trace_id VARCHAR(64) NOT NULL,
    reserved_at TIMESTAMP(3),
    reservation_expires_at TIMESTAMP(3),
    publish_attempts INT NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64),
    next_recovery_at TIMESTAMP(3) NOT NULL,
    recovery_owner VARCHAR(64),
    recovery_lease_until TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_reservation_request_id UNIQUE (reservation_id),
    CONSTRAINT uk_reservation_request_idempotency UNIQUE (user_id, idempotency_key_hash)
);

CREATE TABLE appointment_command_idempotency (
    id BIGINT NOT NULL PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    appointment_id BIGINT NOT NULL,
    operation VARCHAR(32) NOT NULL,
    idempotency_key_hash BINARY(32) NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_appt_command_key UNIQUE (actor_user_id,operation,idempotency_key_hash)
);

CREATE TABLE outbox_event (
    id BIGINT NOT NULL PRIMARY KEY,
    event_id CHAR(36) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INT NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(3) NOT NULL,
    published_at TIMESTAMP(3),
    last_error_code VARCHAR(64),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_outbox_event UNIQUE (event_id)
);

CREATE TABLE appointment_order (
    id BIGINT NOT NULL PRIMARY KEY,
    reservation_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    service_date DATE NOT NULL,
    slot_start_time TIME NOT NULL,
    slot_end_time TIME NOT NULL,
    outlet_name_snapshot VARCHAR(128) NOT NULL,
    item_name_snapshot VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    confirm_deadline TIMESTAMP(3) NOT NULL,
    confirmed_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    expired_at TIMESTAMP(3),
    checked_in_at TIMESTAMP(3),
    serving_at TIMESTAMP(3),
    completed_at TIMESTAMP(3),
    no_show_at TIMESTAMP(3),
    cancel_reason VARCHAR(256),
    qr_nonce_hash BINARY(32),
    qr_expires_at TIMESTAMP(3),
    qr_key_id VARCHAR(64),
    checkin_claim_id CHAR(36),
    queue_ticket_id BIGINT,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_appointment_reservation UNIQUE (reservation_id),
    CONSTRAINT uk_appointment_checkin_claim UNIQUE (checkin_claim_id),
    CONSTRAINT uk_appointment_queue_ticket UNIQUE (queue_ticket_id)
);

CREATE TABLE active_booking_guard (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    service_date DATE NOT NULL,
    reservation_id CHAR(36) NOT NULL,
    appointment_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_active_user_item_date UNIQUE (user_id, item_id, service_date),
    CONSTRAINT uk_active_reservation UNIQUE (reservation_id),
    CONSTRAINT uk_active_appointment UNIQUE (appointment_id)
);

CREATE TABLE appointment_operation_log (
    id BIGINT NOT NULL PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    reservation_id CHAR(36) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id BIGINT,
    operation VARCHAR(64) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP(3) NOT NULL,
    detail_json JSON,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE message_consume_record (
    id BIGINT NOT NULL PRIMARY KEY,
    consumer_name VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    event_id CHAR(36) NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    first_seen_at TIMESTAMP(3) NOT NULL,
    processed_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_message_consumer_idempotency UNIQUE (consumer_name, idempotency_key),
    CONSTRAINT uk_message_consumer_event UNIQUE (consumer_name, event_id)
);

CREATE TABLE stock_release_record (
    id BIGINT NOT NULL PRIMARY KEY,
    reservation_id CHAR(36) NOT NULL,
    slot_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reason VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(3) NOT NULL,
    released_at TIMESTAMP(3),
    last_error_code VARCHAR(64),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_release_reservation UNIQUE (reservation_id)
);

CREATE TABLE stock_reconciliation_run (
    id BIGINT NOT NULL PRIMARY KEY,
    trigger_type VARCHAR(32) NOT NULL,
    slot_scope_json JSON,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP(3) NOT NULL,
    completed_at TIMESTAMP(3),
    actor_id BIGINT,
    idempotency_key_hash BINARY(32),
    request_hash BINARY(32),
    report_json JSON,
    created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_reconciliation_actor_key UNIQUE(actor_id, idempotency_key_hash)
);

CREATE TABLE stock_reconciliation_detail (
    id BIGINT NOT NULL PRIMARY KEY,
    run_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    config_version BIGINT NOT NULL,
    configured_total INT NOT NULL,
    persisted_consumed INT NOT NULL,
    pending_reserved INT NOT NULL,
    expected_remaining INT NOT NULL,
    actual_remaining INT,
    diff INT,
    classification VARCHAR(64) NOT NULL,
    repair_status VARCHAR(32) NOT NULL,
    before_json JSON,
    after_json JSON,
    created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_reconciliation_run_slot UNIQUE(run_id, slot_id)
);

CREATE TABLE stock_admin_audit (
    id BIGINT NOT NULL PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    slot_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    outcome VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    detail_json JSON,
    occurred_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP NOT NULL
);
