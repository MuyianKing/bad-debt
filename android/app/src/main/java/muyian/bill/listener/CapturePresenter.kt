package muyian.bill.listener

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 待确认队列与弹层拉起：队列落 SQLite（pending_captures），进程重启后可恢复。
 * 弹层仍是唯一确认入口，通知仍是弹层拉不起来时的入口。
 */
internal object CapturePresenter {
    private const val TAG = "MuyianBill"
    const val MAX_QUEUE = 5
    private const val SHEET_START_WATCHDOG_MS = 5000L

    private val lock = Any()
    private val queue = ArrayList<JSONObject>()
    private val handler = Handler(Looper.getMainLooper())
    private var showing = false
    private var currentTxn = ""

    /** @return false 表示队列已满 */
    fun present(context: Context, bill: JSONObject): Boolean {
        val txnId = bill.optString("txnId")
        synchronized(lock) {
            if (queue.size >= MAX_QUEUE) return false
            if (txnId.isNotEmpty() && (currentTxn == txnId || queue.any { it.optString("txnId") == txnId })) {
                return true
            }
            queue.add(bill)
            if (txnId.isNotEmpty()) {
                CaptureLedger.enqueuePending(context.applicationContext, txnId, bill.toString())
            }
        }
        handler.post { showNext(context.applicationContext) }
        return true
    }

    /** 从待确认通知点回来：插到队首，当前弹层结束后立即显示。 */
    fun enqueueFromNotice(context: Context, billJson: String): Boolean {
        val bill = try {
            JSONObject(billJson)
        } catch (_: Exception) {
            return false
        }
        val txnId = bill.optString("txnId")
        synchronized(lock) {
            if (txnId.isNotEmpty() && (currentTxn == txnId || queue.any { it.optString("txnId") == txnId })) {
                return true
            }
            if (queue.size >= MAX_QUEUE) return false
            // 当前弹层还在时只能追加，不能插到队首：关闭时要移除的必须是正在显示的那笔
            if (showing) {
                queue.add(bill)
            } else {
                queue.add(0, bill)
            }
            if (txnId.isNotEmpty()) {
                CaptureLedger.enqueuePending(context.applicationContext, txnId, bill.toString())
            }
            if (!showing) {
                handler.post { showNext(context.applicationContext) }
            }
        }
        return true
    }

    fun restore(context: Context) {
        val pending = CaptureLedger.pending(context.applicationContext, MAX_QUEUE)
        if (pending.isEmpty()) return
        synchronized(lock) {
            if (showing) return
            queue.clear()
            for (row in pending) {
                try {
                    queue.add(JSONObject(row.billJson))
                } catch (_: Exception) {
                    CaptureLedger.removePending(context.applicationContext, row.txnId)
                }
            }
        }
        handler.post { showNext(context.applicationContext) }
    }

    fun onSheetClosed(context: Context) {
        val txn = synchronized(lock) {
            if (!showing) return
            showing = false
            val id = currentTxn
            currentTxn = ""
            if (queue.isNotEmpty()) queue.removeAt(0)
            id
        }
        if (txn.isNotEmpty()) {
            CaptureLedger.removePending(context.applicationContext, txn)
        }
        handler.postDelayed({ showNext(context.applicationContext) }, 250)
    }

    fun onSaved(context: Context, bill: JSONObject, costType: String, finalAmount: Double = 0.0) {
        val txnId = bill.optString("txnId")
        if (txnId.isNotEmpty()) {
            CaptureLedger.markState(context.applicationContext, txnId, CaptureLedger.STATE_SAVED)
            CaptureLedger.removePending(context.applicationContext, txnId)
        }
        // 用户改过金额：记一条诊断标记，按 App 统计金额识别质量
        if (finalAmount > 0) {
            val original = Math.abs(bill.optDouble("money", 0.0))
            if (original > 0 && Math.abs(original - finalAmount) > 0.005) {
                CaptureLedger.markAmountEdited(context.applicationContext, txnId)
            }
        }
        CaptureLearning.record(
            context.applicationContext,
            bill.optString("sourcePkg"),
            bill.optString("merchant"),
            costType
        )
    }

    fun onDiscarded(context: Context, bill: JSONObject) {
        val txnId = bill.optString("txnId")
        if (txnId.isNotEmpty()) {
            CaptureLedger.markState(context.applicationContext, txnId, CaptureLedger.STATE_DISCARDED)
            CaptureLedger.removePending(context.applicationContext, txnId)
        }
    }

    fun currentTxnId(): String = synchronized(lock) { currentTxn }

    fun contains(txnId: String): Boolean {
        if (txnId.isEmpty()) return false
        synchronized(lock) {
            return currentTxn == txnId || queue.any { it.optString("txnId") == txnId }
        }
    }

    fun queuedCount(): Int = synchronized(lock) { queue.size }

    fun clearQueue(context: Context) {
        synchronized(lock) {
            queue.clear()
            showing = false
            currentTxn = ""
        }
    }

    private fun showNext(context: Context) {
        var bill: JSONObject? = null
        var noticeId = 0
        synchronized(lock) {
            if (showing || queue.isEmpty()) return
            bill = queue[0]
            showing = true
            currentTxn = bill!!.optString("txnId")
            noticeId = PendingNotices.nextId(context)
        }
        val current = bill ?: return
        if (!isScreenInteractive(context)) {
            postNoticeAndDrop(context, current, noticeId)
            return
        }
        val cats = JSONArray()
        for (c in recentCostTypes(context, 10)) cats.put(c)
        val intent = Intent(context, BillSheetActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        intent.putExtra(BillSheetActivity.EXTRA_BILL, current.toString())
        intent.putExtra(BillSheetActivity.EXTRA_CATS, cats.toString())
        intent.putExtra(BillSheetActivity.EXTRA_ACCENT, SystemPerms.getCardThemeColor(context))
        intent.putExtra(BillSheetActivity.EXTRA_NOTICE_ID, noticeId)
        var started = false
        try {
            context.startActivity(intent)
            started = true
        } catch (e: Exception) {
            Log.e(TAG, "start bill sheet failed", e)
        }
        if (!started) {
            SystemPerms.markPopupBlocked(context)
            postNoticeAndDrop(context, current, noticeId)
            return
        }
        handler.postDelayed({
            if (!BillSheetActivity.isAlive() && !BillSheetActivity.wasStarted(noticeId)) {
                SystemPerms.markPopupBlocked(context)
                postNoticeAndDrop(context, current, noticeId)
            }
        }, SHEET_START_WATCHDOG_MS)
    }

    /** 弹层拉不起来：发待确认通知，把队列项移交给通知（PendingIntent 自带票据）。 */
    private fun postNoticeAndDrop(context: Context, bill: JSONObject, noticeId: Int) {
        val posted = try {
            PendingNotices.post(
                context, bill, noticeId,
                recentCostTypes(context, 10), SystemPerms.getCardThemeColor(context)
            )
        } catch (e: Exception) {
            Log.w(TAG, "post pending notice failed", e)
            false
        }
        val txn = bill.optString("txnId")
        // 通知没发出去（无权限）时保留 pending 行，下次打开 App 再恢复；否则由通知接管
        if (posted && txn.isNotEmpty()) CaptureLedger.removePending(context, txn)
        synchronized(lock) {
            showing = false
            currentTxn = ""
            if (queue.isNotEmpty()) queue.removeAt(0)
        }
        handler.postDelayed({ showNext(context) }, 250)
    }

    private fun isScreenInteractive(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            pm.isInteractive && !km.isKeyguardLocked
        } catch (_: Exception) {
            true
        }
    }

    private fun recentCostTypes(context: Context, limit: Int): ArrayList<String> {
        val out = ArrayList<String>()
        val db = BillDb.openDb(context)
        var cursor: Cursor? = null
        try {
            cursor = db.query("bills", arrayOf("costType"), null, null, null, null, "id desc")
            while (cursor.moveToNext() && out.size < limit) {
                val t = cursor.getString(0) ?: continue
                if (t.isNotEmpty() && !out.contains(t)) out.add(t)
            }
        } catch (e: Exception) {
            Log.w(TAG, "query recent cost types failed", e)
        } finally {
            cursor?.close()
            db.close()
        }
        return out
    }
}
