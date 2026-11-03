package muyian.bill.listener

/**
 * 页面分类器：把"整页正则先到先得"改成"信号 → 假设 → 分数"。
 *
 * 信号带权重、证据文本和原因码；同一 PageKind 的多个信号取最高权重，
 * 不同 PageKind 之间取 top1，并保留 top1/top2 的 margin。
 * 权重刻意保持现有判定优先级（失败 > 处理中 > 列表 > 完成 > 收银台 > 营销 > 聊天），
 * 因此这是一次可解释化重构，不改变已回归页面的结论。
 */

internal data class Completion(
    val kind: PageKind?,
    val direction: Direction,
    val mark: String?,
    val markRegex: java.util.regex.Pattern
)

internal enum class PageSignalKind {
    FAILURE,
    PENDING,
    BILL_LIST,
    PAY_APP_HOME,
    ORDER_MGMT,
    TRANSFER_ROWS,
    COMPLETION,
    CASHIER,
    AD,
    CHAT,
    CHAT_STRICT,
    CONVERSATION_LIST,
    MULTI_TRANSACTION,
    ORDER_DETAIL,
    UNKNOWN
}

internal data class PageSignal(
    val kind: PageSignalKind,
    val weight: Double,
    val evidence: String,
    val reason: Reason? = null
)

internal data class PageHypothesis(
    val kind: PageKind,
    val score: Double,
    val reason: Reason?,
    val signals: List<PageSignal>
)

internal data class Classification(
    val kind: PageKind,
    val reason: Reason?,
    val completion: Completion?,
    val confidence: Double,
    val margin: Double,
    val signals: List<PageSignal>,
    val hypotheses: List<PageHypothesis>
) {
    val isSuccess: Boolean
        get() = kind == PageKind.PAY_SUCCESS ||
            kind == PageKind.TRANSFER_SUCCESS ||
            kind == PageKind.RECEIVE_SUCCESS ||
            kind == PageKind.REFUND_SUCCESS ||
            kind == PageKind.REPAYMENT_SUCCESS
}

/** 解析结果 + 分类证据；classification 为 null 表示在分类前就被拒（空页等）。 */
internal data class ParsedPage(
    val analysis: PageAnalysis,
    val classification: Classification?
)

internal object PageClassifier {

    // 权重是"优先级"而不是概率；数值间隔保证现有判定顺序不变。
    private const val W_FAILURE = 1100.0
    private const val W_PENDING = 1060.0
    private const val W_BILL_LIST = 1020.0
    private const val W_PAY_APP_HOME = 1010.0
    private const val W_ORDER_MGMT = 1000.0
    private const val W_TRANSFER_ROWS = 990.0
    private const val W_CHAT_BOOST = 970.0
    private const val W_MARKED_ROWS = 960.0
    private const val W_COMPLETION = 900.0
    private const val W_CASHIER = 850.0
    private const val W_AD = 800.0
    private const val W_CHAT = 790.0
    private const val W_ORDER_DETAIL = 700.0
    private const val W_UNKNOWN = 0.0
    private const val MAX_WEIGHT = W_FAILURE
    private const val AMBIGUITY_MARGIN = 5.0

    private val ROW_TIME_LABEL = java.util.regex.Pattern.compile(
        "^(?:上午|下午|中午|晚上|凌晨|早上)?\\s*[0-9]{1,2}\\s*[:：]\\s*[0-9]{2}$|" +
            "^(?:昨天|前天|星期[一二三四五六日天]|周[一二三四五六日天])$|" +
            "^[0-9]{1,2}\\s*[/月]\\s*[0-9]{1,2}\\s*日?$"
    )

    fun classifyScreen(doc: PageDoc, rule: AppRule): Classification {
        val compact = doc.flowText
        val signals = ArrayList<PageSignal>()
        if (compact.isEmpty()) {
            return decide(emptyList(), null, signals, Reason.NO_COMPLETION_MARK)
        }

        find(CaptureRules.FAILURE, compact)?.let {
            signals.add(PageSignal(PageSignalKind.FAILURE, W_FAILURE, it, Reason.PAGE_FAILURE))
        }
        find(CaptureRules.PENDING, compact)?.let {
            signals.add(PageSignal(PageSignalKind.PENDING, W_PENDING, it, Reason.PAGE_PENDING))
        }
        val markedRows = countMarkedAmountRows(doc)
        if (!looksLikeSingleBillDetail(compact, markedRows)) {
            find(CaptureRules.BILL_LIST, compact)?.let {
                signals.add(PageSignal(PageSignalKind.BILL_LIST, W_BILL_LIST, it, Reason.PAGE_LIST))
            }
        }
        find(CaptureRules.PAY_APP_HOME, compact)?.let {
            signals.add(PageSignal(PageSignalKind.PAY_APP_HOME, W_PAY_APP_HOME, it, Reason.PAGE_LIST))
        }
        find(CaptureRules.ORDER_MGMT, compact)?.let {
            signals.add(PageSignal(PageSignalKind.ORDER_MGMT, W_ORDER_MGMT, it, Reason.PAGE_ORDER_MGMT))
        }
        val transferRow = find(CaptureRules.TRANSFER_ROW, compact)
        if (transferRow != null && !CaptureRules.TRANSFER_OUT_MARK.matcher(compact).find()) {
            signals.add(
                PageSignal(
                    PageSignalKind.TRANSFER_ROWS, W_TRANSFER_ROWS,
                    transferRow, Reason.MULTI_TRANSACTION
                )
            )
        }

        val completion = completionOf(compact)
        if (completion.kind != null) {
            signals.add(
                PageSignal(
                    PageSignalKind.COMPLETION, W_COMPLETION,
                    completion.mark ?: "", null
                )
            )
        }

        val chatApp = rule.adapter == AdapterId.WECHAT || rule.adapter == AdapterId.ALIPAY
        val chatStrict = find(CaptureRules.CHAT_STRICT, compact)
        if (chatApp && chatStrict != null) {
            val receipt = CaptureRules.RECEIPT_MARK.matcher(compact).find()
            val boost = completion.kind != null && (!receipt || countMarks(compact) != 1)
            signals.add(
                PageSignal(
                    PageSignalKind.CHAT_STRICT,
                    if (boost) W_CHAT_BOOST else W_CHAT,
                    chatStrict,
                    Reason.PAGE_CHAT
                )
            )
        }
        if (chatApp && looksLikeConversationList(doc)) {
            signals.add(
                PageSignal(
                    PageSignalKind.CONVERSATION_LIST, W_CHAT_BOOST,
                    "会话列表时间标签", Reason.PAGE_CHAT
                )
            )
        }
        if (markedRows >= 2) {
            signals.add(
                PageSignal(
                    PageSignalKind.MULTI_TRANSACTION, W_MARKED_ROWS,
                    "多行完成标记+金额", Reason.MULTI_TRANSACTION
                )
            )
        }

        if (completion.kind == null) {
            find(CaptureRules.PAY_TRIGGER, compact)?.let {
                signals.add(PageSignal(PageSignalKind.CASHIER, W_CASHIER, it, Reason.PAGE_CASHIER))
            }
        }
        // 营销/聊天信号即使与完成标记同页也保留，但权重低于完成语义；
        // chatApp 的会话页由上面的 boost 单独压过完成标记。
        find(CaptureRules.AD, compact)?.let {
            signals.add(PageSignal(PageSignalKind.AD, W_AD, it, Reason.PAGE_AD))
        }
        find(CaptureRules.CHAT, compact)?.let {
            signals.add(PageSignal(PageSignalKind.CHAT, W_CHAT, it, Reason.PAGE_CHAT))
        }
        if (completion.kind == null &&
            (CaptureText.extractOrderNo(doc.texts().joinToString("\n")).isNotEmpty() ||
                compact.contains("订单详情"))
        ) {
            signals.add(
                PageSignal(
                    PageSignalKind.ORDER_DETAIL, W_ORDER_DETAIL,
                    "订单详情", Reason.PAGE_ORDER_MGMT
                )
            )
        }

        return decide(hypothesesOf(signals, completion), completion, signals, Reason.NO_COMPLETION_MARK)
    }

    fun classifyNotification(
        doc: PageDoc,
        rule: AppRule,
        obs: NotificationObservation
    ): Classification {
        val compact = doc.flowText
        val signals = ArrayList<PageSignal>()
        if (compact.isEmpty()) {
            return decide(emptyList(), null, signals, Reason.NO_COMPLETION_MARK)
        }

        find(CaptureRules.FAILURE, compact)?.let {
            return reject(PageKind.PAY_FAILURE, Reason.PAGE_FAILURE, PageSignalKind.FAILURE, W_FAILURE, it)
        }
        find(CaptureRules.PENDING, compact)?.let {
            return reject(PageKind.PAY_PENDING, Reason.PAGE_PENDING, PageSignalKind.PENDING, W_PENDING, it)
        }
        if (CaptureRules.NOTIFY_IGNORE.matcher(compact).find()) {
            find(CaptureRules.AD, compact)?.let {
                return reject(PageKind.AD, Reason.PAGE_AD, PageSignalKind.AD, W_AD, it)
            }
            find(CaptureRules.BILL_LIST, compact)?.let {
                return reject(PageKind.BILL_LIST, Reason.PAGE_LIST, PageSignalKind.BILL_LIST, W_BILL_LIST, it)
            }
            return reject(
                PageKind.UNKNOWN, Reason.PAGE_UNKNOWN,
                PageSignalKind.UNKNOWN, W_UNKNOWN, "忽略通知"
            )
        }
        find(CaptureRules.BILL_LIST, compact)?.let {
            return reject(PageKind.BILL_LIST, Reason.PAGE_LIST, PageSignalKind.BILL_LIST, W_BILL_LIST, it)
        }
        if (!notificationSenderAllowed(rule, obs)) {
            return reject(PageKind.CHAT, Reason.PAGE_CHAT, PageSignalKind.CHAT, W_CHAT, "非支付发送方")
        }

        var completion = completionOf(compact)
        if (completion.kind == null) {
            find(CaptureRules.AD, compact)?.let {
                return reject(PageKind.AD, Reason.PAGE_AD, PageSignalKind.AD, W_AD, it)
            }
            val hasWord = CaptureRules.EXPENSE_WORD.matcher(compact).find() ||
                CaptureRules.INCOME_WORD.matcher(compact).find()
            if (hasWord && rule.adapter == AdapterId.WECHAT) {
                return reject(PageKind.CHAT, Reason.PAGE_CHAT, PageSignalKind.CHAT, W_CHAT, "微信会话")
            }
            completion = wordCompletion(compact)
                ?: return decide(emptyList(), null, signals, Reason.NO_COMPLETION_MARK)
        }

        signals.add(
            PageSignal(
                PageSignalKind.COMPLETION, W_COMPLETION,
                completion.mark ?: "", null
            )
        )
        return decide(hypothesesOf(signals, completion), completion, signals, Reason.NO_COMPLETION_MARK)
    }

    // ---------- 判定 ----------

    internal fun decide(
        hypotheses: List<PageHypothesis>,
        completion: Completion?,
        signals: List<PageSignal>,
        unknownReason: Reason
    ): Classification {
        val ordered = hypotheses.sortedByDescending { it.score }
        val top = ordered.firstOrNull()
            ?: PageHypothesis(PageKind.UNKNOWN, W_UNKNOWN, unknownReason, emptyList())
        val second = ordered.getOrNull(1)
        val margin = top.score - (second?.score ?: W_UNKNOWN)
        if (second != null && margin < AMBIGUITY_MARGIN) {
            return Classification(
                kind = PageKind.UNKNOWN,
                reason = Reason.PAGE_AMBIGUOUS,
                completion = completion,
                confidence = 0.0,
                margin = margin,
                signals = signals,
                hypotheses = ordered
            )
        }
        return Classification(
            kind = top.kind,
            reason = top.reason ?: if (top.kind == PageKind.UNKNOWN) unknownReason else null,
            completion = completion,
            confidence = minOf(1.0, top.score / MAX_WEIGHT),
            margin = margin,
            signals = signals,
            hypotheses = ordered
        )
    }

    private fun reject(
        kind: PageKind,
        reason: Reason,
        signalKind: PageSignalKind,
        weight: Double,
        evidence: String
    ): Classification {
        val signal = PageSignal(signalKind, weight, evidence, reason)
        val hypothesis = PageHypothesis(kind, weight, reason, listOf(signal))
        return Classification(
            kind = kind,
            reason = reason,
            completion = null,
            confidence = minOf(1.0, weight / MAX_WEIGHT),
            margin = weight,
            signals = listOf(signal),
            hypotheses = listOf(hypothesis)
        )
    }

    private fun hypothesesOf(
        signals: List<PageSignal>,
        completion: Completion?
    ): List<PageHypothesis> {
        val grouped = LinkedHashMap<PageKind, MutableList<PageSignal>>()
        for (signal in signals) {
            val kind = kindOf(signal, completion)
            grouped.getOrPut(kind) { ArrayList() }.add(signal)
        }
        return grouped.map { (kind, list) ->
            val top = list.maxByOrNull { it.weight } ?: list[0]
            PageHypothesis(kind, top.weight, top.reason, list)
        }
    }

    private fun kindOf(signal: PageSignal, completion: Completion?): PageKind = when (signal.kind) {
        PageSignalKind.FAILURE -> PageKind.PAY_FAILURE
        PageSignalKind.PENDING -> PageKind.PAY_PENDING
        PageSignalKind.BILL_LIST,
        PageSignalKind.PAY_APP_HOME,
        PageSignalKind.TRANSFER_ROWS,
        PageSignalKind.MULTI_TRANSACTION -> PageKind.BILL_LIST
        PageSignalKind.ORDER_MGMT -> PageKind.ORDER_LIST
        PageSignalKind.COMPLETION -> completion?.kind ?: PageKind.UNKNOWN
        PageSignalKind.CASHIER -> PageKind.CASHIER
        PageSignalKind.AD -> PageKind.AD
        PageSignalKind.CHAT,
        PageSignalKind.CHAT_STRICT,
        PageSignalKind.CONVERSATION_LIST -> PageKind.CHAT
        PageSignalKind.ORDER_DETAIL -> PageKind.ORDER_DETAIL
        PageSignalKind.UNKNOWN -> PageKind.UNKNOWN
    }

    private fun find(pattern: java.util.regex.Pattern, compact: String): String? {
        val matcher = pattern.matcher(compact)
        if (!matcher.find()) return null
        return matcher.group() ?: ""
    }

    // ---------- 完成语义 ----------

    private fun completionOf(compact: String): Completion {
        CaptureRules.REFUND_MARK.matcher(compact).let {
            if (it.find()) {
                return Completion(
                    PageKind.REFUND_SUCCESS, Direction.REFUND, it.group(),
                    CaptureRules.REFUND_MARK
                )
            }
        }
        CaptureRules.REPAYMENT_MARK.matcher(compact).let {
            if (it.find()) {
                return Completion(
                    PageKind.REPAYMENT_SUCCESS, Direction.REPAYMENT, it.group(),
                    CaptureRules.REPAYMENT_MARK
                )
            }
        }
        CaptureRules.TRANSFER_SELF_MARK.matcher(compact).let {
            if (it.find()) {
                return Completion(
                    PageKind.TRANSFER_SUCCESS, Direction.TRANSFER, it.group(),
                    CaptureRules.TRANSFER_SELF_MARK
                )
            }
        }
        val hasExpense = CaptureRules.EXPENSE_MARK.matcher(compact).find()
        val hasIncome = CaptureRules.INCOME_MARK.matcher(compact).find()
        if (CaptureRules.TRANSFER_OUT_MARK.matcher(compact).find()) {
            val m = CaptureRules.TRANSFER_OUT_MARK.matcher(compact)
            val mark = if (m.find()) m.group() else "转账成功"
            return Completion(
                PageKind.TRANSFER_SUCCESS, Direction.EXPENSE, mark,
                CaptureRules.TRANSFER_OUT_MARK
            )
        }
        if (hasIncome && !hasExpense) {
            val m = CaptureRules.INCOME_MARK.matcher(compact)
            return Completion(
                PageKind.RECEIVE_SUCCESS, Direction.INCOME,
                if (m.find()) m.group() else "到账", CaptureRules.INCOME_MARK
            )
        }
        if (hasExpense) {
            val m = CaptureRules.EXPENSE_MARK.matcher(compact)
            return Completion(
                PageKind.PAY_SUCCESS, Direction.EXPENSE,
                if (m.find()) m.group() else "支付成功", CaptureRules.EXPENSE_MARK
            )
        }
        if (hasIncome) {
            val m = CaptureRules.INCOME_MARK.matcher(compact)
            return Completion(
                PageKind.RECEIVE_SUCCESS, Direction.INCOME,
                if (m.find()) m.group() else "到账", CaptureRules.INCOME_MARK
            )
        }
        return Completion(null, Direction.UNKNOWN, null, CaptureRules.EXPENSE_MARK)
    }

    /** 通知里常见的"消费/支出/到账"没有"成功"二字，作为完成语义的最后兜底。 */
    private fun wordCompletion(compact: String): Completion? {
        val expense = CaptureRules.EXPENSE_WORD.matcher(compact)
        if (expense.find()) {
            return Completion(
                PageKind.PAY_SUCCESS, Direction.EXPENSE, expense.group(),
                CaptureRules.EXPENSE_WORD
            )
        }
        val income = CaptureRules.INCOME_WORD.matcher(compact)
        if (income.find()) {
            return Completion(
                PageKind.RECEIVE_SUCCESS, Direction.INCOME, income.group(),
                CaptureRules.INCOME_WORD
            )
        }
        return null
    }

    private fun countMarks(compact: String): Int {
        var count = 0
        for (pattern in listOf(
            CaptureRules.EXPENSE_MARK, CaptureRules.INCOME_MARK,
            CaptureRules.REFUND_MARK, CaptureRules.REPAYMENT_MARK
        )) {
            val m = pattern.matcher(compact)
            while (m.find()) {
                count++
                if (count > 3) return count
            }
        }
        return count
    }

    // ---------- 结构特征 ----------

    private fun looksLikeConversationList(doc: PageDoc): Boolean {
        var rows = 0
        for (i in doc.indices) {
            if (ROW_TIME_LABEL.matcher(doc.text(i)).matches()) {
                rows++
                if (rows >= 3) return true
            }
        }
        return false
    }

    /**
     * 支付宝账单详情顶部有「全部账单」入口，会命中 BILL_LIST。
     * 单笔详情必须同时具备详情结构（标题或支付时间+付款方式、账单分类/计入收支），
     * 且不能出现多行"完成标记+金额"；否则仍按列表拒绝。
     */
    private fun looksLikeSingleBillDetail(compact: String, markedRows: Int): Boolean {
        val hasDetailTitle = compact.contains("账单详情")
        val hasDetailFields = compact.contains("支付时间") && compact.contains("付款方式")
        if (!hasDetailTitle && !hasDetailFields) return false
        if (!compact.contains("计入收支") && !compact.contains("账单分类")) return false
        if (markedRows >= 2) return false
        return CaptureRules.ANY_COMPLETION_MARK.matcher(compact).find()
    }

    /** 统计"一行完成标记 + 邻近金额"的行数；≥2 说明是列表，不是单笔结果页。 */
    private fun countMarkedAmountRows(doc: PageDoc): Int {
        var count = 0
        var lastIndex = -10
        var lastAmount = -1.0
        for (i in doc.indices) {
            val text = doc.text(i)
            if (!CaptureRules.ANY_COMPLETION_MARK.matcher(text).find()) continue
            val amount = AmountExtractor.amountLike(text)
                ?: AmountExtractor.amountLike(doc.text(i + 1))
                ?: continue
            // 单笔结果卡常把"转账成功 / 金额"重复渲染成标题+状态两行；
            // 相邻且同额视为同一笔，避免把一张回执判成交易列表。
            if (count > 0 && i - lastIndex <= 3 && Math.abs(amount - lastAmount) < 0.01) {
                lastIndex = i
                continue
            }
            count++
            lastIndex = i
            lastAmount = amount
        }
        return count
    }

    // ---------- 通知发送方 ----------

    private fun notificationSenderAllowed(rule: AppRule, obs: NotificationObservation): Boolean {
        if (rule.notifyTitleKeywords.isEmpty() && rule.notifyChannelKeywords.isEmpty()) return true
        val scope = (obs.title + "\n" + obs.subText + "\n" + obs.channelId + "\n" + obs.notificationKey)
        for (word in rule.notifyTitleKeywords) {
            if (scope.contains(word)) return true
        }
        for (word in rule.notifyChannelKeywords) {
            if (obs.channelId.contains(word) || scope.contains(word)) return true
        }
        // 支付结果类标题本身是强信号（部分 ROM 会把渠道名放在 bigText）
        val compact = CaptureText.compact(scope)
        return CaptureRules.EXPENSE_MARK.matcher(compact).find() ||
            CaptureRules.INCOME_MARK.matcher(compact).find() ||
            CaptureRules.REFUND_MARK.matcher(compact).find()
    }
}
