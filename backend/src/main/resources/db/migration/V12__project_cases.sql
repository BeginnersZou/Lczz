CREATE TABLE project_case (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    site_name VARCHAR(255) NOT NULL COMMENT '工地名称',
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_project_case_site_name (site_name),
    KEY idx_project_case_list (deleted, updated_at, id),
    CONSTRAINT fk_project_case_creator FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_project_case_updater FOREIGN KEY (updated_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目案例';
