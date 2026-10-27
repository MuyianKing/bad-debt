package com.muyian.debt.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PureLogicTest {

    private fun bill(
        day: Int,
        money: Double,
        costType: String = "三餐",
        year: Int = 2026,
        month: Int = 10
    ) = Bill(
        id = 0,
        year = year,
        month = month,
        day = day,
        clock = "12:00",
        money = money,
        bankName = "",
        costType = costType,
        orderRemark = "",
        source = "manual",
        sourcePkg = "",
        createdAt = 0,
        reimb = 0,
        starred = 0,
        orderNo = ""
    )

    @Test
    fun moneyFormatting() {
        assertEquals("1,234.50", fmtMoney(1234.5))
        assertEquals("-1,234.50", fmtMoney(-1234.5))
        assertEquals("-0.05", fmtMoney(-0.05))
        assertEquals("1,234", fmtNum(1234.0))
        assertEquals("12.5", fmtNum(12.5))
        assertEquals("1.5K", fmtTick(1500.0))
        assertEquals("12.5", fmtMoneyInput("12.50"))
        assertEquals("0", fmtMoneyInput("-0"))
    }

    @Test
    fun expressionEvaluation() {
        assertEquals(17.5, evalExpr("12.5+7-2"), 0.0001)
        assertEquals(-3.0, evalExpr("-1-2"), 0.0001)
        assertTrue(evalExpr("").isNaN())
        assertTrue(evalExpr("+").isNaN())
        assertTrue(evalExpr("-").isNaN())
    }

    @Test
    fun groupByDaySortsAndSums() {
        val groups = groupByDay(
            listOf(
                bill(1, -10.0),
                bill(3, -2.0),
                bill(3, 5.0)
            ),
            year = 2026,
            month = 10
        )
        assertEquals(listOf(3, 1), groups.map { it.day })
        assertEquals(2.0, groups[0].expense, 0.0001)
        assertEquals(5.0, groups[0].income, 0.0001)
        assertEquals(10.0, groups[1].expense, 0.0001)
    }

    @Test
    fun statsTotalsAndAxis() {
        val totals = Stats.totals(listOf(bill(1, -10.0), bill(2, 5.0), bill(3, -2.5)))
        assertEquals(12.5, totals.expense, 0.0001)
        assertEquals(5.0, totals.income, 0.0001)
        assertEquals(-7.5, totals.balance, 0.0001)

        assertEquals(120.0, Stats.axisMax(100.0), 0.0001)
        assertEquals(0.0, Stats.axisMax(0.0), 0.0001)
        assertEquals(listOf(120.0, 90.0, 60.0, 30.0, 0.0), Stats.yTicks(120.0))
    }

    @Test
    fun statsLabelsAndShift() {
        assertEquals(listOf("10-01", "10-16", "10-31"), Stats.xLabels("month", 10, 31))
        assertEquals(listOf("1月", "6月", "12月"), Stats.xLabels("year", 10, 12))
        assertEquals(2025 to 12, Stats.shift("month", 2026, 1, -1))
        assertEquals(2027 to 10, Stats.shift("year", 2026, 10, 1))
    }

    @Test
    fun statsReportRowsAreDescending() {
        val rows = Stats.reportRows(
            listOf(bill(1, -10.0), bill(2, -4.0), bill(2, 3.0)),
            range = "month",
            month = 10
        )
        assertEquals(listOf("10-02", "10-01"), rows.map { it.label })
        assertEquals(4.0, rows[0].expense, 0.0001)
        assertEquals(3.0, rows[0].income, 0.0001)
    }

    @Test
    fun statsDonutSlicesMergeAndSort() {
        val cats = CategoryData(Cats.defaultExpense(), Cats.defaultIncome())
        val slices = Stats.donutSlices(
            listOf(bill(1, -10.0, "三餐"), bill(2, -5.0, "三餐"), bill(3, -2.0, "交通")),
            kind = "expense",
            categories = cats
        )
        assertEquals(listOf("三餐", "交通"), slices.map { it.name })
        assertEquals(15.0, slices[0].value, 0.0001)
        assertEquals("88.2%", slices[0].pct)
        assertEquals("11.8%", slices[1].pct)
    }

    @Test
    fun calendarCellsStartOnMonday() {
        val cells = CalendarModel.cells(2026, 10)
        assertEquals(35, cells.size)
        assertEquals(listOf(0, 0, 0), cells.take(3))
        assertEquals(1, cells[3])
        assertEquals(0, cells.last())
    }

    @Test
    fun calendarDayStatsAndMarks() {
        val stats = CalendarModel.dayStats(
            listOf(bill(1, -10.0), bill(1, 5.0), bill(2, -3.0))
        )
        assertEquals(10.0, stats[1]!!.expense, 0.0001)
        assertEquals(5.0, stats[1]!!.income, 0.0001)

        assertEquals(DayMark("-10", "out"), CalendarModel.mark(stats[1], "both", 2026, 10, 1))
        assertEquals(DayMark("-5", "out"), CalendarModel.mark(stats[1], "balance", 2026, 10, 1))
        assertEquals("lunar", CalendarModel.mark(null, "both", 2026, 10, 1).kind)
    }

    @Test
    fun lunarFestival() {
        assertEquals("国庆节", Lunar.lunarText(2026, 10, 1))
    }

    @Test
    fun datesLastDaysAndMonths() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 3, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val days = Dates.lastDays(7, now)
        assertEquals(7, days.size)
        assertEquals(Triple(2026, 10, 3), days.last())
        val keys = Dates.monthKeysForLastDays(7, now)
        assertTrue(keys.contains(2026 to 10))
        assertTrue(keys.contains(2026 to 9))
    }

    @Test
    fun weekSummarySumsLastSevenDays() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 3, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val summary = WeekStats.last7(
            listOf(bill(3, -10.0), bill(2, -5.0), bill(1, 100.0)),
            now = now
        )
        assertEquals(7, summary.days.size)
        assertEquals(15.0, summary.total, 0.0001)
        assertEquals(10.0, summary.max, 0.0001)
    }

    @Test
    fun qianjiParsesRows() {
        val header = listOf("时间", "类型", "金额", "分类", "二级分类", "备注", "ID")
        val rows = listOf(
            listOf("2026-10-01 12:30", "支出", "12.50", "三餐", "", "午饭", "A1"),
            listOf("2026-10-02 08:00", "收入", "100", "工资", "", "工资", "A2"),
            listOf("坏时间", "支出", "1", "", "", "", "A3")
        )
        val parsed = Qianji.parseSheet(header, rows)
        assertTrue(parsed.ok)
        assertEquals(3, parsed.total)
        assertEquals(1, parsed.expense)
        assertEquals(1, parsed.income)
        assertEquals(1, parsed.skipped)
        assertEquals("2026-10-01", parsed.minDay)
        assertEquals("2026-10-02", parsed.maxDay)
        assertEquals(2, parsed.rows.size)
        assertEquals(listOf("A1", "A2"), Qianji.orderNos(parsed.rows))
    }

    @Test
    fun qianjiMappingFallsBackToOther() {
        val leaves = listOf("三餐" to "三餐", "交通" to "交通", "其它" to "其它")
        val mappings = Qianji.buildMappings(
            listOf(Qianji.CatCount("支出", "不存在", "", 3), Qianji.CatCount("支出", "三餐", "", 5)),
            leaves
        )
        assertEquals("三餐", mappings[0].target)
        assertEquals("其它", mappings[1].target)
        assertEquals(5, mappings[0].count)
        assertFalse(mappings.isEmpty())
    }
}
