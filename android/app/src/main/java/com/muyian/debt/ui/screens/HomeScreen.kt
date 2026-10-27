package com.muyian.debt.ui.screens

import android.app.Activity
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyian.debt.data.AppState
import com.muyian.debt.data.Bill
import com.muyian.debt.data.Bills
import com.muyian.debt.data.Screen
import com.muyian.debt.data.fmtMoney
import com.muyian.debt.data.fmtNum
import com.muyian.debt.data.groupByDay
import com.muyian.debt.data.pad2
import com.muyian.debt.data.weekdayCN
import com.muyian.debt.ui.components.AppFab
import com.muyian.debt.ui.components.BillList
import com.muyian.debt.ui.components.DividerLine
import com.muyian.debt.ui.components.EmptyCopy
import com.muyian.debt.ui.components.MascotFace
import com.muyian.debt.ui.components.Motion
import com.muyian.debt.ui.components.countUp
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.pressable
import com.muyian.debt.ui.components.stagger
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun HomeScreen(app: AppState, activity: Activity) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var year by remember { mutableIntStateOf(app.homeYear) }
    var month by remember { mutableIntStateOf(app.homeMonth) }
    var bills by remember { mutableStateOf<List<Bill>>(emptyList()) }
    var weekBills by remember { mutableStateOf<List<Bill>>(emptyList()) }
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
        val months = LinkedHashSet<Pair<Int, Int>>()
        val cal = Calendar.getInstance()
        for (i in 0..6) {
            months.add(cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1))
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        val merged = ArrayList<Bill>()
        for ((y, m) in months) merged.addAll(Bills.month(ctx, y, m))
        weekBills = merged
    }

    fun load() {
        bills = Bills.month(ctx, year, month)
        loadWeekBills()
    }

    LaunchedEffect(app.resumeToken) { load() }

    val monthExpense = remember(bills) { bills.filter { it.money < 0 }.sumOf { abs(it.money) } }
    val monthIncome = remember(bills) { bills.filter { it.money > 0 }.sumOf { it.money } }
    val monthBalance = monthIncome - monthExpense
    val groups = remember(bills, year, month) { groupByDay(bills, year, month) }

    val weekData = remember(weekBills) {
        val today = Calendar.getInstance()
        val todayZero = Calendar.getInstance().apply {
            set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val values = ArrayList<Pair<String, Double>>()
        var max = 0.0
        val atoms = ArrayList<Triple<Int, Int, Int>>()
        for (i in 6 downTo 0) {
            val d = Calendar.getInstance().apply { timeInMillis = todayZero - i * 86_400_000L }
            val y = d.get(Calendar.YEAR); val m = d.get(Calendar.MONTH) + 1; val day = d.get(Calendar.DAY_OF_MONTH)
            atoms.add(Triple(y, m, day))
            var v = 0.0
            for (b in weekBills) if (b.year == y && b.month == m && b.day == day && b.money < 0) v += abs(b.money)
            if (v > max) max = v
            values.add(weekdayCN(y, m, day) to v)
        }
        val heights = values.map { (_, v) ->
            if (v > 0) maxOf(14, Math.round(v / max * 132).toInt()) else 8
        }
        Triple(values, heights, values.sumOf { it.second })
    }

    fun closeDrawer() {
        animateDrawer(0f)
    }

    fun openDrawer() {
        animateDrawer(1f)
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

    Box(
        Modifier
            .fillMaxSize()
            .background(p.pageWash)
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
                    WeekCard(
                        values = weekData.first,
                        heights = weekData.second,
                        total = weekData.third
                    )
                }
            }
            items(groups, key = { it.day }) { group ->
                BillList(
                    app = app,
                    groups = listOf(group),
                    onRow = { showRowMenu(it) },
                    onRowLong = { askDelete(it) },
                    modifier = Modifier.animateItem()
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
                Spacer(
                    Modifier.height(rpx(150) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                )
            }
        }

        // 吸顶导航：始终固定在顶部，滚动后转玻璃底
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = drawerWidthPx * drawerProgress }
                .background(if (navSolid) (if (p.dark) Color.Black.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.72f)) else Color.Transparent)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = rpx(24), vertical = rpx(12)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(rpx(68))
                        .clip(CircleShape)
                        .pressable(if (navSolid) p.fill else Color.White.copy(alpha = 0.18f), ::openDrawer),
                    contentAlignment = Alignment.Center
                ) {
                    AppIcon("menu", if (navSolid) p.text else Color.White, 22.dp)
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(rpx(999)))
                        .clickable {
                            app.showDatePicker("YM", year, month, 1) { y, m, _ ->
                                year = y; month = m; app.homeYear = y; app.homeMonth = m; load()
                            }
                        }
                        .padding(horizontal = rpx(22), vertical = rpx(8)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$year-${pad2(month)}",
                        color = if (navSolid) p.text else Color.White,
                        fontSize = rsp(30),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(rpx(6)))
                    AppIcon(
                        "chevron-down",
                        if (navSolid) p.text else Color.White.copy(alpha = 0.9f),
                        15.dp
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(rpx(68)).clip(CircleShape)
                        .pressable(if (navSolid) p.fill else Color.White.copy(alpha = 0.18f)) { app.navigate(Screen.Calendar) },
                    contentAlignment = Alignment.Center
                ) { AppIcon("calendar", if (navSolid) p.text else Color.White, 20.dp) }
                Box(
                    Modifier.size(rpx(68)).clip(CircleShape)
                        .pressable(if (navSolid) p.fill else Color.White.copy(alpha = 0.18f)) { app.navigate(Screen.Stats) },
                    contentAlignment = Alignment.Center
                ) { AppIcon("bar-chart", if (navSolid) p.text else Color.White, 20.dp) }
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
    val heroBitmap = remember(app.heroImg) {
        if (app.heroImg.isEmpty()) null else try {
            BitmapFactory.decodeFile(app.heroImg.removePrefix("file://"))
        } catch (_: Exception) {
            null
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = rpx(36), bottomEnd = rpx(36)))
            .background(Brush.linearGradient(p.gradPrimaryStrong))
    ) {
        if (heroBitmap != null) {
            Image(
                bitmap = heroBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
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
        if (heroBitmap == null) {
            Box(Modifier.matchParentSize()) {
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(end = rpx(20)),
                    horizontalAlignment = Alignment.End
                ) {
                    MascotFace(app, "idle", app.baseMascotStyle.widthRpx, mode = "base")
                }
            }
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
        Column(
            Modifier
                .statusBarsPadding()
                .padding(top = rpx(132), start = rpx(48), end = rpx(48), bottom = rpx(40))
        ) {
            Text(
                "本月支出",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = rsp(26),
                letterSpacing = rsp(2)
            )
            Text(
                "¥ ${fmtMoney(expenseShown)}",
                color = Color.White,
                fontSize = rsp(72),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = rpx(10))
            )
            Row(
                Modifier.padding(top = rpx(26)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("月收入", color = Color.White.copy(alpha = 0.82f), fontSize = rsp(24))
                    Text(
                        fmtMoney(incomeShown),
                        color = Color.White,
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
                        .background(Color.White.copy(alpha = 0.32f))
                )
                Column {
                    Text("本月结余", color = Color.White.copy(alpha = 0.82f), fontSize = rsp(24))
                    Text(
                        fmtMoney(balanceShown),
                        color = Color.White,
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
private fun WeekCard(values: List<Pair<String, Double>>, heights: List<Int>, total: Double) {
    val p = LocalPalette.current
    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(620, easing = Motion.Out))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24), vertical = rpx(24))
            .glass()
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
            .background(p.pageWash)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                .background(Brush.linearGradient(p.gradPrimaryStrong))
        ) {
            Box(
                Modifier
                    .offset(x = rpx(180), y = rpx(-120))
                    .size(rpx(380))
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            )
            Box(
                Modifier
                    .offset(x = rpx(250), y = rpx(150))
                    .size(rpx(190))
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            )
            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(start = rpx(40), end = rpx(40), top = rpx(48), bottom = rpx(46)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(rpx(92))
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        .padding(rpx(2)),
                    contentAlignment = Alignment.Center
                ) {
                    MascotFace(app, "idle", 88, head = true, mode = "base")
                }
                Spacer(Modifier.width(rpx(24)))
                Column {
                    Text("孽账", color = Color.White, fontSize = rsp(40), fontWeight = FontWeight.Bold, letterSpacing = rsp(2))
                    Text(
                        "记在本地 · 安心省心",
                        color = Color.White.copy(alpha = 0.85f),
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
                    .glass(alpha = 0.6f)
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
