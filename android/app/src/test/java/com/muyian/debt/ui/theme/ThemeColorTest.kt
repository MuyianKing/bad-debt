package com.muyian.debt.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorTest {

    @Test
    fun customColorNormalization() {
        assertEquals("#4489FE", normalizeCustomColor("4489fe"))
        assertEquals("#4489FE", normalizeCustomColor("  #4489fe  "))
        assertNull(normalizeCustomColor("4489F"))
        assertNull(normalizeCustomColor("#GGGGGG"))
        assertNull(normalizeCustomColor(""))
    }

    @Test
    fun hsvRoundTrip() {
        val color = hex("#4489FE")
        val hsv = colorToHsv(color)
        val back = hsvColor(hsv[0], hsv[1], hsv[2])
        assertEquals(colorHex(color), colorHex(back))
    }

    @Test
    fun brightPrimaryUsesDarkInk() {
        assertEquals(inkForPrimary(hex("#FFE58F")), inkForPrimary(hex("#FFFFFF")))
        assertTrue(isBrightPrimary(hex("#FFE58F")))
        assertTrue(isBrightPrimary(hex("#FFEB3B")))
    }

    @Test
    fun darkPrimaryUsesWhiteInk() {
        assertEquals(Color.White, inkForPrimary(hex("#1A2B4C")))
        assertEquals(Color.White, inkForPrimary(hex("#4489FE")))
    }

    @Test
    fun derivedDeepStaysReadableOnLightCards() {
        for (raw in listOf("#FFE58F", "#4489FE", "#22B877", "#FFFFFF", "#F7A600")) {
            val p = customPalette(raw)
            assertTrue(
                "primaryDeep of $raw must read on white",
                contrastRatio(p.primaryDeep, Color.White) >= 3.2f
            )
            assertEquals(hex(raw), p.primary)
            assertTrue(contrastRatio(p.bg, p.text) >= 3f)
        }
    }

    @Test
    fun customPaletteIdAndInkDriveButtons() {
        val bright = customPalette("#FFE58F")
        assertEquals(CUSTOM_THEME_ID, bright.id)
        assertEquals(bright.inkOnPrimary, inkForPrimary(bright.primaryFill))

        val dark = customPalette("#1A2B4C")
        assertEquals(Color.White, dark.inkOnPrimary)
        assertNotNull(dark.tagline)
    }

    @Test
    fun manualInkOverridesAutoChoice() {
        // 亮主色默认配深字，但用户可以手动改成白字
        assertTrue(isBrightPrimary(hex("#FFE58F")))
        assertEquals(Color.White, customPalette("#FFE58F", CUSTOM_INK_WHITE).inkOnPrimary)
        assertEquals(Color.White, customPalette("#FFE58F", CUSTOM_INK_WHITE).onPrimarySub.copy(alpha = 1f))

        val darkInk = inkForCustom(hex("#1A2B4C"), CUSTOM_INK_DARK)
        assertEquals(darkInk, customPalette("#1A2B4C", CUSTOM_INK_DARK).inkOnPrimary)
        assertEquals(darkInk, paletteOf(CUSTOM_THEME_ID, "#1A2B4C", CUSTOM_INK_DARK).inkOnPrimary)
    }

    @Test
    fun inkModeNormalization() {
        assertEquals(CUSTOM_INK_AUTO, normalizeInkMode("nonsense"))
        assertEquals(CUSTOM_INK_AUTO, normalizeInkMode(""))
        assertEquals(CUSTOM_INK_WHITE, normalizeInkMode(" WHITE "))
        assertEquals(CUSTOM_INK_DARK, normalizeInkMode("Dark"))
    }
}
