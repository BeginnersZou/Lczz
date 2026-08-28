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

CREATE TABLE work_order_progress (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  installer_user_id BIGINT NOT NULL,
  progress_type VARCHAR(32) DEFAULT 'PROGRESS' NOT NULL,
  completion_order_id BIGINT AS (CASE WHEN progress_type = 'COMPLETION' THEN order_id ELSE NULL END),
  description VARCHAR(2000) NOT NULL,
  submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  UNIQUE(completion_order_id)
);

CREATE TABLE work_order_review (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT UNIQUE NOT NULL,
  reviewer_user_id BIGINT NOT NULL,
  score INT,
  liked BOOLEAN DEFAULT FALSE NOT NULL,
  content VARCHAR(2000),
  labels_json VARCHAR(2000),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE material_request (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_no VARCHAR(32) UNIQUE NOT NULL,
  order_id BIGINT NOT NULL,
  installer_user_id BIGINT NOT NULL,
  request_status VARCHAR(32) DEFAULT 'PENDING' NOT NULL,
  remark VARCHAR(500),
  submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  completed_by BIGINT, completed_at TIMESTAMP,
  voided_by BIGINT, voided_at TIMESTAMP, void_reason VARCHAR(500),
  version INT DEFAULT 0 NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE material_request_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_code_snapshot VARCHAR(64) NOT NULL,
  product_name_snapshot VARCHAR(255) NOT NULL,
  model_spec_snapshot VARCHAR(255),
  unit_snapshot VARCHAR(32) NOT NULL,
  display_price_snapshot DECIMAL(10,2),
  requested_quantity DECIMAL(12,3) NOT NULL,
  prepared_quantity DECIMAL(12,3) DEFAULT 0 NOT NULL,
  item_status VARCHAR(32) DEFAULT 'PENDING' NOT NULL,
  version INT DEFAULT 0 NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  UNIQUE(request_id, product_id)
);
