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
    fun alipayPayAfterUseBillDetailWithSignedHeadlineAmount() {
        // 支付宝账单详情：先用后付订单完成付款，大字金额带负号（"-30.05"），不是 ¥ 开头
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            sizedScreen(
                "com.eg.android.AlipayGphone",
                "天猫" to 30,
                "天**" to 30,
                "-30.05" to 72,
                "支付成功" to 30,
                "关联记录" to 30,
                "查看关联记录" to 30,
                "支付时间" to 30,
                "2026-11-03 18:52:41" to 30,
                "付款方式" to 30,
                "余额宝" to 30,
                "扣款说明" to 30,
                "先用后付订单完成付款，已计入芝麻分评估" to 30,
                "支付奖励" to 30,
                "立即领取3积分" to 30,
                "交易详情" to 30,
                "四川爱媛38号果冻橙应当季新鲜水果手剥橙子甜柑橘整箱特眉山..." to 30,
                "共1件" to 30,
                "账单管理" to 30,
                "我的消费图谱" to 30,
                "账单分类" to 30,
                "餐饮美食" to 30,
                "标签" to 30,
                "请选择" to 30,
                "为您推荐" to 30,
                "水果" to 30,
                "计入收支" to 30
            ),
            options
        )
        assertEquals(PageKind.PAY_SUCCESS, analysis.kind)
        assertNotNull(analysis.facts)
        assertEquals(30.05, analysis.facts!!.amount, 0.001)
        assertEquals(AmountTier.STRUCT_HEADLINE, analysis.facts!!.tier)
        assertEquals(Direction.EXPENSE, analysis.facts!!.direction)
        assertEquals("支付宝", analysis.facts!!.channel)
        assertNull(analysis.facts!!.suppressReason)
    }

    @Test
    fun payAfterUseCompletionTextWorksWithoutStatusLabel() {
        // 状态文字没进无障碍树时，「先用后付订单完成付款」本身就是完成语义，不能被"立即领取"误判成营销页
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "天猫", "天**", "-30.05",
                "关联记录", "支付时间", "2026-11-03 18:52:41",
                "付款方式", "余额宝",
                "先用后付订单完成付款，已计入芝麻分评估",
                "立即领取3积分"
            ),
            options
        )
        assertEquals(PageKind.PAY_SUCCESS, analysis.kind)
        assertNotNull(analysis.facts)
        assertEquals(30.05, analysis.facts!!.amount, 0.001)
        assertEquals(Direction.EXPENSE, analysis.facts!!.direction)
    }

    @Test
    fun alipaySingleBillDetailWithAllBillsLinkIsNotAList() {
        // 真机账单详情：顶部「全部账单」入口会命中 BILL_LIST，但页面本身是单笔详情
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "天**",
                "支出30.05元",
                "支付成功",
                "关联记录", "查看关联记录",
                "支付时间", "2026-11-03 18:52:41",
                "付款方式", "余额宝",
                "扣款说明", "先用后付订单完成付款，已计入芝麻分评估",
                "支付奖励", "立即领取3积分",
                "交易详情",
                "四川爱媛38号果冻橙应当季新鲜水果手剥橙子甜柑橘整箱特眉山大果",
                "共1件", "更多",
                "账单管理", "我的消费图鉴", "账单分类", "餐饮美食",
                "标签", "请选择",
                "使用记账本，查看自定义分类、标签统计",
                "计入收支", "开关",
                "账单详情", "全部账单"
            ),
            options
        )
        assertEquals(PageKind.PAY_SUCCESS, analysis.kind)
        assertNotNull(analysis.facts)
        assertEquals(30.05, analysis.facts!!.amount, 0.001)
        assertEquals(Direction.EXPENSE, analysis.facts!!.direction)
        assertEquals("支付宝", analysis.facts!!.channel)
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
    fun wechatChatListPreviewIsRejected() {
        // 微信首页会话列表：不少 ROM 不把底部 tab 放进无障碍树，只能靠"多行时间"结构兜底
        val analysis = CaptureParser.parseScreen(
            rule("com.tencent.mm"),
            screen(
                "com.tencent.mm",
                "微信", "已登录Windows电脑",
                "公众号", "国家邮政局：中共中央召开党外人士座谈会 习近平...", "晚上7:43",
                "我的好妹妹", "[转账] 你发起了一笔转账", "晚上7:28",
                "微信支付", "已支付¥2.22", "下午5:59",
                "服务号", "三国杀移动版：神马超限时返场，狂欢活动武将抢...", "下午5:38",
                "久沐产品中心", "recovery/getoperanesdata/", "下午5:14",
                "王勤学", "下午4:44"
            ),
            options
        )
        assertEquals(PageKind.CHAT, analysis.kind)
        assertEquals(Reason.PAGE_CHAT, analysis.reason)
        assertNull(analysis.facts)
    }

    @Test
    fun wechatChatListPreviewRejectedWithoutBottomTabs() {
        // 只有会话行、没有任何聊天页/底部 tab 特征时也不能把摘要当成账单
        val analysis = CaptureParser.parseScreen(
            rule("com.tencent.mm"),
            screen(
                "com.tencent.mm",
                "微信支付", "已支付¥2.22", "下午5:59",
                "我的好妹妹", "[转账] 你发起了一笔转账", "晚上7:28",
                "服务号", "神马超限时返场", "下午5:38"
            ),
            options
        )
        assertEquals(Reason.PAGE_CHAT, analysis.reason)
        assertNull(analysis.facts)
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
    fun transferSuccessWithRepeatedStatusIsSingleTransaction() {
        // 真机：支付宝转账成功页同时渲染"转账成功￥0.01"标题和"转账成功/0.01"状态，
        // 不能因为完成标记出现两次就判成交易列表
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "转账成功￥0.01", "转账成功", "0.01", "收款方", "木易之(杨麟)",
                "交易方式", "余额宝(转出资金付款)", "完成", "回首页"
            ),
            ParseOptions(recordTransfers = true)
        )
        assertEquals(PageKind.TRANSFER_SUCCESS, analysis.kind)
        assertNotNull(analysis.facts)
        assertEquals(0.01, analysis.facts!!.amount, 0.001)
        assertEquals(Direction.EXPENSE, analysis.facts!!.direction)
    }

    @Test
    fun sameAmountRowsFarApartStillCountAsMultiple() {
        // 两笔同额但分隔较远：仍是列表，不能因为金额相同只算一笔
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen(
                "com.eg.android.AlipayGphone",
                "支付成功 ¥0.01", "收款方 A", "订单号 2026102612345678", "付款时间 18:52",
                "支付成功 ¥0.01", "收款方 B", "订单号 2026102612345679", "付款时间 18:53"
            ),
            options
        )
        assertEquals(PageKind.BILL_LIST, analysis.kind)
        assertEquals(Reason.MULTI_TRANSACTION, analysis.reason)
        assertNull(analysis.facts)
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
    fun weakAmountFallsIntoReviewConfidenceBand() {
        // 只有"消费 38.50"这种弱标签时仍然识别，但置信度落在 0.60~0.80，弹层提示核对
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen("com.eg.android.AlipayGphone", "支付成功", "消费 38.50"),
            options
        )
        assertNotNull(analysis.facts)
        assertEquals(38.50, analysis.facts!!.amount, 0.001)
        assertEquals(AmountTier.WEAK_LABEL, analysis.facts!!.tier)
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

    // ---------- 闲鱼/淘宝：成功页无金额，金额来自收银台 intent ----------

    @Test
    fun taobaoShangouConfirmPageUsesTotalNotItemPrice() {
        // 淘宝闪购确认订单：商品总价/打包费/配送费/优惠都在，真正应付是"合计 ¥24.7"
        val obs = sizedScreen(
            "com.taobao.taobao",
            "长沟弄16号楼丙单元301" to 30,
            "1对1急送" to 30, "¥6.1" to 30,
            "商品总价" to 30, "¥28.4" to 30,
            "打包费" to 30, "¥1" to 30,
            "配送费" to 30, "¥2" to 30,
            "商品优惠" to 30, "-¥2.7" to 30,
            "平台红包" to 30, "-¥4" to 30,
            "合计" to 30, "¥24.7" to 30,
            "已优惠11.7" to 30,
            "提交订单" to 50
        )
        val intent = CaptureParser.parseCashierIntent(rule("com.taobao.taobao"), obs)
        assertNotNull(intent)
        assertEquals(24.7, intent!!.amount, 0.001)

        val analysis = CaptureParser.parseScreen(rule("com.taobao.taobao"), obs, options)
        assertEquals(PageKind.CASHIER, analysis.kind)
    }

    @Test
    fun chinesePointAmountNormalizes() {
        // 微信 LiteApp content-desc："10.90元" 被读成 "10点9 0元"
        val candidate = CaptureText.firstAmountIn(
            "10点9 0元", CaptureRules.EXCLUDES, false,
            AmountTier.CURRENCY_CONTEXT, "test"
        )
        assertNotNull(candidate)
        assertEquals(10.90, candidate!!.value, 0.001)
    }

    @Test
    fun wechatLiteAppContentDescAmountParsed() {
        // 微信京东 LitaApp 支付页：文本全在 content-desc，金额是 "10点9 0元"
        val obs = screen(
            "com.tencent.mm",
            "返回",
            "京东-订单编号3671431016744187,京东,10点9 0元",
            "10点9 0元",
            "开通京东商城免密支付  ,切换按钮,关闭",
            "支付"
        )
        val intent = CaptureParser.parseCashierIntent(rule("com.tencent.mm"), obs)
        assertNotNull(intent)
        assertEquals(10.90, intent!!.amount, 0.001)
        assertEquals("微信", intent.channel)

        val analysis = CaptureParser.parseScreen(rule("com.tencent.mm"), obs, options)
        assertEquals(PageKind.CASHIER, analysis.kind)
        assertEquals(Reason.PAGE_CASHIER, analysis.reason)
    }

    @Test
    fun alipayQuickPayGlyphIdentifiesChannel() {
        // 快速支付按钮旁的"支"字图标：页面没有"支付宝/余额宝"文案也能认出支付宝
        val obs = sizedScreen(
            "com.taobao.taobao",
            "更改付款方式" to 30,
            "淘宝闪购商户" to 30,
            "¥7.60" to 72,
            "快速支付" to 34,
            "支" to 30
        )
        val intent = CaptureParser.parseCashierIntent(rule("com.taobao.taobao"), obs)
        assertNotNull(intent)
        assertEquals(7.60, intent!!.amount, 0.001)
        assertEquals("支付宝", intent.channel)
        assertEquals("淘宝闪购商户", intent.merchant)

        val analysis = CaptureParser.parseScreen(rule("com.taobao.taobao"), obs, options)
        assertEquals(PageKind.CASHIER, analysis.kind)
    }

    @Test
    fun quickPayWithoutAlipayGlyphDoesNotAssumeAlipay() {
        // 只有"快速支付"没有"支"图标/支付宝文案时，不强行认成支付宝
        val obs = sizedScreen(
            "com.taobao.taobao",
            "¥7.60" to 72,
            "快速支付" to 34
        )
        val intent = CaptureParser.parseCashierIntent(rule("com.taobao.taobao"), obs)
        assertNotNull(intent)
        assertEquals("银行卡", intent!!.channel)
    }

    @Test
    fun cashierPrefersPayableTotalOverOrderAmount() {
        // 有"订单金额"和"合计"同时存在时，收银台意图必须取实付合计
        val obs = screen(
            "com.taobao.taobao",
            "订单金额 ¥28.4", "商品总价 ¥28.4", "合计 ¥24.7", "提交订单"
        )
        val intent = CaptureParser.parseCashierIntent(rule("com.taobao.taobao"), obs)
        assertNotNull(intent)
        assertEquals(24.7, intent!!.amount, 0.001)
    }

    @Test
    fun taobaoShangouSheetUsesHeadlineAmount() {
        // 淘宝闪购付款弹窗没有"支付金额"标签，只有大字 ¥7.60
        val obs = sizedScreen(
            "com.taobao.taobao",
            "更改付款方式" to 30,
            "淘宝闪购商户" to 30,
            "¥7.60" to 72,
            "有2个优惠可选" to 30,
            "余额宝" to 30,
            "优先使用此方式，按支付顺序完成付款" to 30,
            "极速付款" to 50
        )
        val intent = CaptureParser.parseCashierIntent(rule("com.taobao.taobao"), obs)
        assertNotNull(intent)
        assertEquals(7.60, intent!!.amount, 0.001)
        assertEquals("支付宝", intent.channel)
        assertEquals("淘宝闪购商户", intent.merchant)

        val analysis = CaptureParser.parseScreen(rule("com.taobao.taobao"), obs, options)
        assertEquals(PageKind.CASHIER, analysis.kind)
    }

    @Test
    fun xianyuSuccessPageDoesNotPickRewardAmounts() {
        // 真机成功页：只有"支付成功"+奖励区/商品推荐，页面里的 0.01、¥17.20 都不是本次金额
        val analysis = CaptureParser.parseScreen(
            rule("com.taobao.idlefish"),
            screen(
                "com.taobao.idlefish",
                "支付成功", "返回首页", "查看订单",
                "集市新人限时红包", "仅限24小时内领取", "去领取",
                "恭喜你获得奖励", "恭喜你获得", "150", "闲鱼币", "下次购物可抵钱用", "去领取",
                "你有1份", "免单", "待抽取", "最高全额返", "去参与",
                "获得", "50g", "蚂蚁森林能量", "每天1次限时领，0.01元兑好礼", "去领取",
                "包邮 【全新清仓甩卖】套", "¥", "17", ".20", "10人想要",
                "包邮 日式迷你棘轮扳手", "¥", "120", "47人想要"
            ),
            options
        )
        assertEquals(PageKind.PAY_SUCCESS, analysis.kind)
        assertNull(analysis.facts)
        assertEquals(Reason.NO_AMOUNT, analysis.reason)
        assertEquals("支付成功", analysis.completionMark)
    }

    @Test
    fun xianyuAlipaySheetYieldsPaymentIntent() {
        // 真机：闲鱼内嵌支付宝付款弹窗，包名仍是闲鱼，金额只在这里出现
        val obs = screen(
            "com.taobao.idlefish",
            "更改付款方式", "闲鱼平台商户", "支付金额10.00元", "有3个优惠可选",
            "余额宝", "优先使用此方式，按支付顺序完成付款", "极速付款"
        )
        val intent = CaptureParser.parseCashierIntent(rule("com.taobao.idlefish"), obs)
        assertNotNull(intent)
        assertEquals(10.0, intent!!.amount, 0.001)
        assertEquals("支付宝", intent.channel)
        assertEquals("闲鱼平台商户", intent.merchant)

        val analysis = CaptureParser.parseScreen(rule("com.taobao.idlefish"), obs, options)
        assertEquals(PageKind.CASHIER, analysis.kind)
        assertEquals(Reason.PAGE_CASHIER, analysis.reason)
    }

    @Test
    fun xianyuConfirmPageYieldsPaymentIntent() {
        // 真机确认购买页：按钮文案里带应付金额，不能被下面一排规格价带偏
        val obs = screen(
            "com.taobao.idlefish",
            "现在下单，卖家承诺48小时发货",
            "日式横浜短六角梅花型批头起子实心内六角一字十字棘轮螺丝刀套装",
            "¥10.00", "运费", "包邮", "型号（12）", "购买数量",
            "8件套内六角批头 ¥10.00 可选，按钮",
            "8件套内六花批头 ¥10.00 已选中，按钮",
            "棘轮螺丝刀+内六角内六花 ¥30.00 可选，按钮",
            "确认购买 10.00，按钮",
            "¥10.00"
        )
        val intent = CaptureParser.parseCashierIntent(rule("com.taobao.idlefish"), obs)
        assertNotNull(intent)
        assertEquals(10.0, intent!!.amount, 0.001)

        val analysis = CaptureParser.parseScreen(rule("com.taobao.idlefish"), obs, options)
        assertEquals(PageKind.CASHIER, analysis.kind)
    }

    @Test
    fun hostSuccessPageIgnoresWeakLabelAmount() {
        // 宿主成功页上弱标签附近的数字（奖励/优惠/推荐）一律不当金额，留给收银台 intent
        val analysis = CaptureParser.parseScreen(
            rule("com.taobao.idlefish"),
            screen("com.taobao.idlefish", "支付成功", "支付 0.01元"),
            options
        )
        assertEquals(PageKind.PAY_SUCCESS, analysis.kind)
        assertNull(analysis.facts)
        assertEquals(Reason.NO_AMOUNT, analysis.reason)
    }

    @Test
    fun separateLinesDoNotFuseIntoCompletionMark() {
        // "支付" 和 "成功" 分属两行时不能拼成 "支付成功"：页面本来没有完成语义
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen("com.eg.android.AlipayGphone", "支付", "成功", "¥38.50"),
            options
        )
        assertEquals(PageKind.UNKNOWN, analysis.kind)
        assertEquals(Reason.NO_COMPLETION_MARK, analysis.reason)
        assertNull(analysis.facts)
    }

    @Test
    fun navigationTabsAcrossLinesStillRecognized() {
        // 行间留空格后，"扫一扫" / "收付款" 这种跨节点导航标记仍要能命中
        val analysis = CaptureParser.parseScreen(
            rule("com.eg.android.AlipayGphone"),
            screen("com.eg.android.AlipayGphone", "扫一扫", "收付款", "卡包"),
            options
        )
        assertEquals(PageKind.BILL_LIST, analysis.kind)
        assertEquals(Reason.PAGE_LIST, analysis.reason)
    }
}
