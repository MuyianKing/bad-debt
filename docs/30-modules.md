# 目录职责（禁止重复造轮子）

> 新增页面/组件/工具函数前先读本文件：同一件事只能有一个唯一来源，页面里不许另起一套。

## common/

- `common/cats.js` — 分类体系唯一来源：默认分类播种、`uni.storage`（key：`muyian_categories_v1`）读写、图标/调色板查询、子类归并（`topNameOf`）。页面禁止自带分类常量。
- `common/fmt.js` — 金额千分位（`fmtMoney`）、数字缩写（`fmtNum`/`fmtTick`）、按日分组（`groupByDay`）、日期标签、键盘金额表达式求值（`evalExpr`）。
- `common/lunar.js` — 农历与节日（1900–2100），仅供日历角标使用。
- `common/merchants.js` — 商户分类建议：内置词典（商户关键词→分类叶子名，有序数组、具体词优先）+ 用户修正回流（storage key `muyian_merchant_cats_v1`，上限 500 条）。`suggestCategory(merchant, isIncome)` / `learnCategory(merchant, leafName)`。词典不定义分类体系，叶子名必须与 cats.js 一致；原生只负责提取商户名（解析结果 `merchant` 字段），建议与回流都在页面侧。**当前未接入任何页面（草稿模式已移除），保留待后续在原生弹层/记一笔页复用。**
- `common/theme.js` — 主题唯一来源：主题清单（含 SVG/canvas 字面色 `primary/primaryDeep/primaryFill`、中性图标墨色 `ink/inkSub`、原生弹层强调色 `cardColor`、深色标记 `dark`）、当前主题的响应式状态与持久化。页面禁止自建主题常量。
- `common/recents.js` — 最近任务可见性唯一来源：`isHideRecentsEnabled` / `setHideRecentsEnabled` / `applyRecentsVisibility`（runtime `AppTask.setExcludeFromRecents`，兼容忽略清单标记的 ROM）。设置页开关与 `App.onLaunch/onShow` 都走这里，不要在页面里重复写 plus.android 反射。
- `common/hero-nezha.js` — 首页头图哪吒表情唯一来源：`NEZHA_STYLES`（每项含 `src` / `widthRpx` / `bottomRpx`）、响应式 `nezhaStyle`、持久化（storage key `muyian_nezha_v1`，默认 `grin`）。素材在 `static/nezha/*.webp`；设置页用 `setNezhaStyle` 切换，首页 `onShow` 用 `refreshNezhaStyle` 跟上。新增表情只改这个模块 + 放图，不要在页面里写死路径或尺寸。

## components/

- `components/bill-list/bill-list.vue` — 按日分组账单列表，首页与日历页共用；新列表需求优先扩展它，不要复制粘贴一份。

## 数据格式

- 备注中的图片以 `{{img:<path>}}` token 存在 `orderRemark` 末尾，由 bill-list 负责解析展示；其他页面不得另定图片存储格式。
