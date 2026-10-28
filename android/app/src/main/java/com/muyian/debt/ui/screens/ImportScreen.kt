package com.muyian.debt.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyian.debt.state.AppState
import com.muyian.debt.data.CommitOutcome
import com.muyian.debt.data.FilePick
import com.muyian.debt.data.ImportFlow
import com.muyian.debt.data.ImportLoadResult
import com.muyian.debt.data.ImportSummary
import com.muyian.debt.data.OpenResult
import com.muyian.debt.data.PickKind
import com.muyian.debt.data.PickResult
import com.muyian.debt.data.Qianji
import com.muyian.debt.data.fmtNum
import com.muyian.debt.ui.components.GhostButton
import com.muyian.debt.ui.components.GroupTitle
import com.muyian.debt.ui.components.Motion
import com.muyian.debt.ui.components.PrimaryButton
import com.muyian.debt.ui.components.StatCell
import com.muyian.debt.ui.components.TopBar
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.pageBottomPadding
import com.muyian.debt.ui.components.pressable
import com.muyian.debt.ui.icons.AppIcon as Icon
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import kotlinx.coroutines.launch

@Composable
fun ImportScreen(app: AppState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf("pick") }
    var picking by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var importedCount by remember { mutableIntStateOf(0) }
    var fileName by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf(ImportSummary()) }
    var mappings by remember { mutableStateOf<List<Qianji.MappingRow>>(emptyList()) }
    var rows by remember { mutableStateOf<List<Qianji.SheetRow>>(emptyList()) }
    var result by remember { mutableStateOf(CommitOutcome()) }

    val leafColumns = remember(app.categories) { Qianji.buildLeafColumns(app.categories) }

    fun handlePicked(path: String, name: String) {
        when (val loaded = ImportFlow.load(ctx, path, name, leafColumns)) {
            is ImportLoadResult.Error -> app.showToast(loaded.message)
            is ImportLoadResult.Ok -> {
                summary = loaded.summary
                mappings = loaded.mappings
                rows = loaded.rows
                fileName = loaded.fileName
                step = "review"
            }
        }
    }

    LaunchedEffect(picking) {
        if (!picking) return@LaunchedEffect
        when (val r = FilePick.awaitPick(ctx)) {
            is PickResult.Picked -> {
                picking = false
                handlePicked(r.path, r.name)
            }
            PickResult.Cancelled -> { picking = false; app.showToast("已取消选择") }
            is PickResult.Error -> { picking = false; app.showToast("选择文件失败：${r.message}") }
            PickResult.Timeout -> { picking = false; app.showToast("等待超时，请重新选择") }
        }
    }

    fun onPick() {
        if (picking || importing) return
        when (val r = FilePick.open(ctx, PickKind.QIANJI)) {
            OpenResult.Ok -> picking = true
            is OpenResult.Error -> app.showToast(r.message)
        }
    }

    fun startImport() {
        if (importing || rows.isEmpty()) return
        importing = true
        importedCount = 0
        scope.launch {
            val outcome = ImportFlow.commit(ctx, ImportFlow.billsJson(rows, mappings)) { importedCount = it }
            importing = false
            if (outcome.error == "没有可导入的账单") {
                app.showToast(outcome.error)
                return@launch
            }
            result = outcome
            step = "done"
            app.bumpBills()
            if (!outcome.failed) app.showToast("已导入 ${outcome.inserted} 条")
        }
    }

    fun reset() {
        step = "pick"
        summary = ImportSummary()
        mappings = emptyList()
        rows = emptyList()
        result = CommitOutcome()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "导入账单", onBack = { app.pop() })

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(220, easing = Motion.Out)) +
                    slideInVertically(tween(260, easing = Motion.Out), initialOffsetY = { it / 14 }))
                    .togetherWith(fadeOut(tween(150, easing = Motion.InOut)))
            },
            label = "import-step"
        ) { current ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .pageBottomPadding()
        ) {
            if (current == "pick") {
                GroupTitle("从钱迹导入")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(24), vertical = rpx(4))
                        .glass(shape = RoundedCornerShape(rpx(20)))
                        .padding(horizontal = rpx(28), vertical = rpx(26))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(rpx(48)).clip(RoundedCornerShape(rpx(15))).background(p.blue),
                            contentAlignment = Alignment.Center
                        ) { Icon("file-text", Color.White, rpx(18)) }
                        Spacer(Modifier.width(rpx(14)))
                        Text("导入钱迹历史账单", color = p.text, fontSize = rsp(30), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(rpx(16)))
                    for (line in listOf(
                        "1. 在钱迹 App「我的 → 数据导出」里导出 Excel 账单，把文件保存到手机（微信/QQ 传给自己后「用其他应用打开 → 保存」即可）",
                        "2. 点下方按钮，在系统文件选择器里选中它",
                        "3. 确认分类映射后开始导入，可随时取消"
                    )) {
                        Text(line, color = p.sub, fontSize = rsp(24), lineHeight = rsp(43))
                    }
                    Spacer(Modifier.height(rpx(14)))
                    Box(Modifier.fillMaxWidth().height(rpx(1)).background(p.lineSoft))
                    Spacer(Modifier.height(rpx(14)))
                    Text(
                        "全程只在手机本地解析，不联网；按钱迹单号去重，重复导入不会重复入账。同一笔如果之前已手动或自动记过，单号不同不会自动合并，导入后可在账单里删掉多余的。",
                        color = p.dim,
                        fontSize = rsp(22),
                        lineHeight = rsp(37)
                    )
                }
                Spacer(Modifier.height(rpx(32)))
                PrimaryButton(
                    text = if (picking) "等待选择文件…" else "选择钱迹 Excel 文件",
                    onClick = ::onPick,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = rpx(24)),
                    enabled = !picking,
                    icon = if (picking) null else "download"
                )
                if (picking) {
                    Text(
                        "已打开系统文件选择器，选完自动回来；取消或超时（90 秒）自动放弃",
                        color = p.dim,
                        fontSize = rsp(22),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = rpx(40), vertical = rpx(18))
                    )
                }
            }

            if (current == "review") {
                GroupTitle("解析结果")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(24), vertical = rpx(4))
                        .glass(shape = RoundedCornerShape(rpx(20)))
                        .padding(horizontal = rpx(28), vertical = rpx(24))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(rpx(40)).clip(RoundedCornerShape(rpx(12))).background(p.blue),
                            contentAlignment = Alignment.Center
                        ) { Icon("file-text", Color.White, rpx(15)) }
                        Spacer(Modifier.width(rpx(14)))
                        Text(fileName, color = p.sub, fontSize = rsp(25), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(rpx(20)))
                    Row(Modifier.fillMaxWidth()) {
                        StatCell(rows.size.toString(), "账单", p.text)
                        StatCell(summary.expense.toString(), "支出", p.out)
                        StatCell(summary.income.toString(), "收入", p.inn)
                        StatCell(summary.skipped.toString(), "跳过", p.text)
                    }
                    Spacer(Modifier.height(rpx(18)))
                    Text("${summary.minDay} ~ ${summary.maxDay}", color = p.sub, fontSize = rsp(23), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (summary.dupInDb > 0) {
                        Text(
                            "库里已有 ${summary.dupInDb} 条同单号账单，导入时自动跳过",
                            color = p.outDeep,
                            fontSize = rsp(22),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = rpx(8))
                        )
                    }
                }

                GroupTitle("分类映射")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(24), vertical = rpx(4))
                        .glass(shape = RoundedCornerShape(rpx(20)))
                        .padding(horizontal = rpx(28), vertical = rpx(4))
                ) {
                    for ((idx, m) in mappings.withIndex()) {
                        if (idx > 0) Box(Modifier.fillMaxWidth().height(rpx(1)).background(p.lineSoft))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressable(p.fill) {
                                    app.showPicker(
                                        "「${m.label}」记入",
                                        leafColumns,
                                        m.target
                                    ) { value ->
                                        mappings = mappings.map { if (it.key == m.key) it.copy(target = value) else it }
                                    }
                                }
                                .padding(vertical = rpx(22)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.label, color = p.text, fontSize = rsp(28), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${m.count} 条", color = p.dim, fontSize = rsp(21), modifier = Modifier.padding(top = rpx(4)))
                            }
                            Text(m.target, color = p.primaryDeep, fontSize = rsp(26), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(rpx(8)))
                            Icon("chevron-down", p.sub, rpx(14))
                        }
                    }
                    if (summary.income > 0) {
                        Box(Modifier.fillMaxWidth().height(rpx(1)).background(p.lineSoft))
                        Text(
                            "另有收入 ${summary.income} 条，按本应用口径统一记为「收入」",
                            color = p.dim,
                            fontSize = rsp(22),
                            modifier = Modifier.padding(vertical = rpx(18))
                        )
                    }
                }

                GroupTitle("预览（最近 ${minOf(8, rows.size)} 条）")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(24), vertical = rpx(4))
                        .glass(shape = RoundedCornerShape(rpx(20)))
                        .padding(horizontal = rpx(28), vertical = rpx(4))
                ) {
                    val map = mappings.associate { it.key to it.target }
                    for ((idx, r) in rows.take(8).withIndex()) {
                        if (idx > 0) Box(Modifier.fillMaxWidth().height(rpx(1)).background(p.lineSoft))
                        val income = r.type == "收入"
                        Row(Modifier.fillMaxWidth().padding(vertical = rpx(20)), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.day.substring(5).replace("-", "/"), color = p.dim, fontSize = rsp(23), modifier = Modifier.width(rpx(110)))
                            Text(
                                if (income) "收入" else (map["${r.type}|${r.cat}|${r.sub}"] ?: r.cat.ifEmpty { "其它" }),
                                color = p.text,
                                fontSize = rsp(26),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                (if (income) "+" else "-") + fmtNum(r.amount.toDoubleOrNull() ?: 0.0),
                                color = if (income) p.inn else p.out,
                                fontSize = rsp(27),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = rpx(24), vertical = rpx(36))) {
                    GhostButton("重选文件", ::reset, Modifier.width(rpx(200)))
                    Spacer(Modifier.width(rpx(20)))
                    PrimaryButton(
                        text = if (importing) "导入中 $importedCount/${rows.size}" else "开始导入 ${rows.size} 条",
                        onClick = ::startImport,
                        modifier = Modifier.weight(1f),
                        enabled = !importing
                    )
                }
            }

            if (current == "done") {
                Spacer(Modifier.height(rpx(32)))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(24))
                        .glass(shape = RoundedCornerShape(rpx(20)))
                        .padding(horizontal = rpx(28), vertical = rpx(26))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(rpx(48)).clip(RoundedCornerShape(rpx(15))).background(p.mint),
                            contentAlignment = Alignment.Center
                        ) { Icon("check", Color.White, rpx(18)) }
                        Spacer(Modifier.width(rpx(14)))
                        Text(if (result.failed) "导入中断" else "导入完成", color = p.text, fontSize = rsp(31), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(rpx(16)))
                    Text(
                        "新增 ${result.inserted} 条账单" + if (result.skipped > 0) "，跳过重复 ${result.skipped} 条" else "",
                        color = p.sub,
                        fontSize = rsp(26),
                        lineHeight = rsp(44)
                    )
                    if (result.failed) {
                        Text(
                            "中途出错，已导入的部分保留；可重选文件再次导入，已入账的会按单号自动跳过",
                            color = p.outDeep,
                            fontSize = rsp(23),
                            lineHeight = rsp(37),
                            modifier = Modifier.padding(top = rpx(10))
                        )
                    }
                }
                PrimaryButton(
                    text = "完成",
                    onClick = { app.pop() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = rpx(24), vertical = rpx(36))
                )
            }
        }
        }
    }
}
