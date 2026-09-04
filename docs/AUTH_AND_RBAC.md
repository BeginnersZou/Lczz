# 认证与角色权限（Issue #5）

## 接口

接口同时提供 `/api/auth`（兼容当前前端）和 `/api/v1/auth` 两套前缀：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/auth/login` | 管理端账号密码登录，仅管理员角色可用 |
| POST | `/auth/wechat/login` | 微信 `code` 换取身份；新用户返回 `needPhone: true` |
| POST | `/auth/wechat/bind-phone` | 使用首步 `code` 与微信 `phoneCode` 完成手机号注册/绑定 |
| GET | `/auth/info` | 获取当前用户（Bearer JWT） |
| POST | `/auth/logout` | 客户端清除无状态 JWT |

统一响应成功码为数值 `200`，错误同时使用 HTTP 状态码、数值 `code` 和稳定的 `error` 标识。

## 角色规则

- `ADMIN`：全部模块、全部订单和管理操作；不能代客户确认完成或评价，不能修改封存的施工附件。
- `CUSTOMER`：产品只读；仅本人绑定订单；可确认处理中订单完成，并评价待评价订单。
- `INSTALLER`：产品只读；仅指派给本人的订单；可选择耗材并提交施工进度，不能确认完成；完成后进度及附件只读。
- `DEALER`：一期与普通用户一致；公司维度订单权限未开放，不能查看未直接绑定本人的订单。

后续订单、产品模块必须在服务端调用 `AuthorizationPolicy`，不能依靠前端隐藏按钮实现权限控制。

## 必需配置

生产/开发运行时通过环境变量提供：

- `JWT_SECRET`：至少 32 个 UTF-8 字节。
- `WECHAT_MINI_APP_ID`、`WECHAT_MINI_APP_SECRET`：微信小程序凭据。
- `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`：MySQL 连接。

首次管理员不写入固定默认密码。需要初始化时，仅在首次启动设置：

```powershell
$env:ADMIN_BOOTSTRAP_USERNAME='admin'
$env:ADMIN_BOOTSTRAP_PASSWORD='替换为至少12位的强密码'
$env:ADMIN_BOOTSTRAP_NICKNAME='系统管理员'
```

创建完成后从部署环境删除这三个变量；后续启动会按用户名幂等跳过。

## 安全约束

- `openId` 与手机号都只信任微信官方接口返回，客户端不能直接声明。
- 首步微信 `code` 仅以 SHA-256 摘要短时缓存 5 分钟，并在绑定时一次性消费。
- JWT 只保存用户 ID；每次请求重新读取账号状态和角色，禁用、拉黑或改角色立即生效。
- 相同 `(appId, openId)` 和手机号由数据库唯一键兜底，重复登录不会生成重复用户。
