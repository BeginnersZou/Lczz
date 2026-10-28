ALTER TABLE work_order
    ADD COLUMN sales_user_id BIGINT UNSIGNED NULL COMMENT '指定销售员，未指定时为空',
    ADD COLUMN sales_request_id VARCHAR(64) NULL COMMENT '销售员预约幂等键',
    ADD COLUMN sales_request_hash VARCHAR(64) NULL COMMENT '销售员预约内容摘要',
    ADD CONSTRAINT fk_work_order_sales FOREIGN KEY (sales_user_id) REFERENCES sys_user (id),
    ADD KEY idx_work_order_sales (sales_user_id),
    ADD UNIQUE KEY uk_work_order_sales_request (created_by, sales_request_id),
    DROP CHECK chk_work_order_assignment,
    ADD CONSTRAINT chk_work_order_assignment CHECK (
        (order_status IN ('PENDING_ASSIGNMENT', 'CANCELLED')
            AND order_source IN ('DEALER_APPOINTMENT', 'SALES_APPOINTMENT'))
        OR installer_user_id IS NOT NULL),
    DROP CHECK chk_work_order_source,
    ADD CONSTRAINT chk_work_order_source CHECK (
        (order_source = 'ADMIN' AND dealer_user_id IS NULL AND dealer_request_id IS NULL
            AND dealer_request_hash IS NULL AND sales_request_id IS NULL AND sales_request_hash IS NULL)
        OR (order_source = 'DEALER_APPOINTMENT' AND dealer_user_id IS NOT NULL
            AND dealer_request_id IS NOT NULL AND dealer_request_hash IS NOT NULL
            AND sales_request_id IS NULL AND sales_request_hash IS NULL)
        OR (order_source = 'SALES_APPOINTMENT' AND dealer_user_id IS NULL
            AND dealer_request_id IS NULL AND dealer_request_hash IS NULL
            AND sales_request_id IS NOT NULL AND sales_request_hash IS NOT NULL));
