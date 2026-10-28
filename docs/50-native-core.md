# 原生核心约束（muyian.bill.listener）

> 动 `android/app/src/main/java/muyian/bill/listener/` 之前必读。改完重新构建安装即可，但会影响无障碍/通知监听等系统级行为，必须在真机上回归。

## API 约定

- 原生核心包名 `muyian.bill.listener`，对外入口是 `object MuyianBillCore`（`hybrid.kt`），方法全部 `@JvmStatic`、同步返回。复杂结构用 **JSON 字符串**传递，页面侧通过 `data/Bills.kt`、`data/Qianji.kt`、`data/Capture.kt` 的解析函数消费。
- 同包分文件，不要把所有逻辑塞回 `hybrid.kt`：
  - `CaptureModels.kt` / `CaptureText.kt` / `CaptureRules.kt` / `CaptureParser.kt` / `CaptureGates.kt`：纯 Kotlin，无 Android import，可单测。
  - `CaptureRegistry.kt`：App 角色、用户覆盖、采集设置、应用名查询。
  - `CaptureRoute.kt`：支付路线状态机（`HOST → PAY_ENTERED → PAY_EVIDENCE → RETURNED`），状态落盘 15 分钟。
  - `CaptureLedger.kt`：交易账本（`capture_ledger`）、待确认队列（`pending_captures`）、诊断记录（`capture_trace`）。
  - `CaptureEngine.kt`：观察 → 解析 → 门禁 → 账本 → 弹层编排。
  - `CapturePresenter.kt`：待确认队列与 `BillSheetActivity` 拉起。
  - `CaptureLearning.kt`：商户/App → 分类的本地计数。
  - `BillDb.kt`：SQLite；`SystemPerms.kt`：权限/自愈/主题色；`XlsxReader.kt`：xlsx；`PendingNotices.kt`：待确认通知；`hybrid.kt`：门面 + 导入/备份。
- Kotlin 侧只用 Android 框架 API + `org.json`，禁止引入第三方库。
- 新增采集能力只改对应层：App 名单与页面/金额规则 → `CaptureRules.kt`；解析 → `CaptureParser.kt`；交易身份 → `CaptureGates.kt` 的 `CaptureIdentity`；门禁顺序 → `CaptureGates.kt` + `CaptureEngine.kt`。不要在 `CaptureEngine` 里写某个 App 的 `if`。

## 自动记账链路（唯一路径）

```
无障碍/通知观察
  → SourceGate（本应用/系统/非候选/用户关闭）
  → PageClassifier（失败/处理中/列表/订单管理/营销/聊天/收银台/成功）
  → Facts（金额候选、方向、商户、单号、真实渠道、页面时间、置信度）
  → 语义门禁（本人互转/信用卡还款/历史页面/低置信）
  → Identity（order > session > page > capture > bill）
  → CaptureLedger 原子占位
  → CapturePresenter 持久队列 → BillSheetActivity
  → 用户保存才写 bills
```

- **确认弹层不可绕过**：引擎只产出待确认账单；`saveSheetBill()` 是唯一写库入口。取消/返回/60 秒超时/弹层显示后熄屏都丢弃并标记账本 `discarded`。
- **弹层拉不起来**（熄屏/锁屏、ROM 拦截、5 秒看门狗）：`CapturePresenter` 发待确认通知，通知只携带票据，点开仍进 `BillSheetActivity`。
- **队列持久化**：入队写 `pending_captures`；进程被杀/服务重建后 `MuyianBillCore.init()` → `CaptureEngine.restore()` 恢复。队列上限 5，超出返回 `QUEUE_FULL` 并记诊断，不再静默丢最旧的一笔。

## 页面分类与解析

- 页面类型：`PAY_SUCCESS / TRANSFER_SUCCESS / RECEIVE_SUCCESS / REFUND_SUCCESS / REPAYMENT_SUCCESS / CASHIER / PAY_FAILURE / PAY_PENDING / ORDER_DETAIL / ORDER_LIST / BILL_LIST / CHAT / AD / UNKNOWN`。
- 分类顺序固定：失败 → 处理中 → 账单列表 → 订单管理 → 完成标记。没有完成标记时再判断营销、聊天、收银台、订单详情。支付 App 的会话页同一屏出现多条完成文案按聊天/历史拒绝。
- **金额候选**：`STRONG_LABEL`（实付/交易金额/退款金额等）→ `STRUCT_HEADLINE`（结果卡片内字号 ≥ 基准 1.5 倍且靠近完成标记）→ `WEAK_LABEL`（必须带货币单位或小数）→ `CURRENCY_CONTEXT`（只限完成标记附近卡片）。排除词按余额/额度/优惠/费用/积分/汇率/尾号/验证码分类维护；同一层出现两个不同金额时标 `needsReview`，弹层提示核对，不静默取数。
- **方向语义**：退款 → `REFUND`（金额记正，分类建议「退款」）；还款 → `REPAYMENT`；提现/充值/余额宝/零钱通/本人账户 → `TRANSFER`；这两类默认不弹层（设置页「记录本人互转与还款」可开）。收款/到账 → `INCOME`；支付/消费/扣款 → `EXPENSE`。
- **渠道**：银行 App 从文本取「XX银行 + 卡尾号」，不再统一写「银行卡」；微信/支付宝/云闪付分别固定为对应渠道。
- **历史页面**：支付 App 页面若带明确旧日期且距当前超过 10 分钟，返回 `STALE_PAGE`，不弹层。
- **通知发送方**：微信只认标题/渠道含「微信支付」或支付结果文案；支付宝认「支付宝/交易提醒/支付助手/余额宝/花呗」；银行通知走通用规则。好友聊天里的「我支付了100元」按 `PAGE_CHAT` 拒绝。

## 交易身份与去重

- 身份键：`order:<单号>`（24h）> `session:<支付会话>`（15min）> `page:<包名:页面指纹>`（宿主结果页，24h）> `capture:<包名:金额:方向:商户>`（5min）> `bill:<真实渠道:金额:方向>`（2min）。
- `CaptureLedger.claim()` 在一个事务里查询全部键：命中同会话/同单号 → `MERGED`（后到的支付侧字段补进已排队账单）；命中页面/捕获/账单 → `DUPLICATE`；否则插入并返回 `CLAIMED`。
- 已入账兜底：有单号先查 `bills.orderNo`；无单号查同渠道 + 金额 ±0.005 + `createdAt` ±2 分钟。`bills` 表新增 `idx_bills_created` / `idx_bills_order` 索引，禁止再全表扫描。
- 支付会话：`CaptureRoute` 在支付 App 出现收银台/成功页/支付通知时记证据，回到宿主后进入 `RETURNED`；宿主结果页只接受「3 分钟内刚从有证据的支付 App 返回」或「10 分钟内点过收银台/提交订单」。**不再使用“15 分钟内存在任意 flow 就放行”的兜底。**
- 锁顺序：`CaptureRoute` 内部串行；`CapturePresenter` 内部队列串行；`CaptureLedger` 用独立事务。禁止在持有 presenter 锁时调 route/ledger 的长操作。

## 底部确认弹层

- `BillSheetActivity` 是透明 `Activity`（`Theme.Translucent.NoTitleBar`），由 `CapturePresenter` `startActivity` 拉起；**不用 `WindowManager` 绘制浮窗**，但清单必须声明 `SYSTEM_ALERT_WINDOW`——它对应「显示在其他应用上层」，禁止删除。MIUI/HyperOS 还有独立的「后台弹出界面」AppOp（10021），两个开关都允许才能后台启动 Activity。
- 设置页拆成两项：`hasOverlayPermission` / `requestOverlayPermission` 管「显示在其他应用上层」，`getPopupPermissionState` / `openPopupPermissionSettings` 管「后台弹出界面」；后者只在 `miui = true` 时显示。
- 布局全部用 Android 框架 View，禁止引入 UI 库；分类只读账单里的 `costType` 字符串，原生仍不感知分类体系。必须有 60 秒超时、熄屏收起、返回键取消；取消/超时/熄屏一律丢弃，不落草稿。
- 弹层已显示后熄屏 → 关闭并丢弃；弹层从未拉起 → 通知兜底。`BillSheetActivity` 创建/销毁时取消对应通知。
- `android:taskAffinity=""` + `excludeFromRecents="true"` 让它独立成任务且不进最近任务。**不要再引入 `DraftCard` / `WindowManager` 浮窗 / 兜底草稿 / `isOverlayEnabled` 开关；待确认通知只做弹层入口。**

## 诊断与设置

- `getCaptureDiagnostics(context, limit)` 返回 `{"summary":{accepted,saved,discarded,rejected,merged,reasons[]},"traces":[…],"settings":{…}}`；`getCaptureApps` / `setCaptureApp` 管理 App 角色与开关；`getCaptureSettings` / `setCaptureSettings` 管理 `recordTransfers` / `debugText` / `lowConfidence`。
- 诊断记录默认**不存页面文本**；只有用户打开「记录页面文本（调试）」才存最近 500 条、保留 7 天，仅本机、可一键清空。禁止上传。
- 页面侧只通过 `data/Capture.kt` 消费这些 JSON；设置页入口是「自动记账 → 识别记录与诊断」。

## 原生核心写法与兼容

- 只用 Android 框架 API + `org.json`；新增系统能力优先用公开 API，兼容旧系统时保留版本判断分支。
- 弃用 API（`AccessibilityNodeInfo.recycle()`、`stopForeground(boolean)`、`Notification.Builder(Context)` / `setPriority()`、`startActivityForResult()`、`SOFT_INPUT_ADJUST_RESIZE`、`statusBarColor` / `navigationBarColor`）必须保留原有版本判断分支；调用处用 `@Suppress("DEPRECATION")` 标注。重写弃用成员（如 `onBackPressed()`）要用 `@Suppress("OVERRIDE_DEPRECATION")`。
- 无障碍服务注册名固定全限定名 `muyian.bill.listener.MuyianBillAccessibilityService`，配置在 `app/src/main/res/xml/muyian_accessibility_config.xml`（含 `flagRetrieveInteractiveWindows`）；通知监听为 `muyian.bill.listener.BillNotificationListener`。
- **无障碍自愈顺序**：应用每次回前台（`AppState.onResume()`）必须先 `repairAccessibilityIfNeeded()` 再自增 `resumeToken`；设置页「立即恢复」走同一个 `repairAccessibilityService()`。

## 钱迹导入与备份

- 文件选择采用「拉起 + 轮询」：`openImportPicker()` 拉起 `ImportPickActivity`（SAF `ACTION_GET_CONTENT`，无需存储权限），页面侧每 500ms 轮询 `getImportPickState()` 直到 `picked/cancelled/error`。
- xlsx 读取与账单语义分离：原生 `readImportSheet()` 只用 `ZipInputStream + XmlPullParser` 解压成「表头 + 原始单元格行」，`getImportSheetRows(offset, limit)` 分块取回；语义解析在 `data/Qianji.kt`。
- 备份导出用 `openBackupExport(content, name)` 拉起 `BackupSaveActivity`（SAF `ACTION_CREATE_DOCUMENT`），内容经进程内静态字段传递；`getBackupSaveState()` 轮询。`readBackupText()` 上限 50MB；`getAllBills()` 分页；`commitBackupBills()` 按 `id` / `orderNo` 去重。数据口径见 `40-data.md`。
