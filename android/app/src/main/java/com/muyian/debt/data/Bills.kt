package com.muyian.debt.data

import android.content.Context
import muyian.bill.listener.MuyianBillCore
import org.json.JSONArray
import org.json.JSONObject

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
    ): Boolean {
        val raw = MuyianBillCore.saveBillEx(
            context, date, amount.toString(), isIncome, "手动", costType, remark, reimb, starred
        )
        return try {
            JSONObject(raw).optBoolean("success", false)
        } catch (_: Exception) {
            false
        }
    }

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
    ): Boolean {
        val raw = MuyianBillCore.updateBill(
            context, id, date, amount.toString(), isIncome, "手动", costType, remark, reimb, starred
        )
        return try {
            JSONObject(raw).optBoolean("success", false)
        } catch (_: Exception) {
            false
        }
    }

    fun delete(context: Context, id: Long): Boolean = MuyianBillCore.deleteBill(context, id.toString())
}
