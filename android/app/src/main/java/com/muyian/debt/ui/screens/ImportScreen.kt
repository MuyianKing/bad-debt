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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyian.debt.data.AppState
import com.muyian.debt.data.Qianji
import com.muyian.debt.data.fmtNum
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
import kotlin.math.roundToInt

private data class ImportSummary(
    val total: Int = 0,
    val expense: Int = 0,
    val income: Int = 0,
    val skipped: Int = 0,
    val minDay: String = "",
    val maxDay: String = "",
    val dupInDb: Int = 0
)

private data class ImportResult(val inserted: Int = 0, val skipped: Int = 0, val failed: Boolean = false)

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
    var result by remember { mutableStateOf(ImportResult()) }

    val leafColumns = remember(app.categories) { Qianji.buildLeafColumns(app.categories) }

    fun fetchAllRows(): List<List<String>>? {
        val all = ArrayList<List<String>>()
        var off = 0
        var nativeTotal = 0
        while (true) {
            val resp = try {
                JSONObject(MuyianBillCore.getImportSheetRows(off.toString(), "500"))
            } catch (_: Exception) {
                return null
            }
            val arr = resp.optJSONArray("rows") ?: return null
            nativeTotal = resp.optInt("total", 0)
            for (i in 0 until arr.length()) {
                val r = arr.optJSONArray(i) ?: continue
                val list = ArrayList<String>(r.length())
                for (j in 0 until r.length()) list.add(r.optString(j, ""))
                all.add(list)
            }
            off += 500
            if (off >= nativeTotal || arr.length() == 0) break
        }
        return if (all.size == nativeTotal) all else null
    }

    fun handlePicked(path: String, name: String) {
        if (path.isEmpty()) {
            app.showToast("文件路径无效，请重试")
            return
        }
        val sheet = try { JSONObject(MuyianBillCore.readImportSheet(path)) } catch (_: Exception) { JSONObject() }
        if (!sheet.optBoolean("ok")) {
            app.showToast(sheet.optString("error", "读取文件失败"))
            return
        }
        val headerArr = sheet.optJSONArray("header") ?: JSONArray()
        val header = ArrayList<String>()
        for (i in 0 until headerArr.length()) header.add(headerArr.optString(i, ""))
        val raw = fetchAllRows()
        if (raw == null) {
            app.showToast("读取表格失败，请重选文件")
            return
        }
        val parsed = Qianji.parseSheet(header, raw)
        if (!parsed.ok) {
            app.showToast(parsed.error)
            return
        }
        if (parsed.rows.isEmpty()) {
            app.showToast("文件里没有可导入的账单")
            return
        }
        var dupInDb = 0
        val orderNos = Qianji.orderNos(parsed.rows)
        if (orderNos.isNotEmpty()) {
            val arr = JSONArray()
            orderNos.forEach { arr.put(it) }
            val dup = try { JSONObject(MuyianBillCore.countExistingOrders(ctx, arr.toString())) } catch (_: Exception) { JSONObject() }
            dupInDb = dup.optInt("count", 0)
        }
        summary = ImportSummary(
            parsed.total, parsed.expense, parsed.income, parsed.skipped,
            parsed.minDay, parsed.maxDay, dupInDb
        )
        fileName = name.ifEmpty { "钱迹账单.xlsx" }
        mappings = Qianji.buildMappings(parsed.cats, leafColumns)
        rows = parsed.rows
        step = "review"
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

    fun onPick() {
        if (picking || importing) return
        val resp = try { JSONObject(MuyianBillCore.openImportPicker(ctx)) } catch (_: Exception) { JSONObject() }
        if (!resp.optBoolean("success")) {
            app.showToast("无法打开文件选择器：${resp.optString("error", "未知原因")}")
            return
        }
        picking = true
    }

    fun startImport() {
        if (importing || rows.isEmpty()) return
        val allJson = try { JSONArray(Qianji.buildBillsJson(rows, mappings)) } catch (_: Exception) { JSONArray() }
        if (allJson.length() == 0) {
            app.showToast("没有可导入的账单")
            return
        }
        importing = true
        importedCount = 0
        scope.launch {
            var inserted = 0
            var skipped = 0
            var failed = false
            var i = 0
            while (i < allJson.length()) {
                val part = JSONArray()
                val end = minOf(i + 200, allJson.length())
                for (j in i until end) part.put(allJson.getJSONObject(j))
                val resp = try { JSONObject(MuyianBillCore.commitImportBills(ctx, part.toString())) } catch (_: Exception) { JSONObject() }
                if (!resp.optBoolean("success")) {
                    failed = true
                    break
                }
                inserted += resp.optInt("inserted", 0)
                skipped += resp.optInt("skipped", 0)
                i = end
                importedCount = i
                delay(30)
            }
            importing = false
            result = ImportResult(inserted, skipped, failed)
            step = "done"
            if (!failed) app.showToast("已导入 $inserted 条")
        }
    }

    fun reset() {
        step = "pick"
        summary = ImportSummary()
        mappings = emptyList()
        rows = emptyList()
        result = ImportResult()
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = rpx(20), vertical = rpx(12)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(rpx(72)).clip(CircleShape).pressable(p.fill) { app.pop() }, contentAlignment = Alignment.Center) {
                Icon("chevron-left", p.text, rpx(22))
            }
            Text("导入账单", color = p.text, fontSize = rsp(32), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
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
            label = "import-step"
        ) { current ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = rpx(60) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
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
                        StatItem(rows.size.toString(), "账单", p.text)
                        StatItem(summary.expense.toString(), "支出", p.out)
                        StatItem(summary.income.toString(), "收入", p.inn)
                        StatItem(summary.skipped.toString(), "跳过", p.text)
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

@Composable
private fun RowScope.StatItem(num: String, label: String, color: Color) {
    val p = LocalPalette.current
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(num, color = color, fontSize = rsp(44), fontWeight = FontWeight.Bold)
        Text(label, color = p.dim, fontSize = rsp(21), modifier = Modifier.padding(top = rpx(4)))
    }
}
