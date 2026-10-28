package muyian.bill.listener

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 本地分类建议：只做"商户/App → 用户最终选的分类"计数，不联网、不参与金额解析。
 * 数据量很小（最多各 200 个键），存 SharedPreferences 即可。
 */
internal object CaptureLearning {
    private const val TAG = "MuyianBill"
    private const val PREFS = "muyian_bill"
    private const val KEY_MERCHANT = "capture_merchant_category_v1"
    private const val KEY_PKG = "capture_pkg_category_v1"
    private const val MAX_KEYS = 200

    fun suggest(context: Context, pkg: String, merchant: String, direction: Direction): String {
        when (direction) {
            Direction.INCOME -> return "收入"
            Direction.REFUND -> return "退款"
            Direction.TRANSFER -> return "转账"
            Direction.REPAYMENT -> return "还款"
            else -> Unit
        }
        val mKey = CaptureText.merchantKey(merchant)
        if (mKey.isNotEmpty()) {
            val hit = topCategory(readMap(context, KEY_MERCHANT).optJSONObject(mKey))
            if (hit.isNotEmpty()) return hit
        }
        if (pkg.isNotEmpty()) {
            val hit = topCategory(readMap(context, KEY_PKG).optJSONObject(pkg))
            if (hit.isNotEmpty()) return hit
        }
        return "消费"
    }

    fun record(context: Context, pkg: String, merchant: String, costType: String) {
        val category = costType.trim()
        if (category.isEmpty() || category == "消费" || category == "收入" ||
            category == "退款" || category == "转账" || category == "还款"
        ) return
        val mKey = CaptureText.merchantKey(merchant)
        if (mKey.isNotEmpty()) {
            bump(context, KEY_MERCHANT, mKey, category)
        }
        if (pkg.isNotEmpty()) {
            bump(context, KEY_PKG, pkg, category)
        }
    }

    private fun bump(context: Context, prefKey: String, key: String, category: String) {
        try {
            val map = readMap(context, prefKey)
            val inner = map.optJSONObject(key) ?: JSONObject()
            inner.put(category, inner.optInt(category, 0) + 1)
            map.put(key, inner)
            if (map.length() > MAX_KEYS) {
                // 简单淘汰：删掉第一个键；本地学习不需要严格 LRU
                val it = map.keys()
                if (it.hasNext()) map.remove(it.next())
            }
            prefs(context).edit().putString(prefKey, map.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "record category learning failed", e)
        }
    }

    private fun topCategory(inner: JSONObject?): String {
        if (inner == null) return ""
        var best = ""
        var bestCount = 0
        val keys = inner.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val c = inner.optInt(k, 0)
            if (c > bestCount) {
                bestCount = c
                best = k
            }
        }
        return best
    }

    private fun readMap(context: Context, prefKey: String): JSONObject {
        val raw = prefs(context).getString(prefKey, "") ?: ""
        if (raw.isEmpty()) return JSONObject()
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun clear(context: Context) {
        try {
            prefs(context).edit().remove(KEY_MERCHANT).remove(KEY_PKG).apply()
        } catch (e: Exception) {
            Log.w(TAG, "clear category learning failed", e)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
