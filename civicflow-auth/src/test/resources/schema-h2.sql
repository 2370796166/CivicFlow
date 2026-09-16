DROP TABLE IF EXISTS auth_admin_idempotency;
DROP TABLE IF EXISTS auth_security_audit;
DROP TABLE IF EXISTS refresh_token;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id BIGINT NOT NULL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    mobile_cipher VARBINARY(512),
    mobile_hash BINARY(32),
    mobile_key_version INT,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    token_version INT NOT NULL DEFAULT 0,
    last_login_at TIMESTAMP(3),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_username UNIQUE (username, deleted),
    CONSTRAINT uk_user_mobile_hash UNIQUE (mobile_hash, deleted)
);

CREATE TABLE sys_role (
    id BIGINT NOT NULL PRIMARY KEY,
    role_code VARCHAR(32) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_user_role (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id, deleted)
);

CREATE TABLE refresh_token (
    id BIGINT NOT NULL PRIMARY KEY,
    token_hash BINARY(32) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    family_id CHAR(36) NOT NULL,
    status VARCHAR(32) NOT NULL,
    issued_at TIMESTAMP(3) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    revoked_at TIMESTAMP(3),
    replaced_by_hash BINARY(32),
    client_fingerprint_hash BINARY(32),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE auth_admin_idempotency (
    id BIGINT NOT NULL PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    operation VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_hash BINARY(32) NOT NULL,
    resource_id BIGINT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_admin_idempotency UNIQUE (actor_user_id, operation, idempotency_key)
);

CREATE TABLE auth_security_audit (
    id BIGINT NOT NULL PRIMARY KEY,
    actor_user_id BIGINT,
    target_user_id BIGINT,
    action VARCHAR(64) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    before_json JSON,
    after_json JSON,
    occurred_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO sys_role (id, role_code, role_name, status)
VALUES (1, 'USER', 'User', 'ENABLED'),
       (2, 'STAFF', 'Staff', 'ENABLED'),
       (3, 'ADMIN', 'Admin', 'ENABLED');
