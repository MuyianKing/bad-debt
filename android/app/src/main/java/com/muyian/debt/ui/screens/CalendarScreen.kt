package com.muyian.debt.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyian.debt.data.AppState
import com.muyian.debt.data.Bill
import com.muyian.debt.data.Bills
import com.muyian.debt.data.Lunar
import com.muyian.debt.data.daysInMonth
import com.muyian.debt.data.dayGroupTitle
import com.muyian.debt.data.fmtMoney
import com.muyian.debt.data.fmtNum
import com.muyian.debt.data.groupByDay
import com.muyian.debt.data.pad2
import com.muyian.debt.ui.components.BillList
import com.muyian.debt.ui.components.DividerLine
import com.muyian.debt.ui.components.MascotFace
import com.muyian.debt.ui.components.Motion
import com.muyian.debt.ui.components.RiseIn
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.pressable
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import java.util.Calendar
import kotlin.math.abs

@Composable
fun CalendarScreen(app: AppState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val now = remember { Calendar.getInstance() }
    var year by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }
    var selectedDay by remember { mutableIntStateOf(now.get(Calendar.DAY_OF_MONTH)) }
    var mode by remember { mutableStateOf("both") }
    var bills by remember { mutableStateOf<List<Bill>>(emptyList()) }

    fun load() {
        bills = Bills.month(ctx, year, month)
    }
    fun resetSelectedDay() {
        val t = Calendar.getInstance()
        val same = t.get(Calendar.YEAR) == year && t.get(Calendar.MONTH) + 1 == month
        selectedDay = if (same) minOf(t.get(Calendar.DAY_OF_MONTH), daysInMonth(year, month)) else 1
    }

    LaunchedEffect(app.resumeToken) { load() }

    val dayAll = bills.filter { it.day == selectedDay }
    val dayBills = when (mode) {
        "income" -> dayAll.filter { it.money > 0 }
        "expense" -> dayAll.filter { it.money < 0 }
        else -> dayAll
    }
    val dayExpense = dayBills.filter { it.money < 0 }.sumOf { abs(it.money) }
    val dayIncome = dayBills.filter { it.money > 0 }.sumOf { it.money }
    val dayBalance = dayIncome - dayExpense
    val dayMood = if (dayIncome > 0 && dayIncome >= dayExpense) "success" else "idle"
    val dayTitle = dayGroupTitle(year, month, selectedDay)
    val dayLine = when {
        dayBills.isEmpty() -> when (mode) {
            "income" -> "这天没有进账"
            "expense" -> "这天没花钱"
            else -> "这天还没开账"
        }
        dayMood == "success" -> "有进账，这天不错"
        else -> "钱都花这儿了"
    }
    val emptyText = when (mode) {
        "income" -> "这一天还没有收入"
        "expense" -> "这一天还没有支出"
        else -> "这一天还没有账单"
    }
    val groups = remember(dayBills, year, month) { groupByDay(dayBills, year, month) }

    val dayMap = remember(bills) {
        val map = HashMap<Int, Pair<Double, Double>>()
        for (b in bills) {
            val old = map[b.day] ?: (0.0 to 0.0)
            map[b.day] = if (b.money < 0) old.first + abs(b.money) to old.second
            else old.first to old.second + b.money
        }
        map
    }

    val cells = remember(year, month, dayMap, mode) {
        val out = ArrayList<Int>()
        val first = Calendar.getInstance().apply { set(year, month - 1, 1) }
        var lead = first.get(Calendar.DAY_OF_WEEK) - 2 // 周一为一周开始
        if (lead < 0) lead = 6
        for (i in 0 until lead) out.add(0)
        for (d in 1..daysInMonth(year, month)) out.add(d)
        while (out.size % 7 != 0) out.add(0)
        out
    }

    fun markOf(day: Int): Pair<String, String> {
        val stat = dayMap[day]
        var text = ""
        var kind = ""
        if (stat != null) {
            when (mode) {
                "both" -> {
                    if (stat.first > 0) { text = "-${fmtNum(stat.first)}"; kind = "out" }
                    else if (stat.second > 0) { text = "+${fmtNum(stat.second)}"; kind = "in" }
                }
                "balance" -> {
                    val net = stat.second - stat.first
                    if (net != 0.0) { text = fmtNum(net); kind = if (net < 0) "out" else "in" }
                }
                "income" -> if (stat.second > 0) { text = "+${fmtNum(stat.second)}"; kind = "in" }
                "expense" -> if (stat.first > 0) { text = "-${fmtNum(stat.first)}"; kind = "out" }
            }
        }
        if (text.isEmpty()) {
            text = Lunar.lunarText(year, month, day)
            kind = "lunar"
        }
        return text to kind
    }

    fun askDelete(bill: Bill) {
        app.confirm(
            "删除账单",
            "删除这笔「${bill.costType} ${fmtMoney(abs(bill.money))}元」的记录？",
            "删除"
        ) {
            Bills.delete(ctx, bill.id)
            load()
        }
    }
    fun showRowMenu(bill: Bill) {
        app.actionSheet = com.muyian.debt.data.ActionSheetState(
            listOf("编辑" to false, "删除" to true)
        ) { index ->
            if (index == 0) app.openAdd(bill) else askDelete(bill)
        }
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = rpx(20), vertical = rpx(12)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(rpx(72)).clip(CircleShape).pressable(p.fill) { app.pop() }, contentAlignment = Alignment.Center) {
                AppIcon("chevron-left", p.text, 22.dp)
            }
            Row(
                Modifier.weight(1f).clickable {
                    app.showDatePicker("YM", year, month, 1) { y, m, _ ->
                        year = y; month = m; resetSelectedDay(); load()
                    }
                },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$year.${pad2(month)}", color = p.text, fontSize = rsp(34), fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(rpx(8)))
                AppIcon("chevron-down", p.text, 14.dp)
            }
            Spacer(Modifier.width(rpx(72)))
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = rpx(60) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(24), vertical = rpx(20))
                    .glass()
                    .padding(horizontal = rpx(14), vertical = rpx(26))
            ) {
                Row(Modifier.fillMaxWidth().padding(bottom = rpx(10))) {
                    for ((wi, w) in listOf("一", "二", "三", "四", "五", "六", "日").withIndex()) {
                        Text(
                            w,
                            color = if (wi >= 5) p.primaryDeep else p.sub,
                            fontSize = rsp(24),
                            fontWeight = if (wi >= 5) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                for (rowStart in cells.indices step 7) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val day = cells.getOrNull(rowStart + col) ?: 0
                            Box(Modifier.weight(1f).height(rpx(112)), contentAlignment = Alignment.Center) {
                                if (day > 0) {
                                    val selected = day == selectedDay
                                    val isToday = run {
                                        val t = Calendar.getInstance()
                                        t.get(Calendar.YEAR) == year && t.get(Calendar.MONTH) + 1 == month && t.get(Calendar.DAY_OF_MONTH) == day
                                    }
                                    val mark = markOf(day)
                                    val cellBg by animateColorAsState(
                                        if (selected) p.primaryWeak else Color.Transparent,
                                        tween(180, easing = Motion.InOut),
                                        label = "day-bg"
                                    )
                                    val cellScale by animateFloatAsState(
                                        if (selected) 1.06f else 1f,
                                        tween(230, easing = Motion.Out),
                                        label = "day-scale"
                                    )
                                    val dayFg by animateColorAsState(
                                        when {
                                            selected -> Color.White
                                            isToday -> p.primaryDeep
                                            else -> p.text
                                        },
                                        tween(180, easing = Motion.InOut),
                                        label = "day-fg"
                                    )
                                    Column(
                                        Modifier
                                            .fillMaxSize()
                                            .heightIn(max = rpx(100))
                                            .clip(RoundedCornerShape(rpx(if (selected) 22 else 18)))
                                            .background(cellBg)
                                            .clickable { selectedDay = day },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            Modifier
                                                .height(rpx(44))
                                                .width(rpx(44))
                                                .graphicsLayer {
                                                    scaleX = cellScale
                                                    scaleY = cellScale
                                                }
                                                .clip(RoundedCornerShape(rpx(16)))
                                                .background(
                                                    when {
                                                        selected -> Brush.linearGradient(p.gradPrimary)
                                                        isToday -> SolidColor(p.primaryTint)
                                                        else -> SolidColor(Color.Transparent)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                day.toString(),
                                                color = dayFg,
                                                fontSize = rsp(29),
                                                fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                        if (mark.first.isNotEmpty()) {
                                            Text(
                                                mark.first,
                                                color = when {
                                                    selected && mark.second == "lunar" -> p.primaryDeep
                                                    mark.second == "out" -> p.out
                                                    mark.second == "in" -> p.inn
                                                    else -> p.dim
                                                },
                                                fontSize = rsp(21),
                                                fontWeight = if (mark.second == "out" || mark.second == "in") FontWeight.Medium else FontWeight.Normal,
                                                maxLines = 1,
                                                textAlign = TextAlign.Center,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = rpx(5)).width(rpx(94))
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(24), vertical = rpx(24))
                    .glass(alpha = 0.6f)
                    .padding(rpx(6))
            ) {
                for ((value, label) in listOf(
                    "both" to "收&支",
                    "balance" to "结余",
                    "income" to "收入",
                    "expense" to "支出"
                )) {
                    val active = mode == value
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(rpx(20)))
                            .background(if (active) Brush.linearGradient(p.gradPrimary) else SolidColor(Color.Transparent))
                            .clickable { mode = value }
                            .padding(vertical = rpx(15)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (active) Color.White else p.sub,
                            fontSize = rsp(27),
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(24), vertical = rpx(24))
                    .glass()
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(if (dayBills.isNotEmpty()) Modifier.heightIn(min = rpx(210)) else Modifier)
                        .padding(horizontal = rpx(28), vertical = rpx(22))
                ) {
                    if (dayBills.isNotEmpty()) {
                        Box(Modifier.align(Alignment.BottomEnd).padding(end = rpx(10))) {
                            MascotFace(app, dayMood, 158)
                        }
                    }
                    RiseIn(key = selectedDay to mode, distance = rpx(10)) {
                    Column(Modifier.fillMaxWidth().padding(end = if (dayBills.isNotEmpty()) rpx(188) else 0.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(dayTitle, color = p.text, fontSize = rsp(30), fontWeight = FontWeight.Bold)
                            if (dayBills.isNotEmpty()) {
                                Spacer(Modifier.width(rpx(12)))
                                Text(
                                    "${dayBills.size} 笔",
                                    color = p.primaryDeep,
                                    fontSize = rsp(21),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(rpx(999)))
                                        .background(p.primaryWeak)
                                        .padding(horizontal = rpx(14), vertical = rpx(4))
                                )
                            }
                        }
                        if (dayBills.isNotEmpty()) {
                            Row(Modifier.padding(top = rpx(10)), verticalAlignment = Alignment.CenterVertically) {
                                if (mode == "income") {
                                    Text("收 ", color = p.sub, fontSize = rsp(24))
                                    Text(fmtMoney(dayIncome), color = p.inn, fontSize = rsp(24), fontWeight = FontWeight.SemiBold)
                                } else if (mode == "expense") {
                                    Text("支 ", color = p.sub, fontSize = rsp(24))
                                    Text(fmtMoney(dayExpense), color = p.out, fontSize = rsp(24), fontWeight = FontWeight.SemiBold)
                                } else {
                                    Text("支 ", color = p.sub, fontSize = rsp(24))
                                    Text(fmtMoney(dayExpense), color = p.out, fontSize = rsp(24), fontWeight = FontWeight.SemiBold)
                                    Text("  ·  ", color = p.dim, fontSize = rsp(24))
                                    Text("收 ", color = p.sub, fontSize = rsp(24))
                                    Text(fmtMoney(dayIncome), color = p.inn, fontSize = rsp(24), fontWeight = FontWeight.SemiBold)
                                    Text("  ·  ", color = p.dim, fontSize = rsp(24))
                                    Text("结 ", color = p.sub, fontSize = rsp(24))
                                    Text(
                                        fmtMoney(dayBalance),
                                        color = if (dayBalance < 0) p.out else p.inn,
                                        fontSize = rsp(24),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Row(Modifier.padding(top = rpx(12))) {
                            Text(app.activeMascot.name, color = p.primaryDeep, fontSize = rsp(23), fontWeight = FontWeight.SemiBold)
                            Text("：$dayLine", color = p.sub, fontSize = rsp(23))
                        }
                    }
                    }
                }
                DividerLine(Modifier.padding(horizontal = rpx(28)), p.line)
                if (groups.isNotEmpty()) {
                    BillList(
                        app = app,
                        groups = groups,
                        onRow = { showRowMenu(it) },
                        onRowLong = { askDelete(it) },
                        hideHead = true,
                        flush = true
                    )
                } else {
                    Column(
                        Modifier.fillMaxWidth().padding(top = rpx(56), bottom = rpx(72)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(rpx(144))
                                .clip(CircleShape)
                                .background(p.primaryWeak)
                                .padding(rpx(8)),
                            contentAlignment = Alignment.Center
                        ) { MascotFace(app, "empty", 128, head = true) }
                        Spacer(Modifier.height(rpx(24)))
                        Text(emptyText, color = p.sub, fontSize = rsp(28), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(rpx(10)))
                        Text("${app.activeMascot.name}帮你盯着", color = p.dim, fontSize = rsp(24))
                    }
                }
            }
        }
    }
}
