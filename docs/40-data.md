# 数据约束

> 改数据库、统计口径或账单字段前必读。金额符号与 `costType` 约定是全应用共用口径。

## 表结构

- SQLite：`files/muyian.db`（`BillDb` 内部常量 `DB_NAME`），表 `bills(id, year, month, day, clock, money, bankName, costType, orderRemark, source, sourcePkg, createdAt, reimb, starred, orderNo)`；`orderNo` 为订单/交易单号，单号去重用。
- 建表与旧库补列都在 `BillDb.kt` 的 `openDb()`：`reimb` / `starred` / `orderNo` 用 try-catch `ALTER TABLE ... ADD COLUMN` 兼容旧库；`billJson()` 对缺失列给默认值（0 / 空串）。

## 金额与分类口径

- **金额符号约定：支出为负、收入为正**（`money` 直接带符号；页面展示时再取绝对值加正负号）。所有新增统计/展示代码必须沿用，禁止另存绝对值或反向符号。
- `costType` 存**叶子名**（选了子类存子类名，否则存一级名）；环图统计时用 `Cats.topNameOf()` 归并到一级分类。分类改名不回写历史账单。
- 手动记账由 `saveBillEx()` 写入：收入固定 `costType = "收入"`，支出用页面选的叶子名（空则 `"消费"`）；`source = "manual"`。编辑走 `updateBill()`，保留 `source` / `sourcePkg` / `createdAt`。
- 分类数据只存本机 SharedPreferences（`muyian_native` / key `muyian_categories_v2`，JSON）；原生层不感知分类体系。原生解析出的默认 `costType`（消费/收入）允许与分类表不匹配，页面侧用 `Cats.topNameOf()` / `Cats.exists()` 兜底。

## 查询上限

- 单次查询上限：月 500 行（`getMonthBills`）、年 5000 行（`getYearBills`），够用且防止内存膨胀；不要一次性查全表。
- 全量导出走 `getAllBills(offset, limit)` 按 `id` 升序分页，单次上限 2000，返回 `{"total":n,"rows":[…]}`；`Bills.all()` 已封装分页循环。

## 钱迹导入（ImportScreen）

- **导入是用户主动批量入账**，不走确认弹层：预览 + 分类映射后由用户点「开始导入」才写库，与「自动采集必须过 `BillSheetActivity`」的红线不冲突（那条针对自动捕获通道）。
- 导入账单 `source = "qianji"`，`orderNo` 存钱迹账单 ID；重复导入按 `orderNo` 在库内比对后整批跳过（`commitImportBills` 单事务内先载入全量单号集合）。
- 收入账单 `costType` 固定写 `"收入"`；支出 `costType` 存页面侧映射后的叶子名。
- 钱迹 xlsx 由 `ImportPickActivity`（SAF）拷到应用私有目录 `files/qianji_import.xlsx` 后在本地解析，不引入第三方依赖、不发网络请求；原生 `readImportSheet()` 只用 `ZipInputStream + XmlPullParser` 把 xlsx 解压成「表头 + 原始单元格行」（上限 2 万行、64 列），时间/类型/金额校验、统计、分类计数与映射都在 `data/Qianji.kt`。导入前重复条数由原生 `countExistingOrders` 只读统计。

## 本应用备份（BackupScreen）

- 备份文件是 **JSON**（`孽账备份_YYYYMMDD_HHmm.json`），结构固定：`{ app:"muyian", kind:"backup", version:1, exportedAt, categories:{expense,income}, bills:[…] }`。`bills` 就是 `billJson()` 的完整字段（含 `id` / `orderNo` / `source` / `reimb` / `starred`），金额沿用支出负、收入正。**备注图片（`{{img:path}}` 指向的本地文件）不打包**，恢复后备注文字保留、图片丢失。
- 恢复是**合并模式**，不做覆盖：`commitBackupBills()` 单事务内按 `id`（主键）和 `orderNo` 双重去重，已存在的跳过，因此同一份备份重复恢复是幂等的；恢复时按原 `id` 回填，换机/重装后账单编号与原来一致。
- 分类只补不删不改：备份里当前分类表没有的一级/子类会按原图标追加到对应类型末尾（`BackupScreen` 的缺失分类补全逻辑），已有分类的名称、图标、顺序不动。
- 导出全量账单用 `getAllBills(offset, limit)` 分页，页面侧拼装 JSON 后交给 `openBackupExport()`；内容经进程内静态字段传给 `BackupSaveActivity`，不走 Intent extra（避免大备份超过 Binder 限制）。单文件上限 50MB。
- 备份/恢复与钱迹导入一样只在 App 端可用，不提供 Web/其他端入口。
