# Lczz 后端架构基线

## 1. 定位

后端是后台管理系统和微信小程序的统一业务服务，统一提供 `/api/v1` 接口、身份认证、角色授权、业务状态流转、文件元数据和审计能力。

技术基线：Java 21、Spring Boot 3.5、Maven 3.9、MyBatis-Plus 3.5、MySQL 8、Flyway、Spring Security、JWT、OpenAPI 3。

## 2. 代码分层

```text
com.lczz
├── common
│   ├── api             # 统一响应和分页结构
│   ├── exception       # 业务异常与全局异常处理
│   └── web             # requestId、Web 通用基础设施
├── config              # Security、OpenAPI、MyBatis-Plus 等配置
└── modules
    ├── auth
    ├── user
    ├── product
    ├── order
    ├── stocking
    ├── progress
    ├── review
    └── file
```

每个业务模块内部按 `controller`、`service`、`mapper`、`entity`、`dto`、`enums` 组织。Controller 只处理协议和参数，业务状态变化必须进入 Service；Controller 不直接调用其他模块 Mapper。Mapper 使用 `@Mapper` 明确注册。

## 3. 接口与安全边界

- 公共接口：`/api/v1/auth/**`。
- 管理接口：`/api/v1/admin/**`。
- 小程序接口：`/api/v1/mini/**`。
- 健康检查和 OpenAPI 文档可匿名访问，其他接口默认需要认证。
- 管理员、普通用户、安装师傅和经销商权限由后端校验，前端隐藏按钮不构成授权。
- 业务错误返回稳定错误码，不直接返回堆栈或数据库异常。
- 所有响应返回或携带 `X-Request-Id`，服务端日志使用同一 requestId。
- 后台密码使用 BCrypt；JWT 密钥、微信凭证和数据库密码只从环境变量/密钥管理服务读取。

## 4. 配置和环境

| 环境 | Profile | 数据库 | 用途 |
| --- | --- | --- | --- |
| 单元/集成测试 | `test` | H2 MySQL 兼容模式 | 上下文、HTTP 与安全边界测试，禁用 Flyway |
| 本地开发 | `local` | 本机 `lczz_dev` | 日常接口开发和前端联调 |
| 测试发布 | `test`/后续独立 profile | 独立 MySQL | `release/*` 部署验收 |
| 生产 | `prod` | 生产 MySQL | `master` 稳定发布 |

生产和测试环境不得使用本地默认值。至少必须显式提供：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`、文件存储配置和微信小程序凭证。

## 5. 数据库前置

- PR #11 / Issue #4 交付 V1 表结构和 V2 角色/字典，是业务开发的数据库前置依赖。
- 全新数据库由 Flyway 顺序执行 V1、V2，禁止手工建表。
- 当前开发机 `lczz_dev` 已直接执行过 V1、V2，首次接入后端时对该本地库 baseline 到版本 2；其他环境不得复制此操作。
- 后续表结构变化只新增 `V3__...sql` 等迁移，不修改已在共享环境执行过的 V1/V2。
- 数据库账号遵循最小权限：应用账号不授予建库、用户管理和全局权限；迁移账号与应用账号在部署环境分离。

## 6. 文件与图片

本地开发使用文件系统，生产通过适配层切换对象存储。数据库保存文件对象键、MIME、大小和业务关系，不保存 Base64 和二进制文件。文件下载与图片访问必须验证业务权限。

## 7. 验证门槛

```powershell
./mvnw.cmd verify
```

当前基线自动验证：

- Java 版本必须为 21。
- Spring Boot 上下文可启动。
- 真实嵌入式 Tomcat 可在随机端口启动。
- `/actuator/health` 返回健康状态和 requestId。
- `/v3/api-docs` 返回 OpenAPI 文档。
- 未认证业务请求返回统一 401 响应。

GitHub Actions 在后端或 CI 文件变化时执行相同的 `./mvnw verify`。

## 8. Issue 开发顺序

1. Issue #4：数据库基线（PR #11）。
2. Issue #3：后端基础架构。
3. Issue #5：登录、四角色与服务端权限。
4. Issue #6：产品/耗材接口。
5. Issue #7：订单、绑定、指派与角色数据范围。
6. Issue #8：耗材申请与备货。
7. Issue #9：施工进度与完工。
8. 评价、文件上传、后台联调和小程序联调按依赖继续推进。

每个 Issue 从最新 `dev` 创建独立 `feature/*` 分支，只通过 PR 合入 `dev`。
