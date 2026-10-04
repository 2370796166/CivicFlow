DROP TABLE IF EXISTS checkin_reconciliation_record;
DROP TABLE IF EXISTS queue_operation_log;
DROP TABLE IF EXISTS queue_ticket;
DROP TABLE IF EXISTS queue_ticket_counter;
CREATE TABLE queue_ticket_counter (
  outlet_id BIGINT NOT NULL, service_date DATE NOT NULL, next_number INT NOT NULL,
  PRIMARY KEY(outlet_id,service_date));
CREATE TABLE queue_ticket (
  id BIGINT PRIMARY KEY, appointment_id BIGINT NOT NULL UNIQUE, user_id BIGINT NOT NULL,
  outlet_id BIGINT NOT NULL, item_id BIGINT NOT NULL, service_date DATE NOT NULL,
  ticket_no VARCHAR(16) NOT NULL, priority SMALLINT NOT NULL, status VARCHAR(32) NOT NULL,
  checked_in_at TIMESTAMP(3) NOT NULL, called_at TIMESTAMP(3), serving_at TIMESTAMP(3),
  missed_at TIMESTAMP(3), completed_at TIMESTAMP(3), called_window_id BIGINT,
  work_session_id BIGINT, call_count INT NOT NULL, result_code VARCHAR(64),
  version INT NOT NULL, created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  UNIQUE(outlet_id,service_date,ticket_no));
CREATE TABLE checkin_reconciliation_record (
  id BIGINT PRIMARY KEY, appointment_id BIGINT NOT NULL UNIQUE, ticket_id BIGINT UNIQUE,
  claim_id VARCHAR(36) NOT NULL UNIQUE, appointment_status_snapshot VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL, attempts INT NOT NULL, next_attempt_at TIMESTAMP(3) NOT NULL,
  last_error_code VARCHAR(64), created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE queue_operation_log (
  id BIGINT PRIMARY KEY, ticket_id BIGINT NOT NULL, appointment_id BIGINT NOT NULL,
  actor_type VARCHAR(32) NOT NULL, actor_id BIGINT, operation VARCHAR(64) NOT NULL,
  from_status VARCHAR(32), to_status VARCHAR(32) NOT NULL, request_id VARCHAR(64) NOT NULL,
  occurred_at TIMESTAMP(3) NOT NULL, detail_json VARCHAR(1024),
  created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP);
