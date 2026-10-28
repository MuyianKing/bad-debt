package com.muyian.debt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muyian.debt.data.GlassStyle
import com.muyian.debt.state.AppState
import com.muyian.debt.state.NavMotion
import com.muyian.debt.state.Screen
import com.muyian.debt.ui.components.MascotFace
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.glassPageBackground
import com.muyian.debt.ui.components.liquidGlass
import com.muyian.debt.ui.components.pressable
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp

/** 独立菜单页（原首页侧滑抽屉）：进栈从左缘推进，菜单项横向进入各页面，返回键逐级回退。 */
@Composable
fun MenuScreen(app: AppState) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .glassPageBackground()
    ) {
        val liquid = app.glassStyle == GlassStyle.Liquid
        Box(
            Modifier
                .fillMaxWidth()
                .clipToBounds()
                .then(
                    if (liquid) Modifier.liquidGlass(shape = RoundedCornerShape(0.dp))
                    else Modifier.background(Brush.linearGradient(p.gradPrimaryStrong))
                )
        ) {
            // 装饰圆圈放在 matchParentSize 里，只做背景，不参与头部高度测量
            if (!liquid) Box(Modifier.matchParentSize()) {
                Box(
                    Modifier
                        .offset(x = rpx(180), y = rpx(-120))
                        .size(rpx(380))
                        .clip(CircleShape)
                        .border(1.dp, p.inkOnPrimary.copy(alpha = 0.18f), CircleShape)
                )
                Box(
                    Modifier
                        .offset(x = rpx(250), y = rpx(30))
                        .size(rpx(190))
                        .clip(CircleShape)
                        .background(p.inkOnPrimary.copy(alpha = 0.08f))
                )
            }
            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(start = rpx(40), end = rpx(40), top = rpx(30), bottom = rpx(28)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(rpx(74))
                        .clip(CircleShape)
                        .background(if (liquid) p.fill else p.inkOnPrimary.copy(alpha = 0.22f))
                        .then(if (liquid) Modifier else Modifier.border(1.dp, p.inkOnPrimary.copy(alpha = 0.4f), CircleShape))
                        .padding(rpx(2)),
                    contentAlignment = Alignment.Center
                ) {
                    MascotFace(app, "idle", 70, head = true, mode = "base")
                }
                Spacer(Modifier.width(rpx(24)))
                Column {
                    Text("孽账", color = if (liquid) p.text else p.inkOnPrimary, fontSize = rsp(40), fontWeight = FontWeight.Bold, letterSpacing = rsp(2))
                    Text(
                        "记在本地 · 安心省心",
                        color = if (liquid) p.sub else p.onPrimarySub,
                        fontSize = rsp(23),
                        modifier = Modifier.padding(top = rpx(6))
                    )
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = rpx(20), end = rpx(20), top = rpx(34), bottom = rpx(12))
        ) {
            Text(
                "功能",
                color = p.sub.copy(alpha = 0.85f),
                fontSize = rsp(22),
                letterSpacing = rsp(2),
                modifier = Modifier.padding(start = rpx(24), bottom = rpx(14))
            )
            val menus = listOf(
                Triple("分类管理", "list", Screen.Categories) to p.gradBlue,
                Triple("账单日历", "calendar", Screen.Calendar) to p.gradMint,
                Triple("统计分析", "bar-chart", Screen.Stats) to p.gradSky,
                Triple("识别记录", "activity", Screen.Capture) to p.gradMint,
                Triple("备份·恢复", "rotate-ccw", Screen.Backup) to p.gradRose,
                Triple("设置·关于", "sliders", Screen.Settings) to p.gradViolet
            )
            for ((_, entry) in menus.withIndex()) {
                val (item, grad) = entry
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(rpx(22)))
                        .pressable(p.primaryWeak) { app.navigate(item.third, NavMotion.Horizontal) }
                        .padding(rpx(20)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(rpx(64))
                            .clip(RoundedCornerShape(rpx(20)))
                            .background(Brush.linearGradient(grad)),
                        contentAlignment = Alignment.Center
                    ) { AppIcon(item.second, Color.White, 20.dp) }
                    Spacer(Modifier.width(rpx(22)))
                    Text(item.first, color = p.text, fontSize = rsp(29), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    AppIcon("chevron-right", p.dim, 16.dp)
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = rpx(32), end = rpx(32), top = rpx(24), bottom = rpx(28))
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(rpx(999)))
                    .then(
                        if (app.glassStyle == GlassStyle.Liquid) Modifier.liquidGlass(alpha = 0.6f)
                        else Modifier.glass(alpha = 0.6f)
                    )
                    .padding(horizontal = rpx(20), vertical = rpx(10)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon("lock", p.sub, 13.dp)
                Spacer(Modifier.width(rpx(8)))
                Text("数据仅保存在本机", color = p.sub, fontSize = rsp(21))
            }
            Text(
                "孽账 v${app.versionName}",
                color = p.dim,
                fontSize = rsp(21),
                modifier = Modifier.padding(top = rpx(16))
            )
        }
    }
}
