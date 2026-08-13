INSERT INTO product_category (category_code, category_name, category_level, sort_order)
VALUES
    ('installation-materials', '安装辅料', 1, 10),
    ('refrigerant', '制冷剂', 1, 20),
    ('tools', '工具', 1, 30),
    ('air-conditioning-equipment', '空调设备', 1, 40);

INSERT INTO product_category (category_code, category_name, parent_id, category_level, sort_order)
SELECT 'copper', '铜管', id, 2, 10 FROM product_category WHERE category_code = 'installation-materials'
UNION ALL
SELECT 'bracket', '支架', id, 2, 20 FROM product_category WHERE category_code = 'installation-materials'
UNION ALL
SELECT 'cable', '电缆', id, 2, 30 FROM product_category WHERE category_code = 'installation-materials'
UNION ALL
SELECT 'aux', '其他辅材', id, 2, 40 FROM product_category WHERE category_code = 'installation-materials'
UNION ALL
SELECT 'r410a', 'R410A', id, 2, 10 FROM product_category WHERE category_code = 'refrigerant'
UNION ALL
SELECT 'r32', 'R32', id, 2, 20 FROM product_category WHERE category_code = 'refrigerant'
UNION ALL
SELECT 'r22', 'R22', id, 2, 30 FROM product_category WHERE category_code = 'refrigerant'
UNION ALL
SELECT 'welding-tools', '焊接工具', id, 2, 10 FROM product_category WHERE category_code = 'tools'
UNION ALL
SELECT 'testing-instruments', '检测仪表', id, 2, 20 FROM product_category WHERE category_code = 'tools'
UNION ALL
SELECT 'household-air-conditioner', '家用空调', id, 2, 10 FROM product_category WHERE category_code = 'air-conditioning-equipment'
UNION ALL
SELECT 'commercial-air-conditioner', '商用空调', id, 2, 20 FROM product_category WHERE category_code = 'air-conditioning-equipment';
