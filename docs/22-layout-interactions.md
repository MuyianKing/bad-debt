# 布局与交互

> 本文件覆盖首页抽屉、哪吒头图、沉浸式顶栏、负层光斑与分类拖拽。色彩与组件基线见 `20-ui-basics.md`。

## 首页侧滑抽屉

- 首页左上角为**侧滑抽屉**（index.vue 的 `.drawer` + `.drawer-mask` + `.page-main`，菜单项与图标定义在 `drawerMenus`；新增一级页面入口时同步加进去）。**抽屉是自绘跟手面板，不要再改回 wd-popup**：要求"抽屉滑出宽度 = 首页右移宽度、且都按手指位移实时变化"，wd-popup 的挂载与 enter 过渡有数百毫秒延迟、无法跟手。统一由一个 `drawerProgress`（0~1）驱动三样东西——抽屉 `translate3d(-W×(1-p))`、首页 `translate3d(W×p)`、遮罩 `opacity: p`（W 取 `min(250px, 78vw)`，与 `.drawer` 的宽度一致，改宽度要同步 `DRAWER_MAX_W`）；拖动中三者的 `transition` 必须是 `none`，松手后才交给 280ms 过渡吸附（过半展开）。手势在整个首页与抽屉上生效（根节点 touch 事件冒泡），按水平/垂直位移比判方向，避免抢页面滚动；悬浮加号不受位移影响（fixed），按同一进度淡出。

## 首页头图哪吒

- **首页头图右侧的哪吒**：三张透明 WebP 在 `static/nezha/`，由 `common/hero-nezha.js` 统一配置（表情、显示宽度 `widthRpx`、上提量 `bottomRpx`），页面里**右侧固定留 20rpx 间距，不要贴边裁切**，设置页「外观 → 首页哪吒」切换，首页 `onShow` 调 `refreshNezhaStyle()`。**头图原有的两个装饰圆环（右上大弧线 + 人物后面的小圆）已按要求删除，不要再加回去**；哪吒只在默认渐变头图显示，自定义 `heroImg` 时随 `.hero-deco` 一起隐藏。人物底部压进概览卡，上提量要保证嘴都能露在卡片上方（坏笑提得最多）；换图后浅色主题与沙僧黑各看一遍。素材是网络免抠图（花瓣），仅本地自用，上架前确认授权。

## 内页沉浸式顶栏

- **内页顶部沉浸式**：`wd-navbar` 不是天然沉浸的——默认白底会把状态栏和标题糊成一条白带。做法见 calendar.vue：顶栏底色走 `custom-style`（静止态 `background-color: transparent`，让页面底色/光斑直通状态栏；`onPageScroll` 超过 8px 后换成 `--glass-nav-bg` + blur 转毛玻璃，避免列表从标题下穿过，同时 `:bordered="navSolid"` 只在滚动态留分割线）。**踩过的坑**：页面 `<style>` 在 H5 按 `data-v-*` 作用域编译，而 `custom-class` 落在组件内层元素上（不带页面 data-v），页面里写 `.cal-navbar{...}` 匹配不到；组件外层底色只能用 `custom-style`，或把类写进 App.vue 全局样式（`.glass-nav` 即此机制）。**通用规则**：页面 scoped 样式要改 wd-* 组件内部节点（如 `wd-cell__left/__right`）必须用 `:deep()` 穿透，普通后代选择器会静默不生效。状态栏要真沉浸还需 manifest `app-plus.statusbar` 用**正确键名 `immersed`**（不是 `immersive`），并把 `pages.json` 的 `navigationBarBackgroundColor` 设为页面底色（沉浸失效时状态栏至少与页面同色）。

## 负层光斑

- **负层光斑的前提**：光斑写 `.bg-blobs { z-index: -1 }` 时，页面根节点 `.page` 必须同时 `position: relative; z-index: 0` 建立层叠上下文，否则光斑会被页面底色整块盖掉（H5 必现；日历页的光斑因此需要按此修）。注意新代码仍禁止新增光斑/光效（见 `20-ui-basics.md`），此条只约束存量页面。

## 分类排序

- 分类管理的排序交互是**长按拖拽**（categories.vue 的 startDrag/onDragMove/onDragEnd，矩形命中 + 松手落位 + 立即持久化），不要改回点菜单移一步的旧交互；子类面板渲染在父类行的紧下方（cat-block 内）。
