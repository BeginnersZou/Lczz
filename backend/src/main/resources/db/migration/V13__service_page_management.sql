CREATE TABLE service_page_config (
    id BIGINT UNSIGNED NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    company_subtitle VARCHAR(100) NOT NULL,
    slogan VARCHAR(200) NOT NULL,
    profile_text VARCHAR(2000) NOT NULL,
    phone_primary VARCHAR(32) NOT NULL,
    phone_secondary VARCHAR(32) NULL,
    address VARCHAR(500) NOT NULL,
    longitude DECIMAL(10, 6) NOT NULL,
    latitude DECIMAL(9, 6) NOT NULL,
    business_hours VARCHAR(255) NOT NULL,
    brand_visible TINYINT(1) NOT NULL DEFAULT 1,
    services_visible TINYINT(1) NOT NULL DEFAULT 1,
    profile_visible TINYINT(1) NOT NULL DEFAULT 1,
    gallery_visible TINYINT(1) NOT NULL DEFAULT 1,
    advantages_visible TINYINT(1) NOT NULL DEFAULT 1,
    contact_visible TINYINT(1) NOT NULL DEFAULT 1,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_service_page_creator FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_service_page_updater FOREIGN KEY (updated_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小程序服务页配置';

CREATE TABLE service_page_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    service_page_id BIGINT UNSIGNED NOT NULL,
    item_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_service_page_item_list (service_page_id, item_type, sort_order, id),
    CONSTRAINT fk_service_page_item_page FOREIGN KEY (service_page_id) REFERENCES service_page_config (id) ON DELETE CASCADE,
    CONSTRAINT chk_service_page_item_type CHECK (item_type IN ('HERO_STAT', 'SERVICE', 'PROFILE_TAG', 'ADVANTAGE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小程序服务页可排序内容';

INSERT INTO service_page_config (
    id, company_name, company_subtitle, slogan, profile_text, phone_primary, phone_secondary,
    address, longitude, latitude, business_hours
) VALUES (
    1,
    '武汉力创之尊',
    '制冷技术服务有限公司',
    '以诚信之心，立潮流之品',
    '武汉力创之尊机电设备有限公司（力创之尊）专注于制冷技术、水系统配件、二联供材料销售及水系统中央空调安装与售后服务。我们始终秉持“以诚信之心，立潮流之品”的理念，为家庭与商业客户提供清晰、可靠的暖通服务方案。',
    '027-82710326',
    '027-82710380',
    '湖北省武汉市江岸区不锈钢路S17-49-51号力创之尊',
    114.306997,
    30.665673,
    '周一至周日 8:00-20:00'
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
