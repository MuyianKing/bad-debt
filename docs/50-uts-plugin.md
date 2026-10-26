# UTS 插件约束（muyian-bill-listener）

> 动 `uni_modules/muyian-bill-listener` 之前必读。改完 `hybrid.kt` / `interface.uts` / `index.uts` 都要重做自定义调试基座。

## API 约定

- 跨语言 API 一律**同步调用 + JSON 字符串进出**，页面侧 `JSON.parse`。这是为了规避 UTS number ↔ Kotlin Int/Double 映射坑；新增 API 禁止直接传对象/数值结构。
- 新增原生能力必须**三个文件同步改**：`interface.uts`（声明）→ `app-android/index.uts`（薄封装 + context 判空兜底返回错误 JSON）→ `app-android/hybrid.kt`（Kotlin 实现，@JvmStatic）。
- Kotlin 侧只用 Android 框架 API + org.json，禁止引入第三方依赖。
- `hybrid.kt` 是核心：白名单/黑名单过滤、解析正则、去重（单号 24 小时 / 捕获级 5 分钟 / 弹层层 2 分钟 / 账单级 2 分钟）、底部确认弹层。改解析逻辑必须保持这些窗口语义。

## 底部确认弹层

- **底部确认弹层约束**：`BillSheetActivity` 是透明 `Activity`（`Theme.Translucent.NoTitleBar`），由无障碍服务 `startActivity` 拉起；**不用 `WindowManager` 绘制浮窗**，但 `manifest.json` 必须声明 `SYSTEM_ALERT_WINDOW`——它是 MIUI/HyperOS 允许"后台启动 Activity"的系统开关（设置页「后台弹出界面」，API `hasOverlayPermission`/`requestOverlayPermission`），不是用来画悬浮卡的，禁止删除。布局全部用 Android 框架 View，禁止引入 UI 库；分类只读账单里的 `costType` 字符串，原生仍不感知分类体系。必须有 60 秒超时、熄屏收起、返回键取消；取消/超时/熄屏一律丢弃，不落草稿、不发通知。`android:taskAffinity=""` + `excludeFromRecents="true"` 让它独立成任务且不进最近任务。**不要再引入 `DraftCard` / `WindowManager` 浮窗 / 兜底草稿 / 待确认通知 / `isOverlayEnabled` 开关。**
- **弹层后台启动 Activity 的前提**：AOSP 对无障碍/通知监听这类系统绑定服务有后台启动 Activity 的豁免，但 **MIUI/HyperOS 不认这个豁免**，它额外要求 `SYSTEM_ALERT_WINDOW`（"显示在其他应用上层/后台弹出界面"）已允许；未允许时系统日志为 `MIUILOG- Permission Denied Activity` + `Abort background activity starts`。弹层只能由无障碍/通知监听服务拉起，且**熄屏/锁屏时不要拉起**（会失败或无意义），当前实现直接丢弃这一笔。

## hybrid.kt 写法限制

- **hybrid.kt 的写法限制（HBuilderX 的 UTS Kotlin 语法检查比 kotlinc 严格，踩过的坑）**：① 不要把 `synchronized(lock) { ... }` 直接赋给 `val`——检查器会当成 Java synchronized 块，紧跟着的 `if` 报 `no viable alternative`；同步块统一写成独立语句，配合局部变量或辅助方法（如 `showNextSheet()` / `onSheetClosed()`）。② 块尾不要用 `!expr` 开头的一元表达式（报 `Syntax error: Expecting an element.`），改用 `x == false` 或二元表达式结尾。③ `ScrollView.LayoutParams` 这类嵌套类在 UTS 检查器里报"找不到名称 LayoutParams"，改用 `FrameLayout.LayoutParams`（ScrollView 本质是 FrameLayout）。④ 插件 `utssdk/app-android/AndroidManifest.xml` 根节点必须带 `package="uts.sdk.modules.muyianBillListener"`，否则 aapt2 链接报 `<manifest> must have a 'package' attribute`。改完用 `cli.exe launch app-android --project <项目绝对路径> --compile true --playground custom --customType local` 做无界面编译自检（缺 `--project` 会报"参数 project 值不能为空"）。

## 金额择取

- **金额择取规则（改解析时必须遵守）**：`pickAmount()` 按"强锚点 → App 专属锚点 → 弱锚点 → 独立大字金额行 → 带货币单位上下文"逐级择取；含余额/额度/优惠/原价/积分/红包等排除词的数字不得进入候选。弱锚点要求带小数点或货币单位（防"支付时间2026-10-26"被当金额），强锚点不受同一行前置排除词影响（"账户余额X，本次支出38.50"要能取到 38.50）。新增 App 只改 `AMOUNT_RULES` / `AMOUNT_RULE_PREFIXES` 规则表，不要在主流程里写分支。

## 服务注册与 Web 桩

- 无障碍服务注册名固定全限定名 `muyian.bill.listener.MuyianBillAccessibilityService`，配置在插件 `res/xml/muyian_accessibility_config.xml`；辅助通知监听服务为 `muyian.bill.listener.BillNotificationListener`。
- **必须保留 `utssdk/web/index.js` 桩**：vite 依赖扫描（dep-scan）不识别 `#ifdef`，H5 编译会解析 `@/uni_modules/muyian-bill-listener` 导入，缺 web 桩直接报 resolve 错误。桩的导出签名与 interface.uts 保持一致（JSON 字符串进出）。
