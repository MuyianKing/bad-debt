# 目录职责（禁止重复造轮子）

> 新增页面/组件/工具函数前先读本文件：同一件事只能有一个唯一来源，页面里不许另起一套。

## data/（纯 Kotlin 数据）

- `Models.kt` — 数据模型与通用工具：`Bill` / `Category` / `SubCat` / `CategoryData` / `DayGroup` / `AddDraft`，金额格式化（`fmtMoney` / `fmtNum` / `fmtTick` / `fmtMoneyInput`）、表达式求值（`evalExpr`）、按日分组（`groupByDay`）、日期标签（`dayGroupTitle`）与 `pad2` / `todayStr` / `daysInMonth` / `weekdayCN`。
- `Bills.kt` — 账单读写唯一入口：调用 `MuyianBillCore` 的 SQLite 方法，把 JSON 解析成 `Bill`；页面禁止直接拼 SQL 或自己解析账单 JSON。`month()` / `year()` / `all()` / `save()` / `update()` / `delete()` 是页面使用的 API；`save/update` 返回 `BillWriteResult`，保留原生错误原因。`toJson()` 是账单 JSON 的唯一出站定义。
- `Categories.kt`（`Cats`）— 分类体系唯一来源：默认分类播种、SharedPreferences 读写（key `muyian_categories_v2`）、图标/调色板、`iconOf` / `topNameOf` / `colorHexOf` / `parentOf` / `exists`。数据层只返回 hex，页面自行用 `hex()` 转 `Color`；页面禁止自带分类常量。
- `Prefs.kt` — 本地设置存储（SharedPreferences `muyian_native`）：`str` / `bool` / `putStr` / `putBool`，并负责旧版本 `DCStorage` 的一次性只读迁移（key `legacy_migrated_v1`）。页面不要直接持有 `SharedPreferences`。
- `Mascots.kt` — 角色（吉祥物）唯一来源：`MASCOTS` / `THEME_MASCOT` / `NEZHA` / `WUKONG`、表情列表、`active` / `styleOf` / `resolve` / `setStyle` / `setAutoMood`。存储 key `muyian_mascot_style_v1:<角色id>`、`muyian_mascot_auto_mood_v1`；素材在 `app/src/main/assets/mascots/`。新增角色只改这个模块 + 放图 + 改主题映射。
- `Qianji.kt` — 钱迹 Excel 的纯数据处理：表头定位、时间/类型/金额校验、汇总统计、分类计数、钱迹分类 → 叶子名映射、入库账单拼装（`parseSheet` / `buildLeafColumns` / `buildMappings` / `buildBillsJson` / `orderNos`）。原生只负责把 xlsx 读成「表头 + 原始单元格行」并批量写库。
- `Lunar.kt` / `LunarData.kt` — 农历与节日（1900–2100），仅供日历角标使用。
- `Stats.kt` / `CalendarModel.kt` / `WeekStats.kt` / `Dates.kt` — 统计、日历、最近 7 日与日期格式化的纯计算；页面只负责动画与绘制。
- `Backup.kt` / `ImportFlow.kt` / `FilePick.kt` / `Batch.kt` — 备份解析与恢复、钱迹导入流程、SAF 选文件轮询、分块写库；页面不再直接解析原生 JSON。
- `NativeStatus.kt` — 无障碍/通知/悬浮窗/MIUI 弹层/电池/自愈状态的类型化快照与系统页面跳转。
- `Files.kt` — 应用私有目录图片复制/删除（账单图、玻璃背景图共用）。

## state/（应用状态）

- `AppState.kt` — 门面，持有 `ThemeState` / `CategoryState` / `NavState` / `OverlayState` / `SettingsState`，保留页面使用的属性名。
- `ThemeState.kt` — 主题、角色、玻璃背景图与原生弹层强调色。
- `CategoryState.kt` — 分类读写与深拷贝快照。
- `NavState.kt` — `Screen`、导航栈、记一笔草稿、首页月份。
- `OverlayState.kt` — Toast/确认框/操作菜单/选择器/图片预览状态。
- `SettingsState.kt` — 最近任务可见性、`resumeToken`、`billsVersion`、版本号。

## ui/

- `ui/theme/Theme.kt` — 设计令牌唯一来源：`Palette`、`Wukong` / `Nezha` / `Shaseng`、`AllPalettes` / `paletteOf` / `LocalPalette`、`LocalRpx` 与 `rpx()` / `rsp()`、`tintColor()` / `categoryGradient()`。
- `ui/icons/Icons.kt` + `IconData.kt` — 图标唯一来源：24x24 viewBox 路径数据（描边与实体两套）与 `AppIcon` 绘制。新增图标只往 `IconData.kt` 加路径。
- `ui/components/Basics.kt` — 通用基础件：`Modifier.glass` / `solidCard` / `pressable` / `navBarBackground` / `pageBottomPadding`、`MascotFace` / `MascotImage` / `rememberFileBitmap`、`GroupTitle`、`AppFab`、`AppSwitch`、`MiniToggle`、`StateChip`、`IconBlock`、`StatCell`、`DividerLine`、`EmptyCopy`、`TopBar`、`PrimaryButton`、`GhostButton`。
- `ui/components/BillList.kt` — 按日分组账单列表，首页与日历页共用；`flush = true` 时去掉自带卡片底，供嵌进外层卡片。新列表需求优先扩展它。
- `ui/components/Overlays.kt` — 全局浮层：Toast、确认框、操作菜单、滚轮选择器、日期选择器、图片预览。页面只改 `AppState` 状态位，不自己写 Dialog。
- `ui/components/WheelPicker.kt` — 滚轮列，供日期与选项选择器复用。
- `ui/components/Motion.kt` — 动效唯一入口：页面转场、`AnimatedSheet`、`AnimatedCenter`、`RiseIn`、`countUp`、`stagger`。页面不要各写时长/曲线。
- `ui/screens/` — 首页、记一笔、统计、日历、分类、设置、导入、备份八个页面。页面只做布局与事件，数据逻辑回 `data/`。

## 原生核心

- `app/src/main/java/muyian/bill/listener/` — 包名 `muyian.bill.listener`，系统级能力的唯一实现，同包分文件：`hybrid.kt`（采集/解析/去重/弹层队列）、`BillDb.kt`（SQLite）、`SystemPerms.kt`（权限/自愈/主题色）、`XlsxReader.kt`（xlsx 解压）、`PendingNotices.kt`（待确认通知）、`BillSheetActivity.kt` / `ImportPickActivity.kt` / `BackupSaveActivity.kt`（中转与弹层）、三个 Service。对外入口仍是 `MuyianBillCore`（`@JvmStatic` 同步方法）。
- `android/app/src/main/AndroidManifest.xml` — 权限与服务/Activity 注册；`res/xml/muyian_accessibility_config.xml` 是无障碍服务配置。

## 数据格式

- 备注中的图片以 `{{img:<path>}}` token 存在 `orderRemark` 末尾，由 `Bill.remarkText` / `Bill.imgPath` 解析展示；其他页面不得另定图片存储格式。
