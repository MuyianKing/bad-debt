package com.muyian.debt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyian.debt.data.Capture
import com.muyian.debt.data.CaptureApp
import com.muyian.debt.data.CaptureDiagnostics
import com.muyian.debt.data.CaptureSettingsData
import com.muyian.debt.data.CaptureTrace
import com.muyian.debt.state.ActionSheetState
import com.muyian.debt.state.AppState
import com.muyian.debt.ui.components.AppSwitch
import com.muyian.debt.ui.components.DividerLine
import com.muyian.debt.ui.components.GroupTitle
import com.muyian.debt.ui.components.StateChip
import com.muyian.debt.ui.components.TopBar
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.pageBottomPadding
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 进程内快照：再次进入先用上次数据渲染，避免空态在入场动画里闪一下。 */
private object CaptureSnapshot {
    var diag: CaptureDiagnostics? = null
    var apps: List<CaptureApp>? = null
    var settings: CaptureSettingsData? = null
}

/** 最近识别首屏渲染条数：一次铺满 120 行会在入场那一帧卡顿。 */
private const val TRACE_PAGE = 30

/** 识别记录与诊断：本地只读，展示最近判定、拒绝原因、App 开关与采集设置。 */
@Composable
fun CaptureScreen(app: AppState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var diag by remember { mutableStateOf(CaptureSnapshot.diag ?: CaptureDiagnostics()) }
    var apps by remember { mutableStateOf(CaptureSnapshot.apps ?: emptyList<CaptureApp>()) }
    var settings by remember { mutableStateOf(CaptureSnapshot.settings ?: CaptureSettingsData()) }
    var loaded by remember { mutableStateOf(CaptureSnapshot.diag != null) }
    var includeAllApps by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var showAllTraces by remember { mutableStateOf(false) }
    // 每行各建一个 SimpleDateFormat 会在成批组合时明显拖慢，全页共用一个即可
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }

    fun keepDiag(value: CaptureDiagnostics) {
        diag = value
        settings = value.settings
        CaptureSnapshot.diag = value
        CaptureSnapshot.settings = value.settings
    }

    fun keepApps(value: List<CaptureApp>) {
        apps = value
        CaptureSnapshot.apps = value
    }

    fun reload() {
        keepDiag(Capture.load(ctx, 200))
        loaded = true
    }

    fun updateApp(pkg: String, role: String, enabled: Boolean) {
        // 就地更新，不在切换时重排：重排会让行跳到列表末尾，看起来像改错了应用
        keepApps(
            apps.map {
                if (it.pkg == pkg) it.copy(role = role, enabled = enabled) else it
            }
        )
    }

    val scope = rememberCoroutineScope()
    fun reparseTrace(t: CaptureTrace) {
        if (t.text.isEmpty() || t.pkg.isEmpty()) {
            app.showToast("这条记录没有保存页面文本")
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                Capture.reparse(ctx, t.pkg, t.source, t.text)
            }
            app.alert("重新识别结果", result.summary())
        }
    }

    LaunchedEffect(app.resumeToken) {
        val loadedDiag = withContext(Dispatchers.IO) { Capture.load(ctx, 200) }
        val loadedApps = withContext(Dispatchers.IO) { Capture.apps(ctx, includeAllApps) }
        keepDiag(loadedDiag)
        keepApps(loadedApps)
        loaded = true
    }
    // 枚举全部已安装应用会触发部分 ROM 的权限提示且耗时，只有用户点「扫描其它应用」才做
    LaunchedEffect(includeAllApps) {
        if (!includeAllApps) return@LaunchedEffect
        scanning = true
        keepApps(withContext(Dispatchers.IO) { Capture.apps(ctx, true) })
        scanning = false
    }

    fun saveSettings(next: CaptureSettingsData) {
        if (Capture.saveSettings(ctx, next)) {
            settings = next
            CaptureSnapshot.settings = next
        } else {
            app.showToast("设置保存失败")
            reload()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "识别记录", onBack = { app.pop() })
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .pageBottomPadding()
        ) {
            GroupTitle("今日")
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(20), vertical = rpx(4))
                    .glass(shape = RoundedCornerShape(rpx(20)))
                    .padding(vertical = rpx(22)),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Stat("待确认", diag.summary.accepted, p.primaryDeep)
                Stat("保存", diag.summary.saved, p.inn)
                Stat("丢弃", diag.summary.discarded, p.out)
                Stat("拒绝", diag.summary.rejected, p.sub)
            }

            if (diag.summary.amountEdited > 0) {
                Text(
                    "其中 ${diag.summary.amountEdited} 笔保存时改过金额，这类页面需要优先改进识别",
                    color = p.sub,
                    fontSize = rsp(22),
                    modifier = Modifier.padding(horizontal = rpx(28), vertical = rpx(2))
                )
            }

            if (diag.summary.reasons.isNotEmpty()) {
                GroupTitle("今日拒绝原因")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(20), vertical = rpx(4))
                        .glass(shape = RoundedCornerShape(rpx(20)))
                        .padding(vertical = rpx(6))
                ) {
                    for (r in diag.summary.reasons) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = rpx(24), vertical = rpx(10)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                r.label.ifEmpty { r.reason },
                                color = p.text,
                                fontSize = rsp(24),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("${r.count}", color = p.sub, fontSize = rsp(24))
                        }
                    }
                }
            }

            GroupTitle("识别行为")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(20), vertical = rpx(4))
                    .glass(shape = RoundedCornerShape(rpx(20)))
            ) {
                SwitchRow(
                    title = "记录本人互转与还款",
                    desc = "提现、充值、余额宝转入、信用卡还款默认不记账，避免和真实消费重复。",
                    checked = settings.recordTransfers,
                    onChange = { saveSettings(settings.copy(recordTransfers = it)) }
                )
                DividerLine(color = p.lineSoft)
                SwitchRow(
                    title = "记录页面文本（调试）",
                    desc = "开启后只在本机保留最近 500 条页面文本 7 天，用于排查漏记/误记；不会上传。",
                    checked = settings.debugText,
                    onChange = { saveSettings(settings.copy(debugText = it)) }
                )
                DividerLine(color = p.lineSoft)
                SwitchRow(
                    title = "低置信度也弹层",
                    desc = "识别把握不足时仍然弹出确认（推荐）；关闭后只记录诊断，不打扰。",
                    checked = settings.lowConfidence,
                    onChange = { saveSettings(settings.copy(lowConfidence = it)) }
                )
            }

            GroupTitle("识别应用")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(20), vertical = rpx(4))
                    .glass(shape = RoundedCornerShape(rpx(20)))
            ) {
                when {
                    !loaded -> Text(
                        "正在读取…",
                        color = p.dim,
                        fontSize = rsp(23),
                        modifier = Modifier.padding(rpx(24))
                    )
                    apps.isEmpty() -> Text(
                        "没有发现可识别的应用",
                        color = p.sub,
                        fontSize = rsp(23),
                        modifier = Modifier.padding(rpx(24))
                    )
                    else -> {
                        Text(
                            "内置支付/宿主 App 默认开启；名称含「银行/信用卡/云闪付」的 App 也会自动识别；其它 App 需手动打开。",
                            color = p.dim,
                            fontSize = rsp(20),
                            lineHeight = rsp(30),
                            modifier = Modifier.padding(horizontal = rpx(24), vertical = rpx(14))
                        )
                        DividerLine(color = p.lineSoft)
                    }
                }
                for (a in apps) {
                    key(a.pkg) {
                        AppRow(
                            app = a,
                            onToggle = { enabled ->
                                val role = if (a.role == "OFF") "HOST" else a.role
                                if (Capture.setApp(ctx, a.pkg, role, enabled)) {
                                    updateApp(a.pkg, role, enabled)
                                } else {
                                    app.showToast("设置失败")
                                }
                            },
                            onRole = {
                                app.actionSheet = ActionSheetState(
                                    listOf(
                                        "支付 App（支付宝/微信/银行）" to false,
                                        "宿主 App（美团/京东等）" to false,
                                        "关闭识别" to false
                                    )
                                ) { index ->
                                    val role = if (index == 0) "PAY" else "HOST"
                                    val enabled = index != 2
                                    val storedRole = if (enabled) role else a.role
                                    if (Capture.setApp(ctx, a.pkg, storedRole, enabled)) {
                                        updateApp(a.pkg, storedRole, enabled)
                                    } else {
                                        app.showToast("设置失败")
                                    }
                                }
                            }
                        )
                        DividerLine(color = p.lineSoft)
                    }
                }
                DividerLine(color = p.lineSoft)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !scanning && !includeAllApps) {
                            includeAllApps = true
                        }
                        .padding(horizontal = rpx(24), vertical = rpx(18)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            scanning -> "正在扫描…"
                            includeAllApps -> "已列出所有可启动应用"
                            else -> "扫描其它应用（需系统允许读取已安装应用）"
                        },
                        color = if (includeAllApps) p.dim else p.primaryDeep,
                        fontSize = rsp(22),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            GroupTitle("最近识别")
            if (!loaded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(20), vertical = rpx(4))
                        .glass(shape = RoundedCornerShape(rpx(20)))
                        .padding(rpx(24))
                ) {
                    Text("正在读取…", color = p.dim, fontSize = rsp(24))
                }
            } else if (diag.traces.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(20), vertical = rpx(4))
                        .glass(shape = RoundedCornerShape(rpx(20)))
                        .padding(rpx(24))
                ) {
                    Text("还没有识别记录", color = p.sub, fontSize = rsp(24))
                    Text(
                        "完成一笔支付后再回来查看；这里能看到每笔为什么被接受或拒绝。",
                        color = p.dim,
                        fontSize = rsp(21),
                        modifier = Modifier.padding(top = rpx(6))
                    )
                }
            } else {
                val visible = if (showAllTraces) diag.traces.take(120) else diag.traces.take(TRACE_PAGE)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = rpx(20), vertical = rpx(4))
                        .glass(shape = RoundedCornerShape(rpx(20)))
                ) {
                    for (t in visible) {
                        TraceRow(t, timeFmt, onReparse = { reparseTrace(t) })
                        DividerLine(color = p.lineSoft)
                    }
                    if (diag.traces.size > TRACE_PAGE) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showAllTraces = !showAllTraces }
                                .padding(horizontal = rpx(24), vertical = rpx(16)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (showAllTraces) "收起" else "查看全部 ${minOf(diag.traces.size, 120)} 条",
                                color = p.primaryDeep,
                                fontSize = rsp(22),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(20), vertical = rpx(18))
                    .clip(RoundedCornerShape(rpx(18)))
                    .glass(shape = RoundedCornerShape(rpx(18)))
                    .clickable {
                        app.confirm(
                            "清空识别记录",
                            "只会清空本机的诊断记录与分类学习，不影响已保存的账单。",
                            "清空"
                        ) {
                            Capture.clear(ctx)
                            reload()
                            app.showToast("已清空识别记录")
                        }
                    }
                    .padding(vertical = rpx(20)),
                contentAlignment = Alignment.Center
            ) {
                Text("清空识别记录", color = p.out, fontSize = rsp(25), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, color: Color) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", color = color, fontSize = rsp(40), fontWeight = FontWeight.Bold)
        Text(label, color = p.dim, fontSize = rsp(21), modifier = Modifier.padding(top = rpx(4)))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = rpx(24), vertical = rpx(18)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = rsp(27), fontWeight = FontWeight.Medium)
            Text(
                desc,
                color = p.sub,
                fontSize = rsp(21),
                lineHeight = rsp(31),
                modifier = Modifier.padding(top = rpx(5))
            )
        }
        Spacer(Modifier.width(rpx(12)))
        AppSwitch(checked, onChange = onChange)
    }
}

@Composable
private fun AppRow(app: CaptureApp, onToggle: (Boolean) -> Unit, onRole: () -> Unit) {
    val p = LocalPalette.current
    val enabled = app.enabled
    Row(
        Modifier.fillMaxWidth().padding(horizontal = rpx(24), vertical = rpx(16)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(rpx(48))
                .clip(RoundedCornerShape(rpx(14)))
                .background(if (enabled) p.primaryFill else p.fill),
            contentAlignment = Alignment.Center
        ) {
            Text(
                app.label.take(1).ifEmpty { "?" },
                color = if (enabled) p.primaryDeep else p.dim,
                fontSize = rsp(24),
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.width(rpx(14)))
        Column(Modifier.weight(1f)) {
            Text(
                app.label.ifEmpty { app.pkg },
                color = p.text,
                fontSize = rsp(26),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (!enabled) "已关闭" else roleLabel(app.role),
                color = p.dim,
                fontSize = rsp(20),
                modifier = Modifier.padding(top = rpx(3))
            )
        }
        Text(
            "改角色",
            color = p.primaryDeep,
            fontSize = rsp(21),
            modifier = Modifier
                .clip(RoundedCornerShape(rpx(10)))
                .clickable { onRole() }
                .padding(horizontal = rpx(10), vertical = rpx(6))
        )
        Spacer(Modifier.width(rpx(8)))
        AppSwitch(enabled, onChange = onToggle)
    }
}

@Composable
private fun TraceRow(t: CaptureTrace, timeFmt: SimpleDateFormat, onReparse: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val time = remember(t.at) { timeFmt.format(Date(t.at)) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = rpx(24), vertical = rpx(14)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.app.ifEmpty { "未知应用" },
                    color = p.text,
                    fontSize = rsp(24),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(rpx(8)))
                Text(time, color = p.dim, fontSize = rsp(19))
            }
            val amountText = if (t.amount > 0) {
                (if (t.direction == "INCOME" || t.direction == "REFUND") "+" else "-") +
                    String.format(Locale.US, "%.2f", t.amount)
            } else ""
            Text(
                listOf(t.kindLabel, amountText, t.reasonLabel)
                    .filter { it.isNotEmpty() }
                    .joinToString(" · "),
                color = p.sub,
                fontSize = rsp(20),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = rpx(4))
            )
            if (t.text.isNotEmpty()) {
                Text(
                    t.text.replace("\n", " ").take(80),
                    color = p.dim,
                    fontSize = rsp(18),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = rpx(4))
                )
                if (onReparse != null) {
                    Text(
                        "重新识别",
                        color = p.primaryDeep,
                        fontSize = rsp(19),
                        modifier = Modifier
                            .padding(top = rpx(6))
                            .clickable { onReparse() }
                    )
                }
            }
        }
        Spacer(Modifier.width(rpx(10)))
        StateChip(resultLabel(t.result), t.result == "accepted" || t.result == "saved")
    }
}

private fun roleLabel(role: String): String = when (role) {
    "PAY" -> "支付 App"
    "OFF" -> "已关闭"
    else -> "宿主 App"
}

private fun resultLabel(result: String): String = when (result) {
    "accepted" -> "已捕获"
    "saved" -> "已保存"
    "discarded" -> "已丢弃"
    "merged" -> "已合并"
    "duplicate" -> "重复"
    "rejected" -> "已拒绝"
    else -> result
}
