package com.muyian.debt.state

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.muyian.debt.data.GlassStyle
import com.muyian.debt.data.PrefKeys
import com.muyian.debt.data.Prefs

/** 应用级设置与刷新信号：最近任务可见性、玻璃材质、版本号、resume/账单版本。 */
class SettingsState(private val context: Context, private val prefs: Prefs) {
    var hideRecents by mutableStateOf(prefs.bool(PrefKeys.HIDE_RECENTS, true))
        private set

    var glassStyle by mutableStateOf(GlassStyle.fromKey(prefs.str(PrefKeys.GLASS_STYLE, GlassStyle.Frost.key)))
        private set

    /** 首页吸顶导航是否转成实底，由首页滚动状态写入、MainActivity 读取。 */
    var homeNavSolid by mutableStateOf(false)

    /** 每次 Activity onResume 自增：页面用它触发数据重载。 */
    var resumeToken by mutableIntStateOf(0)
        private set

    /** 账单写库版本：新增/编辑/删除/导入/恢复后自增，页面据此刷新。 */
    var billsVersion by mutableIntStateOf(0)
        private set

    fun onResume() {
        resumeToken++
    }

    fun bumpBills() {
        billsVersion++
    }

    fun updateHideRecents(hidden: Boolean) {
        hideRecents = hidden
        prefs.putBool(PrefKeys.HIDE_RECENTS, hidden)
        applyRecentsVisibility()
    }

    fun updateGlassStyle(style: GlassStyle) {
        glassStyle = style
        prefs.putStr(PrefKeys.GLASS_STYLE, style.key)
    }

    fun applyRecentsVisibility() {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (task in am.appTasks) task.setExcludeFromRecents(hideRecents)
        } catch (_: Exception) {
        }
    }

    val versionName: String
        get() = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.1.0"
        } catch (_: Exception) {
            "0.1.0"
        }
}
