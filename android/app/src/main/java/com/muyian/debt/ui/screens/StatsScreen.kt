package com.muyian.debt.ui.screens

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyian.debt.data.AppState
import com.muyian.debt.data.Bill
import com.muyian.debt.data.Bills
import com.muyian.debt.data.Cats
import com.muyian.debt.data.fmtMoney
import com.muyian.debt.data.fmtTick
import com.muyian.debt.data.pad2
import com.muyian.debt.data.daysInMonth
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.components.DividerLine
import com.muyian.debt.ui.components.EmptyCopy
import com.muyian.debt.ui.components.MascotFace
import com.muyian.debt.ui.components.MiniToggle
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.pressable
import com.muyian.debt.ui.theme.LocalPalette
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

private data class DonutSeg(
    val name: String,
    val value: Double,
    val bills: List<Bill>,
    val pct: String,
    val color: Color
)

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
    var detailSeg by remember { mutableStateOf<DonutSeg?>(null) }
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

    LaunchedEffect(app.resumeToken) { load() }

    val expenseBills = bills.filter { it.money < 0 }
    val incomeBills = bills.filter { it.money > 0 }
    val expenseTotal = expenseBills.sumOf { kotlin.math.abs(it.money) }
    val incomeTotal = incomeBills.sumOf { it.money }
    val balance = incomeTotal - expenseTotal
    val totalMood = when {
        balance < 0 -> "warn"
        balance > 0 -> "success"
        else -> "idle"
    }

    val chartSlots = run {
        val t = Calendar.getInstance().apply { timeInMillis = nowStamp }
        if (range == "month") {
            val isCurrent = t.get(Calendar.YEAR) == year && t.get(Calendar.MONTH) + 1 == month
            if (isCurrent) t.get(Calendar.DAY_OF_MONTH)
            else {
                val isFuture = year > t.get(Calendar.YEAR) || (year == t.get(Calendar.YEAR) && month > t.get(Calendar.MONTH) + 1)
                if (isFuture) 0 else daysInMonth(year, month)
            }
        } else {
            if (year == t.get(Calendar.YEAR)) t.get(Calendar.MONTH) + 1 else if (year > t.get(Calendar.YEAR)) 0 else 12
        }
    }

    val barSums = remember(expenseBills, chartSlots, range) {
        val sums = DoubleArray(chartSlots)
        for (b in expenseBills) {
            val idx = if (range == "month") b.day - 1 else b.month - 1
            if (idx in 0 until chartSlots) sums[idx] += kotlin.math.abs(b.money)
        }
        sums.toList()
    }
    val maxBar = barSums.maxOrNull() ?: 0.0
    val axisMax = remember(maxBar) {
        if (maxBar <= 0) 0.0 else {
            val unit = 10.0.pow(max(0.0, floor(log10(maxBar / 4))))
            ceil(maxBar / 4 / unit) * 4 * unit
        }
    }
    val yTicks = if (axisMax <= 0) listOf(0.0) else listOf(axisMax, axisMax * 0.75, axisMax * 0.5, axisMax * 0.25, 0.0)
    val xLabels = remember(range, month, chartSlots) {
        if (chartSlots <= 0) listOf("", "", "")
        else if (range == "month") {
            val head = "$month-01"
            val mid = "$month-${pad2(ceil(chartSlots / 2.0).toInt())}"
            val end = "$month-${pad2(chartSlots)}"
            if (chartSlots == 1) listOf(head, "", "")
            else listOf(head, if (mid == head || mid == end) "" else mid, end)
        } else {
            if (chartSlots == 1) listOf("1月", "", "")
            else {
                val mid = "${ceil(chartSlots / 2.0).toInt()}月"
                listOf("1月", if (mid == "1月" || mid == "${chartSlots}月") "" else mid, "${chartSlots}月")
            }
        }
    }

    val reportRows = remember(bills, range, month) {
        val byMonth = range == "year"
        val map = LinkedHashMap<Int, Pair<Double, Double>>()
        for (b in bills) {
            val key = if (byMonth) b.month else b.day
            val old = map[key] ?: (0.0 to 0.0)
            map[key] = if (b.money < 0) old.first + kotlin.math.abs(b.money) to old.second
            else old.first to old.second + b.money
        }
        map.entries.sortedByDescending { it.key }.map { (key, sums) ->
            Triple(if (byMonth) "${key}月" else "${pad2(month)}-${pad2(key)}", sums.first, sums.second)
        }
    }

    val donutSegments = remember(bills, donutKind, app.categories) {
        val type = donutKind
        val list = if (type == "expense") expenseBills else incomeBills
        val map = LinkedHashMap<String, MutableList<Bill>>()
        for (b in list) {
            val name = Cats.topNameOf(app.categories, b.costType, type)
            map.getOrPut(name) { mutableListOf() }.add(b)
        }
        val groups = map.map { (name, bs) ->
            name to (bs.sumOf { kotlin.math.abs(it.money) } to bs)
        }.sortedByDescending { it.second.first }
        val total = groups.sumOf { it.second.first }
        if (total <= 0) emptyList()
        else {
            val top = groups.take(8)
            val rest = groups.drop(8)
            val out = ArrayList<DonutSeg>()
            for ((name, pair) in top) {
                out.add(
                    DonutSeg(
                        name, pair.first, pair.second,
                        String.format("%.1f%%", pair.first / total * 100),
                        Cats.colorOf(app.categories, name, type)
                    )
                )
            }
            val restValue = rest.sumOf { it.second.first }
            if (restValue > 0) {
                out.add(
                    DonutSeg(
                        "其他", restValue, rest.flatMap { it.second.second },
                        String.format("%.1f%%", restValue / total * 100),
                        Color(0xFFC8CCD4)
                    )
                )
            }
            out
        }
    }
    val donutTotal = donutSegments.sumOf { it.value }
    val selectedSeg = donutSegments.firstOrNull { it.name == selectedSegName }

    fun shift(delta: Int) {
        if (range == "month") {
            var m = month + delta
            var y = year
            if (m < 1) { m = 12; y-- }
            if (m > 12) { m = 1; y++ }
            month = m; year = y
        } else year += delta
        load()
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(if (navSolid.value) (if (p.dark) Color(0xE60A0A0B) else Color(0xB8FFFFFF)) else Color.Transparent)
        ) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = rpx(20), vertical = rpx(12)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(rpx(72)).clip(CircleShape).pressable(p.fill) { app.pop() }, contentAlignment = Alignment.Center) {
                    AppIcon("chevron-left", p.text, 22.dp)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
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
                MiniToggle(listOf("月", "年"), if (range == "month") "月" else "年") {
                    val newRange = if (it == "月") "month" else "year"
                    if (newRange != range) {
                        range = newRange
                        load()
                    }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                bottom = rpx(60) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            )
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(32), vertical = rpx(20))
                        .clip(RoundedCornerShape(rpx(32)))
                        .background(p.primaryFill)
                        .padding(start = rpx(32), end = rpx(32), top = rpx(34), bottom = rpx(30))
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        MascotFace(
                            app, totalMood, 190,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = rpx(8))
                        )
                        Column(Modifier.fillMaxWidth(0.7f)) {
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
                                    fmtMoney(expenseTotal),
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
                                        fmtMoney(incomeTotal),
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
                                        fmtMoney(balance),
                                        color = Color.White,
                                        fontSize = rsp(32),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = rpx(6)).widthIn(max = rpx(170))
                                    )
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
                            AppIcon("bar-chart", p.primaryDeep, 18.dp, solid = true)
                            Spacer(Modifier.width(rpx(12)))
                            Text(if (range == "month") "每日统计" else "每月统计", color = p.text, fontSize = rsp(32), fontWeight = FontWeight.SemiBold)
                        }
                        MiniToggle(listOf("柱状", "折线"), if (chartType == "bar") "柱状" else "折线") {
                            chartType = if (it == "柱状") "bar" else "line"
                        }
                    }
                    Spacer(Modifier.height(rpx(24)))
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
                                        Canvas(Modifier.fillMaxSize()) {
                                            if (barSums.size < 2) return@Canvas
                                            val step = size.width / (barSums.size - 1)
                                            val top = 4.dp.toPx()
                                            val plotH = size.height - top
                                            val pts = barSums.mapIndexed { i, v ->
                                                Offset(i * step, top + plotH * (1 - v / axisMax).toFloat())
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
                                                            .height(rpx(h))
                                                            .clip(RoundedCornerShape(rpx(if (range == "year") 9 else 5)))
                                                            .background(p.primary)
                                                    )
                                                }
                                            }
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
                                totalLabel = if (donutKind == "expense") "总支出" else "总收入"
                            )
                        }
                        Spacer(Modifier.height(rpx(16)))
                        val maxCat = donutSegments.maxOf { it.value }
                        for (seg in donutSegments) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(rpx(12)))
                                    .background(if (selectedSegName == seg.name) com.muyian.debt.ui.theme.tintColor(seg.color, p.dark) else Color.Transparent)
                                    .clickable { detailSeg = seg }
                                    .padding(vertical = rpx(18), horizontal = rpx(6)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(rpx(60)).clip(RoundedCornerShape(rpx(18))).background(seg.color),
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
                                                .fillMaxWidth(if (maxCat > 0) max(0.06f, (seg.value / maxCat).toFloat()) else 0f)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(rpx(3)))
                                                .background(seg.color)
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
                                Text(row.first, color = p.sub, fontSize = rsp(28), modifier = Modifier.weight(1.2f))
                                Text(
                                    if (row.second > 0) fmtMoney(row.second) else "-",
                                    color = if (row.second > 0) p.out else p.dim,
                                    fontSize = rsp(28),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    if (row.third > 0) fmtMoney(row.third) else "-",
                                    color = if (row.third > 0) p.inn else p.dim,
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

    if (detailSeg != null) {
        val seg = detailSeg!!
        val sorted = remember(seg) {
            seg.bills.sortedWith(
                compareByDescending<Bill> { it.year * 10000 + it.month * 100 + it.day }
                    .thenByDescending { it.clock }
            )
        }
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.mask)
                    .clickable { detailSeg = null }
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = rpx(28), topEnd = rpx(28)))
                    .background(p.raised)
                    .navigationBarsPadding()
            ) {
                Box(Modifier.padding(top = rpx(16), bottom = rpx(4)).width(rpx(72)).height(rpx(8)).clip(RoundedCornerShape(rpx(4))).background(p.line).align(Alignment.CenterHorizontally))
                Row(Modifier.fillMaxWidth().padding(horizontal = rpx(32), vertical = rpx(18)), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(rpx(60)).clip(RoundedCornerShape(rpx(18))).background(seg.color), contentAlignment = Alignment.Center) {
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
}

@Composable
private fun Donut(
    segments: List<DonutSeg>,
    selected: String,
    onSelect: (String) -> Unit,
    total: Double,
    selectedSeg: DonutSeg?,
    totalLabel: String
) {
    val p = LocalPalette.current
    val size = rpx(300)
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
            var start = -90f
            val selIdx = segments.indexOfFirst { it.name == selected }
            segments.forEachIndexed { index, seg ->
                val sweep = (seg.value / total * 360.0).toFloat()
                val isSel = index == selIdx
                val off = if (isSel) 4.dp.toPx() else 0f
                val mid = Math.toRadians((start + sweep / 2).toDouble())
                val cx = c + Math.cos(mid).toFloat() * off
                val cy = c + Math.sin(mid).toFloat() * off
                val color = if (selIdx >= 0 && !isSel) seg.color.copy(alpha = 0.45f) else seg.color
                drawArc(
                    color = color,
                    startAngle = start + 0.7f,
                    sweepAngle = (sweep - 1.4f).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = Offset(cx - rMid, cy - rMid),
                    size = Size(rMid * 2, rMid * 2),
                    style = Stroke(width = rOuter - rInner)
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
