package com.muyian.debt.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muyian.debt.state.AppState
import com.muyian.debt.data.Bill
import com.muyian.debt.data.Cats
import com.muyian.debt.data.DayGroup
import com.muyian.debt.data.fmtMoney
import com.muyian.debt.ui.icons.AppIcon
import com.muyian.debt.ui.theme.LocalPalette
import com.muyian.debt.ui.theme.categoryGradient
import com.muyian.debt.ui.theme.hex
import com.muyian.debt.ui.theme.rpx
import com.muyian.debt.ui.theme.rsp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BillList(
    app: AppState,
    groups: List<DayGroup>,
    onRow: (Bill) -> Unit,
    onRowLong: (Bill) -> Unit,
    modifier: Modifier = Modifier,
    hideHead: Boolean = false,
    flush: Boolean = false
) {
    val p = LocalPalette.current
    Column(modifier) {
        for (group in groups) {
            val groupModifier = if (flush) {
                Modifier.fillMaxWidth().padding(horizontal = rpx(28))
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(start = rpx(24), end = rpx(24), bottom = rpx(24))
                    .glass()
                    .padding(horizontal = rpx(28), vertical = rpx(6))
            }
            Column(groupModifier) {
                if (!hideHead) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = rpx(24), bottom = rpx(10)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            group.title,
                            color = p.text,
                            fontSize = rsp(28),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (group.expense > 0) SumPill("支 ¥${fmtMoney(group.expense)}", true)
                        if (group.income > 0) {
                            Spacer(Modifier.width(rpx(14)))
                            SumPill("收 ¥${fmtMoney(group.income)}", false)
                        }
                    }
                }
                group.bills.forEachIndexed { index, bill ->
                    if (index > 0) DividerLine(Modifier.padding(horizontal = 0.dp), p.line)
                    RiseIn(
                        index = index,
                        key = bill.id,
                        modifier = Modifier.fillMaxWidth(),
                        distance = rpx(14)
                    ) {
                        BillRow(app, bill, onRow, onRowLong)
                    }
                }
            }
        }
    }
}

@Composable
private fun SumPill(text: String, out: Boolean) {
    val p = LocalPalette.current
    Text(
        text,
        color = if (out) p.outDeep else p.inn,
        fontSize = rsp(22),
        modifier = Modifier
            .clip(RoundedCornerShape(rpx(10)))
            .background(if (out) p.outWeak else p.inWeak)
            .padding(horizontal = rpx(14), vertical = rpx(5))
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BillRow(
    app: AppState,
    bill: Bill,
    onRow: (Bill) -> Unit,
    onRowLong: (Bill) -> Unit
) {
    val p = LocalPalette.current
    val type = if (bill.money < 0) "expense" else "income"
    val top = Cats.parentOf(app.categories, bill.costType, type).ifEmpty { bill.costType }
    val color = hex(Cats.colorHexOf(app.categories, top, type))
    val icon = Cats.iconOf(app.categories, bill.costType)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressBg by animateColorAsState(
        if (pressed) p.fill else Color.Transparent,
        tween(120, easing = Motion.InOut),
        label = "row-press"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(pressBg)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = { onRow(bill) },
                onLongClick = { onRowLong(bill) }
            )
            .padding(vertical = rpx(24)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(rpx(64))
                .clip(RoundedCornerShape(rpx(20)))
                .background(Brush.linearGradient(categoryGradient(color))),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(icon, Color.White, 18.dp, solid = true)
        }
        Spacer(Modifier.width(rpx(22)))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    bill.displayName,
                    color = p.text,
                    fontSize = rsp(30),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (bill.reimb == 1) {
                    Text(
                        "报销",
                        color = p.primary,
                        fontSize = rsp(20),
                        modifier = Modifier
                            .padding(start = rpx(12))
                            .clip(RoundedCornerShape(rpx(8)))
                            .background(p.primaryWeak)
                            .padding(horizontal = rpx(10), vertical = rpx(3))
                    )
                }
                if (bill.starred == 1) {
                    Spacer(Modifier.width(rpx(10)))
                    AppIcon("star", p.gold, 14.dp, 2.5f)
                }
                if (bill.imgPath.isNotEmpty()) {
                    Spacer(Modifier.width(rpx(10)))
                    AppIcon("camera", p.sub, 14.dp)
                }
            }
            if (bill.remarkText.isNotEmpty()) {
                Text(
                    bill.costType,
                    color = p.dim,
                    fontSize = rsp(22),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = rpx(5))
                )
            }
        }
        Spacer(Modifier.width(rpx(16)))
        Text(
            (if (bill.money < 0) "-" else "+") + fmtMoney(Math.abs(bill.money)),
            color = if (bill.money < 0) p.out else p.inn,
            fontSize = rsp(32),
            fontWeight = FontWeight.Bold
        )
    }
}
