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

CREATE TABLE project_case (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  site_name VARCHAR(255) NOT NULL,
  deleted BOOLEAN DEFAULT FALSE NOT NULL,
  deleted_at TIMESTAMP,
  created_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
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

CREATE TABLE service_page_config (
  id BIGINT PRIMARY KEY,
  company_name VARCHAR(100) NOT NULL,
  company_subtitle VARCHAR(100) NOT NULL,
  slogan VARCHAR(200) NOT NULL,
  profile_text VARCHAR(2000) NOT NULL,
  phone_primary VARCHAR(32) NOT NULL,
  phone_secondary VARCHAR(32),
  address VARCHAR(500) NOT NULL,
  longitude DECIMAL(10,6) NOT NULL,
  latitude DECIMAL(9,6) NOT NULL,
  business_hours VARCHAR(255) NOT NULL,
  brand_visible BOOLEAN DEFAULT TRUE NOT NULL,
  services_visible BOOLEAN DEFAULT TRUE NOT NULL,
  profile_visible BOOLEAN DEFAULT TRUE NOT NULL,
  gallery_visible BOOLEAN DEFAULT TRUE NOT NULL,
  advantages_visible BOOLEAN DEFAULT TRUE NOT NULL,
  contact_visible BOOLEAN DEFAULT TRUE NOT NULL,
  created_by BIGINT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_by BIGINT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE service_page_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  service_page_id BIGINT NOT NULL,
  item_type VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  description VARCHAR(1000),
  sort_order INT DEFAULT 0 NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

INSERT INTO service_page_config (
  id, company_name, company_subtitle, slogan, profile_text, phone_primary, phone_secondary,
  address, longitude, latitude, business_hours
) VALUES (
  1, '武汉力创之尊', '制冷技术服务有限公司', '以诚信之心，立潮流之品',
  '武汉力创之尊机电设备有限公司（力创之尊）专注于制冷技术、水系统配件、二联供材料销售及水系统中央空调安装与售后服务。我们始终秉持“以诚信之心，立潮流之品”的理念，为家庭与商业客户提供清晰、可靠的暖通服务方案。',
  '027-82710326', '027-82710380', '湖北省武汉市江岸区不锈钢路S17-49-51号力创之尊',
  114.306997, 30.665673, '周一至周日 8:00-20:00'
);

INSERT INTO service_page_item (service_page_id, item_type, title, description, sort_order) VALUES
  (1, 'HERO_STAT', '一站式', '暖通服务', 0),
  (1, 'HERO_STAT', '全流程', '服务跟进', 1),
  (1, 'HERO_STAT', '双热线', '快速响应', 2),
  (1, 'SERVICE', '水系统中央空调配件材料销售', '提供各种高品质水系统中央空调配件及二联供材料，满足家庭和商业需求。品类齐全、价格优惠，品质可靠、送货快捷。', 0),
  (1, 'SERVICE', '水系统中央空调安装', '专业安装团队按规范完成勘察、施工与调试，并依据具体项目约定提供相应质保服务。', 1),
  (1, 'SERVICE', '水系统中央空调售后', '提供中央空调暖通系统故障排查、维修与保养服务，服务过程可沟通、可跟进。', 2),
  (1, 'PROFILE_TAG', '品牌授权', NULL, 0),
  (1, 'PROFILE_TAG', '持证上岗', NULL, 1),
  (1, 'PROFILE_TAG', '正品保证', NULL, 2),
  (1, 'PROFILE_TAG', '售后无忧', NULL, 3),
  (1, 'ADVANTAGE', '诚信为本', '我们始终坚持诚信经营，赢得了广大客户的信赖与支持。', 0),
  (1, 'ADVANTAGE', '专业服务', '专业的技术团队和售后服务团队，确保每一位客户都能享受到高质量的服务体验。', 1),
  (1, 'ADVANTAGE', '品质保障', '严格的质量控制体系，确保每一件产品都符合甚至超越客户的期望。', 2),
  (1, 'ADVANTAGE', '快速响应', '我们承诺快速响应客户的需求，无论是产品咨询还是售后服务，都将在最短时间内给予答复和处理。', 3);
