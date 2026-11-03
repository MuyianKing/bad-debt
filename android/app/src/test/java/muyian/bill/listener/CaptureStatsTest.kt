package muyian.bill.listener

import org.junit.Assert.assertEquals
import org.junit.Test

/** 双通道对账的纯匹配逻辑：同额 + 2 分钟内。 */
class CaptureStatsTest {

    private val t0 = 1_793_726_000_000L

    private fun screen(amount: Double, at: Long, channel: String = "支付宝") =
        CaptureStats.CapturePoint("screen", amount, channel, at)

    private fun notify(amount: Double, at: Long, channel: String = "支付宝") =
        CaptureStats.CapturePoint("notify", amount, channel, at)

    @Test
    fun sameAmountWithinWindowMatches() {
        val r = CaptureStats.reconcilePoints(
            listOf(screen(38.5, t0)),
            listOf(notify(38.5, t0 + 60_000L))
        )
        assertEquals(1, r.matched)
        assertEquals(0, r.screenOnly)
        assertEquals(0, r.notifyOnly)
    }

    @Test
    fun sameAmountOutsideWindowDoesNotMatch() {
        val r = CaptureStats.reconcilePoints(
            listOf(screen(38.5, t0)),
            listOf(notify(38.5, t0 + 3 * 60_000L))
        )
        assertEquals(0, r.matched)
        assertEquals(1, r.screenOnly)
        assertEquals(1, r.notifyOnly)
    }

    @Test
    fun differentAmountDoesNotMatch() {
        val r = CaptureStats.reconcilePoints(
            listOf(screen(38.5, t0)),
            listOf(notify(38.6, t0))
        )
        assertEquals(0, r.matched)
        assertEquals(1, r.screenOnly)
        assertEquals(1, r.notifyOnly)
    }

    @Test
    fun channelMismatchDoesNotBlockMatch() {
        // 宿主结果页渠道可能是"银行卡"，通知侧是"支付宝"；同额同时间仍按一笔对账
        val r = CaptureStats.reconcilePoints(
            listOf(screen(38.5, t0, "银行卡")),
            listOf(notify(38.5, t0 + 10_000L, "支付宝"))
        )
        assertEquals(1, r.matched)
    }

    @Test
    fun twoScreensTwoNoticesPairGreedily() {
        val r = CaptureStats.reconcilePoints(
            listOf(screen(10.0, t0), screen(20.0, t0 + 10_000L)),
            listOf(notify(20.0, t0 + 12_000L), notify(10.0, t0 + 50_000L))
        )
        assertEquals(2, r.matched)
        assertEquals(0, r.screenOnly)
        assertEquals(0, r.notifyOnly)
    }
}
