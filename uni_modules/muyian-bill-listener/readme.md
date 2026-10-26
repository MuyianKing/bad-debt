# muyian-bill-listener

Android 双渠道自动记账核心插件（仅支持 Android，iOS 无对应系统能力）：**无障碍为主渠道 + 通知监听为辅助渠道**。

## 架构

```
主渠道：支付结果页（支付宝/微信/银行App 内）
    │  无障碍事件（窗口切换/内容变化）
    ▼
MuyianBillAccessibilityService (hybrid.kt, AccessibilityService)
    │  节流(600ms/App) + 页面文本快照 + 签名去重
    │                                    辅助渠道：收款到账/碰卡交易通知
    │                                    │ 系统通知栏
    │                                    ▼
    │                          BillNotificationListener (NotificationListenerService)
    ▼                                      │
MuyianBillCore.handleScreen / handleNotification（后台线程）
    ├─ 候选App白名单（包名/前缀/应用名关键词三层匹配，带缓存）
    ├─ 黑名单（待支付/处理中/账单列表页等整页跳过）
    ├─ 解析：结果页走"支付成功"标记 + 大字金额行；通知走关键词 + 货币正则（匹配前剔除"支付宝/微信支付"应用名，防收款误判为支出）
    ├─ 捕获级去重（App+金额+文本头，5分钟窗口，SharedPreferences持久化）
    ├─ 账单级去重（同渠道+金额，2分钟窗口，查SQLite，screen/notify 两路互防重复）
    └─ 生成解析结果 ──> BillSheetActivity（透明 Activity，从底部滑出确认弹层）
                          ├─ 保存 → 写 SQLite
                          └─ 取消/返回/超时/熄屏 → 直接丢弃（不落草稿、不发通知）
```

## 设计要点

- **主渠道覆盖主动付款/转账**：只要支付完成页面出现在屏幕上就会被解析，不依赖对方 App 是否发系统通知
- **辅助渠道覆盖通知栏场景**：收款到账提醒只在通知栏、熄屏碰卡（NFC 卡模拟）交易通知；两路去重互防，不会重复入账
- **解析/去重/存储全部在原生层完成**，不依赖 JS 层存活；UTS 层只是薄封装
- **确认弹层不是悬浮窗**：它是透明 Activity，由无障碍服务直接拉起；但 MIUI/HyperOS 把"后台启动 Activity"绑在 `SYSTEM_ALERT_WINDOW`（显示在其他应用上层/后台弹出界面）上，未允许时会被系统 Abort，所以仍需在设置页引导用户开启这个系统开关
- **不自动入账、也不落草稿**：用户点"保存"才写库；取消/返回/60 秒超时/熄屏都直接丢弃
- **数据全本地**：SQLite（`files/muyian.db`）存账单，不经服务端
- **JSON 字符串作为跨语言协议**：规避 UTS number 与 Kotlin Int/Double 的类型映射问题

## 服务注册

- 主渠道：`muyian.bill.listener.MuyianBillAccessibilityService`，注册在插件 `AndroidManifest.xml`，
  配置 `res/xml/muyian_accessibility_config.xml`（监听 `typeWindowStateChanged|typeWindowContentChanged`，
  `canRetrieveWindowContent=true`，不限定 packageNames，白名单在代码里过滤）
- 辅助渠道：`muyian.bill.listener.BillNotificationListener`，同在插件 `AndroidManifest.xml` 注册
- 确认弹层：`muyian.bill.listener.BillSheetActivity`，透明 Activity（`Theme.Translucent.NoTitleBar`），`taskAffinity=""` + `excludeFromRecents="true"`
- 三个组件均以全限定名声明，不依赖 UTS 生成包名的约定

## 数据库

`bills(id, year, month, day, clock, money, bankName, costType, orderRemark, source, sourcePkg, createdAt, reimb, starred)`

- `money`：支出为负、收入为正
- `source`：`screen`（支付页解析，主渠道）/ `notify`（通知解析，辅助渠道与历史数据）/ `manual`（手动记账）

## 已知边界

- 熄屏碰卡（NFC 卡模拟）且银行/云闪付不发交易通知时，两个渠道都看不到
- **熄屏/锁屏时不会拉起确认弹层**：这一笔按当前策略直接丢弃（没有草稿/通知兜底）
- 部分 ROM 会拦截"后台启动 Activity"，若弹层没有出现，先确认无障碍服务处于开启状态（无障碍服务是后台启动 Activity 的豁免条件）
- 各家 App 页面改版可能导致解析失败，属预期内的持续适配点

## 运行要求

- HBuilderX 云打包/自定义基座会合并插件 `AndroidManifest.xml` 与 `res/` 资源
- 用户需在系统"设置 → 无障碍"中开启"孽账自动记账"（主渠道）；可选开启"通知使用权"（辅助渠道）
- Android 13+ 侧载应用：无障碍开关若为灰色，需先在应用信息中允许"受限设置"

## 后台保活

- `MuyianKeepAliveService`：前台服务 + 最低优先级常驻通知维持进程（`START_STICKY`），App 启动时默认拉起，可在设置页「后台运行 → 后台保活」关闭
- 电池优化：设置页提供「电池优化」入口，调用 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 弹系统"不限制"确认框；不支持该 Intent 的 ROM 退化为电池优化列表页
- 国产 ROM 的自启动/后台管理入口不统一，设置页「自启动 / 后台管理」打开应用详情页供用户手动允许
- 首页按返回键不退出应用：`moveTaskToBack(true)` 把任务移到后台回到桌面（抽屉展开时先收起抽屉）
- 相关权限：`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
