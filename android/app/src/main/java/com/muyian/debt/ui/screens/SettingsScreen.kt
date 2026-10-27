package com.muyian.debt.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.muyian.debt.data.AppState
import com.muyian.debt.data.MascotStyle
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.components.DividerLine
import com.muyian.debt.ui.components.GroupTitle
import com.muyian.debt.ui.components.MascotFace
import com.muyian.debt.ui.components.MascotImage
import com.muyian.debt.ui.components.StateChip
import com.muyian.debt.ui.components.WotSwitch
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.pressable
import com.muyian.debt.ui.theme.AllPalettes
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.paletteOf
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import muyian.bill.listener.MuyianBillCore
import org.json.JSONObject
import java.io.File

private data class PopupState(
    val miui: Boolean = false,
    val miuiPopup: Int = -1,
    val known: Boolean = false,
    val blocked: Boolean = false
) {
    val ok: Boolean get() = known && miuiPopup == 0
    val text: String
        get() = when {
            known && miuiPopup == 0 -> "已允许"
            known && miuiPopup == 1 -> "未允许"
            blocked -> "被系统拦截"
            else -> "未检测"
        }
}

private fun copyHeroImage(context: Context, uri: Uri): String? = try {
    val file = File(context.filesDir, "hero_${System.currentTimeMillis()}.jpg")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    }
    file.absolutePath
} catch (_: Exception) {
    null
}

@Composable
fun SettingsScreen(app: AppState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var listenerEnabled by remember { mutableStateOf(false) }
    var notifyEnabled by remember { mutableStateOf(false) }
    var notifAllowed by remember { mutableStateOf(true) }
    var overlayPerm by remember { mutableStateOf(false) }
    var popup by remember { mutableStateOf(PopupState()) }
    var batteryOk by remember { mutableStateOf(true) }
    var keepAlive by remember { mutableStateOf(true) }
    var repairOk by remember { mutableStateOf(false) }
    var repairPkg by remember { mutableStateOf("") }

    fun refresh() {
        listenerEnabled = try { MuyianBillCore.isAccessibilityEnabled(ctx) } catch (_: Exception) { false }
        notifyEnabled = try { MuyianBillCore.isNotificationListenerEnabled(ctx) } catch (_: Exception) { false }
        notifAllowed = try { MuyianBillCore.areNotificationsEnabled(ctx) } catch (_: Exception) { true }
        overlayPerm = try { MuyianBillCore.hasOverlayPermission(ctx) } catch (_: Exception) { false }
        try {
            val o = JSONObject(MuyianBillCore.getPopupPermissionState(ctx))
            popup = PopupState(
                o.optBoolean("miui"),
                if (o.has("miuiPopup")) o.optInt("miuiPopup") else -1,
                o.optBoolean("miuiPopupKnown"),
                o.optBoolean("blocked")
            )
        } catch (_: Exception) {
            popup = PopupState()
        }
        batteryOk = try { MuyianBillCore.isIgnoringBatteryOptimizations(ctx) } catch (_: Exception) { true }
        keepAlive = try { MuyianBillCore.isKeepAliveEnabled(ctx) } catch (_: Exception) { false }
        try {
            val st = JSONObject(MuyianBillCore.getSelfRepairState(ctx))
            repairOk = st.optBoolean("hasPermission")
            repairPkg = st.optString("packageName", "")
        } catch (_: Exception) {
            repairOk = false
            repairPkg = ""
        }
    }

    LaunchedEffect(app.resumeToken) {
        refresh()
    }

    val heroBitmap = remember(app.heroImg) {
        if (app.heroImg.isEmpty()) null else try {
            BitmapFactory.decodeFile(app.heroImg.removePrefix("file://"))
        } catch (_: Exception) {
            null
        }
    }

    val heroPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = copyHeroImage(ctx, uri)
        if (path == null) {
            app.showToast("读取图片失败")
            return@rememberLauncherForActivityResult
        }
        val old = app.heroImg
        app.updateHeroImg(path)
        if (old.isNotEmpty()) {
            try { File(old.removePrefix("file://")).delete() } catch (_: Exception) {
            }
        }
        app.showToast("已设置首页头图")
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
        app.showToast(if (it) "通知权限已开启" else "请在系统设置中手动允许通知")
    }

    fun chooseHero() {
        app.actionSheet = com.muyian.debt.data.ActionSheetState(
            listOf("选择图片" to false, "恢复默认" to false)
        ) { index ->
            if (index == 0) {
                heroPicker.launch("image/*")
            } else {
                val old = app.heroImg
                app.updateHeroImg("")
                if (old.isNotEmpty()) {
                    try { File(old.removePrefix("file://")).delete() } catch (_: Exception) {
                    }
                }
                app.showToast("已恢复默认")
            }
        }
    }

    val moodHint = run {
        val parts = ArrayList<String>()
        app.activeMascot.styles.firstOrNull { it.mood == "empty" }?.let { parts.add("空态用「${it.name}」") }
        app.activeMascot.styles.firstOrNull { it.mood == "success" }?.let { parts.add("成功用「${it.name}」") }
        if (parts.isEmpty()) "按场景自动切换表情" else parts.joinToString("，")
    }

    Column(Modifier.fillMaxSize().background(p.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = rpx(20), vertical = rpx(12)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(rpx(72)).clip(CircleShape).pressable(p.fill) { app.pop() }, contentAlignment = Alignment.Center) {
                AppIcon("chevron-left", p.text, 22.dp)
            }
            Text(
                "设置·关于",
                color = p.text,
                fontSize = rsp(32),
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(rpx(72)))
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = rpx(60) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            GroupTitle("外观")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(24), vertical = rpx(16))
                    .glass(shape = RoundedCornerShape(rpx(20)))
                    .padding(rpx(26))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBlock("droplet", Brush.linearGradient(p.gradPrimary), 48, 16)
                    Spacer(Modifier.width(rpx(14)))
                    Column {
                        Text("主题", color = p.text, fontSize = rsp(30))
                        Text("当前：${p.name}，切换后立即生效", color = p.sub, fontSize = rsp(22), modifier = Modifier.padding(top = rpx(4)))
                    }
                }
                Spacer(Modifier.height(rpx(20)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rpx(16))) {
                    for (tp in AllPalettes) {
                        val active = p.id == tp.id
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(rpx(18)))
                                .background(p.card.copy(alpha = if (p.dark) 0.5f else 0.55f))
                                .then(if (active) Modifier.border(2.dp, p.primary, RoundedCornerShape(rpx(18))) else Modifier)
                                .clickable {
                                    if (!active) {
                                        app.setTheme(tp.id)
                                        app.showToast("已切换为${tp.name}")
                                    }
                                }
                                .padding(rpx(12))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(rpx(88))
                                    .clip(RoundedCornerShape(rpx(14)))
                                    .background(Brush.linearGradient(tp.gradPrimary))
                                    .padding(rpx(12))
                            ) {
                                Column(
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(rpx(10)))
                                        .background(p.card.copy(alpha = 0.92f))
                                        .padding(horizontal = rpx(10), vertical = rpx(9))
                                ) {
                                    Box(Modifier.width(rpx(42)).height(rpx(6)).clip(RoundedCornerShape(rpx(3))).background(p.dim))
                                    Box(Modifier.padding(top = rpx(6)).width(rpx(26)).height(rpx(6)).clip(RoundedCornerShape(rpx(3))).background(p.dim.copy(alpha = 0.7f)))
                                }
                                if (active) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .size(rpx(32))
                                            .clip(CircleShape)
                                            .background(p.card),
                                        contentAlignment = Alignment.Center
                                    ) { AppIcon("check", p.primary, 12.dp, 3.4f) }
                                }
                            }
                            Text(tp.name, color = p.text, fontSize = rsp(27), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = rpx(12)))
                            Text(tp.tagline, color = p.sub, fontSize = rsp(21), modifier = Modifier.padding(top = rpx(2)))
                        }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(24), vertical = rpx(16))
                    .glass(shape = RoundedCornerShape(rpx(20)))
                    .padding(rpx(26))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBlock("smile", Brush.linearGradient(p.gradRose), 48, 16)
                    Spacer(Modifier.width(rpx(14)))
                    Column {
                        Text("${app.activeMascot.name}表情", color = p.text, fontSize = rsp(30))
                        Text(
                            "当前：${app.baseMascotStyle.name}${if (app.autoMood) "，跟随场景" else "，固定表情"}",
                            color = p.sub, fontSize = rsp(22), modifier = Modifier.padding(top = rpx(4))
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = rpx(20))
                        .clip(RoundedCornerShape(rpx(16)))
                        .background(p.fill)
                        .padding(horizontal = rpx(20), vertical = rpx(18)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("跟随场景", color = p.text, fontSize = rsp(27), fontWeight = FontWeight.SemiBold)
                        Text(moodHint, color = p.sub, fontSize = rsp(21), modifier = Modifier.padding(top = rpx(4)))
                    }
                    WotSwitch(app.autoMood) {
                        app.updateAutoMood(it)
                        app.showToast(if (it) "已开启跟随场景" else "已固定当前表情")
                    }
                }
                Spacer(Modifier.height(rpx(20)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rpx(16))) {
                    for (style in app.activeMascot.styles) {
                        val active = app.baseMascotStyle.id == style.id
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(rpx(18)))
                                .background(p.card.copy(alpha = if (p.dark) 0.5f else 0.55f))
                                .then(if (active) Modifier.border(2.dp, p.primary, RoundedCornerShape(rpx(18))) else Modifier)
                                .clickable {
                                    if (!active) {
                                        val n = app.setMascotStyle(style.id)
                                        app.showToast("已切换为「${n.name}」")
                                    }
                                }
                                .padding(rpx(12))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(rpx(170))
                                    .clip(RoundedCornerShape(rpx(14)))
                                    .background(p.primaryWeak),
                                contentAlignment = Alignment.Center
                            ) {
                                MascotImage(style.headAsset, Modifier.fillMaxSize(), ContentScale.Fit)
                                if (active) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(rpx(8))
                                            .size(rpx(32))
                                            .clip(CircleShape)
                                            .background(p.card),
                                        contentAlignment = Alignment.Center
                                    ) { AppIcon("check", p.primary, 12.dp, 3.4f) }
                                }
                            }
                            Text(style.name, color = p.text, fontSize = rsp(27), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = rpx(12)))
                            Text(style.desc, color = p.sub, fontSize = rsp(21), modifier = Modifier.padding(top = rpx(2)))
                        }
                    }
                }
            }

            SettingGroup {
                SettingCell(
                    icon = "image",
                    gradient = Brush.linearGradient(p.gradSky),
                    title = "首页头图",
                    desc = "自定义首页顶部背景图，仅保存在本机",
                    onClick = ::chooseHero,
                    trailing = {
                        if (heroBitmap != null) {
                            Image(
                                bitmap = heroBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(width = rpx(96), height = rpx(60)).clip(RoundedCornerShape(rpx(12))),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text("设置图片", color = p.primaryDeep, fontSize = rsp(26))
                        }
                        Spacer(Modifier.width(rpx(8)))
                        AppIcon("chevron-right", p.dim, 16.dp)
                    }
                )
            }

            GroupTitle("自动记账")
            SettingGroup {
                SettingCell(
                    icon = "shield",
                    gradient = Brush.linearGradient(p.gradPrimary),
                    title = "无障碍服务",
                    state = if (listenerEnabled) "已开启" to true else "未开启" to false,
                    desc = "自动记账的核心开关。开启后，你在支付宝/微信等App里完成支付时，孽账会读取支付结果页的文字，在本地解析出待确认账单。",
                    action = if (listenerEnabled) null else "去开启",
                    onClick = {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {
                        }
                    }
                )
                SettingCell(
                    icon = "bell",
                    gradient = Brush.linearGradient(p.gradSky),
                    title = "通知使用权（辅助）",
                    state = if (notifyEnabled) "已开启" to true else "未开启" to false,
                    desc = "补充无障碍覆盖不到的场景：收款到账提醒只在通知栏、熄屏碰卡支付的交易通知。两路来源自动去重，不会重复入账。",
                    action = if (notifyEnabled) null else "去开启",
                    onClick = {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {
                        }
                    }
                )
                SettingCell(
                    icon = "rotate-ccw",
                    gradient = Brush.linearGradient(p.gradViolet),
                    title = "无障碍自恢复",
                    state = if (repairOk) "已授权" to true else "未授权" to false,
                    desc = "系统可能杀掉无障碍服务导致自动记账失效。用电脑 adb 执行一次授权命令后，打开孽账会自动把服务重新开启，无需再去系统设置手动开。",
                    action = if (!repairOk) "授权教程" else if (!listenerEnabled) "立即恢复" else null,
                    onClick = {
                        if (!repairOk) {
                            val pkg = repairPkg.ifEmpty { ctx.packageName }
                            val cmd = "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
                            app.confirm(
                                "开启无障碍自恢复",
                                "手机连电脑允许 USB 调试后执行一次，永久生效：$cmd\n\n小米/OPPO/vivo 等机型需先在开发者选项开启「USB 调试（安全设置）」，否则命令会报 GRANT_RUNTIME_PERMISSIONS 权限错误。",
                                "复制命令"
                            ) {
                                app.copyText(cmd)
                                app.showToast("已复制，执行后回到本页刷新")
                            }
                        } else if (!listenerEnabled) {
                            val resp = try { JSONObject(MuyianBillCore.repairAccessibilityService(ctx)) } catch (_: Exception) { JSONObject() }
                            if (resp.optBoolean("success") && resp.optBoolean("serviceEnabled")) {
                                app.showToast("已恢复开启无障碍服务")
                            } else {
                                app.showToast("恢复失败：${resp.optString("error", "未知原因")}")
                            }
                            refresh()
                        } else {
                            app.showToast("无障碍服务运行正常")
                        }
                    }
                )
                SettingCell(
                    icon = "maximize",
                    gradient = Brush.linearGradient(
                        listOf(com.muyian.debt.ui.theme.hex("#74ADFF"), com.muyian.debt.ui.theme.hex("#4489FE"))
                    ),
                    title = "显示在其他应用上层",
                    state = if (overlayPerm) "已允许" to true else "未允许" to false,
                    desc = "系统级悬浮窗开关，弹层要盖在支付页上方必须先允许。这里只开系统开关，不绘制悬浮窗。",
                    action = if (overlayPerm) null else "去允许",
                    onClick = {
                        try { MuyianBillCore.requestOverlayPermission(ctx) } catch (_: Exception) {
                        }
                        app.showToast("请允许“显示在其他应用上层”，返回后自动生效")
                    }
                )
                if (popup.miui) {
                    SettingCell(
                        icon = "shield",
                        gradient = Brush.linearGradient(p.gradViolet),
                        title = "后台弹出界面（MIUI）",
                        state = popup.text to popup.ok,
                        desc = "MIUI/HyperOS 单独的后台启动 Activity 开关，和上面的悬浮窗开关不是一个。点「去开启」后进入权限页，再点「其他权限」→「后台弹出界面」；未开启时弹层会被系统拦掉，只能收到待确认通知。",
                        action = if (popup.ok) null else "去开启",
                        onClick = {
                            try { MuyianBillCore.openPopupPermissionSettings(ctx) } catch (_: Exception) {
                            }
                            app.showToast("点「其他权限」→「后台弹出界面」")
                        }
                    )
                }
                SettingCell(
                    icon = "bell",
                    gradient = Brush.linearGradient(p.gradRose),
                    title = "App 弹通知权限",
                    state = if (notifAllowed) "已开启" to true else "未开启" to false,
                    desc = "Android 13+ 需要。用于后台保活常驻通知，以及确认弹层被系统拦住时的待确认提醒；关闭后这两类通知都收不到",
                    action = if (notifAllowed) null else "请求权限",
                    onClick = {
                        if (notifAllowed) app.showToast("通知权限已开启")
                        else notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }

            GroupTitle("后台运行")
            SettingGroup {
                SettingCell(
                    icon = "battery-charging",
                    gradient = Brush.linearGradient(p.gradPrimary),
                    title = "电池优化",
                    state = if (batteryOk) "不限制" to true else "受限制" to false,
                    desc = "系统省电策略可能在锁屏后回收后台进程，导致自动记账失效。请把电池策略设为“不限制”。",
                    chevron = !batteryOk,
                    onClick = {
                        if (batteryOk) {
                            app.showToast("已不受电池优化限制")
                        } else {
                            app.confirm(
                                "关闭电池优化",
                                "在系统弹窗中选择“不限制”。部分机型还需在“应用详情”里把自启动/后台运行设为允许。",
                                "去设置"
                            ) {
                                try { MuyianBillCore.requestIgnoreBatteryOptimizations(ctx) } catch (_: Exception) {
                                }
                            }
                        }
                    }
                )
                SettingCell(
                    icon = "zap",
                    gradient = Brush.linearGradient(p.gradMint),
                    title = "后台保活",
                    state = if (keepAlive) "已开启" to true else "已关闭" to false,
                    desc = "用一条低优先级常驻通知维持进程，降低被系统回收的概率。关闭后仍可记账，但后台更容易被厂商省电策略杀掉。",
                    switch = keepAlive,
                    onSwitch = { value ->
                        val ok = try { MuyianBillCore.setKeepAliveEnabled(ctx, value) } catch (_: Exception) { false }
                        if (ok) {
                            keepAlive = value
                        } else {
                            app.showToast("设置失败，请重试")
                        }
                    }
                )
                SettingCell(
                    icon = "layers",
                    gradient = Brush.linearGradient(
                        listOf(com.muyian.debt.ui.theme.hex("#74ADFF"), com.muyian.debt.ui.theme.hex("#4489FE"))
                    ),
                    title = "从多任务页面隐藏",
                    state = if (app.hideRecents) "已隐藏" to true else "显示中" to false,
                    desc = "隐藏后系统「一键清理」不会把本应用任务一起清掉，自动记账更不容易被连带杀死。关闭后可在最近任务里看到本应用。",
                    switch = app.hideRecents,
                    onSwitch = { value ->
                        app.updateHideRecents(value)
                        app.showToast(if (value) "已从最近任务隐藏" else "已在最近任务显示")
                    }
                )
                SettingCell(
                    icon = "power",
                    gradient = Brush.linearGradient(p.gradViolet),
                    title = "自启动 / 后台管理",
                    desc = "小米、OPPO、vivo 等机型还需在应用详情里允许“自启动”“后台运行”，否则锁屏后仍可能被清理。",
                    action = "去设置",
                    onClick = {
                        try { MuyianBillCore.openAppSettings(ctx) } catch (_: Exception) {
                        }
                        app.showToast("请在应用详情中允许自启动/后台运行")
                    }
                )
            }

            GroupTitle("隐私说明")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = rpx(20), vertical = rpx(4))
                    .glass(shape = RoundedCornerShape(rpx(20)))
                    .padding(horizontal = rpx(28), vertical = rpx(24))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBlock("lock", Brush.linearGradient(p.gradPrimary), 48, 16)
                    Spacer(Modifier.width(rpx(14)))
                    Text("数据只在本机", color = p.text, fontSize = rsp(27), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(rpx(12)))
                for (line in listOf(
                    "· 只读取候选支付App的窗口文字，且只在手机本地解析，不上传任何服务器",
                    "· 账单数据保存在本机 SQLite，可随时删除",
                    "· 白名单之外的App内容不会被读取或存储"
                )) {
                    Text(line, color = p.sub, fontSize = rsp(24), lineHeight = rsp(46))
                }
            }
        }
    }
}

@Composable
private fun SettingGroup(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = rpx(20), vertical = rpx(4))
            .glass(shape = RoundedCornerShape(rpx(20)))
    ) {
        content()
    }
}

@Composable
private fun SettingCell(
    icon: String,
    gradient: Brush,
    title: String,
    desc: String,
    state: Pair<String, Boolean>? = null,
    action: String? = null,
    onClick: (() -> Unit)? = null,
    switch: Boolean? = null,
    onSwitch: ((Boolean) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    chevron: Boolean = action != null
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null || switch != null) Modifier.clickable(enabled = onClick != null) { onClick?.invoke() } else Modifier)
                .padding(horizontal = rpx(24), vertical = rpx(22)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(rpx(48)).clip(RoundedCornerShape(rpx(15))).background(gradient),
                contentAlignment = Alignment.Center
            ) { AppIcon(icon, Color.White, 16.dp) }
            Spacer(Modifier.width(rpx(14)))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = p.text, fontSize = rsp(30), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state != null) {
                        if (state.second && action == null && switch == null && trailing == null) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.width(rpx(12)))
                        }
                        StateChip(state.first, state.second)
                    }
                }
                Spacer(Modifier.height(rpx(6)))
                Text(desc, color = p.sub, fontSize = rsp(22), lineHeight = rsp(33))
            }
            if (switch != null && onSwitch != null) {
                Spacer(Modifier.width(rpx(12)))
                WotSwitch(switch, onChange = onSwitch)
            } else if (trailing != null) {
                Spacer(Modifier.width(rpx(12)))
                trailing()
            } else if (action != null || chevron) {
                Spacer(Modifier.width(rpx(12)))
                if (action != null) {
                    Text(action, color = p.primaryDeep, fontSize = rsp(26), maxLines = 1)
                    Spacer(Modifier.width(rpx(4)))
                }
                if (chevron) AppIcon("chevron-right", p.dim, 16.dp)
            }
        }
        DividerLine(color = p.lineSoft)
    }
}

@Composable
private fun IconBlock(icon: String, gradient: Brush, size: Int, iconSize: Int) {
    Box(
        Modifier.size(rpx(size)).clip(RoundedCornerShape(rpx(size * 0.3f))).background(gradient),
        contentAlignment = Alignment.Center
    ) { AppIcon(icon, Color.White, iconSize.dp) }
}
