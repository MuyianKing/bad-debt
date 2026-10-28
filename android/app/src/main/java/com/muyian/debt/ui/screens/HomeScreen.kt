package com.muyian.debt.ui.screens

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyian.debt.state.AppState
import com.muyian.debt.state.NavMotion
import com.muyian.debt.data.Bill
import com.muyian.debt.data.Bills
import com.muyian.debt.data.GlassStyle
import com.muyian.debt.state.Screen
import com.muyian.debt.data.fmtMoney
import com.muyian.debt.data.fmtNum
import com.muyian.debt.data.Stats
import com.muyian.debt.data.WeekStats
import com.muyian.debt.data.WeekSummary
import com.muyian.debt.data.groupByDay
import com.muyian.debt.data.pad2
import com.muyian.debt.ui.components.AppFab
import com.muyian.debt.ui.components.BillList
import com.muyian.debt.ui.components.DividerLine
import com.muyian.debt.ui.components.EmptyCopy
import com.muyian.debt.ui.components.MascotFace
import com.muyian.debt.ui.components.Motion
import com.muyian.debt.ui.components.countUp
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.glassPageBackground
import com.muyian.debt.ui.components.liquidGlass
import com.muyian.debt.ui.components.navBottomPadding
import com.muyian.debt.ui.components.pressable
import com.muyian.debt.ui.components.stagger
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.accentOnLight
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HomeScreen(app: AppState, activity: Activity) {
    val p = LocalPalette.current
    val liquid = app.glassStyle == GlassStyle.Liquid
    val ctx = LocalContext.current
    var year by remember { mutableIntStateOf(app.homeYear) }
    var month by remember { mutableIntStateOf(app.homeMonth) }
    var bills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var weekBills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    val listState = rememberLazyListState()
    val navSolid by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 } }

    LaunchedEffect(navSolid) { app.homeNavSolid = navSolid }

    fun loadWeekBills() {
        val merged = ArrayList<Bill>()
        for ((y, m) in WeekStats.monthKeys()) merged.addAll(Bills.month(ctx, y, m))
        weekBills = merged
    }

    fun load() {
        bills = Bills.month(ctx, year, month)
        loadWeekBills()
    }

    LaunchedEffect(app.resumeToken, app.billsVersion) {
        load()
    }

    val totals = remember(bills) { Stats.totals(bills) }
    val monthExpense = totals.expense
    val monthIncome = totals.income
    val monthBalance = totals.balance
    val groups = remember(bills, year, month) { groupByDay(bills, year, month) }
    val week = remember(weekBills) { WeekStats.last7(weekBills) }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f
                    var dy = 0f
                    var toMenu = false
                    var event = down
                    while (event.pressed) {
                        val ev = awaitPointerEvent(PointerEventPass.Main)
                        val change = ev.changes.firstOrNull { it.id == down.id } ?: break
                        dx += change.positionChange().x
                        dy += change.positionChange().y
                        // 水平位移明显占优且向右才认方向，避免抢列表的竖向滚动；左滑不触发
                        if (!toMenu && dx > slop && dx > abs(dy) * 1.2f) toMenu = true
                        event = change
                    }
                    // 菜单页化后不再跟手拖拽，保留「向右滑开菜单」的手势（见 docs/22）
                    if (toMenu) app.navigate(Screen.Menu, NavMotion.Horizontal)
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
        ) {
            item(key = "hero") {
                Hero(
                    app = app,
                    year = year,
                    month = month,
                    expense = monthExpense,
                    income = monthIncome,
                    balance = monthBalance,
                    navSolid = navSolid,
                    onMonth = {
                        app.showDatePicker("YM", year, month, 1) { y, m, _ ->
                            year = y; month = m; app.homeYear = y; app.homeMonth = m; load()
                        }
                    },
                    onCalendar = { app.navigate(Screen.Calendar) },
                    onStats = { app.navigate(Screen.Stats) }
                )
            }
            if (groups.isNotEmpty()) {
                item(key = "week") {
                    WeekCard(week, app.glassStyle)
                }
            }
            items(groups, key = { it.day }) { group ->
                BillList(
                    app = app,
                    groups = listOf(group),
                    onRow = { app.showBillActions(it) },
                    onRowLong = { app.requestDeleteBill(it) },
                    modifier = Modifier.animateItem(),
                    glassStyle = app.glassStyle
                )
            }
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = rpx(90), bottom = rpx(60)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .size(rpx(168))
                                .clip(CircleShape)
                                .background(p.primaryWeak)
                                .padding(rpx(8)),
                            contentAlignment = Alignment.Center
                        ) {
                            MascotFace(app, "empty", 152, head = true)
                        }
                        Spacer(Modifier.height(rpx(28)))
                        Text("这月还没开账", color = p.sub, fontSize = rsp(30), fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(rpx(10)))
                        Text(
                            "点下方 + 记第一笔，${app.activeMascot.name}帮你盯着",
                            color = p.dim,
                            fontSize = rsp(24)
                        )
                    }
                }
            }
            item(key = "bottom") {
                Spacer(Modifier.height(rpx(150) + navBottomPadding()))
            }
        }

        // 吸顶导航：始终固定在顶部，滚动后铺与页面一致的底色 + 壁纸，不用白底
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (navSolid) Modifier.glassPageBackground() else Modifier)
        ) {
            // 左右按钮数不同（左 1 右 2），用 Row 等分空白会把日期推向左侧；改用 Box 以屏幕中线定位
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = rpx(24), vertical = rpx(12))
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(rpx(68))
                        .clip(CircleShape)
                        .pressable(if (navSolid || liquid) p.fill else p.inkOnPrimary.copy(alpha = 0.18f)) {
                            app.navigate(Screen.Menu, NavMotion.Horizontal)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon("menu", if (navSolid || liquid) p.text else p.inkOnPrimary, 22.dp)
                }
                Row(
                    Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(rpx(999)))
                        .clickable {
                            app.showDatePicker("YM", year, month, 1) { y, m, _ ->
                                year = y; month = m; app.homeYear = y; app.homeMonth = m; load()
                            }
                        }
                        .padding(horizontal = rpx(22), vertical = rpx(8)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧补上与「间距 + 箭头」等宽的空白，让月份文字本身落在中线上；
                    // 箭头是固定 dp，不能用 rpx 换算，否则补偿宽度会随屏幕缩放偏小
                    Spacer(Modifier.width(rpx(6) + 15.dp))
                    Text(
                        "$year-${pad2(month)}",
                        color = if (navSolid || liquid) p.text else p.inkOnPrimary,
                        fontSize = rsp(30),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(rpx(6)))
                    AppIcon(
                        "chevron-down",
                        if (navSolid || liquid) p.text else p.onPrimarySub,
                        15.dp
                    )
                }
                Row(
                    Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(rpx(68)).clip(CircleShape)
                            .pressable(if (navSolid || liquid) p.fill else p.inkOnPrimary.copy(alpha = 0.18f)) { app.navigate(Screen.Calendar) },
                        contentAlignment = Alignment.Center
                    ) { AppIcon("calendar", if (navSolid || liquid) p.text else p.inkOnPrimary, 20.dp) }
                    Box(
                        Modifier.size(rpx(68)).clip(CircleShape)
                            .pressable(if (navSolid || liquid) p.fill else p.inkOnPrimary.copy(alpha = 0.18f)) { app.navigate(Screen.Stats) },
                        contentAlignment = Alignment.Center
                    ) { AppIcon("bar-chart", if (navSolid || liquid) p.text else p.inkOnPrimary, 20.dp) }
                }
            }
        }

        AppFab(
            onClick = { app.openAdd() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = rpx(48))
        )

    }
}

@Composable
private fun Hero(
    app: AppState,
    year: Int,
    month: Int,
    expense: Double,
    income: Double,
    balance: Double,
    navSolid: Boolean,
    onMonth: () -> Unit,
    onCalendar: () -> Unit,
    onStats: () -> Unit
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val expenseShown = countUp(expense)
    val incomeShown = countUp(income)
    val balanceShown = countUp(balance)
    val liquid = app.glassStyle == GlassStyle.Liquid
    val heroShape = RoundedCornerShape(bottomStart = rpx(36), bottomEnd = rpx(36))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(heroShape)
            .then(
                if (liquid) Modifier.liquidGlass(shape = heroShape)
                else Modifier.background(Brush.linearGradient(p.gradPrimaryStrong))
            )
    ) {
        if (!liquid) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.32f), Color.Transparent),
                            center = Offset(120f, 40f),
                            radius = 620f
                        )
                    )
            )
        }
        Box(Modifier.matchParentSize()) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = rpx(20)),
                horizontalAlignment = Alignment.End
            ) {
                MascotFace(app, "idle", app.baseMascotStyle.widthRpx, mode = "base")
            }
        }
        if (!liquid) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(rpx(180))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f))
                        )
                    )
            )
        }
        val ink = if (liquid) p.text else p.inkOnPrimary
        val inkSub = if (liquid) p.sub else p.onPrimarySub
        val inkLine = if (liquid) p.line else p.inkOnPrimary.copy(alpha = 0.32f)
        Column(
            Modifier
                .statusBarsPadding()
                .padding(top = rpx(132), start = rpx(48), end = rpx(48), bottom = rpx(40))
        ) {
            Text(
                "本月支出",
                color = inkSub,
                fontSize = rsp(26),
                letterSpacing = rsp(2)
            )
            Text(
                "¥ ${fmtMoney(expenseShown)}",
                color = ink,
                fontSize = rsp(72),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = rpx(10))
            )
            Row(
                Modifier.padding(top = rpx(26)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("月收入", color = inkSub, fontSize = rsp(24))
                    Text(
                        fmtMoney(incomeShown),
                        color = ink,
                        fontSize = rsp(32),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = rpx(6))
                    )
                }
                Box(
                    Modifier
                        .padding(horizontal = rpx(44))
                        .width(rpx(1))
                        .height(rpx(52))
                        .background(inkLine)
                )
                Column {
                    Text("本月结余", color = inkSub, fontSize = rsp(24))
                    Text(
                        fmtMoney(balanceShown),
                        color = ink,
                        fontSize = rsp(32),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = rpx(6))
                    )
                }
            }
        }


    }
}

@Composable
private fun WeekCard(summary: WeekSummary, glassStyle: GlassStyle) {
    val p = LocalPalette.current
    val values = summary.days
    val total = summary.total
    val heights = remember(values) {
        val max = summary.max
        values.map { (_, v) ->
            if (v > 0 && max > 0) maxOf(14, Math.round(v / max * 132).toInt()) else 8
        }
    }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(620, easing = Motion.Out))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24), vertical = rpx(24))
            .then(
                if (glassStyle == GlassStyle.Liquid) Modifier.liquidGlass()
                else Modifier.glass()
            )
            .padding(start = rpx(30), end = rpx(30), top = rpx(30), bottom = rpx(24))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(rpx(40))
                        .clip(RoundedCornerShape(rpx(12)))
                        .background(Brush.linearGradient(p.gradPrimary)),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon("trending-up", p.inkOnPrimary, 14.dp, 2.4f)
                }
                Spacer(Modifier.width(rpx(12)))
                Text("最近7日支出", color = p.text, fontSize = rsp(32), fontWeight = FontWeight.Bold)
            }
            Text("总计", color = p.sub, fontSize = rsp(20))
            Spacer(Modifier.width(rpx(8)))
            Text("¥${fmtMoney(total)}", color = p.sub, fontSize = rsp(26), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(rpx(20)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            for (i in values.indices) {
                val (label, value) = values[i]
                val isToday = i == values.lastIndex
                val barT = if (value > 0) stagger(grow.value, i, values.size) else 1f
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        Modifier.height(rpx(176)),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (value > 0) fmtNum(value) else "",
                            color = p.primaryDeep,
                            fontSize = rsp(21),
                            modifier = Modifier
                                .height(rpx(34))
                                .graphicsLayer { alpha = barT }
                        )
                        Box(
                            Modifier
                                .width(rpx(26))
                                .height(rpx((heights[i] * barT).roundToInt()))
                                .clip(RoundedCornerShape(rpx(13)))
                                .background(
                                    when {
                                        value <= 0 -> p.fill
                                        isToday -> p.accentOnLight
                                        else -> p.bar
                                    }
                                )
                        )
                    }
                    Spacer(Modifier.height(rpx(10)))
                    Text(
                        label,
                        color = if (isToday) p.primaryDeep else p.sub,
                        fontSize = rsp(22),
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

