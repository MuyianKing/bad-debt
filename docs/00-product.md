# 产品定位与红线

> 通用约束见根目录 `AGENTS.md`。本文件是产品侧不可协商的底线，任何功能需求都不得绕过。

## 定位

- Android 无障碍自动记账 App：**Kotlin + Jetpack Compose**，系统能力与原生数据层在 `muyian/bill/listener/` 同包文件（包名 `muyian.bill.listener`）。

## 解析成功不等于入账

- **解析成功的账单不自动入账**：检测到支付后从屏幕底部滑出确认弹层（`BillSheetActivity`，透明 Activity），用户点「保存」才写库；取消/返回/60 秒超时，以及弹层已经显示后的熄屏，都直接丢弃这一笔。弹层从一开始就没能拉起（熄屏/锁屏、ROM 拦截后台 Activity、启动超时）时，改发一条「待确认」通知兜底；点通知仍打开同一个确认弹层，通知本身不写库、没有直接入账入口。弹层不是 WindowManager 悬浮窗，但 **MIUI/HyperOS 要求「显示在其他应用上层」（`SYSTEM_ALERT_WINDOW`）和独立的「后台弹出界面」（MIUI AppOp 10021）都允许**，只开前者时系统日志为 `MIUILOG- Permission Denied Activity` / `Abort background activity starts`；`android/app/src/main/AndroidManifest.xml` 必须声明 `SYSTEM_ALERT_WINDOW`，设置页分别展示两项状态（「后台弹出界面」仅小米/Redmi/POCO 显示）并提供对应授权入口。**不再有悬浮卡、兜底草稿；待确认通知只允许作为弹层拉不起来时的入口，不得演化成旁路**，任何改动不得绕过确认直接入账。

## 隐私红线

- **隐私红线**：通知/窗口文本只在手机本地解析，数据只存本机 SQLite 与 SharedPreferences，禁止引入任何上传、统计 SDK 或云同步代码（云同步在路线图里，且需单独实现）。

## 采集通道

- 自动采集通道以**无障碍服务**（AccessibilityService）为主渠道；**通知监听**（NotificationListenerService）为可选辅助渠道（须用户在设置页单独授权），仅用于覆盖无障碍盲区（收款到账提醒只在通知栏、熄屏碰卡交易通知）。两路来源统一进 `CaptureEngine`：先判页面类型（成功/收银台/列表/订单管理/营销/聊天/失败/处理中），再取金额与方向，最后由 `capture_ledger` 用「单号 > 支付会话 > 宿主页面 > 捕获指纹 > 渠道+金额」原子去重。`notify` source 同时表示辅助渠道与历史数据。禁止把通知监听改回唯一渠道。
- **支付路线去重**：一笔支付在宿主 App（美团/点评/京东等）和支付 App（支付宝/微信/云闪付/银行）两个结果页各出现一次时，两边的 `bankName` 和订单号都不同，不能只靠金额或单号判断。`CaptureRoute` 记录 15 分钟的 `HOST → PAY_ENTERED → PAY_EVIDENCE → RETURNED` 状态（落盘，进程重启可恢复）；同一次会话内的两次捕获合并为一笔，宿主结果页只在「3 分钟内刚从有支付动作的支付 App 返回」或「10 分钟内点过收银台/提交订单」时接受，平时打开 App 翻订单页不会弹层。宿主结果页另有 24 小时页面指纹兜底。
- **误报优先级**：聊天/公众号营销/订单列表/失败/处理中页面一律不弹；本人账户互转（提现/充值/余额宝/零钱通）与信用卡还款默认不记账，可在「识别记录与诊断」里开启。所有判定与拒绝原因只在本机留痕，不上传。
