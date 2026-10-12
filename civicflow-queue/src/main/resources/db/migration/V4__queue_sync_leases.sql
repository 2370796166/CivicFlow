ALTER TABLE queue_state_sync ADD COLUMN lease_owner CHAR(36) NULL,
    ADD COLUMN lease_until DATETIME(3) NULL;
CREATE INDEX idx_queue_state_lease ON queue_state_sync(status,lease_until,id);

CREATE TABLE queue_sync_audit (
    id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    sync_id BIGINT UNSIGNED NOT NULL,
    actor_id BIGINT UNSIGNED NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    reason VARCHAR(256) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_sync_audit (sync_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
