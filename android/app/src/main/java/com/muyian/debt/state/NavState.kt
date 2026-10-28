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

/** 页面进入方向：首页按钮等默认纵向；抽屉菜单走横向。 */
enum class NavMotion { Vertical, Horizontal }

/** 页面栈与记一笔草稿；不持有任何弹层状态。 */
class NavState(private val categoryState: CategoryState) {
    private data class Entry(val screen: Screen, val motion: NavMotion)

    private val entries = mutableStateListOf(Entry(Screen.Home, NavMotion.Vertical))
    val stack: List<Screen> get() = entries.map { it.screen }
    val current: Screen get() = entries.last().screen

    /** 最近一次导航方向：true 进栈，false 出栈。 */
    var navForward by mutableStateOf(true)
        private set

    /** 本次转场方向；出栈时取被弹出页面的进入方向，保证原路返回。 */
    var navMotion by mutableStateOf(NavMotion.Vertical)
        private set

    fun navigate(screen: Screen, motion: NavMotion = NavMotion.Vertical) {
        navForward = true
        navMotion = motion
        entries.add(Entry(screen, motion))
    }

    fun pop() {
        if (entries.size > 1) {
            navForward = false
            navMotion = entries.last().motion
            entries.removeAt(entries.lastIndex)
        }
    }

    fun popToHome() {
        if (entries.size > 1) {
            navForward = false
            navMotion = entries.last().motion
            while (entries.size > 1) entries.removeAt(entries.lastIndex)
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
