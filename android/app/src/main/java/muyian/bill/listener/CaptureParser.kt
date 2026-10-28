package muyian.bill.listener

import java.util.Locale

/**
 * 页面/通知解析器：纯 Kotlin，不依赖 Android，可直接单测。
 * 输出 PageKind + Facts（金额候选、方向、商户、单号、渠道、置信度）。
 */
object CaptureParser {

    fun parseScreen(rule: AppRule, obs: ScreenObservation, options: ParseOptions): PageAnalysis {
        val items = obs.items
            .filter { it.text.isNotBlank() }
            .sortedWith(compareBy<UiText> { it.top }.thenBy { it.left })
        if (items.isEmpty()) return reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK)
        val lines = items.map { CaptureText.normalize(it.text) }
        val compact = CaptureText.compact(lines)
        if (compact.isEmpty()) return reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK)

        // 失败/处理中优先：即使页面里同时出现"支付成功"历史文案也不认
        if (CaptureRules.FAILURE.matcher(compact).find()) {
            return reject(PageKind.PAY_FAILURE, Reason.PAGE_FAILURE)
        }
        if (CaptureRules.PENDING.matcher(compact).find()) {
            return reject(PageKind.PAY_PENDING, Reason.PAGE_PENDING)
        }
        if (CaptureRules.BILL_LIST.matcher(compact).find()) {
            return reject(PageKind.BILL_LIST, Reason.PAGE_LIST)
        }
        // 支付 App 首页（扫一扫/收付款/卡包/最近消息）：页面里会同时出现多笔历史消息
        if (CaptureRules.PAY_APP_HOME.matcher(compact).find()) {
            return reject(PageKind.BILL_LIST, Reason.PAGE_LIST)
        }
        if (CaptureRules.ORDER_MGMT.matcher(compact).find()) {
            return reject(PageKind.ORDER_LIST, Reason.PAGE_ORDER_MGMT)
        }
        // 转账历史/收款人列表：有"转给TA/收到TA"但没有"转账成功"，整页不是单笔结果
        if (CaptureRules.TRANSFER_ROW.matcher(compact).find() &&
            !CaptureRules.TRANSFER_OUT_MARK.matcher(compact).find()
        ) {
            return reject(PageKind.BILL_LIST, Reason.MULTI_TRANSACTION)
        }

        val completion = completionOf(compact)
        if (completion.kind == null) {
            // 没有完成标记：依次判断营销页、聊天页、收银台、订单详情
            if (CaptureRules.AD.matcher(compact).find()) {
                return reject(PageKind.AD, Reason.PAGE_AD)
            }
            if (CaptureRules.CHAT.matcher(compact).find()) {
                return reject(PageKind.CHAT, Reason.PAGE_CHAT)
            }
            if (CaptureRules.PAY_TRIGGER.matcher(compact).find()) {
                return reject(PageKind.CASHIER, Reason.PAGE_CASHIER)
            }
            if (CaptureText.extractOrderNo(lines.joinToString("\n")).isNotEmpty() ||
                compact.contains("订单详情")
            ) {
                return reject(PageKind.ORDER_DETAIL, Reason.PAGE_ORDER_MGMT)
            }
            return reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK)
        }

        // 支付 App 的会话页：聊天里的历史卡片不能当成新交易；
        // 但"微信支付"服务号会话里唯一一条带凭证/单号的凭证页要放行
        val chatApp = rule.adapter == AdapterId.WECHAT || rule.adapter == AdapterId.ALIPAY
        if (chatApp && CaptureRules.CHAT_STRICT.matcher(compact).find()) {
            val receipt = CaptureRules.RECEIPT_MARK.matcher(compact).find()
            if (!receipt || countMarks(compact) != 1) {
                return reject(PageKind.CHAT, Reason.PAGE_CHAT)
            }
        }

        // 同一页出现多行"完成标记 + 金额"：这是消息列表/账单列表，不是单笔结果页
        if (countMarkedAmountRows(items) >= 2) {
            return reject(PageKind.BILL_LIST, Reason.MULTI_TRANSACTION)
        }

        val markIndex = items.indexOfFirst { completion.markRegex.matcher(CaptureText.normalize(it.text)).find() }
        val picked = pickAmount(items, rule, markIndex, completion.direction)
        val amount = picked.candidate
        if (amount == null || amount.value <= 0) {
            return PageAnalysis(completion.kind, null, Reason.NO_AMOUNT)
        }
        // 非强标签金额必须挨着完成标记；否则很可能是从另一行列表里抓来的数字
        if (amount.tier != AmountTier.STRONG_LABEL && !hasMarkNear(items, amount.index)) {
            return PageAnalysis(completion.kind, null, Reason.AMOUNT_NOT_ANCHORED)
        }

        val direction = completion.direction
        if (direction == Direction.UNKNOWN) {
            return PageAnalysis(completion.kind, null, Reason.NO_DIRECTION)
        }

        val allText = lines.joinToString("\n")
        val orderNo = CaptureText.extractOrderNo(allText)
        val merchant = CaptureText.merchantFromLines(lines)
        val channel = CaptureRules.inferChannel(rule.adapter, rule.label, allText)
        val occurredAt = CaptureText.parseOccurredAt(lines, obs.observedAt)
        val suppress = suppressReason(direction, options)
        val confidence = confidenceOf(amount.confidence, 1.0, direction, orderNo)
        val facts = Facts(
            pageKind = completion.kind,
            direction = direction,
            amount = amount.value,
            tier = amount.tier,
            amountConfidence = amount.confidence,
            needsReview = picked.needsReview || amount.confidence < 0.62,
            channel = channel,
            merchant = merchant,
            orderNo = orderNo,
            occurredAt = occurredAt,
            completionMark = completionMarkText(completion.mark, direction, compact),
            confidence = confidence,
            evidence = tendedEvidence(amount, lines, merchant),
            suppressReason = suppress
        )
        return PageAnalysis(completion.kind, facts, null)
    }

    fun parseNotification(
        rule: AppRule, obs: NotificationObservation, options: ParseOptions
    ): PageAnalysis {
        if (obs.groupSummary || obs.ongoing) {
            return reject(PageKind.UNKNOWN, Reason.PAGE_UNKNOWN)
        }
        val lines = ArrayList<String>()
        addLines(lines, obs.title)
        addLines(lines, obs.subText)
        addLines(lines, obs.text)
        addLines(lines, obs.bigText)
        for (line in obs.lines) addLines(lines, line)
        val compact = CaptureText.compact(lines)
        if (compact.isEmpty()) return reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK)

        if (CaptureRules.FAILURE.matcher(compact).find()) {
            return reject(PageKind.PAY_FAILURE, Reason.PAGE_FAILURE)
        }
        if (CaptureRules.PENDING.matcher(compact).find()) {
            return reject(PageKind.PAY_PENDING, Reason.PAGE_PENDING)
        }
        if (CaptureRules.NOTIFY_IGNORE.matcher(compact).find()) {
            if (CaptureRules.AD.matcher(compact).find()) return reject(PageKind.AD, Reason.PAGE_AD)
            if (CaptureRules.BILL_LIST.matcher(compact).find()) {
                return reject(PageKind.BILL_LIST, Reason.PAGE_LIST)
            }
            return reject(PageKind.UNKNOWN, Reason.PAGE_UNKNOWN)
        }
        if (CaptureRules.BILL_LIST.matcher(compact).find()) {
            return reject(PageKind.BILL_LIST, Reason.PAGE_LIST)
        }

        // 发送方/渠道白名单：微信好友聊天里的"我支付了100元"不能当成账单
        if (!notificationSenderAllowed(rule, obs)) {
            return reject(PageKind.CHAT, Reason.PAGE_CHAT)
        }

        var completion = completionOf(compact)
        if (completion.kind == null) {
            if (CaptureRules.AD.matcher(compact).find()) {
                return reject(PageKind.AD, Reason.PAGE_AD)
            }
            // 正文有"支付/付款"但发送方不是支付渠道的，按会话处理
            val hasWord = CaptureRules.EXPENSE_WORD.matcher(compact).find() ||
                CaptureRules.INCOME_WORD.matcher(compact).find()
            if (hasWord && rule.adapter == AdapterId.WECHAT) {
                return reject(PageKind.CHAT, Reason.PAGE_CHAT)
            }
            // 银行短信/支付通知常常没有"支付成功"字样，只有"消费/支出/到账"
            completion = wordCompletion(compact)
                ?: return reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK)
        }

        val kind = completion.kind
            ?: return reject(PageKind.UNKNOWN, Reason.NO_COMPLETION_MARK)
        val amount = pickAmountFromLines(lines, rule, completion.direction)
        if (amount == null || amount.value <= 0) {
            return PageAnalysis(kind, null, Reason.NO_AMOUNT)
        }
        val direction = completion.direction
        if (direction == Direction.UNKNOWN) {
            return PageAnalysis(kind, null, Reason.NO_DIRECTION)
        }
        val allText = lines.joinToString("\n")
        val orderNo = CaptureText.extractOrderNo(allText)
        val merchant = CaptureText.merchantFromNotification(
            obs.text, obs.subText, obs.bigText, obs.lines
        )
        val channel = CaptureRules.inferChannel(rule.adapter, rule.label, allText)
        val confidence = confidenceOf(amount.confidence, 0.95, direction, orderNo)
        val occurredAt = if (obs.postTime > 0) obs.postTime else System.currentTimeMillis()
        val facts = Facts(
            pageKind = kind,
            direction = direction,
            amount = amount.value,
            tier = amount.tier,
            amountConfidence = amount.confidence,
            needsReview = amount.confidence < 0.62,
            channel = channel,
            merchant = merchant,
            orderNo = orderNo,
            occurredAt = occurredAt,
            completionMark = completionMarkText(completion.mark, direction, compact),
            confidence = confidence,
            evidence = tendedEvidence(amount, lines, merchant),
            suppressReason = suppressReason(direction, options)
        )
        return PageAnalysis(kind, facts, null)
    }

    // ---------- 页面完成语义 ----------

    private data class Completion(
        val kind: PageKind?,
        val direction: Direction,
        val mark: String?,
        val markRegex: java.util.regex.Pattern
    )

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
            return Completion(PageKind.TRANSFER_SUCCESS, Direction.EXPENSE, mark,
                CaptureRules.TRANSFER_OUT_MARK)
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
            return Completion(PageKind.PAY_SUCCESS, Direction.EXPENSE, expense.group(),
                CaptureRules.EXPENSE_WORD)
        }
        val income = CaptureRules.INCOME_WORD.matcher(compact)
        if (income.find()) {
            return Completion(PageKind.RECEIVE_SUCCESS, Direction.INCOME, income.group(),
                CaptureRules.INCOME_WORD)
        }
        return null
    }

    /** 统计"一行完成标记 + 邻近金额"的行数；≥2 说明是列表，不是单笔结果页。 */
    private fun countMarkedAmountRows(items: List<UiText>): Int {
        var count = 0
        for (i in items.indices) {
            val text = CaptureText.normalize(items[i].text)
            if (!CaptureRules.ANY_COMPLETION_MARK.matcher(text).find()) continue
            val next = if (i + 1 < items.size) CaptureText.normalize(items[i + 1].text) else ""
            if (hasAmountLike(text) || hasAmountLike(next)) {
                count++
                if (count >= 2) return count
            }
        }
        return count
    }

    private fun hasAmountLike(text: String): Boolean {
        if (text.isEmpty()) return false
        if (CaptureText.isolatedAmount(text) > 0) return true
        return CaptureText.firstAmountIn(
            text, emptyList(), false, AmountTier.CURRENCY_CONTEXT, ""
        ) != null
    }

    private fun hasMarkNear(items: List<UiText>, index: Int): Boolean {
        if (index < 0) return false
        val start = maxOf(0, index - 12)
        val end = minOf(items.size - 1, index + 12)
        var i = start
        while (i <= end) {
            if (CaptureRules.ANY_COMPLETION_MARK.matcher(CaptureText.normalize(items[i].text)).find()) {
                return true
            }
            i++
        }
        return false
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

    // ---------- 金额 ----------

    private data class PickResult(
        val candidate: AmountCandidate?,
        val needsReview: Boolean
    )

    /** 退款/收款/还款页优先取对应金额，避免把原订单金额或实付金额当成这次变动额。 */
    private fun strongAnchorsFor(rule: AppRule, direction: Direction): List<String> {
        val prioritized = when (direction) {
            Direction.REFUND -> listOf("退款金额", "退回金额")
            Direction.INCOME -> listOf("收款金额", "到账金额", "转入金额", "存入金额")
            Direction.REPAYMENT -> listOf("还款金额")
            Direction.TRANSFER -> listOf("取现金额", "转出金额", "转入金额", "转账金额", "存入金额")
            else -> emptyList()
        }
        return (prioritized + CaptureRules.STRONG_ANCHORS + rule.amountAnchors).distinct()
    }

    private fun pickAmount(
        items: List<UiText>, rule: AppRule, markIndex: Int, direction: Direction
    ): PickResult {
        val excludes = CaptureRules.EXCLUDES + rule.amountExcludes
        val strong = strongAnchorsFor(rule, direction)
        val weak = CaptureRules.WEAK_ANCHORS

        // 1. 强标签：按锚点顺序，先命中的锚点优先（退款金额先于订单金额）
        for (anchor in strong) {
            val hits = ArrayList<AmountCandidate>()
            for (i in items.indices) {
                val line = CaptureText.normalize(items[i].text)
                val idx = line.indexOf(anchor)
                if (idx < 0) continue
                val after = line.substring(idx + anchor.length)
                val same = CaptureText.firstAmountIn(
                    after, excludes, true, AmountTier.STRONG_LABEL, anchor, i, items[i].sizePx
                )
                if (same != null) {
                    hits.add(same)
                    continue
                }
                val next = amountFromNextLines(items, i, excludes, true, AmountTier.STRONG_LABEL, anchor)
                if (next != null) hits.add(next)
            }
            if (hits.isNotEmpty()) return choose(hits)
        }

        // 2. 结果卡片里的大字号金额
        val region = regionOf(items, markIndex)
        val headline = pickHeadline(items, region, excludes)
        if (headline != null) return headline

        // 3. 弱标签：必须带货币单位或小数
        for (anchor in weak) {
            val hits = ArrayList<AmountCandidate>()
            for (i in items.indices) {
                val line = CaptureText.normalize(items[i].text)
                val idx = line.indexOf(anchor)
                if (idx < 0) continue
                if (CaptureText.containsExclude(line.substring(maxOf(0, idx - 3), idx), excludes)) continue
                val after = line.substring(idx + anchor.length)
                val same = CaptureText.firstAmountIn(
                    after, excludes, false, AmountTier.WEAK_LABEL, anchor, i, items[i].sizePx
                )
                if (same != null) {
                    hits.add(same)
                    continue
                }
                val next = amountFromNextLines(items, i, excludes, false, AmountTier.WEAK_LABEL, anchor)
                if (next != null) hits.add(next)
            }
            if (hits.isNotEmpty()) return choose(hits)
        }

        // 4. 只认完成标记附近卡片区域里的货币金额（不再全页兜底）
        for (i in region) {
            val item = items[i]
            if (previousLabelExcluded(items, i, excludes)) continue
            val c = CaptureText.firstAmountIn(
                item.text, excludes, false, AmountTier.CURRENCY_CONTEXT, "货币单位", i, item.sizePx
            )
            if (c != null) return choose(listOf(c))
        }
        return PickResult(null, false)
    }

    private fun pickAmountFromLines(
        lines: List<String>, rule: AppRule, direction: Direction
    ): AmountCandidate? {
        val excludes = CaptureRules.EXCLUDES + rule.amountExcludes
        val items = lines.map { UiText(it, sizePx = 30) }
        for (anchor in strongAnchorsFor(rule, direction)) {
            for (i in items.indices) {
                val line = CaptureText.normalize(items[i].text)
                val idx = line.indexOf(anchor)
                if (idx < 0) continue
                val after = line.substring(idx + anchor.length)
                val same = CaptureText.firstAmountIn(
                    after, excludes, true, AmountTier.STRONG_LABEL, anchor, i, 30
                )
                if (same != null) return same
                val next = amountFromNextLines(items, i, excludes, true, AmountTier.STRONG_LABEL, anchor)
                if (next != null) return next
            }
        }
        for (anchor in CaptureRules.WEAK_ANCHORS) {
            for (i in items.indices) {
                val line = CaptureText.normalize(items[i].text)
                val idx = line.indexOf(anchor)
                if (idx < 0) continue
                if (CaptureText.containsExclude(line.substring(maxOf(0, idx - 3), idx), excludes)) continue
                val after = line.substring(idx + anchor.length)
                val same = CaptureText.firstAmountIn(
                    after, excludes, false, AmountTier.WEAK_LABEL, anchor, i, 30
                )
                if (same != null) return same
            }
        }
        for (i in items.indices) {
            val line = items[i].text
            if (previousLabelExcluded(items, i, excludes)) continue
            val c = CaptureText.firstAmountIn(
                line, excludes, false, AmountTier.CURRENCY_CONTEXT, "货币单位", i, 30
            )
            if (c != null) return c
        }
        return null
    }

    private fun amountFromNextLines(
        items: List<UiText>, index: Int, excludes: List<String>, allowBareInteger: Boolean,
        tier: AmountTier, anchor: String
    ): AmountCandidate? {
        var looked = 0
        var j = index + 1
        while (j < items.size && looked < 2) {
            val next = CaptureText.normalize(items[j].text)
            if (next.isEmpty()) {
                j++
                continue
            }
            looked++
            if (previousLabelExcluded(items, j, excludes)) return null
            val c = CaptureText.firstAmountIn(
                next, excludes, allowBareInteger, tier, anchor, j, items[j].sizePx
            )
            if (c != null) return c
            if (next.any { it in '0'..'9' }) return null
            j++
        }
        return null
    }

    private fun pickHeadline(
        items: List<UiText>, region: List<Int>, excludes: List<String>
    ): PickResult? {
        if (region.isEmpty()) return null
        val sizes = region.map { items[it].sizePx }.filter { it > 0 }.sorted()
        if (sizes.isEmpty()) return null
        val median = sizes[sizes.size / 2]
        // 卡片内文本很少时中位数会被大字本身拉高，退回最小字号做基准
        val base = if (sizes.size < 4) sizes.first() else median
        val threshold = maxOf(1, (base * 1.5).toInt())
        val hits = ArrayList<AmountCandidate>()
        for (i in region) {
            val item = items[i]
            if (item.sizePx < threshold) continue
            if (previousLabelExcluded(items, i, excludes)) continue
            val line = CaptureText.normalize(item.text)
            if (CaptureText.containsExclude(line, excludes)) continue
            val value = CaptureText.isolatedAmount(line)
            if (value <= 0) continue
            val conf = 0.78 + 0.15 * minOf(1.0, item.sizePx.toDouble() / (median * 3.0))
            hits.add(
                AmountCandidate(
                    value = value,
                    tier = AmountTier.STRUCT_HEADLINE,
                    confidence = conf,
                    anchor = if (line.startsWith("¥") || line.startsWith("￥")) "¥" else "大字",
                    evidence = line,
                    sizePx = item.sizePx,
                    index = i
                )
            )
        }
        if (hits.isEmpty()) return null
        hits.sortWith(compareByDescending<AmountCandidate> { it.sizePx }.thenBy { it.index })
        return choose(hits)
    }

    private fun choose(hits: List<AmountCandidate>): PickResult {
        if (hits.isEmpty()) return PickResult(null, false)
        val best = hits.maxWithOrNull(
            compareBy<AmountCandidate> { it.confidence }
                .thenBy { it.sizePx }
                .thenByDescending { it.index }
        ) ?: hits[0]
        var review = false
        for (other in hits) {
            if (Math.abs(other.value - best.value) >= 0.01) {
                review = true
                break
            }
        }
        return PickResult(best, review)
    }

    private fun regionOf(items: List<UiText>, markIndex: Int): List<Int> {
        if (markIndex < 0) return items.indices.toList()
        val start = maxOf(0, markIndex - 18)
        val end = minOf(items.size - 1, markIndex + 18)
        val out = ArrayList<Int>()
        var i = start
        while (i <= end) {
            out.add(i)
            i++
        }
        return out
    }

    private fun previousLabelExcluded(items: List<UiText>, index: Int, excludes: List<String>): Boolean {
        var j = index - 1
        while (j >= 0) {
            val prev = CaptureText.normalize(items[j].text)
            j--
            if (prev.isEmpty()) continue
            if (prev.any { it in '0'..'9' }) return false
            return CaptureText.containsExclude(prev, excludes)
        }
        return false
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
