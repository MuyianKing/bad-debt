package com.muyian.debt

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import com.muyian.debt.data.PrefKeys
import com.muyian.debt.data.Prefs
import com.muyian.debt.data.GlassStyle
import com.muyian.debt.data.LauncherIcon
import com.muyian.debt.state.AppState
import com.muyian.debt.state.NavMotion
import com.muyian.debt.state.Screen
import com.muyian.debt.ui.components.ActionSheetHost
import com.muyian.debt.ui.components.AppToastHost
import com.muyian.debt.ui.components.ConfirmDialogHost
import com.muyian.debt.ui.components.GlassBackdropHost
import com.muyian.debt.ui.components.LocalGlassStyle
import com.muyian.debt.ui.components.DatePickerHost
import com.muyian.debt.ui.components.ColorPickerSheetHost
import com.muyian.debt.ui.components.ImagePreviewHost
import com.muyian.debt.ui.components.Motion
import com.muyian.debt.ui.components.rememberFileBitmap
import com.muyian.debt.ui.components.PickerSheetHost
import com.muyian.debt.ui.screens.AddScreen
import com.muyian.debt.ui.screens.BackupScreen
import com.muyian.debt.ui.screens.CalendarScreen
import com.muyian.debt.ui.screens.CaptureScreen
import com.muyian.debt.ui.screens.CategoriesScreen
import com.muyian.debt.ui.screens.HomeScreen
import com.muyian.debt.ui.screens.ImportScreen
import com.muyian.debt.ui.screens.MenuScreen
import com.muyian.debt.ui.screens.SettingsScreen
import com.muyian.debt.ui.screens.StatsScreen
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.liquidReadable
import com.muyian.debt.ui.theme.LocalRpx
import com.muyian.debt.ui.theme.themeStyleRes
import muyian.bill.listener.MuyianBillCore

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(themeStyleRes(Prefs(this).str(PrefKeys.THEME, "wukong")))
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        try {
            MuyianBillCore.init(this)
        } catch (_: Exception) {
        }
        app = AppState(this)
        app.applyCardTheme()
        app.applyRecentsVisibility()
        LauncherIcon.sync(this, app.baseMascotStyle.id)
        setContent { MuyianRoot(app) }
    }

    override fun onResume() {
        super.onResume()
        app.onResume()
    }
}

@Suppress("DEPRECATION")
@Composable
private fun MuyianRoot(app: AppState) {
    val p = app.palette
    val view = LocalView.current
    val activity = view.context as Activity
    val scale = LocalConfiguration.current.screenWidthDp / 750f
    /* 系统启动帧是纯奶油色；首帧用同色盖一下再快速淡出，
       避免首页 hero 的橙/红直接切出来像一块启动页。只做淡出，不额外等待。 */
    val splashFade = remember { Animatable(1f) }
    var splashFading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        splashFade.animateTo(0f, tween(100))
        splashFading = false
    }
    // 首页未吸顶时状态栏压在 Hero 上：自定义亮色主色 / 液态玻璃下 Hero 是浅底，图标要转深色
    val homeDarkInk = !p.dark &&
        (app.glassStyle == GlassStyle.Liquid || p.inkOnPrimary != androidx.compose.ui.graphics.Color.White)
    val darkStatusIcons = splashFading ||
        if (app.current == Screen.Home) {
            if (app.homeNavSolid) !p.dark else homeDarkInk
        } else if (app.current == Screen.Menu) {
            // 菜单页头部与首页头区同为主色渐变，图标深浅沿用头区判定
            homeDarkInk
        } else {
            !p.dark
        }
    LaunchedEffect(app.current, darkStatusIcons) {
        delay(60)
        val window = activity.window
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = darkStatusIcons
        @Suppress("DEPRECATION")
        var flags = window.decorView.systemUiVisibility
        flags = if (darkStatusIcons) {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = flags
    }

    SideEffect {
        val window = activity.window
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = p.bg.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightNavigationBars = splashFading || !p.dark
    }

    BackHandler {
        when {
            app.previewImage != null -> app.previewImage = null
            app.colorPicker != null -> app.colorPicker = null
            app.datePicker != null -> app.datePicker = null
            app.picker != null -> app.picker = null
            app.actionSheet != null -> app.actionSheet = null
            app.dialog != null -> app.dialog = null
            app.current == Screen.Home -> activity.moveTaskToBack(true)
            else -> app.pop()
        }
    }

    // 液态玻璃透底更强，灰阶文字需要加深一档才看得清；毛玻璃沿用原令牌
    val palette = remember(p, app.glassStyle) {
        if (app.glassStyle == GlassStyle.Liquid) p.liquidReadable() else p
    }

    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalRpx provides scale,
        LocalGlassStyle provides app.glassStyle
    ) {
        // 全局壁纸 + 玻璃采样源：所有页面共用同一张背景，玻璃才是“透”而不是贴图
        GlassBackdropHost(rememberFileBitmap(app.bgImage)) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = app.current,
                transitionSpec = {
                    // 进栈：新页面从底部上浮，旧页面轻微缩小后退；出栈反向。
                    // 抽屉菜单进来的是横向推进，返回时原路滑回右侧。
                    val forward = app.navForward
                    if (app.navMotion == NavMotion.Horizontal) {
                        val menuDirected = (forward && targetState == Screen.Menu) ||
                            (!forward && initialState == Screen.Menu)
                        if (menuDirected) {
                            // 菜单页保留抽屉的方位感：从左缘推进、首页向右让位；返回时原路滑回左缘
                            if (forward) {
                                (slideInHorizontally(
                                    tween(320, easing = Motion.Out),
                                    initialOffsetX = { -it }
                                ) + fadeIn(tween(140))).togetherWith(
                                    slideOutHorizontally(
                                        tween(320, easing = Motion.Out),
                                        targetOffsetX = { it / 5 }
                                    ) + fadeOut(tween(200))
                                )
                            } else {
                                (slideInHorizontally(
                                    tween(300, easing = Motion.Out),
                                    initialOffsetX = { it / 5 }
                                ) + fadeIn(tween(160))).togetherWith(
                                    slideOutHorizontally(
                                        tween(300, easing = Motion.Out),
                                        targetOffsetX = { -it }
                                    ) + fadeOut(tween(180))
                                )
                            }
                        } else if (forward) {
                            (slideInHorizontally(
                                tween(320, easing = Motion.Out),
                                initialOffsetX = { it }
                            ) + fadeIn(tween(140))).togetherWith(
                                slideOutHorizontally(
                                    tween(320, easing = Motion.Out),
                                    targetOffsetX = { -it / 5 }
                                ) + fadeOut(tween(200))
                            )
                        } else {
                            (slideInHorizontally(
                                tween(300, easing = Motion.Out),
                                initialOffsetX = { -it / 5 }
                            ) + fadeIn(tween(160))).togetherWith(
                                slideOutHorizontally(
                                    tween(300, easing = Motion.Out),
                                    targetOffsetX = { it }
                                ) + fadeOut(tween(180))
                            )
                        }
                    } else if (forward) {
                        // 记一笔是“大面板”语义，位移比普通列表页更大。
                        val enterOffset = if (targetState == Screen.Add) 0.18f else 0.07f
                        (fadeIn(tween(150, easing = Motion.Out)) +
                            scaleIn(tween(260, easing = Motion.Out), initialScale = 0.985f) +
                            slideInVertically(
                                tween(300, easing = Motion.Out),
                                initialOffsetY = { (it * enterOffset).toInt() }
                            )).togetherWith(
                            fadeOut(tween(130)) +
                                scaleOut(tween(250, easing = Motion.InOut), targetScale = 1.005f)
                        )
                    } else {
                        // 出栈必须是“可见的下落”：两侧页面底色相同，只做淡入淡出会被看成瞬间切换。
                        val exitOffset = if (initialState == Screen.Add) 0.22f else 0.16f
                        (fadeIn(tween(200, easing = Motion.Out)) +
                            scaleIn(tween(300, easing = Motion.Out), initialScale = 0.99f) +
                            slideInVertically(
                                tween(300, easing = Motion.Out),
                                initialOffsetY = { -(it * 0.04f).toInt() }
                            )).togetherWith(
                            fadeOut(tween(240, easing = Motion.InOut)) +
                                scaleOut(tween(280, easing = Motion.Out), targetScale = 0.96f) +
                                slideOutVertically(
                                    tween(300, easing = Motion.Out),
                                    targetOffsetY = { (it * exitOffset).toInt() }
                                )
                        )
                    }
                },
                label = "screen"
            ) { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(app, activity)
                    Screen.Menu -> MenuScreen(app)
                    Screen.Add -> AddScreen(app)
                    Screen.Stats -> StatsScreen(app)
                    Screen.Calendar -> CalendarScreen(app)
                    Screen.Categories -> CategoriesScreen(app)
                    Screen.Settings -> SettingsScreen(app)
                    Screen.Capture -> CaptureScreen(app)
                    Screen.Import -> ImportScreen(app)
                    Screen.Backup -> BackupScreen(app)
                }
            }
            ActionSheetHost(app)
            PickerSheetHost(app)
            DatePickerHost(app)
            ColorPickerSheetHost(app)
            ConfirmDialogHost(app)
            ImagePreviewHost(app)
            AppToastHost(app)
            if (splashFading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = splashFade.value }
                        .background(p.bg)
                )
            }
        }
        }
    }
}
