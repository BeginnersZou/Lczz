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

本机现有 `lczz_dev` 由 Flyway 管理；全新环境必须顺序执行 V1-V4，已有环境继续按版本增量迁移。

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

产品图片只接收统一文件服务返回的 `coverFileId` 和 `detailFileIds`。完整字段和响应模型以 Swagger UI 为准。

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

订单按客户手机号绑定：客户已注册时创建即绑定；未注册时，在微信首次绑定手机号后自动认领同手机号的历史订单。一期每个订单必须且只能有一位有效安装师傅。订单附件由统一文件服务上传和授权访问。

## 耗材申请与订单备货接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/orders/{orderId}/materials` | 安装师傅 | 为指派给自己的订单提交耗材和数量 |
| GET | `/api/v1/orders/{orderId}/materials` | 订单相关角色 | 查询订单最近一次耗材申请 |
| GET | `/api/v1/preparation/list` | 管理员 | 分页查询备货申请 |
| GET | `/api/v1/preparation/detail/{id}` | 管理员 | 查询备货详情及产品快照 |
| POST | `/api/v1/preparation/{id}/prepare` | 管理员 | 保存各耗材备货进度 |
| POST | `/api/v1/preparation/{id}/finish` | 管理员 | 确认全部备货完成并记录操作人、时间 |
| POST | `/api/v1/preparation/{id}/void` | 管理员 | 作废申请并记录操作人、时间和原因 |

申请明细保存产品编码、名称、规格、单位和展示价格快照。相同申请重试返回原记录，不同内容的重复申请返回冲突；V4 迁移通过唯一索引保证每个订单只有一个未作废申请。展示价格和展示库存仅供参考，不收费、不自动扣减库存。

## 施工进度与完工接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/orders/{orderId}/progress` | 订单相关角色 | 按时间正序查询多条施工进度和唯一完工记录 |
| POST | `/api/v1/orders/{orderId}/progress` | 指派安装师傅 | 提交施工说明及可选图片，首次提交后订单进入处理中 |
| POST | `/api/v1/orders/{orderId}/completion` | 指派安装师傅 | 提交唯一完工记录，必须包含说明和至少一张图片 |

施工图片先通过统一文件接口上传，再将返回的文件 ID 放入 `fileIds`。完工提交成功后订单原子流转到 `PENDING_REVIEW`（待评价）；订单行锁、版本条件和数据库唯一约束共同防止并发重复完工。首次耗材申请同样会把待上门订单流转为处理中。

## 统一文件服务

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/v1/files/upload` | 已登录用户 | 上传单张图片，可同时绑定业务关系 |
| POST | `/api/v1/files/{id}/relations` | 已登录用户 | 将本人上传的文件绑定至有权限的业务 |
| GET | `/api/v1/files/{id}/url` | 业务相关用户 | 校验权限并签发短时访问地址 |
| GET | `/api/v1/files/{id}` | 业务相关用户 | 携带 JWT 直接读取文件 |
| GET | `/api/v1/files/access/{id}` | 短时签名 | 图片组件通过签名地址读取文件 |
| POST | `/api/v1/orders/upload` | 已登录用户 | 兼容现有订单页面的上传入口 |

当前支持 JPEG、PNG、GIF 和 WebP，服务端同时校验文件魔数、扩展名、声明 MIME 和大小；对象键由服务端随机生成。开发默认存放在 `FILE_LOCAL_ROOT`，`FileStorage` 接口用于生产环境接入对象存储实现。私有订单、施工和评价图片读取前会校验业务权限；签名地址默认 5 分钟失效。生产必须设置 `FILE_ACCESS_SECRET`（至少 32 字节），并确保上传目录不由 Web 服务器直接公开。

## 订单评价接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/orders/evaluation/{orderId}` | 订单相关角色 | 查询订单评价，未评价时返回 `null` |
| GET | `/api/v1/orders/evaluation/ids` | 已登录用户 | 查询当前角色数据范围内已评价订单 ID |
| POST | `/api/v1/orders/evaluation` | 绑定客户/经销商 | 对待评价订单提交唯一评价并流转到已评价 |
| POST | `/api/v1/upload/image` | 已登录用户 | 兼容小程序评价页的图片上传入口 |

评分范围为 1～5，评价正文必填，最多关联 9 张统一文件服务图片。订单行锁、版本条件和评价表唯一索引共同防止重复评价；评价及图片关系与订单状态在同一事务中提交。

## 目录边界

- `common`：统一响应、异常、Web 基础设施
- `config`：安全、OpenAPI、持久层等配置
- `modules`：后续按 `auth`、`user`、`product`、`order`、`stocking`、`progress`、`review`、`file` 建业务模块
- `db/migration`：Flyway 数据库版本脚本

业务模块只能通过服务层协作，Controller 不直接调用其他模块 Mapper。权限必须在服务端验证。
各业务 Mapper 使用 MyBatis `@Mapper` 明确注册，避免跨模块宽泛扫描。
