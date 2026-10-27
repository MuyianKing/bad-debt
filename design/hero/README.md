# 首页头图 · 角色系统

已接入首页、抽屉、空态与设置页。当前哪吒已启用；悟空已预置（`ready: false`，
素材到位前回退到哪吒）；沙僧待定。素材到位后只改注册表、`ready` 标记与主题映射，
页面不用动。

## 配置

- 唯一来源：`common/mascots.js`
  - `MASCOTS`：角色 → 表情列表。每项含 `src`（头图整图）、`headSrc`（方形头像）、`mood`、`widthRpx`（显示宽度）、`bottomRpx`（上提量）
  - `THEME_MASCOT`：主题 → 角色。当前 `wukong / nezha / shaseng` 都指向 `nezha`
  - `activeMascot` / `activeMascotBaseStyle`：当前主题的角色与用户选中的基础表情
  - `refreshMascots()` / `setMascotStyle(id)` / `setAutoMood(bool)`
  - storage：`muyian_mascot_style_v1:<角色id>`、`muyian_mascot_auto_mood_v1`；旧 `muyian_nezha_v1` 自动迁移
- 通用组件：`components/mascot-face/mascot-face.vue`
  - `mood`：`idle` / `success` / `empty`（可扩展 `warn`）
  - `size`：rpx；`head`：方形头像模式；`mode`：`auto` 跟随场景 / `base` 固定基础表情
- 设置页入口：`pages/settings/settings.vue` → 外观 → **{角色}表情**（当前哪吒）
- 首页：`pages/index/index.vue` → `.hero-deco` 里的 `.hero-mascot`
- 头像裁切：`static/nezha/head-{grin,cheer,grumpy}.webp`，由 `design/hero/build_heads.py` 生成
- 源 PNG（未压进包）：`design/hero/assets/`

## 使用规则

- **主角色一屏最多一处**（记一笔页分类区是主角色；输入行 72rpx 属表情指示，不计）；分类图标、图表、金额与账单行等数据区不放。抽屉属于独立浮层、记一笔保存/校验反馈属于瞬时反馈，均不计入此限。
- 空态用 `head` 模式 + 圆形淡底（首页 152rpx / 日历 128rpx）；统计不在空态放角色
- 抽屉品牌位用 92rpx 头像，`mode="base"`，跟随用户选的基础表情
- 记一笔：分类区右下角固定 145rpx 整身角色 + 台词（绝对定位，不随分类列表滚动、不占键盘高度；分类滚动内容底部留 190rpx），收入/支出台词分开写；输入行 72rpx 头像做收支表情指示，保存成功/金额不合法时弹 112rpx 反馈
- 统计总览卡右侧常驻 190rpx 整身角色（同首页，用整图不是头像）：结余为负用「不服」（`warn` 别名），为正用「大笑」（`success`），持平/无账单用基础表情；总览只保留收入/结余两项，排版同首页 `.hero-stats`（标签在上、金额在下、细分割线、左对齐）
- 表情语义：坏笑 = 常态、大笑 = 成功/收入、不服 = 空态/超支；`moodAliases` 可让 `warn` 复用「不服」；开启「跟随场景」后非 idle 场景自动覆盖
- 角色只出现在默认渐变头图；设置自定义头图（`heroImg`）时随 `.hero-deco` 一起隐藏
- **头图右侧固定留 20rpx 间距**，不贴边裁切；人物整张脸（含下巴）要露在头图外
- 原来的两个装饰圆环（右上大弧线 + 人物后面的小圆）已删除，不要再加回去
- 三套主题都要验证：浅色主题看红色混天绫是否和主题色打架，沙僧黑看黑发/深色衣领是否和纯黑底粘在一起

## 新增角色（悟空 / 沙僧）

1. 素材放 `static/<角色>/`，至少准备 `idle / success / empty` 三张整图 + 对应方形头像
2. 在 `MASCOTS` 里注册新角色（`id / name / defaultStyleId / styles / ready`）
3. 把 `THEME_MASCOT` 对应主题指向新角色
4. 设置页标题、表情列表、首页头图、抽屉头像、空态会自动跟随，不需要改页面

## 悟空素材规格（待补）

- 方向参考：《西游记之大圣归来》的大圣，3D 国漫渲染，和哪吒同一套气质
- 三张表情：`idle` 扛棒坏笑 / `success` 举棒大笑 / `empty` 抱臂不服（`warn` 复用不服）
- 源图：透明 PNG，头肩像，约 650×740，命名 `wukong-{grin,cheer,grumpy}.png`，放 `design/hero/assets/`
- 入包：`static/wukong/{grin,cheer,grumpy}.webp` + `head-*.webp`（320×320）
- 素材齐后：`MASCOTS.wukong.ready = true`，悟空黄主题自动切换
- 网上透明三表情很难直接找齐，建议定制或取得授权后重绘；不要直接拿电影截图/同人图上架

## 素材授权

哪吒素材来自花瓣的哪吒2同人/周边渲染图（透明免抠），仅本地自用；
上架或分发前需自行确认授权，或替换为原创角色素材。
