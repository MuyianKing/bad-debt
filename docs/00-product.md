# 产品定位与红线

> 通用约束见根目录 `AGENTS.md`。本文件是产品侧不可协商的底线，任何功能需求都不得绕过。

## 定位

- Android 无障碍自动记账 App：**uni-app（vue3）+ wot-design-uni + UTS 插件（hybrid.kt 原生核心）**。

## 解析成功不等于入账

- **解析成功的账单不自动入账**：检测到支付后从屏幕底部滑出确认弹层（`BillSheetActivity`，透明 Activity），用户点"保存"才写库；取消/返回/60 秒超时/熄屏都直接丢弃这一笔。弹层不是 WindowManager 悬浮窗，但 **MIUI/HyperOS 把"后台启动 Activity"绑在 `SYSTEM_ALERT_WINDOW` 上**，manifest 必须声明该权限并在设置页引导「后台弹出界面」，否则 `startActivity` 会被系统直接 Abort（日志：`MIUILOG- Permission Denied Activity` / `Abort background activity starts`）。**不再有悬浮卡、兜底草稿、待确认通知**，任何改动不得重新引入这些旁路，也不得绕过确认直接入账。

## 隐私红线

- **隐私红线**：通知/窗口文本只在手机本地解析，数据只存本机 SQLite，禁止引入任何上传、统计 SDK 或云同步代码（云同步在路线图里，且需单独实现）。

## 采集通道

- 自动采集通道以**无障碍服务**（AccessibilityService）为主渠道；**通知监听**（NotificationListenerService）为可选辅助渠道（须用户在设置页单独授权），仅用于覆盖无障碍盲区（收款到账提醒只在通知栏、熄屏碰卡交易通知）。两路来源靠单号/捕获级/账单级去重互防重复（单号优先）；`notify` source 同时表示辅助渠道与历史数据。禁止把通知监听改回唯一渠道。
