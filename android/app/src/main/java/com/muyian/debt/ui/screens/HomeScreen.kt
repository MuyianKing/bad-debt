package com.muyian.debt.ui.screens

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyian.debt.state.AppState
import com.muyian.debt.data.Bill
import com.muyian.debt.data.Bills
import com.muyian.debt.data.GlassStyle
import com.muyian.debt.data.NativeStatus
import com.muyian.debt.data.NativeStatusSnapshot
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
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private data class AutoBillState(val level: String, val title: String, val sub: String)

/** 抽屉底部状态：全量权限聚合，见 docs/22-layout-interactions.md。 */
private fun autoBillStateOf(s: NativeStatusSnapshot): AutoBillState {
    val missing = buildList {
        if (!s.overlay) add("悬浮窗")
        if (s.popup.miui && (if (s.popup.known) s.popup.miuiPopup != 0 else s.popup.blocked)) add("后台弹出界面")
        if (!s.notificationsAllowed) add("通知权限")
        if (!s.batteryUnrestricted) add("电池优化")
        if (!s.notificationListener) add("通知使用权")
        if (!s.repair.hasPermission) add("无障碍自恢复")
    }
    return when {
        !s.accessibility -> AutoBillState("off", "自动记账未开启", "点此开启无障碍服务")
        missing.isNotEmpty() -> {
            val names = missing.take(2).joinToString("、") +
                if (missing.size > 2) " 等 ${missing.size} 项" else ""
            AutoBillState("warn", "自动记账异常", "缺少：$names")
        }
        else -> AutoBillState("on", "自动记账运行中", "所需权限已就绪")
    }
}

@Composable
fun HomeScreen(app: AppState, activity: Activity) {
    val p = LocalPalette.current
    val liquid = app.glassStyle == GlassStyle.Liquid
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var year by remember { mutableIntStateOf(app.homeYear) }
    var month by remember { mutableIntStateOf(app.homeMonth) }
    var bills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var weekBills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var nativeStatus by remember { mutableStateOf(NativeStatus.snapshot(ctx)) }
    val listState = rememberLazyListState()
    val navSolid by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 } }

    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val drawerWidth = minOf(250.dp, screenWidth * 0.78f)
    val drawerWidthPx = with(LocalDensity.current) { drawerWidth.toPx() }
    var drawerProgress by remember { mutableFloatStateOf(0f) }
    var drawerJob by remember { mutableStateOf<Job?>(null) }
    fun animateDrawer(target: Float) {
        drawerJob?.cancel()
        drawerJob = scope.launch {
            animate(drawerProgress, target, animationSpec = tween(280)) { value, _ ->
                drawerProgress = value
            }
        }
    }
    BackHandler(enabled = drawerProgress > 0.01f) { animateDrawer(0f) }
    val drawerOpen = drawerProgress > 0.01f
    LaunchedEffect(navSolid, drawerOpen) {
        app.homeNavSolid = if (drawerOpen) false else navSolid
    }

    fun loadWeekBills() {
        val merged = ArrayList<Bill>()
        for ((y, m) in WeekStats.monthKeys()) merged.addAll(Bills.month(ctx, y, m))
        weekBills = merged
    }

    fun load() {
        bills = Bills.month(ctx, year, month)
        loadWeekBills()
        nativeStatus = NativeStatus.snapshot(ctx)
    }

    LaunchedEffect(app.resumeToken, app.billsVersion) {
        load()
        // 系统权限页返回后部分 ROM 落盘稍晚，补一次延时刷新
        delay(500)
        nativeStatus = NativeStatus.snapshot(ctx)
    }

    val totals = remember(bills) { Stats.totals(bills) }
    val monthExpense = totals.expense
    val monthIncome = totals.income
    val monthBalance = totals.balance
    val groups = remember(bills, year, month) { groupByDay(bills, year, month) }
    val week = remember(weekBills) { WeekStats.last7(weekBills) }
    val autoBill = remember(nativeStatus) { autoBillStateOf(nativeStatus) }

    fun closeDrawer() {
        animateDrawer(0f)
    }

    fun openDrawer() {
        nativeStatus = NativeStatus.snapshot(ctx)
        animateDrawer(1f)
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(drawerWidthPx) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f
                    var dy = 0f
                    var dragging = false
                    val startProgress = drawerProgress
                    var event = down
                    while (event.pressed) {
                        val ev = awaitPointerEvent(PointerEventPass.Main)
                        val change = ev.changes.firstOrNull { it.id == down.id } ?: break
                        dx += change.positionChange().x
                        dy += change.positionChange().y
                        if (!dragging) {
                            if (abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
                                if (startProgress < 0.01f && dx < 0) break
                                dragging = true
                            }
                        }
                        if (dragging) {
                            change.consume()
                            val next = (startProgress + dx / drawerWidthPx).coerceIn(0f, 1f)
                            drawerProgress = next
                        }
                        event = change
                    }
                    if (dragging) {
                        if (drawerProgress >= 0.5f) animateDrawer(1f) else animateDrawer(0f)
                    }
                }
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = drawerWidthPx * drawerProgress }
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
                    onOpenDrawer = ::openDrawer,
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

        // 吸顶导航：始终固定在顶部，滚动后转玻璃底
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = drawerWidthPx * drawerProgress }
                .background(if (navSolid) (if (p.dark) Color.Black.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.72f)) else Color.Transparent)
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
                        .pressable(if (navSolid || liquid) p.fill else Color.White.copy(alpha = 0.18f), ::openDrawer),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon("menu", if (navSolid || liquid) p.text else Color.White, 22.dp)
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
                        color = if (navSolid || liquid) p.text else Color.White,
                        fontSize = rsp(30),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(rpx(6)))
                    AppIcon(
                        "chevron-down",
                        if (navSolid || liquid) p.text else Color.White.copy(alpha = 0.9f),
                        15.dp
                    )
                }
                Row(
                    Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(rpx(68)).clip(CircleShape)
                            .pressable(if (navSolid || liquid) p.fill else Color.White.copy(alpha = 0.18f)) { app.navigate(Screen.Calendar) },
                        contentAlignment = Alignment.Center
                    ) { AppIcon("calendar", if (navSolid || liquid) p.text else Color.White, 20.dp) }
                    Box(
                        Modifier.size(rpx(68)).clip(CircleShape)
                            .pressable(if (navSolid || liquid) p.fill else Color.White.copy(alpha = 0.18f)) { app.navigate(Screen.Stats) },
                        contentAlignment = Alignment.Center
                    ) { AppIcon("bar-chart", if (navSolid || liquid) p.text else Color.White, 20.dp) }
                }
            }
        }

        AppFab(
            onClick = { app.openAdd() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = rpx(48))
                .graphicsLayer { alpha = 1f - drawerProgress }
        )

        // 遮罩
        if (drawerProgress > 0.001f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = drawerProgress }
                    .background(p.mask)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { closeDrawer() }
            )
        }

        // 抽屉面板
        Drawer(
            app = app,
            width = drawerWidth,
            progress = drawerProgress,
            autoBill = autoBill,
            onCloseAndOpen = { screen ->
                closeDrawer()
                app.navigate(screen)
            }
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
    onOpenDrawer: () -> Unit,
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
        val ink = if (liquid) p.text else Color.White
        val inkSub = if (liquid) p.sub else Color.White.copy(alpha = 0.82f)
        val inkLine = if (liquid) p.line else Color.White.copy(alpha = 0.32f)
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
                    AppIcon("trending-up", Color.White, 14.dp, 2.4f)
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
                                        isToday -> p.primary
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

@Composable
private fun Drawer(
    app: AppState,
    width: androidx.compose.ui.unit.Dp,
    progress: Float,
    autoBill: AutoBillState,
    onCloseAndOpen: (Screen) -> Unit
) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }
    val menus = listOf(
        Triple("分类管理", "list", Screen.Categories) to p.gradBlue,
        Triple("账单日历", "calendar", Screen.Calendar) to p.gradMint,
        Triple("统计分析", "bar-chart", Screen.Stats) to p.gradSky,
        Triple("备份·恢复", "rotate-ccw", Screen.Backup) to p.gradRose,
        Triple("设置·关于", "sliders", Screen.Settings) to p.gradViolet
    )
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .graphicsLayer { translationX = -widthPx * (1f - progress) }
            .glassPageBackground()
    ) {
        val drawerLiquid = app.glassStyle == GlassStyle.Liquid
        Box(
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                .then(
                    if (drawerLiquid) Modifier.liquidGlass(shape = RoundedCornerShape(0.dp))
                    else Modifier.background(Brush.linearGradient(p.gradPrimaryStrong))
                )
        ) {
            // 装饰圆圈放在 matchParentSize 里，只做背景，不参与头部高度测量
            if (!drawerLiquid) Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .offset(x = rpx(180), y = rpx(-120))
                        .size(rpx(380))
                        .clip(CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                )
                Box(
                    Modifier
                        .offset(x = rpx(250), y = rpx(30))
                        .size(rpx(190))
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                )
            }
            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(start = rpx(40), end = rpx(40), top = rpx(30), bottom = rpx(28)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(rpx(74))
                        .clip(CircleShape)
                        .background(if (drawerLiquid) p.fill else Color.White.copy(alpha = 0.22f))
                        .then(if (drawerLiquid) Modifier else Modifier.border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape))
                        .padding(rpx(2)),
                    contentAlignment = Alignment.Center
                ) {
                    MascotFace(app, "idle", 70, head = true, mode = "base")
                }
                Spacer(Modifier.width(rpx(24)))
                Column {
                    Text("孽账", color = if (drawerLiquid) p.text else Color.White, fontSize = rsp(40), fontWeight = FontWeight.Bold, letterSpacing = rsp(2))
                    Text(
                        "记在本地 · 安心省心",
                        color = if (drawerLiquid) p.sub else Color.White.copy(alpha = 0.85f),
                        fontSize = rsp(23),
                        modifier = Modifier.padding(top = rpx(6))
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = rpx(20), end = rpx(20), top = rpx(34), bottom = rpx(12))
            ) {
            Text(
                "功能",
                color = p.sub.copy(alpha = 0.85f),
                fontSize = rsp(22),
                letterSpacing = rsp(2),
                modifier = Modifier.padding(start = rpx(24), bottom = rpx(14))
            )
            for ((mi, entry) in menus.withIndex()) {
                val (item, grad) = entry
                Row(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val t = ((progress - mi * 0.06f) / 0.76f).coerceIn(0f, 1f)
                            alpha = t
                            translationX = -(1f - t) * 18.dp.toPx()
                        }
                        .clip(RoundedCornerShape(rpx(22)))
                        .pressable(p.primaryWeak) { onCloseAndOpen(item.third) }
                        .padding(rpx(20)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(rpx(64))
                            .clip(RoundedCornerShape(rpx(20)))
                            .background(Brush.linearGradient(grad)),
                        contentAlignment = Alignment.Center
                    ) { AppIcon(item.second, Color.White, 20.dp) }
                    Spacer(Modifier.width(rpx(22)))
                    Text(item.first, color = p.text, fontSize = rsp(29), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    AppIcon("chevron-right", p.dim, 16.dp)
                }
            }
            }
            AutoBillStatusCard(autoBill, app.glassStyle) { onCloseAndOpen(Screen.Settings) }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = rpx(32), end = rpx(32), top = rpx(24), bottom = rpx(28))
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(rpx(999)))
                    .then(
                        if (app.glassStyle == GlassStyle.Liquid) Modifier.liquidGlass(alpha = 0.6f)
                        else Modifier.glass(alpha = 0.6f)
                    )
                    .padding(horizontal = rpx(20), vertical = rpx(10)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon("lock", p.sub, 13.dp)
                Spacer(Modifier.width(rpx(8)))
                Text("数据仅保存在本机", color = p.sub, fontSize = rsp(21))
            }
            Text(
                "孽账 v${app.versionName}",
                color = p.dim,
                fontSize = rsp(21),
                modifier = Modifier.padding(top = rpx(16))
            )
        }
    }
}

@Composable
private fun AutoBillStatusCard(state: AutoBillState, glassStyle: GlassStyle, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(rpx(22))
    val liquid = glassStyle == GlassStyle.Liquid
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(20))
            .padding(bottom = rpx(18))
            .clip(shape)
            .then(if (liquid) Modifier.liquidGlass(shape = shape) else Modifier.glass(shape = shape))
            .then(
                if (liquid) Modifier.drawBehind {
                    // 左上 / 右下两团柔光，让玻璃卡在壁纸上有边界，不额外描边
                    val glow = if (p.dark) 0.26f else 0.24f
                    val radius = size.minDimension * 1.25f
                    drawRect(
                        Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color.White.copy(alpha = glow),
                                0.55f to Color.White.copy(alpha = glow * 0.35f),
                                1f to Color.Transparent
                            ),
                            center = Offset.Zero,
                            radius = radius
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color.White.copy(alpha = glow * 0.85f),
                                0.55f to Color.White.copy(alpha = glow * 0.3f),
                                1f to Color.Transparent
                            ),
                            center = Offset(size.width, size.height),
                            radius = radius
                        )
                    )
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = rpx(20), vertical = rpx(22)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dotColor = when (state.level) {
            "on" -> p.inn
            "off" -> p.out
            else -> p.gold
        }
        Box(
            Modifier
                .size(rpx(14))
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(rpx(18)))
        Column(Modifier.weight(1f)) {
            Text(state.title, color = p.text, fontSize = rsp(25), fontWeight = FontWeight.SemiBold)
            Text(
                state.sub,
                color = p.sub,
                fontSize = rsp(21),
                modifier = Modifier.padding(top = rpx(4))
            )
        }
        AppIcon("chevron-right", p.dim, 15.dp)
    }
}
