# 孽账 App 图标方案

「孽账」谐音「孽障」→ 哪吒：铜钱、混天绫、火尖枪、莲花化身。四个方案红 / 黄各两版，
都保证在 72px 桌面尺寸下可读。

## 方案

| 文件前缀 | 名称 | 意象 | 底色 |
| --- | --- | --- | --- |
| `red-a-coin` | 红 · 乾坤钱 | 方孔铜钱（=乾坤圈）+ 混天绫 + 火星 | 深红渐变 |
| `red-b-nezha` | 红 · 钱髻哪吒 | 哪吒头像，双丸子头换成两枚铜钱，额间火焰，红战袍 | 暗红 |
| `yellow-a-lotus` | 黄 · 莲钱 | 红莲花瓣 + 方孔金铜钱（莲花化身） | 琥珀黄 |
| `yellow-b-flame` | 黄 · 火尖焰 | 哪吒火 + ¥ | 琥珀黄 |

预览图：[`out/preview.png`](out/preview.png)（含 72/48/32px 实际尺寸与桌面磁贴观感）、
[`out/small-check.png`](out/small-check.png)（72px 像素级放大检查）。

## 产物

- `src/*.svg` — 矢量源（1024×1024，圆角外透明）
- `out/*-1024.png` — 主图，RGBA
- `out/*-192/144/96/72.png` — Android 各密度
- `out/*-48/32.png` — 仅供预览核对，不参与打包

## 重新生成

```bash
node build.mjs            # 生成 4 个 SVG
node render.mjs           # 用本机 Edge headless 渲染 1024 PNG
python resize.py          # 缩各密度 + 拼预览图
```

`build.mjs` 里改配色/构图后重跑上面三步即可；渲染依赖
`C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe`。

## 应用到 App

```bash
python apply.py red-a-coin
```

会把选中方案写入 `unpackage/res/icons/{72x72,96x96,144x144,192x192}.png` 与
`icon_1024.png`——即 `manifest.json` 已配置的路径，无需改配置。
应用后需在 HBuilderX 重新打包（自定义基座）才会在桌面生效。

## 约束

- 圆角 232/1024，圆角外透明；主体内容控制在中心 66% 安全区附近，兼容圆形 / squircle 遮罩。
- 不引入位图素材、不依赖字体，全部路径绘制（¥ 也是笔画），保证任意尺寸锐利。
