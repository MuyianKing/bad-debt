# UI 基础规范

> 主题与毛玻璃见 `21-theme-and-glass.md`；首页菜单页、吸顶导航、拖拽见 `22-layout-interactions.md`。

## 页面结构与导航

- 单 Activity：`MainActivity` + `state/AppState` 的导航栈（`Screen` 枚举、`stack`、`current`、`navigate(screen, motion)`、`pop()`）。页面切换由根节点的 `AnimatedContent` 统一处理，进出栈动画按 `navForward` 区分；`navMotion` 决定方向：默认 `Vertical`（从下往上），菜单页及其菜单项用 `Horizontal`（从右往左），菜单页本身从左缘推进（`transitionSpec` 特判），出栈时按被弹出页面的进入方向原路返回。
- 新增页面必须同时在 `state/AppState.kt` 的 `Screen` 里加枚举值，并在 `MainActivity` 的 `when (screen)` 中渲染；不要在页面内部自己写 `Activity` 跳转。
- 全局浮层（Toast、确认框、操作菜单、滚轮选择器、日期选择器、图片预览）由 `AppState` 的状态位驱动，`MainActivity` 末尾固定挂 `AppToastHost` / `ConfirmDialogHost` / `ActionSheetHost` / `PickerSheetHost` / `DatePickerHost` / `ImagePreviewHost`，页面只调 `app.showToast()` / `app.confirm()` / `app.showPicker()` 等方法。
- 页面根节点用 `statusBarsPadding()` 让顶栏避开状态栏；可滚动内容底部用 `WindowInsets.navigationBars.asPaddingValues()` 补安全区；带输入法的页面用 `imePadding()`。不要写死状态栏高度。
- 内页统一用 `ui/components/Basics.kt` 的 `TopBar(title, onBack, solid, titleContent, actions)`；滚动后把 `solid` 置 true 切到半透明底，避免列表从标题下穿过。

## 设计单位

- 布局与字号用 750 设计稿等比单位：`rpx(value)` 返回 `Dp`，`rsp(value)` 返回 `TextUnit`，换算比例由 `MainActivity` 注入的 `LocalRpx`（`screenWidthDp / 750`）决定。
- 图标尺寸固定用 `dp`（如 `22.dp`），不要拿 `rpx` 去缩放图标。新增组件沿用 `rpx/rsp`，不要混用裸 `dp` 做间距。

## 图标

- 界面与分类图标统一走 `ui/icons`：`IconData.kt` 存 24x24 viewBox 的 SVG 路径（描边 `PATHS` 与实体 `FILLED_PATHS` 两套），`Icons.kt` 的 `AppIcon(name, color, sizeDp, strokeWidth)` 负责解析与 Canvas 绘制。
- 新增图标把 24x24 路径加进 `IconData.kt`；分类数据里的 `icon` 字段存**图标名**（非 emoji），自建分类默认 `Cats.DEFAULT_ICON = "grid"`，可选集见 `data/Categories.kt` 的 `ICON_CHOICES`。
- 分类图标用**实体填充模式**（`IconData.FILLED_PATHS`，evenodd 规则镂空、部件间禁止重叠）；描边模式只用于界面功能图标。禁止引入图标字体或 png 图片图标。
- SVG 圆弧必须沿当前子路径 `lineTo` 继续，用 `moveTo` 会把实体剪影切成碎片、渲染成空心残缺轮廓。

## 设计令牌与配色

- 配色/圆角/阴影统一走 `ui/theme/Theme.kt` 的 `Palette`：`primary` / `primaryDeep` / `primaryFill`、`out`（支出红）、`inn`（收入绿）、`text` / `sub` / `dim` 文字层级、`bg` / `card` / `line` / `lineSoft` / `fill` 面板、`key*` 键盘键帽、`gradPrimary` 等主题渐变。页面通过 `LocalPalette.current` 取色，**禁止硬编码主题色**。
- 填充面（按钮、总览卡、键盘）上的文字与图标用 `inkOnPrimary` / `onPrimarySub`；不要深色字压在主题色上。
- 装饰性渐变只复用主题令牌（`gradPrimary` / `gradPrimaryStrong` / 分类色块）；不要临时拼颜色，也不要新增光斑、光晕、发光描边。
- 分段开关/页内小切换用中性轨道 + `raised` 滑块 + 主题色文字，不要整块主题色填充。

## 通用组件

- `Modifier.glass(shape, alpha)`：半透明玻璃卡（默认）；`Modifier.liquidGlass(shape, alpha)`：液态玻璃卡（采样背景图，未设背景图时全透明，不画白边），由设置页的玻璃材质开关决定首页账单卡用哪一档；`Modifier.solidCard(shape)`：实底卡；`Modifier.pressable(color, onClick)`：按压反馈。
- `GroupTitle`（区块标题）、`AppFab`、`AppSwitch`（开关）、`MiniToggle`、`StateChip`、`IconBlock`、`StatCell`、`DividerLine`、`EmptyCopy`、`TopBar`、`PrimaryButton`、`GhostButton`。
- `BillList` / `BillRow`：按日分组账单列表，首页与日历页共用；`flush` 为 true 时去掉自带卡片底，供嵌进外层卡片。
- `MascotFace` / `MascotImage`：角色表情渲染；`WheelPicker`：滚轮选择器。
- 新增同类基础件优先扩展这些文件，不要在各页面复制一份。

## 统计页面板写法（勿回退）

- 总览卡只保留「标签 + 大号金额 + 三项指标」，指标靠细分隔线分组，不要再套半透明面板 + 2×2 分格线，也不要星星/圆环装饰。
- 大号金额用 `rsp(76)` / `FontWeight.SemiBold`，字距收紧；总览卡毛玻璃下用 `primaryFill` 实底 + 白字，液态玻璃下与首页头区一致，改用 `liquidGlass` + 主题文字色。
- 空状态只留两行文案，不放图标。
- 区块标题图标用实心字形直接着主题色，不要彩色方块底座。
- 柱状图必须有 25% 递增的网格线，且**柱高按 `axisMax`（取整后的刻度上限）换算**，不能按实际最大值换算，否则柱顶与左侧刻度对不上；报表表格日期左对齐、金额右对齐、表头弱化到 `rsp(20)` 的 `dim` 色。
- 柱状/折线图统一叠加一条虚线均值参考线，颜色用 `p.sub` 降透明度，与柱子/折线共用 `barGrow` 从底部升起；均值分母只算已显示的格数（月视图=本月已过天数，年视图=今年已过月份），图例「日均 / 月均 + 金额」右对齐放在标题行下方，不要在图内叠加标签。

## 图表

- 柱状图、环形图、折线图都用 Compose `Canvas` 手绘（`StatsScreen` 的 `Canvas` / `drawArc`），不要引入 echarts 等图表库。
- 动画统一走 `Motion.kt`：金额 `countUp()`，柱状/环形用 `Animatable` 0→1 配合 `stagger(progress, index, count)` 错峰生长。

## 新增页面

- 新增页面必须：在 `Screen` 注册 + 在 `MainActivity` 的 `AnimatedContent` 中渲染 + 对照 `docs/80-checklist.md` 逐项确认。
