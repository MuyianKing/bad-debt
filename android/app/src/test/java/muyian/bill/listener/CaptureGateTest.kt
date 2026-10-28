package muyian.bill.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 门禁与身份键的回归：原因码顺序、签名规则、无单号时 page 键的短窗口。
 * 锁死的是"静默判错"类缺陷——转屏丢账单、排除词误杀、24h 弱指纹误判重。
 */
class CaptureGateTest {

    private fun facts(
        orderNo: String = "",
        amount: Double = 38.5,
        direction: Direction = Direction.EXPENSE,
        channel: String = "支付宝",
        merchant: String = "星巴克",
        completionMark: String = "支付成功",
        suppressReason: Reason? = null
    ) = Facts(
        pageKind = PageKind.PAY_SUCCESS,
        direction = direction,
        amount = amount,
        tier = AmountTier.STRONG_LABEL,
        amountConfidence = 0.95,
        needsReview = false,
        channel = channel,
        merchant = merchant,
        orderNo = orderNo,
        occurredAt = 1_761_550_000_000L,
        completionMark = completionMark,
        confidence = 0.9,
        suppressReason = suppressReason
    )

    // ---------- CaptureIdentity：page 键窗口 ----------

    @Test
    fun pageKeyWithoutOrderUsesShortTtl() {
        val (_, keys) = CaptureIdentity.build(
            facts(), CaptureSource.SCREEN, "com.sankuai.meituan", isHostPkg = true,
            sessionId = "", now = 1_761_550_000_000L
        )
        val page = keys.firstOrNull { it.kind == "page" }
        // 无单号的宿主结果页指纹太弱，24h 会把每天同价的真实第二笔判重
        assertTrue("page key missing", page != null)
        assertTrue(page!!.ttlMs <= 15L * 60 * 1000L)
    }

    @Test
    fun pageKeyWithOrderKeepsDailyTtl() {
        val (_, keys) = CaptureIdentity.build(
            facts(orderNo = "2026102900001234"), CaptureSource.SCREEN, "com.sankuai.meituan",
            isHostPkg = true, sessionId = "", now = 1_761_550_000_000L
        )
        val page = keys.first { it.kind == "page" }
        assertEquals(24L * 3600 * 1000L, page.ttlMs)
    }

    @Test
    fun pageKeyOnlyForHostScreen() {
        val notifyKeys = CaptureIdentity.build(
            facts(), CaptureSource.NOTIFICATION, "com.eg.android.AlipayGphone",
            isHostPkg = true, sessionId = "", now = 1_761_550_000_000L
        ).second
        assertFalse(notifyKeys.any { it.kind == "page" })
        val payScreenKeys = CaptureIdentity.build(
            facts(), CaptureSource.SCREEN, "com.eg.android.AlipayGphone",
            isHostPkg = false, sessionId = "", now = 1_761_550_000_000L
        ).second
        assertFalse(payScreenKeys.any { it.kind == "page" })
    }

    // ---------- CaptureGates.sourceReason ----------

    @Test
    fun sourceGateOrder() {
        val rule = CaptureRules.builtinRule("com.eg.android.AlipayGphone")
        assertEquals(Reason.NOT_CANDIDATE, CaptureGates.sourceReason("", "self", rule))
        assertEquals(Reason.SELF, CaptureGates.sourceReason("self", "self", rule))
        assertEquals(
            Reason.SYSTEM_UI,
            CaptureGates.sourceReason("com.android.systemui", "self", rule)
        )
        assertEquals(Reason.NOT_CANDIDATE, CaptureGates.sourceReason("com.other.app", "self", null))
        val offRule = rule!!.copy(role = AppRole.OFF)
        assertEquals(Reason.APP_OFF, CaptureGates.sourceReason("com.eg.android.AlipayGphone", "self", offRule))
        assertNull(CaptureGates.sourceReason("com.eg.android.AlipayGphone", "self", rule))
    }

    // ---------- CaptureGates.pageReason ----------

    @Test
    fun pageGateRejectsNonTransactionPages() {
        assertEquals(Reason.PAGE_LIST, CaptureGates.pageReason(PageKind.BILL_LIST))
        assertEquals(Reason.PAGE_ORDER_MGMT, CaptureGates.pageReason(PageKind.ORDER_LIST))
        assertEquals(Reason.PAGE_AD, CaptureGates.pageReason(PageKind.AD))
        assertEquals(Reason.PAGE_CHAT, CaptureGates.pageReason(PageKind.CHAT))
        assertEquals(Reason.PAGE_FAILURE, CaptureGates.pageReason(PageKind.PAY_FAILURE))
        assertEquals(Reason.PAGE_PENDING, CaptureGates.pageReason(PageKind.PAY_PENDING))
        assertEquals(Reason.PAGE_CASHIER, CaptureGates.pageReason(PageKind.CASHIER))
        assertNull(CaptureGates.pageReason(PageKind.PAY_SUCCESS))
        assertNull(CaptureGates.pageReason(PageKind.REFUND_SUCCESS))
    }

    // ---------- CaptureGates.contextReason / semanticReason ----------

    @Test
    fun hostNeedsPaymentContext() {
        val host = AppRule("com.sankuai.meituan", AppRole.HOST, AdapterId.MEITUAN, "美团")
        assertEquals(Reason.NO_PAYMENT_CONTEXT, CaptureGates.contextReason(host, false))
        assertNull(CaptureGates.contextReason(host, true))
        val pay = AppRule("com.eg.android.AlipayGphone", AppRole.PAY, AdapterId.ALIPAY, "支付宝")
        assertNull(CaptureGates.contextReason(pay, false))
    }

    @Test
    fun semanticGateSuppressesTransfersAndRepayments() {
        assertEquals(
            Reason.INTERNAL_TRANSFER,
            CaptureGates.semanticReason(facts(direction = Direction.TRANSFER, suppressReason = Reason.INTERNAL_TRANSFER))
        )
        assertNull(CaptureGates.semanticReason(facts()))
    }

    // ---------- CaptureGates.signedAmount ----------

    @Test
    fun signedAmountFollowsDirection() {
        assertEquals(-38.5, CaptureGates.signedAmount(facts()), 0.0001)
        assertEquals(
            38.5,
            CaptureGates.signedAmount(facts(direction = Direction.INCOME, completionMark = "已到账")),
            0.0001
        )
        assertEquals(
            38.5,
            CaptureGates.signedAmount(facts(direction = Direction.REFUND, completionMark = "退款成功")),
            0.0001
        )
        // 本人互转：只有"到账"语义记正，提现/转出记负
        assertEquals(
            38.5,
            CaptureGates.signedAmount(facts(direction = Direction.TRANSFER, completionMark = "提现到账零钱")),
            0.0001
        )
        assertEquals(
            -38.5,
            CaptureGates.signedAmount(facts(direction = Direction.TRANSFER, completionMark = "提现成功")),
            0.0001
        )
        assertEquals(
            -38.5,
            CaptureGates.signedAmount(facts(direction = Direction.REPAYMENT, completionMark = "还款成功")),
            0.0001
        )
    }

    // ---------- CaptureGates.acceptedReason ----------

    @Test
    fun acceptedReasonReflectsReviewState() {
        assertEquals(Reason.ACCEPTED, CaptureGates.acceptedReason(facts()))
        assertEquals(
            Reason.ACCEPTED_REVIEW,
            CaptureGates.acceptedReason(facts(suppressReason = null).copy(needsReview = true))
        )
    }

    // ---------- CaptureText.adjacentPrefixExcluded（排除词误杀修复的回归） ----------

    @Test
    fun excludeWordFarBeforeAmountNoLongerKillsIt() {
        // 银行通知里"尾号"在金额前 10 个字符：不应再被整条拒掉
        val line = "您尾号4321的卡交易¥100.00"
        val candidate = CaptureText.firstAmountIn(
            line, CaptureRules.EXCLUDES, false, AmountTier.CURRENCY_CONTEXT, "¥"
        )
        assertTrue("amount after card tail should be accepted", candidate != null)
        assertEquals(100.0, candidate!!.value, 0.0001)
    }

    @Test
    fun excludeWordRightBeforeAmountStillKillsIt() {
        assertNull(
            CaptureText.firstAmountIn(
                "账户余额¥100.00", CaptureRules.EXCLUDES, false,
                AmountTier.CURRENCY_CONTEXT, "¥"
            )
        )
        assertNull(
            CaptureText.firstAmountIn(
                "手续费¥5.00", CaptureRules.EXCLUDES, false,
                AmountTier.CURRENCY_CONTEXT, "¥"
            )
        )
    }

    @Test
    fun realAmountAfterDiscountInSameLineWinsOverDiscountAmount() {
        // "已优惠2元，实付50元"：旧逻辑跳过 50 后会把优惠额 2 当成交易额
        val candidate = CaptureText.firstAmountIn(
            "已优惠2元，实付50元", CaptureRules.EXCLUDES, false,
            AmountTier.CURRENCY_CONTEXT, "¥"
        )
        assertTrue(candidate != null)
        assertEquals(50.0, candidate!!.value, 0.0001)
    }
}
