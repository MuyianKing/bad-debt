package com.muyian.debt.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.rpx
import kotlinx.coroutines.delay

/**
 * 全局动效口径。只做“短、快、不弹”的位移与淡入：
 * 页面转场 260~340ms、面板 300ms、列表逐条 300ms，退场统一比进场快。
 */
object Motion {
    const val Fast = 160
    const val Medium = 260
    const val Slow = 340

    /** 减速曲线：起步快、收尾稳，适合从屏幕边缘进入的内容。 */
    val Out = LinearOutSlowInEasing

    /** 两端慢：适合颜色、开关、退场。 */
    val InOut = FastOutSlowInEasing

    val Enter = tween<Float>(Medium, easing = Out)
    val Exit = tween<Float>(Fast, easing = InOut)
}

/**
 * 错峰进度：把整体 0~1 的进度换算成第 [index] 个元素自己的 0~1。
 * 元素越多步长越小，保证最后一个也在动画窗口内走完。
 */
fun stagger(progress: Float, index: Int, count: Int): Float {
    if (count <= 1 || index <= 0) return progress.coerceIn(0f, 1f)
    val step = 0.5f / count
    val start = (index.coerceAtMost(count - 1)) * step
    val span = 1f - (count - 1) * step
    if (span <= 0f) return progress.coerceIn(0f, 1f)
    return ((progress - start) / span).coerceIn(0f, 1f)
}

/**
 * 入场：淡入 + 从下方轻微上浮。用 [key] 控制“什么时候重播”，
 * 滚动出屏幕再回来会重新播一次，和列表的进入节奏一致。
 */
@Composable
fun RiseIn(
    index: Int = 0,
    key: Any? = Unit,
    modifier: Modifier = Modifier,
    distance: Dp = rpx(18),
    content: @Composable () -> Unit
) {
    val anim = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        anim.snapTo(0f)
        delay((index.coerceIn(0, 6) * 28).toLong())
        anim.animateTo(1f, tween(300, easing = Motion.Out))
    }
    // 用 Column 而不是 Box：同一区块里可能同时包住标题和卡片，Box 会让它们叠在一起
    Column(
        modifier.graphicsLayer {
            alpha = anim.value
            translationY = (1f - anim.value) * distance.toPx()
        }
    ) {
        content()
    }
}

/**
 * 数值滚动：金额变化时从旧值补间到新值，首次进入从 0 滚上来。
 * 数字变化要一眼看完，时长压短；页面/列表的入场动画才用 300ms 左右。
 */
@Composable
fun countUp(target: Double, duration: Int = 200): Double {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) {
        anim.animateTo(target.toFloat(), tween(duration, easing = Motion.Out))
    }
    return anim.value.toDouble()
}

/**
 * 底部面板：遮罩淡入 + 面板从屏幕底部滑上来。
 * [value] 变 null 时先播退场，再释放内容，所以关闭不会硬切。
 */
@Composable
fun <T : Any> AnimatedSheet(
    value: T?,
    onDismiss: () -> Unit,
    content: @Composable (T) -> Unit
) {
    val p = LocalPalette.current
    val progress = remember { Animatable(0f) }
    var last by remember { mutableStateOf<T?>(null) }

    LaunchedEffect(value) {
        if (value != null) {
            last = value
            progress.animateTo(1f, tween(300, easing = Motion.Out))
        } else {
            progress.animateTo(0f, tween(210, easing = Motion.InOut))
            last = null
        }
    }

    val shown = last ?: return
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(p.mask)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    val t = progress.value
                    translationY = (1f - t) * size.height
                    alpha = 0.4f + 0.6f * t
                }
        ) {
            content(shown)
        }
    }
}

/**
 * 居中弹窗：遮罩淡入 + 内容从 0.9 缩放弹到 1。
 */
@Composable
fun <T : Any> AnimatedCenter(
    value: T?,
    onDismiss: () -> Unit,
    content: @Composable (T) -> Unit
) {
    val p = LocalPalette.current
    val progress = remember { Animatable(0f) }
    var last by remember { mutableStateOf<T?>(null) }

    LaunchedEffect(value) {
        if (value != null) {
            last = value
            progress.animateTo(1f, tween(240, easing = Motion.Out))
        } else {
            progress.animateTo(0f, tween(170, easing = Motion.InOut))
            last = null
        }
    }

    val shown = last ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(p.mask)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        Box(
            Modifier
                // 卡片空白处的点击不能穿透到遮罩，否则点标题旁边也会关掉弹窗
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .graphicsLayer {
                    val t = progress.value
                    scaleX = 0.9f + 0.1f * t
                    scaleY = 0.9f + 0.1f * t
                    alpha = t
                }
        ) {
            content(shown)
        }
    }
}

/**
 * 全屏淡入淡出：图片预览这类没有遮罩层概念的覆盖物。
 */
@Composable
fun <T : Any> AnimatedFade(
    value: T?,
    content: @Composable (T) -> Unit
) {
    val progress = remember { Animatable(0f) }
    var last by remember { mutableStateOf<T?>(null) }

    LaunchedEffect(value) {
        if (value != null) {
            last = value
            progress.animateTo(1f, tween(200, easing = Motion.Out))
        } else {
            progress.animateTo(0f, tween(160, easing = Motion.InOut))
            last = null
        }
    }

    val shown = last ?: return
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress.value }
    ) {
        content(shown)
    }
}
