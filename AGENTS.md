# 孽账 · 项目约束

本文件是本仓库的强约束，所有 AI 会话与协作者必须遵守。与 README 互补：README 讲"是什么/怎么跑"，本文件讲"必须怎么做/不许怎么做"。

## 0. 项目定位与产品红线

- Android 无障碍自动记账 App：**uni-app（vue3）+ wot-design-uni + UTS 插件（hybrid.kt 原生核心）**。
- **解析成功的账单不自动入账**：一律先落"待确认草稿"，用户确认后才写库。任何改动不得绕过该确认流。
- **隐私红线**：通知/窗口文本只在手机本地解析，数据只存本机 SQLite，禁止引入任何上传、统计 SDK 或云同步代码（云同步在路线图里，且需单独实现）。
- 自动采集通道以**无障碍服务**（AccessibilityService）为主渠道；**通知监听**（NotificationListenerService）为可选辅助渠道（须用户在设置页单独授权），仅用于覆盖无障碍盲区（收款到账提醒只在通知栏、熄屏碰卡交易通知）。两路来源靠捕获级/账单级去重互防重复；`notify` source 同时表示辅助渠道与历史数据。禁止把通知监听改回唯一渠道。

## 1. 工程形态

- 用 **HBuilderX 打开整个项目目录**运行/打包（非 CLI 工程）。禁止引入 vite/cli 脚手架改造、禁止改 manifest.json 的 appid。
- npm 仅用于装 uni-app 兼容的前端依赖（当前只有 `wot-design-uni`）。装依赖前确认它支持 HBuilderX 非 cli 用法（easycom 引用，不需要额外构建配置）。
- `package.json` 必须保持 `"type": "commonjs"`（不影响 uni-app 编译，只影响本地 node 工具对 ESM 的解析）。
- 目标平台：仅 Android 真机。iOS 侧只维护 `app-ios/index.uts` 桩文件，不做功能投入。
- 运行验证必须用**自定义调试基座**（含 UTS 插件，标准基座跑不了）。改动 `hybrid.kt`/`interface.uts`/`index.uts` 后必须重新制作基座。
- **H5/Web 运行的两个必需品，删任何一个 H5 编译即失败**：根目录 `index.html`（vue3 模板入口，含 viewport 脚本与 `/main.js` 引用）；项目 devDependencies 的 `sass`（wot 组件 SCSS 编译用，固定 `sass@1.78.0`）。H5 启动：`D:\HBuilderX\cli.exe launch web --project E:\project\muyian\muyian-app`（默认端口 5173，被占自动顺延）。

## 2. UI 约束

- 组件库统一 **wot-design-uni**，通过 `pages.json` 的 easycom 自定义规则引用（`^wd-(.*)` → node_modules）。页面里禁止手写同类基础组件（按钮/弹层/单元格/进度条等优先用 wd-*）。
- 全部页面 `navigationStyle: custom`。自定义头部的页面必须处理状态栏高度（`var(--status-bar-height)`）与底部安全区（`env(safe-area-inset-bottom)`）；带 wd-navbar 的页面用 `:fixed="true" :placeholder="true" :safe-area-inset-top="true"`。
- **布尔 prop 必须显式绑定**（`:is-link="true"`、`:hide-text="true"`）。禁止裸属性写法（`is-link`）——app-vue 下裸属性传空串，按 falsy 处理，会导致 fixed/left-arrow 等静默失效。
- 样式单位用 rpx；全局视觉规范以 App.vue 设计令牌为准：页面底 `--c-bg`(#f5f6fa)、卡片白底圆角 `28rpx` + `--shadow-card`、主蓝/支出红/收入绿见令牌、正文 `--c-text`、次要 `--c-sub`。改色板先改 App.vue 令牌，保持一致。
- **界面与分类图标全部统一用 `app-icon` 组件**（`components/app-icon/app-icon.vue` + `common/icons.js` 的 SVG data-uri 线性图标，Feather 风格）；新增图标往 `icons.js` 的 PATHS 里加 24x24 stroke 路径。分类数据里的 icon 字段存**图标名**（非 emoji），自建分类默认 `grid`，可选集见 `cats.js` 的 ICON_CHOICES。分类图标一律用**实体填充模式**（`<app-icon :solid="true">`，路径在 icons.js 的 FILLED_PATHS，evenodd 规则镂空、部件间禁止重叠），描边模式仅用于界面功能图标。禁止引入图标字体（iconfont/wd-icon 已弃用于新代码）或 png 图片图标。
- **配色/圆角/阴影统一走 `App.vue` page 级 CSS 变量（设计令牌）**：`--c-primary`(#f7a600 主题黄)、`--c-out`(#f0655e 支出红)、`--c-in`(#22b877 收入绿)、`--c-text/--c-sub/--c-dim` 文字层级、`--c-bg/--c-card/--c-line` 面板、`--shadow-card` 卡片阴影。改主题只动 App.vue，页面禁止硬编码主题色（一次性中性色 #f4f6fa/#5b6170 等允许）。
- **毛玻璃用 App.vue 里的全局工具类**：`.glass`（通用半透明卡）、`.glass-nav`（内页 wd-navbar，传入 custom-class）。直接写 `backdrop-filter` 时必须同时给 `-webkit-backdrop-filter` 和不支持环境的半透明兜底（`@supports` 渐进增强），模糊半径控制在 10-22px。**玻璃底色一律取令牌，禁止页面里硬编码 rgba 白色**：`--glass-bg`(0.62 标准卡)、`--glass-bg-strong`(0.88 抽屉/弹窗)、`--glass-border`、`--glass-nav-bg`。`.glass` 的背景带 `!important`——页面样式加载晚于全局样式，卡片自带白色背景会覆盖它（踩过的坑：玻璃卡显示成白实卡）。透明度下限 0.5，否则与渐变背景对比度不足；毛玻璃面板必须真的有内容从其下方经过才有意义。**全局卡片已玻璃化**：页面根节点背景必须 transparent（渐变底在 App.vue 的 page 选择器上），卡片统一挂 .glass 工具类（.card/.bl-group/.rank-card/.cat-card/.cell-group 等），新页面照此办理；统计/日历/首页有 .bg-blobs 装饰光斑（z-index: -1）供玻璃折射；**同一页的光斑要大而柔（blur ≥ 110px、铺满卡片区）**，让各卡取色接近，避免同屏玻璃一偏白一偏透。
- 首页左上角为**侧滑抽屉**（wd-popup position="left"），抽屉菜单项与图标定义在 index.vue 的 `drawerMenus`；新增一级页面入口时同步加进去。
- 分类管理的排序交互是**长按拖拽**（categories.vue 的 startDrag/onDragMove/onDragEnd，矩形命中 + 松手落位 + 立即持久化），不要改回点菜单移一步的旧交互；子类面板渲染在父类行的紧下方（cat-block 内）。
- 图表约束：柱状图用 CSS flex 实现；环形图用 canvas 旧 API（`uni.createCanvasContext`，需传组件实例 proxy）；不要引入 echarts 等图表库。
- 新增页面必须：在 `pages.json` 注册 + 写入本文件第 6 节自查清单对应项。

## 3. 目录职责（禁止重复造轮子）

- `common/cats.js` — 分类体系唯一来源：默认分类播种、`uni.storage`（key：`muyian_categories_v1`）读写、图标/调色板查询、子类归并（`topNameOf`）。页面禁止自带分类常量。
- `common/fmt.js` — 金额千分位（`fmtMoney`）、数字缩写（`fmtNum`/`fmtTick`）、按日分组（`groupByDay`）、日期标签、键盘金额表达式求值（`evalExpr`）。
- `common/lunar.js` — 农历与节日（1900–2100），仅供日历角标使用。
- `common/merchants.js` — 商户分类建议：内置词典（商户关键词→分类叶子名，有序数组、具体词优先）+ 用户修正回流（storage key `muyian_merchant_cats_v1`，上限 500 条）。`suggestCategory(merchant, isIncome)` / `learnCategory(merchant, leafName)`。词典不定义分类体系，叶子名必须与 cats.js 一致；原生只负责提取商户名（草稿 `merchant` 字段），建议与回流都在页面侧。
- `components/bill-list/bill-list.vue` — 按日分组账单列表，首页与日历页共用；新列表需求优先扩展它，不要复制粘贴一份。
- 备注中的图片以 `{{img:<path>}}` token 存在 `orderRemark` 末尾，由 bill-list 负责解析展示；其他页面不得另定图片存储格式。

## 4. 数据约束

- SQLite：`files/muyian.db`，表 `bills(id, year, month, day, clock, money, bankName, costType, orderRemark, source, sourcePkg, createdAt, reimb, starred)`。
- **金额符号约定：支出为负、收入为正**（`money` 直接带符号；页面展示时再取绝对值加正负号）。所有新增统计/展示代码必须沿用，禁止另存绝对值或反向符号。
- `costType` 存**叶子名**（选了子类存子类名，否则存一级名）；排行/环图统计时用 `topNameOf()` 归并到一级分类。分类改名不回写历史账单。
- 分类数据只存前端 storage（JSON），原生层不感知分类体系；原生解析出的默认 `costType`（消费/收入）允许与分类表不匹配，页面侧兜底。
- 新增表字段必须走 `openDb()` 里的 try-catch `ALTER TABLE` 兼容旧库，并在 `billJson()` 补输出（列不存在时给默认值）。
- 单次查询上限：月 500 行、年 5000 行，够用且防止内存膨胀；不要一次性查全表。

## 5. UTS 插件约束（muyian-bill-listener）

- 跨语言 API 一律**同步调用 + JSON 字符串进出**，页面侧 `JSON.parse`。这是为了规避 UTS number ↔ Kotlin Int/Double 映射坑；新增 API 禁止直接传对象/数值结构。
- 新增原生能力必须**三个文件同步改**：`interface.uts`（声明）→ `app-android/index.uts`（薄封装 + context 判空兜底返回错误 JSON）→ `app-android/hybrid.kt`（Kotlin 实现，@JvmStatic）。
- Kotlin 侧只用 Android 框架 API + org.json，禁止引入第三方依赖。
- `hybrid.kt` 是核心：白名单/黑名单过滤、解析正则、三层去重（捕获级 5 分钟、草稿级 2 分钟、账单级 2 分钟）、草稿 TTL 7 天。改解析逻辑必须保持这些窗口与 TTL 语义。
- 无障碍服务注册名固定全限定名 `muyian.bill.listener.MuyianBillAccessibilityService`，配置在插件 `res/xml/muyian_accessibility_config.xml`；辅助通知监听服务为 `muyian.bill.listener.BillNotificationListener`。
- **必须保留 `utssdk/web/index.js` 桩**：vite 依赖扫描（dep-scan）不识别 `#ifdef`，H5 编译会解析 `@/uni_modules/muyian-bill-listener` 导入，缺 web 桩直接报 resolve 错误。桩的导出签名与 interface.uts 保持一致（JSON 字符串进出）。

## 6. 页面侧条件编译约束

- 所有插件调用必须包在 `// #ifdef APP-PLUS ... // #endif` 内导入原生实现，并**成对**提供 `// #ifndef APP-PLUS` 下从 `@/common/web-bill-api.js` 的同名导入——web-bill-api 是 H5 的 localStorage 真实现（协议与 interface.uts 一致），Web 端记账/草稿/统计全流程可用。
- 新增插件 API 时**三处一起加**：interface.uts 声明、web-bill-api.js 的 H5 实现、hybrid.kt 的 Kotlin 实现。
- 页面内 `load()` 等数据加载函数**不要**再用 `#ifdef APP-PLUS` 包裹函数体（两端实现都已就绪，包裹会导致 Web 端不加载数据）；仅 `plus.*` 这类端能力调用保留 `#ifdef`。
- 页面数据加载统一在 `onShow` 里做（返回页面自动刷新）；首页额外有 4 秒草稿轮询（onShow 启动、onHide 清理），新增轮询照此模式管理生命周期。

## 7. 日期与时间约定

- 账单日期三字段 `year/month/day` 为 int；页面间传日期用 `YYYY-MM-DD` 字符串（`saveBillEx`/`updateBill` 的 date 参数同格式，原生用正则 `(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})` 解析）。
- `clock` 格式固定 `M月d日 HH:mm`；"今天/昨天/前天/周X"标签由 `dayGroupTitle` 统一计算，不要散落实现。
- 周起始为周一；7 日统计按自然日（含今天）向回取，跨月时合并所需月份查询。

## 8. 提交前自查清单

改动后逐项过一遍，任何一项不满足不交付：

1. `pages.json` 已注册新页面、easycom 规则未被破坏；
2. 新插件 API 三文件同步，页面侧有 `#ifdef`/`#ifndef` 成对 stub 且签名一致；
3. wd 组件布尔 prop 全部显式绑定（全局搜裸 `left-arrow|placeholder|safe-area-inset-top|is-link|hide-text|clickable`）；
4. 金额符号约定、`costType` 叶子名约定未被新代码破坏；统计口径经过 `topNameOf` 归并；
5. 涉及 DB 的改动带旧库 ALTER 兼容；
6. 涉及 `hybrid.kt` 的改动提示用户重新制作自定义调试基座；
7. 无新增网络请求、无隐私越权（第 0 条红线）；
8. 跑一次真机流程：记一笔（含改日期/再记）→ 首页/日历/统计/排行/日报表数据一致 → 分类增删排序后记一笔页与归并仍正确 → 测试草稿确认与丢弃闭环。
