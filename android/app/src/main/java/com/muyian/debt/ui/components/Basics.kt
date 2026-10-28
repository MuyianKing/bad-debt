package com.muyian.debt.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.material3.Text
import com.muyian.debt.state.AppState
import com.muyian.debt.data.GlassStyle
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.LocalRpx
import com.muyian.debt.ui.theme.accentOnLight
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp

private val mascotCache = HashMap<String, Bitmap?>()

private fun mascotBitmap(context: Context, asset: String): Bitmap? = synchronized(mascotCache) {
    mascotCache.getOrPut(asset) {
        try {
            context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
    }
}

/** 本地图片（背景图/账单图）的解码缓存，多处页面共用。 */
@Composable
fun rememberFileBitmap(path: String?): Bitmap? {
    val clean = path?.removePrefix("file://").orEmpty()
    return remember(clean) {
        if (clean.isEmpty()) null
        else try {
            BitmapFactory.decodeFile(clean)
        } catch (_: Exception) {
            null
        }
    }
}

/** 底部导航栏高度；页面底部留白统一用它，不再各写一遍 WindowInsets。 */
@Composable
fun navBottomPadding(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** 列表页底部留白：60rpx + 系统导航栏。 */
@Composable
fun Modifier.pageBottomPadding(extra: Dp = 0.dp): Modifier =
    padding(bottom = rpx(60) + navBottomPadding() + extra)

/** 吸顶导航背景：滚动后转实底，避免 Stats/首页各写一套色值。 */
@Composable
fun Modifier.navBarBackground(solid: Boolean): Modifier {
    val p = LocalPalette.current
    return background(
        if (solid) (if (p.dark) p.bg.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.72f))
        else Color.Transparent
    )
}

/** 当前玻璃材质设置；由 MainActivity 提供，glass() 据此自动切换。 */
val LocalGlassStyle = androidx.compose.runtime.compositionLocalOf { com.muyian.debt.data.GlassStyle.Frost }

/** 强制毛玻璃（设置页的预览、以及需要固定材质的场合用它）。 */
@Composable
fun Modifier.frostGlass(
    shape: Shape? = null,
    alpha: Float? = null
): Modifier {
    val p = LocalPalette.current
    val actual = shape ?: RoundedCornerShape(rpx(28))
    val bg = if (p.dark) p.card.copy(alpha = alpha ?: 0.82f)
    else Color.White.copy(alpha = alpha ?: 0.56f)
    val border = if (p.dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f)
    val sheen = if (p.dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.34f)
    return this
        .clip(actual)
        .background(bg)
        // 斜向反光：纯色底上也能看出一块玻璃，而不是一块平色板
        .drawWithContent {
            // 只留左上角反光，不要底部压暗
            drawRect(
                Brush.linearGradient(
                    0f to sheen,
                    0.48f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            drawContent()
        }
        .border(1.dp, border, actual)
}

/** 玻璃卡：跟随全局材质设置，液态时直接复用 liquidGlass。 */
@Composable
fun Modifier.glass(
    shape: Shape? = null,
    alpha: Float? = null
): Modifier {
    if (LocalGlassStyle.current == com.muyian.debt.data.GlassStyle.Liquid && LocalGlassArt.current != null) {
        return liquidGlass(shape, alpha)
    }
    return frostGlass(shape, alpha)
}

/**
 * 液态玻璃：半透玻璃片，内部叠一层「隔玻璃看到的虚化背景」——几团低透明度色斑 + 压暗层，
 * 再加斜向柔光、内缘折射环、亮描边和底部暗边。这样即使页面底色很平，玻璃片本身也有层次。
 * Compose 无法低成本采样背后内容做真模糊，色斑用大半径径向渐变代替，视觉上等价且滚动无额外开销。
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape? = null,
    alpha: Float? = null,
    refractionHeight: Dp = 12.dp,
    refractionAmount: Dp = 24.dp,
    chromaticAberration: Boolean = false,
    surfaceStrength: Float = 1f
): Modifier {
    val p = LocalPalette.current
    val actual = shape ?: RoundedCornerShape(rpx(30))
    val k = (alpha ?: 1f).coerceIn(0.15f, 1f)
    // 有整屏底图（背景图/主题渐变）且系统支持 RenderEffect 时，走真采样 + 模糊 + 折射
    val art = LocalGlassArt.current
    if (art != null && isRenderEffectSupported()) {
        val density = LocalDensity.current
        val blurPx = with(density) { 4.dp.toPx() }
        val lensHeightPx = with(density) { refractionHeight.toPx() }
        val lensAmountPx = with(density) { refractionAmount.toPx() }
        val origin = remember { mutableStateOf(Offset.Zero) }
        val backdrop = remember(art, origin) { ScreenArtBackdrop(art, origin) }
        return this
            .onGloballyPositioned { coordinates ->
                val host = art.hostCoordinates.value
                if (host != null && host.isAttached && coordinates.isAttached) {
                    origin.value = host.localPositionOf(coordinates, Offset.Zero)
                }
            }
            .drawBackdrop(
            backdrop = backdrop,
            shape = { actual },
            effects = {
                blur(blurPx)
                vibrancy()
                // 折射 shader 只支持圆角形；其它形状（如整块矩形）跳过，避免直接崩
                if (actual is androidx.compose.foundation.shape.CornerBasedShape) {
                    lens(
                        refractionHeight = lensHeightPx,
                        refractionAmount = lensAmountPx,
                        depthEffect = chromaticAberration,
                        chromaticAberration = chromaticAberration
                    )
                }
            },
            highlight = if (chromaticAberration) {
                { Highlight(width = 0.8.dp, blurRadius = 1.4.dp, alpha = 0.82f, style = HighlightStyle.Default) }
            } else null,
            shadow = if (chromaticAberration) {
                { Shadow(radius = 18.dp, offset = DpOffset(0.dp, 5.dp), color = Color.Black.copy(alpha = 0.22f)) }
            } else null,
            innerShadow = if (chromaticAberration) {
                { InnerShadow(radius = 10.dp, offset = DpOffset(0.dp, 4.dp), color = Color.Black.copy(alpha = 0.16f)) }
            } else null,
            onDrawSurface = {
                if (art.image != null) {
                    // 壁纸已经按半透明压在底色上，卡片直接透它即可；白衬多给一点，保证小字不发虚
                    drawRect(
                        if (p.dark) Color.White.copy(alpha = 0.035f * k * surfaceStrength)
                        else Color.White.copy(alpha = 0.065f * k * surfaceStrength)
                    )
                } else {
                    // 纯色底：保持透明，但给一层极淡的白和最上角的反光，避免看着像平色块
                    drawRect(
                        if (p.dark) Color.White.copy(alpha = 0.03f * k)
                        else Color.White.copy(alpha = 0.05f * k)
                    )
                    val w = size.width
                    val h = size.height
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.White.copy(alpha = if (p.dark) 0.09f else 0.34f),
                            0.55f to Color.Transparent,
                            start = Offset.Zero,
                            end = Offset(w * 0.9f, h * 0.9f)
                        )
                    )

                }
            }
        )
        .drawBehind {
            // 迎光面一条极细的高亮光线：左上最亮，往右下自然消失，宽度控制在发丝级
            val outline = actual.createOutline(size, layoutDirection, this)
            val rim = when (outline) {
                is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is Outline.Generic -> outline.path
            }
            drawPath(
                rim,
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = if (p.dark) 0.9f else 0.85f),
                    0.32f to Color.White.copy(alpha = 0.28f),
                    0.62f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(size.width * 0.3f, size.height)
                ),
                style = Stroke(width = 0.6.dp.toPx())
            )
        }
    }
    val fillTop: Color
    val fillBottom: Color
    val bloom: Color
    val tintA: Color
    val tintB: Color
    val veil: Color
    val sheen: Color
    val underShade: Color
    if (p.dark) {
        fillTop = Color.White.copy(alpha = 0.105f * k)
        fillBottom = Color.White.copy(alpha = 0.045f * k)
        bloom = Color.White.copy(alpha = 0.10f * k)
        tintA = p.primary.copy(alpha = 0.12f * k)
        tintB = p.primaryLight.copy(alpha = 0.07f * k)
        veil = Color.Transparent
        sheen = Color.White.copy(alpha = 0.10f * k)
        underShade = Color.Black.copy(alpha = 0.34f)
    } else {
        fillTop = Color.White.copy(alpha = 0.30f * k)
        fillBottom = Color.White.copy(alpha = 0.18f * k)
        bloom = Color.Transparent
        tintA = p.primary.copy(alpha = 0.05f * k)
        tintB = p.primaryDeep.copy(alpha = 0.03f * k)
        veil = Color(0xFF1D2129).copy(alpha = 0.055f)
        sheen = Color.White.copy(alpha = 0.22f * k)
        underShade = Color(0xFF2A2F3A).copy(alpha = 0.09f)
    }
    return this
        .shadow(
            22.dp, actual, clip = false,
            ambientColor = Color.Black.copy(alpha = if (p.dark) 0.36f else 0.12f),
            spotColor = Color.Black.copy(alpha = if (p.dark) 0.46f else 0.18f)
        )
        .clip(actual)
        .background(Brush.verticalGradient(listOf(fillTop, fillBottom)))
        .drawWithContent {
            val w = size.width
            val h = size.height
            // 隔玻璃看到的虚化背景：极淡的主题色，避免玻璃片发灰
            if (bloom.alpha > 0f) {
                drawRect(
                    Brush.radialGradient(
                        listOf(bloom, Color.Transparent),
                        center = Offset(w * 0.80f, h * 0.10f),
                        radius = w * 0.62f
                    )
                )
            }
            if (tintA.alpha > 0f) {
                drawRect(
                    Brush.radialGradient(
                        listOf(tintA, Color.Transparent),
                        center = Offset(w * 0.08f, h * 0.96f),
                        radius = w * 0.78f
                    )
                )
            }
            if (tintB.alpha > 0f) {
                drawRect(
                    Brush.radialGradient(
                        listOf(tintB, Color.Transparent),
                        center = Offset(w * 0.98f, h * 0.62f),
                        radius = w * 0.58f
                    )
                )
            }
            // 整体压暗一档，让玻璃片比页面“沉”一点，文字更稳
            if (veil.alpha > 0f) drawRect(veil)
            // 左上角斜向柔光：玻璃表面的反射，越靠右下越淡
            drawRect(
                Brush.linearGradient(
                    0f to sheen,
                    0.52f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
            )
            // 底缘内阴影：给玻璃片一个厚度，不压内容
            val shadeTop = h * 0.70f
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, underShade),
                    startY = shadeTop,
                    endY = h
                ),
                topLeft = Offset(0f, shadeTop),
                size = Size(w, h - shadeTop)
            )
            drawContent()
        }
}

@Composable
fun Modifier.solidCard(shape: Shape? = null): Modifier {
    val p = LocalPalette.current
    val actual = shape ?: RoundedCornerShape(rpx(28))
    return this
        .shadow(8.dp, actual, clip = false, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.08f))
        .clip(actual)
        .background(p.card)
}

@Composable
fun Modifier.pressable(color: Color = LocalPalette.current.fill, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg by animateColorAsState(
        if (pressed) color else Color.Transparent,
        tween(130, easing = Motion.InOut),
        label = "press"
    )
    return this
        .background(bg)
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun MascotFace(
    app: AppState,
    mood: String = "idle",
    size: Int = 160,
    head: Boolean = false,
    mode: String = "auto",
    modifier: Modifier = Modifier
) {
    val style = app.mascotStyle(mood, mode)
    val asset = if (head) style.headAsset else style.fullAsset
    val context = LocalContext.current.applicationContext
    val bitmap = remember(asset, context) { mascotBitmap(context, asset) }
    if (bitmap != null) {
        if (head) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = style.name,
                modifier = modifier.size(rpx(size)).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = style.name,
                modifier = modifier.width(rpx(size)).aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun MascotImage(asset: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val context = LocalContext.current.applicationContext
    val bitmap = remember(asset, context) { mascotBitmap(context, asset) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = rpx(32), end = rpx(32), top = rpx(32), bottom = rpx(12)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(rpx(6))
                .height(rpx(22))
                .clip(RoundedCornerShape(rpx(3)))
                .background(Brush.linearGradient(p.gradPrimary))
        )
        Spacer(Modifier.width(rpx(12)))
        Text(
            text,
            color = p.primaryDeep,
            fontSize = rsp(25),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AppFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val liquid = LocalGlassStyle.current == GlassStyle.Liquid
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "fab")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(360, delayMillis = 90, easing = Motion.Out))
    }
    Box(
        modifier = modifier
            .size(rpx(108))
            .graphicsLayer {
                val s = scale * (0.72f + 0.28f * appear.value)
                scaleX = s
                scaleY = s
                alpha = appear.value
            }
            .then(
                if (liquid) Modifier
                    // 主操作按钮浮在列表上：补一层柔和投影 + 整圈发丝边，避免玻璃糊进账单里
                    .shadow(
                        12.dp, CircleShape, clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.14f),
                        spotColor = Color.Black.copy(alpha = 0.22f)
                    )
                    .liquidGlass(
                        shape = CircleShape,
                        refractionHeight = 28.dp,
                        refractionAmount = 60.dp,
                        chromaticAberration = true
                    )
                    .drawBehind {
                        // 左上 / 右下两团大光晕：软扩散，像光打在圆玻璃的两侧
                        val disc = Path().apply { addOval(Rect(Offset.Zero, size)) }
                        clipPath(disc) {
                            val radius = size.minDimension * 1.15f
                            val glow = if (p.dark) 0.5f else 0.42f
                            drawRect(
                                Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0f to Color.White.copy(alpha = glow),
                                        0.55f to Color.White.copy(alpha = glow * 0.35f),
                                        1f to Color.Transparent
                                    ),
                                    center = Offset.Zero,
                                    radius = radius
                                )
                            )
                            drawRect(
                                Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0f to Color.White.copy(alpha = glow * 0.85f),
                                        0.55f to Color.White.copy(alpha = glow * 0.3f),
                                        1f to Color.Transparent
                                    ),
                                    center = Offset(size.width, size.height),
                                    radius = radius
                                )
                            )
                        }
                        // 发丝边保留，别粗
                        drawCircle(
                            color = Color.White.copy(alpha = if (p.dark) 0.45f else 0.4f),
                            style = Stroke(width = 0.6.dp.toPx())
                        )
                    }
                else Modifier
                    .shadow(
                        14.dp, CircleShape, clip = false,
                        ambientColor = p.primary.copy(alpha = 0.35f),
                        spotColor = p.primary.copy(alpha = 0.35f)
                    )
                    .clip(CircleShape)
                    .background(Brush.linearGradient(p.gradPrimary))
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AppIcon("plus", if (liquid) p.text else p.inkOnPrimary, 26.dp, 2.6f)
    }
}

@Composable
fun AppSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    val knob by animateDpAsState(
        if (checked) rpx(40) else 0.dp,
        tween(230, easing = Motion.Out),
        label = "switch-knob"
    )
    val track by animateColorAsState(
        if (checked) p.accentOnLight else p.fill,
        tween(200, easing = Motion.InOut),
        label = "switch-track"
    )
    Box(
        modifier = Modifier
            .width(rpx(88))
            .height(rpx(48))
            .clip(CircleShape)
            .background(track)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled
            ) { onChange(!checked) },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(horizontal = rpx(4))
                .offset(x = knob)
                .size(rpx(40))
                .shadow(2.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(if (p.dark) p.keyFg else Color.White)
        )
    }
}

@Composable
fun MiniToggle(
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    glassStyle: GlassStyle = GlassStyle.Frost,
    onSelect: (String) -> Unit
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(rpx(14))
    val innerShape = RoundedCornerShape(rpx(11))
    val density = LocalDensity.current
    val selIndex = options.indexOf(selected).coerceAtLeast(0)

    // 槽位统一等宽（按最宽字重预留）：滑块平移时整体几何不随选中项跳变
    val measurer = rememberTextMeasurer()
    val rpxScale = LocalRpx.current
    val labelSize = rsp(21)
    val layout = remember(options, density, rpxScale, labelSize) {
        val style = TextStyle(fontSize = labelSize, fontWeight = FontWeight.SemiBold)
        val layouts = options.map { measurer.measure(AnnotatedString(it), style) }
        IntSize(layouts.maxOf { it.size.width }, layouts.maxOf { it.size.height })
    }
    val slotW = with(density) { layout.width.toDp() } + rpx(40)
    val slotH = with(density) { layout.height.toDp() } + rpx(12)

    // 一条动画同时驱动滑块位移、文字颜色与字重；静止态与逐项变色完全一致
    val slide by animateFloatAsState(
        targetValue = selIndex.toFloat(),
        animationSpec = tween(220, easing = Motion.Out),
        label = "mini-slide"
    )

    Row(
        modifier
            .clip(shape)
            .then(
                if (glassStyle == GlassStyle.Liquid) Modifier.liquidGlass(shape = shape)
                else Modifier.background(p.fill)
            )
            .padding(rpx(4))
    ) {
        Box {
            Box(
                Modifier
                    .offset(x = slotW * slide)
                    .width(slotW)
                    .height(slotH)
                    .clip(innerShape)
                    .background(p.raised)
            )
            Row {
                options.forEachIndexed { i, option ->
                    val away = abs(slide - i).coerceIn(0f, 1f)
                    Text(
                        option,
                        color = lerp(p.primaryDeep, p.sub, away),
                        fontSize = rsp(21),
                        fontWeight = FontWeight((400f + 200f * (1f - away)).roundToInt()),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(slotW)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(option) }
                            .padding(horizontal = rpx(20), vertical = rpx(6))
                    )
                }
            }
        }
    }
}

@Composable
fun StateChip(text: String, ok: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Text(
        text,
        color = if (ok) p.inn else p.outDeep,
        fontSize = rsp(21),
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(rpx(9)))
            .background(if (ok) p.inWeak else p.outWeak)
            .padding(horizontal = rpx(12), vertical = rpx(4))
    )
}

@Composable
fun IconBlock(
    icon: String,
    bg: Brush,
    size: Int = 48,
    iconSize: Int = 16,
    modifier: Modifier = Modifier,
    solid: Boolean = true,
    tint: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(rpx(size))
            .clip(RoundedCornerShape(rpx(size * 0.3f)))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(icon, tint, iconSize.dp, solid = solid)
    }
}

@Composable
fun IconBlock(
    icon: String,
    bg: Color,
    size: Int = 48,
    iconSize: Int = 16,
    modifier: Modifier = Modifier,
    solid: Boolean = true,
    tint: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(rpx(size))
            .clip(RoundedCornerShape(rpx(size * 0.3f)))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(icon, tint, iconSize.dp, solid = solid)
    }
}

@Composable
fun RowScope.StatCell(num: String, label: String, color: Color) {
    val p = LocalPalette.current
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(num, color = color, fontSize = rsp(42), fontWeight = FontWeight.Bold)
        Text(label, color = p.dim, fontSize = rsp(21), modifier = Modifier.padding(top = rpx(4)))
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier, color: Color = LocalPalette.current.line) {
    Box(modifier.fillMaxWidth().height(rpx(1)).background(color))
}

@Composable
fun EmptyCopy(text: String, hint: String) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = p.sub, fontSize = rsp(27), fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(rpx(6)))
        Text(hint, color = p.dim, fontSize = rsp(22))
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    titleContent: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val p = LocalPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navBarBackground(solid)
            .statusBarsPadding()
    ) {
        // 标题按整屏中线居中：右侧操作区比返回键宽时也不会把标题挤偏
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = rpx(120), vertical = rpx(12)),
            contentAlignment = Alignment.Center
        ) {
            if (titleContent != null) titleContent() else Text(
                title,
                color = p.text,
                fontSize = rsp(32),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = rpx(20), vertical = rpx(12)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(rpx(72))
                    .clip(CircleShape)
                    .pressable(p.fill, onBack),
                contentAlignment = Alignment.Center
            ) {
                AppIcon("chevron-left", p.text, 22.dp)
            }
            Spacer(Modifier.weight(1f))
            if (actions != null) {
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            } else {
                Spacer(Modifier.width(rpx(72)))
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: String? = null
) {
    val p = LocalPalette.current
    Row(
        modifier
            .height(rpx(92))
            .clip(RoundedCornerShape(rpx(24)))
            .background(p.primaryFill.copy(alpha = if (enabled) 1f else 0.55f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = rpx(20)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            AppIcon(icon, p.inkOnPrimary, 20.dp)
            Spacer(Modifier.width(rpx(10)))
        }
        Text(
            text,
            color = p.inkOnPrimary,
            fontSize = rsp(29),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = rsp(1)
        )
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .height(rpx(92))
            .clip(RoundedCornerShape(rpx(24)))
            .background(p.fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = p.sub, fontSize = rsp(27))
    }
}


