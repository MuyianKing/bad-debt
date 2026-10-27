package com.muyian.debt.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** 钱迹 Excel 的纯数据处理：原生只负责把 xlsx 读成二维文本，这里做账单语义。 */
object Qianji {
    const val MAX_ROWS = 20000
    private val TIME_RE = Regex("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?")

    data class SheetRow(
        val time: String, val type: String, val cat: String, val sub: String,
        val amount: String, val remark: String, val id: String,
        val day: String, val hh: String, val mm: String
    )

    data class CatCount(val type: String, val cat: String, val sub: String, val count: Int)

    data class ParsedSheet(
        val ok: Boolean, val error: String = "",
        val total: Int = 0, val expense: Int = 0, val income: Int = 0, val skipped: Int = 0,
        val minDay: String = "", val maxDay: String = "",
        val cats: List<CatCount> = emptyList(), val rows: List<SheetRow> = emptyList()
    )

    data class MappingRow(val key: String, val label: String, val count: Int, val target: String)

    private fun cell(row: List<String>, index: Int): String =
        if (index < 0 || index >= row.size) "" else row[index].trim()

    private fun columnIndex(header: List<String>): Map<String, Int> {
        val idx = mutableMapOf("time" to -1, "type" to -1, "amount" to -1, "cat" to -1, "sub" to -1, "remark" to -1, "id" to -1)
        val names = mapOf("时间" to "time", "类型" to "type", "金额" to "amount", "分类" to "cat", "二级分类" to "sub", "备注" to "remark", "ID" to "id")
        header.forEachIndexed { i, raw ->
            val key = names[raw.trim()]
            if (key != null && idx[key] == -1) idx[key] = i
        }
        return idx
    }

    fun parseSheet(header: List<String>, rawRows: List<List<String>>): ParsedSheet {
        val cols = columnIndex(header)
        val timeIdx = cols["time"] ?: -1
        val typeIdx = cols["type"] ?: -1
        val amountIdx = cols["amount"] ?: -1
        if (timeIdx < 0 || typeIdx < 0 || amountIdx < 0) {
            return ParsedSheet(false, "表头缺少 时间/类型/金额 列，请确认是钱迹导出的 Excel")
        }
        var total = 0
        var skipped = 0
        var expense = 0
        var income = 0
        var minDay = ""
        var maxDay = ""
        val catCount = LinkedHashMap<String, Int>()
        val rows = ArrayList<SheetRow>()
        for (raw in rawRows) {
            val time = cell(raw, timeIdx)
            val type = cell(raw, typeIdx)
            val amountRaw = cell(raw, amountIdx)
            if (time.isEmpty() && type.isEmpty() && amountRaw.isEmpty()) continue
            total++
            val m = TIME_RE.find(time)
            val amount = amountRaw.toDoubleOrNull()
            if (m == null || (type != "支出" && type != "收入") || amount == null || amount <= 0) {
                skipped++
                continue
            }
            val y = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val d = m.groupValues[3].toInt()
            val day = "${pad2(y)}-${pad2(mo)}-${pad2(d)}"
            if (minDay.isEmpty() || day < minDay) minDay = day
            if (day > maxDay) maxDay = day
            if (type == "支出") expense++ else income++
            val cat = cell(raw, cols["cat"] ?: -1)
            val sub = cell(raw, cols["sub"] ?: -1)
            val key = "$type|$cat|$sub"
            catCount[key] = (catCount[key] ?: 0) + 1
            rows.add(
                SheetRow(
                    time = time, type = type, cat = cat, sub = sub, amount = amountRaw,
                    remark = cell(raw, cols["remark"] ?: -1), id = cell(raw, cols["id"] ?: -1),
                    day = day, hh = m.groupValues[4], mm = m.groupValues[5]
                )
            )
            if (rows.size >= MAX_ROWS) break
        }
        val cats = catCount.map { (key, count) ->
            val parts = key.split("|")
            CatCount(parts[0], parts.getOrElse(1) { "" }, parts.getOrElse(2) { "" }, count)
        }
        return ParsedSheet(true, "", total, expense, income, skipped, minDay, maxDay, cats, rows)
    }

    fun buildLeafColumns(data: CategoryData): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        for (top in data.expense) {
            out.add(top.name to top.name)
            for (s in top.subs) out.add("${top.name} / ${s.name}" to s.name)
        }
        return out
    }

    fun buildMappings(cats: List<CatCount>, leaves: List<Pair<String, String>>): List<MappingRow> {
        val values = leaves.map { it.second }.toSet()
        val out = ArrayList<MappingRow>()
        for (c in cats) {
            if (c.type != "支出") continue
            var leaf = if (c.sub.isNotEmpty() && values.contains(c.sub)) c.sub
            else if (c.cat.isNotEmpty() && values.contains(c.cat)) c.cat
            else "其它"
            if (!values.contains(leaf)) leaf = leaves.firstOrNull()?.second ?: "其它"
            out.add(
                MappingRow(
                    key = "${c.type}|${c.cat}|${c.sub}",
                    label = if (c.sub.isNotEmpty()) "${c.cat} · ${c.sub}" else c.cat.ifEmpty { "未分类" },
                    count = c.count,
                    target = leaf
                )
            )
        }
        out.sortByDescending { it.count }
        return out
    }

    private data class TimeParts(val y: Int, val mo: Int, val d: Int, val hh: String, val mm: String)

    private fun parseTime(s: String): TimeParts? {
        val m = TIME_RE.find(s) ?: return null
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        if (mo !in 1..12 || d !in 1..31) return null
        return TimeParts(y, mo, d, m.groupValues[4], m.groupValues[5])
    }

    fun buildBillsJson(rows: List<SheetRow>, mappings: List<MappingRow>): String {
        val map = mappings.associate { it.key to it.target }
        val arr = JSONArray()
        for (r in rows) {
            val income = r.type == "收入"
            val t = parseTime(r.time) ?: continue
            val amount = r.amount.toDoubleOrNull() ?: continue
            if (amount <= 0) continue
            val o = JSONObject()
            o.put("year", t.y)
            o.put("month", t.mo)
            o.put("day", t.d)
            o.put("clock", "${t.mo}月${t.d}日 ${t.hh}:${t.mm}")
            o.put("money", if (income) amount else -amount)
            o.put("bankName", "钱迹")
            o.put("costType", if (income) "收入" else (map["${r.type}|${r.cat}|${r.sub}"] ?: "其它"))
            o.put("orderRemark", r.remark)
            o.put("reimb", 0)
            o.put("starred", 0)
            val cal = Calendar.getInstance().apply {
                set(t.y, t.mo - 1, t.d, t.hh.toIntOrNull() ?: 0, t.mm.toIntOrNull() ?: 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            o.put("createdAt", cal.timeInMillis)
            o.put("orderNo", r.id)
            arr.put(o)
        }
        return arr.toString()
    }

    fun orderNos(rows: List<SheetRow>): List<String> =
        rows.mapNotNull { it.id.trim().takeIf { v -> v.isNotEmpty() } }
}
