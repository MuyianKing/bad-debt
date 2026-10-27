package com.muyian.debt.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.muyian.debt.state.AppState
import com.muyian.debt.data.Files
import com.muyian.debt.data.MascotStyle
import com.muyian.debt.data.NativeStatus
import com.muyian.debt.state.ActionSheetState
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.components.DividerLine
import com.muyian.debt.ui.components.GroupTitle
import com.muyian.debt.ui.components.IconBlock
import com.muyian.debt.ui.components.MascotFace
import com.muyian.debt.ui.components.MascotImage
import com.muyian.debt.ui.components.Motion
import com.muyian.debt.ui.components.RiseIn
import com.muyian.debt.ui.components.StateChip
import com.muyian.debt.ui.components.AppSwitch
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.TopBar
import com.muyian.debt.ui.components.pageBottomPadding
import com.muyian.debt.ui.components.pressable
import com.muyian.debt.ui.components.rememberFileBitmap
import com.muyian.debt.ui.theme.AllPalettes
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.paletteOf
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp

@Composable
fun SettingsScreen(app: AppState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var status by remember { mutableStateOf(NativeStatus.snapshot(ctx)) }

    fun refresh() {
        status = NativeStatus.snapshot(ctx)
    }

    LaunchedEffect(app.resumeToken) {
        refresh()
    }

    val heroBitmap = rememberFileBitmap(app.heroImg)

    val heroPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = Files.copyUriToFiles(ctx, uri, "hero")
        if (path == null) {
            app.showToast("读取图片失败")
            return@rememberLauncherForActivityResult
        }
        val old = app.heroImg
        app.updateHeroImg(path)
        Files.delete(old)
        app.showToast("已设置首页头图")
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
        app.showToast(if (it) "通知权限已开启" else "请在系统设置中手动允许通知")
    }

    fun chooseHero() {
        app.actionSheet = ActionSheetState(
            listOf("选择图片" to false, "恢复默认" to false)
        ) { index ->
            if (index == 0) {
                heroPicker.launch("image/*")
            } else {
                val old = app.heroImg
                app.updateHeroImg("")
                Files.delete(old)
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
        TopBar(title = "设置·关于", onBack = { app.pop() })

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .pageBottomPadding()
        ) {
            RiseIn(0, key = "look") {
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
                        val borderColor by animateColorAsState(
                            if (active) p.primary else Color.Transparent,
                            tween(200, easing = Motion.InOut),
                            label = "theme-border"
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(rpx(18)))
                                .background(p.card.copy(alpha = if (p.dark) 0.5f else 0.55f))
                                .border(2.dp, borderColor, RoundedCornerShape(rpx(18)))
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
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = active,
                                    enter = fadeIn(tween(150)) +
                                        scaleIn(tween(200, easing = Motion.Out), initialScale = 0.5f),
                                    exit = fadeOut(tween(120)) +
                                        scaleOut(tween(150, easing = Motion.InOut), targetScale = 0.7f),
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Box(
                                        Modifier
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
            }

            RiseIn(1, key = "mascot") {
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
                    AppSwitch(app.autoMood) {
                        app.updateAutoMood(it)
                        app.showToast(if (it) "已开启跟随场景" else "已固定当前表情")
                    }
                }
                Spacer(Modifier.height(rpx(20)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(rpx(16))) {
                    for (style in app.activeMascot.styles) {
                        val active = app.baseMascotStyle.id == style.id
                        val borderColor by animateColorAsState(
                            if (active) p.primary else Color.Transparent,
                            tween(200, easing = Motion.InOut),
                            label = "mascot-border"
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(rpx(18)))
                                .background(p.card.copy(alpha = if (p.dark) 0.5f else 0.55f))
                                .border(2.dp, borderColor, RoundedCornerShape(rpx(18)))
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
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = active,
                                    enter = fadeIn(tween(150)) +
                                        scaleIn(tween(200, easing = Motion.Out), initialScale = 0.5f),
                                    exit = fadeOut(tween(120)) +
                                        scaleOut(tween(150, easing = Motion.InOut), targetScale = 0.7f),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(rpx(8))
                                ) {
                                    Box(
                                        Modifier
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
            }

            RiseIn(2, key = "hero") {
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
            }

            RiseIn(3, key = "auto") {
            GroupTitle("自动记账")
            SettingGroup {
                SettingCell(
                    icon = "shield",
                    gradient = Brush.linearGradient(p.gradPrimary),
                    title = "无障碍服务",
                    state = if (status.accessibility) "已开启" to true else "未开启" to false,
                    desc = "自动记账的核心开关。开启后，你在支付宝/微信等App里完成支付时，孽账会读取支付结果页的文字，在本地解析出待确认账单。",
                    action = if (status.accessibility) null else "去开启",
                    onClick = { NativeStatus.openAccessibilitySettings(ctx) }
                )
                SettingCell(
                    icon = "bell",
                    gradient = Brush.linearGradient(p.gradSky),
                    title = "通知使用权（辅助）",
                    state = if (status.notificationListener) "已开启" to true else "未开启" to false,
                    desc = "补充无障碍覆盖不到的场景：收款到账提醒只在通知栏、熄屏碰卡支付的交易通知。两路来源自动去重，不会重复入账。",
                    action = if (status.notificationListener) null else "去开启",
                    onClick = { NativeStatus.openNotificationListenerSettings(ctx) }
                )
                SettingCell(
                    icon = "rotate-ccw",
                    gradient = Brush.linearGradient(p.gradViolet),
                    title = "无障碍自恢复",
                    state = if (status.repair.hasPermission) "已授权" to true else "未授权" to false,
                    desc = "系统可能杀掉无障碍服务导致自动记账失效。用电脑 adb 执行一次授权命令后，打开孽账会自动把服务重新开启，无需再去系统设置手动开。",
                    action = if (!status.repair.hasPermission) "授权教程" else if (!status.accessibility) "立即恢复" else null,
                    onClick = {
                        if (!status.repair.hasPermission) {
                            val pkg = status.repair.packageName.ifEmpty { ctx.packageName }
                            val cmd = "adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS"
                            app.confirm(
                                "开启无障碍自恢复",
                                "手机连电脑允许 USB 调试后执行一次，永久生效：$cmd\n\n小米/OPPO/vivo 等机型需先在开发者选项开启「USB 调试（安全设置）」，否则命令会报 GRANT_RUNTIME_PERMISSIONS 权限错误。",
                                "复制命令"
                            ) {
                                app.copyText(cmd)
                                app.showToast("已复制，执行后回到本页刷新")
                            }
                        } else if (!status.accessibility) {
                            val result = NativeStatus.repairAccessibility(ctx)
                            if (result.success && result.serviceEnabled) {
                                app.showToast("已恢复开启无障碍服务")
                            } else {
                                app.showToast("恢复失败：${result.error.ifEmpty { "未知原因" }}")
                            }
                            refresh()
                        } else {
                            app.showToast("无障碍服务运行正常")
                        }
                    }
                )
                SettingCell(
                    icon = "maximize",
                    gradient = Brush.linearGradient(p.gradBlue),
                    title = "显示在其他应用上层",
                    state = if (status.overlay) "已允许" to true else "未允许" to false,
                    desc = "系统级悬浮窗开关，弹层要盖在支付页上方必须先允许。这里只开系统开关，不绘制悬浮窗。",
                    action = if (status.overlay) null else "去允许",
                    onClick = {
                        NativeStatus.requestOverlayPermission(ctx)
                        app.showToast("请允许“显示在其他应用上层”，返回后自动生效")
                    }
                )
                if (status.popup.miui) {
                    SettingCell(
                        icon = "shield",
                        gradient = Brush.linearGradient(p.gradViolet),
                        title = "后台弹出界面（MIUI）",
                        state = status.popup.text to status.popup.ok,
                        desc = "MIUI/HyperOS 单独的后台启动 Activity 开关，和上面的悬浮窗开关不是一个。点「去开启」后进入权限页，再点「其他权限」→「后台弹出界面」；未开启时弹层会被系统拦掉，只能收到待确认通知。",
                        action = if (status.popup.ok) null else "去开启",
                        onClick = {
                            NativeStatus.openPopupPermissionSettings(ctx)
                            app.showToast("点「其他权限」→「后台弹出界面」")
                        }
                    )
                }
                SettingCell(
                    icon = "bell",
                    gradient = Brush.linearGradient(p.gradRose),
                    title = "App 弹通知权限",
                    state = if (status.notificationsAllowed) "已开启" to true else "未开启" to false,
                    desc = "Android 13+ 需要。用于后台保活常驻通知，以及确认弹层被系统拦住时的待确认提醒；关闭后这两类通知都收不到",
                    action = if (status.notificationsAllowed) null else "请求权限",
                    onClick = {
                        if (status.notificationsAllowed) app.showToast("通知权限已开启")
                        else notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }
            }

            RiseIn(4, key = "background") {
            GroupTitle("后台运行")
            SettingGroup {
                SettingCell(
                    icon = "battery-charging",
                    gradient = Brush.linearGradient(p.gradPrimary),
                    title = "电池优化",
                    state = if (status.batteryUnrestricted) "不限制" to true else "受限制" to false,
                    desc = "系统省电策略可能在锁屏后回收后台进程，导致自动记账失效。请把电池策略设为“不限制”。",
                    chevron = !status.batteryUnrestricted,
                    onClick = {
                        if (status.batteryUnrestricted) {
                            app.showToast("已不受电池优化限制")
                        } else {
                            app.confirm(
                                "关闭电池优化",
                                "在系统弹窗中选择“不限制”。部分机型还需在“应用详情”里把自启动/后台运行设为允许。",
                                "去设置"
                            ) {
                                NativeStatus.requestIgnoreBatteryOptimization(ctx)
                            }
                        }
                    }
                )
                SettingCell(
                    icon = "zap",
                    gradient = Brush.linearGradient(p.gradMint),
                    title = "后台保活",
                    state = if (status.keepAlive) "已开启" to true else "已关闭" to false,
                    desc = "用一条低优先级常驻通知维持进程，降低被系统回收的概率。关闭后仍可记账，但后台更容易被厂商省电策略杀掉。",
                    switch = status.keepAlive,
                    onSwitch = { value ->
                        if (NativeStatus.setKeepAlive(ctx, value)) {
                            refresh()
                        } else {
                            app.showToast("设置失败，请重试")
                        }
                    }
                )
                SettingCell(
                    icon = "layers",
                    gradient = Brush.linearGradient(p.gradBlue),
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
                        NativeStatus.openAppSettings(ctx)
                        app.showToast("请在应用详情中允许自启动/后台运行")
                    }
                )
            }
            }

            RiseIn(5, key = "privacy") {
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
                AppSwitch(switch, onChange = onSwitch)
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
