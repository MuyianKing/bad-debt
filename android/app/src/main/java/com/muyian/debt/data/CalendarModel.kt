package com.muyian.debt.data

import java.util.Calendar

data class DayStat(val expense: Double, val income: Double) {
    val balance: Double get() = income - expense
}

data class DayMark(val text: String, val kind: String)

/** 日历页的按日汇总、月格与角标规则。 */
object CalendarModel {
    fun dayStats(bills: List<Bill>): Map<Int, DayStat> {
        val map = HashMap<Int, DayStat>()
        for (b in bills) {
            val old = map[b.day] ?: DayStat(0.0, 0.0)
            map[b.day] = if (b.money < 0) old.copy(expense = old.expense + -b.money)
            else old.copy(income = old.income + b.money)
        }
        return map
    }

    /** 周一为一周起点，前后补 0 占位。 */
    fun cells(year: Int, month: Int): List<Int> {
        val out = ArrayList<Int>()
        val first = Calendar.getInstance().apply { set(year, month - 1, 1) }
        var lead = first.get(Calendar.DAY_OF_WEEK) - 2
        if (lead < 0) lead = 6
        for (i in 0 until lead) out.add(0)
        for (d in 1..daysInMonth(year, month)) out.add(d)
        while (out.size % 7 != 0) out.add(0)
        return out
    }

    /** 日历角标：有账单按模式显示金额，否则显示农历/节日。 */
    fun mark(stat: DayStat?, mode: String, year: Int, month: Int, day: Int): DayMark {
        var text = ""
        var kind = ""
        if (stat != null) {
            when (mode) {
                "both" -> {
                    if (stat.expense > 0) { text = "-${fmtNum(stat.expense)}"; kind = "out" }
                    else if (stat.income > 0) { text = "+${fmtNum(stat.income)}"; kind = "in" }
                }
                "balance" -> {
                    val net = stat.balance
                    if (net != 0.0) { text = fmtNum(net); kind = if (net < 0) "out" else "in" }
                }
                "income" -> if (stat.income > 0) { text = "+${fmtNum(stat.income)}"; kind = "in" }
                "expense" -> if (stat.expense > 0) { text = "-${fmtNum(stat.expense)}"; kind = "out" }
            }
        }
        if (text.isEmpty()) {
            text = Lunar.lunarText(year, month, day)
            kind = "lunar"
        }
        return DayMark(text, kind)
    }
}
