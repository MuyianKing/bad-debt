package muyian.bill.listener

/**
 * 交易身份与门禁：纯逻辑，规则顺序固定，方便测试和诊断。
 */
object CaptureIdentity {

    private const val TTL_ORDER = 24L * 3600 * 1000L
    private const val TTL_SESSION = 15L * 60 * 1000L
    private const val TTL_PAGE = 24L * 3600 * 1000L
    /** 无单号的宿主结果页指纹只有金额+方向：24h 会把每天同价的真实第二笔判成重复 */
    private const val TTL_PAGE_NO_ORDER = 10L * 60 * 1000L
    private const val TTL_CAPTURE = 5L * 60 * 1000L
    private const val TTL_BILL = 2L * 60 * 1000L

    fun build(
        facts: Facts,
        source: CaptureSource,
        pkg: String,
        isHostPkg: Boolean,
        sessionId: String,
        now: Long
    ): Pair<String, List<LedgerKey>> {
        val keys = ArrayList<LedgerKey>()
        val amount = CaptureText.moneyKey(facts.amount)
        val direction = facts.direction.name
        val merchant = CaptureText.merchantKey(facts.merchant)

        if (facts.orderNo.isNotEmpty()) {
            keys.add(LedgerKey("order:" + facts.orderNo, "order", TTL_ORDER))
        }
        if (sessionId.isNotEmpty()) {
            keys.add(LedgerKey("session:" + sessionId, "session", TTL_SESSION))
        }
        if (source == CaptureSource.SCREEN && isHostPkg) {
            val pageHash = CaptureText.hash(
                pkg + "|" + amount + "|" + direction + "|" + facts.orderNo + "|" + merchant
            )
            val ttl = if (facts.orderNo.isEmpty()) TTL_PAGE_NO_ORDER else TTL_PAGE
            keys.add(LedgerKey("page:" + pkg + ":" + pageHash, "page", ttl))
        }
        if (facts.orderNo.isEmpty()) {
            val captureHash = CaptureText.hash(pkg + "|" + amount + "|" + direction + "|" + merchant)
            keys.add(LedgerKey("capture:" + pkg + ":" + captureHash, "capture", TTL_CAPTURE))
            val channelKey = CaptureText.merchantKey(facts.channel)
            keys.add(LedgerKey("bill:" + channelKey + ":" + amount + ":" + direction, "bill", TTL_BILL))
        }

        val txnId = when {
            facts.orderNo.isNotEmpty() -> "o:" + facts.orderNo
            sessionId.isNotEmpty() -> "s:" + sessionId
            else -> "c:" + CaptureText.hash(pkg + "|" + amount + "|" + direction + "|" + merchant)
        }
        return txnId to keys
    }
}

/**
 * 会话归属：判断一次观察属于哪次支付路线。
 * 宿主 App 的结果通知可能先于"窗口回到前台"的无障碍事件到达，所以不能只在 RETURNED
 * 阶段才把宿主 App 算进会话，否则同一笔交易会被当成两笔。
 */
object CaptureSession {

    fun resolve(
        pkg: String,
        payPkg: String,
        hostPkg: String,
        sessionId: String,
        returned: Boolean,
        enteredAt: Long,
        evidenceAt: Long,
        returnedAt: Long,
        updatedAt: Long,
        now: Long,
        ttlMs: Long,
        returnWindowMs: Long
    ): String {
        if (pkg.isEmpty() || sessionId.isEmpty()) return ""
        if (now - updatedAt > ttlMs) return ""
        if (payPkg == pkg && now - enteredAt <= ttlMs) return sessionId
        // 支付完成后的宿主通知/结果页：只要还在同一次路线的证据窗口内就算同一笔
        if (hostPkg == pkg && evidenceAt > 0L && now - evidenceAt <= returnWindowMs) {
            return sessionId
        }
        if (returned && hostPkg == pkg && now - returnedAt <= ttlMs) return sessionId
        return ""
    }
}

/**
 * 路线状态迁移：前台切换只改这里。
 * 关键约束：只有回到"本次路线记录的宿主"才算 RETURNED；
 * 进入另一个宿主时，上一笔路线的 hostTrigger/intent/session 全部作废，
 * 否则 3 分钟内打开任何 HOST App 都能继承上一笔的支付上下文。
 */
object CaptureRoutePolicy {

    fun foreground(
        state: RouteState,
        pkg: String,
        isPay: Boolean,
        isHost: Boolean,
        fromHost: Boolean,
        now: Long
    ): RouteState? {
        if (pkg.isEmpty() || state.lastPkg == pkg) return null
        val prev = state.lastPkg
        val base = state.copy(lastPkg = pkg, updatedAt = now)

        if (isPay) {
            return if (fromHost || state.payPkg != pkg || state.payPkg.isEmpty()) {
                base.copy(
                    hostPkg = if (fromHost) prev else state.hostPkg,
                    payPkg = pkg,
                    phase = RoutePhase.PAY_ENTERED,
                    enteredAt = now,
                    evidenceAt = 0L,
                    returnedAt = 0L,
                    sessionId = pkg + ":" + now
                )
            } else {
                base
            }
        }

        if (isHost) {
            val returningToRouteHost = state.hostPkg.isNotEmpty() && state.hostPkg == pkg
            val cameFromPay = returningToRouteHost && state.payPkg.isNotEmpty() &&
                prev == state.payPkg && state.evidenceAt >= state.enteredAt &&
                state.evidenceAt > 0L
            return when {
                cameFromPay -> base.copy(hostPkg = pkg, phase = RoutePhase.RETURNED, returnedAt = now)
                state.hostPkg != pkg -> newHost(base, pkg)
                else -> base.copy(hostPkg = pkg)
            }
        }
        return null
    }

    private fun newHost(state: RouteState, pkg: String): RouteState = state.copy(
        hostPkg = pkg,
        payPkg = "",
        phase = RoutePhase.HOST,
        enteredAt = 0L,
        evidenceAt = 0L,
        returnedAt = 0L,
        hostTriggerAt = 0L,
        sessionId = "",
        intentAmount = 0.0,
        intentChannel = "",
        intentMerchant = "",
        intentPkg = "",
        intentHost = "",
        intentAt = 0L
    )
}

internal data class ClaimHit(val txnId: String, val kind: String, val key: String)

internal data class ClaimChoice(
    val mergeTxnId: String = "",
    val duplicateTxnId: String = "",
    val duplicateReason: Reason? = null
)

/**
 * 身份裁决：order > session > page > capture > bill。
 * 强键指向不同 txn、或强键与重复类键指向不同 txn 时，不在两条记录之间合并，
 * 直接按重复处理——宁可少弹一笔，也不能把两笔并成一笔。
 */
internal object CaptureClaimPolicy {

    fun choose(hits: List<ClaimHit>): ClaimChoice? {
        if (hits.isEmpty()) return null
        val order = hits.firstOrNull { it.kind == "order" }
        val session = hits.firstOrNull { it.kind == "session" }
        if (order != null && session != null && order.txnId != session.txnId) {
            return ClaimChoice(
                duplicateTxnId = order.txnId,
                duplicateReason = Reason.DUPLICATE_ORDER
            )
        }
        val target = order ?: session
        if (target != null) {
            val conflict = hits.firstOrNull {
                it.txnId != target.txnId && (it.kind == "page" || it.kind == "capture" || it.kind == "bill")
            }
            if (conflict != null) {
                return ClaimChoice(
                    duplicateTxnId = conflict.txnId,
                    duplicateReason = reasonOf(conflict.kind)
                )
            }
            return ClaimChoice(mergeTxnId = target.txnId)
        }
        val duplicate = hits.firstOrNull { it.kind == "page" }
            ?: hits.firstOrNull { it.kind == "capture" }
            ?: hits.first()
        return ClaimChoice(
            duplicateTxnId = duplicate.txnId,
            duplicateReason = reasonOf(duplicate.kind)
        )
    }

    fun reasonOf(kind: String): Reason = when (kind) {
        "page" -> Reason.DUPLICATE_PAGE
        "capture" -> Reason.DUPLICATE_CAPTURE
        "bill" -> Reason.DUPLICATE_BILL
        "order" -> Reason.DUPLICATE_ORDER
        "session" -> Reason.DUPLICATE_SESSION
        else -> Reason.DUPLICATE_CAPTURE
    }
}

object CaptureGates {

    fun sourceReason(
        pkg: String, selfPkg: String, rule: AppRule?
    ): Reason? {
        if (pkg.isEmpty()) return Reason.NOT_CANDIDATE
        if (pkg == selfPkg) return Reason.SELF
        if (CaptureRules.isSystemUi(pkg)) return Reason.SYSTEM_UI
        if (rule == null) return Reason.NOT_CANDIDATE
        if (rule.role == AppRole.OFF) return Reason.APP_OFF
        return null
    }

    fun pageReason(kind: PageKind): Reason? = when (kind) {
        PageKind.BILL_LIST -> Reason.PAGE_LIST
        PageKind.ORDER_LIST, PageKind.ORDER_DETAIL -> Reason.PAGE_ORDER_MGMT
        PageKind.AD -> Reason.PAGE_AD
        PageKind.CHAT -> Reason.PAGE_CHAT
        PageKind.PAY_FAILURE -> Reason.PAGE_FAILURE
        PageKind.PAY_PENDING -> Reason.PAGE_PENDING
        PageKind.CASHIER -> Reason.PAGE_CASHIER
        PageKind.UNKNOWN -> Reason.PAGE_UNKNOWN
        else -> null
    }

    fun contextReason(rule: AppRule, hasHostContext: Boolean): Reason? {
        if (rule.role == AppRole.HOST && !hasHostContext) return Reason.NO_PAYMENT_CONTEXT
        return null
    }

    fun semanticReason(facts: Facts): Reason? = facts.suppressReason

    fun acceptedReason(facts: Facts): Reason =
        if (facts.needsReview || facts.confidence < 0.60) Reason.ACCEPTED_REVIEW else Reason.ACCEPTED

    /** 统一符号：支出负、收入/退款正；本人互转"到账"记正、其余记负。 */
    fun signedAmount(facts: Facts): Double = when (facts.direction) {
        Direction.INCOME, Direction.REFUND -> facts.amount
        Direction.TRANSFER ->
            if (facts.completionMark.contains("到账")) facts.amount else -facts.amount
        else -> -facts.amount
    }
}
