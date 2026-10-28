package com.muyian.debt.state

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.muyian.debt.data.Bill
import com.muyian.debt.data.Bills
import com.muyian.debt.data.CategoryData
import com.muyian.debt.data.Mascot
import com.muyian.debt.data.MascotStyle
import com.muyian.debt.data.NativeStatus
import com.muyian.debt.data.Prefs
import com.muyian.debt.data.fmtMoney
import com.muyian.debt.ui.theme.Palette
import kotlin.math.abs

/**
 * 应用级状态的门面：内部按主题/分类/导航/弹层/设置拆成独立子状态，
 * 页面仍可通过原有属性名访问，避免一次改动扩散到所有调用点。
 */
class AppState(val context: Context) {
    val prefs = Prefs(context)

    val theme = ThemeState(context, prefs)
    private val categoryStore = CategoryState(prefs)
    val nav = NavState(categoryStore)
    val overlay = OverlayState()
    val settings = SettingsState(context, prefs)

    /* ---------- 主题 / 角色 ---------- */
    val palette: Palette get() = theme.palette
    val isDark: Boolean get() = theme.isDark
    val activeMascot: Mascot get() = theme.activeMascot
    val mascotStyles get() = theme.mascotStyles
    val autoMood get() = theme.autoMood
    val bgImage get() = theme.bgImage
    val customColor get() = theme.customColor
    val customInk get() = theme.customInk
    val baseMascotStyle: MascotStyle get() = theme.baseMascotStyle

    fun mascotStyle(mood: String, mode: String = "auto"): MascotStyle = theme.mascotStyle(mood, mode)
    fun setTheme(id: String) = theme.setTheme(id)
    fun setCustomColor(hex: String, ink: String = customInk) = theme.updateCustomColor(hex, ink)
    fun applyCardTheme() = theme.applyCardTheme()
    fun setMascotStyle(styleId: String): MascotStyle = theme.setMascotStyle(styleId)
    fun updateAutoMood(enabled: Boolean) = theme.updateAutoMood(enabled)
    fun updateBgImage(path: String) = theme.updateBgImage(path)

    /* ---------- 分类 ---------- */
    val categories: CategoryData get() = categoryStore.categories
    fun categoryData(): CategoryData = categoryStore.snapshot()
    fun saveCategories(data: CategoryData) = categoryStore.save(data)

    /* ---------- 导航 ---------- */
    val stack get() = nav.stack
    val current get() = nav.current
    val navForward get() = nav.navForward
    val navMotion get() = nav.navMotion
    fun navigate(screen: Screen, motion: NavMotion = NavMotion.Vertical) = nav.navigate(screen, motion)
    fun pop() = nav.pop()
    fun popToHome() = nav.popToHome()
    val addDraft get() = nav.addDraft
    fun openAdd(bill: Bill? = null) = nav.openAdd(bill)
    fun updateDraft(draft: com.muyian.debt.data.AddDraft) = nav.updateDraft(draft)
    fun finishAdd() = nav.finishAdd()

    var homeYear: Int
        get() = nav.homeYear
        set(value) { nav.homeYear = value }
    var homeMonth: Int
        get() = nav.homeMonth
        set(value) { nav.homeMonth = value }

    /* ---------- 弹层 ---------- */
    val toastText get() = overlay.toastText
    val toastToken get() = overlay.toastToken
    fun showToast(text: String) = overlay.showToast(text)

    var dialog: AppDialog?
        get() = overlay.dialog
        set(value) { overlay.dialog = value }
    var actionSheet: ActionSheetState?
        get() = overlay.actionSheet
        set(value) { overlay.actionSheet = value }
    var picker: PickerState?
        get() = overlay.picker
        set(value) { overlay.picker = value }
    var datePicker: DatePickerState?
        get() = overlay.datePicker
        set(value) { overlay.datePicker = value }
    var colorPicker: ColorPickerState?
        get() = overlay.colorPicker
        set(value) { overlay.colorPicker = value }
    var previewImage: String?
        get() = overlay.previewImage
        set(value) { overlay.previewImage = value }

    fun confirm(
        title: String,
        content: String,
        confirmText: String = "确定",
        cancelText: String = "取消",
        onConfirm: () -> Unit
    ) = overlay.confirm(title, content, confirmText, cancelText, onConfirm)

    fun alert(title: String, content: String, confirmText: String = "知道了") =
        overlay.alert(title, content, confirmText)

    fun showDatePicker(mode: String, year: Int, month: Int, day: Int, onConfirm: (Int, Int, Int) -> Unit) =
        overlay.showDatePicker(mode, year, month, day, onConfirm)

    fun showPicker(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) =
        overlay.showPicker(title, options, selected, onSelect)

    fun showColorPicker(initial: String, initialInk: String, onConfirm: (String, String) -> Unit) =
        overlay.showColorPicker(initial, initialInk, onConfirm)

    /* ---------- 设置 / 刷新 ---------- */
    val hideRecents get() = settings.hideRecents
    val glassStyle get() = settings.glassStyle
    var homeNavSolid: Boolean
        get() = settings.homeNavSolid
        set(value) { settings.homeNavSolid = value }
    val resumeToken get() = settings.resumeToken
    val billsVersion get() = settings.billsVersion
    val versionName get() = settings.versionName

    fun updateHideRecents(hidden: Boolean) = settings.updateHideRecents(hidden)
    fun updateGlassStyle(style: com.muyian.debt.data.GlassStyle) = settings.updateGlassStyle(style)
    fun applyRecentsVisibility() = settings.applyRecentsVisibility()
    fun onResume() {
        // 覆盖安装/强停会触发 HyperOS 移除无障碍服务，先自愈再让页面刷新状态。
        NativeStatus.repairAccessibilityIfNeeded(context)
        settings.onResume()
    }
    fun bumpBills() = settings.bumpBills()

    fun copyText(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("muyian", text))
    }

    /* ---------- 账单行操作 ---------- */
    fun requestDeleteBill(bill: Bill, onDeleted: () -> Unit = {}) {
        confirm(
            "删除账单",
            "删除这笔「${bill.costType} ${fmtMoney(abs(bill.money))}元」的记录？",
            "删除"
        ) {
            if (Bills.delete(context, bill.id)) {
                bumpBills()
                onDeleted()
            } else {
                showToast("删除失败")
            }
        }
    }

    fun showBillActions(bill: Bill, onDeleted: () -> Unit = {}) {
        actionSheet = ActionSheetState(listOf("编辑" to false, "删除" to true)) { index ->
            if (index == 0) openAdd(bill) else requestDeleteBill(bill, onDeleted)
        }
    }
}
