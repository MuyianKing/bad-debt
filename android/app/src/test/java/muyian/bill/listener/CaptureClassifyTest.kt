package muyian.bill.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 证据分类器：信号、权重、margin 与歧义兜底。 */
class CaptureClassifyTest {

    private val now = 1_761_550_000_000L

    private fun rule(pkg: String): AppRule =
        CaptureRules.builtinRule(pkg) ?: error("no rule for $pkg")

    private fun doc(pkg: String, vararg lines: String): PageDoc =
        PageDoc.ofLines(pkg, lines.toList(), now)

    @Test
    fun successPageExposesCompletionSignalAndConfidence() {
        val classification = PageClassifier.classifyScreen(
            doc("com.eg.android.AlipayGphone", "支付成功", "¥38.50", "收款方 星巴克"),
            rule("com.eg.android.AlipayGphone")
        )
        assertEquals(PageKind.PAY_SUCCESS, classification.kind)
        assertNull(classification.reason)
        assertTrue(classification.isSuccess)
        assertEquals(Direction.EXPENSE, classification.completion!!.direction)
        assertTrue(classification.signals.any { it.kind == PageSignalKind.COMPLETION })
        assertTrue(classification.confidence > 0.8)
        assertTrue(classification.margin > 0.0)
    }

    @Test
    fun failureOutranksCompletionByScore() {
        val classification = PageClassifier.classifyScreen(
            doc("com.eg.android.AlipayGphone", "支付成功", "支付失败", "请重试"),
            rule("com.eg.android.AlipayGphone")
        )
        assertEquals(PageKind.PAY_FAILURE, classification.kind)
        assertEquals(Reason.PAGE_FAILURE, classification.reason)
        assertTrue(classification.signals.any { it.kind == PageSignalKind.COMPLETION })
        assertTrue(classification.signals.any { it.kind == PageSignalKind.FAILURE })
    }

    @Test
    fun conversationListBoostBeatsCompletion() {
        val classification = PageClassifier.classifyScreen(
            doc(
                "com.tencent.mm",
                "微信支付", "已支付¥2.22", "下午5:59",
                "我的好妹妹", "[转账] 你发起了一笔转账", "晚上7:28",
                "服务号", "神马超限时返场", "下午5:38"
            ),
            rule("com.tencent.mm")
        )
        assertEquals(PageKind.CHAT, classification.kind)
        assertEquals(Reason.PAGE_CHAT, classification.reason)
        assertTrue(classification.signals.any { it.kind == PageSignalKind.CONVERSATION_LIST })
    }

    @Test
    fun equalHypothesesBecomePageAmbiguous() {
        val adSignal = PageSignal(PageSignalKind.AD, 800.0, "活动", Reason.PAGE_AD)
        val chatSignal = PageSignal(PageSignalKind.CHAT, 800.0, "输入消息", Reason.PAGE_CHAT)
        val hypotheses = listOf(
            PageHypothesis(PageKind.AD, 800.0, Reason.PAGE_AD, listOf(adSignal)),
            PageHypothesis(PageKind.CHAT, 800.0, Reason.PAGE_CHAT, listOf(chatSignal))
        )
        val classification = PageClassifier.decide(
            hypotheses, null, listOf(adSignal, chatSignal), Reason.NO_COMPLETION_MARK
        )
        assertEquals(PageKind.UNKNOWN, classification.kind)
        assertEquals(Reason.PAGE_AMBIGUOUS, classification.reason)
        assertEquals(0.0, classification.margin, 0.0)
    }

    @Test
    fun notificationSenderGateStillWins() {
        val classification = PageClassifier.classifyNotification(
            doc("com.tencent.mm", "张三", "我支付了100元"),
            rule("com.tencent.mm"),
            NotificationObservation(
                pkg = "com.tencent.mm",
                title = "张三",
                text = "我支付了100元",
                postTime = now
            )
        )
        assertEquals(PageKind.CHAT, classification.kind)
        assertEquals(Reason.PAGE_CHAT, classification.reason)
        assertNotNull(classification.reason)
    }
}
