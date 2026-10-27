package muyian.bill.listener

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.util.Locale

/** 权限、省电、自愈与弹层主题色：不参与账单解析，只读系统状态或拉起系统页面。 */
internal object SystemPerms {
    private const val TAG = "MuyianBill"
    private const val PREFS_NAME = "muyian_bill"
    private const val KEY_KEEP_ALIVE = "keep_alive_enabled"
    private const val KEY_CARD_THEME = "card_theme_color"
    private const val KEY_POPUP_BLOCKED_AT = "popup_blocked_at"
    private const val MIUI_POPUP_OP = 10021
    private const val POPUP_BLOCKED_TTL_MS = 7 * 24 * 3600 * 1000L
    private val DEFAULT_CARD_THEME = 0xFFF7A600.toInt()

/** 后台保活默认开启：自动记账依赖进程存活，用户可在设置页关闭 */
@JvmStatic
fun isKeepAliveEnabled(context: Context): Boolean {
    return try {
        prefs(context).getBoolean(KEY_KEEP_ALIVE, true)
    } catch (e: Exception) {
        true
    }
}

/** 开关后台保活前台服务，返回是否设置成功 */
@JvmStatic
fun setKeepAliveEnabled(context: Context, enabled: Boolean): Boolean {
    return try {
        prefs(context).edit().putBoolean(KEY_KEEP_ALIVE, enabled).apply()
        if (enabled) MuyianKeepAliveService.start(context) else MuyianKeepAliveService.stop(context)
        true
    } catch (e: Exception) {
        Log.w(TAG, "set keep alive failed", e)
        false
    }
}

/**
 * MIUI/HyperOS 的"后台弹出界面"开关（对应系统的"显示在其他应用上层"）。
 * 确认弹层是 Activity，不用这个权限绘制任何窗口；但 HyperOS 把"后台启动 Activity"
 * 绑在这个 appop 上，未允许时 startActivity 会被 ActivityTaskManager 直接 Abort。
 */
@JvmStatic
fun hasOverlayPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 23) return true
    return try {
        Settings.canDrawOverlays(context)
    } catch (e: Exception) {
        false
    }
}

/** 打开系统的"显示在其他应用上层"授权页 */
@JvmStatic
fun requestOverlayPermission(context: Context): Boolean {
    return try {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "open overlay settings failed", e)
        false
    }
}

/**
 * 弹层弹出权限状态：
 * overlay = "显示在其他应用上层"（SYSTEM_ALERT_WINDOW）；
 * miuiPopup = MIUI/HyperOS 的「后台弹出界面」AppOp 10021（0=允许 1=忽略 2=拒绝 3=默认），
 *   known=false 表示系统不提供或隐藏 API 被拦截；
 * blocked = 最近 7 天确实发生过"亮屏但弹层被系统拦住"。
 */
@JvmStatic
fun getPopupPermissionState(context: Context): String {
    val o = JSONObject()
    o.put("miui", isMiuiDevice())
    o.put("overlay", hasOverlayPermission(context))
    var known = false
    var mode = -1
    try {
        val am = context.getSystemService("appops") as AppOpsManager
        // 标准 SDK 只有 String 版；MIUI 的 op 10021 没有字符串名，只能走隐藏的 int 版
        val method = AppOpsManager::class.java.getDeclaredMethod(
            "checkOpNoThrow", Integer.TYPE, Integer.TYPE, String::class.java
        )
        method.isAccessible = true
        mode = method.invoke(am, MIUI_POPUP_OP, Process.myUid(), context.packageName) as Int
        known = true
    } catch (e: Exception) {
        Log.w(TAG, "query miui popup op failed", e)
    }
    o.put("miuiPopup", mode)
    o.put("miuiPopupKnown", known)
    o.put("blocked", isPopupBlocked(context))
    return o.toString()
}

/** 小米/Redmi/POCO：只有这些设备才有 MIUI 的「后台弹出界面」AppOp 10021 */
private fun isMiuiDevice(): Boolean {
    val brand = Build.BRAND.lowercase(Locale.US)
    val manufacturer = Build.MANUFACTURER.lowercase(Locale.US)
    return brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") ||
        manufacturer.contains("xiaomi")
}

/** 打开 MIUI 权限编辑页（列表里有「后台弹出界面」）；非 MIUI 退回应用详情页 */
@JvmStatic
fun openPopupPermissionSettings(context: Context): Boolean {
    return try {
        val intent = Intent("miui.intent.action.APP_PERM_EDITOR")
        intent.putExtra("extra_pkgname", context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "open miui permission editor failed", e)
        openAppSettings(context)
    }
}

/** 亮屏但弹层没起来（ROM 拦截后台 Activity）时记一笔，设置页据此提示 */
internal fun markPopupBlocked(context: Context) {
    try {
        prefs(context).edit().putLong(KEY_POPUP_BLOCKED_AT, System.currentTimeMillis()).apply()
    } catch (e: Exception) {
        Log.w(TAG, "mark popup blocked failed", e)
    }
}

/** 弹层成功拉起后清掉拦截标记，避免权限已开还长期显示警告 */
@JvmStatic
fun clearPopupBlocked(context: Context) {
    try {
        prefs(context).edit().remove(KEY_POPUP_BLOCKED_AT).apply()
    } catch (e: Exception) {
        Log.w(TAG, "clear popup blocked failed", e)
    }
}

private fun isPopupBlocked(context: Context): Boolean {
    return try {
        val at = prefs(context).getLong(KEY_POPUP_BLOCKED_AT, 0L)
        at > 0 && System.currentTimeMillis() - at < POPUP_BLOCKED_TTL_MS
    } catch (e: Exception) {
        false
    }
}

/**
 * 底部确认弹层的主题色：Activity 不认前端 CSS 变量，由页面切换主题时传字面色进来。
 * color 只接受 #RRGGBB，非法值返回 false 并保留旧色。
 */
@JvmStatic
fun setCardThemeColor(context: Context, color: String): Boolean {
    val parsed = try {
        Color.parseColor(if (color.startsWith("#")) color else "#$color")
    } catch (e: IllegalArgumentException) {
        null
    }
    if (parsed == null) return false
    return try {
        prefs(context).edit().putInt(KEY_CARD_THEME, parsed).apply()
        true
    } catch (e: Exception) {
        Log.w(TAG, "set card theme failed", e)
        false
    }
}

@JvmStatic
fun getCardThemeColor(context: Context): Int {
    return try {
        prefs(context).getInt(KEY_CARD_THEME, DEFAULT_CARD_THEME)
    } catch (e: Exception) {
        DEFAULT_CARD_THEME
    }
}

/** 电池优化白名单状态：true 表示系统不会因省电策略限制本应用后台 */
@JvmStatic
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 23) return true
    return try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (e: Exception) {
        false
    }
}

/**
 * 请求把本应用加入电池优化白名单，返回 {"success":bool,"granted":bool}。
 * 优先弹系统的"不限制"确认框；部分 ROM 不支持该 Intent 时退化为电池优化列表页。
 */
@JvmStatic
fun requestIgnoreBatteryOptimizations(context: Context): String {
    val result = JSONObject()
    if (Build.VERSION.SDK_INT < 23 || isIgnoringBatteryOptimizations(context)) {
        result.put("success", true).put("granted", true)
        return result.toString()
    }
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:" + context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        result.put("success", true).put("granted", false)
        return result.toString()
    } catch (e: Exception) {
        Log.w(TAG, "request ignore battery optimizations failed", e)
    }
    return try {
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(fallback)
        result.put("success", true).put("granted", false).put("fallback", true)
        result.toString()
    } catch (e: Exception) {
        Log.w(TAG, "open battery optimization settings failed", e)
        result.put("success", false).put("granted", false).toString()
    }
}

/** 打开本应用系统详情页：国产 ROM 的自启动/后台管理入口通常在这里 */
@JvmStatic
fun openAppSettings(context: Context): Boolean {
    return try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:" + context.packageName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(TAG, "open app settings failed", e)
        false
    }
}

@JvmStatic
fun isAccessibilityEnabled(context: Context): Boolean {
    return try {
        val expected = ComponentName(context, MuyianBillAccessibilityService::class.java)
        val raw = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val flat = expected.flattenToString()
        val shortFlat = expected.flattenToShortString()
        raw.split(":").any {
            it == flat || it == shortFlat || ComponentName.unflattenFromString(it) == expected
        }
    } catch (e: Exception) {
        false
    }
}

@JvmStatic
fun isNotificationListenerEnabled(context: Context): Boolean {
    return try {
        val raw = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        )
        if (raw == null) false
        else raw.split(":").any { it.substringBefore('/') == context.packageName }
    } catch (e: Exception) {
        false
    }
}

/** App 级通知开关（Android 13+ 的 POST_NOTIFICATIONS 也体现在这里）；异常时按已开启处理，避免误报 */
@JvmStatic
fun areNotificationsEnabled(context: Context): Boolean {
    return try {
        if (Build.VERSION.SDK_INT < 24) true
        else {
            val nm = context.getSystemService("notification") as NotificationManager
            nm.areNotificationsEnabled()
        }
    } catch (e: Exception) {
        true
    }
}

/** WRITE_SECURE_SETTINGS 是否已通过 adb 授予：授权后 App 可自行开关无障碍服务（掉开关自愈） */
@JvmStatic
fun hasWriteSecureSettings(context: Context): Boolean {
    return try {
        context.checkPermission(
            "android.permission.WRITE_SECURE_SETTINGS",
            Process.myPid(), Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }
}

/**
 * 自恢复状态查询，返回 {"hasPermission":bool,"serviceEnabled":bool,"packageName":"..."}。
 * packageName 供设置页拼接 adb 授权命令展示。
 */
@JvmStatic
fun getSelfRepairState(context: Context): String {
    val o = JSONObject()
    o.put("hasPermission", hasWriteSecureSettings(context))
    o.put("serviceEnabled", isAccessibilityEnabled(context))
    o.put("packageName", context.packageName)
    return o.toString()
}

/**
 * 无障碍服务自恢复：已授予 WRITE_SECURE_SETTINGS 时，把本应用无障碍服务
 * 追加回系统"已启用无障碍服务"列表并打开总开关，免用户去系统设置手动重开。
 * 只追加本应用的条目，不改动其他应用已注册的服务。
 * 返回 {"success":bool,"serviceEnabled":bool,"error"?:string}
 */
@JvmStatic
fun repairAccessibilityService(context: Context): String {
    val result = JSONObject()
    if (!hasWriteSecureSettings(context)) {
        result.put("success", false).put("error", "no permission")
        return result.toString()
    }
    var ok = false
    try {
        val cn = ComponentName(context, MuyianBillAccessibilityService::class.java)
        ok = appendEnabledService(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, cn)
        if (ok) {
            Settings.Secure.putInt(
                context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1
            )
        }
    } catch (e: Exception) {
        Log.e(TAG, "repair accessibility service failed", e)
        ok = false
    }
    result.put("success", ok)
    result.put("serviceEnabled", isAccessibilityEnabled(context))
    if (!ok) result.put("error", "write failed")
    return result.toString()
}

/** 把一个 ComponentName 追加进 secure settings 的冒号分隔服务列表（已在列表则不动，视为成功） */
private fun appendEnabledService(context: Context, key: String, cn: ComponentName): Boolean {
    return try {
        val flat = cn.flattenToString()
        val shortFlat = cn.flattenToShortString()
        val raw = Settings.Secure.getString(context.contentResolver, key) ?: ""
        val parts = ArrayList<String>()
        var exists = false
        for (p in raw.split(":")) {
            if (p.isBlank()) continue
            if (p == flat || p == shortFlat || ComponentName.unflattenFromString(p) == cn) {
                exists = true
            }
            parts.add(p)
        }
        if (exists) return true
        parts.add(flat)
        Settings.Secure.putString(context.contentResolver, key, parts.joinToString(":"))
        true
    } catch (e: Exception) {
        Log.e(TAG, "append enabled service failed", e)
        false
    }
}

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, 0)
}
