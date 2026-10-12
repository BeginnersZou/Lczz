# 项目时间约定

本系统业务页面统一按北京时间（Asia/Shanghai）展示，接口中的业务日期时间带 `+08:00`。设备和 Java 进程的默认时区不应改变显示、排序或业务日期筛选结果。

## 保留现有字段存储含义

| 字段来源 | 数据库存储含义 | 返回方式 |
| --- | --- | --- |
| 施工进度 `submitted_at` | Java 明确写入 UTC 的 DATETIME | `@UtcTime` 按 UTC 解读，转北京时间并带时区 |
| 工程耗材申请的提交、备货完成、作废时间 | Java 明确写入 UTC 的 DATETIME | `@UtcTime`，申请视图 `createTime` 是提交时间的别名，同样按 UTC 解读 |
| 评价 `created_at`、用户 `last_login_at` | Java 明确写入 UTC 的 DATETIME | `@UtcTime` |
| 客户确认完成时间 | 状态历史内保存 UTC，已有 OffsetDateTime 视图 | 保留原时刻，序列化为北京时间 |
| 订单、用户、产品、文件、案例、服务页、自助耗材订单等数据库自动生成时间 | 北京时间 DATETIME | 默认 LocalDateTime 序列化补上北京时间时区，不再平移八小时 |
| 预约开始和结束时间 | 北京时间 DATETIME | 带时区输入先转换到北京时间，无时区输入按北京时间接受；返回带时区时间 |
| 短信重试、登录挑战到期、文件签名到期等内部控制时间 | 保留现有 UTC / Instant 语义 | 不使用页面展示时间进行到期判断 |

`BusinessTimeJsonConfig` 只调整响应序列化，不批量修改数据库字段。UTC 字段必须在响应模型上显式标注 `@UtcTime`，不能把所有 LocalDateTime 都当作 UTC。

数据库连接初始化为 `SET time_zone = '+08:00'`，使未来 `CURRENT_TIMESTAMP` 生成北京时间，不依赖数据库主机的 SYSTEM 时区。测试 H2 使用对应的 `SET TIME ZONE '+08:00'`。DATETIME 读取使用 JDBC `getObject(..., LocalDateTime.class)`，避免通过 Timestamp 再套用 JVM 时区。

## 页面、预约和导出

- 两端时间函数固定显示北京时间。Z、带偏移字符串及时间戳代表真实时刻；旧的无时区字符串按北京时间兼容，不能一律补 Z。
- 后台日期选择器回显时，先将接口时间转成北京时间的无时区表单值，再按现有表单格式保存，避免设备时区影响回显。
- 备货汇总将工程申请的 UTC 提交时间在查询投影中转成北京时间，与自助下单的北京时间一起排序；列表、详情及 CSV 使用同一口径。原表数据保持原值。
- 工作台以北京时间确定“今天”和日期分桶。订单日期筛选仍以北京时间的起止日边界查询数据库自动生成的订单创建时间。
- 订单 CSV 和导出文件日期统一按北京时间生成。

## 上线前核对历史时间来源

本次不提供自动迁移 SQL。发版前在原生产连接中只读查询：

```sql
SELECT @@global.time_zone AS global_zone,
       @@session.time_zone AS session_zone,
       NOW(3) AS session_now,
       UTC_TIMESTAMP(3) AS utc_now,
       TIMEDIFF(NOW(), UTC_TIMESTAMP()) AS session_offset;
```

预期生产自动生成时间的历史来源为北京时间（偏移 08:00:00）；若 session_zone 为 SYSTEM，结合 session_offset 判断实际时区。仅看 JDBC `serverTimezone` 参数不能证明历史 `CURRENT_TIMESTAMP` 的时区。

再选择一笔操作发生时间明确的订单，核对 `work_order.created_at`、`work_order_progress.submitted_at`、`material_request.submitted_at`。正常情况下，后两者的 UTC 值比真实北京时间早八小时，而订单创建时间本身就是北京时间。

如果发现某些历史数据库自动生成字段实际上保存 UTC，或者历史预约通过旧接口带时区提交后保存了 UTC，需要根据表、字段、调用来源及可验证的样本单独判定范围、备份和回滚，不能对所有时间统一加八小时。单凭 DATETIME 文本无法识别其原始时区。

## 验证

后端测试覆盖 UTC 响应、客户确认、数据库时间、预约回显、日期筛选、备货排序与导出，以及不同 JVM 默认时区下的工作台统计。

```powershell
node scripts/verify-business-time.cjs
```

该检查分别在北京时间、UTC 和洛杉矶设备时区运行两端实际格式化函数与订单 CSV，验证上午 10:36、跨零点、跨年、时间戳和预约表单值。
