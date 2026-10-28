package com.muyian.debt.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muyian.debt.state.AppState
import com.muyian.debt.data.daysInMonth
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.theme.CUSTOM_INK_AUTO
import com.muyian.debt.ui.theme.CUSTOM_INK_DARK
import com.muyian.debt.ui.theme.CUSTOM_INK_WHITE
import com.muyian.debt.ui.theme.DEFAULT_CUSTOM_COLOR
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.accentOnLight
import com.muyian.debt.ui.theme.colorHex
import com.muyian.debt.ui.theme.colorToHsv
import com.muyian.debt.ui.theme.hex
import com.muyian.debt.ui.theme.hsvColor
import com.muyian.debt.ui.theme.inkForCustom
import com.muyian.debt.ui.theme.inkForPrimary
import com.muyian.debt.ui.theme.normalizeCustomColor
import com.muyian.debt.ui.theme.normalizeInkMode
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import kotlinx.coroutines.delay
import kotlin.math.abs as kabs

@Composable
fun AppToastHost(app: AppState) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(app.toastToken) {
        if (app.toastToken > 0) {
            visible = true
            delay(1800)
            visible = false
        }
    }
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)) + scaleIn(tween(220, easing = Motion.Out), initialScale = 0.9f),
            exit = fadeOut(tween(190, easing = Motion.InOut)) +
                scaleOut(tween(190, easing = Motion.InOut), targetScale = 0.94f)
        ) {
            Text(
                app.toastText,
                color = Color.White,
                fontSize = rsp(26),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(rpx(14)))
                    .background(if (p.dark) p.raised.copy(alpha = 0.90f) else p.text.copy(alpha = 0.85f))
                    .padding(horizontal = rpx(34), vertical = rpx(20))
            )
        }
    }
}

@Composable
fun ConfirmDialogHost(app: AppState) {
    AnimatedCenter(value = app.dialog, onDismiss = { app.dialog = null }) { dialog ->
        val p = LocalPalette.current
        Column(
            Modifier
                .fillMaxWidth(0.86f)
                .clip(RoundedCornerShape(rpx(28)))
                .glass(RoundedCornerShape(rpx(28)))
                .padding(horizontal = rpx(32), vertical = rpx(30))
        ) {
            Text(
                dialog.title,
                color = p.text,
                fontSize = rsp(32),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            if (dialog.content.isNotEmpty()) {
                Text(
                    dialog.content,
                    color = p.sub,
                    fontSize = rsp(26),
                    lineHeight = rsp(40),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = rpx(18))
                )
            }
            Spacer(Modifier.height(rpx(28)))
            Row(horizontalArrangement = Arrangement.spacedBy(rpx(16))) {
                if (dialog.showCancel) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rpx(84))
                            .clip(RoundedCornerShape(rpx(18)))
                            .background(p.fill)
                            .clickable {
                                val cb = dialog.onCancel
                                app.dialog = null
                                cb?.invoke()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(dialog.cancelText, color = p.sub, fontSize = rsp(28), fontWeight = FontWeight.Medium)
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(rpx(84))
                        .clip(RoundedCornerShape(rpx(18)))
                        .background(p.primaryFill)
                        .clickable {
                            val cb = dialog.onConfirm
                            app.dialog = null
                            cb?.invoke()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(dialog.confirmText, color = p.inkOnPrimary, fontSize = rsp(28), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun ActionSheetHost(app: AppState) {
    AnimatedSheet(value = app.actionSheet, onDismiss = { app.actionSheet = null }) { sheet ->
        val p = LocalPalette.current
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = rpx(28), topEnd = rpx(28)))
                .glass(RoundedCornerShape(topStart = rpx(28), topEnd = rpx(28)))
                .navigationBarsPadding()
        ) {
            sheet.items.forEachIndexed { index, item ->
                if (index > 0) DividerLine(color = p.line)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(rpx(108))
                        .pressable(if (p.dark) p.fill else p.lineSoft) {
                            app.actionSheet = null
                            sheet.onSelect(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item.first,
                        color = if (item.second) p.out else p.text,
                        fontSize = rsp(30)
                    )
                }
            }
            DividerLine(color = p.line)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(rpx(108))
                    .pressable(if (p.dark) p.fill else p.lineSoft) {
                        app.actionSheet = null
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("取消", color = p.sub, fontSize = rsp(29), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun PickerSheetHost(app: AppState) {
    AnimatedSheet(value = app.picker, onDismiss = { app.picker = null }) { state ->
        val p = LocalPalette.current
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = rpx(28), topEnd = rpx(28)))
                .glass(RoundedCornerShape(topStart = rpx(28), topEnd = rpx(28)))
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rpx(28), vertical = rpx(22)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(state.title, color = p.text, fontSize = rsp(30), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(rpx(56)).clip(RoundedCornerShape(rpx(28))).pressable(p.fill) { app.picker = null },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon("x", p.sub, 20.dp)
                }
            }
            DividerLine(color = p.line)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(state.options) { option ->
                    val selected = option.second == state.selectedValue
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(rpx(96))
                            .pressable(p.fill) {
                                app.picker = null
                                state.onSelect(option.second)
                            }
                            .padding(horizontal = rpx(30)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            option.first,
                            color = if (selected) p.primaryDeep else p.text,
                            fontSize = rsp(28),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) AppIcon("check", p.primaryDeep, 20.dp, 2.4f)
                    }
                }
            }
        }
    }
}

private val ColorPresets = listOf(
    "#F7A600", "#F0655E", "#FF6B81", "#9A6BFF", "#4489FE",
    "#00AAF5", "#22B877", "#14B8A6", "#8B5CF6", "#FF8A3D"
)

/**
 * 自定义主题色取色面板：HSV 取色 + 预设色 + 实时预览。
 * 预览区直接用 [inkForPrimary] 算出的字色，主色偏亮/偏暗一眼能看出来。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerSheetHost(app: AppState) {
    AnimatedSheet(value = app.colorPicker, onDismiss = { app.colorPicker = null }) { state ->
        val p = LocalPalette.current
        val initial = remember(state) { normalizeCustomColor(state.initial) ?: DEFAULT_CUSTOM_COLOR }
        val start = remember(initial) { colorToHsv(hex(initial)) }
        var hue by remember(initial) { mutableFloatStateOf(start[0]) }
        var sat by remember(initial) { mutableFloatStateOf(start[1]) }
        var value by remember(initial) { mutableFloatStateOf(start[2]) }
        var inkMode by remember(state) { mutableStateOf(normalizeInkMode(state.initialInk)) }

        val color = hsvColor(hue, sat, value)
        val hexText = colorHex(color)
        val autoInk = inkForPrimary(color)
        val ink = inkForCustom(color, inkMode)
        val bright = autoInk != Color.White
        val hueStops = remember { List(7) { hsvColor(it * 60f, 1f, 1f) } }

        fun pickSaturation(pos: Offset, w: Float, h: Float) {
            sat = (pos.x / w).coerceIn(0f, 1f)
            value = (1f - pos.y / h).coerceIn(0f, 1f)
        }

        fun pickHue(pos: Offset, w: Float) {
            hue = (pos.x / w).coerceIn(0f, 1f) * 360f
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = rpx(28), topEnd = rpx(28)))
                .glass(RoundedCornerShape(topStart = rpx(28), topEnd = rpx(28)))
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rpx(28), vertical = rpx(22)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("自定义主题色", color = p.text, fontSize = rsp(30), fontWeight = FontWeight.SemiBold)
                    Text(
                        when (inkMode) {
                            CUSTOM_INK_WHITE -> "文字颜色：白色（手动）"
                            CUSTOM_INK_DARK -> "文字颜色：黑色（手动）"
                            else -> if (bright) "主色偏亮 · 自动配深色字" else "主色偏暗 · 自动配浅色字"
                        },
                        color = p.sub,
                        fontSize = rsp(21),
                        modifier = Modifier.padding(top = rpx(4))
                    )
                }
                Box(
                    Modifier.size(rpx(56)).clip(CircleShape).pressable(p.fill) { app.colorPicker = null },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon("x", p.sub, 20.dp)
                }
            }
            DividerLine(color = p.line)

            Column(Modifier.fillMaxWidth().padding(horizontal = rpx(28), vertical = rpx(24))) {
                // 饱和度/明度面板：横向白 -> 纯色，纵向透明 -> 黑
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(rpx(300))
                        .clip(RoundedCornerShape(rpx(20)))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.White, hsvColor(hue, 1f, 1f))
                            )
                        )
                ) {
                    Box(
                        Modifier.matchParentSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    )
                    Box(
                        Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    pickSaturation(it, size.width.toFloat(), size.height.toFloat())
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        pickSaturation(it, size.width.toFloat(), size.height.toFloat())
                                    }
                                ) { change, _ ->
                                    change.consume()
                                    pickSaturation(change.position, size.width.toFloat(), size.height.toFloat())
                                }
                            }
                    )
                    val thumb = rpx(26)
                    Box(
                        Modifier
                            .offset(x = (maxWidth - thumb) * sat, y = (maxHeight - thumb) * (1f - value))
                            .size(thumb)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(3.dp, color, CircleShape)
                    )
                }

                Spacer(Modifier.height(rpx(22)))

                // 色相条
                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(rpx(34))
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(hueStops))
                ) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                detectTapGestures { pickHue(it, size.width.toFloat()) }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { pickHue(it, size.width.toFloat()) }
                                ) { change, _ ->
                                    change.consume()
                                    pickHue(change.position, size.width.toFloat())
                                }
                            }
                    )
                    val knob = rpx(30)
                    Box(
                        Modifier
                            .offset(x = (maxWidth - knob) * (hue / 360f))
                            .align(Alignment.CenterStart)
                            .size(knob)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, p.line, CircleShape)
                    )
                }

                Spacer(Modifier.height(rpx(22)))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(rpx(16)),
                    verticalArrangement = Arrangement.spacedBy(rpx(16))
                ) {
                    for (preset in ColorPresets) {
                        val presetColor = hex(preset)
                        val selected = colorHex(presetColor).equals(hexText, ignoreCase = true)
                        Box(
                            Modifier
                                .size(rpx(64))
                                .clip(CircleShape)
                                .background(presetColor)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) p.text else p.lineSoft,
                                    shape = CircleShape
                                )
                                .clickable {
                                    val v = colorToHsv(presetColor)
                                    hue = v[0]
                                    sat = v[1]
                                    value = v[2]
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) AppIcon("check", inkForCustom(presetColor, inkMode), 20.dp, 3f)
                        }
                    }
                }

                Spacer(Modifier.height(rpx(22)))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("文字颜色", color = p.text, fontSize = rsp(24), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (inkMode == CUSTOM_INK_AUTO) "按主色明暗自动判断" else "手动指定",
                        color = p.sub,
                        fontSize = rsp(21)
                    )
                }
                Spacer(Modifier.height(rpx(12)))
                Row(horizontalArrangement = Arrangement.spacedBy(rpx(14))) {
                    for ((mode, label) in listOf(
                        CUSTOM_INK_AUTO to "自动",
                        CUSTOM_INK_WHITE to "白色",
                        CUSTOM_INK_DARK to "黑色"
                    )) {
                        val selected = inkMode == mode
                        Row(
                            Modifier
                                .weight(1f)
                                .height(rpx(68))
                                .clip(RoundedCornerShape(rpx(16)))
                                .background(if (selected) p.primaryTint else p.fill)
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) p.accentOnLight else Color.Transparent,
                                    shape = RoundedCornerShape(rpx(16))
                                )
                                .clickable { inkMode = mode }
                                .padding(horizontal = rpx(8)),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (mode != CUSTOM_INK_AUTO) {
                                Box(
                                    Modifier
                                        .size(rpx(22))
                                        .clip(CircleShape)
                                        .background(if (mode == CUSTOM_INK_WHITE) Color.White else Color(0xFF1D2129))
                                        .border(1.dp, p.lineSoft, CircleShape)
                                )
                                Spacer(Modifier.width(rpx(8)))
                            }
                            Text(
                                label,
                                color = if (selected) p.primaryDeep else p.sub,
                                fontSize = rsp(24),
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(rpx(24)))

                // 预览：填色 + 当前字色，直接看叠字对比
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(rpx(18)))
                        .background(color)
                        .padding(horizontal = rpx(24), vertical = rpx(20)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon("droplet", ink, 18.dp)
                    Spacer(Modifier.width(rpx(12)))
                    Text("主题色预览", color = ink, fontSize = rsp(26), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(hexText, color = ink.copy(alpha = 0.82f), fontSize = rsp(24))
                }
                Text(
                    when (inkMode) {
                        CUSTOM_INK_WHITE -> "已手动指定白色文字；叠在很亮的主色上可能不够清楚，请以预览为准"
                        CUSTOM_INK_DARK -> "已手动指定黑色文字；叠在很暗的主色上可能不够清楚，请以预览为准"
                        else -> if (bright) {
                            "主色明度较高，按钮、Hero 和标题上的文字会切到深色，保证看得清"
                        } else {
                            "主色明度较低，按钮、Hero 和标题上的文字会保持浅色"
                        }
                    },
                    color = p.sub,
                    fontSize = rsp(21),
                    lineHeight = rsp(31),
                    modifier = Modifier.padding(top = rpx(12))
                )

                Spacer(Modifier.height(rpx(26)))

                Row(horizontalArrangement = Arrangement.spacedBy(rpx(16))) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rpx(84))
                            .clip(RoundedCornerShape(rpx(18)))
                            .background(p.fill)
                            .clickable { app.colorPicker = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("取消", color = p.sub, fontSize = rsp(28), fontWeight = FontWeight.Medium)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(rpx(84))
                            .clip(RoundedCornerShape(rpx(18)))
                            .background(color)
                            .clickable {
                                val cb = state.onConfirm
                                app.colorPicker = null
                                cb(hexText, inkMode)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("应用", color = ink, fontSize = rsp(28), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(rpx(8)))
            }
        }
    }
}

@Composable
fun DatePickerHost(app: AppState) {
    AnimatedSheet(value = app.datePicker, onDismiss = { app.datePicker = null }) { s ->
        val p = LocalPalette.current
        var year by remember(s.year) { mutableIntStateOf(s.year.coerceIn(1970, 2100)) }
        var month by remember(s.month) { mutableIntStateOf(s.month.coerceIn(1, 12)) }
        var day by remember(s.day) { mutableIntStateOf(s.day.coerceIn(1, daysInMonth(s.year.coerceIn(1970, 2100), s.month.coerceIn(1, 12)))) }

        fun clampDay() {
            val max = daysInMonth(year, month)
            if (day > max) day = max
        }

        val years = remember { (1970..2100).map { "${it}年" } }
        val months = remember { (1..12).map { "${it}月" } }
        val days = remember(year, month) { (1..daysInMonth(year, month)).map { "${it}日" } }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = rpx(28), topEnd = rpx(28)))
                .glass(RoundedCornerShape(topStart = rpx(28), topEnd = rpx(28)))
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = rpx(28), vertical = rpx(20)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "取消",
                    color = p.sub,
                    fontSize = rsp(28),
                    modifier = Modifier.clickable { app.datePicker = null }.padding(rpx(8))
                )
                Text(
                    if (s.mode == "Y") "选择年份" else if (s.mode == "YM") "选择月份" else "选择日期",
                    color = p.text,
                    fontSize = rsp(30),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "确定",
                    color = p.primaryDeep,
                    fontSize = rsp(28),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable {
                            clampDay()
                            val cb = s.onConfirm
                            app.datePicker = null
                            cb(year, month, day)
                        }
                        .padding(rpx(8))
                )
            }
            DividerLine(color = p.line)
            Box(
                Modifier.fillMaxWidth().padding(horizontal = rpx(20), vertical = rpx(8))
            ) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(rpx(84))
                        .clip(RoundedCornerShape(rpx(12)))
                        .background(p.fill)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WheelColumn(
                        years,
                        (year - 1970).coerceIn(0, years.lastIndex),
                        { year = 1970 + it; clampDay() },
                        Modifier.weight(if (s.mode == "Y") 1f else 1.25f)
                    )
                    if (s.mode != "Y") {
                        WheelColumn(
                            months,
                            month - 1,
                            { month = it + 1; clampDay() },
                            Modifier.weight(1f)
                        )
                    }
                    if (s.mode == "YMD") {
                        WheelColumn(
                            days,
                            (day - 1).coerceIn(0, days.lastIndex),
                            { day = it + 1 },
                            Modifier.weight(1f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(rpx(12)))
        }
    }
}

@Composable
fun ImagePreviewHost(app: AppState) {
    val p = LocalPalette.current
    AnimatedFade(value = app.previewImage) { path ->
        val bitmap = rememberFileBitmap(path)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "图片预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            Box(Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { app.previewImage = null })
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(rpx(20))
                    .size(rpx(72))
                    .clip(RoundedCornerShape(rpx(36)))
                    .background(p.mask.copy(alpha = 0.45f))
                    .clickable { app.previewImage = null },
                contentAlignment = Alignment.Center
            ) {
                AppIcon("x", Color.White, 24.dp)
            }
        }
    }
}
