# 孽账 · 启动图方案

Android 启动图是 App 冷启动的第一帧，**无法取消，只能替换**。DCloud 默认启动图会带一个雪花等待动画；
本项目已通过 `manifest.json` 的 `app-plus.splashscreen.waiting = false` 关掉雪花，并换成下面这套 9-patch。

## 产物

- `logo.png` — 启动图中央图标（当前与桌面图标一致）
- `build.py` — 生成脚本
- `out/{hdpi,xhdpi,xxhdpi}.9.png` — 三个密度（480x762 / 720x1242 / 1080x1882，含最外 1px 标记边）
- `out/preview.png` — 16:9 / 19.5:9 拉伸效果预览

`build.py` 同时把图写入 `unpackage/res/splashscreen/`，`manifest.json` 的
`app-plus.distribute.splashscreen.android` 引用的就是那份。

## 为什么用 .9.png

普通 PNG 会被整体拉伸，20:9 手机上圆形图标会变成竖长条。9-patch 只拉伸品牌块四周的纯色空白，
图标和文字保持原尺寸、原比例：顶边黑线标记可横向拉伸的列，左边黑线标记可纵向拉伸的行，
品牌块对应的行/列不打标记，因此不会被拉。

## 重新生成

```bash
python build.py
python build.py ../icon/out/red-a-coin-1024.png   # 换图标时传 1024 方图
```

重新生成后需要重新打包（自定义调试基座也要重做），启动图才会进 APK。

## 注意

- 背景固定 `#f7f4ec`，与 `App.vue` 的 `--c-bg` 一致，启动图切首页不跳色；改底色要同时改两边。
- 9-patch 必须是标准 PNG，最外 1px 是标记边，不要转 jpg、不要裁掉边框。
- Android 12+ 会先显示一次系统启动画面（居中 App 图标），这是系统行为，uni-app 配置关不掉。
- `androidNavigationBarColor` 同步设为 `#f7f4ec`，避免底部导航栏闪白。
