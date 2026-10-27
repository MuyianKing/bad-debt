package com.muyian.debt.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.muyian.debt.data.AddDraft
import com.muyian.debt.data.Bill
import com.muyian.debt.data.fmtMoneyInput

sealed class Screen {
    data object Home : Screen()
    data object Add : Screen()
    data object Stats : Screen()
    data object Calendar : Screen()
    data object Categories : Screen()
    data object Settings : Screen()
    data object Import : Screen()
    data object Backup : Screen()
}

/** 页面栈与记一笔草稿；不持有任何弹层状态。 */
class NavState(private val categoryState: CategoryState) {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = stack.last()

    /** 最近一次导航方向：true 进栈，false 出栈。 */
    var navForward by mutableStateOf(true)
        private set

    fun navigate(screen: Screen) {
        navForward = true
        stack.add(screen)
    }

    fun pop() {
        if (stack.size > 1) {
            navForward = false
            stack.removeAt(stack.lastIndex)
        }
    }

    fun popToHome() {
        if (stack.size > 1) {
            navForward = false
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        }
    }

    var addDraft by mutableStateOf<AddDraft?>(null)
        private set

    fun openAdd(bill: Bill? = null) {
        addDraft = if (bill == null) {
            AddDraft()
        } else {
            val type = if (bill.isIncome) "income" else "expense"
            val list = categoryState.categories.list(type)
            var topIndex = list.indexOfFirst { it.name == bill.costType }
            var subName = ""
            if (topIndex < 0) {
                topIndex = list.indexOfFirst { c -> c.subs.any { it.name == bill.costType } }
                if (topIndex >= 0) subName = bill.costType
            }
            if (topIndex < 0) topIndex = 0
            AddDraft(
                editId = bill.id.toString(),
                isIncome = bill.isIncome,
                selectedTopIndex = topIndex,
                selectedSub = subName,
                remark = bill.remarkText,
                expr = fmtMoneyInput(String.format("%.2f", Math.abs(bill.money))),
                reimb = bill.reimb == 1,
                starred = bill.starred == 1,
                dateValue = bill.dateText,
                imgPath = bill.imgPath
            )
        }
        navigate(Screen.Add)
    }

    fun updateDraft(draft: AddDraft) {
        addDraft = draft
    }

    fun finishAdd() {
        addDraft = null
        pop()
    }

    /** 首页选中的月份跨页面保留。 */
    var homeYear by mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
    var homeMonth by mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1)
}
