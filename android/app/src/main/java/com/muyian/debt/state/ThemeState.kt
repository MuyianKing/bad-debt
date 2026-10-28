package com.muyian.debt.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.muyian.debt.data.LauncherIcon
import com.muyian.debt.data.Mascot
import com.muyian.debt.data.MascotStyle
import com.muyian.debt.data.Mascots
import com.muyian.debt.data.PrefKeys
import com.muyian.debt.data.Prefs
import com.muyian.debt.ui.theme.CUSTOM_INK_AUTO
import com.muyian.debt.ui.theme.CUSTOM_THEME_ID
import com.muyian.debt.ui.theme.DEFAULT_CUSTOM_COLOR
import com.muyian.debt.ui.theme.Palette
import com.muyian.debt.ui.theme.normalizeCustomColor
import com.muyian.debt.ui.theme.normalizeInkMode
import com.muyian.debt.ui.theme.paletteOf
import muyian.bill.listener.MuyianBillCore

/** 主题、角色与背景图。只影响外观，不参与导航或账单数据。 */
class ThemeState(private val context: Context, private val prefs: Prefs) {
    /** 自定义主题的主色；未设置时用默认蓝。 */
    var customColor by mutableStateOf(
        normalizeCustomColor(prefs.str(PrefKeys.CUSTOM_COLOR, DEFAULT_CUSTOM_COLOR))
            ?: DEFAULT_CUSTOM_COLOR
    )
        private set

    /** 自定义主题的字色：auto 自动判断，white / dark 手动指定。 */
    var customInk by mutableStateOf(
        normalizeInkMode(prefs.str(PrefKeys.CUSTOM_INK, CUSTOM_INK_AUTO))
    )
        private set

    var themeId by mutableStateOf(run {
        val id = prefs.str(PrefKeys.THEME, "wukong")
        if (paletteOf(id, customColor, customInk).id == id) id else "wukong"
    })
        private set

    val palette: Palette get() = paletteOf(themeId, customColor, customInk)
    val isDark: Boolean get() = palette.dark
    val activeMascot: Mascot get() = Mascots.active(themeId)

    var mascotStyles by mutableStateOf(Mascots.readAllStyleIds(prefs))
        private set
    var autoMood by mutableStateOf(Mascots.readAutoMood(prefs))
        private set
    var bgImage by mutableStateOf(prefs.str(PrefKeys.GLASS_BG, ""))
        private set

    val baseMascotStyle: MascotStyle
        get() = Mascots.styleOf(activeMascot, mascotStyles[activeMascot.id] ?: activeMascot.defaultStyleId)

    fun mascotStyle(mood: String, mode: String = "auto"): MascotStyle {
        val id = mascotStyles[activeMascot.id] ?: activeMascot.defaultStyleId
        return Mascots.resolve(activeMascot, id, mode != "base" && autoMood, mood)
    }

    fun setTheme(id: String) {
        val p = paletteOf(id, customColor, customInk)
        themeId = p.id
        prefs.putStr(PrefKeys.THEME, p.id)
        applyCardTheme()
    }

    /** 应用自定义主色与字色；非法主色直接忽略。 */
    fun updateCustomColor(raw: String, inkMode: String = customInk) {
        val hex = normalizeCustomColor(raw) ?: return
        customColor = hex
        customInk = normalizeInkMode(inkMode)
        prefs.putStr(PrefKeys.CUSTOM_COLOR, hex)
        prefs.putStr(PrefKeys.CUSTOM_INK, customInk)
        setTheme(CUSTOM_THEME_ID)
    }

    fun applyCardTheme() {
        // 原生弹层只认字面色，且保存键固定白字：主色偏亮时改用压暗后的 primaryDeep
        val argb = when {
            palette.dark -> 0xFF1A1A1D.toInt()
            palette.inkOnPrimary == Color.White -> palette.primaryFill.toArgb()
            else -> palette.primaryDeep.toArgb()
        }
        val hex = String.format("#%06X", argb and 0xFFFFFF)
        try {
            MuyianBillCore.setCardThemeColor(context, hex)
        } catch (_: Exception) {
        }
    }

    fun setMascotStyle(styleId: String): MascotStyle {
        val target = Mascots.setStyle(prefs, activeMascot, styleId)
        mascotStyles = Mascots.readAllStyleIds(prefs)
        LauncherIcon.sync(context, target.id)
        return target
    }

    fun updateAutoMood(enabled: Boolean) {
        Mascots.setAutoMood(prefs, enabled)
        autoMood = enabled
    }

    fun updateBgImage(path: String) {
        bgImage = path
        prefs.putStr(PrefKeys.GLASS_BG, path)
    }
}
