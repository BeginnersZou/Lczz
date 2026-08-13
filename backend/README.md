# Lczz Backend

Java 21 + Spring Boot 3 的统一后端工程，为后台管理系统和微信小程序提供 `/api/v1` 接口。

## 前置要求

- JDK 21
- Maven 3.9+（仓库包含 Maven Wrapper 启动脚本）
- MySQL 8
- 已创建本地数据库 `lczz_dev`
- GitHub Actions 使用 JDK 21 执行 `./mvnw verify`

## 本地配置

不要把数据库密码或 JWT 密钥写入仓库。启动前在当前终端设置：

```powershell
$env:DB_USERNAME="lczz_app"
$env:DB_PASSWORD="本地数据库密码"
$env:JWT_SECRET="至少32字节的随机密钥"
```

本机现有 `lczz_dev` 由 Flyway 管理；全新环境必须顺序执行 V1-V3，已有环境继续按版本增量迁移。

## 常用命令

```powershell
./mvnw.cmd test
./mvnw.cmd spring-boot:run
```

启动后：

- 健康检查：`http://localhost:8080/actuator/health`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

## 产品与耗材接口

产品一期仅用于展示和安装师傅选择耗材，不提供购买、购物车、支付或库存自动扣减。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/consumables/list` | 已登录用户 | 分页查询；非管理员只返回已启用产品 |
| GET | `/api/v1/consumables/detail/{id}` | 已登录用户 | 产品详情；非管理员不能读取已下架产品 |
| GET | `/api/v1/consumables/categories` | 已登录用户 | 查询两级分类 |
| POST/PUT/DELETE | `/api/v1/consumables/categories/**` | 管理员 | 分类维护 |
| POST | `/api/v1/consumables` | 管理员 | 创建产品 |
| PUT/DELETE | `/api/v1/consumables/{id}` | 管理员 | 更新或逻辑删除产品 |
| PATCH | `/api/v1/consumables/{id}/enabled` | 管理员 | 上架或下架产品 |

产品图片只接收统一文件服务返回的 `coverFileId` 和 `detailFileIds`；文件上传接口由 Issue #13 提供。完整字段和响应模型以 Swagger UI 为准。

## 订单接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/orders/list` | 已登录用户 | 管理员查全部；客户/经销商查绑定订单；师傅查指派订单 |
| GET | `/api/v1/orders/detail/{id}` | 已登录用户 | 按当前角色数据范围查询详情 |
| GET | `/api/v1/orders/masters` | 管理员 | 查询可指派安装师傅 |
| POST | `/api/v1/orders` | 管理员 | 创建订单并指派一位师傅 |
| PUT | `/api/v1/orders/{id}` | 管理员 | 编辑未结束订单 |
| POST | `/api/v1/orders/{id}/assign-master` | 管理员 | 重新指派并保留指派历史 |
| PATCH | `/api/v1/orders/{id}/status` | 管理员 | 按状态机修改订单状态 |
| POST | `/api/v1/orders/{id}/cancel` | 管理员 | 作废订单并保留业务历史 |

订单按客户手机号绑定：客户已注册时创建即绑定；未注册时，在微信首次绑定手机号后自动认领同手机号的历史订单。一期每个订单必须且只能有一位有效安装师傅。订单附件上传由后续文件模块提供。

## 目录边界

- `common`：统一响应、异常、Web 基础设施
- `config`：安全、OpenAPI、持久层等配置
- `modules`：后续按 `auth`、`user`、`product`、`order`、`stocking`、`progress`、`review`、`file` 建业务模块
- `db/migration`：Flyway 数据库版本脚本

业务模块只能通过服务层协作，Controller 不直接调用其他模块 Mapper。权限必须在服务端验证。
各业务 Mapper 使用 MyBatis `@Mapper` 明确注册，避免跨模块宽泛扫描。
