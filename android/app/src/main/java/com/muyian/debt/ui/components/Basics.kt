package com.muyian.debt.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.muyian.debt.data.AppState
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp

private val mascotCache = HashMap<String, Bitmap?>()

private fun mascotBitmap(context: Context, asset: String): Bitmap? =
    mascotCache.getOrPut(asset) {
        try {
            context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) {
            null
        }
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
    return this
        .background(if (pressed) color else Color.Transparent)
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
    Box(
        modifier = modifier
            .size(rpx(108))
            .graphicsLayer { scaleX = scale; scaleY = scale }
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
fun WotSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .width(rpx(88))
            .height(rpx(48))
            .clip(CircleShape)
            .background(if (checked) p.primary else p.fill)
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
                .offset(x = if (checked) rpx(40) else 0.dp)
                .size(rpx(40))
                .shadow(2.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(if (p.dark) Color(0xFFF2F2F4) else Color.White)
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
            Text(
                option,
                color = if (active) p.primaryDeep else p.sub,
                fontSize = rsp(21),
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(rpx(11)))
                    .background(if (active) p.raised else Color.Transparent)
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
fun Chevron(color: Color = LocalPalette.current.dim, size: Int = 16) {
    AppIcon("chevron-right", color, size.dp)
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
    content: @Composable (() -> Unit)? = null
) {
    val p = LocalPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (solid) (if (p.dark) Color(0xE60A0A0B) else Color(0xB8FFFFFF)) else Color.Transparent)
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
            if (content != null) content() else Text(
                title,
                color = p.text,
                fontSize = rsp(32),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(rpx(72)))
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


