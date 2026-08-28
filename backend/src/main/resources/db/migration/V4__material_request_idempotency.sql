ALTER TABLE material_request
    ADD COLUMN active_order_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (
            CASE WHEN request_status IN ('PENDING', 'PREPARING', 'DONE') THEN order_id ELSE NULL END
        ) STORED COMMENT '同一订单仅允许一个未作废耗材申请',
    ADD UNIQUE KEY uk_material_request_one_active (active_order_id);
