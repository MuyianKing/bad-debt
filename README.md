# 孽账

<p align="center"><img src="android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="88" alt="孽账 App 图标"></p>

Android 双渠道自动记账 App（无障碍为主 + 通知监听辅助）。技术栈：**Kotlin + Jetpack Compose**，系统能力与原生数据层集中在 `muyian/bill/listener/` 同包文件。

界面布局参考【钱迹】，配色与视觉按本项目的三主题重做。

> 构建、签名与数据迁移见 [android/README.md](android/README.md)；开发与 AI 协作的通用强约束见 [AGENTS.md](AGENTS.md)，按主题拆分的业务细则见 [docs/](docs/README.md)，改动前必读。

核心链路：支付结果页（无障碍采集窗口文字，主渠道）/ 支付类 App 通知（辅助渠道）→ 候选 App 白名单 → 解析金额/收支 → 单号/捕获级去重 → **底部确认弹层**（透明 Activity 从屏幕底部滑出，覆盖在支付页上方，可改金额/分类/备注）→ 用户点「保存」才入账。弹层不是悬浮窗、不落草稿：取消/返回/60 秒超时/弹层显示后熄屏都会直接丢弃这一笔；弹层从一开始就拉不起来（熄屏/锁屏、系统拦截后台启动 Activity）时，发一条「待确认」通知兜底，点开仍回到同一个确认弹层，通知本身不写库。注意 **MIUI/HyperOS 要求同时允许「显示在其他应用上层」和独立的「后台弹出界面」**（设置页拆成两项显示状态并各有入口，后者仅小米/Redmi/POCO 显示），否则系统会直接拦掉后台启动 Activity。主渠道不依赖对方 App 是否发系统通知，主动付款/转账也能捕获；辅助渠道覆盖收款到账提醒、熄屏碰卡交易通知。

## 目录结构

```
muyian/
└── android/                                  # Android Gradle 工程（唯一工程）
    ├── app/build.gradle.kts                  # compileSdk 36 / minSdk 23 / targetSdk 33
    ├── app/src/main/AndroidManifest.xml      # 权限、无障碍/通知监听服务、透明弹层 Activity
    └── app/src/main/java/
        ├── com/muyian/debt/
        │   ├── MainActivity.kt               # 单 Activity + Compose 导航 + 系统栏
        │   ├── data/                         # 账单/分类/设置/角色/钱迹/统计/备份/导入
        │   ├── state/                        # AppState 门面 + 主题/导航/弹层/设置子状态
        │   └── ui/
        │       ├── theme/Theme.kt            # 三主题设计令牌（Palette）
        │       ├── icons/                    # 24x24 图标路径与 Compose 绘制
        │       ├── components/               # 玻璃卡、角色、账单列表、弹层、滚轮、动效
        │       └── screens/                  # 首页/记一笔/统计/日历/分类/设置/识别记录/导入/备份
        └── muyian/bill/listener/              # 原生核心：Capture* 引擎 + hybrid.kt 门面 + BillDb/SystemPerms/XlsxReader/PendingNotices + Activities/Services
```

## 数据设计

SQLite `files/muyian.db`，表 `bills(id, year, month, day, clock, money, bankName, costType, orderRemark, source, sourcePkg, createdAt, reimb, starred, orderNo)`。
`money` 支出为负、收入为正；`source` 为 `screen`（支付页解析，主渠道）、`notify`（通知解析，辅助渠道与历史数据）或 `manual`（手动）；`reimb` 报销标记、`starred` 星标、`orderNo` 订单/交易单号（旧库自动 ALTER 补列）。账单在底部确认弹层点「保存」后入账；取消/返回/超时直接丢弃，不落任何草稿。
分类体系存本机 SharedPreferences（`muyian_native` / key `muyian_categories_v2`）：一级分类 + 子类，账单 `costType` 存叶子名，统计时归并到一级。

去重四层（单号优先，无单号才逐级退回）：

1. 单号级：同订单/交易单号 24 小时窗口（页面重复渲染、通知更新、screen/notify 双路互防）；
2. 捕获级：无单号时 `包名|金额|页面文本头` 5 分钟窗口（SharedPreferences 持久化）；
3. 弹层层：同渠道 + 金额 + 2 分钟窗口（弹层还没保存前，另一路来源不重复弹；两边单号不同则视为两笔）；
4. 账单级：同上，查已入账的自动来源记录（同样按单号区分同金额的两笔交易）。

## 如何运行

1. 安装 JDK 17+、Android SDK Platform 36 与 Build-Tools 36.0.0，在 `android/local.properties` 写入 `sdk.dir`；
2. `cd android && ./gradlew assembleDebug`，安装生成的 APK 到 Android 真机（必须真机，模拟器通知场景不真实）；
3. 启动后进「设置」页：
   - 点「去开启」跳转系统「无障碍」设置页，开启「孽账自动记账」（Android 13+ 侧载应用若开关为灰色，先在应用信息中允许「受限设置」）；
   - 可选：开启「通知使用权（辅助）」，覆盖收款到账提醒、熄屏碰卡交易通知；
   - 点「请求通知权限」（Android 13+），用于「后台保活」常驻通知和弹层拉不起来时的「待确认」通知；
   - **「显示在其他应用上层」和「后台弹出界面」都必须允许**：这是 MIUI/HyperOS 的两个独立开关，设置页分别显示状态（后者仅小米/Redmi/POCO 显示）；只开前者时付款后弹层会被系统直接拦掉，被拦后只能收到待确认通知（只开系统开关，不绘制悬浮窗）；
   - 在「后台运行」分组把「电池优化」设为「不限制」；需要常驻时保持「后台保活」开启（默认开启，前台服务 + 低优先级通知）；「从多任务页面隐藏」默认开启；国产 ROM 再按需在「自启动 / 后台管理」里允许自启动；
4. 真实验证：在支付宝/微信里支付或转账一笔，支付成功页面出现后 1 秒左右应从屏幕底部滑出记账弹层；点「保存」入账，点「取消」/返回键丢弃。熄屏/锁屏或未允许「后台弹出界面」时付款，应收到一条「待确认」通知；点通知仍进同一个确认弹层，取消后通知同步消失；
5. 首页按返回键不会退出应用，而是回到桌面继续在后台运行；再次打开即恢复。

## 构建注意

- UI 全部是 Jetpack Compose，不引入第三方 UI 库；原生核心只用 Android 框架 API + `org.json`，不引入网络/统计/云同步依赖。
- 无障碍服务 `muyian.bill.listener.MuyianBillAccessibilityService`、通知监听服务 `muyian.bill.listener.BillNotificationListener`、确认弹层 `muyian.bill.listener.BillSheetActivity` 都注册在 `android/app/src/main/AndroidManifest.xml`；无障碍配置在 `app/src/main/res/xml/muyian_accessibility_config.xml`。
- 改 `muyian/bill/listener/` 或清单后重新构建安装即可；换包名/换签名后无障碍、通知使用权、悬浮窗、MIUI「后台弹出界面」、电池优化白名单都要重新授权。
