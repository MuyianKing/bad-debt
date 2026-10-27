package com.muyian.debt.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import muyian.bill.listener.MuyianBillCore
import org.json.JSONObject

data class PopupPermissionState(
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

data class SelfRepairState(
    val hasPermission: Boolean = false,
    val packageName: String = ""
)

data class NativeStatusSnapshot(
    val accessibility: Boolean = false,
    val notificationListener: Boolean = false,
    val notificationsAllowed: Boolean = true,
    val overlay: Boolean = false,
    val popup: PopupPermissionState = PopupPermissionState(),
    val batteryUnrestricted: Boolean = true,
    val keepAlive: Boolean = true,
    val repair: SelfRepairState = SelfRepairState()
)

data class RepairResult(
    val success: Boolean,
    val serviceEnabled: Boolean,
    val error: String = ""
)

/** 系统能力状态与跳转的唯一封装：设置页不再直接拼原生 JSON。 */
object NativeStatus {
    fun snapshot(context: Context): NativeStatusSnapshot {
        val popup = try {
            val o = JSONObject(MuyianBillCore.getPopupPermissionState(context))
            PopupPermissionState(
                miui = o.optBoolean("miui"),
                miuiPopup = if (o.has("miuiPopup")) o.optInt("miuiPopup") else -1,
                known = o.optBoolean("miuiPopupKnown"),
                blocked = o.optBoolean("blocked")
            )
        } catch (_: Exception) {
            PopupPermissionState()
        }
        val repair = try {
            val st = JSONObject(MuyianBillCore.getSelfRepairState(context))
            SelfRepairState(
                hasPermission = st.optBoolean("hasPermission"),
                packageName = st.optString("packageName", "")
            )
        } catch (_: Exception) {
            SelfRepairState()
        }
        return NativeStatusSnapshot(
            accessibility = runCatching { MuyianBillCore.isAccessibilityEnabled(context) }.getOrDefault(false),
            notificationListener = runCatching { MuyianBillCore.isNotificationListenerEnabled(context) }.getOrDefault(false),
            notificationsAllowed = runCatching { MuyianBillCore.areNotificationsEnabled(context) }.getOrDefault(true),
            overlay = runCatching { MuyianBillCore.hasOverlayPermission(context) }.getOrDefault(false),
            popup = popup,
            batteryUnrestricted = runCatching {
                MuyianBillCore.isIgnoringBatteryOptimizations(context)
            }.getOrDefault(true),
            keepAlive = runCatching { MuyianBillCore.isKeepAliveEnabled(context) }.getOrDefault(false),
            repair = repair
        )
    }

    fun setKeepAlive(context: Context, enabled: Boolean): Boolean =
        runCatching { MuyianBillCore.setKeepAliveEnabled(context, enabled) }.getOrDefault(false)

    fun repairAccessibility(context: Context): RepairResult = try {
        val resp = JSONObject(MuyianBillCore.repairAccessibilityService(context))
        RepairResult(
            success = resp.optBoolean("success"),
            serviceEnabled = resp.optBoolean("serviceEnabled"),
            error = resp.optString("error", "")
        )
    } catch (e: Exception) {
        RepairResult(false, false, e.javaClass.name)
    }

    fun requestOverlayPermission(context: Context): Boolean =
        runCatching { MuyianBillCore.requestOverlayPermission(context) }.getOrDefault(false)

    fun requestIgnoreBatteryOptimization(context: Context) {
        runCatching { MuyianBillCore.requestIgnoreBatteryOptimizations(context) }
    }

    fun openPopupPermissionSettings(context: Context): Boolean =
        runCatching { MuyianBillCore.openPopupPermissionSettings(context) }.getOrDefault(false)

    fun openAppSettings(context: Context): Boolean =
        runCatching { MuyianBillCore.openAppSettings(context) }.getOrDefault(false)

    fun openAccessibilitySettings(context: Context) {
        openSystemSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    fun openNotificationListenerSettings(context: Context) {
        openSystemSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    private fun openSystemSettings(context: Context, action: String) {
        runCatching {
            context.startActivity(
                Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
