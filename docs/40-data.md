# 数据约束

> 改数据库、统计口径或账单字段前必读。金额符号与 `costType` 约定是全应用共用口径。

## 表结构

- SQLite：`files/muyian.db`，表 `bills(id, year, month, day, clock, money, bankName, costType, orderRemark, source, sourcePkg, createdAt, reimb, starred, orderNo)`（`orderNo` 为订单/交易单号，单号去重用，旧库自动 ALTER 补列）。

## 金额与分类口径

- **金额符号约定：支出为负、收入为正**（`money` 直接带符号；页面展示时再取绝对值加正负号）。所有新增统计/展示代码必须沿用，禁止另存绝对值或反向符号。
- `costType` 存**叶子名**（选了子类存子类名，否则存一级名）；环图统计时用 `topNameOf()` 归并到一级分类。分类改名不回写历史账单。
- 分类数据只存前端 storage（JSON），原生层不感知分类体系；原生解析出的默认 `costType`（消费/收入）允许与分类表不匹配，页面侧兜底。

## 旧库兼容与查询上限

- 新增表字段必须走 `openDb()` 里的 try-catch `ALTER TABLE` 兼容旧库，并在 `billJson()` 补输出（列不存在时给默认值）。
- 单次查询上限：月 500 行、年 5000 行，够用且防止内存膨胀；不要一次性查全表。
