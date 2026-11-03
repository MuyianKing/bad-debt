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
    /** 支付意图（收银台里的金额）在成功页到达前有效的窗口 */
    private const val INTENT_TTL_MS = 10L * 60 * 1000L

    private val lock = Any()

    fun noteForeground(context: Context, pkg: String) {
        if (pkg.isEmpty()) return
        val rule = CaptureRegistry.ruleFor(context, pkg) ?: return
        if (rule.role == AppRole.OFF) return
        val isPay = rule.role == AppRole.PAY
        val isHost = rule.role == AppRole.HOST
        if (!isPay && !isHost) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val state = load(context, now)
            if (state.lastPkg == pkg) return
            val prev = state.lastPkg
            val fromHost = isPay && prev.isNotEmpty() && prev != pkg &&
                CaptureRegistry.isHostApp(context, prev)
            val next = CaptureRoutePolicy.foreground(
                state, pkg, isPay, isHost, fromHost, now
            ) ?: return
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
                state.phase == RoutePhase.PAY_ENTERED && now - state.enteredAt <= 60_000L
            val next = if (keep) {
                state.copy(evidenceAt = maxOf(state.evidenceAt, at), updatedAt = now)
            } else {
                state.copy(
                    payPkg = pkg,
                    phase = RoutePhase.PAY_ENTERED,
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
                state.phase != RoutePhase.RETURNED && now - state.evidenceAt <= 60_000L
            val next = if (sameSession) {
                state.copy(
                    phase = RoutePhase.PAY_EVIDENCE,
                    evidenceAt = maxOf(state.evidenceAt, at),
                    updatedAt = now
                )
            } else {
                // 锁屏碰卡/通知先到，或上一笔已经完成：单开一个会话
                state.copy(
                    payPkg = pkg,
                    phase = RoutePhase.PAY_EVIDENCE,
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

    /**
     * 记录收银台/确认购买页/支付宝付款弹窗里的金额。
     * 宿主内嵌支付时包名仍是宿主，所以这里同时承担"宿主支付上下文"的职责。
     */
    fun markIntent(
        context: Context, pkg: String, at: Long, intent: PaymentIntent, host: Boolean
    ) {
        if (pkg.isEmpty() || intent.amount <= 0) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val state = load(context, now)
            var next = state.copy(
                intentAmount = intent.amount,
                intentChannel = intent.channel,
                intentMerchant = intent.merchant,
                intentPkg = pkg,
                intentHost = if (host) pkg else state.hostPkg.ifEmpty { state.intentHost },
                intentAt = at,
                updatedAt = now
            )
            if (host) {
                // 宿主自己的确认页/付款弹窗意味着一次新的购买开始：
                // 清掉上一笔残留的支付会话，避免成功页复用旧 session 被账本误并
                next = next.copy(
                    hostPkg = pkg,
                    hostTriggerAt = maxOf(state.hostTriggerAt, at),
                    payPkg = "",
                    phase = RoutePhase.HOST,
                    enteredAt = 0L,
                    evidenceAt = 0L,
                    returnedAt = 0L,
                    sessionId = ""
                )
            } else {
                val sameSession = state.payPkg == pkg && state.sessionId.isNotEmpty() &&
                    now - state.evidenceAt <= 60_000L
                next = if (sameSession) {
                    next.copy(
                        phase = RoutePhase.PAY_EVIDENCE,
                        evidenceAt = maxOf(state.evidenceAt, at)
                    )
                } else {
                    next.copy(
                        payPkg = pkg,
                        phase = RoutePhase.PAY_EVIDENCE,
                        enteredAt = at,
                        evidenceAt = at,
                        returnedAt = 0L,
                        sessionId = pkg + ":" + at
                    )
                }
            }
            save(context, next)
        }
    }

    /** 宿主成功页可用的金额证据；只接受同一条宿主路线的 intent。 */
    fun intentFor(context: Context, hostPkg: String, now: Long): PaymentIntent? {
        if (hostPkg.isEmpty()) return null
        synchronized(lock) {
            val state = load(context, now)
            if (state.intentAmount <= 0 || state.intentPkg.isEmpty()) return null
            if (now - state.intentAt > INTENT_TTL_MS) return null
            val owner = state.intentHost.ifEmpty { state.hostPkg }
            if (owner != hostPkg) return null
            return PaymentIntent(
                amount = state.intentAmount,
                channel = state.intentChannel,
                merchant = state.intentMerchant,
                pkg = state.intentPkg,
                at = state.intentAt
            )
        }
    }

    /** 支付 App 成功页可用的金额证据：只接受同一笔支付会话的 intent。 */
    fun intentForPayment(context: Context, payPkg: String, now: Long): PaymentIntent? {
        if (payPkg.isEmpty()) return null
        synchronized(lock) {
            val state = load(context, now)
            if (state.intentAmount <= 0 || state.intentPkg.isEmpty()) return null
            if (now - state.intentAt > INTENT_TTL_MS) return null
            if (state.payPkg != payPkg) return null
            return PaymentIntent(
                amount = state.intentAmount,
                channel = state.intentChannel,
                merchant = state.intentMerchant,
                pkg = state.intentPkg,
                at = state.intentAt
            )
        }
    }

    /** 成功页已消费，避免停留在后台的同一页再次复用。 */
    fun clearIntent(context: Context) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val state = load(context, now)
            if (state.intentAmount <= 0 && state.intentPkg.isEmpty()) return
            save(
                context,
                state.copy(
                    intentAmount = 0.0,
                    intentChannel = "",
                    intentMerchant = "",
                    intentPkg = "",
                    intentHost = "",
                    intentAt = 0L,
                    updatedAt = now
                )
            )
        }
    }

    fun hasHostContext(context: Context, hostPkg: String, now: Long): Boolean {
        if (hostPkg.isEmpty()) return false
        synchronized(lock) {
            val state = load(context, now)
            if (now - state.updatedAt > TTL_MS) return false
            if (state.phase == RoutePhase.RETURNED && state.hostPkg == hostPkg &&
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
            return CaptureSession.resolve(
                pkg = pkg,
                payPkg = state.payPkg,
                hostPkg = state.hostPkg,
                sessionId = state.sessionId,
                returned = state.phase == RoutePhase.RETURNED,
                enteredAt = state.enteredAt,
                evidenceAt = state.evidenceAt,
                returnedAt = state.returnedAt,
                updatedAt = state.updatedAt,
                now = now,
                ttlMs = TTL_MS,
                returnWindowMs = RETURN_WINDOW_MS
            )
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

    private fun load(context: Context, now: Long): RouteState {
        val raw = try {
            prefs(context).getString(KEY, "") ?: ""
        } catch (_: Exception) {
            ""
        }
        if (raw.isEmpty()) return RouteState(updatedAt = now)
        return try {
            val o = JSONObject(raw)
            val state = RouteState(
                hostPkg = o.optString("hostPkg", ""),
                payPkg = o.optString("payPkg", ""),
                phase = o.optString("phase", RoutePhase.HOST),
                enteredAt = o.optLong("enteredAt", 0L),
                evidenceAt = o.optLong("evidenceAt", 0L),
                returnedAt = o.optLong("returnedAt", 0L),
                hostTriggerAt = o.optLong("hostTriggerAt", 0L),
                sessionId = o.optString("sessionId", ""),
                lastPkg = o.optString("lastPkg", ""),
                updatedAt = o.optLong("updatedAt", 0L),
                intentAmount = o.optDouble("intentAmount", 0.0),
                intentChannel = o.optString("intentChannel", ""),
                intentMerchant = o.optString("intentMerchant", ""),
                intentPkg = o.optString("intentPkg", ""),
                intentHost = o.optString("intentHost", ""),
                intentAt = o.optLong("intentAt", 0L)
            )
            if (now - state.updatedAt > TTL_MS) RouteState(updatedAt = now) else state
        } catch (_: Exception) {
            RouteState(updatedAt = now)
        }
    }

    private fun save(context: Context, state: RouteState) {
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
            o.put("intentAmount", state.intentAmount)
            o.put("intentChannel", state.intentChannel)
            o.put("intentMerchant", state.intentMerchant)
            o.put("intentPkg", state.intentPkg)
            o.put("intentHost", state.intentHost)
            o.put("intentAt", state.intentAt)
            prefs(context).edit().putString(KEY, o.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "save route failed", e)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
