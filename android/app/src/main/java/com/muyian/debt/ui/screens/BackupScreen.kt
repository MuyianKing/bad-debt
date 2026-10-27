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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.muyian.debt.data.AppState
import com.muyian.debt.data.Bill
import com.muyian.debt.data.Bills
import com.muyian.debt.data.Cats
import com.muyian.debt.data.pad2
import com.muyian.debt.ui.components.GhostButton
import com.muyian.debt.ui.components.GroupTitle
import com.muyian.debt.ui.components.Motion
import com.muyian.debt.ui.components.PrimaryButton
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.pressable
import com.muyian.debt.ui.icons.AppIcon as Icon
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import muyian.bill.listener.MuyianBillCore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

private data class BackupInfo(
    val name: String = "",
    val exportedAt: Long = 0,
    val bills: List<JSONObject> = emptyList(),
    val categories: JSONObject = JSONObject()
)

private data class MissingCat(
    val type: String,
    val topName: String,
    val topIcon: String,
    val subName: String,
    val subIcon: String
)

private data class BackupResult(
    val inserted: Int = 0,
    val skipped: Int = 0,
    val catAdded: Int = 0,
    val failed: Boolean = false
)

private fun billJson(b: Bill): JSONObject = JSONObject()
    .put("id", b.id)
    .put("year", b.year)
    .put("month", b.month)
    .put("day", b.day)
    .put("clock", b.clock)
    .put("money", b.money)
    .put("bankName", b.bankName)
    .put("costType", b.costType)
    .put("orderRemark", b.orderRemark)
    .put("source", b.source)
    .put("sourcePkg", b.sourcePkg)
    .put("createdAt", b.createdAt)
    .put("reimb", b.reimb)
    .put("starred", b.starred)
    .put("orderNo", b.orderNo)

private fun sanitizeBills(arr: JSONArray): List<JSONObject> {
    val out = ArrayList<JSONObject>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val money = o.optDouble("money", 0.0)
        if (money == 0.0 || money.isNaN()) continue
        val y = o.optInt("year")
        val mo = o.optInt("month")
        val d = o.optInt("day")
        if (y < 1970 || mo !in 1..12 || d !in 1..31) continue
        out.add(
            JSONObject()
                .put("id", o.optLong("id", 0L))
                .put("year", y)
                .put("month", mo)
                .put("day", d)
                .put("clock", o.optString("clock", ""))
                .put("money", money)
                .put("bankName", o.optString("bankName", ""))
                .put("costType", o.optString("costType", if (money > 0) "收入" else "消费"))
                .put("orderRemark", o.optString("orderRemark", ""))
                .put("source", o.optString("source", "manual"))
                .put("sourcePkg", o.optString("sourcePkg", ""))
                .put("createdAt", o.optLong("createdAt", 0L))
                .put("reimb", o.optInt("reimb", 0))
                .put("starred", o.optInt("starred", 0))
                .put("orderNo", o.optString("orderNo", ""))
        )
    }
    return out
}

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
    var result by remember { mutableStateOf(BackupResult()) }

    fun stamp(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}${pad2(c.get(Calendar.MONTH) + 1)}${pad2(c.get(Calendar.DAY_OF_MONTH))}_" +
            "${pad2(c.get(Calendar.HOUR_OF_DAY))}${pad2(c.get(Calendar.MINUTE))}"
    }

    fun formatTs(ts: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return "${c.get(Calendar.YEAR)}-${pad2(c.get(Calendar.MONTH) + 1)}-${pad2(c.get(Calendar.DAY_OF_MONTH))} " +
            "${pad2(c.get(Calendar.HOUR_OF_DAY))}:${pad2(c.get(Calendar.MINUTE))}"
    }

    fun onExport() {
        if (exporting || importing) return
        val all = Bills.all(ctx)
        if (all.isEmpty()) {
            app.showToast("还没有账单可以导出")
            return
        }
        val billsArr = JSONArray()
        for (b in all) billsArr.put(billJson(b))
        val payload = JSONObject()
            .put("app", "muyian")
            .put("kind", "backup")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("categories", JSONObject(Cats.toJson(app.categories)))
            .put("bills", billsArr)
        val name = "孽账备份_${stamp()}.json"
        val resp = try {
            JSONObject(MuyianBillCore.openBackupExport(ctx, payload.toString(), name))
        } catch (_: Exception) {
            JSONObject()
        }
        if (!resp.optBoolean("success")) {
            app.showToast("无法打开保存界面：${resp.optString("error", "未知原因")}")
            return
        }
        exporting = true
    }

    LaunchedEffect(exporting) {
        if (!exporting) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (true) {
            delay(400)
            val st = try { JSONObject(MuyianBillCore.getBackupSaveState(ctx)) } catch (_: Exception) { JSONObject() }
            when (st.optString("state")) {
                "saved" -> { exporting = false; app.showToast("已导出：${st.optString("name", "备份文件")}"); return@LaunchedEffect }
                "cancelled" -> { exporting = false; app.showToast("已取消导出"); return@LaunchedEffect }
                "error" -> { exporting = false; app.showToast("导出失败：${st.optString("error", "未知原因")}"); return@LaunchedEffect }
            }
            if (System.currentTimeMillis() - start > 120_000) {
                exporting = false
                app.showToast("保存超时，请重试")
                return@LaunchedEffect
            }
        }
    }

    fun onPickBackup() {
        if (picking || importing || exporting) return
        val resp = try { JSONObject(MuyianBillCore.openBackupPicker(ctx)) } catch (_: Exception) { JSONObject() }
        if (!resp.optBoolean("success")) {
            app.showToast("无法打开文件选择器：${resp.optString("error", "未知原因")}")
            return
        }
        picking = true
    }

    fun collectMissingCategories(cats: JSONObject): List<MissingCat> {
        val missing = ArrayList<MissingCat>()
        for (type in listOf("expense", "income")) {
            val src = cats.optJSONArray(type) ?: continue
            val dst = app.categories.list(type)
            for (i in 0 until src.length()) {
                val top = src.optJSONObject(i) ?: continue
                val topName = top.optString("name", "")
                if (topName.isEmpty()) continue
                val topIcon = top.optString("icon", "grid").ifEmpty { "grid" }
                val hit = dst.firstOrNull { it.name == topName }
                if (hit == null) {
                    missing.add(MissingCat(type, topName, topIcon, "", ""))
                    val subs = top.optJSONArray("subs")
                    if (subs != null) {
                        for (j in 0 until subs.length()) {
                            val s = subs.optJSONObject(j) ?: continue
                            val sn = s.optString("name", "")
                            if (sn.isNotEmpty()) {
                                missing.add(MissingCat(type, topName, topIcon, sn, s.optString("icon", "grid").ifEmpty { "grid" }))
                            }
                        }
                    }
                } else {
                    val subs = top.optJSONArray("subs") ?: continue
                    for (j in 0 until subs.length()) {
                        val s = subs.optJSONObject(j) ?: continue
                        val sn = s.optString("name", "")
                        if (sn.isEmpty()) continue
                        if (hit.subs.none { it.name == sn }) {
                            missing.add(MissingCat(type, topName, topIcon, sn, s.optString("icon", "grid").ifEmpty { "grid" }))
                        }
                    }
                }
            }
        }
        return missing
    }

    fun applyMissingCategories(cats: JSONObject): Int {
        val missing = collectMissingCategories(cats)
        if (missing.isEmpty()) return 0
        val data = app.categoryData()
        for (m in missing) {
            val list = data.list(m.type)
            var top = list.firstOrNull { it.name == m.topName }
            if (top == null) {
                top = com.muyian.debt.data.Category(m.topName, m.topIcon)
                list.add(top)
            }
            if (m.subName.isNotEmpty() && top.subs.none { it.name == m.subName }) {
                top.subs.add(com.muyian.debt.data.SubCat(m.subName, m.subIcon))
            }
        }
        app.saveCategories(data)
        return missing.size
    }

    fun handlePicked(path: String, name: String) {
        val r = try { JSONObject(MuyianBillCore.readBackupText(ctx, path)) } catch (_: Exception) { JSONObject() }
        if (!r.optBoolean("ok")) {
            app.showToast(r.optString("error", "读取备份失败"))
            return
        }
        val data = try { JSONObject(r.optString("text", "{}")) } catch (_: Exception) { JSONObject() }
        if (data.optString("app") != "muyian" || data.optJSONArray("bills") == null) {
            app.showToast("不是孽账的备份文件")
            return
        }
        if (data.optInt("version", 0) != 1) {
            app.showToast("备份版本不支持：${data.optInt("version", 0)}")
            return
        }
        val bills = sanitizeBills(data.optJSONArray("bills") ?: JSONArray())
        if (bills.isEmpty()) {
            app.showToast("备份里没有可恢复的账单")
            return
        }
        backup = BackupInfo(
            name = name,
            exportedAt = data.optLong("exportedAt", 0L),
            bills = bills,
            categories = data.optJSONObject("categories") ?: JSONObject()
        )
        missingCatCount = collectMissingCategories(backup.categories).size
        step = "preview"
    }

    LaunchedEffect(picking) {
        if (!picking) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (true) {
            delay(500)
            val st = try { JSONObject(MuyianBillCore.getImportPickState(ctx)) } catch (_: Exception) { JSONObject() }
            when (st.optString("state")) {
                "picked" -> {
                    val path = st.optString("path", "")
                    val name = st.optString("name", "")
                    picking = false
                    handlePicked(path, name)
                    return@LaunchedEffect
                }
                "cancelled" -> { picking = false; app.showToast("已取消选择"); return@LaunchedEffect }
                "error" -> { picking = false; app.showToast("选择文件失败：${st.optString("error", "未知原因")}"); return@LaunchedEffect }
            }
            if (System.currentTimeMillis() - start > 90_000) {
                picking = false
                app.showToast("等待超时，请重新选择")
                return@LaunchedEffect
            }
        }
    }

    fun startRestore() {
        if (importing || backup.bills.isEmpty()) return
        importing = true
        restoredCount = 0
        val catAdded = applyMissingCategories(backup.categories)
        val list = backup.bills
        scope.launch {
            var inserted = 0
            var skipped = 0
            var failed = false
            var i = 0
            while (i < list.size) {
                val end = minOf(i + 400, list.size)
                val part = JSONArray()
                for (j in i until end) part.put(list[j])
                val resp = try { JSONObject(MuyianBillCore.commitBackupBills(ctx, part.toString())) } catch (_: Exception) { JSONObject() }
                if (!resp.optBoolean("success")) {
                    failed = true
                    break
                }
                inserted += resp.optInt("inserted", 0)
                skipped += resp.optInt("skipped", 0)
                i = end
                restoredCount = i
                delay(30)
            }
            importing = false
            result = BackupResult(inserted, skipped, catAdded, failed)
            step = "done"
            if (!failed) app.showToast("已恢复 $inserted 条")
        }
    }

    fun reset() {
        step = "idle"
        backup = BackupInfo()
        missingCatCount = 0
        result = BackupResult()
    }

    val stats = remember(backup) {
        var expense = 0
        var income = 0
        var minDay = ""
        var maxDay = ""
        for (o in backup.bills) {
            if (o.optDouble("money", 0.0) < 0) expense++ else income++
            val day = "${o.optInt("year")}-${pad2(o.optInt("month"))}-${pad2(o.optInt("day"))}"
            if (minDay.isEmpty() || day < minDay) minDay = day
            if (day > maxDay) maxDay = day
        }
        intArrayOf(backup.bills.size, expense, income) to (minDay to maxDay)
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = rpx(20), vertical = rpx(12)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(rpx(72)).clip(CircleShape).pressable(p.fill) { app.pop() }, contentAlignment = Alignment.Center) {
                Icon("chevron-left", p.text, rpx(22))
            }
            Text("备份·恢复", color = p.text, fontSize = rsp(32), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(rpx(72)))
        }

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
                .padding(bottom = rpx(60) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
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
                        onClick = { app.navigate(com.muyian.debt.data.Screen.Import) },
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
                        BStat(stats.first[0].toString(), "账单", p.text)
                        BStat(stats.first[1].toString(), "支出", p.out)
                        BStat(stats.first[2].toString(), "收入", p.inn)
                        BStat(missingCatCount.toString(), "补全分类", p.text)
                    }
                    Spacer(Modifier.height(rpx(14)))
                    Text("${stats.second.first} ~ ${stats.second.second}", color = p.sub, fontSize = rsp(23), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    if (backup.exportedAt > 0) {
                        Text("导出时间：${formatTs(backup.exportedAt)}", color = p.sub, fontSize = rsp(23), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = rpx(8)))
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

@Composable
private fun RowScope.BStat(num: String, label: String, color: Color) {
    val p = LocalPalette.current
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(num, color = color, fontSize = rsp(42), fontWeight = FontWeight.Bold)
        Text(label, color = p.dim, fontSize = rsp(21), modifier = Modifier.padding(top = rpx(4)))
    }
}
