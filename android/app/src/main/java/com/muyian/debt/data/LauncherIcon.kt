package com.muyian.debt.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 桌面图标跟随哪吒基础表情：三个 Launcher activity-alias 互斥启用。
 * 只切换组件的 enabled 状态，不重启进程；桌面由系统刷新。
 */
object LauncherIcon {
    private const val FALLBACK_STYLE = "grin"
    private val ALIASES = mapOf(
        "grin" to "LauncherGrin",
        "cheer" to "LauncherCheer",
        "grumpy" to "LauncherGrumpy"
    )

    fun sync(context: Context, styleId: String) {
        val wanted = ALIASES[styleId] ?: ALIASES.getValue(FALLBACK_STYLE)
        val pm = context.packageManager
        val pkg = context.packageName
        for (alias in ALIASES.values) {
            val component = ComponentName(pkg, "$pkg.$alias")
            val state = if (alias == wanted) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            if (pm.getComponentEnabledSetting(component) == state) continue
            try {
                pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            } catch (_: Exception) {
            }
        }
    }
}
