ALTER TABLE work_order
    ADD COLUMN project_address VARCHAR(500) NULL COMMENT '项目地址';

CREATE INDEX idx_work_order_project_address ON work_order (project_address);
