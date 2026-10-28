package muyian.bill.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureIdentityTest {

    private fun facts(
        orderNo: String = "",
        amount: Double = 38.5,
        direction: Direction = Direction.EXPENSE,
        channel: String = "支付宝",
        merchant: String = "星巴克",
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
        completionMark = "支付成功",
        confidence = 0.9,
        suppressReason = suppressReason
    )

    @Test
    fun orderIsStrongestIdentity() {
        val (txnId, keys) = CaptureIdentity.build(
            facts(orderNo = "2026102612345678"), CaptureSource.SCREEN,
            "com.eg.android.AlipayGphone", isHostPkg = false,
            sessionId = "s1", now = 1_761_550_000_000L
        )
        assertEquals("o:2026102612345678", txnId)
        assertTrue(keys.any { it.kind == "order" })
        assertTrue(keys.any { it.kind == "session" })
        assertFalse(keys.any { it.kind == "capture" })
        assertFalse(keys.any { it.kind == "bill" })
    }

    @Test
    fun noOrderUsesCaptureAndBillKeys() {
        val (txnId, keys) = CaptureIdentity.build(
            facts(), CaptureSource.NOTIFICATION,
            "com.tencent.mm", isHostPkg = false,
            sessionId = "", now = 1_761_550_000_000L
        )
        assertTrue(txnId.startsWith("c:"))
        assertTrue(keys.any { it.kind == "capture" })
        assertTrue(keys.any { it.kind == "bill" })
        assertFalse(keys.any { it.kind == "page" })
    }

    @Test
    fun hostScreenGetsPageKey() {
        val (txnId, keys) = CaptureIdentity.build(
            facts(channel = "微信", merchant = "美团"), CaptureSource.SCREEN,
            "com.sankuai.meituan", isHostPkg = true,
            sessionId = "pay:123", now = 1_761_550_000_000L
        )
        assertEquals("s:pay:123", txnId)
        assertTrue(keys.any { it.kind == "page" })
        assertTrue(keys.any { it.kind == "session" })
    }

    @Test
    fun billKeyUsesRealChannel() {
        val (_, keys) = CaptureIdentity.build(
            facts(channel = "招商银行(1234)"), CaptureSource.SCREEN,
            "com.cmbchina.ccd.pluto.cmbActivity", isHostPkg = false,
            sessionId = "", now = 1_761_550_000_000L
        )
        val bill = keys.first { it.kind == "bill" }.key
        assertTrue(bill.contains("招商银行"))
        assertTrue(bill.contains("1234"))
    }

    @Test
    fun sourceGateRejectsSelfAndSystem() {
        val rule = CaptureRules.builtinRule("com.tencent.mm")
        assertEquals(Reason.SELF, CaptureGates.sourceReason("com.muyian.debt", "com.muyian.debt", rule))
        assertEquals(Reason.SYSTEM_UI, CaptureGates.sourceReason("com.android.systemui", "x", null))
        assertEquals(Reason.NOT_CANDIDATE, CaptureGates.sourceReason("com.unknown", "x", null))
        assertNull(CaptureGates.sourceReason("com.tencent.mm", "x", rule))
    }

    @Test
    fun contextGateOnlyForHost() {
        val pay = CaptureRules.builtinRule("com.eg.android.AlipayGphone")!!
        val host = CaptureRules.builtinRule("com.sankuai.meituan")!!
        assertNull(CaptureGates.contextReason(pay, false))
        assertEquals(Reason.NO_PAYMENT_CONTEXT, CaptureGates.contextReason(host, false))
        assertNull(CaptureGates.contextReason(host, true))
    }

    @Test
    fun semanticGateUsesParserSuppression() {
        assertEquals(
            Reason.INTERNAL_TRANSFER,
            CaptureGates.semanticReason(facts(direction = Direction.TRANSFER, suppressReason = Reason.INTERNAL_TRANSFER))
        )
        assertEquals(
            Reason.REPAYMENT,
            CaptureGates.semanticReason(facts(direction = Direction.REPAYMENT, suppressReason = Reason.REPAYMENT))
        )
        assertNull(CaptureGates.semanticReason(facts()))
    }

    @Test
    fun transferSignUsesArrivalMarker() {
        val inFacts = facts(direction = Direction.TRANSFER)
            .copy(completionMark = "提现成功 到账")
        val outFacts = facts(direction = Direction.TRANSFER)
            .copy(completionMark = "充值成功")
        assertEquals(38.5, CaptureGates.signedAmount(inFacts), 0.001)
        assertEquals(-38.5, CaptureGates.signedAmount(outFacts), 0.001)
    }

    @Test
    fun pageReasonCoversAllRejectKinds() {
        assertEquals(Reason.PAGE_AD, CaptureGates.pageReason(PageKind.AD))
        assertEquals(Reason.PAGE_CHAT, CaptureGates.pageReason(PageKind.CHAT))
        assertEquals(Reason.PAGE_LIST, CaptureGates.pageReason(PageKind.BILL_LIST))
        assertEquals(Reason.PAGE_ORDER_MGMT, CaptureGates.pageReason(PageKind.ORDER_LIST))
        assertEquals(Reason.PAGE_FAILURE, CaptureGates.pageReason(PageKind.PAY_FAILURE))
        assertEquals(Reason.PAGE_PENDING, CaptureGates.pageReason(PageKind.PAY_PENDING))
        assertNull(CaptureGates.pageReason(PageKind.PAY_SUCCESS))
    }
}
