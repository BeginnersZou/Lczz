# Lczz 后端数据库

数据库迁移脚本位于 `src/main/resources/db/migration`，后续由 Spring Boot 启动时通过 Flyway 自动执行。

## 本地数据库

建议开发库名：`lczz_dev`。

```powershell
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS lczz_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
mysql -u root -p lczz_dev < src/main/resources/db/migration/V1__init_schema.sql
mysql -u root -p lczz_dev < src/main/resources/db/migration/V2__init_roles_and_dicts.sql
```

正式联调后以 Flyway 执行记录为准，不在已有环境手工修改表结构。数据库账号密码只通过本地环境变量或未提交配置提供。

当前开发机的 `lczz_dev` 已在 2026-08-12 使用 V1、V2 脚本初始化。由于初始化时直接使用 MySQL 客户端执行，首次接入 Spring Boot/Flyway 时，应仅对这个已核验的本地开发库执行一次 `baseline`，基线版本设为 `2`；之后从 V3 开始由 Flyway 自动迁移。全新环境不得 baseline，必须由 Flyway 顺序执行 V1、V2。

## 一期冻结规则

- 订单类型：空调安装、空调维修、空调清洗、空调移机；通过字典扩充。
- 每张订单仅一位当前主责安装师傅，重新指派保留历史。
- 产品一期只展示，不提供购买、购物车、支付和自动库存扣减。
- 经销商一期权限等同普通用户，不建立企业订单范围。
