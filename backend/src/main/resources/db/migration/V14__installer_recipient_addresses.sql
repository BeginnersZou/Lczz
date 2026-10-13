CREATE TABLE installer_recipient_address (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    installer_id BIGINT UNSIGNED NOT NULL,
    recipient_name VARCHAR(64) NOT NULL,
    recipient_phone VARCHAR(32) NOT NULL,
    recipient_address VARCHAR(500) NOT NULL,
    is_default TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_recipient_address_owner (installer_id, is_default, id),
    CONSTRAINT fk_recipient_address_installer FOREIGN KEY (installer_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安装师傅常用收货地址';

ALTER TABLE material_self_order
    ADD COLUMN recipient_name VARCHAR(64) NULL,
    ADD COLUMN recipient_phone VARCHAR(32) NULL,
    ADD COLUMN recipient_address VARCHAR(500) NULL;
