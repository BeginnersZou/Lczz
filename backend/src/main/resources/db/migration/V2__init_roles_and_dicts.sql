INSERT INTO sys_role (role_code, role_name)
VALUES
    ('ADMIN', '管理员'),
    ('CUSTOMER', '普通用户'),
    ('INSTALLER', '安装师傅'),
    ('DEALER', '经销商');

INSERT INTO sys_dict_type (dict_code, dict_name)
VALUES
    ('ORDER_TASK_TYPE', '订单任务类型'),
    ('ORDER_STATUS', '订单状态'),
    ('MATERIAL_REQUEST_STATUS', '备货状态');

INSERT INTO sys_dict_item (dict_type_id, item_code, item_name, sort_order)
SELECT id, 'AIR_CONDITIONING_INSTALL', '空调安装', 10 FROM sys_dict_type WHERE dict_code = 'ORDER_TASK_TYPE'
UNION ALL
SELECT id, 'AIR_CONDITIONING_REPAIR', '空调维修', 20 FROM sys_dict_type WHERE dict_code = 'ORDER_TASK_TYPE'
UNION ALL
SELECT id, 'AIR_CONDITIONING_CLEAN', '空调清洗', 30 FROM sys_dict_type WHERE dict_code = 'ORDER_TASK_TYPE'
UNION ALL
SELECT id, 'AIR_CONDITIONING_RELOCATE', '空调移机', 40 FROM sys_dict_type WHERE dict_code = 'ORDER_TASK_TYPE';

INSERT INTO sys_dict_item (dict_type_id, item_code, item_name, sort_order)
SELECT id, 'PENDING_VISIT', '待上门', 10 FROM sys_dict_type WHERE dict_code = 'ORDER_STATUS'
UNION ALL
SELECT id, 'IN_PROGRESS', '处理中', 20 FROM sys_dict_type WHERE dict_code = 'ORDER_STATUS'
UNION ALL
SELECT id, 'PENDING_REVIEW', '待评价', 30 FROM sys_dict_type WHERE dict_code = 'ORDER_STATUS'
UNION ALL
SELECT id, 'REVIEWED', '已评价', 40 FROM sys_dict_type WHERE dict_code = 'ORDER_STATUS'
UNION ALL
SELECT id, 'CANCELLED', '已取消', 90 FROM sys_dict_type WHERE dict_code = 'ORDER_STATUS';

INSERT INTO sys_dict_item (dict_type_id, item_code, item_name, sort_order)
SELECT id, 'PENDING', '待备货', 10 FROM sys_dict_type WHERE dict_code = 'MATERIAL_REQUEST_STATUS'
UNION ALL
SELECT id, 'PREPARING', '备货中', 20 FROM sys_dict_type WHERE dict_code = 'MATERIAL_REQUEST_STATUS'
UNION ALL
SELECT id, 'DONE', '已备货', 30 FROM sys_dict_type WHERE dict_code = 'MATERIAL_REQUEST_STATUS'
UNION ALL
SELECT id, 'VOIDED', '已作废', 90 FROM sys_dict_type WHERE dict_code = 'MATERIAL_REQUEST_STATUS';
