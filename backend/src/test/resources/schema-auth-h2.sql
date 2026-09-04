DROP ALL OBJECTS;

CREATE TABLE sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) UNIQUE,
  password_hash VARCHAR(100),
  nickname VARCHAR(64),
  real_name VARCHAR(64),
  gender VARCHAR(16),
  phone VARCHAR(20) UNIQUE,
  avatar_file_id BIGINT,
  account_status VARCHAR(32) DEFAULT 'ENABLED' NOT NULL,
  audit_status VARCHAR(32) DEFAULT 'APPROVED' NOT NULL,
  audit_reason VARCHAR(500), audited_by BIGINT, audited_at TIMESTAMP,
  blacklist BOOLEAN DEFAULT FALSE NOT NULL,
  installer_status VARCHAR(32), installer_remark VARCHAR(500),
  last_login_at TIMESTAMP, last_login_ip VARCHAR(64),
  version INT DEFAULT 0 NOT NULL, deleted BOOLEAN DEFAULT FALSE NOT NULL, deleted_at TIMESTAMP,
  created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_by BIGINT, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE sys_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_code VARCHAR(32) UNIQUE NOT NULL, role_name VARCHAR(64) NOT NULL,
  enabled BOOLEAN DEFAULT TRUE NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE sys_user_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, created_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  UNIQUE(user_id, role_id)
);

CREATE TABLE operation_audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  operator_user_id BIGINT,
  operation_type VARCHAR(64) NOT NULL,
  business_type VARCHAR(64) NOT NULL,
  business_id VARCHAR(64),
  request_id VARCHAR(64),
  before_json CLOB,
  after_json CLOB,
  result_code VARCHAR(64),
  client_ip VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE sms_notification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_key VARCHAR(160) UNIQUE NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  business_type VARCHAR(64) NOT NULL,
  business_id VARCHAR(64) NOT NULL,
  recipient_phone VARCHAR(20) NOT NULL,
  template_code VARCHAR(128),
  template_params_json CLOB,
  notification_status VARCHAR(32) NOT NULL,
  attempt_count INT DEFAULT 0 NOT NULL,
  last_attempt_at TIMESTAMP,
  next_attempt_at TIMESTAMP,
  sent_at TIMESTAMP,
  provider_request_id VARCHAR(128),
  provider_biz_id VARCHAR(128),
  provider_code VARCHAR(64),
  last_error VARCHAR(1000),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE user_wechat_identity (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL, app_id VARCHAR(64) NOT NULL, open_id VARCHAR(128) NOT NULL,
  union_id VARCHAR(128), session_version INT DEFAULT 0 NOT NULL,
  last_login_at TIMESTAMP, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  UNIQUE(app_id, open_id)
);

CREATE TABLE wechat_login_challenge (
  code_hash VARCHAR(64) PRIMARY KEY,
  app_id VARCHAR(64) NOT NULL,
  open_id VARCHAR(128) NOT NULL,
  union_id VARCHAR(128),
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

INSERT INTO sys_role(role_code, role_name) VALUES
 ('ADMIN', '管理员'), ('CUSTOMER', '普通用户'), ('INSTALLER', '安装师傅'), ('DEALER', '经销商');
