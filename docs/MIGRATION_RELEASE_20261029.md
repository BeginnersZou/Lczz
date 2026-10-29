# 2026-10-29 已发布迁移编号兼容

线上已执行 `V17__add_auxiliary_other_category.sql`（2026-10-29 生产只读查询截图确认）。已交付的 20261022-bb723f1 包使用这个编号；不能把该历史改成新角色迁移。

本次从 dev@9db1344 调整为以下固定顺序：

| 版本 | 文件 | 处理 |
| --- | --- | --- |
| V17 | V17__add_auxiliary_other_category.sql | 恢复原发布 SQL，内容不变 |
| V18 | V18__single_role_sales_and_project_manager.sql | 原 dev 的 V17，仅顺延编号 |
| V19 | V19__sales_order_assignment.sql | 原 dev 的 V18，仅顺延编号 |

上线前备份生产数据库，执行只读查询：

```sql
SELECT version, description, script, checksum, success
FROM flyway_schema_history
WHERE version IN ('17', '18', '19') ORDER BY installed_rank;

SELECT user_id, COUNT(*) AS role_count
FROM sys_user_role GROUP BY user_id HAVING COUNT(*) > 1;
```

针对当前线上旧版，第一条应仅有已成功执行的辅材 V17；第二条必须无结果。V18/V19 由新后端启动自动执行。不要删除或修改 Flyway 历史，不要运行 repair 掩盖不一致。若任何环境已执行旧编号的新角色 V17 或销售订单 V18，不能直接用本包升级，必须单独核对，不能覆盖其迁移历史。

本修复不更改业务接口，也不修改旧版分类数据。测试使用隔离 MySQL 8，保留旧 V17 校验值，检查仅执行 V18/V19、角色与唯一约束/订单字段正常、再次启动无待执行迁移。测试库为模拟已升级的生产结构，在 V9 前加外键支持索引；仓库既有 V9 的全新安装问题不在本修复范围内，本次不声称全新安装验证通过。

2026-10-29 验证通过：后端完整 `mvn verify` 212 项全部通过；MySQL 8.0.46 从旧发布包的真实迁移资源建立 V17 历史后升级成功，再次 migrate 执行 0 条。V17 checksum 保持 `83900083`，新 V18 为 `857361270`、V19 为 `908656044`。本次没有连接或修改生产数据库。
