package com.muyian.debt.data

import android.content.Context
import muyian.bill.listener.MuyianBillCore
import org.json.JSONArray
import org.json.JSONObject

data class BackupInfo(
    val name: String = "",
    val exportedAt: Long = 0,
    val bills: List<Bill> = emptyList(),
    val categories: CategoryData? = null
)

data class MissingCategory(
    val type: String,
    val topName: String,
    val topIcon: String,
    val subName: String = "",
    val subIcon: String = ""
)

data class BackupStats(
    val total: Int,
    val expense: Int,
    val income: Int,
    val minDay: String,
    val maxDay: String
)

data class MergeResult(val data: CategoryData, val added: Int)

data class RestoreResult(
    val inserted: Int,
    val skipped: Int,
    val catAdded: Int,
    val failed: Boolean
)

sealed class BackupParseResult {
    data class Ok(val info: BackupInfo) : BackupParseResult()
    data class Error(val message: String) : BackupParseResult()
}

/** 本应用备份的纯数据处理：页面不再拼 JSON、不再自己遍历分类。 */
object Backup {
    const val APP = "muyian"
    const val KIND = "backup"
    const val VERSION = 1
    private const val CHUNK = 400

    fun payload(bills: List<Bill>, categories: CategoryData): JSONObject = JSONObject()
        .put("app", APP)
        .put("kind", KIND)
        .put("version", VERSION)
        .put("exportedAt", System.currentTimeMillis())
        .put("categories", JSONObject(Cats.toJson(categories)))
        .put("bills", Bills.toJsonArray(bills))

    fun parse(raw: String, name: String): BackupParseResult {
        val data = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return BackupParseResult.Error("备份文件不是有效的 JSON")
        }
        if (data.optString("app") != APP || data.optJSONArray("bills") == null) {
            return BackupParseResult.Error("不是孽账的备份文件")
        }
        val version = data.optInt("version", 0)
        if (version != VERSION) {
            return BackupParseResult.Error("备份版本不支持：$version")
        }
        val bills = sanitizeBills(data.optJSONArray("bills") ?: JSONArray())
        if (bills.isEmpty()) {
            return BackupParseResult.Error("备份里没有可恢复的账单")
        }
        return BackupParseResult.Ok(
            BackupInfo(
                name = name,
                exportedAt = data.optLong("exportedAt", 0L),
                bills = bills,
                categories = data.optJSONObject("categories")?.let { Cats.fromJsonObject(it) }
            )
        )
    }

    fun sanitizeBills(arr: JSONArray): List<Bill> {
        val out = ArrayList<Bill>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val money = o.optDouble("money", 0.0)
            if (money == 0.0 || money.isNaN()) continue
            val y = o.optInt("year")
            val mo = o.optInt("month")
            val d = o.optInt("day")
            if (y < 1970 || mo !in 1..12 || d !in 1..31) continue
            out.add(
                Bill(
                    id = o.optLong("id", 0L),
                    year = y,
                    month = mo,
                    day = d,
                    clock = o.optString("clock", ""),
                    money = money,
                    bankName = o.optString("bankName", ""),
                    costType = o.optString("costType", if (money > 0) "收入" else "消费"),
                    orderRemark = o.optString("orderRemark", ""),
                    source = o.optString("source", "manual"),
                    sourcePkg = o.optString("sourcePkg", ""),
                    createdAt = o.optLong("createdAt", 0L),
                    reimb = o.optInt("reimb", 0),
                    starred = o.optInt("starred", 0),
                    orderNo = o.optString("orderNo", "")
                )
            )
        }
        return out
    }

    fun stats(bills: List<Bill>): BackupStats {
        var expense = 0
        var income = 0
        var minDay = ""
        var maxDay = ""
        for (b in bills) {
            if (b.money < 0) expense++ else income++
            val day = b.dateText
            if (minDay.isEmpty() || day < minDay) minDay = day
            if (day > maxDay) maxDay = day
        }
        return BackupStats(bills.size, expense, income, minDay, maxDay)
    }

    fun missingCategories(categories: CategoryData?, current: CategoryData): List<MissingCategory> {
        if (categories == null) return emptyList()
        val missing = ArrayList<MissingCategory>()
        for (type in listOf("expense", "income")) {
            val dst = current.list(type)
            for (top in categories.list(type)) {
                if (top.name.isEmpty()) continue
                val topIcon = top.icon.ifEmpty { Cats.DEFAULT_ICON }
                val hit = dst.firstOrNull { it.name == top.name }
                if (hit == null) {
                    missing.add(MissingCategory(type, top.name, topIcon))
                    for (sub in top.subs) {
                        if (sub.name.isNotEmpty()) {
                            missing.add(
                                MissingCategory(
                                    type, top.name, topIcon, sub.name,
                                    sub.icon.ifEmpty { Cats.DEFAULT_ICON }
                                )
                            )
                        }
                    }
                } else {
                    for (sub in top.subs) {
                        if (sub.name.isEmpty()) continue
                        if (hit.subs.none { it.name == sub.name }) {
                            missing.add(
                                MissingCategory(
                                    type, top.name, topIcon, sub.name,
                                    sub.icon.ifEmpty { Cats.DEFAULT_ICON }
                                )
                            )
                        }
                    }
                }
            }
        }
        return missing
    }

    fun mergeMissing(current: CategoryData, missing: List<MissingCategory>): MergeResult {
        if (missing.isEmpty()) return MergeResult(current, 0)
        val data = current.deepCopy()
        for (m in missing) {
            val list = data.list(m.type)
            var top = list.firstOrNull { it.name == m.topName }
            if (top == null) {
                top = Category(m.topName, m.topIcon)
                list.add(top)
            }
            if (m.subName.isNotEmpty() && top.subs.none { it.name == m.subName }) {
                top.subs.add(SubCat(m.subName, m.subIcon))
            }
        }
        return MergeResult(data, missing.size)
    }

    /** 分块恢复；页面只传账单和处理进度。 */
    suspend fun commit(
        context: Context,
        bills: List<Bill>,
        onProgress: (Int) -> Unit = {}
    ): CommitOutcome = Batch.commit(
        items = Bills.toJsonArray(bills),
        chunk = CHUNK,
        commit = { MuyianBillCore.commitBackupBills(context, it) },
        onProgress = onProgress
    )
}
