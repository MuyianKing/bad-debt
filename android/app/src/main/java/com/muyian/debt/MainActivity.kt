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
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import com.muyian.debt.data.AppState
import com.muyian.debt.data.Prefs
import com.muyian.debt.data.Screen
import com.muyian.debt.ui.components.ActionSheetHost
import com.muyian.debt.ui.components.AppToastHost
import com.muyian.debt.ui.components.ConfirmDialogHost
import com.muyian.debt.ui.components.DatePickerHost
import com.muyian.debt.ui.components.ImagePreviewHost
import com.muyian.debt.ui.components.Motion
import com.muyian.debt.ui.components.PickerSheetHost
import com.muyian.debt.ui.screens.AddScreen
import com.muyian.debt.ui.screens.BackupScreen
import com.muyian.debt.ui.screens.CalendarScreen
import com.muyian.debt.ui.screens.CategoriesScreen
import com.muyian.debt.ui.screens.HomeScreen
import com.muyian.debt.ui.screens.ImportScreen
import com.muyian.debt.ui.screens.SettingsScreen
import com.muyian.debt.ui.screens.StatsScreen
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.LocalRpx
import muyian.bill.listener.MuyianBillCore

private val SplashColor = Color(0xFFF7F4EC)

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(
            when (Prefs(this).str("muyian_theme_v1", "wukong")) {
                "nezha" -> R.style.Theme_Muyian_Nezha
                "shaseng" -> R.style.Theme_Muyian_Shaseng
                else -> R.style.Theme_Muyian_Wukong
            }
        )
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        try {
            MuyianBillCore.init(this)
        } catch (_: Exception) {
        }
        app = AppState(this)
        app.applyCardTheme()
        app.applyRecentsVisibility()
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
    val darkStatusIcons = splashFading ||
        if (app.current == Screen.Home) app.homeNavSolid && !p.dark else !p.dark
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
            app.datePicker != null -> app.datePicker = null
            app.picker != null -> app.picker = null
            app.actionSheet != null -> app.actionSheet = null
            app.dialog != null -> app.dialog = null
            app.current == Screen.Home -> activity.moveTaskToBack(true)
            else -> app.pop()
        }
    }

    CompositionLocalProvider(LocalPalette provides p, LocalRpx provides scale) {
        Box(Modifier.fillMaxSize().background(p.bg)) {
            AnimatedContent(
                targetState = app.current,
                transitionSpec = {
                    // 进栈：新页面从底部上浮，旧页面轻微缩小后退；出栈反向。
                    // 记一笔是“大面板”语义，位移比普通列表页更大。
                    val forward = app.navForward
                    if (forward) {
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
                        val exitOffset = if (initialState == Screen.Add) 0.18f else 0.07f
                        (fadeIn(tween(180, easing = Motion.Out)) +
                            scaleIn(tween(280, easing = Motion.Out), initialScale = 1.01f)).togetherWith(
                            fadeOut(tween(170, easing = Motion.InOut)) +
                                scaleOut(tween(260, easing = Motion.Out), targetScale = 0.97f) +
                                slideOutVertically(
                                    tween(280, easing = Motion.Out),
                                    targetOffsetY = { (it * exitOffset).toInt() }
                                )
                        )
                    }
                },
                label = "screen"
            ) { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(app, activity)
                    Screen.Add -> AddScreen(app)
                    Screen.Stats -> StatsScreen(app)
                    Screen.Calendar -> CalendarScreen(app)
                    Screen.Categories -> CategoriesScreen(app)
                    Screen.Settings -> SettingsScreen(app)
                    Screen.Import -> ImportScreen(app)
                    Screen.Backup -> BackupScreen(app)
                }
            }
            ActionSheetHost(app)
            PickerSheetHost(app)
            DatePickerHost(app)
            ConfirmDialogHost(app)
            ImagePreviewHost(app)
            AppToastHost(app)
            if (splashFading) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = splashFade.value }
                        .background(SplashColor)
                )
            }
        }
    }
}
