package com.muyian.debt.data

/** 首页最近 7 日支出摘要；柱高由 UI 按 max 换算。 */
data class WeekSummary(val days: List<Pair<String, Double>>, val total: Double) {
    val max: Double get() = days.maxOfOrNull { it.second } ?: 0.0
}

object WeekStats {
    fun last7(bills: List<Bill>, now: Long = System.currentTimeMillis()): WeekSummary {
        val out = ArrayList<Pair<String, Double>>(7)
        var total = 0.0
        for ((y, m, d) in Dates.lastDays(7, now)) {
            var value = 0.0
            for (b in bills) {
                if (b.year == y && b.month == m && b.day == d && b.money < 0) value += -b.money
            }
            total += value
            out.add(weekdayCN(y, m, d) to value)
        }
        return WeekSummary(out, total)
    }

    fun monthKeys(now: Long = System.currentTimeMillis()): List<Pair<Int, Int>> =
        Dates.monthKeysForLastDays(7, now)
}
