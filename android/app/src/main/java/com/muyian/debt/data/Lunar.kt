package com.muyian.debt.data

import java.util.Calendar
import java.util.TimeZone

/** 农历转换 + 节日（1900-2100），用于日历角标。 */
object Lunar {
    private val MONTHS = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
    private val DAYS = listOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    private val SOLAR_FESTIVALS = mapOf(
        "01-01" to "元旦", "02-14" to "情人节", "03-08" to "妇女节", "03-12" to "植树节",
        "04-01" to "愚人节", "05-01" to "劳动节", "05-04" to "青年节", "06-01" to "儿童节",
        "07-01" to "建党节", "08-01" to "建军节", "09-10" to "教师节", "10-01" to "国庆节",
        "10-31" to "万圣节", "12-24" to "平安夜", "12-25" to "圣诞节"
    )

    private val LUNAR_FESTIVALS = mapOf(
        "1-1" to "春节", "1-15" to "元宵节", "5-5" to "端午节", "7-7" to "七夕",
        "8-15" to "中秋节", "9-9" to "重阳节", "12-8" to "腊八"
    )

    fun leapMonth(year: Int): Int = LUNAR_INFO[year - 1900] and 0xF

    private fun leapMonthDays(year: Int): Int =
        if (leapMonth(year) != 0) (if (LUNAR_INFO[year - 1900] and 0x10000 != 0) 30 else 29) else 0

    private fun lunarMonthDays(year: Int, month: Int): Int =
        if (LUNAR_INFO[year - 1900] and (0x10000 shr month) != 0) 30 else 29

    private fun lunarYearDays(year: Int): Int {
        var sum = 348
        val info = LUNAR_INFO[year - 1900]
        var i = 0x8000
        while (i > 0x8) {
            if (info and i != 0) sum += 1
            i = i shr 1
        }
        return sum + leapMonthDays(year)
    }

    /** 公历 -> 农历。越界返回 null。 */
    fun solarToLunar(year: Int, month: Int, day: Int): IntArray? {
        if (year !in 1901..2099) return null
        val offset0 = daysBetween(1900, 1, 31, year, month, day)
        if (offset0 < 0) return null
        var offset = offset0
        var i = 1900
        var temp = 0
        while (i < 2101 && offset > 0) {
            temp = lunarYearDays(i)
            offset -= temp
            i++
        }
        if (offset < 0) {
            offset += temp
            i--
        }
        val leap = leapMonth(i)
        var isLeap = false
        var mm = 1
        while (mm < 13 && offset > 0) {
            if (leap > 0 && mm == leap + 1 && !isLeap) {
                mm--
                isLeap = true
                temp = leapMonthDays(i)
            } else {
                temp = lunarMonthDays(i, mm)
            }
            if (isLeap && mm == leap + 1) isLeap = false
            offset -= temp
            mm++
        }
        if (offset == 0 && leap > 0 && mm == leap + 1) {
            if (isLeap) isLeap = false else {
                isLeap = true
                mm--
            }
        }
        if (offset < 0) {
            offset += temp
            mm--
        }
        return intArrayOf(i, mm, offset + 1, if (isLeap) 1 else 0)
    }

    private fun daysBetween(y1: Int, m1: Int, d1: Int, y2: Int, m2: Int, d2: Int): Int {
        val tz = TimeZone.getTimeZone("UTC")
        val a = Calendar.getInstance(tz).apply {
            clear(); set(y1, m1 - 1, d1)
        }.timeInMillis
        val b = Calendar.getInstance(tz).apply {
            clear(); set(y2, m2 - 1, d2)
        }.timeInMillis
        return Math.round((b - a) / 86_400_000.0).toInt()
    }

    /** 日历角标：农历节日 > 公历节日 > 初一显示月名 > 农历日 */
    fun lunarText(year: Int, month: Int, day: Int): String {
        val solarKey = pad2(month) + "-" + pad2(day)
        val r = solarToLunar(year, month, day) ?: return SOLAR_FESTIVALS[solarKey] ?: ""
        val lunarKey = "${r[1]}-${r[2]}"
        LUNAR_FESTIVALS[lunarKey]?.let { return it }
        SOLAR_FESTIVALS[solarKey]?.let { return it }
        if (r[2] == 1) return (if (r[3] == 1) "闰" else "") + MONTHS[r[1] - 1] + "月"
        return DAYS[r[2] - 1]
    }
}
