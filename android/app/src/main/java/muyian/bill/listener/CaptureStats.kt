package muyian.bill.listener

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * 诊断统计：只读 capture_trace / capture_feedback，不参与捕获决策。
 * - 漏斗：捕获 / 拒绝 / 合并 来自决策日志；保存 / 丢弃 / 改金额 来自反馈表。
 * - 双通道对账：屏幕与通知的成交按“同额 + 2 分钟内”贪心配对，用于估算漏采，仅作参考。
 */
internal object CaptureStats {
    private const val TAG = "MuyianBill"
    private const val MATCH_WINDOW_MS = 2L * 60 * 1000L

    data class AppStats(
        val pkg: String,
        val captured: Int,
        val saved: Int,
        val discarded: Int,
        val rejected: Int,
        val edited: Int
    )

    data class KindStats(
        val kind: String,
        val captured: Int,
        val saved: Int,
        val discarded: Int,
        val rejected: Int,
        val edited: Int
    )

    data class ReconcileStats(
        val screen: Int,
        val notify: Int,
        val matched: Int,
        val screenOnly: Int,
        val notifyOnly: Int
    )

    data class TodayStats(
        val captured: Int,
        val saved: Int,
        val discarded: Int,
        val rejected: Int,
        val merged: Int,
        val edited: Int,
        val reasons: Map<String, Int>,
        val apps: List<AppStats>,
        val kinds: List<KindStats>,
        val reconcile: ReconcileStats
    )

    fun today(context: Context, dayStart: Long): TodayStats {
        var captured = 0
        var rejected = 0
        var merged = 0
        var saved = 0
        var discarded = 0
        var edited = 0
        val reasons = HashMap<String, Int>()
        val appCaptured = HashMap<String, Int>()
        val appRejected = HashMap<String, Int>()
        val appSaved = HashMap<String, Int>()
        val appDiscarded = HashMap<String, Int>()
        val appEdited = HashMap<String, Int>()
        val kindCaptured = HashMap<String, Int>()
        val kindRejected = HashMap<String, Int>()
        val kindSaved = HashMap<String, Int>()
        val kindDiscarded = HashMap<String, Int>()
        val kindEdited = HashMap<String, Int>()

        val db = BillDb.openDb(context)
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "select pkg,kind,result,reason,count(*) from capture_trace " +
                    "where created_at>=? and result in ('accepted','rejected','duplicate','merged') " +
                    "group by pkg,kind,result,reason",
                arrayOf(dayStart.toString())
            )
            while (cursor.moveToNext()) {
                val pkg = cursor.getString(0) ?: ""
                val kind = cursor.getString(1) ?: ""
                val result = cursor.getString(2) ?: ""
                val reason = cursor.getString(3) ?: ""
                val count = cursor.getInt(4)
                when (result) {
                    "accepted" -> {
                        captured += count
                        bump(appCaptured, pkg, count)
                        bump(kindCaptured, kind, count)
                    }
                    "merged" -> {
                        merged += count
                    }
                    else -> {
                        rejected += count
                        bump(appRejected, pkg, count)
                        bump(kindRejected, kind, count)
                    }
                }
                if (reason.isNotEmpty() &&
                    (result == "rejected" || result == "duplicate" || result == "merged")
                ) {
                    bump(reasons, reason, count)
                }
            }
            cursor.close()
            cursor = null

            cursor = db.rawQuery(
                "select pkg,kind,outcome,count(*),sum(amount_edited) from capture_feedback " +
                    "where created_at>=? group by pkg,kind,outcome",
                arrayOf(dayStart.toString())
            )
            while (cursor.moveToNext()) {
                val pkg = cursor.getString(0) ?: ""
                val kind = cursor.getString(1) ?: ""
                val outcome = cursor.getString(2) ?: ""
                val count = cursor.getInt(3)
                val edits = cursor.getInt(4)
                if (outcome == CaptureLedger.STATE_SAVED) {
                    saved += count
                    bump(appSaved, pkg, count)
                    bump(kindSaved, kind, count)
                } else if (outcome == CaptureLedger.STATE_DISCARDED) {
                    discarded += count
                    bump(appDiscarded, pkg, count)
                    bump(kindDiscarded, kind, count)
                }
                if (edits > 0) {
                    edited += edits
                    bump(appEdited, pkg, edits)
                    bump(kindEdited, kind, edits)
                }
            }
            cursor.close()
            cursor = null

            val reconcile = reconcile(db, dayStart)

            val apps = (appCaptured.keys + appRejected.keys + appSaved.keys +
                appDiscarded.keys + appEdited.keys)
                .filter { it.isNotEmpty() }
                .map { pkg ->
                    AppStats(
                        pkg = pkg,
                        captured = appCaptured[pkg] ?: 0,
                        saved = appSaved[pkg] ?: 0,
                        discarded = appDiscarded[pkg] ?: 0,
                        rejected = appRejected[pkg] ?: 0,
                        edited = appEdited[pkg] ?: 0
                    )
                }
                .sortedWith(
                    compareByDescending<AppStats> { it.captured }
                        .thenByDescending { it.saved }
                        .thenByDescending { it.edited }
                )

            val kinds = (kindCaptured.keys + kindRejected.keys + kindSaved.keys +
                kindDiscarded.keys + kindEdited.keys)
                .filter { it.isNotEmpty() }
                .map { kind ->
                    KindStats(
                        kind = kind,
                        captured = kindCaptured[kind] ?: 0,
                        saved = kindSaved[kind] ?: 0,
                        discarded = kindDiscarded[kind] ?: 0,
                        rejected = kindRejected[kind] ?: 0,
                        edited = kindEdited[kind] ?: 0
                    )
                }
                .sortedWith(
                    compareByDescending<KindStats> { it.captured }
                        .thenByDescending { it.saved }
                        .thenByDescending { it.edited }
                )

            return TodayStats(
                captured = captured,
                saved = saved,
                discarded = discarded,
                rejected = rejected,
                merged = merged,
                edited = edited,
                reasons = reasons,
                apps = apps,
                kinds = kinds,
                reconcile = reconcile
            )
        } catch (e: Exception) {
            Log.w(TAG, "query capture stats failed", e)
            return TodayStats(
                captured = captured,
                saved = saved,
                discarded = discarded,
                rejected = rejected,
                merged = merged,
                edited = edited,
                reasons = reasons,
                apps = emptyList(),
                kinds = emptyList(),
                reconcile = ReconcileStats(0, 0, 0, 0, 0)
            )
        } finally {
            cursor?.close()
            db.close()
        }
    }

    internal data class CapturePoint(
        val source: String,
        val amount: Double,
        val channel: String,
        val at: Long
    )

    /** 同额、2 分钟内的屏幕/通知成交做贪心配对；channel 不硬性要求，只做时间打平后的偏好。 */
    internal fun reconcilePoints(
        screens: List<CapturePoint>,
        notices: List<CapturePoint>
    ): ReconcileStats {
        val used = BooleanArray(notices.size)
        var matched = 0
        var screenOnly = 0
        for (screen in screens) {
            var best = -1
            var bestScore = Long.MAX_VALUE
            for (i in notices.indices) {
                if (used[i]) continue
                val notice = notices[i]
                if (Math.abs(notice.amount - screen.amount) > 0.005) continue
                val dt = Math.abs(notice.at - screen.at)
                if (dt > MATCH_WINDOW_MS) continue
                val channelPenalty = if (
                    screen.channel.isEmpty() || notice.channel.isEmpty() ||
                    screen.channel == notice.channel
                ) 0L else 60_000L
                val score = dt + channelPenalty
                if (score < bestScore) {
                    bestScore = score
                    best = i
                }
            }
            if (best >= 0) {
                used[best] = true
                matched++
            } else {
                screenOnly++
            }
        }
        val notifyOnly = used.indices.count { !used[it] }
        return ReconcileStats(
            screen = screens.size,
            notify = notices.size,
            matched = matched,
            screenOnly = screenOnly,
            notifyOnly = notifyOnly
        )
    }

    private fun reconcile(db: SQLiteDatabase, dayStart: Long): ReconcileStats {
        val points = ArrayList<CapturePoint>()
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "select source,amount,channel,created_at from capture_trace " +
                    "where created_at>=? and result in ('accepted','merged') and amount>0",
                arrayOf(dayStart.toString())
            )
            while (cursor.moveToNext()) {
                points.add(
                    CapturePoint(
                        source = cursor.getString(0) ?: "",
                        amount = cursor.getDouble(1),
                        channel = cursor.getString(2) ?: "",
                        at = cursor.getLong(3)
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "reconcile query failed", e)
        } finally {
            cursor?.close()
        }

        return reconcilePoints(
            screens = points.filter { it.source == "screen" },
            notices = points.filter { it.source == "notify" }
        )
    }

    private fun bump(map: HashMap<String, Int>, key: String, count: Int) {
        map[key] = (map[key] ?: 0) + count
    }
}
