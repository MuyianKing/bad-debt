# 孽账 · 项目约束

本文件是全仓库的强约束入口。`README.md` 讲「是什么 / 怎么跑」，这里讲「必须怎么做 / 不许怎么做」。跨模块的通用底线留在本文件，专项细节拆到 `docs/`，按下面的索引取用。

## 一、通用约束（任何改动都必须遵守）

- **技术栈与工程形态固定**：uni-app（vue3）+ wot-design-uni + UTS 插件（`hybrid.kt` 为原生核心），用 HBuilderX 打开整个项目目录运行/打包。不引入 vite/cli 脚手架，不改 `manifest.json` 的 appid，不替换组件库与图标体系。目标平台只有 Android 真机，只出 `arm64-v8a`。
- **实现优先级：能 uni-app 就不下沉原生**：uni-app 内置 API、页面与 `common/*` 的 JS 能完成的功能，不得放进 UTS 插件；UTS 只承担 uni-app 无法实现的原生能力（无障碍采集、通知监听、确认弹层、SAF 选文件、原生 SQLite 数据层）。解析、映射、流程编排等数据逻辑留在 JS；确需原生实现的数据逻辑要在 `docs/` 写明理由。
- **隐私红线**：通知/窗口文本只在手机本地解析，数据只存本机 SQLite。禁止任何上传、统计 SDK、云同步与新增网络请求。
- **确认弹层不可绕过**：解析成功的账单不自动入账，必须由原生底部确认弹层（`BillSheetActivity`）用户点「保存」才写库；取消/返回/60 秒超时，以及弹层已经显示后的熄屏，都直接丢弃这一笔。弹层从一开始就因熄屏/锁屏或 ROM 拦截拉不起来时，允许发一条「待确认」通知兜底，但点通知必须回到同一个确认弹层；通知本身不得写库、不得提供直接入账入口。不得重新引入悬浮卡、兜底草稿或其他旁路。
- **自动采集以无障碍服务为主渠道**，通知监听只是可选辅助渠道，禁止把通知监听改回唯一渠道。
- **UI 基线**：组件统一 wot-design-uni（easycom），图标统一 `app-icon`，配色/圆角/阴影/毛玻璃统一走 `App.vue` 设计令牌。新代码一律纯色，禁止渐变、光斑、光晕；所有页面卡片走 `.glass`。wd 组件的布尔 prop 必须显式绑定（`:is-link="true"`），禁止裸属性写法。
- **跨语言 API 只走同步调用 + JSON 字符串**：新增插件能力必须 `interface.uts` → `app-android/index.uts` → `app-android/hybrid.kt` 三处同步，并补 `utssdk/web/index.js` 桩与 `common/web-bill-api.js` 的 H5 实现；页面调用处 `#ifdef APP-PLUS` / `#ifndef APP-PLUS` 成对出现。
- **数据口径不可另立**：金额支出为负、收入为正；`costType` 存叶子名，统计经 `topNameOf()` 归并；日期用 `year/month/day` 三字段，页面间传 `YYYY-MM-DD`。
- **专项约束以 `docs/` 为准**：改到哪一类就按对应文档执行；本文件与具体文档表述冲突时，以更具体的那篇为准。
- **交付前必须过清单**：逐项对照 `docs/80-checklist.md`，任何一项不满足不交付。

## 二、文档索引

| 文档 | 内容 |
| --- | --- |
| [docs/00-product.md](docs/00-product.md) | 产品定位、确认弹层、隐私与采集通道红线 |
| [docs/10-build.md](docs/10-build.md) | HBuilderX/依赖/ABI、自定义调试基座、相机模块、最近任务隐藏、启动图、H5 运行 |
| [docs/20-ui-basics.md](docs/20-ui-basics.md) | 组件库、导航与安全区、布尔 prop、rpx、图标、设计令牌、纯色约束、分段开关、统计面板、图表、新增页面 |
| [docs/21-theme-and-glass.md](docs/21-theme-and-glass.md) | 三主题切换、深色主题、彩色图标、毛玻璃与统一底色 |
| [docs/22-layout-interactions.md](docs/22-layout-interactions.md) | 首页侧滑抽屉、角色系统与头图、内页沉浸式、负层光斑、分类拖拽 |
| [docs/30-modules.md](docs/30-modules.md) | `common/*`、`components/*` 职责边界，禁止重复造轮子 |
| [docs/40-data.md](docs/40-data.md) | SQLite 表结构、金额符号、分类存储、旧库兼容、查询上限 |
| [docs/50-uts-plugin.md](docs/50-uts-plugin.md) | UTS 插件 API 约定、确认弹层实现、hybrid.kt 写法坑、金额择取规则 |
| [docs/60-conditional-compile.md](docs/60-conditional-compile.md) | 页面侧条件编译、onShow 数据加载、无障碍自愈顺序 |
| [docs/70-datetime.md](docs/70-datetime.md) | 日期/时间字段与展示约定 |
| [docs/80-checklist.md](docs/80-checklist.md) | 提交前自查清单（9 项） |
| [docs/README.md](docs/README.md) | docs 目录自身索引与维护约定 |

## 三、最小自查（完整版见清单）

- 新页面已在 `pages.json` 注册，easycom 规则未被破坏。
- 新增插件 API 三文件 + web 桩 + H5 实现齐备，页面 stub 成对且签名一致。
- 无新增网络请求；金额符号与 `costType` 叶子名未被破坏。
- 改到 `hybrid.kt`、根目录 `AndroidManifest.xml` 或 `manifest.json` 的 `modules`/`permissions` 时，提示用户重新制作自定义调试基座。
- 改到配色/组件样式时，三个主题（悟空黄、哪吒红、沙僧黑）各过一遍。
- 新增角色/表情只改 `common/mascots.js` 与素材，页面不写死角色名、图片路径或按主题写 if/else。
