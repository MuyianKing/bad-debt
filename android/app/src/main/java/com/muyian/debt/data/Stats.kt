package com.muyian.debt.data

import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

data class MoneyTotals(val expense: Double, val income: Double) {
    val balance: Double get() = income - expense
}

data class ReportRow(val label: String, val expense: Double, val income: Double)

data class DonutSlice(
    val name: String,
    val value: Double,
    val bills: List<Bill>,
    val pct: String,
    val colorHex: String
)

/** 统计页的全部业务计算：页面只负责动画与绘制。 */
object Stats {
    /** 分类报表里“其他”的固定色，数据层只给 hex，UI 再转 Color。 */
    const val OTHER_COLOR_HEX = "#C8CCD4"

    fun totals(bills: List<Bill>): MoneyTotals {
        var expense = 0.0
        var income = 0.0
        for (b in bills) {
            if (b.money < 0) expense += -b.money else income += b.money
        }
        return MoneyTotals(expense, income)
    }

    /** 当前周期已经过去多少格；未来月份返回 0。 */
    fun chartSlots(range: String, year: Int, month: Int, now: Long): Int {
        val t = Calendar.getInstance().apply { timeInMillis = now }
        return if (range == "month") {
            val isCurrent = t.get(Calendar.YEAR) == year && t.get(Calendar.MONTH) + 1 == month
            if (isCurrent) t.get(Calendar.DAY_OF_MONTH)
            else {
                val isFuture = year > t.get(Calendar.YEAR) ||
                    (year == t.get(Calendar.YEAR) && month > t.get(Calendar.MONTH) + 1)
                if (isFuture) 0 else daysInMonth(year, month)
            }
        } else {
            if (year == t.get(Calendar.YEAR)) t.get(Calendar.MONTH) + 1
            else if (year > t.get(Calendar.YEAR)) 0
            else 12
        }
    }

    fun barValues(bills: List<Bill>, range: String, slots: Int): List<Double> {
        if (slots <= 0) return emptyList()
        val sums = DoubleArray(slots)
        for (b in bills) {
            if (b.money >= 0) continue
            val idx = if (range == "month") b.day - 1 else b.month - 1
            if (idx in 0 until slots) sums[idx] += -b.money
        }
        return sums.toList()
    }

    fun axisMax(maxBar: Double): Double {
        if (maxBar <= 0) return 0.0
        val unit = 10.0.pow(max(0.0, floor(log10(maxBar / 4))))
        return ceil(maxBar / 4 / unit) * 4 * unit
    }

    fun yTicks(axisMax: Double): List<Double> =
        if (axisMax <= 0) listOf(0.0)
        else listOf(axisMax, axisMax * 0.75, axisMax * 0.5, axisMax * 0.25, 0.0)

    fun xLabels(range: String, month: Int, slots: Int): List<String> {
        if (slots <= 0) return listOf("", "", "")
        return if (range == "month") {
            val head = "$month-01"
            val mid = "$month-${pad2(ceil(slots / 2.0).toInt())}"
            val end = "$month-${pad2(slots)}"
            if (slots == 1) listOf(head, "", "")
            else listOf(head, if (mid == head || mid == end) "" else mid, end)
        } else {
            if (slots == 1) listOf("1月", "", "")
            else {
                val mid = "${ceil(slots / 2.0).toInt()}月"
                listOf("1月", if (mid == "1月" || mid == "${slots}月") "" else mid, "${slots}月")
            }
        }
    }

    fun reportRows(bills: List<Bill>, range: String, month: Int): List<ReportRow> {
        val byMonth = range == "year"
        val map = LinkedHashMap<Int, Pair<Double, Double>>()
        for (b in bills) {
            val key = if (byMonth) b.month else b.day
            val old = map[key] ?: (0.0 to 0.0)
            map[key] = if (b.money < 0) old.first + (-b.money) to old.second
            else old.first to old.second + b.money
        }
        return map.entries.sortedByDescending { it.key }.map { (key, sums) ->
            ReportRow(
                label = if (byMonth) "${key}月" else "${pad2(month)}-${pad2(key)}",
                expense = sums.first,
                income = sums.second
            )
        }
    }

    fun donutSlices(bills: List<Bill>, kind: String, categories: CategoryData): List<DonutSlice> {
        val list = bills.filter { if (kind == "expense") it.money < 0 else it.money > 0 }
        val map = LinkedHashMap<String, MutableList<Bill>>()
        for (b in list) {
            val name = Cats.topNameOf(categories, b.costType, kind)
            map.getOrPut(name) { mutableListOf() }.add(b)
        }
        val groups = map.map { (name, bs) ->
            name to (bs.sumOf { kotlin.math.abs(it.money) } to bs)
        }.sortedByDescending { it.second.first }
        val total = groups.sumOf { it.second.first }
        if (total <= 0) return emptyList()

        val out = ArrayList<DonutSlice>()
        for ((name, pair) in groups.take(8)) {
            out.add(
                DonutSlice(
                    name = name,
                    value = pair.first,
                    bills = pair.second,
                    pct = String.format("%.1f%%", pair.first / total * 100),
                    colorHex = Cats.colorHexOf(categories, name, kind)
                )
            )
        }
        val rest = groups.drop(8)
        val restValue = rest.sumOf { it.second.first }
        if (restValue > 0) {
            out.add(
                DonutSlice(
                    name = "其他",
                    value = restValue,
                    bills = rest.flatMap { it.second.second },
                    pct = String.format("%.1f%%", restValue / total * 100),
                    colorHex = OTHER_COLOR_HEX
                )
            )
        }
        return out
    }

    /** 周期切换：月模式跨年，年模式直接加减。 */
    fun shift(range: String, year: Int, month: Int, delta: Int): Pair<Int, Int> {
        if (range != "month") return year + delta to month
        var m = month + delta
        var y = year
        if (m < 1) { m = 12; y-- }
        if (m > 12) { m = 1; y++ }
        return y to m
    }
}
