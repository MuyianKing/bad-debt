# 自动记账引擎设计（Capture Engine）

> 状态：已实施（P0–P5）。本文既描述目标架构，也作为实施记录；第 14 节列出实际落地顺序与偏差。实施不得突破 `00-product.md` 的确认弹层、隐私与采集通道红线；去重窗口语义以 `50-native-core.md` 为准，本文第 12 节给出新旧对应关系。改到 `muyian/bill/listener/` 时仍按 `50-native-core.md` 的 API 约定执行。

## 0. 落地位置速查

| 层 | 文件 |
| --- | --- |
| 观察模型 / 文本 / 规则 / 解析 / 门禁 | `CaptureModels.kt` / `CaptureText.kt` / `CaptureRules.kt` / `CaptureParser.kt` / `CaptureGates.kt` |
| App 注册表与设置 | `CaptureRegistry.kt` |
| 支付路线 | `CaptureRoute.kt` |
| 账本 / 队列 / 诊断 | `CaptureLedger.kt` |
| 编排 | `CaptureEngine.kt` |
| 弹层队列 | `CapturePresenter.kt` |
| 本地分类学习 | `CaptureLearning.kt` |
| 页面侧封装 / 诊断页 | `data/Capture.kt` / `ui/screens/CaptureScreen.kt` |
| 回归测试 | `app/src/test/java/muyian/bill/listener/CaptureParserTest.kt` / `CaptureIdentityTest.kt` |

## 1. 重构前的问题（历史记录）

重构前的自动记账能跑，但它是**一串按经验追加的布尔判断**，而不是一个有模型的系统：

- `handleScreen()` / `handleNotification()` 里是 8～10 个连续的 `if (!force && ...) return`，每个分支各自决定"拒绝"，拒绝原因只拼一个字符串返回，随后就被丢掉。用户漏记一笔时，无法知道是多通道去重、路线上下文还是金额解析导致的。
- 解析、去重、路线、弹层队列、导入、备份全部住在 `hybrid.kt`（1964 行）里；金额、商户、单号、收支方向、支付渠道都靠全局正则和包名判断，没有"页面类型"这一层。
- 去重有 7 套窗口、3 种存储（SharedPreferences 两张 map、内存 `recentBills`、内存 `flows`、SQLite 全表扫描），语义互相重叠，且没有统一的交易身份。
- 采集文本只有 `List<String>`，丢掉了字体大小、位置、窗口、控件类型。所谓"大字金额"其实是"第 15 行以内的纯小数行"，"锚点"其实是整页第一个命中行。
- 没有任何本地留痕和回归样本，规则改动只能靠真机碰运气；`force` 测试入口没有任何地方调用。

### 1.1 重构前问题清单（按影响排序，均已修复）

| # | 问题 | 位置 | 后果 |
| --- | --- | --- | --- |
| P1 | 微信/支付宝的**聊天、服务号、营销推送、首页最近消息**没有页面类型区分，只要出现"支付"类和金额就认 | `hybrid.kt:939-965`、`hybrid.kt:972-995` | 好友发"我支付了100元"、公众号"支付立减最高88元"都会弹层。这是误报的最大来源 |
| P2 | 通知只看正文关键词，不看渠道/发送方；`IGNORE_PATTERN` 缺"处理中/已取消/抽奖/最高"等 | `BillNotificationListener.kt:76-98`、`hybrid.kt:154-159` | 营销通知、订单取消通知、支付处理中通知被当成已支付 |
| P3 | **本人账户互转、信用卡还款、提现/充值**没有语义：`提现` 在支出词里、`转入`/`存入余额` 在收入词里 | `hybrid.kt:168-180` | 微信零钱提现、银行卡→余额宝、还信用卡各记一笔，和真实消费重复统计 |
| P4 | 所有非微信/支付宝 App 的 `bankName` 一律是"银行卡" | `hybrid.kt:1275-1281` | 弹层渠道信息失真；跨行同金额的两笔在 2 分钟内容易被误并 |
| P5 | 宿主结果页只要 15 分钟内**存在该宿主的任意 flow** 就放行 | `hybrid.kt:485-501` | 一次支付后 15 分钟内的另一张"成功页"也可能被接受 |
| P6 | 金额兜底过宽：`pickIsolatedAmount` 取前 15 行任意纯小数，`pickByCurrencyContext` 取全页第一个带"元/¥"的数字；排除词没有"限额/汇率/尾号/验证码" | `hybrid.kt:1173-1213`、`hybrid.kt:205-208` | 免密限额、汇率、卡尾号、积分行都可能被当成交易额 |
| P7 | `isDuplicateBill()` 每次事件全表扫描 `bills`，无索引、无时间条件、每次开关数据库 | `hybrid.kt:1325-1352` | 账单越多越慢；主线程/后台线程都可能触发 ANR 风险 |
| P8 | 弹层队列只存在内存，`MAX_SHEETS` 超限直接丢掉最旧的一笔 | `hybrid.kt:86-90`、`hybrid.kt:1412-1416` | 进程被杀或连续多笔时静默丢单，用户无从知晓 |
| P9 | 路由状态（`pendingPayAt` / `returnedFromPayAt` / `routeEntries`）只在内存，进程重启即丢 | `hybrid.kt:116-127` | 支付过程中进程被回收，宿主结果页会因"没有支付路线"被拒 |
| P10 | 无障碍只取 `rootInActiveWindow`，只认 `isVisibleToUser` 的文本，不记录字体/位置/窗口层级 | `MuyianBillAccessibilityService.kt:117-156` | 分屏/悬浮窗/WebView 支付页漏采；无法判断"最大字号金额" |
| P11 | App 名单硬编码，用户不能增删，也不能把某个 App 标成"只认通知"或"关闭" | `hybrid.kt:272-290` | 新支付 App（抖音支付、拼多多、区域银行等）永远不识别；误报 App 无法关闭 |
| P12 | 没有本地诊断：拒绝原因、置信度、用户是否改过金额都不落盘 | 全链路 | 无法定位漏记/误记，规则只能猜 |

## 2. 设计原则

1. **确认弹层仍是唯一入账入口**：引擎只负责把"候选交易"送到弹层，永不写 `bills`。置信度只影响提示与排序，不改变"必须点保存"。
2. **宁可少弹，不可乱弹；但漏记必须可解释**：硬门禁负责拒绝，每次拒绝都落一条带原因码的本地记录。
3. **一次交易只有一个身份**：单号 > 支付会话 > 页面指纹 > 捕获指纹 > 渠道+金额兜底，统一由账本原子裁决。
4. **采集与解析分离**：采集层只产出结构化观察（文本 + 位置 + 字号 + 窗口 + 通道），解析层不碰 Android API，可单元测试。
5. **规则数据化**：App 角色、页面标记、金额锚点、排除词、通知渠道都进规则表；主流程不写 `if (pkg == ...)`。
6. **本地闭环**：诊断、反馈、商户→分类学习都只在手机本地，不新增网络请求。

## 3. 目标架构

```
                    ┌──────────────────────┐
屏幕观察 ──────────►│ Observation          │◄────────── 通知观察
(无障碍快照)         │ source/时刻/结构文本  │          (通知渠道/发送方)
                    └──────────┬───────────┘
                               ▼
                    ┌──────────────────────┐
                    │ SourcePolicy          │  候选 App / 用户开关 / 系统页
                    └──────────┬───────────┘
                               ▼
                    ┌──────────────────────┐
                    │ PageClassifier        │  → PageKind + 页面标记
                    │ SourceAdapter         │  → Facts（金额候选/方向/商户/单号）
                    └──────────┬───────────┘
                               ▼
                    ┌──────────────────────┐
                    │ TransactionAssembler  │  → TransactionCandidate
                    │ + RouteEngine         │     身份键 + 证据 + 置信度
                    └──────────┬───────────┘
                               ▼
                    ┌──────────────────────┐
                    │ Gate 流水线           │  → Decision(accepted/reason)
                    └──────────┬───────────┘
                               ▼
                    ┌──────────────────────┐
                    │ CaptureLedger(SQLite) │  原子占位/去重/状态/诊断
                    └──────────┬───────────┘
                               ▼
                    ┌──────────────────────┐
                    │ Presenter             │  持久队列 → BillSheetActivity
                    └──────────┬───────────┘
                               ▼
                    用户保存/取消 → Feedback → 本地学习与诊断
```

### 3.1 文件划分（全部在 `muyian.bill.listener` 同包）

| 文件 | 职责 | Android 依赖 |
| --- | --- | --- |
| `CaptureModels.kt` | `Observation` / `PageKind` / `Direction` / `Facts` / `Candidate` / `Decision` / `Reason` 枚举与数据类 | 无 |
| `CaptureText.kt` | 文本归一化、行切分、金额/日期/单号 token 解析 | 无 |
| `CaptureRules.kt` | App 注册表、页面标记、金额锚点与排除词、通知渠道规则 | 无 |
| `CaptureParser.kt` | 页面/通知 → `Facts`，金额候选打分与择取 | 无 |
| `CaptureRoute.kt` | 支付路线状态机、会话 ID、宿主上下文 | 仅 `Context` 存取 |
| `CaptureLedger.kt` | 去重账本与待确认队列表（SQLite） | 仅 `Context` |
| `CaptureGates.kt` | 有序门禁与原因码 | 无（账本接口注入） |
| `CaptureEngine.kt` | 编排：观察 → 判定 → 弹层；`MuyianBillCore` 门面转调 | 有 |
| `CaptureDiagnostics.kt` | 诊断查询、计数、调试文本留存 | 有 |
| `hybrid.kt` | 只保留 `MuyianBillCore` 门面、弹层队列、导入/备份 | 有 |

### 3.2 纯逻辑下沉的好处

`CaptureModels/CaptureText/CaptureRules/CaptureParser/CaptureGates` 不 import Android，放在主源码集但可以直接被 `android/app/src/test` 覆盖。真机只负责"文本采得全不全"，不再负责"规则对不对"。

## 4. 观察层（Observation）

### 4.1 屏幕快照

```kotlin
data class UiText(
    val text: String,
    val left: Int, val top: Int, val right: Int, val bottom: Int,
    val sizePx: Int,          // bottom - top，用于识别"大字金额"
    val className: String,    // TextView / EditText / WebView ...
    val viewId: String,       // 有则带上，适配器可精确匹配
    val depth: Int
)

data class ScreenObservation(
    val pkg: String,
    val windowTitle: String,
    val windowType: Int,        // TYPE_APPLICATION / TYPE_APPLICATION_OVERLAY ...
    val screenWidth: Int, val screenHeight: Int,
    val items: List<UiText>,    // 按 top、left 排序后的可见文本
    val eventType: Int,
    val observedAt: Long
)
```

采集改动（`MuyianBillAccessibilityService`）：

- 配置增加 `flagRetrieveInteractiveWindows`；用 `windows` 找**包名匹配且层级最高**的窗口，而不是只信 `rootInActiveWindow`。保留现有"root 包名不一致就跳过"的校验，但改为"从 windows 里选正确的那个"。
- 去掉"只收 `isVisibleToUser`"的硬过滤，改为**收可见节点 + 最多额外 N 个不可见节点**；金额可能被滚动出视口，但同一窗口里仍然存在。
- 每个节点记录 bounds / 字号 / className / viewId / depth；上限从 400 提到 600，超出时优先保留字号大的节点（先做两遍：先统计字号分布，再按优先级截断）。实现可以简化为：第一遍收全部，超过上限时按 `sizePx` 降序保留。
- 事件去抖保持现状（每 App 600ms + 签名去重），但 `screenSignature` 改为基于 `(text + bounds.top)` 的稳定哈希，避免页面轻微动效导致重复解析。
- 服务 `onServiceConnected` 时主动采一次当前窗口：服务被系统重建时，结果页可能已经渲染完成、不会再有事件。

### 4.2 通知观察

```kotlin
data class NotificationObservation(
    val pkg: String,
    val channelId: String,
    val category: String?,      // Notification.CATEGORY_*
    val notificationKey: String,
    val isGroupSummary: Boolean,
    val isOngoing: Boolean,
    val title: String, val text: String,
    val subText: String, val bigText: String,
    val lines: List<String>,
    val postTime: Long
)
```

`BillNotificationListener` 需要补 `sbn.key`、`notification.channelId`、`notification.category`、`notification.flags`。没有这些字段，就无法把"微信支付"渠道和"好友聊天"区分开。

## 5. 页面分类（PageKind）

```kotlin
enum class PageKind {
    PAY_SUCCESS,        // 支付/消费成功
    TRANSFER_SUCCESS,   // 转账成功（可能是本人互转）
    RECEIVE_SUCCESS,    // 收款/到账
    REFUND_SUCCESS,     // 退款成功
    REPAYMENT_SUCCESS,  // 还款成功
    CASHIER,            // 收银台/确认支付（只作为支付证据）
    PAY_FAILURE,        // 失败
    PAY_PENDING,        // 处理中/待支付
    ORDER_DETAIL,       // 订单详情
    ORDER_LIST,         // 订单列表/管理
    BILL_LIST,          // 账单/交易记录
    CHAT,               // 聊天/服务号会话
    AD,                 // 营销/活动页
    UNKNOWN
}
```

分类顺序（先命中先返回，最具体优先）：

1. `PAY_FAILURE` / `PAY_PENDING`（完成标记与失败标记同时出现时，失败优先）。
2. `ORDER_LIST` / `BILL_LIST`（"全部订单/账单明细/本月支出"等整页标记）。
3. `AD`（"最高/抽奖/领红包/限时/活动/立减"且无完成标记）。
4. `CHAT`（微信/支付宝会话页：出现输入框、发送按钮、多条聊天气泡；或同一屏出现多条"转账/凭证"卡片）。
5. `CASHIER`（"确认支付/立即支付/收银台/选择支付方式"）。
6. `PAY_SUCCESS` / `TRANSFER_SUCCESS` / `RECEIVE_SUCCESS` / `REFUND_SUCCESS` / `REPAYMENT_SUCCESS`（完成标记 + 金额）。
7. `ORDER_DETAIL`（有订单号/商品但无完成标记）。
8. `UNKNOWN`。

**关键约束**：`CHAT` 页只有"最顶部/最新的一条支付卡片"可以进入解析，且要求卡片时间与观察时间相差在 2 分钟内。这样微信支付服务号里翻历史消息不会误弹。`CASHIER` 永远不产生交易，只更新路线证据。

分类规则按 App 适配器分组（第 6 节），未命中时走通用规则；通用规则对 `CHAT`/`AD` 的判定更保守。

## 6. 来源适配器（SourceAdapter）

适配器不是"每个 App 写一套 if"，而是一张规则表 + 少量结构提取函数：

```kotlin
data class AppRule(
    val pkg: String,
    val role: AppRole,                  // PAY / HOST / OFF
    val adapter: AdapterId,             // ALIPAY / WECHAT / UNIONPAY / BANK / MEITUAN / JD / GENERIC
    val amountAnchors: List<String>,    // 在该 App 内额外信任的标签
    val amountExcludes: List<String>,   // 额外排除词
    val notifyChannels: List<String>,   // 只接受这些通知渠道（为空=通用判断）
    val knownPages: Map<PageKind, List<String>>  // 可选：页面特征
)
```

内置注册表（`CaptureRules.BUILT_IN_APPS`）覆盖现在的 `BILL_PACKAGES`/`BILL_PREFIXES`，但增加 `role` 与 `adapter` 两个维度：

| adapter | 典型包名 | 额外能力 |
| --- | --- | --- |
| `ALIPAY` | `com.eg.android.AlipayGphone`、`com.alipay.*` | 区分"余额宝收益/花呗还款/转账/退款"；识别"交易提醒" |
| `WECHAT` | `com.tencent.mm` | 只认"微信支付"服务号渠道或支付凭证卡片；聊天正文永不算账单 |
| `UNIONPAY` | `com.unionpay*` | 交易金额 + 卡尾号 |
| `BANK` | `com.icbc*`、`com.cmbchina*` 等 | 从文本取真实银行名与卡尾号，替换统一"银行卡" |
| `MEITUAN` / `JD` | 美团/点评/京东 | 宿主结果页，必须有支付上下文 |
| `GENERIC` | 用户手动添加的 App | 最严格的通用规则（完成标记 + 强锚点/大字金额二选一） |

宿主 App 与支付 App 的判定不再靠"包名前缀 + 银行列表"散落各处，而是 `AppRule.role`。用户可以在设置页把某个 App 改成 `PAY`/`HOST`/`OFF`（存 `capture_app_overrides_v1`，覆盖内置表）。

## 7. 字段抽取与置信度

### 7.1 金额候选

解析器不再"返回第一个金额"，而是产出候选列表，再择取：

```kotlin
enum class AmountTier { STRONG_LABEL, STRUCT_HEADLINE, WEAK_LABEL, CURRENCY_CONTEXT }

data class AmountCandidate(
    val value: Double,
    val tier: AmountTier,
    val confidence: Double,   // 0~1
    val anchor: String,       // "实付金额" / "¥" / "元"
    val evidence: String      // 命中的行（仅本地诊断用）
)
```

择取顺序与现有语义一致，但增加结构信号：

1. **STRONG_LABEL**：强锚点（实付/支付金额/交易金额/退款金额等）后紧跟的金额；值可在同一行或下一行。
2. **STRUCT_HEADLINE**：结果卡片内**字号最大**、带货币符号或纯小数、水平居中/独立的金额。字号必须 ≥ 同页中位字号的 1.6 倍，且该行距离完成标记不超过 3 行或 240px。
3. **WEAK_LABEL**：弱锚点后的金额，必须带货币单位或小数。
4. **CURRENCY_CONTEXT**：只允许在包含完成标记的卡片区域内扫描；全页兜底取消。

排除词按类型维护并单测（`CaptureRules.EXCLUDES`）：

| 类型 | 词 |
| --- | --- |
| 余额 | 余额、账户余额、可用、零钱、零钱通、余额宝 |
| 额度 | 额度、限额、单笔限额、剩余额度、可用额度 |
| 优惠 | 优惠、立减、折扣、原价、已省、节省、红包、返现、抵扣、优惠券 |
| 费用 | 手续费、服务费、运费、配送费、打包费 |
| 积分 | 积分、京豆、金币、成长值 |
| 比率 | 汇率、利率、费率、折 |
| 其它 | 尾号、验证码、满…减 |

**歧义处理**：同一层级出现两个不同金额且都通过排除检查时，取置信度高者，并在候选上标 `needsReview = true`；弹层把金额高亮提示"识别到多个金额，请确认"。不因为歧义丢弃整笔（用户仍可改），但绝不静默取一个看起来合理的数。

### 7.2 收支方向

```kotlin
enum class Direction { EXPENSE, INCOME, REFUND, TRANSFER, REPAYMENT, UNKNOWN }
```

判定顺序：

1. 退款标记（退款/退回/返还）→ `REFUND`（金额记正，`costType` 建议"退款"）。
2. 还款标记（还款成功/已还款/信用卡还款）→ `REPAYMENT`。
3. 本人账户标记（提现/充值/转入零钱通/余额宝/存入余额/转出到卡/本人账户）→ `TRANSFER`。
4. 收款/到账标记 → `INCOME`。
5. 支付/消费/扣款标记 → `EXPENSE`。
6. 只有方向词没有完成标记 → `UNKNOWN`，拒绝。

`TRANSFER` 与 `REPAYMENT` 默认**不送弹层**（记为诊断 `INTERNAL_TRANSFER` / `REPAYMENT`），因为支出/收入统计会重复。设置页提供开关"记录本人账户互转与信用卡还款"，开启后照常弹层并归入"转账"类。这是唯一会影响入账范围的新增开关，默认关闭。

### 7.3 商户、单号、渠道

- 商户：标签（收款方/商户全称/付款给/转账给）→ 结果卡片标题 → 尾部括号；过滤通用词与银行后缀；保留原值只用于本地。
- 单号：`[A-Za-z0-9]{8,40}`，允许中间空格/连字符但不允许跨到下一个标签；至少 4 位数字；排除 8 位日期和卡尾号；统一大写。取不到就为空。
- 渠道：优先从文本取"XX银行 + 卡尾号(1234)"；其次 `AppRule.adapter`；最后才是"银行卡"。**禁止所有银行 App 共用"银行卡"**。
- 时间：页面/通知里有明确时间且落在 `[now-24h, now+5min]` 时用页面时间，否则用观察时间；`bills` 的 `year/month/day/clock` 由它生成。

## 8. 交易身份、会话与账本

### 8.1 身份键

| 键 | 含义 | 生成方式 | 窗口 |
| --- | --- | --- | --- |
| `order:<no>` | 单号唯一 | 解析出的订单号 | 24h |
| `session:<id>` | 同一次"宿主→支付→宿主"往返 | `RouteEngine.sessionId` | 15min |
| `page:<pkg>:<hash>` | 宿主结果页 | 包名 + 金额 + 方向 + 单号 + 商户的结构哈希 | 24h |
| `capture:<pkg>:<hash>` | 无单号捕获 | 包名 + 金额 + 关键行前 40 字 | 5min |
| `bill:<channel>:<amount>:<bucket>` | 跨渠道兜底 | 渠道 + 金额 + 方向 + 2min 时间桶 | 2min |

`bill` 键的 `channel` 必须是真实渠道（微信/支付宝/招商银行/云闪付），不能是"银行卡"，否则跨行同金额会误并——这正是 P4 的根因。

### 8.2 账本表

```sql
CREATE TABLE IF NOT EXISTS capture_ledger (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  key         TEXT    NOT NULL,
  kind        TEXT    NOT NULL,   -- order|session|page|capture|bill
  txn_id      TEXT    NOT NULL,
  pkg         TEXT    NOT NULL,
  amount      REAL    NOT NULL,
  direction   TEXT    NOT NULL,
  channel     TEXT    NOT NULL,
  order_no    TEXT    NOT NULL DEFAULT '',
  state       TEXT    NOT NULL,   -- queued|saved|discarded|rejected
  reason      TEXT    NOT NULL DEFAULT '',
  confidence  REAL    NOT NULL DEFAULT 0,
  needs_review INTEGER NOT NULL DEFAULT 0,
  created_at  INTEGER NOT NULL,
  expires_at  INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_ledger_key ON capture_ledger(key);
CREATE INDEX IF NOT EXISTS idx_ledger_exp ON capture_ledger(expires_at);
CREATE INDEX IF NOT EXISTS idx_ledger_order ON capture_ledger(order_no);
CREATE INDEX IF NOT EXISTS idx_ledger_txn ON capture_ledger(txn_id);
```

裁决在一个 `BEGIN IMMEDIATE` 事务内完成：

1. 用候选的全部键查 `expires_at > now` 的行。
2. 命中同一 `txn_id` → 判为同一笔，返回 `MERGE`（后到的支付侧字段可覆盖渠道/金额）。
3. 命中不同 `txn_id` → 判为重复，返回 `DUPLICATE_<kind>`。
4. 未命中 → 插入全部键，返回 `CLAIMED`。

这替代所有"先查再写"的竞态写法，也天然跨进程/跨重启有效。旧的 `KEY_DEDUP` / `KEY_ORDER_DEDUP` / `KEY_HOST_PAGE_DEDUP` / `KEY_FLOWS` 已在新版本首次启动时直接删除，不回填；旧账单的去重靠 `bills.orderNo` 与「渠道 + 金额 + 2 分钟」兜底。

### 8.3 待确认队列持久化

```sql
CREATE TABLE IF NOT EXISTS pending_captures (
  txn_id     TEXT PRIMARY KEY,
  bill_json  TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  attempts   INTEGER NOT NULL DEFAULT 0
);
```

- 入队时写一行，`state=queued`；保存/取消/超时/熄屏删除行并更新账本状态。
- 进程启动、服务重连、`MuyianBillCore.init()` 时恢复队列：亮屏则弹第一笔，熄屏/锁屏则按现有规则发待确认通知。
- `MAX_SHEETS` 超限不再静默丢弃：拒绝新的一笔并记 `QUEUE_FULL`，弹层关闭后重试一次，仍失败才丢弃（有诊断记录）。

## 9. 路线与会话状态机

```kotlin
enum class RoutePhase { HOST, PAY_ENTERED, PAY_EVIDENCE, RETURNED }

data class RouteState(
    val hostPkg: String,
    val payPkg: String,
    val phase: RoutePhase,
    val enteredAt: Long,
    val evidenceAt: Long,
    val returnedAt: Long,
    val sessionId: String,
    val updatedAt: Long
)
```

- `noteForeground` 只负责状态迁移：`HOST → PAY_ENTERED → PAY_EVIDENCE → RETURNED`。
- 宿主结果页接受条件：`phase == RETURNED` 且距 `returnedAt ≤ 3min`，或 App 内收银台触发（`triggeredAt ≤ 10min`）。**删除"15 分钟内存在任意 flow 就放行"的兜底**（P5）。
- 会话 ID 用 `payPkg + enteredAt`（毫秒）生成；宿主结果页、支付结果页、通知共用同一次会话。
- 状态整体序列化到 `capture_route_v1`（JSON，TTL 15 分钟），每次迁移写一次；进程重启后恢复（P9）。
- 锁顺序保持 `routeLock → sheetLock`，账本自己串行。

## 10. 判定流水线（Gate）

固定顺序，每道门返回 `Pass` 或 `Reject(reason)`。顺序本身就是文档，便于测试和诊断。

| 顺序 | Gate | 拒绝原因码 | 说明 |
| --- | --- | --- | --- |
| 1 | `SourceGate` | `SELF` / `SYSTEM_UI` / `NOT_CANDIDATE` / `APP_OFF` | 用户关闭的 App 直接忽略 |
| 2 | `PageGate` | `PAGE_LIST` / `PAGE_ORDER_MGMT` / `PAGE_AD` / `PAGE_CHAT` / `PAGE_FAILURE` / `PAGE_PENDING` / `PAGE_CASHIER` | `CASHIER` 只更新证据 |
| 3 | `ContextGate` | `NO_PAYMENT_CONTEXT` | 宿主结果页必须有会话/触发 |
| 4 | `ParseGate` | `NO_AMOUNT` / `NO_DIRECTION` / `NO_COMPLETION_MARK` | 页面/通知必须有完成语义 |
| 5 | `SemanticGate` | `INTERNAL_TRANSFER` / `REPAYMENT` / `DUPLICATE_REFUND` | 默认不弹的语义类 |
| 6 | `IdentityGate` | `DUPLICATE_ORDER` / `DUPLICATE_SESSION` / `DUPLICATE_PAGE` / `DUPLICATE_CAPTURE` / `DUPLICATE_BILL` | 账本原子裁决 |
| 7 | `CapacityGate` | `QUEUE_FULL` | 队列上限 |
| 8 | `Presenter` | `ACCEPTED` / `ACCEPTED_REVIEW` | 入队并拉起弹层 |

原因码是稳定枚举，写进账本与诊断；测试对每个码都有断言。

置信度计算（仅用于排序、提示、诊断，不参与是否拒绝）：

```
confidence = 0.55 * amountConfidence
           + 0.25 * pageConfidence
           + 0.15 * directionConfidence
           + 0.05 * (单号是否为空 ? 0 : 1)
```

- `≥ 0.80`：正常弹层。
- `0.60 ~ 0.80`：弹层顶部提示"自动识别可能有误"，金额仍预填。
- `< 0.60`：仍然弹（硬门禁已保证是成功页），但诊断标记 `low_confidence`；设置页可开"低置信不提醒"，默认开提醒。

## 11. 展示、反馈与诊断

### 11.1 弹层

行为不变（60 秒超时、返回取消、熄屏丢弃、保存才写库）。新增：

- 顶部可显示"支付渠道 · 识别置信度"；`needsReview` 时金额下方一行浅色提示。
- 分类默认值来自本地建议（见 11.3），不再固定"消费"。
- 商户预填到备注的现有行为保留。

### 11.2 反馈

用户动作写回账本：

- 保存：`state=saved`，记录最终金额/方向/分类/商户，以及是否改过金额。
- 取消/超时/熄屏：`state=discarded`。
- 若改过金额，按 `adapter + pageKind` 累计 `amount_edited` 计数；诊断页按此排序，优先修这些页面。

### 11.3 本地学习（不做模型，只做计数）

- `merchant_category_v1`（prefs JSON）：`规范化商户名 + 包名 → {分类: 次数}`，保存时累加；弹层按最高次数建议分类。
- `pkg_category_v1`：App → 最常用分类，作为没有商户时的兜底。
- 只在本机，可一键清空；不参与金额解析，避免"学坏"。

### 11.4 诊断页（设置 → 自动记账 → 识别记录）

本地只读列表 + 计数：

- 最近 200 条判定：时间、App、页面类型、方向、金额、结果、原因码、置信度、用户是否修改。
- 汇总：今日捕获/保存/丢弃/拒绝；按 App、按原因码排序；"金额被改过"最多的页面。
- 开关：`记录页面文本（调试）`，默认关闭；开启后只保留 7 天、最多 200 条，仅本机，可一键清空。用于收集样本，不做上传。
- 操作：`重新识别`（把保存的文本重新跑一遍解析，展示结果，不弹层）、`复制原因码`。

## 12. 与现有语义的对应（迁移不破坏行为）

| 现有 | 新实现 | 备注 |
| --- | --- | --- |
| `CAPTURE_DEDUP_WINDOW_MS` 5min | `capture:` 键 5min | 语义不变 |
| `ORDER_DEDUP_WINDOW_MS` 24h | `order:` 键 24h | 语义不变 |
| `HOST_PAGE_DEDUP_WINDOW_MS` 24h | `page:` 键 24h | 指纹改为结构哈希 |
| `BILL_DEDUP_WINDOW_MS` 2min | `bill:` 键 2min | 渠道改为真实渠道 |
| `FLOW_MATCH_WINDOW_MS` / `sessionAt` | `session:` 键 15min + `RouteState` | 合并规则不变 |
| `recentBills` / `isDuplicatePresented` | 账本 `queued` 状态 | 不再依赖内存 |
| `sheetQueue` | `pending_captures` | 进程死亡可恢复 |
| `SCREEN_ORDER_MGMT_PATTERN` | `PageGate` 的 `ORDER_LIST` | 规则表化 |
| `pickAmount` 三级 | `AmountTier` 四级 + 结构信号 | 置信度可解释 |
| `inferPayWay` | `Facts.channel` | 取真实银行/卡尾号 |
| `KEY_FLOWS` | `CaptureRoute` 会话 + `session:` 账本键 | 不再单独存 flow 数组 |

## 13. 测试与验收

### 13.1 单元测试（无需真机）

- `CaptureParserTest`：真实页面文本 fixture（Alipay/微信/云闪付/银行/美团/京东各 3～5 例）→ 断言 `PageKind`、金额、方向、商户、单号、置信度档位。
- `CaptureAmountTest`：排除词表逐项断言；"限额/汇率/尾号/优惠"不得被选中；千分位、0.01、整数元。
- `CaptureGateTest`：固定时钟 + 内存账本，逐条断言原因码；同一观察重复两次只产生一个候选。
- `CaptureDirectionTest`：提现/充值/还款/退款/收款/支付。
- `CaptureRouteTest`：HOST→PAY→HOST 会话合并；进程重启恢复；超过 TTL 失效。

必测反例（现在会误报）：

1. 微信好友消息"我支付了100元" → `PAGE_CHAT`。
2. 公众号"支付立减，最高88元" → `PAGE_AD`。
3. 微信支付服务号历史凭证 → 只认最新一条，旧的不弹。
4. "支付处理中" / "订单已取消" → `PAGE_PENDING` / `PAGE_FAILURE`。
5. "免密支付限额500元" + "支付成功0.01元" → 取 0.01。
6. 微信零钱提现 100 元 → `INTERNAL_TRANSFER`。
7. 花呗还款 500 元 → `REPAYMENT`。
8. 招商银行 + 建设银行同金额 2 分钟内两笔 → 两笔都保留。
9. 同一宿主 15 分钟内两张不同结果页 → 只有有会话的那张被接受。
10. 进程被杀时队列里有 2 笔 → 重启后都能恢复。
11. 支付宝首页「最近消息」同时有「转账成功 ¥0.01」「付款成功 ¥21.00」→ `PAGE_LIST`；没有首页标记但一页多行完成标记+金额 → `MULTI_TRANSACTION`。
12. 支付宝「转账 → 最近/联系人/我的」收款人列表（多行「转给TA/收到TA」）→ `PAGE_LIST`；滚动后 tab 不在树里时由转账行标记兜底为 `MULTI_TRANSACTION`；单笔「转账成功 + 转给TA」仍正常识别。

### 13.2 真机回归清单

- 支付宝：扫码支付、转账、退款、余额宝转入、花呗还款。
- 微信：扫码支付、转账收款、零钱提现、服务号历史消息。
- 银行 App/SMS：消费、退款、还款。
- 宿主：美团下单→微信支付→返回美团；京东 App 内支付；订单列表翻页。
- 锁屏/熄屏碰卡通知 → 待确认通知 → 点开弹层。
- 无障碍服务被系统杀死后恢复 → 当前结果页不漏。

## 14. 分阶段实施（已完成）

以下阶段已全部落地，对应文件见第 0 节：

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| P0 | 抽 `CaptureModels/Text/Rules/Parser/Gates`，纯逻辑；补 39 条回归单测 | 单测全绿 |
| P1 | 账本 + 待确认队列持久化 + 原因码落盘；旧 prefs 直接清理 | 重启不丢队列；去重窗口与旧语义一致 |
| P2 | 结构化快照（位置/包围盒/窗口）+ 页面分类 + 金额候选/排除词 | 聊天/营销/列表/失败/限额等反例通过 |
| P3 | 路线状态机持久化 + 通知渠道策略 + 互转/还款/退款/历史页语义 | 会话合并、通知发送方、语义拒绝通过 |
| P4 | App 注册表与用户开关、诊断页、反馈学习、分类建议 | 设置页可改角色/关闭；诊断可定位原因 |
| P5 | 删除旧去重与旧解析分支；更新 `50-native-core.md` / `40-data.md` | 文档与代码一致 |

### 14.1 实施偏差（与设计稿不同之处）

- 无障碍只采集**可见节点**，没有额外收不可见节点：避免订单列表/滚动列表里的隐藏金额进入候选。字号用节点包围盒高度近似（`AccessibilityNodeInfo` 不暴露 textSize），对"大字金额"的相对比较足够。
- 账单日期仍优先用页面/通知里的明确时间；支付 App 页面若带旧日期且超过 10 分钟，新增 `STALE_PAGE` 拒绝，防止翻历史回执。
- 「低置信度也弹层」落地为设置项：默认开；关闭后 `needsReview` 或置信度 <0.60 只记诊断。
- 旧去重键直接删除而非导入；账本从空开始，旧账单靠 `bills` 表兜底。
- 诊断页入口：设置 → 自动记账 → 识别记录与诊断；不单独占首页入口。
- App 列表默认只列内置包名 + 用户覆盖，避免打开页面就触发 MIUI「读取已安装应用」提示；「扫描其它应用」按需枚举全部可启动应用（清单声明 `QUERY_ALL_PACKAGES`，仅本机使用）。
- 金额排除词判定改为**紧邻前缀**（金额前压缩空白后 6 字符）：整段前缀判定会把「您尾号4321的卡交易¥100.00」整条拒掉，还会跳过真金额后取到同行优惠额（「已优惠2元，实付50元」取到 2）。紧邻窗口由 `CaptureText.adjacentPrefixExcluded` 统一实现。
- 无单号的宿主结果页 `page:` 键 TTL 从 24h 降为 10 分钟（`TTL_PAGE_NO_ORDER`）：弱指纹（仅金额+方向）挂 24h 会把每天同价的真实第二笔判成 `DUPLICATE_PAGE`；有单号时仍为 24h。
- 账本 `claim` 异常从 fail-open（返回 CLAIMED）改为 **fail-closed**（返回 `DUPLICATE_CAPTURE`）：DB 忙时宁可漏弹，也不能同一笔重复弹层。
- 支付宝结果页为渐进渲染，无障碍层对支付宝**固定跑快慢两遍解析**（事件后 160ms 与 450ms，参照钱迹实测参数），绕过 600ms 节流；签名去重保证已稳定页面不重复解析。
- 无障碍服务 `onServiceConnected` 时调用 `restoreCaptureQueue`：进程被杀后服务重绑、用户未打开 App 也能恢复待确认队列（§8.3 的落地补齐）。
- 用户在弹层保存时改过金额的，往该笔判定记录追加 `amount_edited` 标记（`CaptureLedger.markAmountEdited`），诊断页今日摘要显示条数——这是规则迭代的核心反馈信号。
- `BillSheetActivity` 声明 `configChanges`：转屏/折叠不再触发 `onDestroy` 的未保存丢弃分支，用户正在编辑的内容得以保留。

## 15. 兼容与红线

- `MuyianBillCore` 对外仍全部 `@JvmStatic` 同步返回，复杂结构仍用 JSON 字符串；页面侧仍通过 `data/` 封装消费。
- `bills` 表结构不变，`source` 取值仍为 `manual/notify/screen/qianji`，金额支出负、收入正。
- 新增 SQLite 表只在 `BillDb.openDb()` 里建，旧库 `ALTER TABLE` 兼容策略不变。
- 不新增网络请求、不上传文本；调试文本留存默认关闭且仅本机。
- 弹层仍是唯一确认入口；通知仍只是弹层入口；不引入悬浮卡/草稿。
- 无障碍仍是主渠道，通知监听仍是辅助渠道；用户关闭通知监听后，屏幕路径不受影响。

## 16. 已定产品决策

1. **本人互转/信用卡还款默认不记账**，设置页「记录本人互转与还款」可开；开启后按支出（负数）记录，分类建议「转账」/「还款」。
2. **退款保持"收入 + 退款分类标签"**，不改统计口径；冲抵原支出需要关联原单，暂不做。
3. **低置信度默认弹层并提示**，可在诊断页关闭；关闭后只记诊断。
4. **用户配置粒度只有"角色 + 开关"**（支付 App / 宿主 App / 关闭），不提供自定义正则。
