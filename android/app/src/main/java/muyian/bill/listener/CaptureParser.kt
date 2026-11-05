package muyian.bill.listener

import java.util.Locale

/**
 * 页面/通知解析器：纯 Kotlin，不依赖 Android，可直接单测。
 * 输出 PageKind + Facts（方向、商户、单号、渠道、置信度）；金额候选统一委托 AmountExtractor。
 */
object CaptureParser {

    fun parseScreen(rule: AppRule, obs: ScreenObservation, options: ParseOptions): PageAnalysis =
        parseScreenDetailed(rule, obs, options).analysis

    internal fun parseScreenDetailed(
        rule: AppRule,
        obs: ScreenObservation,
        options: ParseOptions
    ): ParsedPage {
        val doc = PageDoc.of(obs.pkg, obs.items, obs.observedAt)
        if (doc.isEmpty) {
            return ParsedPage(reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK), null)
        }
        val compact = doc.flowText
        if (compact.isEmpty()) {
            return ParsedPage(reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK), null)
        }

        val classification = PageClassifier.classifyScreen(doc, rule)
        if (!classification.isSuccess) {
            return ParsedPage(
                PageAnalysis(
                    classification.kind,
                    null,
                    classification.reason ?: Reason.PAGE_UNKNOWN
                ),
                classification
            )
        }
        val completion = classification.completion
            ?: return ParsedPage(
                reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK),
                classification
            )
        val kind = completion.kind
            ?: return ParsedPage(
                reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK),
                classification
            )

        val markIndex = doc.lines.indexOfFirst { completion.markRegex.matcher(it.text).find() }
        val picked = AmountExtractor.pickScreen(
            doc, rule, markIndex, completion.direction,
            allowCurrencyContext = rule.role != AppRole.HOST
        )
        val markText = completionMarkText(completion.mark, completion.direction, compact)
        val amount = picked.candidate
        // 宿主结果页只认强锚点/大字金额：闲鱼成功页的 "0.01元兑好礼" 这类弱标签
        // 一旦进入候选就会记成 0.01，金额宁可留给同路线的收银台 intent。
        if (amount != null && rule.role == AppRole.HOST &&
            amount.tier != AmountTier.STRONG_LABEL && amount.tier != AmountTier.STRUCT_HEADLINE
        ) {
            return ParsedPage(
                PageAnalysis(kind, null, Reason.NO_AMOUNT, markText),
                classification
            )
        }
        if (amount == null || amount.value <= 0) {
            return ParsedPage(
                PageAnalysis(kind, null, Reason.NO_AMOUNT, markText),
                classification
            )
        }
        // 非强标签金额必须挨着完成标记；否则很可能是从另一行列表里抓来的数字
        if (amount.tier != AmountTier.STRONG_LABEL && !hasMarkNear(doc, amount.index)) {
            return ParsedPage(
                PageAnalysis(kind, null, Reason.AMOUNT_NOT_ANCHORED, markText),
                classification
            )
        }

        val direction = completion.direction
        if (direction == Direction.UNKNOWN) {
            return ParsedPage(
                PageAnalysis(kind, null, Reason.NO_DIRECTION, markText),
                classification
            )
        }

        val texts = doc.texts()
        val allText = texts.joinToString("\n")
        val orderNo = CaptureText.extractOrderNo(allText)
        val merchant = CaptureText.merchantFromLines(texts)
        val channel = CaptureRules.inferChannel(rule.adapter, rule.label, allText)
        val occurredAt = CaptureText.parseOccurredAt(texts, obs.observedAt)
        val suppress = suppressReason(direction, options)
        val confidence = confidenceOf(amount.confidence, classification.confidence, direction, orderNo)
        val facts = Facts(
            pageKind = kind,
            direction = direction,
            amount = amount.value,
            tier = amount.tier,
            amountConfidence = amount.confidence,
            needsReview = picked.needsReview || amount.confidence < 0.62 || confidence < 0.80,
            channel = channel,
            merchant = merchant,
            orderNo = orderNo,
            occurredAt = occurredAt,
            completionMark = markText,
            confidence = confidence,
            evidence = tendedEvidence(amount, texts, merchant),
            suppressReason = suppress
        )
        return ParsedPage(PageAnalysis(kind, facts, null, markText), classification)
    }

    fun parseNotification(
        rule: AppRule, obs: NotificationObservation, options: ParseOptions
    ): PageAnalysis = parseNotificationDetailed(rule, obs, options).analysis

    internal fun parseNotificationDetailed(
        rule: AppRule,
        obs: NotificationObservation,
        options: ParseOptions
    ): ParsedPage {
        if (obs.groupSummary || obs.ongoing) {
            return ParsedPage(reject(PageKind.UNKNOWN, Reason.PAGE_UNKNOWN), null)
        }
        val lines = ArrayList<String>()
        addLines(lines, obs.title)
        addLines(lines, obs.subText)
        addLines(lines, obs.text)
        addLines(lines, obs.bigText)
        for (line in obs.lines) addLines(lines, line)
        val doc = PageDoc.ofLines(
            obs.pkg,
            lines,
            if (obs.postTime > 0) obs.postTime else System.currentTimeMillis()
        )
        if (doc.isEmpty) {
            return ParsedPage(reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK), null)
        }
        val compact = doc.flowText
        if (compact.isEmpty()) {
            return ParsedPage(reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK), null)
        }

        val classification = PageClassifier.classifyNotification(doc, rule, obs)
        if (!classification.isSuccess) {
            return ParsedPage(
                PageAnalysis(
                    classification.kind,
                    null,
                    classification.reason ?: Reason.PAGE_UNKNOWN
                ),
                classification
            )
        }
        val completion = classification.completion
            ?: return ParsedPage(
                reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK),
                classification
            )
        val kind = completion.kind
            ?: return ParsedPage(
                reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK),
                classification
            )

        val picked = AmountExtractor.pickNotification(doc, rule, completion.direction)
        val amount = picked.candidate
        if (amount == null || amount.value <= 0) {
            return ParsedPage(PageAnalysis(kind, null, Reason.NO_AMOUNT), classification)
        }
        val direction = completion.direction
        if (direction == Direction.UNKNOWN) {
            return ParsedPage(PageAnalysis(kind, null, Reason.NO_DIRECTION), classification)
        }
        val texts = doc.texts()
        val allText = texts.joinToString("\n")
        val orderNo = CaptureText.extractOrderNo(allText)
        val merchant = CaptureText.merchantFromNotification(
            obs.text, obs.subText, obs.bigText, obs.lines
        )
        val channel = CaptureRules.inferChannel(rule.adapter, rule.label, allText)
        val confidence = confidenceOf(amount.confidence, classification.confidence, direction, orderNo)
        val occurredAt = if (obs.postTime > 0) obs.postTime else System.currentTimeMillis()
        val facts = Facts(
            pageKind = kind,
            direction = direction,
            amount = amount.value,
            tier = amount.tier,
            amountConfidence = amount.confidence,
            needsReview = picked.needsReview || amount.confidence < 0.62 || confidence < 0.80,
            channel = channel,
            merchant = merchant,
            orderNo = orderNo,
            occurredAt = occurredAt,
            completionMark = completionMarkText(completion.mark, direction, compact),
            confidence = confidence,
            evidence = tendedEvidence(amount, texts, merchant),
            suppressReason = suppressReason(direction, options)
        )
        return ParsedPage(PageAnalysis(kind, facts, null), classification)
    }

    // ---------- 支付意图（收银台/确认页） ----------

    /**
     * 从收银台/确认购买页/支付弹窗里取金额。
     * 这些页面没有"完成"语义，但金额是整条支付链路上最可靠的一帧；
     * 后续宿主成功页只有"支付成功"没有金额时，用它补齐。
     *
     * 金额候选统一交给 AmountExtractor.pickCashier：强锚点 → 大字独立金额 →
     * 收银台大字货币金额 → 收银台页内货币兜底。
     */
    fun parseCashierIntent(rule: AppRule, obs: ScreenObservation): PaymentIntent? {
        val doc = PageDoc.of(obs.pkg, obs.items, obs.observedAt)
        if (doc.isEmpty) return null
        val candidate = AmountExtractor.pickCashier(doc, rule) ?: return null
        return cashierIntent(rule, obs, doc.texts(), candidate.value, candidate.confidence)
    }

    private fun cashierIntent(
        rule: AppRule,
        obs: ScreenObservation,
        lines: List<String>,
        amount: Double,
        confidence: Double
    ): PaymentIntent = PaymentIntent(
        amount = amount,
        channel = CaptureRules.inferChannel(rule.adapter, rule.label, lines.joinToString("\n")),
        merchant = cashierMerchant(lines),
        pkg = obs.pkg,
        at = obs.observedAt,
        confidence = confidence
    )

    private fun cashierMerchant(lines: List<String>): String {
        // 支付宝付款弹窗的收款方常见形态："闲鱼平台商户"（没有冒号，不能走标签后缀）
        for (line in lines) {
            val text = CaptureText.normalize(line)
            if (text.endsWith("商户") && CaptureText.isUsableMerchant(text)) return text
        }
        return CaptureText.merchantFromLines(lines)
    }

    // ---------- 页面完成语义 ----------

    private fun hasMarkNear(doc: PageDoc, index: Int): Boolean {
        if (index < 0) return false
        val start = maxOf(0, index - 12)
        val end = minOf(doc.lines.size - 1, index + 12)
        var i = start
        while (i <= end) {
            if (CaptureRules.ANY_COMPLETION_MARK.matcher(doc.text(i)).find()) {
                return true
            }
            i++
        }
        return false
    }

    /** "提现成功"本身看不出进出，补上同页的"到账"给符号判断用。 */
    private fun completionMarkText(mark: String?, direction: Direction, compact: String): String {
        val base = mark ?: ""
        return if (direction == Direction.TRANSFER && compact.contains("到账")) {
            if (base.contains("到账")) base else base + " 到账"
        } else {
            base
        }
    }

    private fun suppressReason(direction: Direction, options: ParseOptions): Reason? {
        if (options.recordTransfers) return null
        return when (direction) {
            Direction.TRANSFER -> Reason.INTERNAL_TRANSFER
            Direction.REPAYMENT -> Reason.REPAYMENT
            else -> null
        }
    }

    private fun confidenceOf(
        amount: Double, page: Double, direction: Direction, orderNo: String
    ): Double {
        val dir = when (direction) {
            Direction.EXPENSE, Direction.INCOME -> 0.95
            Direction.REFUND, Direction.TRANSFER, Direction.REPAYMENT -> 1.0
            Direction.UNKNOWN -> 0.0
        }
        var score = 0.55 * amount + 0.25 * page + 0.15 * dir
        if (orderNo.isNotEmpty()) score += 0.05
        return Math.round(score * 1000.0) / 1000.0
    }

    private fun tendedEvidence(amount: AmountCandidate, lines: List<String>, merchant: String): List<String> {
        val out = ArrayList<String>()
        if (amount.evidence.isNotEmpty()) out.add(amount.evidence)
        if (merchant.isNotEmpty()) out.add("商户：" + merchant)
        for (line in lines) {
            if (out.size >= 4) break
            if (line.isNotEmpty() && line != amount.evidence && out.size < 4) out.add(line)
        }
        return out
    }

    // ---------- 通知发送方 ----------

    private fun addLines(out: ArrayList<String>, raw: String) {
        val n = CaptureText.normalize(raw)
        if (n.isEmpty()) return
        for (part in n.split('\n')) {
            val line = CaptureText.normalize(part)
            if (line.isNotEmpty()) out.add(line)
        }
    }

    private fun reject(kind: PageKind, reason: Reason): PageAnalysis = PageAnalysis(kind, null, reason)

    /** 诊断/测试用：把金额候选格式化成稳定字符串。 */
    fun describeAmount(candidate: AmountCandidate?): String {
        if (candidate == null) return "none"
        return String.format(
            Locale.US, "%s %.2f %s %.2f", candidate.tier.name, candidate.value,
            candidate.anchor, candidate.confidence
        )
    }
}
