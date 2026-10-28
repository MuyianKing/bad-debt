package muyian.bill.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 解析器回归样本：每个用例都是真实页面/通知文本的缩影。 */
class CaptureParserTest {

    private val now = 1_761_550_000_000L
    private val options = ParseOptions(recordTransfers = false)

    private fun rule(pkg: String): AppRule =
        CaptureRules.builtinRule(pkg) ?: error("no rule for $pkg")

    private fun screen(pkg: String, vararg lines: String): ScreenObservation {
        val items = lines.mapIndexed { i, text ->
            UiText(text, top = i * 60, bottom = i * 60 + 48, sizePx = 30)
        }
        return ScreenObservation(pkg = pkg, items = items, observedAt = now)
    }

    private fun sizedScreen(pkg: String, vararg parts: Pair<String, Int>): ScreenObservation {
        var top = 0
        val items = parts.map { (text, size) ->
            val item = UiText(text, top = top, bottom = top + size, sizePx = size)
            top += size + 12
            item
        }
        return ScreenObservation(pkg = pkg, items = items, observedAt = now)
    }

    private fun notification(
        pkg: String, title: String, text: String, channelId: String = ""
    ): NotificationObservation = NotificationObservation(
        pkg = pkg,
        channelId = channelId,
        title = title,
        text = text,
        postTime = now
    )

    @Test
    fun alipayExpenseFromResultPage() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "支付成功", "¥38.50", "收款方 星巴克咖啡", "订单号 2026102612345678"
            ),
            options
        )
        assertEquals(PageKind.PAY_SUCCESS, analysis.kind)
        val facts = analysis.facts
        assertNotNull(facts)
        assertEquals(38.50, facts!!.amount, 0.001)
        assertEquals(Direction.EXPENSE, facts.direction)
        assertEquals("星巴克咖啡", facts.merchant)
        assertEquals("2026102612345678", facts.orderNo)
        assertEquals("支付宝", facts.channel)
        assertTrue(facts.confidence >= 0.6)
    }

    @Test
    fun bigHeadlineAmountWinsOverSmallText() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            sizedScreen(
                "com.eg.android.AlipayGphone",
                "支付成功" to 30,
                "38.50" to 72,
                "收款方 星巴克咖啡" to 30
            ),
            options
        )
        assertEquals(38.50, analysis.facts!!.amount, 0.001)
        assertEquals(AmountTier.STRUCT_HEADLINE, analysis.facts!!.tier)
    }

    @Test
    fun refundUsesRefundAmountNotOriginalOrder() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "退款成功", "退款金额 ¥50.00", "原订单金额 ¥100.00"
            ),
            options
        )
        assertEquals(PageKind.REFUND_SUCCESS, analysis.kind)
        assertEquals(Direction.REFUND, analysis.facts!!.direction)
        assertEquals(50.0, analysis.facts!!.amount, 0.001)
    }

    @Test
    fun refundPrefersRefundAmountOverPaidAmount() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "退款成功", "实付金额 ¥100.00", "退款金额 ¥50.00"
            ),
            options
        )
        assertEquals(PageKind.REFUND_SUCCESS, analysis.kind)
        assertEquals(50.0, analysis.facts!!.amount, 0.001)
    }

    @Test
    fun wechatTransferCardInsideChatIsRejected() {
        val analysis = CaptureParser.parseScreen(
            rule("com.tencent.mm"),
            screen(
                "com.tencent.mm", "张三", "微信转账", "已收款 ¥100.00",
                "转账说明", "输入消息", "发送"
            ),
            options
        )
        assertEquals(PageKind.CHAT, analysis.kind)
        assertEquals(Reason.PAGE_CHAT, analysis.reason)
    }

    @Test
    fun paymentResultPageWithShareButtonStillAccepted() {
        val analysis = CaptureParser.parseScreen(
            rule("com.tencent.mm"),
            screen(
                "com.tencent.mm", "支付成功", "¥38.50", "收款方 星巴克咖啡", "发送给朋友"
            ),
            options
        )
        assertEquals(PageKind.PAY_SUCCESS, analysis.kind)
        assertEquals(38.5, analysis.facts!!.amount, 0.001)
    }

    @Test
    fun wechatServiceReceiptInsideConversationIsAccepted() {
        val analysis = CaptureParser.parseScreen(
            rule("com.tencent.mm"),
            screen(
                "com.tencent.mm", "微信支付", "微信支付凭证", "支付成功", "¥38.50",
                "交易单号 420001234567890123", "输入消息"
            ),
            options
        )
        assertEquals(PageKind.PAY_SUCCESS, analysis.kind)
        assertEquals(38.5, analysis.facts!!.amount, 0.001)
        assertEquals("420001234567890123", analysis.facts!!.orderNo)
    }

    @Test
    fun alipayHomeRecentMessagesIsRejected() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "常州", "搜索服务、内容或商品", "扫一扫", "收付款", "出行", "卡包",
                "最近消息", "转账 转账成功 ¥0.01", "2小时前",
                "美团 付款成功 ¥21.00", "2小时前"
            ),
            options
        )
        assertEquals(PageKind.BILL_LIST, analysis.kind)
        assertEquals(Reason.PAGE_LIST, analysis.reason)
        assertNull(analysis.facts)
    }

    @Test
    fun multipleMarkedAmountRowsIsRejected() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "账户通知", "转账成功 ¥0.01", "付款成功 ¥21.00"
            ),
            options
        )
        assertEquals(PageKind.BILL_LIST, analysis.kind)
        assertEquals(Reason.MULTI_TRANSACTION, analysis.reason)
        assertNull(analysis.facts)
    }

    @Test
    fun singleReceiptStillWorks() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "支付成功", "¥21.00", "收款方 美团", "订单号 2026102812345678"
            ),
            options
        )
        assertEquals(PageKind.PAY_SUCCESS, analysis.kind)
        assertEquals(21.0, analysis.facts!!.amount, 0.001)
    }

    @Test
    fun alipayTransferPayeeListIsRejected() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "转账", "预约转账", "向多人转账", "AA收款", "亲情卡", "小荷包",
                "收起工具", "最近", "联系人", "我的",
                "木易之(杨麟)", "2026-10-28 转给TA ¥0.01",
                "年华似水(杨成先)", "2026-03-02 收到TA ¥500.00",
                "无忧收款(**云)", "2024-08-31 转给TA ¥13.57"
            ),
            options
        )
        assertEquals(PageKind.BILL_LIST, analysis.kind)
        assertEquals(Reason.PAGE_LIST, analysis.reason)
        assertNull(analysis.facts)
    }

    @Test
    fun scrolledTransferHistoryWithoutTabsIsRejected() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "无忧收款(**云)", "2024-08-31 转给TA ¥13.57",
                "年华似水", "2026-03-02 收到TA ¥500.00"
            ),
            options
        )
        assertEquals(PageKind.BILL_LIST, analysis.kind)
        assertEquals(Reason.MULTI_TRANSACTION, analysis.reason)
        assertNull(analysis.facts)
    }

    @Test
    fun singleTransferResultStillAccepted() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "转账成功", "¥13.57", "转给TA 张三"
            ),
            options
        )
        assertEquals(PageKind.TRANSFER_SUCCESS, analysis.kind)
        assertEquals(13.57, analysis.facts!!.amount, 0.001)
        assertEquals(Direction.EXPENSE, analysis.facts!!.direction)
    }

    @Test
    fun wechatFriendMessageIsChatNotBill() {
        val analysis = CaptureParser.parseScreen(
            rule("com.tencent.mm"),
            screen("com.tencent.mm", "张三", "我支付了100元", "发送"),
            options
        )
        assertEquals(PageKind.CHAT, analysis.kind)
        assertNull(analysis.facts)
        assertEquals(Reason.PAGE_CHAT, analysis.reason)
    }

    @Test
    fun marketingPushIsAdNotBill() {
        val analysis = CaptureParser.parseScreen(
            rule("com.tencent.mm"),
            screen("com.tencent.mm", "微信支付", "支付立减，最高88元", "立即领取"),
            options
        )
        assertEquals(PageKind.AD, analysis.kind)
        assertEquals(Reason.PAGE_AD, analysis.reason)
    }

    @Test
    fun pendingPaymentIsRejected() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen("com.eg.android.AlipayGphone", "支付处理中", "¥38.50"),
            options
        )
        assertEquals(PageKind.PAY_PENDING, analysis.kind)
        assertEquals(Reason.PAGE_PENDING, analysis.reason)
    }

    @Test
    fun failedPaymentIsRejected() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen("com.eg.android.AlipayGphone", "支付失败", "¥38.50"),
            options
        )
        assertEquals(PageKind.PAY_FAILURE, analysis.kind)
        assertEquals(Reason.PAGE_FAILURE, analysis.reason)
    }

    @Test
    fun limitAmountNeverBeatsActualAmount() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "支付成功", "实付金额 ¥0.01", "免密支付限额 ¥500.00"
            ),
            options
        )
        assertEquals(0.01, analysis.facts!!.amount, 0.001)
    }

    @Test
    fun exchangeRateAndPointsNeverTreatedAsAmount() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "支付成功", "汇率 7.25", "积分 100.00", "¥38.50"
            ),
            options
        )
        assertEquals(38.50, analysis.facts!!.amount, 0.001)
    }

    @Test
    fun withdrawalIsInternalTransfer() {
        val analysis = CaptureParser.parseScreen(
            rule("com.tencent.mm"),
            screen("com.tencent.mm", "提现成功", "提现到账 ¥100.00", "零钱"),
            options
        )
        assertEquals(PageKind.TRANSFER_SUCCESS, analysis.kind)
        assertEquals(Direction.TRANSFER, analysis.facts!!.direction)
        assertEquals(Reason.INTERNAL_TRANSFER, analysis.facts!!.suppressReason)
    }

    @Test
    fun creditCardRepaymentIsSuppressed() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen("com.eg.android.AlipayGphone", "还款成功", "还款金额 ¥500.00"),
            options
        )
        assertEquals(PageKind.REPAYMENT_SUCCESS, analysis.kind)
        assertEquals(Direction.REPAYMENT, analysis.facts!!.direction)
        assertEquals(Reason.REPAYMENT, analysis.facts!!.suppressReason)
    }

    @Test
    fun transferRecordingCanBeEnabled() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen("com.eg.android.AlipayGphone", "还款成功", "还款金额 ¥500.00"),
            ParseOptions(recordTransfers = true)
        )
        assertNull(analysis.facts!!.suppressReason)
    }

    @Test
    fun bankSmsNotificationUsesRealBankName() {
        val analysis = CaptureParser.parseNotification(
            rule("com.icbc"),
            notification(
                "com.icbc", "工商银行",
                "您尾号1234的储蓄卡消费38.00元，余额1000.00元"
            ),
            options
        )
        assertEquals(PageKind.PAY_SUCCESS, analysis.kind)
        val facts = analysis.facts!!
        assertEquals(38.0, facts.amount, 0.001)
        assertEquals(Direction.EXPENSE, facts.direction)
        assertTrue(facts.channel.contains("银行"))
        assertTrue(facts.channel.contains("1234"))
    }

    @Test
    fun wechatFriendNotificationIsRejected() {
        val analysis = CaptureParser.parseNotification(
            rule("com.tencent.mm"),
            notification("com.tencent.mm", "张三", "我支付了100元"),
            options
        )
        assertEquals(PageKind.CHAT, analysis.kind)
        assertEquals(Reason.PAGE_CHAT, analysis.reason)
    }

    @Test
    fun wechatPayNotificationIsAccepted() {
        val analysis = CaptureParser.parseNotification(
            rule("com.tencent.mm"),
            notification("com.tencent.mm", "微信支付", "支付成功 ¥38.00", "微信支付"),
            options
        )
        assertEquals(PageKind.PAY_SUCCESS, analysis.kind)
        assertEquals(38.0, analysis.facts!!.amount, 0.001)
        assertEquals("微信", analysis.facts!!.channel)
    }

    @Test
    fun marketingNotificationIsRejected() {
        val analysis = CaptureParser.parseNotification(
            rule("com.tencent.mm"),
            notification("com.tencent.mm", "微信支付", "支付立减，最高88元", "微信支付"),
            options
        )
        assertEquals(PageKind.AD, analysis.kind)
        assertEquals(Reason.PAGE_AD, analysis.reason)
    }

    @Test
    fun orderListPageIsRejected() {
        val analysis = CaptureParser.parseScreen(
            rule("com.sankuai.meituan"),
            screen("com.sankuai.meituan", "我的订单", "全部订单", "支付成功 ¥38.50"),
            options
        )
        assertEquals(PageKind.ORDER_LIST, analysis.kind)
        assertEquals(Reason.PAGE_ORDER_MGMT, analysis.reason)
    }

    @Test
    fun amountAmbiguityFlagsReview() {
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            sizedScreen(
                "com.eg.android.AlipayGphone",
                "支付成功" to 30,
                "38.50" to 72,
                "88.00" to 72
            ),
            options
        )
        assertNotNull(analysis.facts)
        assertTrue(analysis.facts!!.needsReview)
    }

    @Test
    fun notifiedTransferSelfKeepsSuppressReason() {
        val analysis = CaptureParser.parseNotification(
            rule("com.tencent.mm"),
            notification("com.tencent.mm", "微信支付", "提现成功 100.00元 已到账", "微信支付"),
            options
        )
        assertEquals(PageKind.TRANSFER_SUCCESS, analysis.kind)
        assertEquals(Reason.INTERNAL_TRANSFER, analysis.facts!!.suppressReason)
    }

    @Test
    fun groupSummaryNotificationIgnored() {
        val obs = notification("com.tencent.mm", "微信支付", "支付成功 ¥38.00")
            .copy(groupSummary = true)
        val analysis = CaptureParser.parseNotification(rule("com.tencent.mm"), obs, options)
        assertEquals(PageKind.UNKNOWN, analysis.kind)
        assertFalse(analysis.isSuccess)
    }
}
