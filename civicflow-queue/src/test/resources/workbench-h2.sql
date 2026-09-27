DROP TABLE IF EXISTS queue_state_sync;
CREATE ALIAS IF NOT EXISTS UTC_TIMESTAMP FOR "com.civicflow.queue.H2UtcClock.now";
DROP TABLE IF EXISTS queue_command_idempotency;
DROP TABLE IF EXISTS active_window_session_guard;
DROP TABLE IF EXISTS window_work_session;
DROP TABLE IF EXISTS queue_operation_log;
DROP TABLE IF EXISTS queue_ticket;
CREATE TABLE queue_ticket (id BIGINT PRIMARY KEY, appointment_id BIGINT NOT NULL UNIQUE, user_id BIGINT NOT NULL,
 outlet_id BIGINT NOT NULL, item_id BIGINT NOT NULL, service_date DATE NOT NULL, ticket_no VARCHAR(16) NOT NULL,
 priority SMALLINT NOT NULL, status VARCHAR(32) NOT NULL, checked_in_at TIMESTAMP(3) NOT NULL,
 called_at TIMESTAMP(3), serving_at TIMESTAMP(3), missed_at TIMESTAMP(3), completed_at TIMESTAMP(3),
 called_window_id BIGINT, work_session_id BIGINT, call_count INT NOT NULL DEFAULT 0, result_code VARCHAR(64),
 version INT NOT NULL DEFAULT 0, created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE window_work_session (id BIGINT PRIMARY KEY,window_id BIGINT NOT NULL,outlet_id BIGINT NOT NULL,staff_user_id BIGINT NOT NULL,
 status VARCHAR(32) NOT NULL,started_at TIMESTAMP(3) NOT NULL,ended_at TIMESTAMP(3),version INT NOT NULL,
 created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE active_window_session_guard (window_id BIGINT PRIMARY KEY,session_id BIGINT NOT NULL UNIQUE,staff_user_id BIGINT NOT NULL,
 created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE queue_operation_log (id BIGINT PRIMARY KEY,ticket_id BIGINT,appointment_id BIGINT,actor_type VARCHAR(32) NOT NULL,
 actor_id BIGINT,operation VARCHAR(64) NOT NULL,from_status VARCHAR(32),to_status VARCHAR(32),request_id VARCHAR(64) NOT NULL,
 occurred_at TIMESTAMP(3) NOT NULL,detail_json VARCHAR(1024),created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE queue_command_idempotency (id BIGINT PRIMARY KEY,staff_user_id BIGINT NOT NULL,operation VARCHAR(32) NOT NULL,
 idempotency_key VARCHAR(128) NOT NULL,payload_hash CHAR(64) NOT NULL,result_id BIGINT,
 created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,UNIQUE(staff_user_id,operation,idempotency_key));
CREATE TABLE queue_state_sync (id BIGINT PRIMARY KEY,ticket_id BIGINT NOT NULL,appointment_id BIGINT NOT NULL,target_status VARCHAR(32) NOT NULL,
 status VARCHAR(16) NOT NULL,attempts INT NOT NULL DEFAULT 0,next_attempt_at TIMESTAMP(3) NOT NULL,last_error_code VARCHAR(64),
 created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,UNIQUE(ticket_id,target_status));
