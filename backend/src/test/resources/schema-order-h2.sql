CREATE TABLE work_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(32) UNIQUE NOT NULL,
  task_type VARCHAR(64) NOT NULL,
  order_status VARCHAR(32) DEFAULT 'PENDING_VISIT' NOT NULL,
  description VARCHAR(1000),
  customer_user_id BIGINT,
  customer_name VARCHAR(64) NOT NULL,
  customer_phone VARCHAR(20) NOT NULL,
  installer_user_id BIGINT NOT NULL,
  province_code VARCHAR(64), province_name VARCHAR(64),
  city_code VARCHAR(64), city_name VARCHAR(64),
  district_code VARCHAR(64), district_name VARCHAR(64),
  detailed_address VARCHAR(500) NOT NULL,
  required_start_at TIMESTAMP,
  expected_end_at TIMESTAMP,
  admin_remark VARCHAR(1000),
  cancelled_by BIGINT, cancelled_at TIMESTAMP, cancel_reason VARCHAR(500),
  version INT DEFAULT 0 NOT NULL,
  deleted BOOLEAN DEFAULT FALSE NOT NULL, deleted_at TIMESTAMP,
  created_by BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE work_order_assignment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  installer_user_id BIGINT NOT NULL,
  assigned_by BIGINT NOT NULL,
  assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  unassigned_at TIMESTAMP,
  is_active BOOLEAN DEFAULT TRUE NOT NULL,
  change_reason VARCHAR(500)
);

CREATE TABLE work_order_status_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32) NOT NULL,
  change_reason VARCHAR(500),
  operator_user_id BIGINT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);
