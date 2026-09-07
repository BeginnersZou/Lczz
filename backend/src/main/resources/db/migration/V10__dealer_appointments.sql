ALTER TABLE work_order
    MODIFY installer_user_id BIGINT UNSIGNED NULL COMMENT '待派单为空，派单后为唯一主责师傅',
    ADD COLUMN order_source VARCHAR(32) NOT NULL DEFAULT 'ADMIN' COMMENT 'ADMIN/DEALER_APPOINTMENT',
    ADD COLUMN dealer_user_id BIGINT UNSIGNED NULL COMMENT '提交预约的经销商',
    ADD COLUMN dealer_request_id VARCHAR(64) NULL COMMENT '经销商提交幂等键',
    ADD COLUMN dealer_request_hash VARCHAR(64) NULL COMMENT '原始预约内容摘要',
    ADD CONSTRAINT fk_work_order_dealer FOREIGN KEY (dealer_user_id) REFERENCES sys_user (id),
    ADD UNIQUE KEY uk_work_order_dealer_request (dealer_user_id, dealer_request_id),
    DROP CHECK chk_work_order_status,
    ADD CONSTRAINT chk_work_order_status CHECK (order_status IN
        ('PENDING_ASSIGNMENT', 'PENDING_VISIT', 'IN_PROGRESS', 'PENDING_REVIEW', 'REVIEWED', 'CANCELLED')),
    ADD CONSTRAINT chk_work_order_assignment CHECK (
        (order_status IN ('PENDING_ASSIGNMENT', 'CANCELLED') AND order_source = 'DEALER_APPOINTMENT')
        OR installer_user_id IS NOT NULL),
    ADD CONSTRAINT chk_work_order_source CHECK (
        (order_source = 'ADMIN' AND dealer_user_id IS NULL AND dealer_request_id IS NULL AND dealer_request_hash IS NULL)
        OR (order_source = 'DEALER_APPOINTMENT' AND dealer_user_id IS NOT NULL
            AND dealer_request_id IS NOT NULL AND dealer_request_hash IS NOT NULL));

INSERT INTO sys_dict_item (dict_type_id, item_code, item_name, sort_order)
SELECT id, 'PENDING_ASSIGNMENT', '待派单', 5 FROM sys_dict_type WHERE dict_code = 'ORDER_STATUS';
