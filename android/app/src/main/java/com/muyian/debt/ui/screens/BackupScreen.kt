package com.muyian.debt.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyian.debt.state.AppState
import com.muyian.debt.data.Backup
import com.muyian.debt.data.BackupInfo
import com.muyian.debt.data.BackupParseResult
import com.muyian.debt.data.Bills
import com.muyian.debt.data.Dates
import com.muyian.debt.data.FilePick
import com.muyian.debt.data.OpenResult
import com.muyian.debt.data.PickKind
import com.muyian.debt.data.PickResult
import com.muyian.debt.data.ReadResult
import com.muyian.debt.data.RestoreResult
import com.muyian.debt.data.SaveResult
import com.muyian.debt.state.Screen
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
fun BackupScreen(app: AppState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf("idle") }
    var exporting by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var restoredCount by remember { mutableIntStateOf(0) }
    var backup by remember { mutableStateOf(BackupInfo()) }
    var missingCatCount by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf(RestoreResult(0, 0, 0, false)) }

    fun onExport() {
        if (exporting || importing) return
        val all = Bills.all(ctx)
        if (all.isEmpty()) {
            app.showToast("还没有账单可以导出")
            return
        }
        val name = "孽账备份_${Dates.stamp()}.json"
        when (val r = FilePick.openExport(ctx, Backup.payload(all, app.categories).toString(), name)) {
            OpenResult.Ok -> exporting = true
            is OpenResult.Error -> app.showToast(r.message)
        }
    }

    LaunchedEffect(exporting) {
        if (!exporting) return@LaunchedEffect
        when (val r = FilePick.awaitSave(ctx)) {
            is SaveResult.Saved -> {
                exporting = false
                app.showToast("已导出：${r.name.ifEmpty { "备份文件" }}")
            }
            SaveResult.Cancelled -> { exporting = false; app.showToast("已取消导出") }
            is SaveResult.Error -> { exporting = false; app.showToast("导出失败：${r.message}") }
            SaveResult.Timeout -> { exporting = false; app.showToast("保存超时，请重试") }
        }
    }

    fun onPickBackup() {
        if (picking || importing || exporting) return
        when (val r = FilePick.open(ctx, PickKind.BACKUP)) {
            OpenResult.Ok -> picking = true
            is OpenResult.Error -> app.showToast(r.message)
        }
    }

    fun handlePicked(path: String, name: String) {
        val text = when (val r = FilePick.readBackup(ctx, path)) {
            is ReadResult.Ok -> r.text
            is ReadResult.Error -> {
                app.showToast(r.message)
                return
            }
        }
        when (val parsed = Backup.parse(text, name)) {
            is BackupParseResult.Error -> app.showToast(parsed.message)
            is BackupParseResult.Ok -> {
                backup = parsed.info
                missingCatCount = Backup.missingCategories(backup.categories, app.categories).size
                step = "preview"
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

    fun startRestore() {
        if (importing || backup.bills.isEmpty()) return
        importing = true
        restoredCount = 0
        val merge = Backup.mergeMissing(
            app.categories,
            Backup.missingCategories(backup.categories, app.categories)
        )
        app.saveCategories(merge.data)
        scope.launch {
            val outcome = Backup.commit(ctx, backup.bills) { restoredCount = it }
            importing = false
            result = RestoreResult(outcome.inserted, outcome.skipped, merge.added, outcome.failed)
            step = "done"
            app.bumpBills()
            if (!outcome.failed) app.showToast("已恢复 ${outcome.inserted} 条")
        }
    }

    fun reset() {
        step = "idle"
        backup = BackupInfo()
        missingCatCount = 0
        result = RestoreResult(0, 0, 0, false)
    }

    val stats = remember(backup) { Backup.stats(backup.bills) }


    Column(Modifier.fillMaxSize().background(p.bg)) {
        TopBar(title = "备份·恢复", onBack = { app.pop() })

        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(220, easing = Motion.Out)) +
                    slideInVertically(tween(260, easing = Motion.Out), initialOffsetY = { it / 14 }))
                    .togetherWith(fadeOut(tween(150, easing = Motion.InOut)))
            },
            label = "backup-step"
        ) { current ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .pageBottomPadding()
        ) {
            if (current == "idle") {
                GroupTitle("导出备份")
                BackupCard(
                    icon = "download",
                    iconColor = p.blue,
                    title = "导出全部账单",
                    desc = "生成一个 JSON 备份文件，包含全部账单和分类。保存位置由系统选择（下载目录、网盘等均可）。备注里的图片不会打包进备份。"
                ) {
                    PrimaryButton(
                        text = if (exporting) "保存中…" else "导出备份文件",
                        onClick = ::onExport,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !exporting && !importing
                    )
                }

                GroupTitle("恢复备份")
                BackupCard(
                    icon = "rotate-ccw",
                    iconColor = p.mint,
                    title = "从备份文件恢复",
                    desc = "选择之前导出的 JSON 备份。合并恢复：已存在的账单（同 id 或同单号）自动跳过，不会覆盖现有数据；备份里缺失的分类会自动补全。"
                ) {
                    PrimaryButton(
                        text = if (picking) "等待选择文件…" else "选择备份文件",
                        onClick = ::onPickBackup,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !picking && !importing && !exporting
                    )
                    if (picking) {
                        Text(
                            "已打开系统文件选择器，选完自动回来；取消或超时（90 秒）自动放弃",
                            color = p.dim,
                            fontSize = rsp(22),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = rpx(16))
                        )
                    }
                }

                GroupTitle("从钱迹导入")
                BackupCard(
                    icon = "file-text",
                    iconColor = p.blue,
                    title = "导入钱迹历史账单",
                    desc = "选择钱迹 App 导出的 Excel，把历史账单批量记进来。只在手机本地解析，按单号去重，重复导入不会重复入账。"
                ) {
                    PrimaryButton(
                        text = "导入钱迹账单",
                        onClick = { app.navigate(Screen.Import) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (current == "preview") {
                GroupTitle("备份内容")
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
                        Text(backup.name.ifEmpty { "孽账备份.json" }, color = p.sub, fontSize = rsp(25), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(rpx(20)))
                    Row(Modifier.fillMaxWidth()) {
                        StatCell(stats.total.toString(), "账单", p.text)
                        StatCell(stats.expense.toString(), "支出", p.out)
                        StatCell(stats.income.toString(), "收入", p.inn)
                        StatCell(missingCatCount.toString(), "补全分类", p.text)
                    }
                    Spacer(Modifier.height(rpx(14)))
                    Text("${stats.minDay} ~ ${stats.maxDay}", color = p.sub, fontSize = rsp(23), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (backup.exportedAt > 0) {
                        Text("导出时间：${Dates.format(backup.exportedAt)}", color = p.sub, fontSize = rsp(23), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = rpx(8)))
                    }
                    Spacer(Modifier.height(rpx(16)))
                    Box(Modifier.fillMaxWidth().height(rpx(1)).background(p.lineSoft))
                    Spacer(Modifier.height(rpx(14)))
                    Text(
                        "恢复为合并模式：同 id 或同单号的账单会跳过，重复恢复不会产生重复数据",
                        color = p.dim,
                        fontSize = rsp(22),
                        lineHeight = rsp(36),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = rpx(24), vertical = rpx(36))) {
                    GhostButton("重选文件", ::reset, Modifier.width(rpx(200)))
                    Spacer(Modifier.width(rpx(20)))
                    PrimaryButton(
                        text = if (importing) "恢复中 $restoredCount/${backup.bills.size}" else "开始恢复 ${backup.bills.size} 条",
                        onClick = ::startRestore,
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
                        Text(if (result.failed) "恢复中断" else "恢复完成", color = p.text, fontSize = rsp(31), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(rpx(16)))
                    Text(
                        "新增 ${result.inserted} 条账单" + if (result.skipped > 0) "，跳过 ${result.skipped} 条（已存在或无效）" else "",
                        color = p.sub,
                        fontSize = rsp(26),
                        lineHeight = rsp(44)
                    )
                    if (result.catAdded > 0) {
                        Text("补全了 ${result.catAdded} 个分类", color = p.sub, fontSize = rsp(26), modifier = Modifier.padding(top = rpx(6)))
                    }
                    if (result.failed) {
                        Text(
                            "中途出错，已恢复的部分保留；可重新选择同一备份再次恢复，已存在的会按 id/单号自动跳过",
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

@Composable
private fun BackupCard(
    icon: String,
    iconColor: Color,
    title: String,
    desc: String,
    content: @Composable () -> Unit
) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(24), vertical = rpx(4))
            .glass(shape = RoundedCornerShape(rpx(20)))
            .padding(horizontal = rpx(28), vertical = rpx(26))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(rpx(48)).clip(RoundedCornerShape(rpx(15))).background(iconColor),
                contentAlignment = Alignment.Center
            ) { Icon(icon, Color.White, rpx(18)) }
            Spacer(Modifier.width(rpx(14)))
            Text(title, color = p.text, fontSize = rsp(30), fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(rpx(14)))
        Text(desc, color = p.sub, fontSize = rsp(24), lineHeight = rsp(43))
        Spacer(Modifier.height(rpx(22)))
        content()
    }
}
