-- The admin's category picker is populated from product_category.
-- Add "其他" under each existing top-level "辅材" group without changing its siblings.
INSERT INTO product_category
    (category_code, category_name, parent_id, category_level, sort_order, enabled, deleted)
SELECT
    CONCAT('lczz-aux-other-', parent.id),
    '其他',
    parent.id,
    2,
    COALESCE((
        SELECT MAX(child.sort_order)
        FROM product_category AS child
        WHERE child.parent_id = parent.id AND child.deleted = 0
    ), 0) + 10,
    1,
    0
FROM product_category AS parent
WHERE parent.category_name = '辅材'
  AND parent.category_level = 1
  AND parent.enabled = 1
  AND parent.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM product_category AS existing
      WHERE existing.parent_id = parent.id
        AND existing.category_name = '其他'
        AND existing.deleted = 0
  )
  AND NOT EXISTS (
      SELECT 1
      FROM product_category AS existing
      WHERE existing.category_code = CONCAT('lczz-aux-other-', parent.id)
  );
