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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import androidx.compose.material3.Text
import com.muyian.debt.state.AppState
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.theme.LocalPalette
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

/** 本地图片（头图/账单图）的解码缓存，多处页面共用。 */
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

@Composable
fun Modifier.glass(
    shape: Shape? = null,
    alpha: Float? = null
): Modifier {
    val p = LocalPalette.current
    val actual = shape ?: RoundedCornerShape(rpx(28))
    val bg = if (p.dark) p.card.copy(alpha = alpha ?: 0.82f)
    else Color.White.copy(alpha = alpha ?: 0.72f)
    val border = if (p.dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.85f)
    return this
        .shadow(10.dp, actual, clip = false, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.12f))
        .clip(actual)
        .background(bg)
        .border(1.dp, border, actual)
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
            .shadow(
                14.dp, CircleShape, clip = false,
                ambientColor = p.primary.copy(alpha = 0.35f),
                spotColor = p.primary.copy(alpha = 0.35f)
            )
            .clip(CircleShape)
            .background(Brush.linearGradient(p.gradPrimary))
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AppIcon("plus", Color.White, 26.dp, 2.6f)
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
        if (checked) p.primary else p.fill,
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
    onSelect: (String) -> Unit
) {
    val p = LocalPalette.current
    Row(
        modifier
            .clip(RoundedCornerShape(rpx(14)))
            .background(p.fill)
            .padding(rpx(4)),
        horizontalArrangement = Arrangement.spacedBy(rpx(2))
    ) {
        for (option in options) {
            val active = option == selected
            val bg by animateColorAsState(
                if (active) p.raised else Color.Transparent,
                tween(180, easing = Motion.InOut),
                label = "mini-bg"
            )
            val fg by animateColorAsState(
                if (active) p.primaryDeep else p.sub,
                tween(180, easing = Motion.InOut),
                label = "mini-fg"
            )
            Text(
                option,
                color = fg,
                fontSize = rsp(21),
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(rpx(11)))
                    .background(bg)
                    .clickable { onSelect(option) }
                    .padding(horizontal = rpx(20), vertical = rpx(6))
            )
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
    solid: Boolean = true
) {
    Box(
        modifier = modifier
            .size(rpx(size))
            .clip(RoundedCornerShape(rpx(size * 0.3f)))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(icon, Color.White, iconSize.dp, solid = solid)
    }
}

@Composable
fun IconBlock(
    icon: String,
    bg: Color,
    size: Int = 48,
    iconSize: Int = 16,
    modifier: Modifier = Modifier,
    solid: Boolean = true
) {
    Box(
        modifier = modifier
            .size(rpx(size))
            .clip(RoundedCornerShape(rpx(size * 0.3f)))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(icon, Color.White, iconSize.dp, solid = solid)
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navBarBackground(solid)
            .statusBarsPadding()
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
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (titleContent != null) titleContent() else Text(
                title,
                color = p.text,
                fontSize = rsp(32),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (actions != null) {
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        } else {
            Spacer(Modifier.width(rpx(72)))
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


