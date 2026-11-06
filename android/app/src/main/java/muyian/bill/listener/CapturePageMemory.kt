package muyian.bill.listener

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 结果页现场：一张成功页被处理过之后，只要页面内容没有变化，就不应该再次弹层。
 *
 * 账本里的 `capture:`（5 分钟）与 `bill:`（2 分钟）是时间窗口。实测 2026-11-06 11:35
 * 付了 15.80，11:40 再点开支付宝时结果页还停在那一屏，时间窗口已过期，重开 App 又
 * 换掉了支付会话，于是同一张页面被当成新交易又弹了一次。
 *
 * 这里补一层按页面内容的记忆：记下已处理页面的指纹（包名 + 金额 + 方向 + 商户）。
 * - 同一指纹再次出现 → 还是那张页面，判重复；
 * - 观察到同一 App 的其它页面（收银台、账单、失败页…）→ 页面已经翻过去了，
 *   解除记录，之后同商户同金额的第二笔仍会正常弹层；
 * - 记录最多保活 [TTL_MS]，隔天同商户同金额的真实第二笔不会被历史页面挡住。
 * 只对屏幕采集生效，通知渠道仍走账本键。
 */
internal object CapturePageMemory {
    private const val TAG = "MuyianBill"
    private const val PREFS = "muyian_bill"
    private const val KEY = "capture_page_v1"

    /** 页面一直停在前台时不该被时间判定成“新交易”，但也不能无限期挡住同价第二笔。 */
    internal const val TTL_MS = 30L * 60 * 1000L

    /** 页面停留期间每次解析都刷新时间，没必要每个事件都落盘。 */
    private const val TOUCH_MS = 60L * 1000L

    private val lock = Any()

    internal data class Record(val pkg: String, val fingerprint: String, val at: Long)

    internal fun isSuccess(kind: PageKind): Boolean = when (kind) {
        PageKind.PAY_SUCCESS, PageKind.TRANSFER_SUCCESS, PageKind.RECEIVE_SUCCESS,
        PageKind.REFUND_SUCCESS, PageKind.REPAYMENT_SUCCESS -> true
        else -> false
    }

    /** 纯判定：同一 App、同一页面指纹，且还在保活期内。 */
    internal fun isReplay(record: Record?, pkg: String, fingerprint: String, now: Long): Boolean {
        if (record == null || fingerprint.isEmpty()) return false
        if (record.pkg != pkg || record.fingerprint != fingerprint) return false
        return now >= record.at && now - record.at <= TTL_MS
    }

    fun isReplay(context: Context, pkg: String, facts: Facts, now: Long): Boolean {
        val fingerprint = CaptureIdentity.captureFingerprint(pkg, facts)
        return isReplay(load(context), pkg, fingerprint, now)
    }

    /**
     * 每解析出一页都调用一次。同 App 出现别的页面就解除记录；
     * [PageKind.UNKNOWN] 不算数——支付宝结果页是渐进渲染的，解析中途会短暂落到未识别。
     */
    fun observe(context: Context, pkg: String, kind: PageKind, facts: Facts?) {
        if (pkg.isEmpty()) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val record = load(context) ?: return
            if (record.pkg != pkg) return
            if (!isSuccess(kind)) {
                if (kind != PageKind.UNKNOWN) clear(context)
                return
            }
            val fingerprint = facts?.let { CaptureIdentity.captureFingerprint(pkg, it) } ?: return
            if (fingerprint != record.fingerprint) {
                // 同一 App 出现了另一张结果页，说明已经翻过页，上一张的现场作废
                clear(context)
            } else if (now - record.at > TOUCH_MS) {
                save(context, record.copy(at = now))
            }
        }
    }

    /** 弹层真正露出来之后才落记录：被门禁拒掉的页面不算“已处理”。 */
    fun remember(context: Context, pkg: String, facts: Facts, at: Long) {
        if (pkg.isEmpty()) return
        val fingerprint = CaptureIdentity.captureFingerprint(pkg, facts)
        if (fingerprint.isEmpty()) return
        synchronized(lock) { save(context, Record(pkg, fingerprint, at)) }
    }

    fun clear(context: Context) {
        try {
            prefs(context).edit().remove(KEY).apply()
        } catch (e: Exception) {
            Log.w(TAG, "clear page memory failed", e)
        }
    }

    private fun load(context: Context): Record? {
        val raw = try {
            prefs(context).getString(KEY, "") ?: ""
        } catch (_: Exception) {
            ""
        }
        if (raw.isEmpty()) return null
        return try {
            val o = JSONObject(raw)
            val pkg = o.optString("pkg", "")
            val fingerprint = o.optString("fp", "")
            if (pkg.isEmpty() || fingerprint.isEmpty()) return null
            Record(pkg, fingerprint, o.optLong("at", 0L))
        } catch (_: Exception) {
            null
        }
    }

    private fun save(context: Context, record: Record) {
        try {
            val o = JSONObject()
            o.put("pkg", record.pkg)
            o.put("fp", record.fingerprint)
            o.put("at", record.at)
            prefs(context).edit().putString(KEY, o.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "save page memory failed", e)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
