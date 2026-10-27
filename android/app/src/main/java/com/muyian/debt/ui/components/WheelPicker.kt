package com.muyian.debt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.abs

@Composable
fun WheelColumn(
    values: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = LocalPalette.current
    val itemHeight = rpx(84)
    val listState = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(listState)
    var current by remember { mutableIntStateOf(selected.coerceIn(0, (values.size - 1).coerceAtLeast(0))) }

    LaunchedEffect(Unit) {
        if (values.isNotEmpty()) listState.scrollToItem(current.coerceIn(0, values.lastIndex))
    }
    LaunchedEffect(listState, values.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@snapshotFlow null
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull {
                abs(it.offset + it.size / 2 - center)
            }?.index
        }.filterNotNull().distinctUntilChanged().collect {
            val idx = it.coerceIn(0, values.lastIndex)
            if (idx != current) {
                current = idx
                onSelected(idx)
            }
        }
    }

    Box(modifier.height(itemHeight * 5)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(rpx(12)))
                .background(p.fill)
        )
        LazyColumn(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight * 2),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(values) { index, text ->
                Box(
                    Modifier.fillMaxWidth().height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text,
                        color = if (index == current) p.primaryDeep else p.sub,
                        fontSize = rsp(30),
                        fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun WheelRow(
    content: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        content(Modifier)
    }
}
