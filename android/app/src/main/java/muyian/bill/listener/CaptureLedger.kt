package muyian.bill.listener

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * 捕获账本：交易身份、去重、待确认队列、诊断记录的唯一边界。
 * 所有去重都在一个事务里原子裁决，跨进程/重启有效；不再用 SharedPreferences 存去重 map。
 */
internal object CaptureLedger {
    private const val TAG = "MuyianBill"

    const val STATE_QUEUED = "queued"
    const val STATE_SAVED = "saved"
    const val STATE_DISCARDED = "discarded"
    const val STATE_REJECTED = "rejected"

    private const val TRACE_MAX = 500
    private const val TRACE_TTL_MS = 7L * 24 * 3600 * 1000L
    private const val PENDING_TTL_MS = 24L * 3600 * 1000L

    @Volatile
    private var lastPurgeAt = 0L

    enum class ClaimKind { CLAIMED, MERGED, DUPLICATE }

    data class Claim(
        val kind: ClaimKind,
        val txnId: String,
        val reason: Reason? = null
    )

    data class PendingRow(
        val txnId: String,
        val billJson: String,
        val createdAt: Long
    )

    data class TraceRow(
        val id: Long,
        val createdAt: Long,
        val pkg: String,
        val source: String,
        val kind: String,
        val result: String,
        val reason: String,
        val txnId: String,
        val amount: Double,
        val direction: String,
        val channel: String,
        val orderNo: String,
        val confidence: Double,
        val needsReview: Boolean,
        val text: String
    )

    fun ensureSchema(db: SQLiteDatabase) {
        db.execSQL(
            "create table if not exists capture_ledger(" +
                "id integer primary key autoincrement," +
                "key text not null," +
                "kind text not null," +
                "txn_id text not null," +
                "pkg text not null default ''," +
                "amount real not null default 0," +
                "direction text not null default ''," +
                "channel text not null default ''," +
                "order_no text not null default ''," +
                "state text not null default 'queued'," +
                "reason text not null default ''," +
                "confidence real not null default 0," +
                "needs_review integer not null default 0," +
                "created_at integer not null," +
                "expires_at integer not null)"
        )
        db.execSQL("create unique index if not exists idx_capture_ledger_key on capture_ledger(key)")
        db.execSQL("create index if not exists idx_capture_ledger_exp on capture_ledger(expires_at)")
        db.execSQL("create index if not exists idx_capture_ledger_order on capture_ledger(order_no)")
        db.execSQL("create index if not exists idx_capture_ledger_txn on capture_ledger(txn_id)")
        db.execSQL(
            "create table if not exists pending_captures(" +
                "txn_id text primary key," +
                "bill_json text not null," +
                "created_at integer not null," +
                "attempts integer not null default 0)"
        )
        db.execSQL(
            "create table if not exists capture_trace(" +
                "id integer primary key autoincrement," +
                "created_at integer not null," +
                "pkg text not null default ''," +
                "source text not null default ''," +
                "kind text not null default ''," +
                "result text not null default ''," +
                "reason text not null default ''," +
                "txn_id text not null default ''," +
                "amount real not null default 0," +
                "direction text not null default ''," +
                "channel text not null default ''," +
                "order_no text not null default ''," +
                "confidence real not null default 0," +
                "needs_review integer not null default 0," +
                "text_hash text not null default ''," +
                "text text not null default '')"
        )
        db.execSQL("create index if not exists idx_capture_trace_created on capture_trace(created_at)")
        try {
            db.execSQL("alter table capture_trace add column txn_id text not null default ''")
        } catch (_: Exception) {
            // column already exists
        }
        db.execSQL("create index if not exists idx_capture_trace_txn on capture_trace(txn_id)")
        // 账单表的去重/核对索引：不再全表扫描
        try {
            db.execSQL("create index if not exists idx_bills_created on bills(createdAt)")
        } catch (_: Exception) {
        }
        try {
            db.execSQL("create index if not exists idx_bills_order on bills(orderNo)")
        } catch (_: Exception) {
        }
    }

    /**
     * 原子占位：先查全部身份键，命中同会话/同单号 → 合并；命中页面/捕获/账单 → 重复；
     * 否则插入并返回 CLAIMED。
     */
    fun claim(context: Context, candidate: Candidate): Claim {
        val db = BillDb.openDb(context)
        try {
            maybePurge(db)
            db.beginTransaction()
            try {
                val hits = queryHits(db, candidate.keys, System.currentTimeMillis())
                if (hits.isNotEmpty()) {
                    val mergeHit = hits.firstOrNull { it.second == "session" }
                        ?: hits.firstOrNull { it.second == "order" }
                    if (mergeHit != null) {
                        val txnId = mergeHit.first
                        for (key in candidate.keys) {
                            if (!hits.any { it.third == key.key }) {
                                insertKey(db, key, txnId, candidate, STATE_QUEUED)
                            }
                        }
                        touchLedger(db, txnId, candidate)
                        db.setTransactionSuccessful()
                        return Claim(ClaimKind.MERGED, txnId)
                    }
                    // 页面/捕获/账单键直接判重
                    val duplicate = hits.first()
                    val reason = when (duplicate.second) {
                        "page" -> Reason.DUPLICATE_PAGE
                        "capture" -> Reason.DUPLICATE_CAPTURE
                        "bill" -> Reason.DUPLICATE_BILL
                        else -> Reason.DUPLICATE_CAPTURE
                    }
                    db.setTransactionSuccessful()
                    return Claim(ClaimKind.DUPLICATE, duplicate.first, reason)
                }

                // 已入账账单兜底：单号优先，其次同渠道+金额+2 分钟
                val orderNo = candidate.facts.orderNo
                if (orderNo.isNotEmpty()) {
                    if (orderExists(db, orderNo)) {
                        db.setTransactionSuccessful()
                        return Claim(ClaimKind.DUPLICATE, candidate.txnId, Reason.DUPLICATE_ORDER)
                    }
                } else if (billExists(db, candidate)) {
                    db.setTransactionSuccessful()
                    return Claim(ClaimKind.DUPLICATE, candidate.txnId, Reason.DUPLICATE_BILL)
                }

                for (key in candidate.keys) {
                    insertKey(db, key, candidate.txnId, candidate, STATE_QUEUED)
                }
                db.setTransactionSuccessful()
                return Claim(ClaimKind.CLAIMED, candidate.txnId)
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.e(TAG, "claim capture failed", e)
            // fail-closed：账本不可用时宁可漏弹，也不能放行导致同一笔重复弹层
            return Claim(ClaimKind.DUPLICATE, candidate.txnId, Reason.DUPLICATE_CAPTURE)
        } finally {
            db.close()
        }
    }

    /** 账本/队列的过期清理最多每小时一次，避免每次捕获都做写操作。 */
    private fun maybePurge(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        if (now - lastPurgeAt < 60L * 60 * 1000L) return
        lastPurgeAt = now
        try {
            db.delete("capture_ledger", "expires_at < ?", arrayOf(now.toString()))
            db.delete("pending_captures", "created_at < ?", arrayOf((now - PENDING_TTL_MS).toString()))
        } catch (e: Exception) {
            Log.w(TAG, "periodic purge failed", e)
        }
    }

    private fun queryHits(
        db: SQLiteDatabase, keys: List<LedgerKey>, now: Long
    ): List<Triple<String, String, String>> {
        if (keys.isEmpty()) return emptyList()
        val placeholders = keys.joinToString(",") { "?" }
        val args = ArrayList<String>()
        for (key in keys) args.add(key.key)
        args.add(now.toString())
        val out = ArrayList<Triple<String, String, String>>()
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "select txn_id, kind, key from capture_ledger where key in ($placeholders) and expires_at > ?",
                args.toTypedArray()
            )
            while (cursor.moveToNext()) {
                out.add(Triple(cursor.getString(0) ?: "", cursor.getString(1) ?: "", cursor.getString(2) ?: ""))
            }
        } catch (e: Exception) {
            Log.w(TAG, "query ledger failed", e)
        } finally {
            cursor?.close()
        }
        return out
    }

    private fun insertKey(
        db: SQLiteDatabase, key: LedgerKey, txnId: String, candidate: Candidate, state: String
    ) {
        val cv = ContentValues()
        cv.put("key", key.key)
        cv.put("kind", key.kind)
        cv.put("txn_id", txnId)
        cv.put("pkg", candidate.sourcePkg)
        cv.put("amount", candidate.facts.amount)
        cv.put("direction", candidate.facts.direction.name)
        cv.put("channel", candidate.facts.channel)
        cv.put("order_no", candidate.facts.orderNo)
        cv.put("state", state)
        cv.put("reason", "")
        cv.put("confidence", candidate.facts.confidence)
        cv.put("needs_review", if (candidate.facts.needsReview) 1 else 0)
        cv.put("created_at", candidate.createdAt)
        cv.put("expires_at", candidate.createdAt + key.ttlMs)
        // REPLACE 而不是 IGNORE：过期但尚未被 purge 的旧键必须被这次捕获刷新，
        // 否则新交易在旧行过期后仍会被唯一索引挡住，后续去重窗口失效。
        db.insertWithOnConflict("capture_ledger", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun touchLedger(db: SQLiteDatabase, txnId: String, candidate: Candidate) {
        val cv = ContentValues()
        cv.put("amount", candidate.facts.amount)
        cv.put("channel", candidate.facts.channel)
        if (candidate.facts.orderNo.isNotEmpty()) cv.put("order_no", candidate.facts.orderNo)
        cv.put("confidence", candidate.facts.confidence)
        cv.put("needs_review", if (candidate.facts.needsReview) 1 else 0)
        val where = "txn_id=?"
        try {
            db.update("capture_ledger", cv, where, arrayOf(txnId))
        } catch (e: Exception) {
            Log.w(TAG, "touch ledger failed", e)
        }
    }

    private fun orderExists(db: SQLiteDatabase, orderNo: String): Boolean {
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "select id from bills where orderNo=? and source in ('screen','notify') limit 1",
                arrayOf(orderNo)
            )
            return cursor.moveToFirst()
        } catch (e: Exception) {
            return false
        } finally {
            cursor?.close()
        }
    }

    private fun billExists(db: SQLiteDatabase, candidate: Candidate): Boolean {
        val signed = CaptureGates.signedAmount(candidate.facts)
        val from = candidate.createdAt - 2L * 60 * 1000L
        val to = candidate.createdAt + 2L * 60 * 1000L
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "select id from bills where bankName=? and money between ? and ? " +
                    "and createdAt between ? and ? and source in ('screen','notify') limit 1",
                arrayOf(
                    candidate.facts.channel,
                    (signed - 0.005).toString(),
                    (signed + 0.005).toString(),
                    from.toString(),
                    to.toString()
                )
            )
            return cursor.moveToFirst()
        } catch (e: Exception) {
            return false
        } finally {
            cursor?.close()
        }
    }

    // ---------- 待确认队列 ----------

    fun enqueuePending(context: Context, txnId: String, billJson: String): Boolean {
        val db = BillDb.openDb(context)
        return try {
            val cv = ContentValues()
            cv.put("txn_id", txnId)
            cv.put("bill_json", billJson)
            cv.put("created_at", System.currentTimeMillis())
            cv.put("attempts", 0)
            db.insertWithOnConflict("pending_captures", null, cv, SQLiteDatabase.CONFLICT_REPLACE) > 0
        } catch (e: Exception) {
            Log.e(TAG, "enqueue pending failed", e)
            false
        } finally {
            db.close()
        }
    }

    fun removePending(context: Context, txnId: String) {
        if (txnId.isEmpty()) return
        val db = BillDb.openDb(context)
        try {
            db.delete("pending_captures", "txn_id=?", arrayOf(txnId))
        } catch (e: Exception) {
            Log.w(TAG, "remove pending failed", e)
        } finally {
            db.close()
        }
    }

    /** 占位后没能入队（队列满/异常）时释放全部身份键，允许下一次事件重新捕获。 */
    fun release(context: Context, txnId: String) {
        if (txnId.isEmpty()) return
        val db = BillDb.openDb(context)
        try {
            db.delete("capture_ledger", "txn_id=?", arrayOf(txnId))
            db.delete("pending_captures", "txn_id=?", arrayOf(txnId))
        } catch (e: Exception) {
            Log.w(TAG, "release capture claim failed", e)
        } finally {
            db.close()
        }
    }

    fun pending(context: Context, limit: Int = 10): List<PendingRow> {
        val out = ArrayList<PendingRow>()
        val db = BillDb.openDb(context)
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "select txn_id, bill_json, created_at from pending_captures order by created_at asc limit " + limit,
                null
            )
            while (cursor.moveToNext()) {
                out.add(
                    PendingRow(
                        cursor.getString(0) ?: "",
                        cursor.getString(1) ?: "",
                        cursor.getLong(2)
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "query pending failed", e)
        } finally {
            cursor?.close()
            db.close()
        }
        return out
    }

    fun countPending(context: Context): Int {
        val db = BillDb.openDb(context)
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("select count(*) from pending_captures", null)
            if (cursor.moveToFirst()) return cursor.getInt(0)
        } catch (_: Exception) {
        } finally {
            cursor?.close()
            db.close()
        }
        return 0
    }

    /** 同一笔在宿主/支付侧各被捕获一次时，把后到的权威字段补进已排队账单。 */
    fun mergePending(context: Context, txnId: String, incomingJson: String, incomingIsPay: Boolean) {
        if (txnId.isEmpty()) return
        val db = BillDb.openDb(context)
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "select bill_json from pending_captures where txn_id=? limit 1", arrayOf(txnId)
            )
            if (!cursor.moveToFirst()) return
            val target = try {
                org.json.JSONObject(cursor.getString(0) ?: "{}")
            } catch (_: Exception) {
                return
            }
            if (incomingIsPay) {
                val incoming = try {
                    org.json.JSONObject(incomingJson)
                } catch (_: Exception) {
                    return
                }
                target.put("money", incoming.optDouble("money", target.optDouble("money", 0.0)))
                target.put("bankName", incoming.optString("bankName", target.optString("bankName")))
                if (target.optString("orderNo").isEmpty() && incoming.optString("orderNo").isNotEmpty()) {
                    target.put("orderNo", incoming.optString("orderNo"))
                }
                if (target.optString("merchant").isEmpty() && incoming.optString("merchant").isNotEmpty()) {
                    target.put("merchant", incoming.optString("merchant"))
                }
                if (!target.optBoolean("needsReview") && incoming.optBoolean("needsReview")) {
                    target.put("needsReview", true)
                }
            }
            val cv = ContentValues()
            cv.put("bill_json", target.toString())
            db.update("pending_captures", cv, "txn_id=?", arrayOf(txnId))
        } catch (e: Exception) {
            Log.w(TAG, "merge pending failed", e)
        } finally {
            cursor?.close()
            db.close()
        }
    }

    fun markState(context: Context, txnId: String, state: String, reason: String = "") {
        if (txnId.isEmpty()) return
        val db = BillDb.openDb(context)
        try {
            val cv = ContentValues()
            cv.put("state", state)
            cv.put("reason", reason)
            db.update("capture_ledger", cv, "txn_id=?", arrayOf(txnId))
            val trace = ContentValues()
            trace.put("result", state)
            if (reason.isNotEmpty()) trace.put("reason", reason)
            db.update("capture_trace", trace, "txn_id=?", arrayOf(txnId))
        } catch (e: Exception) {
            Log.w(TAG, "mark ledger state failed", e)
        } finally {
            db.close()
        }
    }

    /**
     * 用户在弹层里改过金额：往该笔的判定记录上追加 amount_edited 标记。
     * 这是规则迭代最重要的信号——哪类页面金额识别得差，看它排序就知道。
     */
    fun markAmountEdited(context: Context, txnId: String) {
        if (txnId.isEmpty()) return
        val db = BillDb.openDb(context)
        try {
            db.execSQL(
                "update capture_trace set reason = case " +
                    "when reason like '%amount_edited%' then reason " +
                    "else reason || '|amount_edited' end where txn_id=?",
                arrayOf(txnId)
            )
        } catch (e: Exception) {
            Log.w(TAG, "mark amount edited failed", e)
        } finally {
            db.close()
        }
    }

    // ---------- 诊断 ----------

    fun trace(
        context: Context,
        createdAt: Long,
        pkg: String,
        source: String,
        kind: String,
        result: String,
        reason: String,
        txnId: String,
        facts: Facts?,
        text: String
    ) {
        val db = BillDb.openDb(context)
        try {
            val cv = ContentValues()
            cv.put("created_at", createdAt)
            cv.put("pkg", pkg)
            cv.put("source", source)
            cv.put("kind", kind)
            cv.put("result", result)
            cv.put("reason", reason)
            cv.put("txn_id", txnId)
            cv.put("amount", facts?.amount ?: 0.0)
            cv.put("direction", facts?.direction?.name ?: "")
            cv.put("channel", facts?.channel ?: "")
            cv.put("order_no", facts?.orderNo ?: "")
            cv.put("confidence", facts?.confidence ?: 0.0)
            cv.put("needs_review", if (facts?.needsReview == true) 1 else 0)
            cv.put("text_hash", CaptureText.hash(text))
            cv.put("text", text)
            db.insert("capture_trace", null, cv)
            db.delete("capture_trace", "created_at < ?", arrayOf((createdAt - TRACE_TTL_MS).toString()))
            db.execSQL(
                "delete from capture_trace where id not in " +
                    "(select id from capture_trace order by id desc limit $TRACE_MAX)"
            )
        } catch (e: Exception) {
            Log.w(TAG, "write capture trace failed", e)
        } finally {
            db.close()
        }
    }

    fun traces(context: Context, limit: Int): List<TraceRow> {
        val out = ArrayList<TraceRow>()
        val db = BillDb.openDb(context)
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery(
                "select id,created_at,pkg,source,kind,result,reason,txn_id,amount,direction," +
                    "channel,order_no,confidence,needs_review,text from capture_trace " +
                    "order by id desc limit " + limit,
                null
            )
            while (cursor.moveToNext()) {
                out.add(
                    TraceRow(
                        id = cursor.getLong(0),
                        createdAt = cursor.getLong(1),
                        pkg = cursor.getString(2) ?: "",
                        source = cursor.getString(3) ?: "",
                        kind = cursor.getString(4) ?: "",
                        result = cursor.getString(5) ?: "",
                        reason = cursor.getString(6) ?: "",
                        txnId = cursor.getString(7) ?: "",
                        amount = cursor.getDouble(8),
                        direction = cursor.getString(9) ?: "",
                        channel = cursor.getString(10) ?: "",
                        orderNo = cursor.getString(11) ?: "",
                        confidence = cursor.getDouble(12),
                        needsReview = cursor.getInt(13) == 1,
                        text = cursor.getString(14) ?: ""
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "query capture trace failed", e)
        } finally {
            cursor?.close()
            db.close()
        }
        return out
    }

    fun clearTraces(context: Context) {
        val db = BillDb.openDb(context)
        try {
            db.delete("capture_trace", null, null)
        } catch (e: Exception) {
            Log.w(TAG, "clear trace failed", e)
        } finally {
            db.close()
        }
    }

    fun purge(context: Context) {
        val now = System.currentTimeMillis()
        val db = BillDb.openDb(context)
        try {
            db.delete("capture_ledger", "expires_at < ?", arrayOf(now.toString()))
            db.delete("pending_captures", "created_at < ?", arrayOf((now - PENDING_TTL_MS).toString()))
        } catch (e: Exception) {
            Log.w(TAG, "purge capture tables failed", e)
        } finally {
            db.close()
        }
    }
}
