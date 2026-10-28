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
import com.muyian.debt.state.AppState
import com.muyian.debt.data.Bill
import com.muyian.debt.data.Bills
import com.muyian.debt.data.CalendarModel
import com.muyian.debt.data.Stats
import com.muyian.debt.data.daysInMonth
import com.muyian.debt.data.dayGroupTitle
import com.muyian.debt.data.fmtMoney
import com.muyian.debt.data.groupByDay
import com.muyian.debt.data.pad2
import com.muyian.debt.ui.components.BillList
import com.muyian.debt.ui.components.DividerLine
import com.muyian.debt.ui.components.MascotFace
import com.muyian.debt.ui.components.Motion
import com.muyian.debt.ui.components.RiseIn
import com.muyian.debt.ui.components.TopBar
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.pageBottomPadding
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

    LaunchedEffect(app.resumeToken, app.billsVersion) { load() }

    val dayAll = bills.filter { it.day == selectedDay }
    val dayBills = when (mode) {
        "income" -> dayAll.filter { it.money > 0 }
        "expense" -> dayAll.filter { it.money < 0 }
        else -> dayAll
    }
    val dayTotals = remember(dayBills) { Stats.totals(dayBills) }
    val dayExpense = dayTotals.expense
    val dayIncome = dayTotals.income
    val dayBalance = dayTotals.balance
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

    val dayMap = remember(bills) { CalendarModel.dayStats(bills) }
    val cells = remember(year, month) { CalendarModel.cells(year, month) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "",
            onBack = { app.pop() },
            titleContent = {
                Row(
                    Modifier.clickable {
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
            }
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .pageBottomPadding()
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
                                    val mark = CalendarModel.mark(dayMap[day], mode, year, month, day)
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
                                        if (mark.text.isNotEmpty()) {
                                            Text(
                                                mark.text,
                                                color = when {
                                                    selected && mark.kind == "lunar" -> p.primaryDeep
                                                    mark.kind == "out" -> p.out
                                                    mark.kind == "in" -> p.inn
                                                    else -> p.dim
                                                },
                                                fontSize = rsp(21),
                                                fontWeight = if (mark.kind == "out" || mark.kind == "in") FontWeight.Medium else FontWeight.Normal,
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
                ) {
                    if (dayBills.isNotEmpty()) {
                        // 贴住卡片底部（圆角裁切），水平位置保持原来的内缩
                        Box(Modifier.align(Alignment.BottomEnd).padding(end = rpx(38))) {
                            MascotFace(app, dayMood, 158)
                        }
                    }
                    RiseIn(key = selectedDay to mode, distance = rpx(10)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = rpx(28), vertical = rpx(22))
                            .padding(end = if (dayBills.isNotEmpty()) rpx(188) else 0.dp)
                    ) {
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
                        onRow = { app.showBillActions(it) },
                        onRowLong = { app.requestDeleteBill(it) },
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
