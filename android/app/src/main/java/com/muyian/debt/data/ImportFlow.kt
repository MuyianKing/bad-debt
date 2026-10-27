package com.muyian.debt.data

import android.content.Context
import muyian.bill.listener.MuyianBillCore
import org.json.JSONArray
import org.json.JSONObject

data class ImportSummary(
    val total: Int = 0,
    val expense: Int = 0,
    val income: Int = 0,
    val skipped: Int = 0,
    val minDay: String = "",
    val maxDay: String = "",
    val dupInDb: Int = 0
)

sealed class ImportLoadResult {
    data class Ok(
        val fileName: String,
        val summary: ImportSummary,
        val mappings: List<Qianji.MappingRow>,
        val rows: List<Qianji.SheetRow>
    ) : ImportLoadResult()

    data class Error(val message: String) : ImportLoadResult()
}

/** 钱迹导入的流程层：读表、解析、去重计数、分块入库都在这里，页面只做状态切换。 */
object ImportFlow {
    private const val CHUNK = 200
    private const val ROW_PAGE = 500

    fun load(
        context: Context,
        path: String,
        name: String,
        leafColumns: List<Pair<String, String>>
    ): ImportLoadResult {
        if (path.isEmpty()) return ImportLoadResult.Error("文件路径无效，请重试")

        val sheet = try {
            JSONObject(MuyianBillCore.readImportSheet(path))
        } catch (_: Exception) {
            JSONObject()
        }
        if (!sheet.optBoolean("ok")) {
            return ImportLoadResult.Error(sheet.optString("error", "读取文件失败"))
        }

        val headerArr = sheet.optJSONArray("header") ?: JSONArray()
        val header = ArrayList<String>(headerArr.length())
        for (i in 0 until headerArr.length()) header.add(headerArr.optString(i, ""))

        val raw = fetchAllRows() ?: return ImportLoadResult.Error("读取表格失败，请重选文件")
        val parsed = Qianji.parseSheet(header, raw)
        if (!parsed.ok) return ImportLoadResult.Error(parsed.error)
        if (parsed.rows.isEmpty()) return ImportLoadResult.Error("文件里没有可导入的账单")

        var dupInDb = 0
        val orderNos = Qianji.orderNos(parsed.rows)
        if (orderNos.isNotEmpty()) {
            val arr = JSONArray()
            orderNos.forEach { arr.put(it) }
            val dup = try {
                JSONObject(MuyianBillCore.countExistingOrders(context, arr.toString()))
            } catch (_: Exception) {
                JSONObject()
            }
            dupInDb = dup.optInt("count", 0)
        }

        return ImportLoadResult.Ok(
            fileName = name.ifEmpty { "钱迹账单.xlsx" },
            summary = ImportSummary(
                total = parsed.total,
                expense = parsed.expense,
                income = parsed.income,
                skipped = parsed.skipped,
                minDay = parsed.minDay,
                maxDay = parsed.maxDay,
                dupInDb = dupInDb
            ),
            mappings = Qianji.buildMappings(parsed.cats, leafColumns),
            rows = parsed.rows
        )
    }

    fun billsJson(rows: List<Qianji.SheetRow>, mappings: List<Qianji.MappingRow>): String =
        Qianji.buildBillsJson(rows, mappings)

    suspend fun commit(
        context: Context,
        billsJson: String,
        onProgress: (Int) -> Unit = {}
    ): CommitOutcome {
        val all = try {
            JSONArray(billsJson)
        } catch (_: Exception) {
            JSONArray()
        }
        if (all.length() == 0) {
            return CommitOutcome(failed = true, error = "没有可导入的账单")
        }
        return Batch.commit(
            items = all,
            chunk = CHUNK,
            commit = { MuyianBillCore.commitImportBills(context, it) },
            onProgress = onProgress
        )
    }

    private fun fetchAllRows(): List<List<String>>? {
        val all = ArrayList<List<String>>()
        var off = 0
        var nativeTotal = 0
        while (true) {
            val resp = try {
                JSONObject(MuyianBillCore.getImportSheetRows(off.toString(), ROW_PAGE.toString()))
            } catch (_: Exception) {
                return null
            }
            val arr = resp.optJSONArray("rows") ?: return null
            nativeTotal = resp.optInt("total", 0)
            for (i in 0 until arr.length()) {
                val r = arr.optJSONArray(i) ?: continue
                val list = ArrayList<String>(r.length())
                for (j in 0 until r.length()) list.add(r.optString(j, ""))
                all.add(list)
            }
            off += ROW_PAGE
            if (off >= nativeTotal || arr.length() == 0) break
        }
        return if (all.size == nativeTotal) all else null
    }
}
