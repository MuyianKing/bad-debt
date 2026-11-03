package muyian.bill.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** AmountExtractor 的入口契约：三种模式共享候选/排除词，但保留各自的置信度语义。 */
class CaptureAmountTest {

    private val now = 1_761_550_000_000L
    private fun rule(pkg: String): AppRule =
        CaptureRules.builtinRule(pkg) ?: error("no rule for $pkg")

    @Test
    fun cashierStrongAnchorKeepsIntentConfidence() {
        val doc = PageDoc.of(
            "com.eg.android.AlipayGphone",
            listOf(UiText("支付金额 ¥10.00", top = 0, bottom = 48, sizePx = 30)),
            now
        )
        val candidate = AmountExtractor.pickCashier(doc, rule("com.eg.android.AlipayGphone"))
        assertNotNull(candidate)
        assertEquals(10.0, candidate!!.value, 0.001)
        // 收银台金额是"支付意图"，沿用原来的 0.9，而不是成功页强锚点的 0.95
        assertEquals(0.9, candidate.confidence, 0.0001)
    }

    @Test
    fun notificationUsesAnchoredAmountWithoutGeometry() {
        val doc = PageDoc.ofLines(
            "com.eg.android.AlipayGphone",
            listOf("支付成功", "支付金额 38.50元"),
            now
        )
        val picked = AmountExtractor.pickNotification(
            doc, rule("com.eg.android.AlipayGphone"), Direction.EXPENSE
        )
        val candidate = picked.candidate
        assertNotNull(candidate)
        assertEquals(38.50, candidate!!.value, 0.001)
        assertEquals(AmountTier.STRONG_LABEL, candidate.tier)
    }

    @Test
    fun nearCompletionAnchorBeatsFarMarketingAnchor() {
        // 顶部的"实付金额 5.00"是营销/预估；完成标记旁边的"支付金额 38.50"才是真金额
        val doc = PageDoc.of(
            "com.eg.android.AlipayGphone",
            listOf(
                UiText("实付金额 5.00", top = 0, bottom = 48, sizePx = 30),
                UiText("商品介绍", top = 60, bottom = 108, sizePx = 30),
                UiText("配送说明", top = 120, bottom = 168, sizePx = 30),
                UiText("温馨提示", top = 180, bottom = 228, sizePx = 30),
                UiText("支付成功", top = 240, bottom = 288, sizePx = 30),
                UiText("支付金额 38.50", top = 300, bottom = 348, sizePx = 30)
            ),
            now
        )
        val picked = AmountExtractor.pickScreen(
            doc,
            rule("com.eg.android.AlipayGphone"),
            markIndex = 4,
            direction = Direction.EXPENSE,
            allowCurrencyContext = true
        )
        assertNotNull(picked.candidate)
        assertEquals(38.50, picked.candidate!!.value, 0.001)
    }

    @Test
    fun notificationAmbiguousStrongAnchorsFlagReview() {
        val doc = PageDoc.ofLines(
            "com.eg.android.AlipayGphone",
            listOf("支付成功", "实付金额 5.00", "支付金额 38.50"),
            now
        )
        val picked = AmountExtractor.pickNotification(
            doc, rule("com.eg.android.AlipayGphone"), Direction.EXPENSE
        )
        assertNotNull(picked.candidate)
        // 同一层出现两个不同金额：不静默选一个，弹层提示核对
        org.junit.Assert.assertTrue(picked.needsReview)
    }
}
