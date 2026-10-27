# UTS 插件约束（muyian-bill-listener）

> 动 `uni_modules/muyian-bill-listener` 之前必读。改完 `hybrid.kt` / `interface.uts` / `index.uts` 都要重做自定义调试基座。

## API 约定

- 跨语言 API 一律**同步调用 + JSON 字符串进出**，页面侧 `JSON.parse`。这是为了规避 UTS number ↔ Kotlin Int/Double 映射坑；新增 API 禁止直接传对象/数值结构。
- 新增原生能力必须**三个文件同步改**：`interface.uts`（声明）→ `app-android/index.uts`（薄封装 + context 判空兜底返回错误 JSON）→ `app-android/hybrid.kt`（Kotlin 实现，@JvmStatic）。
- Kotlin 侧只用 Android 框架 API + org.json，禁止引入第三方依赖。
- `hybrid.kt` 是核心：白名单/黑名单过滤、解析正则、去重（单号 24 小时 / 捕获级 5 分钟 / 弹层层 2 分钟 / 账单级 2 分钟）、底部确认弹层。改解析逻辑必须保持这些窗口语义。

## 底部确认弹层

- **底部确认弹层约束**：`BillSheetActivity` 是透明 `Activity`（`Theme.Translucent.NoTitleBar`），由无障碍服务 `startActivity` 拉起；**不用 `WindowManager` 绘制浮窗**，但 `manifest.json` 必须声明 `SYSTEM_ALERT_WINDOW`——它对应「显示在其他应用上层」，不是用来画悬浮卡的，禁止删除。MIUI/HyperOS 还有独立的「后台弹出界面」AppOp（10021），两个开关都允许才能后台启动 Activity。设置页拆成两项：`hasOverlayPermission`/`requestOverlayPermission` 管「显示在其他应用上层」，`getPopupPermissionState`/`openPopupPermissionSettings` 管「后台弹出界面」；后者只在 `miui=true`（小米/Redmi/POCO）时显示，op 10021 用隐藏的 `AppOpsManager.checkOpNoThrow(int,...)` 读取，读不到时用「亮屏但弹层被系统拦住」的持久化标记（`popup_blocked_at`，7 天）兜底，避免把「未检测」误报成「已允许」。布局全部用 Android 框架 View，禁止引入 UI 库；分类只读账单里的 `costType` 字符串，原生仍不感知分类体系。必须有 60 秒超时、熄屏收起、返回键取消；取消/超时/弹层显示后熄屏一律丢弃这一笔，不落草稿。**弹层从一开始就拉不起来时（熄屏/锁屏、ROM 拦截、启动看门狗 5 秒超时），由 `MuyianBillCore.postPendingNotice()` 发一条高优先级「待确认」通知兜底**：通知携带 `EXTRA_BILL`/`EXTRA_CATS`/`EXTRA_ACCENT`/`EXTRA_NOTICE_ID`，点击用 `PendingIntent` 直接打开 `BillSheetActivity`（`VISIBILITY_PRIVATE` + 不含金额的 `publicVersion` 防锁屏泄露），确认仍只能在弹层里点保存；`BillSheetActivity` 创建/销毁时取消对应通知，避免取消后还残留。`android:taskAffinity=""` + `excludeFromRecents="true"` 让它独立成任务且不进最近任务。**不要再引入 `DraftCard` / `WindowManager` 浮窗 / 兜底草稿 / `isOverlayEnabled` 开关；待确认通知只做弹层入口，不得在通知里写库或提供「直接入账」入口。**
- **弹层后台启动 Activity 的前提**：AOSP 对无障碍/通知监听这类系统绑定服务有后台启动 Activity 的豁免，但 **MIUI/HyperOS 不认这个豁免**，它额外要求「显示在其他应用上层」（`SYSTEM_ALERT_WINDOW`）和「后台弹出界面」（MIUI AppOp 10021）都已允许；只开前者时 `startActivity` 会被系统直接 Abort，日志为 `MIUILOG- Permission Denied Activity` + `Abort background activity starts`。弹层只能由无障碍/通知监听服务拉起，且**熄屏/锁屏时不要拉起**（会失败或无意义），此时改发通知兜底，不再直接丢弃这一笔。

## hybrid.kt 写法限制

- **hybrid.kt 的写法限制（HBuilderX 的 UTS Kotlin 语法检查比 kotlinc 严格，踩过的坑）**：① 不要把 `synchronized(lock) { ... }` 直接赋给 `val`——检查器会当成 Java synchronized 块，紧跟着的 `if` 报 `no viable alternative`；同步块统一写成独立语句，配合局部变量或辅助方法（如 `showNextSheet()` / `onSheetClosed()`）。② 块尾不要用 `!expr` 开头的一元表达式（报 `Syntax error: Expecting an element.`），改用 `x == false` 或二元表达式结尾。③ `ScrollView.LayoutParams` 这类嵌套类在 UTS 检查器里报"找不到名称 LayoutParams"，改用 `FrameLayout.LayoutParams`（ScrollView 本质是 FrameLayout）。④ 插件 `utssdk/app-android/AndroidManifest.xml` 根节点必须带 `package="uts.sdk.modules.muyianBillListener"`，否则 aapt2 链接报 `<manifest> must have a 'package' attribute`。改完用 `cli.exe launch app-android --project <项目绝对路径> --compile true --playground custom --customType local` 做无界面编译自检（缺 `--project` 会报"参数 project 值不能为空"）。⑤ 兼容旧系统的弃用 API（`AccessibilityNodeInfo.recycle()`、`stopForeground(boolean)`、`Notification.Builder(Context)` / `setPriority()`、`startActivityForResult()`、`SOFT_INPUT_ADJUST_RESIZE`、`statusBarColor` / `navigationBarColor`）必须保留原有版本判断分支，不要升级到新 API；调用处用 `@Suppress("DEPRECATION")` 标注消除编译警告，若标注在分支内表达式上不生效（如构造函数），改为标注整个函数；重写弃用成员（如 `onBackPressed()`）要用 `@Suppress("OVERRIDE_DEPRECATION")`，`DEPRECATION` 压不住这条。
- **编译自检的两个命令**：只改插件时优先用 `cli.exe compile app-android --project <项目绝对路径> --uni_module muyian-bill-listener`（只编插件，HBuilderX IDE 开着也能跑，几十秒出结果）。`launch app-android --compile true` 需要干净的运行状态，IDE 里已有运行会话时会瞬间报「运行状态错误，请重试」，此时关掉 IDE 的运行/预览再试；它的 R.jar 缓存与 IDE 共用，报 `R.jar 另一个程序正在使用` 时等 IDE 空闲后重试。

## 金额择取

- **金额择取规则（改解析时必须遵守）**：`pickAmount()` 按"强锚点 → App 专属锚点 → 弱锚点 → 独立大字金额行 → 带货币单位上下文"逐级择取；含余额/额度/优惠/原价/积分/红包等排除词的数字不得进入候选。弱锚点要求带小数点或货币单位（防"支付时间2026-10-26"被当金额），强锚点不受同一行前置排除词影响（"账户余额X，本次支出38.50"要能取到 38.50）。新增 App 只改 `AMOUNT_RULES` / `AMOUNT_RULE_PREFIXES` 规则表，不要在主流程里写分支。

## 服务注册与 Web 桩

- 无障碍服务注册名固定全限定名 `muyian.bill.listener.MuyianBillAccessibilityService`，配置在插件 `res/xml/muyian_accessibility_config.xml`；辅助通知监听服务为 `muyian.bill.listener.BillNotificationListener`。
- **必须保留 `utssdk/web/index.js` 桩**：vite 依赖扫描（dep-scan）不识别 `#ifdef`，H5 编译会解析 `@/uni_modules/muyian-bill-listener` 导入，缺 web 桩直接报 resolve 错误。桩的导出签名与 interface.uts 保持一致（JSON 字符串进出）。

## 钱迹导入 API

- 文件选择不是同步回调能表达的能力，采用**「拉起 + 轮询」**：`openImportPicker()` 拉起透明中转 Activity `muyian.bill.listener.ImportPickActivity`（SAF `ACTION_GET_CONTENT`，无需存储权限，选中的文件拷到私有目录 `qianji_import.xlsx`），页面侧每 500ms 轮询 `getImportPickState()` 直到 `picked/cancelled/error`；`ImportPickActivity.isAlive()` 为 false 时 `openImportPicker` 允许重新拉起，避免卡死在 `picking` 态。
- xlsx 读取与账单语义分离：原生 `MuyianBillCore.readImportSheet()` 只用 `ZipInputStream + XmlPullParser`（`android.util.Xml`）把 `xl/sharedStrings.xml` 与第一个 `xl/worksheets/*.xml` 解压成「表头 + 原始单元格行」，缓存在内存 `importSheetRows`（上限 2 万行、64 列），`getImportSheetRows(offset, limit)` 分块取回；时间/类型/金额校验、汇总统计、分类计数、钱迹分类 → 叶子名映射、入库字段拼装在 `common/qianji-import.js`。保留原生读表的原因：App 端 JS 运行时没有可靠的 zip/XML API，且项目不引入第三方解压库；这属于文件格式读取能力，不是业务规则。`countExistingOrders(orderNosJson)` 只做库内单号计数，`commitImportBills(json)` 单事务按 `orderNo` 去重后批量插入。
- 页面路径 `pages/import/import`；数据口径见 `40-data.md` 的「钱迹导入」一节。
- 本应用备份复用同一套「拉起 + 轮询」模式：`openBackupPicker()` 用同一个 `ImportPickActivity`（`EXTRA_KIND="json"`，拷到 `files/muyian_backup.json`），`getImportPickState()` 轮询；导出用 `openBackupExport(content, name)` 拉起 `BackupSaveActivity`（SAF `ACTION_CREATE_DOCUMENT`），内容经进程内静态字段传递、不走 Intent，`getBackupSaveState()` 轮询结果。`readBackupText()` 读回文本（上限 50MB），`getAllBills()` 分页读全量，`commitBackupBills()` 按 `id`/`orderNo` 去重批量入库；页面路径 `pages/backup/backup`，口径见 `40-data.md` 的「本应用备份」一节。
