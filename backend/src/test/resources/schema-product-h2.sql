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
  storage_type VARCHAR(32),
  object_key VARCHAR(512),
  original_name VARCHAR(255),
  mime_type VARCHAR(128),
  file_size BIGINT,
  sha256 VARCHAR(64),
  access_url VARCHAR(1000),
  uploaded_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  deleted BOOLEAN DEFAULT FALSE NOT NULL
  ,deleted_at TIMESTAMP
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

CREATE TABLE product_spec_dimension (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  dimension_name VARCHAR(64) NOT NULL,
  sort_order INT DEFAULT 0 NOT NULL,
  deleted BOOLEAN DEFAULT FALSE NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  UNIQUE(product_id, dimension_name, deleted)
);

CREATE TABLE product_spec_value (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  dimension_id BIGINT NOT NULL,
  spec_value VARCHAR(128) NOT NULL,
  sort_order INT DEFAULT 0 NOT NULL,
  deleted BOOLEAN DEFAULT FALSE NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  UNIQUE(dimension_id, spec_value, deleted)
);

CREATE TABLE product_sku (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  sku_code VARCHAR(200) NOT NULL,
  spec_signature VARCHAR(2000) DEFAULT '' NOT NULL,
  spec_signature_hash VARCHAR(64) NOT NULL,
  spec_label VARCHAR(2000) DEFAULT '' NOT NULL,
  unit VARCHAR(32) NOT NULL,
  stock DECIMAL(12,3) DEFAULT 0 NOT NULL,
  enabled BOOLEAN DEFAULT TRUE NOT NULL,
  default_sku BOOLEAN DEFAULT FALSE NOT NULL,
  sort_order INT DEFAULT 0 NOT NULL,
  version INT DEFAULT 0 NOT NULL,
  deleted BOOLEAN DEFAULT FALSE NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  UNIQUE(product_id, sku_code, deleted),
  UNIQUE(product_id, spec_signature_hash, deleted)
);

CREATE TABLE product_sku_spec_value (
  sku_id BIGINT NOT NULL,
  dimension_id BIGINT NOT NULL,
  spec_value_id BIGINT NOT NULL,
  PRIMARY KEY(sku_id, dimension_id),
  UNIQUE(sku_id, spec_value_id)
);

CREATE TABLE installer_cart_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  installer_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  UNIQUE(installer_id, sku_id),
  CHECK(quantity > 0)
);

CREATE TABLE material_self_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(64) UNIQUE NOT NULL,
  order_name VARCHAR(64) DEFAULT '客户下单' NOT NULL,
  installer_id BIGINT NOT NULL,
  request_token VARCHAR(64) NOT NULL,
  order_status VARCHAR(32) DEFAULT 'ORDERED' NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  UNIQUE(installer_id, request_token)
);

CREATE TABLE material_self_order_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  self_order_id BIGINT NOT NULL,
  sku_id BIGINT,
  product_id BIGINT,
  product_name_snapshot VARCHAR(255) NOT NULL,
  sku_code_snapshot VARCHAR(200) NOT NULL,
  spec_snapshot VARCHAR(2000) DEFAULT '' NOT NULL,
  unit_snapshot VARCHAR(32) NOT NULL,
  quantity INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CHECK(quantity > 0)
);
