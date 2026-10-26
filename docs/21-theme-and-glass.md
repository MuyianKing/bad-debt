# 主题与毛玻璃

> 基础样式与纯色约束见 `20-ui-basics.md`。本文件所有令牌的最终本体都在 `App.vue`。

## 主题切换（悟空黄 / 哪吒红 / 沙僧黑）

- **主题切换（悟空黄 / 哪吒红 / 沙僧黑）**：`common/theme.js` 是主题唯一来源（`THEMES` / 响应式 `theme` / `themeClass` / `setTheme` / `refreshTheme`，storage key `muyian_theme_v1`）。令牌本体在 App.vue：`page` 是默认「悟空黄」，`.theme-nezha` / `.theme-shaseng` 分别覆盖为「哪吒红」「沙僧黑」。**每个页面根节点必须绑 `:class="['page', themeClass]"`，并在 `onShow` 调 `refreshTheme()`**；需要字面色的地方（`app-icon` 的 color、canvas 画笔）用 `theme.primary`/`theme.primaryDeep`/`theme.primaryFill`（中性图标用 `theme.ink`/`theme.inkSub`），不要写死主题色或深灰。主题相关的阴影/柱条/光斑走令牌（`--shadow-primary-sm/-lg`、`--grad-bar`、`--blob-a/b/c`）。原生弹层不认 CSS 变量：切换主题时页面调用 `setCardThemeColor(theme.cardColor || theme.primary)` 同步——深色主题的页面强调色是亮银，而原生弹层是浅色底，必须传 `cardColor` 这种深色值，不能直接传 `primary`；新增主题要同时补 `cardColor`（必要时补 `dark: true`）。

## 深色主题（沙僧黑）

- **深色主题（沙僧黑）的额外约定**：`theme.dark` 为 true 时 `themeClass` 会额外带 `wot-theme-dark`，让 wd-cell / wd-action-sheet / wd-popup 等 wot 组件切到深色底（配合 `.theme-shaseng` 里的 `--wot-dark-*`）。深色主题的 `--c-primary` 是**强调色**（亮银，供给柱条/选中描边/图标字面色），**承载白字的实底填充改用 `--c-primary-fill` / `--c-primary-press`**（统计总览卡、记一笔保存键）；`--c-key-*` 是键盘键帽专用令牌，`--c-raised` 是分段滑块/底部弹层这类浮起面。`common/cats.js` 的 `tintOf()` 在深色主题按黑色混合，页面不要自己拼浅色底。**新增页面或改配色时必须两套主题各看一遍**，禁止写死 `#fff` / `rgba(29,33,41,...)` 这类浅色值。

## 彩色图标

- **彩色图标规范**：分类图标在 `bill-list` / `stats` / `categories` 三处用分类色做圆角块（圆角约 30%：64rpx 块配 20rpx 圆角），里面压 `<app-icon :solid="true" color="#ffffff">` 白色实体图标；**统计页已改为纯色块（`gradOf` → 分类原色，去掉阴影），其余两处改版时同步换成纯色**。子类/图标选择器用 `tintOf(色)` 淡底 + 同色实体图标（层级更轻）。**记一笔页（add）的分类宫格是例外**：保持克制的原版样式——圆形透明底 + 灰色实体图标（#7a8194），选中项用 `--c-primary-tint` 淡圆底 + `theme.primaryDeep` 主题色图标。区块标题的图标一律用**实心字形直接着主题色**（无底座）；设置项/抽屉这类列表里的功能图标仍用白色图标压纯色块上，禁止灰底灰图标的旧写法。

## 毛玻璃与统一底色

- **毛玻璃用 App.vue 里的全局工具类**：`.glass`（通用半透明卡）、`.glass-nav`（内页 wd-navbar，传入 custom-class）。直接写 `backdrop-filter` 时必须同时给 `-webkit-backdrop-filter` 和不支持环境的半透明兜底（`@supports` 渐进增强），模糊半径控制在 10-22px。**玻璃底色一律取令牌，禁止页面里硬编码 rgba 白色**：`--glass-bg`(0.52 标准卡)、`--glass-bg-strong`(0.88 抽屉/弹窗)、`--glass-border`、`--glass-nav-bg`。`.glass` 的背景带 `!important`——页面样式加载晚于全局样式，卡片自带白色背景会覆盖它（踩过的坑：玻璃卡显示成白实卡）。透明度下限 0.5，否则与渐变背景对比度不足；毛玻璃面板必须真的有内容从其下方经过才有意义。**首页 / 抽屉 / 记一笔页统一底色**：都用极淡主题色纯色底 `--c-page-wash`（黄/红各约 6% 主题色 + 白，深色主题为纯黑），三处必须保持一致；**折射层不铺**：卡片后面没有可模糊的内容时，玻璃就只是"半透明 + 细边 + 阴影"，不要用 `.page::before`、光斑、光晕去伪造玻璃（已按要求删掉，属纯色/无光效的硬约束，见 `20-ui-basics.md`）。纯色底上白玻璃和底色只差约 2%（#fffdf7 vs #fef9ed），这是该约束下的预期效果——**不要再用"把卡改成 `--c-card` 实底白"来救**（那样玻璃感就没了），宁可接受通透感弱一点。玻璃面自己的层次只靠 `--glass-border` + 卡片阴影。**所有页面的卡片都走 `.glass`**（首页、账单分组、日历、设置、分类、统计、记一笔键盘面板）；唯一例外是统计页顶部的总览卡——它用 `--c-primary-fill` 实底承载白字，挂 `.glass` 会把底色冲掉。
