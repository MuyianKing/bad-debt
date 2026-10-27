package com.muyian.debt.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import com.muyian.debt.data.Mascot
import com.muyian.debt.data.MascotStyle
import com.muyian.debt.data.Mascots
import com.muyian.debt.data.PrefKeys
import com.muyian.debt.data.Prefs
import com.muyian.debt.ui.theme.Palette
import com.muyian.debt.ui.theme.paletteOf
import muyian.bill.listener.MuyianBillCore

/** 主题、角色与首页头图。只影响外观，不参与导航或账单数据。 */
class ThemeState(private val context: Context, private val prefs: Prefs) {
    var themeId by mutableStateOf(run {
        val id = prefs.str(PrefKeys.THEME, "wukong")
        if (paletteOf(id).id == id) id else "wukong"
    })
        private set

    val palette: Palette get() = paletteOf(themeId)
    val isDark: Boolean get() = palette.dark
    val activeMascot: Mascot get() = Mascots.active(themeId)

    var mascotStyles by mutableStateOf(Mascots.readAllStyleIds(prefs))
        private set
    var autoMood by mutableStateOf(Mascots.readAutoMood(prefs))
        private set
    var heroImg by mutableStateOf(prefs.str(PrefKeys.HERO_IMG, ""))
        private set

    val baseMascotStyle: MascotStyle
        get() = Mascots.styleOf(activeMascot, mascotStyles[activeMascot.id] ?: activeMascot.defaultStyleId)

    fun mascotStyle(mood: String, mode: String = "auto"): MascotStyle {
        val id = mascotStyles[activeMascot.id] ?: activeMascot.defaultStyleId
        return Mascots.resolve(activeMascot, id, mode != "base" && autoMood, mood)
    }

    fun setTheme(id: String) {
        val p = paletteOf(id)
        themeId = p.id
        prefs.putStr(PrefKeys.THEME, p.id)
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

    fun updateHeroImg(path: String) {
        heroImg = path
        prefs.putStr(PrefKeys.HERO_IMG, path)
    }
}
