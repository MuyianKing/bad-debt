package muyian.bill.listener

/**
 * 金额抽取的唯一入口：屏幕、通知、收银台共享同一套候选生成与择取。
 * 屏幕有几何（大字/完成标记区域），通知只有行序，收银台额外允许"大字金额"作为支付意图。
 * 输入是已归一化的 PageDoc，候选里的 index 始终指向 PageDoc.lines。
 */
object AmountExtractor {

    data class Pick(val candidate: AmountCandidate?, val needsReview: Boolean)

    fun pickScreen(
        doc: PageDoc,
        rule: AppRule,
        markIndex: Int,
        direction: Direction,
        allowCurrencyContext: Boolean
    ): Pick {
        val excludes = CaptureRules.EXCLUDES + rule.amountExcludes

        // 1. 强标签：先看方向专属锚点（退款金额/到账金额等），再看通用锚点。
        // 同一层内收集所有锚点的候选，按"锚点优先级 + 离完成标记的距离"打分，
        // 不再由 STRONG_ANCHORS 的书写顺序直接决定。
        val prioritized = directionAnchors(direction)
        val prioritizedHits = collectStrong(doc, prioritized, excludes, markIndex)
        if (prioritizedHits.isNotEmpty()) return chooseScored(prioritizedHits)
        val generalHits = collectStrong(doc, generalAnchors(rule, direction), excludes, markIndex)
        if (generalHits.isNotEmpty()) return chooseScored(generalHits)

        // 2. 结果卡片里的大字号金额
        val region = doc.regionAround(markIndex, 18)
        val headline = pickHeadline(doc, region, excludes)
        if (headline != null) return headline

        // 3. 弱标签：必须带货币单位或小数
        for (anchor in CaptureRules.WEAK_ANCHORS) {
            val hits = ArrayList<AmountCandidate>()
            for (i in doc.indices) {
                val line = doc.text(i)
                val idx = line.indexOf(anchor)
                if (idx < 0) continue
                if (CaptureText.containsExclude(
                        line.substring(maxOf(0, idx - 3), idx),
                        excludes,
                        alreadyNormalized = true
                    )
                ) continue
                val after = line.substring(idx + anchor.length)
                val same = CaptureText.firstAmountIn(
                    after, excludes, false, AmountTier.WEAK_LABEL, anchor, i,
                    doc.line(i)?.sizePx ?: 0, alreadyNormalized = true
                )
                if (same != null) {
                    hits.add(same)
                    continue
                }
                val next = amountFromNextLines(doc, i, excludes, false, AmountTier.WEAK_LABEL, anchor)
                if (next != null) hits.add(next)
            }
            if (hits.isNotEmpty()) return choose(hits)
        }

        // 4. 只认完成标记附近卡片区域里的货币金额；宿主结果页禁用这一层
        if (!allowCurrencyContext) return Pick(null, false)
        for (i in region) {
            if (previousLabelExcluded(doc, i, excludes)) continue
            val c = CaptureText.firstAmountIn(
                doc.text(i), excludes, false, AmountTier.CURRENCY_CONTEXT, "货币单位", i,
                doc.line(i)?.sizePx ?: 0, alreadyNormalized = true
            )
            if (c != null) return choose(listOf(c))
        }
        return Pick(null, false)
    }

    fun pickNotification(
        doc: PageDoc,
        rule: AppRule,
        direction: Direction
    ): Pick {
        val excludes = CaptureRules.EXCLUDES + rule.amountExcludes

        val prioritizedHits = collectStrong(doc, directionAnchors(direction), excludes, -1)
        if (prioritizedHits.isNotEmpty()) return chooseScored(prioritizedHits)
        val generalHits = collectStrong(doc, generalAnchors(rule, direction), excludes, -1)
        if (generalHits.isNotEmpty()) return chooseScored(generalHits)

        for (anchor in CaptureRules.WEAK_ANCHORS) {
            for (i in doc.indices) {
                val line = doc.text(i)
                val idx = line.indexOf(anchor)
                if (idx < 0) continue
                if (CaptureText.containsExclude(
                        line.substring(maxOf(0, idx - 3), idx),
                        excludes,
                        alreadyNormalized = true
                    )
                ) continue
                val after = line.substring(idx + anchor.length)
                val same = CaptureText.firstAmountIn(
                    after, excludes, false, AmountTier.WEAK_LABEL, anchor, i, 30,
                    alreadyNormalized = true
                )
                if (same != null) return Pick(same, false)
            }
        }

        for (i in doc.indices) {
            if (previousLabelExcluded(doc, i, excludes)) continue
            val c = CaptureText.firstAmountIn(
                doc.text(i), excludes, false, AmountTier.CURRENCY_CONTEXT, "货币单位", i, 30,
                alreadyNormalized = true
            )
            if (c != null) return Pick(c, false)
        }
        return Pick(null, false)
    }

    fun pickCashier(doc: PageDoc, rule: AppRule): AmountCandidate? {
        if (doc.isEmpty) return null
        val excludes = CaptureRules.EXCLUDES + rule.amountExcludes
        val anchors = (
            listOf(
                "实付款", "实付金额", "应付金额", "支付金额", "付款金额",
                "合计", "总计", "确认购买", "交易金额", "订单金额"
            ) + rule.amountAnchors
            ).distinct()

        for (anchor in anchors) {
            for (i in doc.indices) {
                val line = doc.text(i)
                val idx = line.indexOf(anchor)
                if (idx < 0) continue
                val after = line.substring(idx + anchor.length)
                val same = CaptureText.firstAmountIn(
                    after, excludes, true, AmountTier.STRONG_LABEL, anchor, i,
                    doc.line(i)?.sizePx ?: 0, alreadyNormalized = true
                )
                val candidate = same ?: amountFromNextLines(
                    doc, i, excludes, true, AmountTier.STRONG_LABEL, anchor
                )
                if (candidate == null || !CaptureText.isSaneAmount(candidate.value)) continue
                // 收银台强锚点是"支付意图"而非成功页金额，置信度沿用原来的 0.9
                return candidate.copy(confidence = 0.9)
            }
        }

        // 没有"支付金额/合计"标签的收银台：取页面里字号最大的独立金额
        val headline = pickHeadline(doc, doc.indices, excludes)?.candidate
        if (headline != null && CaptureText.isSaneAmount(headline.value)) return headline
        // 大字带"元"后缀（微信 LiteApp content-desc：10.90元）
        val headlineCurrency = cashierHeadlineCurrency(doc, excludes)
        if (headlineCurrency != null) return headlineCurrency
        // 最后兜底：收银台页面里第一个未被排除的货币金额
        for (i in doc.indices) {
            if (previousLabelExcluded(doc, i, excludes)) continue
            val candidate = CaptureText.firstAmountIn(
                doc.text(i), excludes, false, AmountTier.CURRENCY_CONTEXT, "收银台金额", i,
                doc.line(i)?.sizePx ?: 0, alreadyNormalized = true
            )
            if (candidate != null && CaptureText.isSaneAmount(candidate.value)) return candidate
        }
        return null
    }

    /** 供"同一页是否出现多笔交易"的结构判断使用；返回行内/下一行可读到的金额。 */
    fun amountLike(text: String): Double? {
        if (text.isEmpty()) return null
        val isolated = CaptureText.isolatedAmount(text, alreadyNormalized = true)
        if (isolated > 0) return isolated
        return CaptureText.firstAmountIn(
            text, emptyList(), false, AmountTier.CURRENCY_CONTEXT, "",
            alreadyNormalized = true
        )?.value
    }

    fun looksLikeAmount(text: String): Boolean = amountLike(text) != null

    // ---------- 内部 ----------

    private data class Scored(val candidate: AmountCandidate, val score: Double)

    /** 退款/收款/还款页优先取对应金额，避免把原订单金额或实付金额当成这次变动额。 */
    private fun directionAnchors(direction: Direction): List<String> = when (direction) {
        Direction.REFUND -> listOf("退款金额", "退回金额")
        Direction.INCOME -> listOf("收款金额", "到账金额", "转入金额", "存入金额")
        Direction.REPAYMENT -> listOf("还款金额")
        Direction.TRANSFER -> listOf("取现金额", "转出金额", "转入金额", "转账金额", "存入金额")
        else -> emptyList()
    }

    private fun generalAnchors(rule: AppRule, direction: Direction): List<String> {
        val prioritized = directionAnchors(direction)
        return (CaptureRules.STRONG_ANCHORS + rule.amountAnchors)
            .distinct()
            .filterNot { it in prioritized }
    }

    private fun collectStrong(
        doc: PageDoc,
        anchors: List<String>,
        excludes: List<String>,
        markIndex: Int
    ): List<Scored> {
        val out = ArrayList<Scored>()
        for ((priority, anchor) in anchors.withIndex()) {
            for (i in doc.indices) {
                val line = doc.text(i)
                val idx = line.indexOf(anchor)
                if (idx < 0) continue
                val after = line.substring(idx + anchor.length)
                val same = CaptureText.firstAmountIn(
                    after, excludes, true, AmountTier.STRONG_LABEL, anchor, i,
                    doc.line(i)?.sizePx ?: 0, alreadyNormalized = true
                )
                val candidate = same ?: amountFromNextLines(
                    doc, i, excludes, true, AmountTier.STRONG_LABEL, anchor
                ) ?: continue
                out.add(Scored(candidate, scoreOf(candidate, priority, markIndex)))
            }
        }
        return out
    }

    /**
     * 锚点书写顺序只做小幅加分；离完成标记越近加分越多。
     * 这样"页面顶部营销实付"不会压过"完成标记旁边的真实支付金额"。
     */
    private fun scoreOf(candidate: AmountCandidate, anchorPriority: Int, markIndex: Int): Double {
        val priorityBonus = maxOf(0.0, 0.02 - anchorPriority * 0.001)
        val proximityBonus = if (markIndex < 0) {
            0.0
        } else {
            maxOf(0.0, 0.04 - Math.abs(candidate.index - markIndex) * 0.002)
        }
        return candidate.confidence + priorityBonus + proximityBonus
    }

    private fun chooseScored(hits: List<Scored>): Pick {
        if (hits.isEmpty()) return Pick(null, false)
        val best = hits.maxWithOrNull(
            compareBy<Scored> { it.score }
                .thenBy { it.candidate.confidence }
                .thenBy { it.candidate.sizePx }
                .thenByDescending { it.candidate.index }
        ) ?: hits[0]
        val review = hits.any {
            Math.abs(it.candidate.value - best.candidate.value) >= 0.01
        }
        return Pick(best.candidate, review)
    }

    private fun amountFromNextLines(
        doc: PageDoc,
        index: Int,
        excludes: List<String>,
        allowBareInteger: Boolean,
        tier: AmountTier,
        anchor: String
    ): AmountCandidate? {
        var looked = 0
        var j = index + 1
        while (j < doc.lines.size && looked < 2) {
            val next = doc.text(j)
            if (next.isEmpty()) {
                j++
                continue
            }
            looked++
            if (previousLabelExcluded(doc, j, excludes)) return null
            val c = CaptureText.firstAmountIn(
                next, excludes, allowBareInteger, tier, anchor, j,
                doc.line(j)?.sizePx ?: 0, alreadyNormalized = true
            )
            if (c != null) return c
            if (next.any { it in '0'..'9' }) return null
            j++
        }
        return null
    }

    private fun pickHeadline(
        doc: PageDoc,
        region: List<Int>,
        excludes: List<String>
    ): Pick? {
        if (region.isEmpty()) return null
        val sizes = region.mapNotNull { doc.line(it)?.sizePx?.takeIf { s -> s > 0 } }.sorted()
        if (sizes.isEmpty()) return null
        val median = sizes[sizes.size / 2]
        // 卡片内文本很少时中位数会被大字本身拉高，退回最小字号做基准
        val base = if (sizes.size < 4) sizes.first() else median
        val threshold = maxOf(1, (base * 1.5).toInt())
        val hits = ArrayList<AmountCandidate>()
        for (i in region) {
            val item = doc.line(i) ?: continue
            if (item.sizePx < threshold) continue
            if (previousLabelExcluded(doc, i, excludes)) continue
            val line = item.text
            if (CaptureText.containsExclude(line, excludes, alreadyNormalized = true)) continue
            val value = CaptureText.isolatedAmount(line, alreadyNormalized = true)
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

    private fun cashierHeadlineCurrency(
        doc: PageDoc,
        excludes: List<String>
    ): AmountCandidate? {
        val sizes = doc.lines.map { it.sizePx }.filter { it > 0 }.sorted()
        if (sizes.isEmpty()) return null
        val median = sizes[sizes.size / 2]
        val base = if (sizes.size < 4) sizes.first() else median
        val threshold = maxOf(1, (base * 1.5).toInt())
        var best: AmountCandidate? = null
        for (i in doc.indices) {
            val item = doc.line(i) ?: continue
            if (item.sizePx < threshold) continue
            if (previousLabelExcluded(doc, i, excludes)) continue
            val candidate = CaptureText.firstAmountIn(
                item.text, excludes, false, AmountTier.CURRENCY_CONTEXT, "收银台大字", i,
                item.sizePx, alreadyNormalized = true
            ) ?: continue
            val current = best
            if (current == null || candidate.sizePx > current.sizePx) best = candidate
        }
        return best
    }

    private fun choose(hits: List<AmountCandidate>): Pick {
        if (hits.isEmpty()) return Pick(null, false)
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
        return Pick(best, review)
    }

    private fun previousLabelExcluded(doc: PageDoc, index: Int, excludes: List<String>): Boolean {
        var j = index - 1
        while (j >= 0) {
            val prev = doc.text(j)
            j--
            if (prev.isEmpty()) continue
            if (prev.any { it in '0'..'9' }) return false
            return CaptureText.containsExclude(prev, excludes, alreadyNormalized = true)
        }
        return false
    }
}
