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
