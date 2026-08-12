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

本机现有 `lczz_dev` 由 V1、V2 SQL 直接初始化，首次接入 Flyway 时需仅对该本地库执行一次版本 2 baseline；全新环境禁止 baseline，必须由 Flyway顺序执行 V1/V2。

## 常用命令

```powershell
./mvnw.cmd test
./mvnw.cmd spring-boot:run
```

启动后：

- 健康检查：`http://localhost:8080/actuator/health`
- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

## 目录边界

- `common`：统一响应、异常、Web 基础设施
- `config`：安全、OpenAPI、持久层等配置
- `modules`：后续按 `auth`、`user`、`product`、`order`、`stocking`、`progress`、`review`、`file` 建业务模块
- `db/migration`：Flyway 数据库版本脚本

业务模块只能通过服务层协作，Controller 不直接调用其他模块 Mapper。权限必须在服务端验证。
各业务 Mapper 使用 MyBatis `@Mapper` 明确注册，避免跨模块宽泛扫描。
