CREATE TABLE sys_user (
    id BIGINT UNSIGNED NOT NULL,
    username VARCHAR(64) NOT NULL,
    mobile_cipher VARBINARY(512) NULL,
    mobile_hash BINARY(32) NULL,
    mobile_key_version SMALLINT UNSIGNED NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    token_version INT UNSIGNED NOT NULL DEFAULT 0,
    last_login_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username, deleted),
    UNIQUE KEY uk_user_mobile_hash (mobile_hash, deleted),
    KEY idx_user_status (status, deleted, id),
    CONSTRAINT ck_user_deleted CHECK (deleted = 0 OR deleted = id),
    CONSTRAINT ck_user_mobile_envelope CHECK (
        (mobile_cipher IS NULL AND mobile_hash IS NULL AND mobile_key_version IS NULL)
        OR (mobile_cipher IS NOT NULL AND mobile_hash IS NOT NULL AND mobile_key_version IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role (
    id BIGINT UNSIGNED NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code),
    KEY idx_role_status (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_role (
    id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id, deleted),
    KEY idx_user_role_role (role_id, deleted, user_id),
    CONSTRAINT ck_user_role_deleted CHECK (deleted = 0 OR deleted = id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refresh_token (
    id BIGINT UNSIGNED NOT NULL,
    token_hash BINARY(32) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    family_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    replaced_by_hash BINARY(32) NULL,
    client_fingerprint_hash BINARY(32) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_hash (token_hash),
    KEY idx_refresh_user (user_id, status, expires_at),
    KEY idx_refresh_family (family_id, issued_at),
    KEY idx_refresh_expiry (status, expires_at, id),
    CONSTRAINT ck_refresh_expiry CHECK (expires_at > issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_role (id, role_code, role_name, status)
VALUES
    (1, 'USER', '用户', 'ENABLED'),
    (2, 'STAFF', '窗口人员', 'ENABLED'),
    (3, 'ADMIN', '管理员', 'ENABLED');
