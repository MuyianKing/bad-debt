package com.muyian.debt.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.muyian.debt.data.Capture
import com.muyian.debt.data.CaptureDiagnostics
import com.muyian.debt.state.AppState
import com.muyian.debt.ui.components.DividerLine
import com.muyian.debt.ui.components.GroupTitle
import com.muyian.debt.ui.components.TopBar
import com.muyian.debt.ui.components.glass
import com.muyian.debt.ui.components.navBottomPadding
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/** 最近识别的「查看全部」：完整判定列表，按 TRACE_PAGE 分块懒加载，块间连成一张卡。 */
@Composable
fun CaptureAllScreen(app: AppState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var diag by remember { mutableStateOf(CaptureSnapshot.diag ?: CaptureDiagnostics()) }
    var loaded by remember { mutableStateOf(CaptureSnapshot.diag != null) }
    // 每行各建一个 SimpleDateFormat 会在成批组合时明显拖慢，全页共用一个即可
    val timeFmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.CHINA) }

    LaunchedEffect(app.resumeToken) {
        val value = withContext(Dispatchers.IO) { Capture.load(ctx, 200) }
        CaptureSnapshot.diag = value
        diag = value
        loaded = true
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "全部识别记录", onBack = { app.pop() })
        val chunks = remember(diag.traces) { diag.traces.chunked(TRACE_PAGE) }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = rpx(60) + navBottomPadding())
        ) {
            item { GroupTitle("全部记录") }
            if (!loaded || diag.traces.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = rpx(20), vertical = rpx(4))
                            .glass(shape = RoundedCornerShape(rpx(20)))
                            .padding(rpx(24))
                    ) {
                        Text(
                            if (!loaded) "正在读取…" else "还没有识别记录",
                            color = if (!loaded) p.dim else p.sub,
                            fontSize = rsp(24)
                        )
                    }
                }
            } else {
                itemsIndexed(chunks) { index, chunk ->
                    val first = index == 0
                    val last = index == chunks.lastIndex
                    val shape = when {
                        first && last -> RoundedCornerShape(rpx(20))
                        first -> RoundedCornerShape(topStart = rpx(20), topEnd = rpx(20))
                        last -> RoundedCornerShape(bottomEnd = rpx(20), bottomStart = rpx(20))
                        else -> RoundedCornerShape(0.dp)
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = rpx(20))
                            .then(
                                if (first) Modifier.padding(top = rpx(4)) else Modifier
                            )
                            .then(
                                if (last) Modifier.padding(bottom = rpx(4)) else Modifier
                            )
                            .glass(shape = shape)
                    ) {
                        for (t in chunk) {
                            TraceRow(t, timeFmt, onReparse = { reparseCaptureTrace(ctx, scope, app, t) })
                            DividerLine(color = p.lineSoft)
                        }
                    }
                }
            }
        }
    }
}
