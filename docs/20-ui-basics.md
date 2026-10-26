# UI 基础规范

> 主题与毛玻璃见 `21-theme-and-glass.md`；首页抽屉、沉浸式顶栏、拖拽见 `22-layout-interactions.md`。

## 组件、页面结构与单位

- 组件库统一 **wot-design-uni**，通过 `pages.json` 的 easycom 自定义规则引用（`^wd-(.*)` → node_modules）。页面里禁止手写同类基础组件（按钮/弹层/单元格/进度条等优先用 wd-*）。
- 全部页面 `navigationStyle: custom`。自定义头部的页面必须处理状态栏高度（`var(--status-bar-height)`）与底部安全区（`env(safe-area-inset-bottom)`）；带 wd-navbar 的页面用 `:fixed="true" :placeholder="true" :safe-area-inset-top="true"`。
- **布尔 prop 必须显式绑定**（`:is-link="true"`、`:hide-text="true"`）。禁止裸属性写法（`is-link`）——app-vue 下裸属性传空串，按 falsy 处理，会导致 fixed/left-arrow 等静默失效。
- 样式单位用 rpx；全局视觉规范以 App.vue 设计令牌为准：页面底 `--c-bg`(#f7f4ec 暖米色)、卡片白底圆角 `32rpx`、实底卡片用 `--shadow-card-soft`（玻璃卡仍用 `--shadow-card`）、主题黄/支出红/收入绿见令牌、正文 `--c-text`、次要 `--c-sub`。改色板先改 App.vue 令牌，保持一致。

## 图标

- **界面与分类图标全部统一用 `app-icon` 组件**（`components/app-icon/app-icon.vue` + `common/icons.js` 的 SVG data-uri 线性图标，Feather 风格）；新增图标往 `icons.js` 的 PATHS 里加 24x24 stroke 路径。分类数据里的 icon 字段存**图标名**（非 emoji），自建分类默认 `grid`，可选集见 `cats.js` 的 ICON_CHOICES。分类图标一律用**实体填充模式**（`<app-icon :solid="true">`，路径在 icons.js 的 FILLED_PATHS，evenodd 规则镂空、部件间禁止重叠），描边模式仅用于界面功能图标。禁止引入图标字体（iconfont/wd-icon 已弃用于新代码）或 png 图片图标。

## 设计令牌与配色

- **配色/圆角/阴影统一走 `App.vue` page 级 CSS 变量（设计令牌）**：`--c-primary`(#f7a600 主题黄)、`--c-out`(#f0655e 支出红)、`--c-in`(#22b877 收入绿)、`--c-text/--c-sub/--c-dim` 文字层级、`--c-bg/--c-card/--c-line/--c-line-soft/--c-fill` 面板（后两个是中性细分隔线与浅底，开关轨道/空状态底板用）、`--shadow-card/--shadow-card-soft` 卡片阴影。改主题只动 App.vue，页面禁止硬编码主题色（一次性中性色 #f4f6fa/#5b6170 等允许）。
- **禁止渐变、禁止光斑光晕（硬约束）**：不许用 `linear-gradient` / `radial-gradient` 做填充、质感或高光，也不许做装饰光斑、光晕、光染、发光描边（页面级氛围光、卡片顶部渐隐高光都算）。**新代码一律纯色**；存量 `--grad-*` 只为尚未改版的旧页面保留，改到哪页就把那页的渐变换成对应纯色令牌，并同步删掉该页对旧令牌的引用，禁止新增渐变令牌或 `--blob-*` 之类光效令牌。阴影（`--shadow-*`）只用于表达层级，不许做成彩色光晕。
- **填充面用纯色，不用渐变**：统计页已全部改成纯色（总览卡 `--c-primary`、柱条 `--c-primary`、分类色块直接用分类原色、标题图标着 `theme.primaryDeep`）；新页面与改版页面都照此办理，`--grad-*` 令牌只为尚未改版的旧页面保留，改到哪页就把那页的渐变换成对应纯色。填充面上的文字与图标**必须是白色**（`--c-ink-on-primary`，辅助文字 `--c-on-primary-sub`）；禁止深色字压在主题色上，也禁止硬编码主题色（SVG/canvas 的字面色除外）。
- **分段开关的选中态例外**：`wd-segmented`（月/年）与页内小型切换（柱状/折线、支出/收入）用**中性轨道 + 白色滑块 + 主题色文字**（轨道 `--c-fill`、滑块 `--c-raised` + `0 2rpx 8rpx rgba(29,33,41,0.10)`、选中文字 `--c-primary-deep`），禁止整块主题色填充——大面积饱和色块做开关是上一版"显旧"的主因。

## 统计页面板写法（勿回退）

- **统计页的现代面板写法（勿回退）**：总览卡只保留"标签 + 大号金额 + 三项指标"（指标靠细分隔线分组，**禁止再套半透明面板 + 2×2 分格线**，也不要星星/圆环装饰）；大号金额 76rpx/600、`letter-spacing: -2rpx`；卡片统一 `--shadow-card-soft`；**空状态只留两行文案、不放图标**（小尺寸实心图标压在灰底上会糊成一团）；**区块标题图标用实心字形（`:solid="true"`，路径加在 icons.js 的 FILLED_PATHS）直接着主题色，不要彩色方块底座**；柱状图必须有 25% 递增的网格线，且**柱高按 `axisMax`（取整后的刻度上限）换算**，不能按实际最大值换算，否则柱顶与左侧刻度对不上；报表表格日期左对齐、金额右对齐、表头弱化到 20rpx `--c-dim`。

## 图表

- 图表约束：柱状图用 CSS flex 实现；环形图与折线图用 canvas 旧 API（`uni.createCanvasContext`，需传组件实例 proxy——实例要在 setup 阶段先存下来，事件回调/定时器里 `getCurrentInstance()` 是 null）；不要引入 echarts 等图表库。

## 新增页面

- 新增页面必须：在 `pages.json` 注册 + 对照 `docs/80-checklist.md` 逐项确认。
