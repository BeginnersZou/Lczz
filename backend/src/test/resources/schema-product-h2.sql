CREATE TABLE product_category (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_code VARCHAR(64) UNIQUE NOT NULL,
  category_name VARCHAR(128) NOT NULL,
  parent_id BIGINT,
  category_level TINYINT DEFAULT 1 NOT NULL,
  sort_order INT DEFAULT 0 NOT NULL,
  enabled BOOLEAN DEFAULT TRUE NOT NULL,
  deleted BOOLEAN DEFAULT FALSE NOT NULL,
  created_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE file_asset (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  access_url VARCHAR(1000),
  deleted BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE product (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_code VARCHAR(64) UNIQUE NOT NULL,
  product_name VARCHAR(255) NOT NULL,
  category_id BIGINT NOT NULL,
  model_spec VARCHAR(255),
  unit VARCHAR(32) NOT NULL,
  display_price DECIMAL(10,2),
  display_stock DECIMAL(12,3),
  description CLOB,
  cover_file_id BIGINT,
  enabled BOOLEAN DEFAULT TRUE NOT NULL,
  sort_order INT DEFAULT 0 NOT NULL,
  version INT DEFAULT 0 NOT NULL,
  deleted BOOLEAN DEFAULT FALSE NOT NULL,
  deleted_at TIMESTAMP,
  created_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE business_file_relation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  business_type VARCHAR(32) NOT NULL,
  business_id BIGINT NOT NULL,
  usage_type VARCHAR(32) NOT NULL,
  file_id BIGINT NOT NULL,
  sort_order INT DEFAULT 0 NOT NULL,
  created_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  UNIQUE(business_type, business_id, usage_type, file_id)
);
