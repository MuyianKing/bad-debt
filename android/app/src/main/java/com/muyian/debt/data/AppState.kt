package com.muyian.debt.data

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.muyian.debt.ui.theme.Palette
import com.muyian.debt.ui.theme.paletteOf
import muyian.bill.listener.MuyianBillCore

sealed class Screen {
    data object Home : Screen()
    data object Add : Screen()
    data object Stats : Screen()
    data object Calendar : Screen()
    data object Categories : Screen()
    data object Settings : Screen()
    data object Import : Screen()
    data object Backup : Screen()
}

data class AppDialog(
    val title: String,
    val content: String,
    val confirmText: String = "确定",
    val cancelText: String = "取消",
    val showCancel: Boolean = true,
    val onConfirm: (() -> Unit)? = null,
    val onCancel: (() -> Unit)? = null
)

data class ActionSheetState(
    val items: List<Pair<String, Boolean>>, // name to danger
    val onSelect: (Int) -> Unit
)

data class PickerState(
    val title: String,
    val options: List<Pair<String, String>>, // label to value
    val selectedValue: String,
    val onSelect: (String) -> Unit
)

data class DatePickerState(
    val mode: String, // YMD / YM / Y
    val year: Int,
    val month: Int,
    val day: Int,
    val onConfirm: (Int, Int, Int) -> Unit
)

class AppState(val context: Context) {
    val prefs = Prefs(context)

    var themeId by mutableStateOf(run {
        val id = prefs.str("muyian_theme_v1", "wukong")
        if (paletteOf(id).id == id) id else "wukong"
    })
        private set

    val palette: Palette get() = paletteOf(themeId)
    val isDark: Boolean get() = palette.dark
    val activeMascot: Mascot get() = Mascots.active(themeId)

    var categories by mutableStateOf(Cats.load(prefs))
        private set
    var mascotStyles by mutableStateOf(Mascots.readAllStyleIds(prefs))
        private set
    var autoMood by mutableStateOf(Mascots.readAutoMood(prefs))
        private set
    var heroImg by mutableStateOf(prefs.str("muyian_hero_img_v1", ""))
        private set
    var hideRecents by mutableStateOf(prefs.bool("muyian_hide_recents_v1", true))
        private set

    val baseMascotStyle: MascotStyle
        get() = Mascots.styleOf(activeMascot, mascotStyles[activeMascot.id] ?: activeMascot.defaultStyleId)

    fun mascotStyle(mood: String, mode: String = "auto"): MascotStyle {
        val id = mascotStyles[activeMascot.id] ?: activeMascot.defaultStyleId
        return Mascots.resolve(activeMascot, id, mode != "base" && autoMood, mood)
    }

    /* ---------- 主题 / 角色 ---------- */

    fun setTheme(id: String) {
        val p = paletteOf(id)
        themeId = p.id
        prefs.putStr("muyian_theme_v1", p.id)
        applyCardTheme()
    }

    fun applyCardTheme() {
        val argb = if (palette.dark) 0xFF1A1A1D.toInt() else (palette.primary.toArgb() or 0xFF000000.toInt())
        val hex = String.format("#%06X", argb and 0xFFFFFF)
        try {
            MuyianBillCore.setCardThemeColor(context, hex)
        } catch (_: Exception) {
        }
    }

    fun setMascotStyle(styleId: String): MascotStyle {
        val target = Mascots.setStyle(prefs, activeMascot, styleId)
        mascotStyles = Mascots.readAllStyleIds(prefs)
        return target
    }

    fun updateAutoMood(enabled: Boolean) {
        Mascots.setAutoMood(prefs, enabled)
        autoMood = enabled
    }

    /* ---------- 分类 ---------- */

    fun saveCategories(data: CategoryData) {
        categories = data
        Cats.save(prefs, data)
    }

    fun categoryData(): CategoryData = categories.deepCopy()

    /* ---------- 导航 ---------- */

    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()

    /** 最近一次导航方向：true 进栈（页面向上推入），false 出栈（页面向下滑出）。 */
    var navForward by mutableStateOf(true)
        private set

    fun navigate(screen: Screen) {
        navForward = true
        stack.add(screen)
    }

    fun pop() {
        if (stack.size > 1) {
            navForward = false
            stack.removeAt(stack.lastIndex)
        }
    }

    fun popToHome() {
        if (stack.size > 1) {
            navForward = false
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
    }

    var addDraft by mutableStateOf<AddDraft?>(null)
        private set

    fun openAdd(bill: Bill? = null) {
        addDraft = if (bill == null) {
            AddDraft()
        } else {
            val type = if (bill.isIncome) "income" else "expense"
            val list = categories.list(type)
            var topIndex = list.indexOfFirst { it.name == bill.costType }
            var subName = ""
            if (topIndex < 0) {
                topIndex = list.indexOfFirst { c -> c.subs.any { it.name == bill.costType } }
                if (topIndex >= 0) subName = bill.costType
            }
            if (topIndex < 0) topIndex = 0
            AddDraft(
                editId = bill.id.toString(),
                isIncome = bill.isIncome,
                selectedTopIndex = topIndex,
                selectedSub = subName,
                remark = bill.remarkText,
                expr = fmtMoneyInput(String.format("%.2f", Math.abs(bill.money))),
                reimb = bill.reimb == 1,
                starred = bill.starred == 1,
                dateValue = bill.dateText,
                imgPath = bill.imgPath
            )
        }
        navigate(Screen.Add)
    }

    fun updateDraft(draft: AddDraft) {
        addDraft = draft
    }

    fun finishAdd() {
        addDraft = null
        pop()
    }

    /* ---------- 弹层 ---------- */

    var toastText by mutableStateOf("")
        private set
    var toastToken by mutableStateOf(0)
        private set

    fun showToast(text: String) {
        toastText = text
        toastToken++
    }

    var dialog by mutableStateOf<AppDialog?>(null)
    var actionSheet by mutableStateOf<ActionSheetState?>(null)
    var picker by mutableStateOf<PickerState?>(null)
    var datePicker by mutableStateOf<DatePickerState?>(null)
    var previewImage by mutableStateOf<String?>(null)

    fun confirm(
        title: String,
        content: String,
        confirmText: String = "确定",
        cancelText: String = "取消",
        onConfirm: () -> Unit
    ) {
        dialog = AppDialog(title, content, confirmText, cancelText, true, onConfirm)
    }

    fun alert(title: String, content: String, confirmText: String = "知道了") {
        dialog = AppDialog(title, content, confirmText, "", false)
    }

    fun showDatePicker(mode: String, year: Int, month: Int, day: Int, onConfirm: (Int, Int, Int) -> Unit) {
        datePicker = DatePickerState(mode, year, month, day, onConfirm)
    }

    fun showPicker(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
        picker = PickerState(title, options, selected, onSelect)
    }

    /* ---------- 杂项 ---------- */

    val versionName: String
        get() = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.1.0"
        } catch (_: Exception) {
            "0.1.0"
        }

    fun copyText(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("muyian", text))
    }

    fun updateHideRecents(hidden: Boolean) {
        hideRecents = hidden
        prefs.putBool("muyian_hide_recents_v1", hidden)
        applyRecentsVisibility()
    }

    fun applyRecentsVisibility() {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (task in am.appTasks) task.setExcludeFromRecents(hideRecents)
        } catch (_: Exception) {
        }
    }

    fun updateHeroImg(path: String) {
        heroImg = path
        prefs.putStr("muyian_hero_img_v1", path)
    }

    var darkStatusIcons by mutableStateOf(!isDark)
    var homeNavSolid by mutableStateOf(false)

    fun invalidateStatusIcons() {
        darkStatusIcons = !isDark
    }

    /** 每次 Activity onResume 自增：页面用它触发数据重载（从系统设置或其他页面返回时）。 */
    var resumeToken by mutableStateOf(0)
        private set

    fun onResume() {
        resumeToken++
    }

    /** 首页选中的月份跨页面保留 */
    var homeYear by mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
    var homeMonth by mutableStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1)
}
