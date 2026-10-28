package com.muyian.debt.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.muyian.debt.data.AddDraft
import com.muyian.debt.state.AppState
import com.muyian.debt.data.BillWriteResult
import com.muyian.debt.data.Bills
import com.muyian.debt.data.Cats
import com.muyian.debt.data.Files
import com.muyian.debt.data.Category
import com.muyian.debt.state.Screen
import com.muyian.debt.data.evalExpr
import com.muyian.debt.data.fmtMoneyInput
import com.muyian.debt.data.pad2
import com.muyian.debt.data.todayStr
import com.muyian.debt.state.ActionSheetState
import com.muyian.debt.ui.components.DividerLine
import com.muyian.debt.ui.components.MascotFace
import com.muyian.debt.ui.components.Motion
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.pressable
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddScreen(app: AppState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf(app.addDraft ?: AddDraft()) }
    fun setDraft(value: AddDraft) {
        draft = value
        app.updateDraft(value)
    }

    val income = draft.isIncome
    val cats = app.categories.list(if (income) "income" else "expense")
    val safeTop = draft.selectedTopIndex.coerceIn(0, (cats.size - 1).coerceAtLeast(0))
    val selectedTop = cats.getOrNull(safeTop)
    val subList = selectedTop?.subs ?: emptyList()
    val subRowEndIndex = if (subList.isEmpty()) -1 else {
        val rowStart = (safeTop / 5) * 5
        minOf(cats.size - 1, rowStart + 4)
    }
    val leafName = draft.selectedSub.ifEmpty { selectedTop?.name ?: "其它" }
    val amount = evalExpr(draft.expr)
    val amountValid = !amount.isNaN() && amount > 0
    val inputMood = when {
        draft.expr.isEmpty() -> "idle"
        !amountValid -> "warn"
        income -> "success"
        else -> "idle"
    }
    val mascotLine = when {
        draft.isEdit -> if (income) "改这笔进账" else "改这笔支出"
        draft.expr.isEmpty() -> if (income) "进账多少？" else "这笔花在哪？"
        amount.isNaN() -> if (income) "进账怎么算？" else "支出怎么算？"
        amount <= 0 -> if (income) "进账得大于 0" else "支出得大于 0"
        income -> "进账啦"
        else -> "花哪儿了？"
    }
    val dateChip = if (draft.dateValue == todayStr()) "今天" else draft.dateValue.substring(5).replace('-', '/')

    var flashVisible by remember { mutableStateOf(false) }
    var flashText by remember { mutableStateOf("") }
    var flashMood by remember { mutableStateOf("success") }
    var flashJob by remember { mutableStateOf<Job?>(null) }
    var leaving by remember { mutableStateOf(false) }
    fun flash(text: String, mood: String = "success", duration: Long = 500) {
        flashJob?.cancel()
        flashText = text
        flashMood = mood
        flashVisible = true
        flashJob = scope.launch {
            delay(duration)
            flashVisible = false
        }
    }

    fun buildRemark(): String {
        val text = draft.remark.trim()
        return if (draft.imgPath.isNotEmpty()) {
            if (text.isNotEmpty()) "$text {{img:${draft.imgPath}}}" else "{{img:${draft.imgPath}}}"
        } else text
    }

    fun persist(): BillWriteResult {
        val value = evalExpr(draft.expr)
        if (value.isNaN() || value <= 0) return BillWriteResult.fail("先填个数")
        return if (draft.isEdit) {
            Bills.update(
                ctx, draft.editId, draft.dateValue, value, income,
                leafName, buildRemark(), draft.reimb, draft.starred
            )
        } else {
            Bills.save(
                ctx, draft.dateValue, value, income,
                leafName, buildRemark(), draft.reimb, draft.starred
            )
        }
    }

    fun save() {
        if (leaving) return
        if (!amountValid) {
            flash("先填个数", "empty", 900)
            return
        }
        val result = persist()
        if (!result.ok) {
            app.showToast(result.error.ifEmpty { "保存失败" })
            return
        }
        app.bumpBills()
        leaving = true
        flash(if (draft.isEdit) "改好了" else if (income) "进账了" else "记下了")
        scope.launch {
            delay(520)
            app.finishAdd()
        }
    }

    fun again() {
        if (!amountValid) {
            flash("先填个数", "empty", 900)
            return
        }
        val result = persist()
        if (!result.ok) {
            app.showToast(result.error.ifEmpty { "保存失败" })
            return
        }
        app.bumpBills()
        flash(if (income) "进账了" else "记下了")
        setDraft(draft.copy(expr = "", remark = ""))
    }

    fun onKey(key: String) {
        var e = draft.expr
        when (key) {
            "del" -> e = e.dropLast(1)
            "." -> {
                val last = e.split(Regex("[+-]")).last()
                if (last.contains('.')) return
                e = if (e.isEmpty() || e.last() == '+' || e.last() == '-') e + "0." else e + "."
            }
            "+", "-" -> {
                if (e.isEmpty()) {
                    if (key == "-") e = "-" else return
                } else if (e.last() == '+' || e.last() == '-') {
                    e = e.dropLast(1) + key
                } else {
                    e += key
                }
            }
            else -> {
                if (e.length > 15) return
                val last = e.split(Regex("[+-]")).last()
                e = if (last == "0") e.dropLast(1) + key else e + key
            }
        }
        setDraft(draft.copy(expr = e))
    }

    val keyboard = LocalSoftwareKeyboardController.current
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) {
            val path = Files.copyFileToFiles(ctx, f)
            if (path != null) {
                setDraft(draft.copy(imgPath = path))
                app.showToast("已添加图片")
            }
        }
        try { f?.delete() } catch (_: Exception) {
        }
        cameraFile = null
    }
    fun launchCamera() {
        try {
            val f = File(ctx.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            cameraFile = f
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)
            takePicture.launch(uri)
        } catch (_: Exception) {
            app.showToast("无法打开相机")
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else app.showToast("需要相机权限才能拍照")
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val path = Files.copyUriToFiles(ctx, uri)
            if (path != null) {
                setDraft(draft.copy(imgPath = path))
                app.showToast("已添加图片")
            } else app.showToast("读取图片失败")
        }
    }
    fun chooseImage() {
        app.actionSheet = ActionSheetState(
            listOf("拍照" to false, "从相册选择" to false)
        ) { index ->
            if (index == 0) {
                val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
            } else {
                gallery.launch("image/*")
            }
        }
    }
    fun onImageChip() {
        if (draft.imgPath.isEmpty()) {
            chooseImage()
        } else {
            app.actionSheet = ActionSheetState(
                listOf("预览图片" to false, "移除图片" to true, "更换图片" to false)
            ) { index ->
                when (index) {
                    0 -> app.previewImage = draft.imgPath
                    1 -> setDraft(draft.copy(imgPath = ""))
                    else -> chooseImage()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = rpx(20), end = rpx(20), top = rpx(12)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(rpx(72)).clip(CircleShape).pressable(p.primaryTint) { app.pop() },
                    contentAlignment = Alignment.Center
                ) { AppIcon("x", p.text, 22.dp) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val lineOffset by animateDpAsState(if (income) rpx(135) else rpx(5), label = "tab")
                    Box(Modifier.width(rpx(280)).height(rpx(66))) {
                        Row {
                            Box(
                                Modifier.width(rpx(140)).height(rpx(66)).clickable {
                                    if (!income) setDraft(
                                        draft.copy(
                                            isIncome = false,
                                            selectedTopIndex = 0,
                                            selectedSub = ""
                                        )
                                    )
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "支出",
                                    color = if (!income) p.primaryDeep else p.dim,
                                    fontSize = rsp(33),
                                    fontWeight = if (!income) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                            Box(
                                Modifier.width(rpx(140)).height(rpx(66)).clickable {
                                    if (income) setDraft(
                                        draft.copy(
                                            isIncome = true,
                                            selectedTopIndex = 0,
                                            selectedSub = ""
                                        )
                                    )
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "收入",
                                    color = if (income) p.inn else p.dim,
                                    fontSize = rsp(33),
                                    fontWeight = if (income) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = lineOffset)
                                .width(rpx(130))
                                .height(rpx(6))
                                .clip(RoundedCornerShape(rpx(4)))
                                .background(if (income) p.inn else p.primary)
                        )
                    }
                }
                Box(
                    Modifier.size(rpx(72)).clip(CircleShape).pressable(p.primaryTint) {
                        app.navigate(Screen.Categories)
                    },
                    contentAlignment = Alignment.Center
                ) { AppIcon("plus", p.text, 22.dp) }
            }
            DividerLine(color = p.line)
        }

        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = rpx(190))
            ) {
                val rows = (cats.size + 4) / 5
                for (row in 0 until rows) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = rpx(8))) {
                        for (col in 0..4) {
                            val index = row * 5 + col
                            if (index < cats.size) {
                                CatItem(
                                    app = app,
                                    category = cats[index],
                                    selected = index == safeTop,
                                    onClick = { setDraft(draft.copy(selectedTopIndex = index, selectedSub = "")) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = subList.isNotEmpty() && subRowEndIndex in (row * 5)..(row * 5 + 4),
                        enter = fadeIn(tween(170)) +
                            expandVertically(
                                tween(250, easing = Motion.Out),
                                expandFrom = Alignment.Top,
                                clip = false
                            ),
                        exit = fadeOut(tween(120, easing = Motion.InOut)) +
                            shrinkVertically(
                                tween(200, easing = Motion.InOut),
                                shrinkTowards = Alignment.Top,
                                clip = false
                            )
                    ) {
                        SubPanel(
                            app = app,
                            selectedIndex = safeTop,
                            subs = subList,
                            selectedSub = draft.selectedSub,
                            onSelect = { setDraft(draft.copy(selectedSub = it)) }
                        )
                    }
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = rpx(40)),
                verticalAlignment = Alignment.Bottom
            ) {
                Row(
                    Modifier
                        .padding(bottom = rpx(36), end = rpx(6))
                        .widthIn(max = rpx(380)),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(app.activeMascot.name, color = p.primaryDeep, fontSize = rsp(24), fontWeight = FontWeight.Bold)
                    Text("：$mascotLine", color = p.sub, fontSize = rsp(24), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                MascotFace(app, inputMood, 145)
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .glass(
                    shape = RoundedCornerShape(topStart = rpx(28), topEnd = rpx(28)),
                    alpha = if (p.dark) 0.62f else 0.52f
                )
                .navigationBarsPadding()
                // 底部 24 = 键盘最后一行的 10rpx 下边距 + 面板自身的 14rpx
                .padding(start = rpx(24), end = rpx(24), top = rpx(20), bottom = rpx(24))
        ) {
            val amountColor by animateColorAsState(
                if (income) p.inn else p.out,
                tween(220, easing = Motion.InOut),
                label = "amount-fg"
            )
            Row(
                Modifier.fillMaxWidth().padding(start = rpx(12), end = rpx(12), bottom = rpx(16)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(rpx(72)).clip(CircleShape).background(p.primaryWeak).border(1.dp, p.primaryTint, CircleShape),
                    contentAlignment = Alignment.Center
                ) { MascotFace(app, inputMood, 64, head = true) }
                Spacer(Modifier.width(rpx(14)))
                BasicTextField(
                    value = draft.remark,
                    onValueChange = { setDraft(draft.copy(remark = it)) },
                    singleLine = true,
                    textStyle = TextStyle(color = p.text, fontSize = rsp(27)),
                    cursorBrush = SolidColor(p.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (draft.remark.isEmpty()) {
                                Text(if (income) "这笔从哪来？" else "这笔花在哪？", color = p.dim, fontSize = rsp(27))
                            }
                            inner()
                        }
                    }
                )
                Spacer(Modifier.width(rpx(24)))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("¥", color = amountColor, fontSize = rsp(30), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = rpx(8), end = rpx(4)).graphicsLayer { alpha = 0.55f })
                    Text(
                        if (draft.expr.isEmpty()) "0.0" else draft.expr,
                        color = amountColor,
                        fontSize = rsp(54),
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
            DividerLine(color = p.line)
            Spacer(Modifier.height(rpx(16)))
            Row(horizontalArrangement = Arrangement.spacedBy(rpx(16)), verticalAlignment = Alignment.CenterVertically) {
                val reimbFg by animateColorAsState(
                    if (draft.reimb) Color.White else p.text,
                    tween(170, easing = Motion.InOut),
                    label = "reimb-fg"
                )
                val imgFg by animateColorAsState(
                    if (draft.imgPath.isNotEmpty()) Color.White else p.sub,
                    tween(170, easing = Motion.InOut),
                    label = "img-fg"
                )
                val starFg by animateColorAsState(
                    if (draft.starred) Color.White else p.sub,
                    tween(170, easing = Motion.InOut),
                    label = "star-fg"
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(rpx(14)))
                        .glass(alpha = 0.7f)
                        .clickable {
                            val parts = draft.dateValue.split("-")
                            app.showDatePicker(
                                "YMD",
                                parts.getOrNull(0)?.toIntOrNull() ?: 2026,
                                parts.getOrNull(1)?.toIntOrNull() ?: 1,
                                parts.getOrNull(2)?.toIntOrNull() ?: 1
                            ) { y, m, d ->
                                setDraft(draft.copy(dateValue = "$y-${pad2(m)}-${pad2(d)}"))
                            }
                        }
                        .padding(horizontal = rpx(24), vertical = rpx(11)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon("calendar", p.sub, 15.dp)
                    Spacer(Modifier.width(rpx(6)))
                    Text(dateChip, color = p.text, fontSize = rsp(26))
                }
                Chip(draft.reimb, { setDraft(draft.copy(reimb = !draft.reimb)) }) {
                    Text("报销", color = reimbFg, fontSize = rsp(26), fontWeight = if (draft.reimb) FontWeight.SemiBold else FontWeight.Normal)
                }
                Chip(draft.imgPath.isNotEmpty(), ::onImageChip) {
                    AppIcon("image", imgFg, 15.dp)
                    Spacer(Modifier.width(rpx(6)))
                    Text("图片", color = imgFg, fontSize = rsp(26))
                }
                Chip(draft.starred, { setDraft(draft.copy(starred = !draft.starred)) }) {
                    AppIcon("star", starFg, 15.dp, 2.2f)
                }
            }
            Spacer(Modifier.height(rpx(16)))
            Column(verticalArrangement = Arrangement.spacedBy(rpx(10))) {
                val rows = listOf(
                    listOf("1", "2", "3", "del"),
                    listOf("4", "5", "6", "-"),
                    listOf("7", "8", "9", "+"),
                    listOf("again", "0", ".", "save")
                )
                for (row in rows) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (key in row) {
                            KeyButton(
                                key = key,
                                modifier = Modifier.width(rpx(162)),
                                onKey = {
                                    when (key) {
                                        "again" -> again()
                                        "save" -> save()
                                        else -> onKey(key)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = flashVisible,
        enter = fadeIn(tween(150)) + scaleIn(tween(240, easing = Motion.Out), initialScale = 0.88f),
        exit = fadeOut(tween(180, easing = Motion.InOut)) +
            scaleOut(tween(180, easing = Motion.InOut), targetScale = 0.94f),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .offset(y = -rpx(80))
                    .clip(RoundedCornerShape(rpx(28)))
                    .background(if (p.dark) p.card.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f))
                    .border(1.dp, p.line, RoundedCornerShape(rpx(28)))
                    .padding(start = rpx(40), end = rpx(40), top = rpx(28), bottom = rpx(24)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MascotFace(app, flashMood, 112, head = true)
                Text(flashText, color = p.text, fontSize = rsp(28), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = rpx(10)))
            }
        }
    }
}

@Composable
private fun CatItem(app: AppState, category: Category, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val bubble by animateColorAsState(
        if (selected) p.primaryTint else Color.Transparent,
        tween(180, easing = Motion.InOut),
        label = "cat-bubble"
    )
    val scale by animateFloatAsState(
        if (selected) 1.08f else 1f,
        tween(230, easing = Motion.Out),
        label = "cat-scale"
    )
    val fg by animateColorAsState(
        if (selected) p.primaryDeep else p.sub,
        tween(180, easing = Motion.InOut),
        label = "cat-fg"
    )
    val nameFg by animateColorAsState(
        if (selected) p.primaryDeep else p.sub,
        tween(180, easing = Motion.InOut),
        label = "cat-name"
    )
    Column(
        modifier.padding(vertical = rpx(14)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(rpx(88))
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(bubble),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                category.icon.ifEmpty { "grid" },
                fg,
                25.dp,
                solid = true
            )
        }
        Spacer(Modifier.height(rpx(10)))
        Text(
            category.name,
            color = nameFg,
            fontSize = rsp(24),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = rpx(130))
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubPanel(
    app: AppState,
    selectedIndex: Int,
    subs: List<com.muyian.debt.data.SubCat>,
    selectedSub: String,
    onSelect: (String) -> Unit
) {
    val p = LocalPalette.current
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            // 原版 .sub-row：左右 8rpx 外边距 + 宫格自身 8rpx 内边距 = 距屏幕 16rpx；上 4、下 10
            .padding(start = rpx(16), end = rpx(16), top = rpx(4), bottom = rpx(10))
    ) {
        // 箭头要对准父类所在列的中心：宫格内容宽 = 面板块宽 + 左右各 8rpx，左侧再扣掉宫格的 8rpx
        val caretCenter = (maxWidth + rpx(16)) * ((selectedIndex % 5 + 0.5f) / 5f) - rpx(8)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(rpx(20)))
                .background(p.primaryWeak)
                .border(1.dp, p.primaryTint, RoundedCornerShape(rpx(20)))
                // 原版：面板 10/16/4；chip 自带 8rpx 上下边距，右侧还有 16rpx 右边距
                .padding(start = rpx(16), end = rpx(32), top = rpx(18), bottom = rpx(12))
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(rpx(16)),
                verticalArrangement = Arrangement.spacedBy(rpx(16))
            ) {
                for (sub in subs) {
                    val active = sub.name == selectedSub
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(rpx(32)))
                            .background(if (active) p.primaryFill else p.fill)
                            .clickable { onSelect(sub.name) }
                            .padding(horizontal = rpx(26), vertical = rpx(12)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(sub.icon.ifEmpty { "grid" }, if (active) Color.White else p.sub, 15.dp, solid = true)
                        Spacer(Modifier.width(rpx(8)))
                        Text(
                            sub.name,
                            color = if (active) Color.White else p.sub,
                            fontSize = rsp(25),
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
        }
        SubCaret(p.primaryWeak, p.primaryTint, caretCenter)
    }
}

/**
 * 子类面板上沿的指示三角：与原版 .sub-row::before 一致，是 18rpx 方块绕中心旋转 45°，
 * 只画上/左两条边线，尖端 3rpx 圆角；可见部分是 25.5rpx 宽、12.7rpx 高的三角形。
 */
@Composable
private fun SubCaret(fill: Color, border: Color, centerX: Dp) {
    val side = rpx(18)
    val radius = rpx(3)
    val strokeWidth = rpx(1)
    // 28rpx 画布刚好容下旋转后的 25.5rpx 对角线，避免绘制被画布边界裁掉
    Canvas(
        Modifier
            .offset(x = centerX - rpx(14), y = -rpx(14))
            .size(rpx(28))
    ) {
        val s = side.toPx()
        val r = radius.toPx()
        val left = (size.width - s) / 2f
        val top = (size.height - s) / 2f
        val fillPath = Path().apply {
            moveTo(left + r, top)
            lineTo(left + s, top)
            lineTo(left + s, top + s)
            lineTo(left, top + s)
            lineTo(left, top + r)
            quadraticBezierTo(left, top, left + r, top)
            close()
        }
        val borderPath = Path().apply {
            moveTo(left + s, top)
            lineTo(left + r, top)
            quadraticBezierTo(left, top, left, top + r)
            lineTo(left, top + s)
        }
        rotate(45f) {
            drawPath(fillPath, fill)
            drawPath(borderPath, border, style = Stroke(width = strokeWidth.toPx()))
        }
    }
}

@Composable
private fun Chip(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val bg by animateColorAsState(
        if (active) p.primaryFill else p.card.copy(alpha = if (p.dark) 0.72f else 0.7f),
        tween(180, easing = Motion.InOut),
        label = "chip-bg"
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(rpx(14)))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = rpx(24), vertical = rpx(11)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

@Composable
private fun KeyButton(key: String, modifier: Modifier = Modifier, onKey: () -> Unit) {
    val p = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val isFn = key == "del" || key == "-" || key == "+"
    val isAgain = key == "again"
    val isSave = key == "save"
    val bg = when {
        isSave -> p.primaryFill
        isAgain -> p.primaryWeak
        isFn -> p.keyFnBg
        else -> p.keyBg
    }
    val edge = when {
        isSave -> p.primaryPress
        isAgain -> p.primaryTint
        isFn -> p.keyFnEdge
        else -> p.keyEdge
    }
    val textColor = when {
        isSave -> p.inkOnPrimary
        isAgain -> p.primaryDeep
        isFn -> p.keyFnFg
        else -> p.keyFg
    }
    val pressedBg = when {
        isSave -> p.primaryPress
        isAgain -> bg
        isFn -> p.keyFnEdge
        else -> p.keyPress
    }
    val edgeThickness = if (pressed) rpx(1) else rpx(3)
    val offset by animateDpAsState(if (pressed) rpx(2) else 0.dp, label = "key")
    val shape = RoundedCornerShape(rpx(18))
    Box(
        modifier
            .height(rpx(86))
            .clickable(interactionSource = source, indication = null, onClick = onKey)
    ) {
        // 底边对应 CSS 的 box-shadow 0 3rpx 0：按下时键帽下移 2rpx、底边只剩 1rpx
        Box(
            Modifier
                .fillMaxWidth()
                .height(rpx(86))
                .offset(y = offset + edgeThickness)
                .clip(shape)
                .background(edge)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(rpx(86))
                .offset(y = offset)
                .clip(shape)
                .background(if (pressed) pressedBg else bg),
            contentAlignment = Alignment.Center
        ) {
            when (key) {
                "del" -> AppIcon("delete", p.sub, 22.dp)
                "again" -> Text("再记", color = textColor, fontSize = rsp(29), fontWeight = FontWeight.SemiBold)
                "save" -> Text("保存", color = textColor, fontSize = rsp(31), fontWeight = FontWeight.Bold, letterSpacing = rsp(2))
                "-" -> Text("−", color = textColor, fontSize = rsp(38), fontWeight = FontWeight.SemiBold)
                else -> Text(key, color = textColor, fontSize = rsp(38), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
