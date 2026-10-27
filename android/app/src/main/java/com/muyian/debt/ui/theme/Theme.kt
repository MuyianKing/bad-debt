package com.muyian.debt.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val pageWash: Color,
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
    val keyFnBg: Color,
    val keyFnEdge: Color,
    val keyFnFg: Color,
    val bar: Color,
    val gradPrimary: List<Color>,
    val gradPrimaryStrong: List<Color>,
    val gradBlue: List<Color>,
    val gradSky: List<Color>,
    val gradViolet: List<Color>,
    val gradMint: List<Color>,
    val gradRose: List<Color>
)

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
    bg = hex("#F7F4EC"),
    pageWash = hex("#FEF9ED"),
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
    keyFnBg = hex("#E9EBF2"),
    keyFnEdge = hex("#DCDFE8"),
    keyFnFg = hex("#4A5060"),
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
    pageWash = hex("#FDF1F0"),
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
    pageWash = Color.Black,
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
    keyFnBg = hex("#151517"),
    keyFnEdge = hex("#0B0B0C"),
    keyFnFg = hex("#C9C9D0"),
    bar = hex("#B9B9C2"),
    gradPrimary = listOf(hex("#3D3D44"), hex("#232327"), hex("#111113")),
    gradPrimaryStrong = listOf(hex("#2A2A2F"), hex("#151517"), Color.Black)
)

val AllPalettes = listOf(Wukong, Nezha, Shaseng)

fun paletteOf(id: String): Palette = AllPalettes.firstOrNull { it.id == id } ?: Wukong

val LocalPalette = staticCompositionLocalOf { Wukong }

/** 1 设计单位对应的 dp：屏幕逻辑宽度 / 750。 */
val LocalRpx = staticCompositionLocalOf { 0.5f }

@Composable
fun rpx(value: Number): Dp = (value.toFloat() * LocalRpx.current).dp

@Composable
fun rsp(value: Number): TextUnit = (value.toFloat() * LocalRpx.current).sp

/** 分类色 -> 同色系纯色（替代 CSS 渐变；保持现有视觉的明度层次） */
fun categoryGradient(base: Color): List<Color> {
    fun mix(a: Color, b: Color, ratio: Float) = Color(
        red = a.red + (b.red - a.red) * ratio,
        green = a.green + (b.green - a.green) * ratio,
        blue = a.blue + (b.blue - a.blue) * ratio,
        alpha = 1f
    )
    return listOf(
        mix(base, Color.White, 0.24f),
        base,
        mix(base, Color.Black, 0.12f)
    )
}

fun tintColor(base: Color, dark: Boolean, ratio: Float = 0.88f): Color {
    val target = if (dark) Color.Black else Color.White
    return Color(
        red = base.red + (target.red - base.red) * ratio,
        green = base.green + (target.green - base.green) * ratio,
        blue = base.blue + (target.blue - base.blue) * ratio,
        alpha = 1f
    )
}
