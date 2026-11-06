package muyian.bill.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 结果页现场：同一张成功页在内容没变之前只能处理一次。
 * 对应真机 2026-11-06 11:35 保存、11:40 重开支付宝又弹一次的问题。
 */
class CapturePageMemoryTest {

    private fun facts(amount: Double = 15.8, merchant: String = "迎桂馒头钟楼科技街店") = Facts(
        pageKind = PageKind.PAY_SUCCESS,
        direction = Direction.EXPENSE,
        amount = amount,
        tier = AmountTier.STRONG_LABEL,
        amountConfidence = 0.95,
        needsReview = false,
        channel = "支付宝",
        merchant = merchant,
        orderNo = "",
        occurredAt = 1_793_936_113_601L,
        completionMark = "支付成功",
        confidence = 0.9,
        suppressReason = null
    )

    private fun record(pkg: String = "com.eg.android.AlipayGphone", fp: String, at: Long) =
        CapturePageMemory.Record(pkg, fp, at)

    @Test
    fun `同一张结果页在保活期内判重复`() {
        val fp = CaptureIdentity.captureFingerprint("com.eg.android.AlipayGphone", facts())
        val now = 1_793_936_113_601L + 5L * 60 * 1000L + 33_000L
        assertTrue(
            CapturePageMemory.isReplay(
                record(fp = fp, at = 1_793_936_113_601L),
                "com.eg.android.AlipayGphone", fp, now
            )
        )
    }

    @Test
    fun `换了 App 或换了金额就不是同一张页`() {
        val fp = CaptureIdentity.captureFingerprint("com.eg.android.AlipayGphone", facts())
        val at = 1_793_936_113_601L
        assertFalse(
            CapturePageMemory.isReplay(record(pkg = "com.tencent.mm", fp = fp, at = at),
                "com.eg.android.AlipayGphone", fp, at + 1_000L)
        )
        val other = CaptureIdentity.captureFingerprint("com.eg.android.AlipayGphone", facts(amount = 16.8))
        assertFalse(CapturePageMemory.isReplay(record(fp = fp, at = at),
            "com.eg.android.AlipayGphone", other, at + 1_000L))
    }

    @Test
    fun `超过保活期允许再弹`() {
        val fp = CaptureIdentity.captureFingerprint("com.eg.android.AlipayGphone", facts())
        val at = 1_793_936_113_601L
        assertFalse(
            CapturePageMemory.isReplay(
                record(fp = fp, at = at),
                "com.eg.android.AlipayGphone", fp, at + CapturePageMemory.TTL_MS + 1
            )
        )
    }

    @Test
    fun `没有记录时不判重复`() {
        assertFalse(CapturePageMemory.isReplay(null, "com.eg.android.AlipayGphone", "abc", 1L))
    }

    @Test
    fun `同一笔的渐进渲染不受文本细节影响`() {
        // 11:35:13 的短文本与 11:35:16 的完整文本只有完成标记/时间不同，指纹必须一致
        val settled = CaptureIdentity.captureFingerprint("com.eg.android.AlipayGphone", facts())
        val redraw = CaptureIdentity.captureFingerprint(
            "com.eg.android.AlipayGphone",
            facts().copy(occurredAt = 1_793_936_113_601L + 3_000L, completionMark = "完成")
        )
        assertEquals(settled, redraw)
        // 账本 capture: 键与页面现场共用同一份指纹
        val captureKey = CaptureIdentity.build(
            facts(), CaptureSource.SCREEN, "com.eg.android.AlipayGphone",
            isHostPkg = false, sessionId = "", now = 1L
        ).second.first { it.kind == "capture" }.key
        assertEquals("capture:com.eg.android.AlipayGphone:$settled", captureKey)
    }

    @Test
    fun `成功页类型判定`() {
        assertTrue(CapturePageMemory.isSuccess(PageKind.PAY_SUCCESS))
        assertTrue(CapturePageMemory.isSuccess(PageKind.REFUND_SUCCESS))
        assertFalse(CapturePageMemory.isSuccess(PageKind.CASHIER))
        assertFalse(CapturePageMemory.isSuccess(PageKind.CHAT))
        assertFalse(CapturePageMemory.isSuccess(PageKind.UNKNOWN))
    }
}
