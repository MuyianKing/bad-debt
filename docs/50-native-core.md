# 原生核心约束（hybrid.kt）

> 动 `android/app/src/main/java/muyian/bill/listener/hybrid.kt` 之前必读。改完重新构建安装即可，但会影响无障碍/通知监听等系统级行为，必须在真机上回归。

## API 约定

- 原生核心包名 `muyian.bill.listener`，对外入口是 `object MuyianBillCore`，方法全部 `@JvmStatic`、同步返回。复杂结构用 **JSON 字符串**传递，页面侧通过 `data/Bills.kt`、`data/Qianji.kt` 的解析函数消费。
- 新增原生能力直接在 `hybrid.kt` 里加 `@JvmStatic` 方法，并同步补 `data/` 侧的类型化封装；不要引入反射桥接或第三方依赖。
- Kotlin 侧只用 Android 框架 API + `org.json`，禁止引入第三方库。
- 核心职责：白名单/黑名单过滤、解析正则、去重（单号 24 小时 / 捕获级 5 分钟 / 弹层层 2 分钟 / 账单级 2 分钟 / 支付路线 15 分钟 / 宿主结果页指纹 24 小时）、底部确认弹层、后台保活、SAF 选文件、SQLite。改解析逻辑必须保持这些窗口语义。

## 底部确认弹层

- `BillSheetActivity` 是透明 `Activity`（`Theme.Translucent.NoTitleBar`），由无障碍服务 `startActivity` 拉起；**不用 `WindowManager` 绘制浮窗**，但 `android/app/src/main/AndroidManifest.xml` 必须声明 `SYSTEM_ALERT_WINDOW`——它对应「显示在其他应用上层」，不是用来画悬浮卡的，禁止删除。MIUI/HyperOS 还有独立的「后台弹出界面」AppOp（10021），两个开关都允许才能后台启动 Activity。
- 设置页拆成两项：`hasOverlayPermission` / `requestOverlayPermission` 管「显示在其他应用上层」，`getPopupPermissionState` / `openPopupPermissionSettings` 管「后台弹出界面」；后者只在 `miui = true`（小米/Redmi/POCO）时显示，op 10021 用隐藏的 `AppOpsManager.checkOpNoThrow(int, ...)` 读取，读不到时用「亮屏但弹层被系统拦住」的持久化标记（`popup_blocked_at`，7 天）兜底，避免把「未检测」误报成「已允许」。
- 布局全部用 Android 框架 View，禁止引入 UI 库；分类只读账单里的 `costType` 字符串，原生仍不感知分类体系。必须有 60 秒超时、熄屏收起、返回键取消；取消/超时/弹层显示后熄屏一律丢弃这一笔，不落草稿。
- **弹层从一开始就拉不起来时（熄屏/锁屏、ROM 拦截、启动看门狗 5 秒超时），由 `MuyianBillCore.postPendingNotice()` 发一条高优先级「待确认」通知兜底**：通知携带 `EXTRA_BILL` / `EXTRA_CATS` / `EXTRA_ACCENT` / `EXTRA_NOTICE_ID`，点击用 `PendingIntent` 直接打开 `BillSheetActivity`（`VISIBILITY_PRIVATE` + 不含金额的 `publicVersion` 防锁屏泄露），确认仍只能在弹层里点保存；`BillSheetActivity` 创建/销毁时取消对应通知，避免取消后还残留。
- `android:taskAffinity=""` + `excludeFromRecents="true"` 让它独立成任务且不进最近任务。**不要再引入 `DraftCard` / `WindowManager` 浮窗 / 兜底草稿 / `isOverlayEnabled` 开关；待确认通知只做弹层入口，不得在通知里写库或提供「直接入账」入口。**

## 弹层后台启动 Activity 的前提

- AOSP 对无障碍/通知监听这类系统绑定服务有后台启动 Activity 的豁免，但 **MIUI/HyperOS 不认这个豁免**，它额外要求「显示在其他应用上层」（`SYSTEM_ALERT_WINDOW`）和「后台弹出界面」（MIUI AppOp 10021）都已允许；只开前者时 `startActivity` 会被系统直接 Abort，日志为 `MIUILOG- Permission Denied Activity` + `Abort background activity starts`。
- 弹层只能由无障碍/通知监听服务拉起，且**熄屏/锁屏时不要拉起**（会失败或无意义），此时改发通知兜底，不再直接丢弃这一笔。

## 支付路线与跨 App 去重

- 无障碍窗口切换通过 `noteForeground()` 维护最近 15 分钟的 App 路线，只记录包名与来源包名，不记录页面文本；`isPaymentApp()` 区分支付 App（微信/支付宝/云闪付/银行）与宿主 App（美团/点评/京东等）。系统弹窗、权限页不参与路线。
- 跨 App 同一笔支付由 `presentBill()` 里的 `findMatchingFlowLocked()` 合并：宿主 → 支付 → 宿主路线上，金额与收支方向一致即视为同一笔，即使两边 `bankName`、`orderNo` 完全不同；合并时保留先到的弹层，支付 App 的金额/渠道覆盖宿主 App 的默认值。同 App 的重复仍走原有单号/捕获级/账单级规则，避免同金额的两笔真实消费被误合并。
- 宿主 App 的屏幕/通知结果页要求 `hasHostPaymentContext()`：刚从有支付动作的支付 App 返回 3 分钟内，或点过收银台/提交订单 10 分钟内，或已有该宿主的支付流水。另有 `SCREEN_ORDER_MGMT_PATTERN` 过滤订单管理页（申请退款/查看物流/确认收货/待评价/已送达等），避免翻历史订单误弹。宿主结果页写入 24 小时指纹（`KEY_HOST_PAGE_DEDUP`），期间没有新的支付动作就不再弹。
- 采集前必须校验 `rootInActiveWindow.packageName` 与事件包名一致：切回宿主 App 的一瞬间 root 可能还停在支付 App，直接采信会把支付结果页算到宿主头上，造成重复弹层。
- `flow` 状态落盘在 `capture_flows`，进程被杀后 15 分钟内仍能跨 App 去重。锁顺序：路线状态只在 `routeLock` 内访问，`flows`/弹层队列只在 `sheetLock` 内访问；先取 `routeLock`、释放后再取 `sheetLock`，禁止反向嵌套。

## hybrid.kt 写法与兼容

- 只用 Android 框架 API + `org.json`；新增系统能力优先用公开 API，兼容旧系统时保留版本判断分支。
- 弃用 API（`AccessibilityNodeInfo.recycle()`、`stopForeground(boolean)`、`Notification.Builder(Context)` / `setPriority()`、`startActivityForResult()`、`SOFT_INPUT_ADJUST_RESIZE`、`statusBarColor` / `navigationBarColor`）必须保留原有版本判断分支，不要强行升级；调用处用 `@Suppress("DEPRECATION")` 标注。重写弃用成员（如 `onBackPressed()`）要用 `@Suppress("OVERRIDE_DEPRECATION")`。
- 同步块写成独立语句，配合局部变量或辅助方法（如 `showNextSheet()` / `onSheetClosed()`），不要直接赋给 `val`。
- 无障碍服务注册名固定全限定名 `muyian.bill.listener.MuyianBillAccessibilityService`，配置在 `app/src/main/res/xml/muyian_accessibility_config.xml`；辅助通知监听服务为 `muyian.bill.listener.BillNotificationListener`；两者都在 `android/app/src/main/AndroidManifest.xml` 注册。
- **无障碍自愈顺序**：MIUI/HyperOS 在强停/清理应用时会把服务从 `enabled_accessibility_services` 里移除。设置页/首页需要检测状态时，必须**先 `repairAccessibilityService()` 再 `isAccessibilityEnabled()`**，自愈成功时同步把 UI 状态置为已开启；顺序反了会出现「服务已静默补回、界面却一直挂着未开启」的误报。`getSelfRepairState()` 只读展示自愈能力状态。

## 金额择取

- **金额择取规则（改解析时必须遵守）**：`pickAmount()` 按「强锚点 → App 专属锚点 → 弱锚点 → 独立大字金额行 → 带货币单位上下文」逐级择取；含余额/额度/优惠/原价/积分/红包等排除词的数字不得进入候选。弱锚点要求带小数点或货币单位（防「支付时间2026-10-26」被当金额），强锚点不受同一行前置排除词影响（「账户余额X，本次支出38.50」要能取到 38.50）。新增 App 只改 `AMOUNT_RULES` / `AMOUNT_RULE_PREFIXES` 规则表，不要在主流程里写分支。

## 钱迹导入 API

- 文件选择不是同步回调能表达的能力，采用**「拉起 + 轮询」**：`openImportPicker()` 拉起透明中转 Activity `muyian.bill.listener.ImportPickActivity`（SAF `ACTION_GET_CONTENT`，无需存储权限，选中的文件拷到私有目录 `qianji_import.xlsx`），页面侧每 500ms 轮询 `getImportPickState()` 直到 `picked/cancelled/error`；`ImportPickActivity.isAlive()` 为 false 时 `openImportPicker` 允许重新拉起，避免卡死在 `picking` 态。
- xlsx 读取与账单语义分离：原生 `readImportSheet()` 只用 `ZipInputStream + XmlPullParser`（`android.util.Xml`）把 `xl/sharedStrings.xml` 与第一个 `xl/worksheets/*.xml` 解压成「表头 + 原始单元格行」，缓存在内存 `importSheetRows`（上限 2 万行、64 列），`getImportSheetRows(offset, limit)` 分块取回；时间/类型/金额校验、汇总统计、分类计数、钱迹分类 → 叶子名映射、入库字段拼装在 `data/Qianji.kt`。保留原生读表的原因：App 端没有可靠的 zip/XML API，且项目不引入第三方解压库；这属于文件格式读取能力，不是业务规则。`countExistingOrders(orderNosJson)` 只做库内单号计数，`commitImportBills(json)` 单事务按 `orderNo` 去重后批量插入。
- 本应用备份复用同一套「拉起 + 轮询」模式：`openBackupPicker()` 用同一个 `ImportPickActivity`（`EXTRA_KIND="json"`，拷到 `files/muyian_backup.json`），`getImportPickState()` 轮询；导出用 `openBackupExport(content, name)` 拉起 `BackupSaveActivity`（SAF `ACTION_CREATE_DOCUMENT`），内容经进程内静态字段传递、不走 Intent，`getBackupSaveState()` 轮询结果。`readBackupText()` 读回文本（上限 50MB），`getAllBills()` 分页读全量，`commitBackupBills()` 按 `id` / `orderNo` 去重批量入库；数据口径见 `40-data.md` 的「本应用备份」一节。
