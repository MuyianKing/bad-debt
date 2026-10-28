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

- `Palette.dark = true` 时，所有页面、浮层、键盘、滚轮都要用深色令牌：`bg` / `card` / `line` / `fill` / `raised` / `key*`。禁止写死 `Color.White`、`#fff`、`rgba(29,33,41,...)` 这类浅色值。
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

## 毛玻璃与液态玻璃

- 玻璃材质有两档，由设置页「外观 → 玻璃材质」切换，持久化 key `muyian_glass_style_v1`（`frost` / `liquid`），默认 `frost`。读取走 `AppState.glassStyle`，切换走 `AppState.updateGlassStyle()`，类型是 `data/Prefs.kt` 的 `GlassStyle`。
- 毛玻璃用 `Modifier.glass()`：浅色主题是白色 72% + 白色描边，深色主题是 `card` 82% + 10% 白描边，外加 `shadow(10.dp)`。
- 液态玻璃用 `Modifier.liquidGlass()`：内置 `com.kyant.backdrop`（Kyant0/AndroidLiquidGlass 2.0.0，Apache-2.0，许可与改动见 `android/third_party/kyant-backdrop/`）做真采样：按卡片窗口位置取一整屏底图的一块，做模糊、边缘折射和轻微提亮，再叠阴影。边缘只画一条发丝级（0.6dp）的迎光高光：左上最亮、往下渐隐；不要加宽描边或整圈高光（会变成“大白边”）。
- 底图由 `ui/components/GlassBackdrop.kt` 提供：设置页「外观 → 玻璃背景图」单独设置（key `muyian_glass_bg_v1`）；没设置时**全透明**，不掺主题色。底图不会画到页面上，页面底色仍是统一的 `bg` 纯色。
- 液态玻璃不加宽描边/边框；迎光侧那条发丝级高光是唯一的“边”，卡片边界仍主要靠折射和阴影，不要给卡片补 border。
- 液态玻璃下 `sub` / `dim` 两档灰阶会经 `Palette.liquidReadable()` 加深（玻璃透底强，叠壁纸后原灰阶会发虚）；`MainActivity` 只在液态玻璃模式提供这份调色板，毛玻璃仍用原令牌。改灰阶时两档都要看。
- API 31 以下没有 `RenderEffect`，`liquidGlass` 退回自绘的半透玻璃体（不采样）；设置页预览也用这一档。
- 全局生效：`MainActivity` 用 `GlassBackdropHost` 铺一层全屏壁纸（玻璃背景图，50% 压在 `p.bg` 上），并 provide `LocalGlassStyle`；`Modifier.glass()` 在液态设置下自动委托给 `liquidGlass()`，所以全站卡片（含设置/统计/日历）都跟着切换。需要固定毛玻璃的场合用 `frostGlass()`（设置页的预览就是）。
- 首页 Hero 和抽屉头部在液态玻璃下本身就是一块玻璃（没有自己的背景）；毛玻璃下用主题渐变。
- 页面根部不要再写 `.background(p.bg)`，底色由 `GlassBackdropHost` 统一铺；抽屉面板用 `glassPageBackground()`。
- 实底卡用 `Modifier.solidCard()`；需要强调的实底区域（如统计总览卡）直接用 `primaryFill`，不要再叠 `glass` 把底色冲掉。
- **所有页面统一底色**：都用 `p.bg`（与统计页一致；浅色是极淡主题色，深色是纯黑），首页、抽屉、记一笔、统计、日历、分类、设置保持一致，且不受玻璃材质开关影响。
- 毛玻璃的层次只靠描边 + 阴影；液态玻璃的采样/模糊/折射全部封在 `liquidGlass()` 内部，页面里不要自己用 `Canvas` 画光斑、光晕或 `.page::before` 式的折射层。
- 所有页面的普通卡片都走 `glass` / `liquidGlass` / `solidCard`；例外只有统计页总览卡：毛玻璃下保持 `primaryFill` 实底 + 白字，液态玻璃下改用 `liquidGlass` + 主题文字色（与首页头区一致）。
