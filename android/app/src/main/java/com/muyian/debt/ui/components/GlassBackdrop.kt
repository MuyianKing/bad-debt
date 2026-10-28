package com.muyian.debt.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.Backdrop
import com.muyian.debt.ui.theme.LocalPalette
import kotlin.math.max
import kotlin.math.roundToInt

/** 整屏的玻璃底图：取设置里的玻璃背景图，没设置时保持透明。 */
@Immutable
class GlassArt(
    val image: ImageBitmap?,
    val background: Color,
    val imageAlpha: Float,
    val screenWidth: Float,
    val screenHeight: Float
)

val LocalGlassArt = staticCompositionLocalOf<GlassArt?> { null }

/**
 * 液态玻璃的采样源。这里不再让卡片自己去采样页面（首页背后只有纯色），
 * 而是把玻璃背景图当成一张整屏壁纸，卡片按自己的窗口位置取其中一块，
 * 再交给 Kyant backdrop 做模糊和边缘折射。页面本身仍然是统一的 bg 纯色。
 */
@Composable
fun GlassBackdropHost(
    bgBitmap: Bitmap?,
    content: @Composable () -> Unit
) {
    val p = LocalPalette.current
    val image = remember(bgBitmap) { bgBitmap?.asImageBitmap() }
    val imageAlpha = if (p.dark) 0.45f else 0.50f
    var screen by remember { mutableStateOf(Size.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .onSizeChanged { screen = Size(it.width.toFloat(), it.height.toFloat()) }
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = imageAlpha },
                contentScale = ContentScale.Crop
            )
        }
        CompositionLocalProvider(
            LocalGlassArt provides GlassArt(
                image = image,
                background = p.bg,
                imageAlpha = imageAlpha,
                screenWidth = screen.width,
                screenHeight = screen.height
            )
        ) {
            content()
        }
    }
}

/** 把整屏底图平移到卡片下面，让卡片像一扇窗。 */
class ScreenArtBackdrop(
    private val art: GlassArt,
    private val origin: State<Offset>
) : Backdrop {

    override val isCoordinatesDependent: Boolean = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val w = art.screenWidth
        val h = art.screenHeight
        if (w <= 0f || h <= 0f) return
        val o = origin.value
        translate(-o.x, -o.y) {
            // 和页面上看到的一致：底色 + 半透明壁纸，玻璃才是“从这块背景里切出来的”
            drawRect(art.background, size = Size(w, h))
            val image = art.image
            if (image != null && image.width > 0 && image.height > 0) {
                val scale = max(w / image.width, h / image.height)
                val dw = image.width * scale
                val dh = image.height * scale
                drawImage(
                    image = image,
                    dstOffset = IntOffset(
                        ((w - dw) / 2f).roundToInt(),
                        ((h - dh) / 2f).roundToInt()
                    ),
                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                    alpha = art.imageAlpha
                )
            }
        }
    }
}

/** 页面/抽屉底色：主题底色 + 与玻璃采样源同一张壁纸，保证玻璃透出的和看到的是同一块背景。 */
@Composable
fun Modifier.glassPageBackground(): Modifier {
    val p = LocalPalette.current
    val art = LocalGlassArt.current
    return this
        .clipToBounds()
        .background(p.bg)
        .drawBehind {
            val image = art?.image ?: return@drawBehind
            val w = art.screenWidth
            val h = art.screenHeight
            if (w <= 0f || h <= 0f || image.width <= 0 || image.height <= 0) return@drawBehind
            val scale = max(w / image.width, h / image.height)
            val dw = image.width * scale
            val dh = image.height * scale
            drawImage(
                image = image,
                dstOffset = IntOffset(((w - dw) / 2f).roundToInt(), ((h - dh) / 2f).roundToInt()),
                dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                alpha = art.imageAlpha
            )
        }
}
