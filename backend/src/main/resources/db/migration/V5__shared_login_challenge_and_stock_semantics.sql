CREATE TABLE wechat_login_challenge (
    code_hash CHAR(64) NOT NULL,
    app_id VARCHAR(64) NOT NULL,
    open_id VARCHAR(128) NOT NULL,
    union_id VARCHAR(128) NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (code_hash),
    KEY idx_wechat_login_challenge_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信登录一次性身份挑战';

ALTER TABLE product
    MODIFY COLUMN display_stock DECIMAL(12,3) NULL COMMENT '当前可申请库存，耗材申请提交时预占、作废时释放';
