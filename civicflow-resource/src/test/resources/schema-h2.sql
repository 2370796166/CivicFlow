DROP TABLE IF EXISTS resource_slot_outbox;
DROP TABLE IF EXISTS resource_slot_batch_result;
DROP TABLE IF EXISTS resource_slot_day_lock;
DROP TABLE IF EXISTS resource_admin_audit;
DROP TABLE IF EXISTS resource_admin_idempotency;
DROP TABLE IF EXISTS resource_slot;
DROP TABLE IF EXISTS staff_window_scope;
DROP TABLE IF EXISTS window_item_rel;
DROP TABLE IF EXISTS service_window;
DROP TABLE IF EXISTS service_item;
DROP TABLE IF EXISTS service_outlet;

CREATE TABLE service_outlet (
    id BIGINT NOT NULL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    address VARCHAR(256) NOT NULL,
    longitude DECIMAL(10,7),
    latitude DECIMAL(10,7),
    contact_phone_cipher VARBINARY(512),
    contact_phone_key_version SMALLINT,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_outlet_code UNIQUE (code, deleted)
);

CREATE TABLE service_item (
    id BIGINT NOT NULL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1000),
    default_duration_minutes SMALLINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_item_code UNIQUE (code, deleted)
);

CREATE TABLE service_window (
    id BIGINT NOT NULL PRIMARY KEY,
    outlet_id BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_window_outlet_code UNIQUE (outlet_id, code, deleted)
);

CREATE TABLE window_item_rel (
    id BIGINT NOT NULL PRIMARY KEY,
    window_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_window_item UNIQUE (window_id, item_id, deleted)
);

CREATE TABLE staff_window_scope (
    id BIGINT NOT NULL PRIMARY KEY,
    staff_user_id BIGINT NOT NULL,
    outlet_id BIGINT NOT NULL,
    window_id BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_staff_window_scope UNIQUE (staff_user_id, outlet_id, window_id, deleted)
);

CREATE TABLE resource_slot (
    id BIGINT NOT NULL PRIMARY KEY,
    outlet_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    service_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    total_quota INT NOT NULL,
    release_at TIMESTAMP(3) NOT NULL,
    check_in_start TIMESTAMP(3) NOT NULL,
    check_in_end TIMESTAMP(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    config_version BIGINT NOT NULL DEFAULT 1,
    consumed_hint INT,
    version INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_slot_exact UNIQUE (outlet_id, item_id, service_date, start_time, end_time, deleted)
);

CREATE TABLE resource_admin_idempotency (
    id BIGINT NOT NULL PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    resource_id BIGINT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_resource_admin_idempotency UNIQUE (actor_user_id, operation, idempotency_key)
);

CREATE TABLE resource_admin_audit (
    id BIGINT NOT NULL PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    before_json JSON,
    after_json JSON,
    occurred_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE resource_slot_day_lock (
    id BIGINT NOT NULL PRIMARY KEY,
    outlet_id BIGINT NOT NULL,
    item_id BIGINT NOT NULL,
    service_date DATE NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_slot_day_lock UNIQUE (outlet_id, item_id, service_date)
);

CREATE TABLE resource_slot_batch_result (
    id BIGINT NOT NULL PRIMARY KEY,
    result_json JSON NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE resource_slot_outbox (
    id BIGINT NOT NULL PRIMARY KEY,
    event_id CHAR(36) NOT NULL,
    slot_id BIGINT NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version INT NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(3) NOT NULL,
    published_at TIMESTAMP(3),
    last_error_code VARCHAR(128),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_resource_slot_outbox_event UNIQUE (event_id)
);
