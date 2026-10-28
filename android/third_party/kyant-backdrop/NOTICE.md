# Kyant backdrop（液态玻璃实现）

来源：https://github.com/Kyant0/AndroidLiquidGlass
基线：tag `2.0.0`，commit `bebb11a91bd97bf1dabde479f3b332ad9898731f`
许可：Apache-2.0，见同目录 `LICENSE`

本项目把上游 `com.kyant.backdrop` 的核心源码内置在
`app/src/main/java/com/kyant/backdrop/`，用于首页每日账单卡片的液态玻璃
（真采样 + 模糊 + 边缘折射 + 高光/阴影）。

相对上游做的适配（仅为兼容 Compose 1.7.5 / Kotlin 2.0.21 单模块 Android 工程）：

- 去掉 KMP 的 expect/actual，直接把 androidMain 实现并入 common 源码；删除
  `SharedBlurBackdrop` / `TransformAwareLayerBackdrop` 等未使用的扩展路径。
- 去掉 Kotlin context parameters，`recordLayer` 改为显式参数。
- `GraphicsLayer.record(size)` 改为 Compose 1.7 的
  `record(density, layoutDirection, size)`。
- 去掉 `GraphicsLayerScope.blendMode/colorFilter` 覆盖（1.7 没有这两个成员）。
- `boundsInWindow(clipBounds = false)` 改为 1.7 的 `boundsInWindow()`。
- 效果在节点 attach 时尺寸未知，`updateEffects` 延迟到首次 draw 再应用。
- `Lens` 去掉 `com.kyant.shapes.RoundedRectangularShape` 分支，只保留
  Compose 的 `CornerBasedShape`（本项目只传 `RoundedCornerShape`）。

未使用上游的壁纸/场景录制机制；采样源改由 `ui/components/GlassBackdrop.kt`
提供（设置里的背景图，未设置时保持透明）。
