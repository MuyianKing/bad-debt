# 目录职责（禁止重复造轮子）

> 新增页面/组件/工具函数前先读本文件：同一件事只能有一个唯一来源，页面里不许另起一套。

## common/

- `common/cats.js` — 分类体系唯一来源：默认分类播种、`uni.storage`（key：`muyian_categories_v1`）读写、图标/调色板查询、子类归并（`topNameOf`）。页面禁止自带分类常量。
- `common/fmt.js` — 金额千分位（`fmtMoney`）、数字缩写（`fmtNum`/`fmtTick`）、金额输入回填去尾零（`fmtMoneyInput`）、按日分组（`groupByDay`）、日期标签、键盘金额表达式求值（`evalExpr`）。
- `common/qianji-import.js` — 钱迹 Excel 导入的唯一数据逻辑：表头定位、时间/类型/金额校验、汇总统计、分类计数、钱迹分类 → 叶子名映射、入库账单拼装。原生插件只把 xlsx 读成「表头 + 原始单元格行」并批量写库，页面只负责 UI 与调用顺序；纯函数，可在 H5/Node 直接验证。
- `common/lunar.js` — 农历与节日（1900–2100），仅供日历角标使用。
- `common/merchants.js` — 商户分类建议：内置词典（商户关键词→分类叶子名，有序数组、具体词优先）+ 用户修正回流（storage key `muyian_merchant_cats_v1`，上限 500 条）。`suggestCategory(merchant, isIncome)` / `learnCategory(merchant, leafName)`。词典不定义分类体系，叶子名必须与 cats.js 一致；原生只负责提取商户名（解析结果 `merchant` 字段），建议与回流都在页面侧。**当前未接入任何页面（草稿模式已移除），保留待后续在原生弹层/记一笔页复用。**
- `common/theme.js` — 主题唯一来源：主题清单（含 SVG/canvas 字面色 `primary/primaryDeep/primaryFill`、中性图标墨色 `ink/inkSub`、原生弹层强调色 `cardColor`、深色标记 `dark`）、当前主题的响应式状态与持久化。页面禁止自建主题常量。
- `common/recents.js` — 最近任务可见性唯一来源：`isHideRecentsEnabled` / `setHideRecentsEnabled` / `applyRecentsVisibility`（runtime `AppTask.setExcludeFromRecents`，兼容忽略清单标记的 ROM）。设置页开关与 `App.onLaunch/onShow` 都走这里，不要在页面里重复写 plus.android 反射。
- `common/mascots.js` — 角色（吉祥物）唯一来源：`MASCOTS`（角色 → 表情列表，每项含 `src` / `headSrc` / `mood` / `widthRpx` / `bottomRpx`，可用 `moodAliases` 把一个 mood 复用已有表情，如 `warn: 'grumpy'`）、`THEME_MASCOT`（主题 → 角色）、`activeMascot` / `activeMascotBaseStyle`、`autoMood`、`setMascotStyle` / `setAutoMood` / `refreshMascots`。角色可带 `ready: false` 表示素材未齐，`activeMascot` 会回退到 `FALLBACK_MASCOT_ID`（当前哪吒）。storage：`muyian_mascot_style_v1:<角色id>`、`muyian_mascot_auto_mood_v1`；旧 `muyian_nezha_v1` 自动迁移。素材在 `static/<角色>/`。新增角色只改这个模块 + 放图 + 改主题映射，页面不要写死角色名或路径。

## components/

- `components/bill-list/bill-list.vue` — 按日分组账单列表，首页与日历页共用；`flush` 为 `true` 时去掉自带的卡片底/圆角/阴影，供嵌进外层卡片（日历页与选中日摘要合并）；新列表需求优先扩展它，不要复制粘贴一份。
- `components/mascot-face/mascot-face.vue` — 通用角色表情渲染：`mood`（idle / success / empty）、`size`（rpx）、`head`（方形头像模式）、`mode`（auto / base）。空态、抽屉头像统一用它，不要直接 `<image :src="/static/...">`。

## 数据格式

- 备注中的图片以 `{{img:<path>}}` token 存在 `orderRemark` 末尾，由 bill-list 负责解析展示；其他页面不得另定图片存储格式。
