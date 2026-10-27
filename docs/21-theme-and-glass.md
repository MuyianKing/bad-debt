# 主题与毛玻璃

> 基础组件与绘制规范见 `20-ui-basics.md`。本文件所有令牌的最终本体都在 `ui/theme/Theme.kt`。

## 主题切换（悟空黄 / 哪吒红 / 沙僧黑）

- 主题唯一来源是 `ui/theme/Theme.kt`：`Wukong`（默认）、`Nezha`、`Shaseng` 三个 `Palette`，`AllPalettes` 是全部主题，`paletteOf(id)` 按 id 取，`LocalPalette` 是 Compose 侧的当前主题。
- 当前主题由 `AppState.themeId` 持有，持久化 key `muyian_theme_v1`；切换走 `AppState.setTheme(id)`，它会写 SharedPreferences、更新 `LocalPalette`，并调用 `applyCardTheme()` 同步原生确认弹层的强调色。
- **原生弹层不认 Compose 主题**：`applyCardTheme()` 通过 `MuyianBillCore.setCardThemeColor(context, hex)` 传色。深色主题的页面强调色是亮银，而原生弹层是浅色底，所以深色主题传固定的深色 `#1A1A1D`，浅色主题传 `palette.primary`；新增主题要同时确认这一分支。
- 页面取色一律 `LocalPalette.current.xxx`，不要写死主题色；只有在 SVG/Canvas 必须字面色的地方才用 `palette` 字段。

## 主题与角色解耦

- 主题只管配色。主题对应哪个角色由 `data/Mascots.kt` 的 `THEME_MASCOT` 决定：`wukong → wukong`、`nezha → nezha`、`shaseng → nezha`。悟空已预置但 `ready = false`，素材到位前 `Mascots.active()` 回退到哪吒。
- `AppState.activeMascot`、`mascotStyle(mood, mode)`、`baseMascotStyle` 是页面取角色的入口；表情持久化 key `muyian_mascot_style_v1:<角色id>`，旧 key `muyian_nezha_v1` 会自动迁移。
- `autoMood`（key `muyian_mascot_auto_mood_v1`，默认开启）控制空态/成功态是否自动换表情；关闭后固定基础表情。
- 新增/启用角色只改 `Mascots.kt` 与 `app/src/main/assets/mascots/` 素材，页面不要写死角色名、图片路径或按主题写 if/else。

## 深色主题（沙僧黑）

- `Palette.dark = true` 时，所有页面、浮层、键盘、滚轮都要用深色令牌：`bg` / `pageWash` / `card` / `line` / `fill` / `raised` / `key*`。禁止写死 `Color.White`、`#fff`、`rgba(29,33,41,...)` 这类浅色值。
- 深色主题的 `primary` 是**强调色**（亮银，用于柱条、选中描边、图标字面色）；承载白字的实底填充用 `primaryFill` / `primaryPress`（统计总览卡、记一笔保存键）。
- `Cats.colorHexOf()` + `ui/theme/hex()` 与 `ui/theme/tintColor(base, dark)` 已按主题处理浅底/深底混合，页面不要自己拼浅色底。
- 日期/期间选择不要用平台 `DatePickerDialog`（深色主题下是白底），统一用 `DatePickerHost`（`WheelPicker` 自绘）或 `AppState.showDatePicker()`。
- 状态栏图标由 `MainActivity` 按「当前页面 + 首页滚动 + 主题深浅」统一设置 `LIGHT_STATUS_BAR`；页面不要自己改。
- **新增页面或改配色时必须三个主题各看一遍**（悟空黄、哪吒红、沙僧黑）。

## 彩色图标

- 分类图标在账单列表、统计、分类管理三处用分类色做圆角块（约 30% 圆角），里面压白色实体图标；分类色取 `Cats.colorHexOf()` 后经 `hex()` 转 `Color`，不要另建色板。
- 子类/图标选择器用 `tintColor(色, dark)` 淡底 + 同色实体图标（层级更轻）。
- **记一笔页的分类宫格是例外**：保持克制的原版样式——圆形透明底 + 灰色实体图标，选中项用 `primaryTint` 淡圆底 + `primaryDeep` 主题色图标。
- 区块标题的图标用实心字形直接着主题色（无底座）；设置项/抽屉这类列表里的功能图标用白色图标压纯色块上。

## 毛玻璃与统一底色

- 毛玻璃统一用 `ui/components/Basics.kt` 的 `Modifier.glass()`：浅色主题是白色 72% + 白色描边，深色主题是 `card` 82% + 10% 白描边，外加 `shadow(10.dp)`。
- 实底卡用 `Modifier.solidCard()`；需要强调的实底区域（如统计总览卡）直接用 `primaryFill`，不要再叠 `glass` 把底色冲掉。
- **首页 / 抽屉 / 记一笔页统一底色**：都用 `pageWash`（浅色是极淡主题色，深色是纯黑），三处保持一致。
- 玻璃面层次只靠描边 + 阴影；不要用 `Canvas` 画光斑、光晕或 `.page::before` 式的折射层来伪造玻璃。
- 所有页面的普通卡片都走 `glass` 或 `solidCard`；例外只有统计页总览卡这类 `primaryFill` 实底。
