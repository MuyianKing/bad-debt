package com.muyian.debt.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

/** 自定义主题色取色器：初始主色/字色 + 确认回调。 */
data class ColorPickerState(
    val initial: String,
    val initialInk: String,
    val onConfirm: (color: String, ink: String) -> Unit
)

/** 全局弹层与 Toast：页面只改状态位，由 MainActivity 的 Host 统一渲染。 */
class OverlayState {
    var toastText by mutableStateOf("")
        private set
    var toastToken by mutableIntStateOf(0)
        private set

    fun showToast(text: String) {
        toastText = text
        toastToken++
    }

    var dialog by mutableStateOf<AppDialog?>(null)
    var actionSheet by mutableStateOf<ActionSheetState?>(null)
    var picker by mutableStateOf<PickerState?>(null)
    var datePicker by mutableStateOf<DatePickerState?>(null)
    var colorPicker by mutableStateOf<ColorPickerState?>(null)
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

    fun showColorPicker(initial: String, initialInk: String, onConfirm: (String, String) -> Unit) {
        colorPicker = ColorPickerState(initial, initialInk, onConfirm)
    }

    fun showPicker(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
        picker = PickerState(title, options, selected, onSelect)
    }
}
