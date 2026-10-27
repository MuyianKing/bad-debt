# 孽账 · 项目约束

本文件是全仓库的强约束入口。`README.md` 讲「是什么 / 怎么跑」，这里讲「必须怎么做 / 不许怎么做」。跨模块的通用底线留在本文件，专项细节拆到 `docs/`，按下面的索引取用。

## 一、通用约束（任何改动都必须遵守）

- **技术栈与工程形态固定**：Kotlin + Jetpack Compose，单 Activity（`MainActivity`）+ Compose 导航；系统能力与原生数据层位于 `android/app/src/main/java/muyian/bill/listener/` 同包文件（`hybrid.kt` 负责采集/解析/去重/弹层，`BillDb.kt` 负责 SQLite，`SystemPerms.kt` 负责权限，`XlsxReader.kt` 负责 xlsx，`PendingNotices.kt` 负责待确认通知，Activity/Service 各自成文件）。用 `android/` 下的 Android Gradle 工程构建，不引入第三方 UI 库、图表库或网络/统计依赖。目标平台只有 Android 真机。
- **实现优先级：能 Kotlin/Compose 就不下沉系统服务**：页面、组件、格式化、分类/主题/角色、钱迹映射、导入/备份流程放在 `data/` 与 `ui/` 的 Kotlin 代码；`muyian.bill.listener` 只承担系统级能力（无障碍采集、通知监听、确认弹层、后台保活、SAF 选文件、原生 SQLite 数据层）。新增系统能力写入同包对应文件，由 `MuyianBillCore` 暴露同步方法，不引入跨语言桥接层。
- **隐私红线**：通知/窗口文本只在手机本地解析，数据只存本机 SQLite 与 SharedPreferences。禁止任何上传、统计 SDK、云同步与新增网络请求。
- **确认弹层不可绕过**：解析成功的账单不自动入账，必须由原生底部确认弹层（`BillSheetActivity`）用户点「保存」才写库；取消/返回/60 秒超时，以及弹层已经显示后的熄屏，都直接丢弃这一笔。弹层从一开始就因熄屏/锁屏或 ROM 拦截拉不起来时，允许发一条「待确认」通知兜底，但点通知必须回到同一个确认弹层；通知本身不得写库、不得提供直接入账入口。不得重新引入悬浮卡、兜底草稿或其他旁路。
- **自动采集以无障碍服务为主渠道**，通知监听只是可选辅助渠道，禁止把通知监听改回唯一渠道。
- **UI 基线**：全部界面用 Jetpack Compose；颜色/圆角/阴影/毛玻璃统一走 `ui/theme/Theme.kt` 的 `Palette` 与 `LocalPalette`，图标统一走 `ui/icons` 的 `AppIcon` 与 `IconData`，卡片统一用 `Modifier.glass` / `solidCard`。禁止在页面里写死主题色；装饰性渐变、光斑、光晕先复用主题令牌（`gradPrimary` 等），不要临时拼色。状态栏图标、安全区与返回键由 `MainActivity` 统一处理，页面不各写一套。
- **原生接口约定**：`MuyianBillCore` 的对外方法保持**同步调用**，复杂结构用 JSON 字符串传递；页面侧通过 `data/Bills.kt`、`data/Qianji.kt`、`data/NativeStatus.kt`、`data/Backup.kt`、`data/ImportFlow.kt`、`data/FilePick.kt` 的类型化封装消费，不直接调 `MuyianBillCore`、不自己拼 JSON。
- **数据口径不可另立**：金额支出为负、收入为正；`costType` 存叶子名，统计经 `Cats.topNameOf()` 归并；日期用 `year/month/day` 三字段，页面间传 `YYYY-MM-DD`。
- **专项约束以 `docs/` 为准**：改到哪一类就按对应文档执行；本文件与具体文档表述冲突时，以更具体的那篇为准。
- **交付前必须过清单**：逐项对照 `docs/80-checklist.md`，任何一项不满足不交付。

## 二、文档索引

| 文档 | 内容 |
| --- | --- |
| [docs/00-product.md](docs/00-product.md) | 产品定位、确认弹层、隐私与采集通道红线 |
| [docs/10-build.md](docs/10-build.md) | Gradle/SDK/ABI、构建与签名、相机与权限、最近任务隐藏、启动画面 |
| [docs/20-ui-basics.md](docs/20-ui-basics.md) | Compose 结构、设计单位、图标、设计令牌、通用组件、统计面板、图表、新增页面 |
| [docs/21-theme-and-glass.md](docs/21-theme-and-glass.md) | 三主题切换、深色主题、彩色图标、毛玻璃与统一底色 |
| [docs/22-layout-interactions.md](docs/22-layout-interactions.md) | 首页侧滑抽屉、角色系统与头图、吸顶导航、分类拖拽、子类展开 |
| [docs/30-modules.md](docs/30-modules.md) | `data/`、`ui/` 与原生核心的职责边界，禁止重复造轮子 |
| [docs/40-data.md](docs/40-data.md) | SQLite 表结构、金额符号、分类存储、旧库兼容、查询上限、导入与备份 |
| [docs/50-native-core.md](docs/50-native-core.md) | 原生核心 API 约定、确认弹层、去重规则、金额择取、服务注册 |
| [docs/70-datetime.md](docs/70-datetime.md) | 日期/时间字段与展示约定 |
| [docs/80-checklist.md](docs/80-checklist.md) | 提交前自查清单（9 项） |
| [docs/README.md](docs/README.md) | docs 目录自身索引与维护约定 |

## 三、最小自查（完整版见清单）

- 新页面已在 `Screen` 中注册，并在 `MainActivity` 的 `AnimatedContent` 中渲染。
- 新增原生能力已由 `MuyianBillCore` 暴露，复杂结构走 JSON 字符串；`data/` 侧有对应解析函数。
- 无新增网络请求；金额符号与 `costType` 叶子名未被破坏。
- 改到 `muyian/bill/listener/` 下任意文件或 `android/app/src/main/AndroidManifest.xml` 时，重新构建安装；权限/服务声明变化后提示用户重新授权。
- 改到配色/组件样式时，三个主题（悟空黄、哪吒红、沙僧黑）各过一遍。
- 新增角色/表情只改 `data/Mascots.kt` 与 `app/src/main/assets/mascots/` 素材，页面不写死角色名、图片路径或按主题写 if/else。
