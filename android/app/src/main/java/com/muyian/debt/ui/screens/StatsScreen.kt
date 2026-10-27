package com.muyian.debt.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyian.debt.state.AppState
import com.muyian.debt.data.Bill
import com.muyian.debt.data.Bills
import com.muyian.debt.data.Cats
import com.muyian.debt.data.fmtMoney
import com.muyian.debt.data.fmtTick
import com.muyian.debt.data.pad2
import com.muyian.debt.data.daysInMonth
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.components.AnimatedSheet
import com.muyian.debt.ui.components.DividerLine
import com.muyian.debt.ui.components.EmptyCopy
import com.muyian.debt.ui.components.MascotFace
import com.muyian.debt.ui.components.MiniToggle
import com.muyian.debt.ui.components.Motion
import com.muyian.debt.ui.components.countUp
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.pressable
import com.muyian.debt.ui.components.stagger
import com.muyian.debt.data.DonutSlice
import com.muyian.debt.data.Stats
import com.muyian.debt.ui.components.TopBar
import com.muyian.debt.ui.components.navBottomPadding
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.hex
import com.muyian.debt.ui.theme.tintColor
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import java.util.Calendar
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun StatsScreen(app: AppState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val now = remember { Calendar.getInstance() }
    var range by remember { mutableStateOf("month") }
    var year by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }
    var bills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var nowStamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var chartType by remember { mutableStateOf("bar") }
    var donutKind by remember { mutableStateOf("expense") }
    var selectedSegName by remember { mutableStateOf("") }
    var detailSeg by remember { mutableStateOf<DonutSlice?>(null) }
    val listState = rememberLazyListState()
    val navSolid = remember {
        androidx.compose.runtime.derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8
        }
    }

    fun load() {
        nowStamp = System.currentTimeMillis()
        bills = if (range == "month") Bills.month(ctx, year, month) else Bills.year(ctx, year)
        selectedSegName = ""
    }

    LaunchedEffect(app.resumeToken, app.billsVersion) { load() }

    val totals = remember(bills) { Stats.totals(bills) }
    val expenseTotal = totals.expense
    val incomeTotal = totals.income
    val balance = totals.balance
    val expenseShown = countUp(expenseTotal)
    val incomeShown = countUp(incomeTotal)
    val balanceShown = countUp(balance)
    val barGrow = remember { Animatable(0f) }
    val donutGrow = remember { Animatable(0f) }
    LaunchedEffect(bills, range) {
        barGrow.snapTo(0f)
        barGrow.animateTo(1f, tween(700, easing = Motion.Out))
    }
    LaunchedEffect(bills, donutKind) {
        donutGrow.snapTo(0f)
        donutGrow.animateTo(1f, tween(760, easing = Motion.Out))
    }
    val totalMood = when {
        balance < 0 -> "warn"
        balance > 0 -> "success"
        else -> "idle"
    }

    val chartSlots = remember(range, year, month, nowStamp) {
        Stats.chartSlots(range, year, month, nowStamp)
    }
    val barSums = remember(bills, range, chartSlots) {
        Stats.barValues(bills, range, chartSlots)
    }
    val maxBar = barSums.maxOrNull() ?: 0.0
    // 均值分母是已显示的格数：月视图=本月已过天数，年视图=今年已过月份
    val chartAvg = remember(barSums) { if (barSums.isEmpty()) 0.0 else barSums.average() }
    val axisMax = remember(maxBar) { Stats.axisMax(maxBar) }
    val yTicks = remember(axisMax) { Stats.yTicks(axisMax) }
    val xLabels = remember(range, month, chartSlots) { Stats.xLabels(range, month, chartSlots) }
    val reportRows = remember(bills, range, month) { Stats.reportRows(bills, range, month) }
    val donutSegments = remember(bills, donutKind, app.categories) {
        Stats.donutSlices(bills, donutKind, app.categories)
    }
    val donutTotal = donutSegments.sumOf { it.value }
    val selectedSeg = donutSegments.firstOrNull { it.name == selectedSegName }

    fun shift(delta: Int) {
        val (y, m) = Stats.shift(range, year, month, delta)
        year = y
        month = m
        load()
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        TopBar(
            title = "",
            onBack = { app.pop() },
            solid = navSolid.value,
            titleContent = {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(rpx(44)).clip(CircleShape).pressable(p.fill) { shift(-1) }, contentAlignment = Alignment.Center) {
                        AppIcon("chevron-left", p.sub, 16.dp)
                    }
                    Text(
                        if (range == "month") "$year 年 $month 月" else "$year 年",
                        color = p.text,
                        fontSize = rsp(32),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable {
                                app.showDatePicker(if (range == "month") "YM" else "Y", year, month, 1) { y, m, _ ->
                                    year = y
                                    if (range == "month") month = m
                                    load()
                                }
                            }
                            .padding(horizontal = rpx(12), vertical = rpx(8))
                    )
                    Box(Modifier.size(rpx(44)).clip(CircleShape).pressable(p.fill) { shift(1) }, contentAlignment = Alignment.Center) {
                        AppIcon("chevron-right", p.sub, 16.dp)
                    }
                }
            },
            actions = {
                MiniToggle(listOf("月", "年"), if (range == "month") "月" else "年") {
                    val newRange = if (it == "月") "month" else "year"
                    if (newRange != range) {
                        range = newRange
                        load()
                    }
                }
            }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = rpx(60) + navBottomPadding()
            )
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(32), vertical = rpx(20))
                        .clip(RoundedCornerShape(rpx(32)))
                        .background(p.primaryFill)
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .padding(start = rpx(32), top = rpx(34), bottom = rpx(30))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (range == "month") "本月支出" else "本年支出",
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = rsp(30)
                            )
                            Spacer(Modifier.width(rpx(14)))
                            Text(
                                "${bills.size} 笔",
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = rsp(22),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(rpx(999)))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .padding(horizontal = rpx(16), vertical = rpx(6))
                            )
                        }
                        Spacer(Modifier.height(rpx(16)))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("¥", color = Color.White.copy(alpha = 0.72f), fontSize = rsp(32), modifier = Modifier.padding(bottom = rpx(10), end = rpx(10)))
                                Text(
                                    fmtMoney(expenseShown),
                                    color = Color.White,
                                    fontSize = rsp(78),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                        }
                        Spacer(Modifier.height(rpx(26)))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(if (range == "month") "月收入" else "年收入", color = Color.White.copy(alpha = 0.82f), fontSize = rsp(24))
                                    Text(
                                        fmtMoney(incomeShown),
                                        color = Color.White,
                                        fontSize = rsp(32),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = rpx(6)).widthIn(max = rpx(170))
                                    )
                            }
                            Box(Modifier.padding(horizontal = rpx(44)).width(rpx(1)).height(rpx(52)).background(Color.White.copy(alpha = 0.32f)))
                            Column {
                                Text(if (range == "month") "本月结余" else "本年结余", color = Color.White.copy(alpha = 0.82f), fontSize = rsp(24))
                                    Text(
                                        fmtMoney(balanceShown),
                                        color = Color.White,
                                        fontSize = rsp(32),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = rpx(6)).widthIn(max = rpx(170))
                                    )
                            }
                        }
                    }
                    MascotFace(
                        app, totalMood, 190,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = rpx(8))
                    )
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(32), vertical = rpx(20))
                        .glass()
                        .padding(rpx(32))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon("bar-chart", p.primaryDeep, 18.dp, solid = true)
                            Spacer(Modifier.width(rpx(12)))
                            Text(if (range == "month") "每日统计" else "每月统计", color = p.text, fontSize = rsp(32), fontWeight = FontWeight.SemiBold)
                        }
                        MiniToggle(listOf("柱状", "折线"), if (chartType == "bar") "柱状" else "折线") {
                            chartType = if (it == "柱状") "bar" else "line"
                        }
                    }
                    if (chartAvg > 0) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = rpx(10)),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val avgColor = p.sub.copy(alpha = 0.6f)
                            Canvas(Modifier.width(rpx(34)).height(rpx(10))) {
                                drawLine(
                                    color = avgColor,
                                    start = Offset(0f, size.height / 2),
                                    end = Offset(size.width, size.height / 2),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                                    )
                                )
                            }
                            Spacer(Modifier.width(rpx(10)))
                            Text(
                                "${if (range == "month") "日均" else "月均"} ${fmtMoney(chartAvg)}",
                                color = p.dim,
                                fontSize = rsp(22)
                            )
                        }
                    }
                    Spacer(Modifier.height(rpx(16)))
                    if (maxBar <= 0) {
                        Column(Modifier.fillMaxWidth().padding(vertical = rpx(34)), horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyCopy("该期间暂无支出", "记一笔后显示每日趋势")
                        }
                    } else {
                        Row(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.height(rpx(330)).padding(end = rpx(10)),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.End
                            ) {
                                for (t in yTicks) Text(fmtTick(t), color = p.dim, fontSize = rsp(24), maxLines = 1)
                            }
                            Column(Modifier.weight(1f)) {
                                Box(Modifier.fillMaxWidth().height(rpx(330)).background(Color.Transparent)) {
                                    // 网格线
                                    for (i in 0..3) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(top = rpx(330 * i / 4))
                                                .height(rpx(1))
                                                .background(p.lineSoft)
                                        )
                                    }
                                    if (chartType == "line") {
                                        val tint = p.primaryTint
                                        val line = p.primary
                                        val grow = barGrow.value
                                        Canvas(Modifier.fillMaxSize()) {
                                            if (barSums.size < 2) return@Canvas
                                            val step = size.width / (barSums.size - 1)
                                            val top = 4.dp.toPx()
                                            val plotH = size.height - top
                                            // 从基线向上生长：grow=0 时整条线贴在底部
                                            val pts = barSums.mapIndexed { i, v ->
                                                val targetY = top + plotH * (1 - v / axisMax).toFloat()
                                                Offset(i * step, size.height + (targetY - size.height) * grow)
                                            }
                                            val path = Path().apply {
                                                moveTo(pts[0].x, pts[0].y)
                                                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                                                lineTo(size.width, size.height)
                                                lineTo(0f, size.height)
                                                close()
                                            }
                                            drawPath(path, tint)
                                            val stroke = Path().apply {
                                                moveTo(pts[0].x, pts[0].y)
                                                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                                            }
                                            drawPath(stroke, line, style = Stroke(width = 2.dp.toPx()))
                                        }
                                    } else {
                                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                                            for (i in barSums.indices) {
                                                val v = barSums[i]
                                                val h = if (v > 0) max(10, (v / axisMax * 328).roundToInt()) else 0
                                                val grow = stagger(barGrow.value, i, barSums.size)
                                                Box(
                                                    Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                        .clickable {
                                                            if (v > 0) app.showToast(
                                                                "${if (range == "month") "${i + 1}日" else "${i + 1}月"} 支出 ${fmtMoney(v)}"
                                                            )
                                                        },
                                                    contentAlignment = Alignment.BottomCenter
                                                ) {
                                                    Box(
                                                        Modifier
                                                            .width(rpx(if (range == "year") 18 else 10))
                                                            .height(rpx((h * grow).roundToInt()))
                                                            .clip(RoundedCornerShape(rpx(if (range == "year") 9 else 5)))
                                                            .background(p.primary)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    // 平均参考线：与柱子同步从底部升起
                                    if (chartAvg > 0) {
                                        val avgColor = p.sub.copy(alpha = 0.6f)
                                        Canvas(Modifier.fillMaxSize()) {
                                            val ratio = (chartAvg / axisMax).toFloat().coerceIn(0f, 1f)
                                            val y = size.height * (1 - ratio * barGrow.value)
                                            drawLine(
                                                color = avgColor,
                                                start = Offset(0f, y),
                                                end = Offset(size.width, y),
                                                strokeWidth = 1.5.dp.toPx(),
                                                pathEffect = PathEffect.dashPathEffect(
                                                    floatArrayOf(6.dp.toPx(), 5.dp.toPx())
                                                )
                                            )
                                        }
                                    }
                                }
                                Row(Modifier.fillMaxWidth().padding(top = rpx(14))) {
                                    Text(xLabels[0], color = p.dim, fontSize = rsp(24), modifier = Modifier.weight(1f))
                                    Text(xLabels[1], color = p.dim, fontSize = rsp(24), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                    Text(xLabels[2], color = p.dim, fontSize = rsp(24), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(32), vertical = rpx(20))
                        .glass()
                        .padding(rpx(32))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon("bar-list", p.primaryDeep, 18.dp, solid = true)
                            Spacer(Modifier.width(rpx(12)))
                            Text("分类报表", color = p.text, fontSize = rsp(32), fontWeight = FontWeight.SemiBold)
                        }
                        MiniToggle(listOf("支出", "收入"), if (donutKind == "expense") "支出" else "收入") {
                            donutKind = if (it == "支出") "expense" else "income"
                        }
                    }
                    Spacer(Modifier.height(rpx(24)))
                    if (donutSegments.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = rpx(34)), horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyCopy("该期间暂无${if (donutKind == "expense") "支出" else "收入"}", "记一笔后显示分类占比")
                        }
                    } else {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Donut(
                                segments = donutSegments,
                                selected = selectedSegName,
                                onSelect = { selectedSegName = if (selectedSegName == it) "" else it },
                                total = donutTotal,
                                selectedSeg = selectedSeg,
                                totalLabel = if (donutKind == "expense") "总支出" else "总收入",
                                progress = donutGrow.value
                            )
                        }
                        Spacer(Modifier.height(rpx(16)))
                        val maxCat = donutSegments.maxOf { it.value }
                        for (seg in donutSegments) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(rpx(12)))
                                    .background(if (selectedSegName == seg.name) tintColor(hex(seg.colorHex), p.dark) else Color.Transparent)
                                    .clickable { detailSeg = seg }
                                    .padding(vertical = rpx(18), horizontal = rpx(6)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(rpx(60)).clip(RoundedCornerShape(rpx(18))).background(hex(seg.colorHex)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIcon(Cats.iconOf(app.categories, seg.name, donutKind), Color.White, 18.dp, solid = true)
                                }
                                Spacer(Modifier.width(rpx(20)))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(seg.name, color = p.text, fontSize = rsp(29), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(Modifier.width(rpx(12)))
                                        Text(seg.pct, color = p.dim, fontSize = rsp(22))
                                    }
                                    Spacer(Modifier.height(rpx(10)))
                                    Box(Modifier.fillMaxWidth().height(rpx(6)).clip(RoundedCornerShape(rpx(3))).background(p.fill)) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth(
                                                    (if (maxCat > 0) max(0.06f, (seg.value / maxCat).toFloat()) else 0f) *
                                                        donutGrow.value
                                                )
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(rpx(3)))
                                                .background(hex(seg.colorHex))
                                        )
                                    }
                                }
                                Spacer(Modifier.width(rpx(16)))
                                Text(fmtMoney(seg.value), color = p.text, fontSize = rsp(30), fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(rpx(6)))
                                AppIcon("chevron-right", p.dim, 14.dp)
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(32), vertical = rpx(20))
                        .glass()
                        .padding(rpx(32))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon("file-text", p.primaryDeep, 18.dp, solid = true)
                            Spacer(Modifier.width(rpx(12)))
                            Text(if (range == "month") "日报表" else "月报表", color = p.text, fontSize = rsp(32), fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "${reportRows.size}${if (range == "month") " 天有记录" else " 个月有记录"}",
                            color = p.dim,
                            fontSize = rsp(22)
                        )
                    }
                    Spacer(Modifier.height(rpx(16)))
                    if (reportRows.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = rpx(34)), horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyCopy("该期间暂无账单", "记一笔后显示${if (range == "month") "每日" else "每月"}明细")
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(bottom = rpx(14))) {
                            Text(if (range == "month") "日期" else "月份", color = p.dim, fontSize = rsp(24), modifier = Modifier.weight(1.2f))
                            Text("支出", color = p.dim, fontSize = rsp(24), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            Text("收入", color = p.dim, fontSize = rsp(24), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        }
                        for ((idx, row) in reportRows.withIndex()) {
                            if (idx > 0) DividerLine(color = p.lineSoft)
                            Row(Modifier.fillMaxWidth().padding(vertical = rpx(20)), verticalAlignment = Alignment.CenterVertically) {
                                Text(row.label, color = p.sub, fontSize = rsp(28), modifier = Modifier.weight(1.2f))
                                Text(
                                    if (row.expense > 0) fmtMoney(row.expense) else "-",
                                    color = if (row.expense > 0) p.out else p.dim,
                                    fontSize = rsp(28),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (row.income > 0) fmtMoney(row.income) else "-",
                                    color = if (row.income > 0) p.inn else p.dim,
                                    fontSize = rsp(28),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    AnimatedSheet(value = detailSeg, onDismiss = { detailSeg = null }) { seg ->
        val sorted = remember(seg) {
            seg.bills.sortedWith(
                compareByDescending<Bill> { it.year * 10000 + it.month * 100 + it.day }
                    .thenByDescending { it.clock }
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = rpx(28), topEnd = rpx(28)))
                .background(p.raised)
                .navigationBarsPadding()
        ) {
                Box(Modifier.padding(top = rpx(16), bottom = rpx(4)).width(rpx(72)).height(rpx(8)).clip(RoundedCornerShape(rpx(4))).background(p.line).align(Alignment.CenterHorizontally))
                Row(Modifier.fillMaxWidth().padding(horizontal = rpx(32), vertical = rpx(18)), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(rpx(60)).clip(RoundedCornerShape(rpx(18))).background(hex(seg.colorHex)), contentAlignment = Alignment.Center) {
                        AppIcon(Cats.iconOf(app.categories, seg.name, donutKind), Color.White, 18.dp, solid = true)
                    }
                    Spacer(Modifier.width(rpx(18)))
                    Column(Modifier.weight(1f)) {
                        Text(seg.name, color = p.text, fontSize = rsp(34), fontWeight = FontWeight.Bold)
                        Text("${seg.bills.size} 笔 · 占比 ${seg.pct}", color = p.sub, fontSize = rsp(23), modifier = Modifier.padding(top = rpx(4)))
                    }
                    Text("¥${fmtMoney(seg.value)}", color = p.text, fontSize = rsp(34), fontWeight = FontWeight.Bold)
                }
                DividerLine(color = p.lineSoft)
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(horizontal = rpx(32))) {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(sorted) { bill ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        detailSeg = null
                                        app.openAdd(bill)
                                    }
                                    .padding(vertical = rpx(22)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        bill.costType.ifEmpty { seg.name },
                                        color = p.text,
                                        fontSize = rsp(30)
                                    )
                                    Text(
                                        bill.dateText + if (bill.remarkText.isNotEmpty()) " ${bill.remarkText}" else "",
                                        color = p.sub,
                                        fontSize = rsp(23),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = rpx(5))
                                    )
                                }
                                Text(
                                    (if (bill.money < 0) "-" else "+") + fmtMoney(kotlin.math.abs(bill.money)),
                                    color = if (bill.money < 0) p.out else p.inn,
                                    fontSize = rsp(31),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            DividerLine(color = p.lineSoft)
                        }
                    }
                }
        }
    }
}

@Composable
private fun Donut(
    segments: List<DonutSlice>,
    selected: String,
    onSelect: (String) -> Unit,
    total: Double,
    selectedSeg: DonutSlice?,
    totalLabel: String,
    progress: Float
) {
    val p = LocalPalette.current
    val size = rpx(300)
    val t = progress.coerceIn(0f, 1f)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(segments, selected) {
                    detectTapGestures { pos ->
                        val c = this.size.width / 2f
                        val dx = pos.x - c
                        val dy = pos.y - c
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist < c * 0.62f || dist > c + 8) {
                            onSelect("")
                            return@detectTapGestures
                        }
                        var ang = atan2(dy, dx) + Math.PI / 2
                        if (ang < 0) ang += Math.PI * 2
                        var acc = 0.0
                        for (seg in segments) {
                            val span = seg.value / total * Math.PI * 2
                            if (ang >= acc && ang < acc + span) {
                                onSelect(seg.name)
                                return@detectTapGestures
                            }
                            acc += span
                        }
                    }
                }
        ) {
            val c = this.size.minDimension / 2f
            val rOuter = c - 7.dp.toPx()
            val rInner = c * 0.62f
            val rMid = (rOuter + rInner) / 2f
            var acc = 0f
            val selIdx = segments.indexOfFirst { it.name == selected }
            segments.forEachIndexed { index, seg ->
                val sweep = (seg.value / total * 360.0).toFloat()
                val isSel = index == selIdx
                val shown = sweep * t
                val arcStart = -90f + acc * t
                val off = if (isSel) 4.dp.toPx() * t else 0f
                val mid = Math.toRadians((arcStart + shown / 2).toDouble())
                val cx = c + Math.cos(mid).toFloat() * off
                val cy = c + Math.sin(mid).toFloat() * off
                val segColor = hex(seg.colorHex)
                val color = if (selIdx >= 0 && !isSel) segColor.copy(alpha = 0.45f) else segColor
                val gap = min(1.4f, shown * 0.4f)
                if (shown > gap) {
                    drawArc(
                        color = color,
                        startAngle = arcStart + gap / 2,
                        sweepAngle = shown - gap,
                        useCenter = false,
                        topLeft = Offset(cx - rMid, cy - rMid),
                        size = Size(rMid * 2, rMid * 2),
                        style = Stroke(width = rOuter - rInner)
                    )
                }
                acc += sweep
            }
        }
        Column(
            Modifier.graphicsLayer { alpha = t },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                selectedSeg?.name ?: totalLabel,
                color = p.sub,
                fontSize = rsp(23),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "¥${fmtMoney(selectedSeg?.value ?: total)}",
                color = p.text,
                fontSize = rsp(32),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = rpx(6))
            )
            Text(
                selectedSeg?.pct ?: "点击圆环查看",
                color = p.dim,
                fontSize = rsp(21),
                modifier = Modifier.padding(top = rpx(4))
            )
        }
    }
}
