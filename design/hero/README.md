# 首页头图 · 搞怪哪吒

已接入首页与设置页：三张表情都进包，设置里选哪张，首页就显示哪张。

## 配置

- 唯一来源：`common/hero-nezha.js`
  - `NEZHA_STYLES`：三张表情的 `src`、`widthRpx`（显示宽度）、`bottomRpx`（上提量）
  - storage key：`muyian_nezha_v1`，默认 `grin`（坏笑）
  - `nezhaStyle` / `refreshNezhaStyle()` / `setNezhaStyle(id)`
- 设置页入口：`pages/settings/settings.vue` → 外观 → **首页哪吒**
- 首页：`pages/index/index.vue` → `.hero-deco` 里的 `.hero-nezha`，`onShow` 调 `refreshNezhaStyle()`
- 素材：`static/nezha/{grin,cheer,grumpy}.webp`（每张约 57–60KB）
- 源 PNG（未压进包）：`design/hero/assets/`

## 头图规则

- 只在**默认渐变头图**显示；设置了自定义头图（`heroImg`）时随 `.hero-deco` 一起隐藏
- **原来的两个装饰圆环已删除**（右上大弧线 + 人物后面的小圆）
- 人物靠右、**右侧固定留 20rpx 间距**（不再贴边裁切）；底部压进玻璃概览卡
- 三张图的宽度/上提量不同，保证嘴都露在卡片上方：
  - 坏笑 236rpx 宽、上提 34rpx
  - 大笑 236rpx 宽、不上提
  - 不服 236rpx 宽、上提 20rpx
- 三套主题（悟空黄 / 哪吒红 / 沙僧黑）都验证过

## 效果截图

- 首页（坏笑）：`out/app-hero-grin.png`
- 首页深色主题（大笑）：`out/app-home-cheer-dark.png`
- 设置页（浅色）：`out/app-settings-final.png`
- 设置页（深色）：`out/app-settings-dark.png`
- 设计总览：`out/funny-sheet.png`

## 设计稿

- `preview.html`（浏览器直接打开，仍是概念尺寸）

## 素材授权

素材来自花瓣的哪吒2同人/周边渲染图（透明免抠）。仅本地自用没问题；
上架或分发前需自行确认授权，非官方素材。
