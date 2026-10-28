package com.muyian.debt.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muyian.debt.R
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

fun hex(value: String, alpha: Float = 1f): Color {
    val raw = value.removePrefix("#")
    val v = raw.toLong(16)
    return if (raw.length == 8) {
        Color(
            red = ((v shr 16) and 0xFF) / 255f,
            green = ((v shr 8) and 0xFF) / 255f,
            blue = (v and 0xFF) / 255f,
            alpha = ((v shr 24) and 0xFF) / 255f
        )
    } else {
        Color(
            red = ((v shr 16) and 0xFF) / 255f,
            green = ((v shr 8) and 0xFF) / 255f,
            blue = (v and 0xFF) / 255f,
            alpha = alpha
        )
    }
}

/**
 * 设计令牌。切换主题只换一整个 Palette。
 */
data class Palette(
    val id: String,
    val name: String,
    val tagline: String,
    val dark: Boolean,
    val primary: Color,
    val primaryDeep: Color,
    val primaryLight: Color,
    val primaryWeak: Color,
    val primaryTint: Color,
    val primaryFill: Color,
    val primaryPress: Color,
    val inkOnPrimary: Color,
    val onPrimarySub: Color,
    val heroFrom: Color,
    val heroMid: Color,
    val heroTo: Color,
    val out: Color,
    val outDeep: Color,
    val outWeak: Color,
    val inn: Color,
    val inWeak: Color,
    val gold: Color,
    val blue: Color,
    val sky: Color,
    val violet: Color,
    val mint: Color,
    val rose: Color,
    val text: Color,
    val sub: Color,
    val dim: Color,
    val bg: Color,
    val card: Color,
    val line: Color,
    val lineSoft: Color,
    val fill: Color,
    val raised: Color,
    val mask: Color,
    val keyBg: Color,
    val keyPress: Color,
    val keyEdge: Color,
    val keyFg: Color,
    val bar: Color,
    val gradPrimary: List<Color>,
    val gradPrimaryStrong: List<Color>,
    val gradBlue: List<Color>,
    val gradSky: List<Color>,
    val gradViolet: List<Color>,
    val gradMint: List<Color>,
    val gradRose: List<Color>
)

/**
 * 液态玻璃模式的可读性修正：玻璃比毛玻璃透，叠上壁纸后原 sub/dim 灰阶容易糊在背景里。
 * 只加深这两档灰阶，主文字、强调色、底色不动；毛玻璃仍用原令牌。
 */
fun Palette.liquidReadable(): Palette = copy(
    sub = if (dark) hex("#B9B9C3") else hex("#5F6675"),
    dim = if (dark) hex("#8C8C97") else hex("#8A92A2")
)

/** 主色当细线/边框/小图标压在浅色卡片上时的安全色；很亮的主色退回 primaryDeep。 */
val Palette.accentOnLight: Color
    get() = if (!dark && relativeLuminance(primary) >= 0.72f) primaryDeep else primary

/* ---------- 自定义主题色：取色器与调色板共用同一套色彩换算 ---------- */

const val CUSTOM_THEME_ID = "custom"
const val DEFAULT_CUSTOM_COLOR = "#4489FE"

const val CUSTOM_INK_AUTO = "auto"
const val CUSTOM_INK_WHITE = "white"
const val CUSTOM_INK_DARK = "dark"

/** 叠在主色上的深色字。用主题文字色而不是纯黑，避免自定义亮色时显得发灰。 */
private val DarkInk = Color(0xFF1D2129)

fun normalizeInkMode(raw: String): String = when (raw.trim().lowercase(Locale.US)) {
    CUSTOM_INK_WHITE -> CUSTOM_INK_WHITE
    CUSTOM_INK_DARK -> CUSTOM_INK_DARK
    else -> CUSTOM_INK_AUTO
}

/** 主色偏亮时叠深色字，偏暗时叠浅色字；中间灰阶按对比度择优。 */
fun inkForPrimary(bg: Color): Color {
    if (relativeLuminance(bg) >= 0.55f) return DarkInk
    val onWhite = contrastRatio(bg, Color.White)
    return if (onWhite >= 3f || onWhite >= contrastRatio(bg, DarkInk)) Color.White else DarkInk
}

/** 自定义主题的字色：auto 自动判断，white/dark 由用户手动指定。 */
fun inkForCustom(bg: Color, mode: String): Color = when (normalizeInkMode(mode)) {
    CUSTOM_INK_WHITE -> Color.White
    CUSTOM_INK_DARK -> DarkInk
    else -> inkForPrimary(bg)
}

fun isBrightPrimary(color: Color): Boolean = inkForPrimary(color) == DarkInk

fun mixColor(a: Color, b: Color, ratio: Float): Color {
    val t = ratio.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f
    )
}

/** WCAG 相对亮度。 */
fun relativeLuminance(color: Color): Float {
    fun channel(v: Float): Float = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
    return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
}

fun contrastRatio(a: Color, b: Color): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/** #RRGGBB；非法输入返回 null，调用方保留旧值。 */
fun normalizeCustomColor(raw: String): String? {
    val v = raw.trim().removePrefix("#")
    if (v.length != 6 || v.any { it.digitToIntOrNull(16) == null }) return null
    return "#${v.uppercase(Locale.US)}"
}

fun colorHex(color: Color): String {
    fun channel(v: Float): Int = (v * 255f).roundToInt().coerceIn(0, 255)
    return String.format(Locale.US, "#%02X%02X%02X", channel(color.red), channel(color.green), channel(color.blue))
}

/** 取色器用：HSV -> Color（h 0..360，s/v 0..1）。 */
fun hsvColor(hue: Float, saturation: Float, value: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = v - c
    val rgb = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(rgb.first + m, rgb.second + m, rgb.third + m, 1f)
}

/** Color -> HSV，返回 [h(0..360), s(0..1), v(0..1)]。 */
fun colorToHsv(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val d = hi - lo
    val h = when {
        d == 0f -> 0f
        hi == r -> 60f * (((g - b) / d) % 6f)
        hi == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return floatArrayOf(if (h < 0f) h + 360f else h, if (hi == 0f) 0f else d / hi, hi)
}

/**
 * 自定义主题的完整调色板：主色只是入口，其余令牌按明度派生。
 * - `inkMode` 为 auto 时按主色明暗自动选字色，white/dark 时尊重用户手动选择。
 * - `primaryDeep` 会一直压暗到在浅色卡片上可读，保证设置项/标题这类小字不会糊。
 */
fun customPalette(raw: String, inkMode: String = CUSTOM_INK_AUTO): Palette {
    val clean = normalizeCustomColor(raw) ?: DEFAULT_CUSTOM_COLOR
    val primary = hex(clean)
    val deep = readableDeep(primary)
    val press = mixColor(deep, Color.Black, 0.14f)
    val light = mixColor(primary, Color.White, 0.32f)
    val ink = inkForCustom(primary, inkMode)
    val grad = if (ink == Color.White) {
        listOf(primary, mixColor(primary, Color.Black, 0.08f), deep)
    } else {
        listOf(light, primary, deep)
    }
    return Wukong.copy(
        id = CUSTOM_THEME_ID,
        name = "自定义",
        tagline = "我的主色 · $clean",
        primary = primary,
        primaryDeep = deep,
        primaryLight = light,
        primaryWeak = mixColor(primary, Color.White, 0.90f),
        primaryTint = primary.copy(alpha = 0.14f),
        primaryFill = primary,
        primaryPress = press,
        inkOnPrimary = ink,
        onPrimarySub = ink.copy(alpha = 0.82f),
        heroFrom = grad[0],
        heroMid = grad[1],
        heroTo = grad[2],
        bg = mixColor(primary, Color.White, 0.94f),
        bar = mixColor(primary, Color.White, 0.45f),
        gradPrimary = grad,
        gradPrimaryStrong = grad
    )
}

/** 压暗到与白底对比度 >= 3.2，保证 `primaryDeep` 当小字用时不虚。 */
private fun readableDeep(primary: Color): Color {
    var c = mixColor(primary, Color.Black, 0.18f)
    var guard = 0
    while (contrastRatio(c, Color.White) < 3.2f && guard < 40) {
        c = mixColor(c, Color.Black, 0.10f)
        guard++
    }
    return c
}

private val commonBlue = listOf(hex("#74ADFF"), hex("#4489FE"))
private val commonSky = listOf(hex("#4CC8FF"), hex("#00AAF5"))
private val commonViolet = listOf(hex("#B795FF"), hex("#9A6BFF"))
private val commonMint = listOf(hex("#5AD6A0"), hex("#22B877"))
private val commonRose = listOf(hex("#FF96A6"), hex("#FF6B81"))

val Wukong = Palette(
    id = "wukong",
    name = "悟空黄",
    tagline = "金箍棒 · 暖金",
    dark = false,
    primary = hex("#F7A600"),
    primaryDeep = hex("#D97F00"),
    primaryLight = hex("#FFC63F"),
    primaryWeak = hex("#FFF4DB"),
    primaryTint = hex("#F7A600", 0.14f),
    primaryFill = hex("#F7A600"),
    primaryPress = hex("#D97F00"),
    inkOnPrimary = Color.White,
    onPrimarySub = Color.White.copy(alpha = 0.82f),
    heroFrom = hex("#FFC63F"),
    heroMid = hex("#F9A400"),
    heroTo = hex("#E88700"),
    out = hex("#F0655E"),
    outDeep = hex("#E14C46"),
    outWeak = hex("#FEF0EF"),
    inn = hex("#22B877"),
    inWeak = hex("#E7F8F0"),
    gold = hex("#FFB400"),
    blue = hex("#4489FE"),
    sky = hex("#00AAF5"),
    violet = hex("#9A6BFF"),
    mint = hex("#22B877"),
    rose = hex("#FF6B81"),
    text = hex("#1D2129"),
    sub = hex("#8A90A0"),
    dim = hex("#C2C6D1"),
    bg = hex("#FEF9ED"),
    card = Color.White,
    line = hex("#F2F0EA"),
    lineSoft = Color.Black.copy(alpha = 0.06f),
    fill = Color.Black.copy(alpha = 0.05f),
    raised = Color.White,
    mask = Color.Black.copy(alpha = 0.55f),
    keyBg = Color.White,
    keyPress = hex("#FAFBFD"),
    keyEdge = hex("#E5E8EF"),
    keyFg = hex("#2A2F3A"),
    bar = hex("#FFC24E"),
    gradPrimary = listOf(hex("#FFC63F"), hex("#F9AA00"), hex("#EC8C00")),
    gradPrimaryStrong = listOf(hex("#FFBB2B"), hex("#F39A00"), hex("#DD7B00")),
    gradBlue = commonBlue,
    gradSky = commonSky,
    gradViolet = commonViolet,
    gradMint = commonMint,
    gradRose = commonRose
)

val Nezha = Wukong.copy(
    id = "nezha",
    name = "哪吒红",
    tagline = "混天绫 · 朱红",
    primary = hex("#E0362E"),
    primaryDeep = hex("#BF271F"),
    primaryLight = hex("#FF7D63"),
    primaryWeak = hex("#FFECEA"),
    primaryTint = hex("#E0362E", 0.14f),
    primaryFill = hex("#E0362E"),
    primaryPress = hex("#BF271F"),
    heroFrom = hex("#FF7D63"),
    heroMid = hex("#EA3F30"),
    heroTo = hex("#BB1F19"),
    bg = hex("#FDF1F0"),
    bar = hex("#FF9D8C"),
    gradPrimary = listOf(hex("#FF7D63"), hex("#E8402F"), hex("#C9211B")),
    gradPrimaryStrong = listOf(hex("#FF6A4D"), hex("#E33A2C"), hex("#B81D18"))
)

val Shaseng = Wukong.copy(
    id = "shaseng",
    name = "沙僧黑",
    tagline = "降妖宝杖 · 纯黑",
    dark = true,
    primary = hex("#E6E6EC"),
    primaryDeep = Color.White,
    primaryLight = hex("#F2F2F6"),
    primaryWeak = hex("#1D1D21"),
    primaryTint = Color.White.copy(alpha = 0.10f),
    primaryFill = hex("#191A1C"),
    primaryPress = Color.Black,
    inkOnPrimary = Color.White,
    onPrimarySub = Color.White.copy(alpha = 0.72f),
    heroFrom = hex("#2A2A2F"),
    heroMid = hex("#151517"),
    heroTo = Color.Black,
    out = hex("#FF6F68"),
    outDeep = hex("#FF8A84"),
    outWeak = hex("#2A1716"),
    inn = hex("#35D492"),
    inWeak = hex("#10241B"),
    text = hex("#F2F2F4"),
    sub = hex("#A2A2AA"),
    dim = hex("#6A6A72"),
    bg = Color.Black,
    card = hex("#121214"),
    line = hex("#232327"),
    lineSoft = Color.White.copy(alpha = 0.07f),
    fill = Color.White.copy(alpha = 0.06f),
    raised = hex("#232327"),
    mask = Color.Black.copy(alpha = 0.66f),
    keyBg = hex("#1C1C1F"),
    keyPress = hex("#2A2A2E"),
    keyEdge = hex("#09090A"),
    keyFg = hex("#F2F2F4"),
    bar = hex("#B9B9C2"),
    gradPrimary = listOf(hex("#3D3D44"), hex("#232327"), hex("#111113")),
    gradPrimaryStrong = listOf(hex("#2A2A2F"), hex("#151517"), Color.Black)
)

val AllPalettes = listOf(Wukong, Nezha, Shaseng)

fun paletteOf(
    id: String,
    customColor: String = DEFAULT_CUSTOM_COLOR,
    customInk: String = CUSTOM_INK_AUTO
): Palette = if (id == CUSTOM_THEME_ID) customPalette(customColor, customInk)
else AllPalettes.firstOrNull { it.id == id } ?: Wukong

/** 主题 id -> 系统主题资源；Activity 启动前就要用，不能依赖 AppState。 */
fun themeStyleRes(id: String): Int = when (id) {
    Nezha.id -> R.style.Theme_Muyian_Nezha
    Shaseng.id -> R.style.Theme_Muyian_Shaseng
    else -> R.style.Theme_Muyian_Wukong
}

val LocalPalette = staticCompositionLocalOf { Wukong }

/** 1 设计单位对应的 dp：屏幕逻辑宽度 / 750。 */
val LocalRpx = staticCompositionLocalOf { 0.5f }

@Composable
fun rpx(value: Number): Dp = (value.toFloat() * LocalRpx.current).dp

@Composable
fun rsp(value: Number): TextUnit = (value.toFloat() * LocalRpx.current).sp

/** 分类色 -> 同色系纯色（替代 CSS 渐变；保持现有视觉的明度层次） */
fun categoryGradient(base: Color): List<Color> = listOf(
    mixColor(base, Color.White, 0.24f),
    base,
    mixColor(base, Color.Black, 0.12f)
)

fun tintColor(base: Color, dark: Boolean, ratio: Float = 0.88f): Color =
    mixColor(base, if (dark) Color.Black else Color.White, ratio)
