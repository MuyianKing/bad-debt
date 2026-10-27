package com.muyian.debt.data

import java.util.Calendar

/** 日期/时间格式化的唯一出口，页面不再各写一遍 Calendar 拼接。 */
object Dates {
    /** 备份文件名时间戳：YYYYMMDD_HHmm */
    fun stamp(now: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return "${c.get(Calendar.YEAR)}${pad2(c.get(Calendar.MONTH) + 1)}${pad2(c.get(Calendar.DAY_OF_MONTH))}_" +
            "${pad2(c.get(Calendar.HOUR_OF_DAY))}${pad2(c.get(Calendar.MINUTE))}"
    }

    /** 展示用时间戳：YYYY-MM-DD HH:mm */
    fun format(ts: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        return "${c.get(Calendar.YEAR)}-${pad2(c.get(Calendar.MONTH) + 1)}-${pad2(c.get(Calendar.DAY_OF_MONTH))} " +
            "${pad2(c.get(Calendar.HOUR_OF_DAY))}:${pad2(c.get(Calendar.MINUTE))}"
    }

    /** 最近 n 天涉及到的月份（含跨年），用于按周加载账单。 */
    fun monthKeysForLastDays(days: Int, now: Long = System.currentTimeMillis()): List<Pair<Int, Int>> {
        val keys = LinkedHashSet<Pair<Int, Int>>()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        for (i in 0 until days) {
            keys.add(cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1))
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return keys.toList()
    }

    /** 最近 n 天的年月日，按时间正序。 */
    fun lastDays(days: Int, now: Long = System.currentTimeMillis()): List<Triple<Int, Int, Int>> {
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val todayZero = Calendar.getInstance().apply {
            set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val out = ArrayList<Triple<Int, Int, Int>>(days)
        for (i in days - 1 downTo 0) {
            val d = Calendar.getInstance().apply { timeInMillis = todayZero - i * 86_400_000L }
            out.add(
                Triple(
                    d.get(Calendar.YEAR),
                    d.get(Calendar.MONTH) + 1,
                    d.get(Calendar.DAY_OF_MONTH)
                )
            )
        }
        return out
    }
}
