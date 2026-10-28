package com.muyian.debt.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.muyian.debt.state.AppState
import com.muyian.debt.data.Cats
import com.muyian.debt.data.Category
import com.muyian.debt.data.CategoryData
import com.muyian.debt.data.SubCat
import com.muyian.debt.ui.components.AppFab
import com.muyian.debt.ui.components.AnimatedCenter
import com.muyian.debt.ui.components.DividerLine
import com.muyian.debt.ui.components.MascotFace
import com.muyian.debt.ui.components.Motion
import com.muyian.debt.ui.components.glass
import com.muyian.debt.state.ActionSheetState
import com.muyian.debt.ui.components.TopBar
import com.muyian.debt.ui.components.pageBottomPadding
import com.muyian.debt.ui.components.pressable
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.hex
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import com.muyian.debt.ui.theme.tintColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(app: AppState) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("expense") }
    var expanded by remember { mutableIntStateOf(-1) }
    var data by remember { mutableStateOf(app.categoryData()) }
    val list = data.list(type)

    var savedTip by remember { mutableStateOf("") }
    var savedJob by remember { mutableStateOf<Job?>(null) }
    fun flash(text: String) {
        savedTip = text
        savedJob?.cancel()
        savedJob = scope.launch {
            delay(1200)
            savedTip = ""
        }
    }

    /* ---------- 拖拽状态 ---------- */
    var dragKind by remember { mutableStateOf<String?>(null) }
    var dragParent by remember { mutableIntStateOf(-1) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragTo by remember { mutableIntStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    var dragDx by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var justDraggedAt by remember { mutableLongStateOf(0L) }
    val cardOrigin = remember { mutableStateOf(Offset.Zero) }
    val topRects = remember { mutableStateMapOf<Int, Rect>() }   // 整块（行 + 展开的子类面板）
    val rowRects = remember { mutableStateMapOf<Int, Rect>() }   // 只有行，用来判定从哪儿起拖
    val subRects = remember { mutableStateMapOf<Int, Rect>() }
    // 拖拽开始时拍下的槽位快照（相对玻璃卡）。拖动中块自己会跟手、其余块会实时让位，
    // 若拿实时矩形做命中就会自己推自己，所以判定与吸附目标一律用这份快照。
    var dragRects by remember { mutableStateOf<List<Rect>>(emptyList()) }
    var dragPointer by remember { mutableStateOf(Offset.Zero) }

    fun persist() {
        app.saveCategories(data.deepCopy())
        data = app.categoryData()
    }

    fun startDrag(kind: String, from: Int, parent: Int, pointer: Offset) {
        if (dragKind != null) return
        val rects = if (kind == "top") topRects else subRects
        val arr = data.list(type)
        val count = if (kind == "top") arr.size else (arr.getOrNull(parent)?.subs?.size ?: 0)
        val snapshot = (0 until count).map { rects[it] ?: return }
        if (from !in snapshot.indices) return
        dragKind = kind
        dragFrom = from
        dragTo = from
        dragParent = parent
        dragDy = 0f
        dragDx = 0f
        settling = false
        dragRects = snapshot
        dragPointer = pointer
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** 长按落点：先看展开的子类格子，再看父类行；落在面板空白/添加格上不启动拖拽。 */
    fun beginDragAt(pos: Offset) {
        if (dragKind != null) return
        val arr = data.list(type)
        if (expanded >= 0) {
            val parent = expanded
            val subs = arr.getOrNull(parent)?.subs.orEmpty()
            for (si in subs.indices) {
                val r = subRects[si] ?: continue
                if (pos.x in r.left..r.right && pos.y in r.top..r.bottom) {
                    startDrag("sub", si, parent, pos)
                    return
                }
            }
        }
        for (i in arr.indices) {
            val r = rowRects[i] ?: continue
            if (pos.y in r.top..r.bottom) {
                startDrag("top", i, -1, pos)
                return
            }
        }
    }

    fun updateTarget() {
        val kind = dragKind ?: return
        if (dragRects.isEmpty()) return
        val x = dragPointer.x + dragDx
        val y = dragPointer.y + dragDy
        var hit = dragTo
        for (i in dragRects.indices) {
            val r = dragRects[i]
            if (y < r.top || y > r.bottom) continue
            if (kind == "sub" && (x < r.left || x > r.right)) continue
            hit = i
            break
        }
        if (hit != dragTo) dragTo = hit
    }

    fun commitDrag() {
        val kind = dragKind
        val from = dragFrom
        val to = dragTo
        dragKind = null
        dragFrom = -1
        dragTo = -1
        dragDy = 0f
        dragDx = 0f
        settling = false
        dragRects = emptyList()
        if (kind == null || from < 0 || to < 0 || from == to) return
        if (kind == "top") {
            val arr = data.list(type)
            if (from in arr.indices && to in arr.indices) {
                // 展开的面板跟着分类一起走，不要留在原来的行号上
                val keepName = arr.getOrNull(expanded)?.name
                arr.add(to, arr.removeAt(from))
                if (keepName != null) {
                    val idx = arr.indexOfFirst { it.name == keepName }
                    if (idx >= 0) expanded = idx
                }
            }
        } else {
            val parent = data.list(type).getOrNull(dragParent)
            val subs = parent?.subs
            if (subs != null && from in subs.indices && to in subs.indices) {
                subs.add(to, subs.removeAt(from))
            }
        }
        persist()
        flash("排好了")
    }

    fun endDrag() {
        val kind = dragKind ?: return
        if (settling) return
        settling = true
        justDraggedAt = System.currentTimeMillis()
        val from = dragFrom
        val to = dragTo
        val fr = dragRects.getOrNull(from)
        val tr = dragRects.getOrNull(to)
        if (fr == null || tr == null) {
            commitDrag()
            return
        }
        val targetY = if (to == from) 0f else if (kind == "top") {
            if (to > from) tr.top + tr.height - fr.height - fr.top else tr.top - fr.top
        } else tr.top - fr.top
        val targetX = if (kind == "sub" && to != from) tr.left - fr.left else 0f
        val startY = dragDy
        val startX = dragDx
        scope.launch {
            animate(0f, 1f, animationSpec = tween(200)) { t, _ ->
                dragDy = startY + (targetY - startY) * t
                dragDx = startX + (targetX - startX) * t
            }
            commitDrag()
        }
    }

    fun cancelDrag() {
        if (dragKind == null) return
        val startY = dragDy
        val startX = dragDx
        scope.launch {
            animate(0f, 1f, animationSpec = tween(180)) { t, _ ->
                dragDy = startY * (1f - t)
                dragDx = startX * (1f - t)
            }
            commitDrag()
        }
    }

    fun topShift(i: Int): Float {
        if (dragKind != "top") return 0f
        val h = dragRects.getOrNull(dragFrom)?.height ?: 0f
        return when {
            i == dragFrom -> dragDy
            i > dragFrom && i <= dragTo -> -h
            i < dragFrom && i >= dragTo -> h
            else -> 0f
        }
    }

    fun subShift(i: Int): Pair<Float, Float> {
        if (dragKind != "sub") return 0f to 0f
        if (i == dragFrom) return dragDx to dragDy
        if (i > dragFrom && i <= dragTo) {
            val a = dragRects.getOrNull(i - 1)
            val b = dragRects.getOrNull(i)
            if (a != null && b != null) return a.left - b.left to a.top - b.top
        }
        if (i < dragFrom && i >= dragTo) {
            val a = dragRects.getOrNull(i + 1)
            val b = dragRects.getOrNull(i)
            if (a != null && b != null) return a.left - b.left to a.top - b.top
        }
        return 0f to 0f
    }

    /* ---------- 提示条 ---------- */
    val hintMood = when {
        savedTip.isNotEmpty() -> "success"
        dragKind != null -> "warn"
        else -> "idle"
    }
    val hintLine = when {
        savedTip.isNotEmpty() -> savedTip
        dragKind != null -> if (settling) "放好了" else if (dragTo == dragFrom) "按住，上下挪" else "松手，放这儿"
        else -> if (type == "income") "点一下改名，长按能拖动" else "长按排序，点一下改图标"
    }

    /* ---------- 弹窗 ---------- */
    var dlgShow by remember { mutableStateOf(false) }
    var dlgMode by remember { mutableStateOf("addTop") }
    var dlgIndex by remember { mutableIntStateOf(-1) }
    var dlgSubIndex by remember { mutableIntStateOf(-1) }
    var dlgName by remember { mutableStateOf("") }
    var dlgIcon by remember { mutableStateOf(Cats.DEFAULT_ICON) }
    var inputFocused by remember { mutableStateOf(false) }
    val dlgTitle = when (dlgMode) {
        "addTop" -> "添加一级分类"
        "addSub" -> "添加子类"
        "editTop" -> "编辑分类"
        else -> "编辑子类"
    }

    fun openAddTop() {
        dlgMode = "addTop"; dlgName = ""; dlgIcon = Cats.DEFAULT_ICON; dlgIndex = -1; inputFocused = false; dlgShow = true
    }
    fun openAddSub(topIdx: Int) {
        dlgMode = "addSub"; dlgIndex = topIdx; dlgName = ""; dlgIcon = Cats.DEFAULT_ICON; inputFocused = false; dlgShow = true
    }
    fun openEditTop(i: Int) {
        if (System.currentTimeMillis() - justDraggedAt < 600) return
        val c = list.getOrNull(i) ?: return
        dlgMode = "editTop"; dlgIndex = i; dlgName = c.name; dlgIcon = c.icon; inputFocused = false; dlgShow = true
    }
    fun openEditSub(topIdx: Int, subIdx: Int) {
        if (System.currentTimeMillis() - justDraggedAt < 600) return
        val s = list.getOrNull(topIdx)?.subs?.getOrNull(subIdx) ?: return
        dlgMode = "editSub"; dlgIndex = topIdx; dlgSubIndex = subIdx; dlgName = s.name; dlgIcon = s.icon; inputFocused = false; dlgShow = true
    }

    fun confirmDialog() {
        val name = dlgName.trim()
        if (name.isEmpty()) { app.showToast("请输入分类名称"); return }
        val icon = dlgIcon.ifEmpty { Cats.DEFAULT_ICON }
        val arr = data.list(type)
        when (dlgMode) {
            "addTop" -> {
                if (arr.any { it.name == name }) { app.showToast("分类已存在"); return }
                arr.add(Category(name, icon))
            }
            "addSub" -> {
                val top = arr.getOrNull(dlgIndex) ?: return
                if (top.subs.any { it.name == name }) { app.showToast("子类已存在"); return }
                top.subs.add(SubCat(name, icon))
                expanded = dlgIndex
            }
            "editTop" -> {
                if (arr.withIndex().any { (idx, c) -> idx != dlgIndex && c.name == name }) { app.showToast("分类已存在"); return }
                arr.getOrNull(dlgIndex)?.let {
                    arr[dlgIndex] = it.copy(name = name, icon = icon)
                }
            }
            "editSub" -> {
                val top = arr.getOrNull(dlgIndex) ?: return
                if (top.subs.withIndex().any { (idx, s) -> idx != dlgSubIndex && s.name == name }) {
                    app.showToast("子类已存在"); return
                }
                if (dlgSubIndex in top.subs.indices) {
                    top.subs[dlgSubIndex] = SubCat(name, icon)
                }
            }
        }
        dlgShow = false
        persist()
        flash("记下了")
        app.showToast("已保存")
    }

    fun removeTop(i: Int) {
        val c = list.getOrNull(i) ?: return
        app.confirm(
            "删除分类「${c.name}」",
            "历史账单仍会保留该分类名称，仅从分类列表中移除。确定删除？",
            "删除"
        ) {
            data.list(type).removeAt(i)
            if (expanded == i) expanded = -1
            persist()
        }
    }

    fun removeSub(topIdx: Int, subIdx: Int) {
        val top = list.getOrNull(topIdx) ?: return
        val sub = top.subs.getOrNull(subIdx) ?: return
        app.confirm(
            "删除子类「${sub.name}」",
            "历史账单仍会保留该子类名称。确定删除？",
            "删除"
        ) {
            top.subs.removeAt(subIdx)
            persist()
        }
    }

    fun rowMenu(i: Int) {
        app.actionSheet = ActionSheetState(listOf("删除" to true)) {
            removeTop(i)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "",
            onBack = { app.pop() },
            titleContent = {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    for (t in listOf("expense" to "支出", "income" to "收入")) {
                        val active = type == t.first
                        val tabFg by animateColorAsState(
                            if (active) p.text else p.dim,
                            tween(180, easing = Motion.InOut),
                            label = "cat-tab-fg"
                        )
                        val underline by animateFloatAsState(
                            if (active) 1f else 0f,
                            tween(200, easing = Motion.Out),
                            label = "cat-tab-line"
                        )
                        Column(
                            Modifier
                                .clickable {
                                    if (type != t.first) {
                                        type = t.first
                                        expanded = -1
                                        subRects.clear()
                                    }
                                }
                                .padding(horizontal = rpx(24), vertical = rpx(6)),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                t.second,
                                color = tabFg,
                                fontSize = rsp(32),
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            )
                            Spacer(Modifier.height(rpx(6)))
                            Box(
                                Modifier
                                    .width(rpx(48))
                                    .height(rpx(6))
                                    .graphicsLayer { alpha = underline }
                                    .clip(RoundedCornerShape(rpx(4)))
                                    .background(p.primary)
                            )
                        }
                    }
                }
            }
        )

        Row(
            Modifier
                .fillMaxWidth()
                .background(p.primaryWeak)
                .padding(horizontal = rpx(28), vertical = rpx(12)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MascotFace(app, hintMood, 52, head = true, modifier = Modifier.padding(end = rpx(10)))
            Text(app.activeMascot.name, color = p.primaryDeep, fontSize = rsp(24), fontWeight = FontWeight.Bold)
            Text(
                "：$hintLine",
                color = p.sub,
                fontSize = rsp(24),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .pageBottomPadding(extra = rpx(100))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(24), vertical = rpx(24))
                    .glass()
                    .onGloballyPositioned { cardOrigin.value = it.positionInRoot() }
                    // 拖拽手势挂在整张卡上：卡片自己不会动，位移就是手指的真实位移，
                    // 父类行 / 子类格子只是被 offset 移动的“演员”。
                    .pointerInput(type, list.size, expanded) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { beginDragAt(it) },
                            onDrag = { change, amount ->
                                if (dragKind != null && !settling) {
                                    change.consume()
                                    dragDx += amount.x
                                    dragDy += amount.y
                                    updateTarget()
                                }
                            },
                            onDragEnd = { endDrag() },
                            onDragCancel = { cancelDrag() }
                        )
                    }
                    .padding(vertical = rpx(6))
            ) {
                for ((i, c) in list.withIndex()) {
                    key(c.name) {
                    val isDragging = dragKind == "top" && dragFrom == i
                    val shiftTarget = topShift(i)
                    val animatedShift by animateFloatAsState(
                        if (isDragging) 0f else shiftTarget,
                        if (dragKind == null) tween(0) else tween(200),
                        label = "top$i"
                    )
                    val blockShift = if (isDragging) dragDy else animatedShift
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 20f else 0f)
                            .offset { IntOffset(0, blockShift.roundToInt()) }
                            .onGloballyPositioned { coords ->
                                val origin = cardOrigin.value
                                val pos = coords.positionInRoot()
                                topRects[i] = Rect(
                                    pos.x - origin.x,
                                    pos.y - origin.y,
                                    pos.x - origin.x + coords.size.width,
                                    pos.y - origin.y + coords.size.height
                                )
                            }
                            .then(
                                if (isDragging) Modifier
                                    .shadow(12.dp, RoundedCornerShape(rpx(16)), clip = false)
                                    .clip(RoundedCornerShape(rpx(16)))
                                    .background(p.primaryWeak)
                                else Modifier
                            )
                    ) {
                        if (i > 0) DividerLine(color = p.line)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { openEditTop(i) }
                                .onGloballyPositioned { coords ->
                                    val origin = cardOrigin.value
                                    val pos = coords.positionInRoot()
                                    rowRects[i] = Rect(
                                        pos.x - origin.x,
                                        pos.y - origin.y,
                                        pos.x - origin.x + coords.size.width,
                                        pos.y - origin.y + coords.size.height
                                    )
                                }
                                .padding(horizontal = rpx(28), vertical = rpx(24)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(rpx(66))
                                    .clip(RoundedCornerShape(rpx(20)))
                                    .background(hex(Cats.paletteHex(i))),
                                contentAlignment = Alignment.Center
                            ) { AppIcon(c.icon.ifEmpty { "grid" }, Color.White, 20.dp, solid = true) }
                            Spacer(Modifier.width(rpx(20)))
                            Text(c.name, color = p.text, fontSize = rsp(30), fontWeight = FontWeight.Medium)
                            if (c.subs.isNotEmpty()) {
                                Spacer(Modifier.width(rpx(14)))
                                Text(
                                    "${c.subs.size} 个子类",
                                    color = p.primaryDeep,
                                    fontSize = rsp(21),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(rpx(9)))
                                        .background(p.primaryWeak)
                                        .padding(horizontal = rpx(10), vertical = rpx(3))
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier.size(rpx(60)).clip(CircleShape).clickable { rowMenu(i) },
                                contentAlignment = Alignment.Center
                            ) { AppIcon("more-horizontal", p.dim, 18.dp) }
                            Box(
                                Modifier.size(rpx(60)).clip(CircleShape).clickable {
                                    expanded = if (expanded == i) -1 else i
                                    if (expanded == i) subRects.clear()
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcon(if (expanded == i) "chevron-down" else "chevron-right", p.dim, 16.dp)
                            }
                        }
                        AnimatedVisibility(
                            visible = expanded == i,
                            enter = fadeIn(tween(160)) +
                                expandVertically(tween(240, easing = Motion.Out), expandFrom = Alignment.Top),
                            exit = fadeOut(tween(110, easing = Motion.InOut)) +
                                shrinkVertically(tween(190, easing = Motion.InOut), shrinkTowards = Alignment.Top)
                        ) {
                            SubPanel(
                                subs = c.subs,
                                paletteColor = hex(Cats.paletteHex(i)),
                                dragKind = dragKind,
                                dragFrom = dragFrom,
                                subRects = subRects,
                                cardOrigin = cardOrigin,
                                onEdit = { si -> openEditSub(i, si) },
                                onAdd = { openAddSub(i) },
                                subShift = { si -> subShift(si) }
                            )
                        }
                    }
                    }
                }
            }
        }

    }
    AppFab(
        onClick = ::openAddTop,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = rpx(40), bottom = rpx(48))
    )

    AnimatedCenter(value = if (dlgShow) dlgTitle else null, onDismiss = { dlgShow = false }) {
            Column(
                Modifier
                    .widthIn(max = rpx(640))
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(rpx(32)))
                    .background(if (p.dark) p.raised else Color.White.copy(alpha = 0.97f))
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                    .padding(start = rpx(32), end = rpx(32), top = rpx(40), bottom = rpx(32))
            ) {
                Text(dlgTitle, color = p.text, fontSize = rsp(32), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(rpx(26)))
                Box(Modifier.fillMaxWidth().heightIn(max = rpx(328)).verticalScroll(rememberScrollState())) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(rpx(8)),
                        verticalArrangement = Arrangement.spacedBy(rpx(8))
                    ) {
                        for ((ii, ic) in Cats.ICON_CHOICES.withIndex()) {
                            val active = dlgIcon == ic
                            val color = hex(Cats.paletteHex(ii))
                            Box(
                                Modifier
                                    .size(rpx(74))
                                    .clip(RoundedCornerShape(rpx(20)))
                                    .background(tintColor(color, p.dark, 0.88f))
                                    .then(if (active) Modifier.border(3.dp, p.primary, RoundedCornerShape(rpx(20))) else Modifier)
                                    .clickable { dlgIcon = ic },
                                contentAlignment = Alignment.Center
                            ) { AppIcon(ic, color, 19.dp, solid = true) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = rpx(24), bottom = rpx(12)), verticalAlignment = Alignment.CenterVertically) {
                    Text("分类名称", color = p.sub, fontSize = rsp(23), modifier = Modifier.weight(1f))
                    Text(
                        "${dlgName.length}/8",
                        color = if (dlgName.length >= 8) p.primaryDeep else p.dim,
                        fontSize = rsp(22)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rpx(96))
                        .clip(RoundedCornerShape(rpx(24)))
                        .background(if (inputFocused) p.card else p.primaryWeak)
                        .border(2.dp, if (inputFocused) p.primary else Color.Transparent, RoundedCornerShape(rpx(24)))
                        .padding(horizontal = rpx(18)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(rpx(46)).clip(RoundedCornerShape(rpx(14))).background(p.primaryFill),
                        contentAlignment = Alignment.Center
                    ) { AppIcon("edit-3", Color.White, 15.dp, 2.2f) }
                    Spacer(Modifier.width(rpx(16)))
                    BasicTextField(
                        value = dlgName,
                        onValueChange = { if (it.length <= 8) dlgName = it },
                        singleLine = true,
                        textStyle = TextStyle(color = p.text, fontSize = rsp(30), fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(p.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box {
                                if (dlgName.isEmpty()) Text("给分类起个名字", color = p.dim, fontSize = rsp(30))
                                inner()
                            }
                        }
                    )
                }
                Spacer(Modifier.height(rpx(32)))
                Row(horizontalArrangement = Arrangement.spacedBy(rpx(16))) {
                    DialogButton("取消", false, p.fill, p.sub) { dlgShow = false }
                    if (dlgMode == "editSub") {
                        DialogButton("删除", false, p.out, Color.White) {
                            val ti = dlgIndex
                            val si = dlgSubIndex
                            dlgShow = false
                            removeSub(ti, si)
                        }
                    }
                    DialogButton("保存", true, p.primaryFill, p.inkOnPrimary, ::confirmDialog)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubPanel(
    subs: List<SubCat>,
    paletteColor: Color,
    dragKind: String?,
    dragFrom: Int,
    subRects: MutableMap<Int, Rect>,
    cardOrigin: androidx.compose.runtime.MutableState<Offset>,
    onEdit: (Int) -> Unit,
    onAdd: () -> Unit,
    subShift: (Int) -> Pair<Float, Float>
) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = rpx(28), end = rpx(28), bottom = rpx(20))
            .clip(RoundedCornerShape(rpx(22)))
            .glass(alpha = 0.6f)
            .padding(horizontal = rpx(18), vertical = rpx(24))
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(rpx(10)),
            verticalArrangement = Arrangement.spacedBy(rpx(10))
        ) {
            for ((si, sub) in subs.withIndex()) {
                val isDragging = dragKind == "sub" && dragFrom == si
                val target = subShift(si)
                val subSpec: AnimationSpec<Float> = if (dragKind == null) tween(0) else tween(200)
                val animatedX by animateFloatAsState(if (isDragging) 0f else target.first, subSpec, label = "subx$si")
                val animatedY by animateFloatAsState(if (isDragging) 0f else target.second, subSpec, label = "suby$si")
                val dx = if (isDragging) target.first else animatedX
                val dy = if (isDragging) target.second else animatedY
                Column(
                    Modifier
                        .width(rpx(150))
                        .zIndex(if (isDragging) 20f else 0f)
                        .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                        .onGloballyPositioned { coords ->
                            val origin = cardOrigin.value
                            val pos = coords.positionInRoot()
                            subRects[si] = Rect(
                                pos.x - origin.x,
                                pos.y - origin.y,
                                pos.x - origin.x + coords.size.width,
                                pos.y - origin.y + coords.size.height
                            )
                        }
                        .clip(RoundedCornerShape(rpx(16)))
                        .background(if (isDragging) p.primaryWeak else Color.Transparent)
                        .clickable { onEdit(si) }
                        .padding(vertical = rpx(12)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(rpx(76))
                            .clip(CircleShape)
                            .background(tintColor(paletteColor, p.dark, 0.86f)),
                        contentAlignment = Alignment.Center
                    ) { AppIcon(sub.icon.ifEmpty { "grid" }, paletteColor, 19.dp, solid = true) }
                    Text(
                        sub.name,
                        color = p.sub,
                        fontSize = rsp(24),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = rpx(10)).widthIn(max = rpx(140))
                    )
                }
            }
            Column(
                Modifier.width(rpx(150)).clickable(onClick = onAdd).padding(vertical = rpx(12)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(rpx(76)).clip(CircleShape).background(p.fill),
                    contentAlignment = Alignment.Center
                ) { AppIcon("plus", p.sub, 22.dp) }
                Text("添加子类", color = p.sub, fontSize = rsp(24), modifier = Modifier.padding(top = rpx(10)))
            }
        }
    }
}

@Composable
private fun RowScope.DialogButton(
    text: String,
    primary: Boolean,
    bg: Color,
    fg: Color,
    onClick: () -> Unit
) {
    val p = LocalPalette.current
    Box(
        Modifier
            .weight(1f)
            .height(rpx(84))
            .clip(RoundedCornerShape(rpx(18)))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = rsp(28), fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium)
    }
}
