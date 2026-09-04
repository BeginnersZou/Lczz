CREATE TABLE product_spec_dimension (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id BIGINT UNSIGNED NOT NULL,
    dimension_name VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_dimension_name (product_id, dimension_name, deleted),
    KEY idx_product_dimension (product_id, deleted, sort_order),
    CONSTRAINT fk_product_dimension_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品动态规格维度';

CREATE TABLE product_spec_value (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dimension_id BIGINT UNSIGNED NOT NULL,
    spec_value VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_dimension_spec_value (dimension_id, spec_value, deleted),
    KEY idx_dimension_value (dimension_id, deleted, sort_order),
    CONSTRAINT fk_spec_value_dimension FOREIGN KEY (dimension_id) REFERENCES product_spec_dimension (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品动态规格值';

CREATE TABLE product_sku (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    product_id BIGINT UNSIGNED NOT NULL,
    sku_code VARCHAR(96) NOT NULL,
    spec_signature VARCHAR(2000) NOT NULL DEFAULT '',
    spec_signature_hash CHAR(64) NOT NULL,
    spec_label VARCHAR(2000) NOT NULL DEFAULT '',
    unit VARCHAR(32) NOT NULL,
    stock DECIMAL(12,3) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    default_sku TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_sku_code (product_id, sku_code, deleted),
    UNIQUE KEY uk_product_spec_signature (product_id, spec_signature_hash, deleted),
    KEY idx_product_sku (product_id, enabled, deleted, sort_order),
    CONSTRAINT fk_product_sku_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品SKU';

CREATE TABLE product_sku_spec_value (
    sku_id BIGINT UNSIGNED NOT NULL,
    dimension_id BIGINT UNSIGNED NOT NULL,
    spec_value_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (sku_id, dimension_id),
    UNIQUE KEY uk_sku_spec_value (sku_id, spec_value_id),
    CONSTRAINT fk_sku_value_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id),
    CONSTRAINT fk_sku_value_dimension FOREIGN KEY (dimension_id) REFERENCES product_spec_dimension (id),
    CONSTRAINT fk_sku_value_value FOREIGN KEY (spec_value_id) REFERENCES product_spec_value (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='SKU规格值组合';

CREATE TABLE installer_cart_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    installer_id BIGINT UNSIGNED NOT NULL,
    sku_id BIGINT UNSIGNED NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_installer_cart_sku (installer_id, sku_id),
    KEY idx_installer_cart (installer_id, updated_at),
    CONSTRAINT fk_cart_installer FOREIGN KEY (installer_id) REFERENCES sys_user (id),
    CONSTRAINT fk_cart_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id) ON DELETE CASCADE,
    CONSTRAINT chk_cart_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安装师傅耗材购物车';

CREATE TABLE material_self_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    order_name VARCHAR(64) NOT NULL DEFAULT '客户下单',
    installer_id BIGINT UNSIGNED NOT NULL,
    request_token VARCHAR(64) NOT NULL,
    order_status VARCHAR(32) NOT NULL DEFAULT 'ORDERED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_material_self_order_no (order_no),
    UNIQUE KEY uk_material_self_order_request (installer_id, request_token),
    KEY idx_self_order_installer (installer_id, created_at),
    CONSTRAINT fk_self_order_installer FOREIGN KEY (installer_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安装师傅自助取货订单';

CREATE TABLE material_self_order_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    self_order_id BIGINT UNSIGNED NOT NULL,
    sku_id BIGINT UNSIGNED NULL,
    product_id BIGINT UNSIGNED NULL,
    product_name_snapshot VARCHAR(255) NOT NULL,
    sku_code_snapshot VARCHAR(96) NOT NULL,
    spec_snapshot VARCHAR(2000) NOT NULL DEFAULT '',
    unit_snapshot VARCHAR(32) NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_self_order_item_order (self_order_id, id),
    CONSTRAINT fk_self_order_item_order FOREIGN KEY (self_order_id) REFERENCES material_self_order (id),
    CONSTRAINT fk_self_order_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id) ON DELETE SET NULL,
    CONSTRAINT fk_self_order_item_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT chk_self_order_quantity CHECK (quantity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自助取货订单明细快照';

ALTER TABLE material_request_item
    ADD COLUMN sku_id BIGINT UNSIGNED NULL AFTER product_id,
    ADD COLUMN sku_code_snapshot VARCHAR(96) NULL AFTER product_code_snapshot,
    ADD COLUMN sku_spec_snapshot VARCHAR(2000) NULL AFTER product_name_snapshot,
    ADD KEY idx_material_request_sku (sku_id),
    DROP INDEX uk_material_request_product;

INSERT INTO product_sku(product_id, sku_code, spec_signature, spec_signature_hash, spec_label, unit, stock, enabled,
                        default_sku, sort_order, version, deleted)
SELECT id, CONCAT(product_code, '-DEFAULT'), '', SHA2('', 256), COALESCE(model_spec, ''), unit,
       COALESCE(display_stock, 0), enabled, 1, 0, 0, 0
FROM product
WHERE deleted = 0;

UPDATE material_request_item i
JOIN product_sku s ON s.product_id=i.product_id AND s.default_sku=1 AND s.deleted=0
SET i.sku_id=s.id,
    i.sku_code_snapshot=s.sku_code,
    i.sku_spec_snapshot=s.spec_label;

ALTER TABLE material_request_item
    ADD UNIQUE KEY uk_material_request_sku (request_id, sku_id),
    ADD CONSTRAINT fk_material_request_item_sku FOREIGN KEY (sku_id) REFERENCES product_sku (id) ON DELETE SET NULL;
