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
    └─ 生成待确认草稿 ──> 系统通知"待确认 · 支出 xx元"
                           └─ 点击打开App，在首页确认入账
```

## 设计要点

- **主渠道覆盖主动付款/转账**：只要支付完成页面出现在屏幕上就会被解析，不依赖对方 App 是否发系统通知
- **辅助渠道覆盖通知栏场景**：收款到账提醒只在通知栏、熄屏碰卡（NFC 卡模拟）交易通知；两路去重互防，不会重复入账
- **解析/去重/存储全部在原生层完成**，不依赖 JS 层存活；UTS 层只是薄封装
- **草稿携带 merchant 字段**（提取的商户名，可能为空串）：原生不感知分类体系，分类建议由页面侧
  `common/merchants.js` 的词典 + 用户修正回流决定
- **草稿不自动入账**：解析成功只发"待确认"通知，用户点开确认后才写库，错误可拦截
- **数据全本地**：SQLite（`files/muyian.db`）存账单，SharedPreferences 存草稿，不经服务端
- **JSON 字符串作为跨语言协议**：规避 UTS number 与 Kotlin Int/Double 的类型映射问题

## 服务注册

- 主渠道：`muyian.bill.listener.MuyianBillAccessibilityService`，注册在插件 `AndroidManifest.xml`，
  配置 `res/xml/muyian_accessibility_config.xml`（监听 `typeWindowStateChanged|typeWindowContentChanged`，
  `canRetrieveWindowContent=true`，不限定 packageNames，白名单在代码里过滤）
- 辅助渠道：`muyian.bill.listener.BillNotificationListener`，同在插件 `AndroidManifest.xml` 注册
- 两个服务均以全限定名声明，不依赖 UTS 生成包名的约定

## 数据库

`bills(id, year, month, day, clock, money, bankName, costType, orderRemark, source, sourcePkg, createdAt, reimb, starred)`

- `money`：支出为负、收入为正
- `source`：`screen`（支付页解析，主渠道）/ `notify`（通知解析，辅助渠道与历史数据）/ `manual`（手动记账）

## 已知边界

- 熄屏碰卡（NFC 卡模拟）且银行/云闪付不发交易通知时，两个渠道都看不到
- 各家 App 页面改版可能导致解析失败，属预期内的持续适配点

## 运行要求

- HBuilderX 云打包/自定义基座会合并插件 `AndroidManifest.xml` 与 `res/` 资源
- 用户需在系统"设置 → 无障碍"中开启"孽账自动记账"（主渠道）；可选开启"通知使用权"（辅助渠道）
- Android 13+ 侧载应用：无障碍开关若为灰色，需先在应用信息中允许"受限设置"
