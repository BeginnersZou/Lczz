-- Deployment preflight: resolve any existing user with more than one role before this migration.
-- The unique key intentionally fails rather than silently granting a different role.
INSERT INTO sys_role (role_code, role_name)
VALUES ('PROJECT_MANAGER', '项目经理'), ('SALES', '销售员');

ALTER TABLE sys_user_role
    ADD UNIQUE KEY uk_sys_user_role_single_user (user_id);
