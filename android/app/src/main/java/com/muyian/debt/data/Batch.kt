package com.muyian.debt.data

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

data class CommitOutcome(
    val inserted: Int = 0,
    val skipped: Int = 0,
    val failed: Boolean = false,
    val error: String = ""
)

/** 分块写库的唯一实现：导入和备份恢复共用，避免两套 for 循环各写一遍。 */
object Batch {
    suspend fun commit(
        items: JSONArray,
        chunk: Int,
        commit: (String) -> String,
        onProgress: (Int) -> Unit = {}
    ): CommitOutcome {
        var inserted = 0
        var skipped = 0
        var i = 0
        while (i < items.length()) {
            val end = minOf(i + chunk, items.length())
            val part = JSONArray()
            for (j in i until end) part.put(items.getJSONObject(j))
            val resp = try {
                JSONObject(commit(part.toString()))
            } catch (_: Exception) {
                JSONObject()
            }
            if (!resp.optBoolean("success", false)) {
                return CommitOutcome(inserted, skipped, true, resp.optString("error", ""))
            }
            inserted += resp.optInt("inserted", 0)
            skipped += resp.optInt("skipped", 0)
            i = end
            onProgress(i)
            delay(30)
        }
        return CommitOutcome(inserted, skipped)
    }
}
