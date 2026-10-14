ALTER TABLE work_order_progress
    ADD COLUMN admin_approved_content_hash CHAR(64) NULL,
    ADD COLUMN admin_edited_by BIGINT UNSIGNED NULL,
    ADD COLUMN admin_edited_at DATETIME(3) NULL;
