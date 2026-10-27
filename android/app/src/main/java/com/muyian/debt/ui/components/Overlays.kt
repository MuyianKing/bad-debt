package com.muyian.debt.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muyian.debt.data.AppState
import com.muyian.debt.data.daysInMonth
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.theme.LocalPalette
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
                    .background(if (p.dark) Color(0xE6232327) else Color(0xD91D2129))
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
                .background(p.card)
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
                .background(p.raised)
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
                    .background(p.card)
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
                .background(p.raised)
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
                .background(p.raised)
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
    AnimatedFade(value = app.previewImage) { path ->
        val bitmap = remember(path) {
            try {
                BitmapFactory.decodeFile(path.removePrefix("file://"))
            } catch (_: Exception) {
                null
            }
        }
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
                    .background(Color(0x66000000))
                    .clickable { app.previewImage = null },
                contentAlignment = Alignment.Center
            ) {
                AppIcon("x", Color.White, 24.dp)
            }
        }
    }
}
