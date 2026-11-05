package muyian.bill.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * 路线状态迁移的回归：一条路线只属于一个宿主。
 * 进入另一个 HOST App 时，上一笔的 RETURNED / hostTrigger / intent 都必须作废，
 * 否则 3 分钟内打开任何宿主都能继承上一笔的支付上下文。
 */
class CaptureRouteTest {

    private fun state(
        hostPkg: String = "",
        payPkg: String = "",
        phase: String = RoutePhase.HOST,
        enteredAt: Long = 0L,
        evidenceAt: Long = 0L,
        returnedAt: Long = 0L,
        hostTriggerAt: Long = 0L,
        sessionId: String = "",
        lastPkg: String = "",
        updatedAt: Long = 0L,
        intentAmount: Double = 0.0,
        intentPkg: String = "",
        intentHost: String = ""
    ) = RouteState(
        hostPkg = hostPkg,
        payPkg = payPkg,
        phase = phase,
        enteredAt = enteredAt,
        evidenceAt = evidenceAt,
        returnedAt = returnedAt,
        hostTriggerAt = hostTriggerAt,
        sessionId = sessionId,
        lastPkg = lastPkg,
        updatedAt = updatedAt,
        intentAmount = intentAmount,
        intentPkg = intentPkg,
        intentHost = intentHost
    )

    @Test
    fun returningToSameHostMarksReturned() {
        val next = CaptureRoutePolicy.foreground(
            state(
                hostPkg = "host.A", payPkg = "pay.P", phase = RoutePhase.PAY_EVIDENCE,
                enteredAt = 1_000L, evidenceAt = 2_000L, lastPkg = "pay.P", updatedAt = 2_000L
            ),
            pkg = "host.A", isPay = false, isHost = true, fromHost = false, now = 3_000L
        )
        assertNotNull(next)
        assertEquals("host.A", next!!.hostPkg)
        assertEquals(RoutePhase.RETURNED, next.phase)
        assertEquals(3_000L, next.returnedAt)
    }

    @Test
    fun openingAnotherHostResetsRouteAndTrigger() {
        val next = CaptureRoutePolicy.foreground(
            state(
                hostPkg = "host.A", payPkg = "pay.P", phase = RoutePhase.RETURNED,
                enteredAt = 1_000L, evidenceAt = 2_000L, returnedAt = 3_000L,
                hostTriggerAt = 2_500L, sessionId = "pay.P:1",
                lastPkg = "host.A", updatedAt = 3_000L,
                intentAmount = 10.0, intentPkg = "host.A", intentHost = "host.A"
            ),
            pkg = "host.B", isPay = false, isHost = true, fromHost = false, now = 4_000L
        )
        assertNotNull(next)
        assertEquals("host.B", next!!.hostPkg)
        assertEquals(RoutePhase.HOST, next.phase)
        assertEquals(0L, next.hostTriggerAt)
        assertEquals("", next.payPkg)
        assertEquals("", next.sessionId)
        assertEquals(0.0, next.intentAmount, 0.0)
        assertEquals("", next.intentPkg)
        assertEquals("", next.intentHost)
    }

    @Test
    fun openingAnotherHostFromPayDoesNotInheritReturn() {
        // 宿主 A 付款后直接打开宿主 B：B 不是本次路线的宿主，不能算 RETURNED
        val next = CaptureRoutePolicy.foreground(
            state(
                hostPkg = "host.A", payPkg = "pay.P", phase = RoutePhase.PAY_EVIDENCE,
                enteredAt = 1_000L, evidenceAt = 2_000L,
                lastPkg = "pay.P", updatedAt = 2_000L
            ),
            pkg = "host.B", isPay = false, isHost = true, fromHost = false, now = 3_000L
        )
        assertNotNull(next)
        assertEquals("host.B", next!!.hostPkg)
        assertNotEquals(RoutePhase.RETURNED, next.phase)
        assertEquals("", next.sessionId)
    }

    @Test
    fun payAppOpenedFromHostStartsSession() {
        val next = CaptureRoutePolicy.foreground(
            state(hostPkg = "host.A", lastPkg = "host.A", updatedAt = 1_000L),
            pkg = "pay.P", isPay = true, isHost = false, fromHost = true, now = 2_000L
        )
        assertNotNull(next)
        assertEquals("host.A", next!!.hostPkg)
        assertEquals("pay.P", next.payPkg)
        assertEquals(RoutePhase.PAY_ENTERED, next.phase)
        assertEquals("pay.P:2000", next.sessionId)
    }

    @Test
    fun samePackageIsIgnored() {
        val next = CaptureRoutePolicy.foreground(
            state(hostPkg = "host.A", lastPkg = "host.A", updatedAt = 1_000L),
            pkg = "host.A", isPay = false, isHost = true, fromHost = false, now = 2_000L
        )
        assertEquals(null, next)
    }
}
