CREATE TABLE service_outlet (
    id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    address VARCHAR(256) NOT NULL,
    longitude DECIMAL(10, 7) NULL,
    latitude DECIMAL(10, 7) NULL,
    contact_phone_cipher VARBINARY(512) NULL,
    contact_phone_key_version SMALLINT UNSIGNED NULL,
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outlet_code (code, deleted),
    KEY idx_outlet_status (status, deleted, id),
    CONSTRAINT ck_outlet_deleted CHECK (deleted = 0 OR deleted = id),
    CONSTRAINT ck_outlet_contact_envelope CHECK (
        (contact_phone_cipher IS NULL AND contact_phone_key_version IS NULL)
        OR (contact_phone_cipher IS NOT NULL AND contact_phone_key_version IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_item (
    id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(1000) NULL,
    default_duration_minutes SMALLINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_code (code, deleted),
    KEY idx_item_status (status, deleted, id),
    CONSTRAINT ck_item_duration CHECK (default_duration_minutes > 0),
    CONSTRAINT ck_item_deleted CHECK (deleted = 0 OR deleted = id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_window (
    id BIGINT UNSIGNED NOT NULL,
    outlet_id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_window_outlet_code (outlet_id, code, deleted),
    KEY idx_window_outlet_status (outlet_id, status, deleted, id),
    CONSTRAINT ck_window_deleted CHECK (deleted = 0 OR deleted = id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE window_item_rel (
    id BIGINT UNSIGNED NOT NULL,
    window_id BIGINT UNSIGNED NOT NULL,
    item_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_window_item (window_id, item_id, deleted),
    KEY idx_window_item_item (item_id, deleted, window_id),
    CONSTRAINT ck_window_item_deleted CHECK (deleted = 0 OR deleted = id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE staff_window_scope (
    id BIGINT UNSIGNED NOT NULL,
    staff_user_id BIGINT UNSIGNED NOT NULL,
    outlet_id BIGINT UNSIGNED NOT NULL,
    window_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0 means all windows in the outlet',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_staff_window_scope (staff_user_id, outlet_id, window_id, deleted),
    KEY idx_scope_outlet_window (outlet_id, window_id, deleted, staff_user_id),
    CONSTRAINT ck_staff_scope_deleted CHECK (deleted = 0 OR deleted = id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resource_slot (
    id BIGINT UNSIGNED NOT NULL,
    outlet_id BIGINT UNSIGNED NOT NULL,
    item_id BIGINT UNSIGNED NOT NULL,
    service_date DATE NOT NULL,
    start_time TIME(0) NOT NULL,
    end_time TIME(0) NOT NULL,
    total_quota INT UNSIGNED NOT NULL,
    release_at DATETIME(3) NOT NULL,
    check_in_start DATETIME(3) NOT NULL,
    check_in_end DATETIME(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    config_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    consumed_hint INT UNSIGNED NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NOT NULL,
    updated_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_slot_exact (outlet_id, item_id, service_date, start_time, end_time, deleted),
    KEY idx_slot_calendar (outlet_id, item_id, service_date, status, deleted, start_time),
    KEY idx_slot_release (status, release_at, id),
    CONSTRAINT ck_slot_window CHECK (end_time > start_time),
    CONSTRAINT ck_slot_checkin_window CHECK (check_in_end > check_in_start),
    CONSTRAINT ck_slot_consumed_hint CHECK (consumed_hint IS NULL OR consumed_hint <= total_quota),
    CONSTRAINT ck_slot_deleted CHECK (deleted = 0 OR deleted = id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
