ALTER TABLE work_order_progress
    ADD COLUMN request_token VARCHAR(64) NULL,
    ADD COLUMN submission_hash CHAR(64) NULL,
    ADD COLUMN confirmed_content_hash CHAR(64) NULL,
    ADD COLUMN customer_confirmed_by BIGINT UNSIGNED NULL,
    ADD COLUMN customer_confirmed_at DATETIME(3) NULL,
    ADD UNIQUE KEY uk_progress_submission (order_id, installer_user_id, request_token);

CREATE TABLE work_order_progress_confirmation (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    progress_id BIGINT UNSIGNED NOT NULL,
    customer_user_id BIGINT UNSIGNED NOT NULL,
    content_hash CHAR(64) NOT NULL,
    content_snapshot JSON NOT NULL,
    confirmed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_progress_confirmation (progress_id, confirmed_at),
    CONSTRAINT fk_confirmation_progress FOREIGN KEY (progress_id) REFERENCES work_order_progress (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户施工进度确认记录及内容快照';
