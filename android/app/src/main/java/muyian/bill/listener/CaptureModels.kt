package muyian.bill.listener

/**
 * 自动记账引擎的纯数据模型：不依赖 Android，可直接被单元测试覆盖。
 * 采集层只产出 Observation，解析层产出 Facts，引擎据此组装 Candidate 并交给账本。
 */

enum class CaptureSource { SCREEN, NOTIFICATION }

enum class AppRole { PAY, HOST, OFF }

enum class AdapterId { ALIPAY, WECHAT, UNIONPAY, BANK, MEITUAN, JD, GENERIC }

enum class PageKind(val label: String) {
    PAY_SUCCESS("支付成功"),
    TRANSFER_SUCCESS("转账成功"),
    RECEIVE_SUCCESS("收款到账"),
    REFUND_SUCCESS("退款成功"),
    REPAYMENT_SUCCESS("还款成功"),
    CASHIER("收银台"),
    PAY_FAILURE("支付失败"),
    PAY_PENDING("处理中"),
    ORDER_DETAIL("订单详情"),
    ORDER_LIST("订单列表"),
    BILL_LIST("账单列表"),
    CHAT("聊天/会话"),
    AD("营销活动"),
    UNKNOWN("未识别页面")
}

enum class Direction(val label: String) {
    EXPENSE("支出"),
    INCOME("收入"),
    REFUND("退款"),
    TRANSFER("转账"),
    REPAYMENT("还款"),
    UNKNOWN("未知")
}

enum class AmountTier(val label: String) {
    STRONG_LABEL("强标签"),
    STRUCT_HEADLINE("页面大字"),
    WEAK_LABEL("弱标签"),
    CURRENCY_CONTEXT("货币上下文")
}

/** 判定原因码：写进账本与诊断页，测试逐条断言。 */
enum class Reason(val label: String) {
    SELF("本应用"),
    SYSTEM_UI("系统界面"),
    NOT_CANDIDATE("非记账应用"),
    APP_OFF("应用已关闭"),
    PAGE_LIST("账单/订单列表"),
    MULTI_TRANSACTION("同一页出现多笔交易"),
    PAGE_ORDER_MGMT("订单管理页"),
    PAGE_AD("营销活动页"),
    PAGE_CHAT("聊天/会话页"),
    PAGE_FAILURE("支付失败/已取消"),
    PAGE_PENDING("处理中/待支付"),
    PAGE_CASHIER("收银台"),
    PAGE_UNKNOWN("未识别页面"),
    NO_PAYMENT_CONTEXT("没有支付路线"),
    NO_AMOUNT("没有可信金额"),
    NO_DIRECTION("无法判断收支方向"),
    NO_COMPLETION_MARK("没有完成标记"),
    AMOUNT_NOT_ANCHORED("金额与完成标记不相邻"),
    LOW_CONFIDENCE("置信度不足"),
    STALE_PAGE("历史页面"),
    INTERNAL_TRANSFER("本人账户互转"),
    REPAYMENT("信用卡还款"),
    DUPLICATE_ORDER("重复单号"),
    DUPLICATE_SESSION("重复支付会话"),
    DUPLICATE_PAGE("重复结果页"),
    DUPLICATE_CAPTURE("重复捕获"),
    DUPLICATE_BILL("重复账单"),
    QUEUE_FULL("待确认队列已满"),
    ACCEPTED("已捕获"),
    ACCEPTED_REVIEW("已捕获·建议核对")
}

/** 无障碍节点文本：位置与字号用于识别"页面大字金额"，不做跨进程传输。 */
data class UiText(
    val text: String,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
    val sizePx: Int = 0,
    val className: String = "",
    val viewId: String = "",
    val depth: Int = 0
)

data class ScreenObservation(
    val pkg: String,
    val windowTitle: String = "",
    val windowType: Int = 0,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val items: List<UiText>,
    val eventType: Int = 0,
    val observedAt: Long
)

data class NotificationObservation(
    val pkg: String,
    val channelId: String = "",
    val category: String = "",
    val notificationKey: String = "",
    val groupSummary: Boolean = false,
    val ongoing: Boolean = false,
    val title: String = "",
    val text: String = "",
    val subText: String = "",
    val bigText: String = "",
    val lines: List<String> = emptyList(),
    val postTime: Long
)

/**
 * App 规则：角色决定它能不能作为支付上下文，适配器决定金额锚点与通知来源。
 * 用户覆盖项来自 SharedPreferences，内置项来自 CaptureRules；解析器只认这份数据。
 */
data class AppRule(
    val pkg: String,
    val role: AppRole,
    val adapter: AdapterId,
    val label: String = "",
    val amountAnchors: List<String> = emptyList(),
    val amountExcludes: List<String> = emptyList(),
    /** 通知只接受标题/渠道命中这些关键词的（微信支付、支付宝等），为空表示走通用判断 */
    val notifyTitleKeywords: List<String> = emptyList(),
    val notifyChannelKeywords: List<String> = emptyList(),
    val builtin: Boolean = true
)

data class AmountCandidate(
    val value: Double,
    val tier: AmountTier,
    val confidence: Double,
    val anchor: String,
    val evidence: String = "",
    val sizePx: Int = 0,
    val index: Int = 0
)

data class Facts(
    val pageKind: PageKind,
    val direction: Direction,
    val amount: Double,
    val tier: AmountTier,
    val amountConfidence: Double,
    val needsReview: Boolean,
    val channel: String,
    val merchant: String,
    val orderNo: String,
    val occurredAt: Long,
    val completionMark: String,
    val confidence: Double,
    val evidence: List<String> = emptyList(),
    val suppressReason: Reason? = null
)

data class PageAnalysis(
    val kind: PageKind,
    val facts: Facts? = null,
    val reason: Reason? = null
) {
    val isSuccess: Boolean
        get() = facts != null && (
            kind == PageKind.PAY_SUCCESS ||
                kind == PageKind.TRANSFER_SUCCESS ||
                kind == PageKind.RECEIVE_SUCCESS ||
                kind == PageKind.REFUND_SUCCESS ||
                kind == PageKind.REPAYMENT_SUCCESS
            )
}

data class LedgerKey(val key: String, val kind: String, val ttlMs: Long)

data class Candidate(
    val txnId: String,
    val source: CaptureSource,
    val sourcePkg: String,
    val facts: Facts,
    val keys: List<LedgerKey>,
    val createdAt: Long
)

data class ParseOptions(
    val recordTransfers: Boolean = false
)

/** 通知/屏幕统一判定结果；reason 始终有值，便于诊断。 */
data class Decision(
    val accepted: Boolean,
    val reason: Reason,
    val confidence: Double = 0.0,
    val txnId: String = "",
    val candidate: Candidate? = null
)
