package com.muyian.debt.data

import java.util.Calendar

data class SubCat(val name: String, val icon: String)

data class Category(
    val name: String,
    val icon: String,
    val subs: MutableList<SubCat> = mutableListOf()
)

data class CategoryData(
    val expense: MutableList<Category>,
    val income: MutableList<Category>
) {
    fun list(type: String): MutableList<Category> = if (type == "income") income else expense

    fun deepCopy(): CategoryData = CategoryData(
        expense.toMutableList().map { it.copy(subs = it.subs.toMutableList()) }.toMutableList(),
        income.toMutableList().map { it.copy(subs = it.subs.toMutableList()) }.toMutableList()
    )
}

data class DayGroup(
    val day: Int,
    val title: String,
    val expense: Double,
    val income: Double,
    val bills: List<Bill>
)

private val IMG_TOKEN = Regex("""\{\{img:([^}]+)\}\}""")

data class Bill(
    val id: Long,
    val year: Int,
    val month: Int,
    val day: Int,
    val clock: String,
    val money: Double,
    val bankName: String,
    val costType: String,
    val orderRemark: String,
    val source: String,
    val sourcePkg: String,
    val createdAt: Long,
    val reimb: Int,
    val starred: Int,
    val orderNo: String
) {
    val isIncome: Boolean get() = money >= 0

    /** 备注里的图片 token 不参与展示 */
    val remarkText: String
        get() {
            val r = orderRemark
            val idx = r.indexOf("{{img:")
            return (if (idx >= 0) r.substring(0, idx) else r).trim()
        }

    val imgPath: String
        get() = IMG_TOKEN.find(orderRemark)?.groupValues?.get(1) ?: ""

    val displayName: String get() = remarkText.ifEmpty { costType.ifEmpty { if (isIncome) "收入" else "支出" } }

    val dateText: String get() = "$year-${pad2(month)}-${pad2(day)}"
}

data class MascotStyle(
    val id: String,
    val mood: String,
    val name: String,
    val desc: String,
    val fullAsset: String,
    val headAsset: String,
    val widthRpx: Int = 236
)

data class Mascot(
    val id: String,
    val name: String,
    val defaultStyleId: String,
    val moodAliases: Map<String, String>,
    val styles: List<MascotStyle>,
    val ready: Boolean = true
)

data class AddDraft(
    val editId: String = "",
    val isIncome: Boolean = false,
    val selectedTopIndex: Int = 0,
    val selectedSub: String = "",
    val remark: String = "",
    val expr: String = "",
    val reimb: Boolean = false,
    val starred: Boolean = false,
    val dateValue: String = todayStr(),
    val imgPath: String = ""
) {
    val isEdit: Boolean get() = editId.isNotEmpty()
}

fun pad2(n: Int): String = if (n < 10) "0$n" else n.toString()

fun todayStr(): String {
    val c = Calendar.getInstance()
    return "${c.get(Calendar.YEAR)}-${pad2(c.get(Calendar.MONTH) + 1)}-${pad2(c.get(Calendar.DAY_OF_MONTH))}"
}

fun daysInMonth(year: Int, month: Int): Int {
    val c = Calendar.getInstance()
    c.set(year, month - 1, 1)
    return c.getActualMaximum(Calendar.DAY_OF_MONTH)
}

fun weekdayCN(year: Int, month: Int, day: Int): String {
    val c = Calendar.getInstance()
    c.set(year, month - 1, day)
    return when (c.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> "周日"
        Calendar.MONDAY -> "周一"
        Calendar.TUESDAY -> "周二"
        Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"
        Calendar.FRIDAY -> "周五"
        else -> "周六"
    }
}

fun dayGroupTitle(year: Int, month: Int, day: Int): String {
    val today = Calendar.getInstance()
    val t0 = Calendar.getInstance().apply {
        set(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val d0 = Calendar.getInstance().apply {
        set(year, month - 1, day, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val diff = Math.round((t0 - d0) / 86_400_000.0).toInt()
    val suffix = when (diff) {
        0 -> "今天"
        1 -> "昨天"
        2 -> "前天"
        else -> weekdayCN(year, month, day)
    }
    return "${month}.${day} $suffix"
}

fun fmtMoney(v: Double): String {
    val neg = v < 0
    val centsTotal = Math.round(Math.abs(v) * 100)
    val whole = centsTotal / 100
    val cents = (centsTotal % 100).toInt()
    val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return (if (neg) "-" else "") + grouped + "." + pad2(cents)
}

fun fmtNum(v: Double): String {
    val neg = v < 0
    val abs = Math.abs(v)
    var s = if (abs >= 1000) fmtMoney(abs) else {
        val rounded = Math.round(abs * 100) / 100.0
        if (rounded == Math.floor(rounded)) rounded.toLong().toString() else rounded.toString()
    }
    if (s.contains('.')) s = s.trimEnd('0').trimEnd('.')
    return (if (neg) "-" else "") + s
}

fun fmtTick(v: Double): String {
    if (Math.abs(v) >= 1000) {
        val k = Math.round(v / 100) / 10.0
        return (if (v < 0) "-" else "") + (if (k == Math.floor(k)) k.toLong().toString() else k.toString()) + "K"
    }
    return (if (v == Math.floor(v)) v.toLong().toString() else v.toString())
}

fun fmtMoneyInput(v: String): String {
    val s = v.trim()
    if (!Regex("^-?\\d+(\\.\\d+)?$").matches(s)) return s
    var out = s.replace(Regex("(\\.\\d*?)0+$"), "$1").removeSuffix(".")
    if (out == "-0") out = "0"
    return out
}

/** 只含 + - 的金额表达式，非法返回 NaN */
fun evalExpr(expr: String): Double {
    val s = expr.trim()
    if (s.isEmpty() || Regex("^[+\\-.]+$").matches(s)) return Double.NaN
    var body = s.replace(Regex("[+]+$"), "")
    if (body.isEmpty() || Regex("[+\\-]$").matches(body.replace(Regex("^-"), ""))) {
        body = body.replace(Regex("[+\\-]+$"), "")
    }
    if (body.isEmpty() || body == "-") return Double.NaN
    val tokens = Regex("-?\\d*\\.?\\d+|[+\\-]").findAll(body).map { it.value }.toList()
    if (tokens.isEmpty()) return Double.NaN
    var acc = 0.0
    var op = '+'
    for (t in tokens) {
        if (t == "+" || t == "-") { op = t[0]; continue }
        val value = t.toDoubleOrNull() ?: return Double.NaN
        acc = if (op == '+') acc + value else acc - value
    }
    return Math.round(acc * 100) / 100.0
}

fun groupByDay(bills: List<Bill>, year: Int, month: Int): List<DayGroup> {
    val map = LinkedHashMap<Int, MutableList<Bill>>()
    for (b in bills) map.getOrPut(b.day) { mutableListOf() }.add(b)
    return map.keys.sortedDescending().map { day ->
        val list = map[day]!!.sortedByDescending { it.clock }
        var expense = 0.0
        var income = 0.0
        for (b in list) {
            if (b.money < 0) expense += Math.abs(b.money) else income += b.money
        }
        DayGroup(day, dayGroupTitle(year, month, day), expense, income, list)
    }
}
