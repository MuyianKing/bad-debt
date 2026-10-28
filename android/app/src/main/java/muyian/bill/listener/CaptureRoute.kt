package muyian.bill.listener

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * 支付路线状态机：HOST → PAY_ENTERED → PAY_EVIDENCE → RETURNED。
 * 状态落盘 15 分钟，进程被杀后仍能判断"宿主结果页属于哪次支付"。
 */
internal object CaptureRoute {
    private const val TAG = "MuyianBill"
    private const val PREFS = "muyian_bill"
    private const val KEY = "capture_route_v2"
    private const val TTL_MS = 15L * 60 * 1000L
    private const val RETURN_WINDOW_MS = 3L * 60 * 1000L
    private const val TRIGGER_WINDOW_MS = 10L * 60 * 1000L

    private const val PHASE_HOST = "HOST"
    private const val PHASE_PAY_ENTERED = "PAY_ENTERED"
    private const val PHASE_PAY_EVIDENCE = "PAY_EVIDENCE"
    private const val PHASE_RETURNED = "RETURNED"

    private val lock = Any()

    private data class State(
        val hostPkg: String = "",
        val payPkg: String = "",
        val phase: String = PHASE_HOST,
        val enteredAt: Long = 0L,
        val evidenceAt: Long = 0L,
        val returnedAt: Long = 0L,
        val hostTriggerAt: Long = 0L,
        val sessionId: String = "",
        val lastPkg: String = "",
        val updatedAt: Long = 0L
    )

    fun noteForeground(context: Context, pkg: String) {
        if (pkg.isEmpty()) return
        val rule = CaptureRegistry.ruleFor(context, pkg) ?: return
        if (rule.role == AppRole.OFF) return
        val isPay = rule.role == AppRole.PAY
        val isHost = rule.role == AppRole.HOST
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val state = load(context, now)
            if (state.lastPkg == pkg) return
            val prev = state.lastPkg
            var next = state.copy(lastPkg = pkg, updatedAt = now)
            if (isPay) {
                val fromHost = prev.isNotEmpty() && prev != pkg &&
                    CaptureRegistry.isHostApp(context, prev)
                if (fromHost || state.payPkg != pkg || state.payPkg.isEmpty()) {
                    next = next.copy(
                        hostPkg = if (fromHost) prev else state.hostPkg,
                        payPkg = pkg,
                        phase = PHASE_PAY_ENTERED,
                        enteredAt = now,
                        evidenceAt = 0L,
                        returnedAt = 0L,
                        sessionId = pkg + ":" + now
                    )
                }
            } else if (isHost) {
                val cameFromPay = state.payPkg.isNotEmpty() && prev == state.payPkg &&
                    state.evidenceAt >= state.enteredAt && state.evidenceAt > 0L
                next = if (cameFromPay) {
                    next.copy(hostPkg = pkg, phase = PHASE_RETURNED, returnedAt = now)
                } else {
                    next.copy(hostPkg = pkg)
                }
            } else {
                return
            }
            save(context, next)
        }
    }

    /** 支付 App 出现收银台：一笔新的支付开始，旧会话即使还在 15 分钟内也要换掉。 */
    fun markCashier(context: Context, pkg: String, at: Long) {
        if (pkg.isEmpty()) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val state = load(context, now)
            val keep = state.payPkg == pkg && state.sessionId.isNotEmpty() &&
                state.phase == PHASE_PAY_ENTERED && now - state.enteredAt <= 60_000L
            val next = if (keep) {
                state.copy(evidenceAt = maxOf(state.evidenceAt, at), updatedAt = now)
            } else {
                state.copy(
                    payPkg = pkg,
                    phase = PHASE_PAY_ENTERED,
                    enteredAt = at,
                    evidenceAt = at,
                    returnedAt = 0L,
                    sessionId = pkg + ":" + at,
                    updatedAt = now
                )
            }
            save(context, next)
        }
    }

    /** 支付 App 出现收银台/成功页/支付通知：这次访问确实在支付。 */
    fun markEvidence(context: Context, pkg: String, at: Long) {
        if (pkg.isEmpty()) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val state = load(context, now)
            val sameSession = state.payPkg == pkg && state.sessionId.isNotEmpty() &&
                state.phase != PHASE_RETURNED && now - state.evidenceAt <= 60_000L
            val next = if (sameSession) {
                state.copy(
                    phase = PHASE_PAY_EVIDENCE,
                    evidenceAt = maxOf(state.evidenceAt, at),
                    updatedAt = now
                )
            } else {
                // 锁屏碰卡/通知先到，或上一笔已经完成：单开一个会话
                state.copy(
                    payPkg = pkg,
                    phase = PHASE_PAY_EVIDENCE,
                    enteredAt = at,
                    evidenceAt = at,
                    returnedAt = 0L,
                    sessionId = pkg + ":" + at,
                    updatedAt = now
                )
            }
            save(context, next)
        }
    }

    /** 宿主 App 里出现收银台/提交订单：App 内支付的上下文。 */
    fun markHostTrigger(context: Context, hostPkg: String, at: Long) {
        if (hostPkg.isEmpty()) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val state = load(context, now)
            save(
                context,
                state.copy(hostPkg = hostPkg, hostTriggerAt = at, updatedAt = now)
            )
        }
    }

    fun hasHostContext(context: Context, hostPkg: String, now: Long): Boolean {
        if (hostPkg.isEmpty()) return false
        synchronized(lock) {
            val state = load(context, now)
            if (now - state.updatedAt > TTL_MS) return false
            if (state.phase == PHASE_RETURNED && state.hostPkg == hostPkg &&
                now - state.returnedAt <= RETURN_WINDOW_MS
            ) return true
            if (state.hostTriggerAt > 0 && state.hostPkg == hostPkg &&
                now - state.hostTriggerAt <= TRIGGER_WINDOW_MS
            ) return true
            return false
        }
    }

    fun sessionFor(context: Context, pkg: String, now: Long): String {
        if (pkg.isEmpty()) return ""
        synchronized(lock) {
            val state = load(context, now)
            if (now - state.updatedAt > TTL_MS) return ""
            if (state.payPkg == pkg && state.sessionId.isNotEmpty() &&
                now - state.enteredAt <= TTL_MS
            ) return state.sessionId
            if (state.phase == PHASE_RETURNED && state.hostPkg == pkg &&
                state.sessionId.isNotEmpty() && now - state.returnedAt <= TTL_MS
            ) return state.sessionId
            return ""
        }
    }

    fun reset(context: Context) {
        synchronized(lock) {
            try {
                prefs(context).edit().remove(KEY).apply()
            } catch (e: Exception) {
                Log.w(TAG, "reset route failed", e)
            }
        }
    }

    private fun load(context: Context, now: Long): State {
        val raw = try {
            prefs(context).getString(KEY, "") ?: ""
        } catch (_: Exception) {
            ""
        }
        if (raw.isEmpty()) return State(updatedAt = now)
        return try {
            val o = JSONObject(raw)
            val state = State(
                hostPkg = o.optString("hostPkg", ""),
                payPkg = o.optString("payPkg", ""),
                phase = o.optString("phase", PHASE_HOST),
                enteredAt = o.optLong("enteredAt", 0L),
                evidenceAt = o.optLong("evidenceAt", 0L),
                returnedAt = o.optLong("returnedAt", 0L),
                hostTriggerAt = o.optLong("hostTriggerAt", 0L),
                sessionId = o.optString("sessionId", ""),
                lastPkg = o.optString("lastPkg", ""),
                updatedAt = o.optLong("updatedAt", 0L)
            )
            if (now - state.updatedAt > TTL_MS) State(updatedAt = now) else state
        } catch (_: Exception) {
            State(updatedAt = now)
        }
    }

    private fun save(context: Context, state: State) {
        try {
            val o = JSONObject()
            o.put("hostPkg", state.hostPkg)
            o.put("payPkg", state.payPkg)
            o.put("phase", state.phase)
            o.put("enteredAt", state.enteredAt)
            o.put("evidenceAt", state.evidenceAt)
            o.put("returnedAt", state.returnedAt)
            o.put("hostTriggerAt", state.hostTriggerAt)
            o.put("sessionId", state.sessionId)
            o.put("lastPkg", state.lastPkg)
            o.put("updatedAt", state.updatedAt)
            prefs(context).edit().putString(KEY, o.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "save route failed", e)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
