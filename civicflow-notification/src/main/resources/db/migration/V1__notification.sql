CREATE TABLE notification_template (
  id BIGINT NOT NULL PRIMARY KEY,
  template_code VARCHAR(64) NOT NULL,
  template_version INT NOT NULL,
  title_pattern VARCHAR(128) NOT NULL,
  body_pattern VARCHAR(512) NOT NULL,
  allowed_variables VARCHAR(512) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_template_version (template_code, template_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_preference (
  user_id BIGINT NOT NULL,
  channel VARCHAR(16) NOT NULL,
  optional_enabled TINYINT(1) NOT NULL DEFAULT 1,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (user_id, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_record (
  id BIGINT NOT NULL PRIMARY KEY,
  event_id VARCHAR(36) NOT NULL,
  channel VARCHAR(16) NOT NULL,
  event_type VARCHAR(96) NOT NULL,
  template_code VARCHAR(64) NOT NULL,
  template_version INT NOT NULL,
  user_id BIGINT NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  required_notice TINYINT(1) NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  event_json JSON NOT NULL,
  title VARCHAR(128) NOT NULL,
  body VARCHAR(512) NOT NULL,
  status VARCHAR(16) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  last_error_code VARCHAR(64) NULL,
  read_at DATETIME(3) NULL,
  sent_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  UNIQUE KEY uk_notification_event_channel (event_id, channel),
  KEY idx_notification_user (user_id, created_at, id),
  KEY idx_notification_failure (status, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notification_replay_audit (
  id BIGINT NOT NULL PRIMARY KEY,
  record_id BIGINT NOT NULL,
  actor_id BIGINT NOT NULL,
  reason VARCHAR(256) NOT NULL,
  requested_at DATETIME(3) NOT NULL,
  KEY idx_replay_record (record_id, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO notification_template VALUES
(1,'APPOINTMENT_CREATED',1,'预约待确认','预约 {appointmentId} 已创建，请在截止时间前确认。','appointmentId',UTC_TIMESTAMP(3)),
(2,'CONFIRM_REMINDER',1,'请确认预约','预约 {appointmentId} 即将超过确认期限。','appointmentId',UTC_TIMESTAMP(3)),
(3,'APPOINTMENT_CONFIRMED',1,'预约已确认','预约 {appointmentId} 已确认。','appointmentId',UTC_TIMESTAMP(3)),
(4,'APPOINTMENT_CANCELLED',1,'预约已取消','预约 {appointmentId} 已取消。','appointmentId',UTC_TIMESTAMP(3)),
(5,'QUEUE_CALLED',1,'请前往窗口','排队号 {ticketNo} 已叫号，请前往窗口。','ticketNo',UTC_TIMESTAMP(3)),
(6,'QUEUE_MISSED',1,'已过号','排队号 {ticketNo} 已过号。','ticketNo',UTC_TIMESTAMP(3));
INSERT INTO notification_template VALUES
(7,'QUEUE_NEAR',1,'即将到号','排队号 {ticketNo} 即将到号，请留意叫号。','ticketNo',UTC_TIMESTAMP(3));
