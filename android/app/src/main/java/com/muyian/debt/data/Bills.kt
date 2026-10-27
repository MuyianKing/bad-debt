package com.muyian.debt.data

import android.content.Context
import muyian.bill.listener.MuyianBillCore
import org.json.JSONArray
import org.json.JSONObject

/** 账单写入结果：不再用 Boolean 吞掉原生返回的错误原因。 */
data class BillWriteResult(val ok: Boolean, val error: String = "") {
    companion object {
        val Success = BillWriteResult(true)
        fun fail(message: String) = BillWriteResult(false, message)
    }
}

/** 账单数据层：统一走 hybrid.kt 的原生 SQLite 实现，页面只认 Bill。 */
object Bills {
    fun parseArray(raw: String?): List<Bill> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<Bill>(arr.length())
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out.add(parse(it)) }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parse(o: JSONObject): Bill = Bill(
        id = o.optLong("id"),
        year = o.optInt("year"),
        month = o.optInt("month"),
        day = o.optInt("day"),
        clock = o.optString("clock", ""),
        money = o.optDouble("money", 0.0),
        bankName = o.optString("bankName", ""),
        costType = o.optString("costType", ""),
        orderRemark = o.optString("orderRemark", ""),
        source = o.optString("source", ""),
        sourcePkg = o.optString("sourcePkg", ""),
        createdAt = o.optLong("createdAt"),
        reimb = o.optInt("reimb", 0),
        starred = o.optInt("starred", 0),
        orderNo = o.optString("orderNo", "")
    )

    /** 账单 JSON 的唯一出站定义：备份、导入、导出都走这里，字段变更只改一处。 */
    fun toJson(b: Bill): JSONObject = JSONObject()
        .put("id", b.id)
        .put("year", b.year)
        .put("month", b.month)
        .put("day", b.day)
        .put("clock", b.clock)
        .put("money", b.money)
        .put("bankName", b.bankName)
        .put("costType", b.costType)
        .put("orderRemark", b.orderRemark)
        .put("source", b.source)
        .put("sourcePkg", b.sourcePkg)
        .put("createdAt", b.createdAt)
        .put("reimb", b.reimb)
        .put("starred", b.starred)
        .put("orderNo", b.orderNo)

    fun toJsonArray(list: List<Bill>): JSONArray {
        val arr = JSONArray()
        for (b in list) arr.put(toJson(b))
        return arr
    }

    fun month(context: Context, year: Int, month: Int): List<Bill> =
        parseArray(MuyianBillCore.getMonthBills(context, year.toString(), month.toString()))

    fun year(context: Context, year: Int): List<Bill> =
        parseArray(MuyianBillCore.getYearBills(context, year.toString()))

    fun all(context: Context): List<Bill> {
        val out = ArrayList<Bill>()
        var offset = 0
        val chunk = 1000
        while (true) {
            val raw = MuyianBillCore.getAllBills(context, offset.toString(), chunk.toString())
            val o = try { JSONObject(raw) } catch (_: Exception) { null } ?: break
            val total = o.optInt("total", 0)
            val rows = o.optJSONArray("rows") ?: break
            for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { out.add(parse(it)) }
            offset += chunk
            if (offset >= total || rows.length() == 0) break
        }
        return out
    }

    fun save(
        context: Context,
        date: String,
        amount: Double,
        isIncome: Boolean,
        costType: String,
        remark: String,
        reimb: Boolean,
        starred: Boolean
    ): BillWriteResult = write(
        MuyianBillCore.saveBillEx(
            context, date, amount.toString(), isIncome, "手动", costType, remark, reimb, starred
        )
    )

    fun update(
        context: Context,
        id: String,
        date: String,
        amount: Double,
        isIncome: Boolean,
        costType: String,
        remark: String,
        reimb: Boolean,
        starred: Boolean
    ): BillWriteResult = write(
        MuyianBillCore.updateBill(
            context, id, date, amount.toString(), isIncome, "手动", costType, remark, reimb, starred
        )
    )

    fun delete(context: Context, id: Long): Boolean = MuyianBillCore.deleteBill(context, id.toString())

    private fun write(raw: String): BillWriteResult = try {
        val o = JSONObject(raw)
        if (o.optBoolean("success", false)) BillWriteResult.Success
        else BillWriteResult.fail(o.optString("error", ""))
    } catch (_: Exception) {
        BillWriteResult.fail("write failed")
    }
}
