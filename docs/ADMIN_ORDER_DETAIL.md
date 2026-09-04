# 后台订单详情

后台订单管理的“查看详情”进入 `/orders/detail/:id`，与新增/编辑表单独立。返回列表保留关键词、状态及分页条件。

## 聚合接口

`GET /api/v1/admin/orders/{id}`，兼容 `GET /api/admin/orders/{id}`。

仅 `ADMIN` 可访问，Controller 和 Service 均校验角色。订单不存在或已逻辑删除时返回 `404`；未登录返回 `401`，非管理员返回 `403`，非法 ID 返回 `400`。响应沿用 `{ code, message, data, requestId, timestamp }`。

`data` 字段：

| 字段 | 内容 | 无记录时 |
| --- | --- | --- |
| `order` | `OrderView`：订单号、类型、客户、地址、预约起止时间、师傅、状态、备注、附件、客户确认人及时间 | 整个接口返回 404 |
| `progress` | 现有 `ProgressView[]`：提交时间、说明、类型、提交师傅 ID、图片/视频 | `[]` |
| `materialRequests` | 现有 `RequestView[]`：申请号、时间、备注、备货状态和耗材明细 | `[]` |
| `review` | 现有 `ReviewView`：评分、点赞、内容、标签、图片及时间 | `null` |

施工记录按提交时间、ID 升序展示，包含已有完工记录；耗材申请按提交时间、ID 倒序返回全部申请，包括作废申请及原因。耗材名称、规格、单位、数量读取提交时的快照，不受当前产品改名、下架或删除影响。现有模型中的备注位于申请单，页面按申请展示。

图片和视频继续使用统一文件服务生成的短时签名地址。页面提供预览、视频播放及刷新入口；地址失效时刷新详情重新获取。前端使用 `statusCode` 区分待评价和已评价，不仅依赖兼容展示字段 `status`。

该接口复用订单、施工、耗材和评价服务。Issue #99 新增 `order.customerConfirmedBy`、`order.customerConfirmedAt`，列表和详情展示确认时间，评价后仍显示原时间；历史师傅完工显示“暂无客户确认记录”。后台“已完成”筛选和导出包含待评价、已评价两种状态，精确筛选保持可用。

## 验证

后端：在 `backend` 执行 `./mvnw.cmd verify`（Windows）或 `./mvnw verify`。`AdminOrderDetailIntegrationTests` 覆盖完整施工与评价流程、签名文件读取、多次耗材快照、其他订单隔离、空态、双前缀权限、非法/删除订单和作废订单查看。

前端：在 `frontend/admin` 执行 `npm ci`、`npm run build`。页面验收包括列表详情跳转与返回筛选、完整数据、空进度/耗材/评价、加载、异常重试、404 和无权限跳转。
