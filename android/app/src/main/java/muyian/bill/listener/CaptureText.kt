package muyian.bill.listener

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

/**
 * 纯文本工具：归一化、金额、单号、商户、页面时间。
 * 金额解析的每个入口都带排除词检查，绝不返回余额/额度/优惠类数字。
 */
object CaptureText {

    private val WHITESPACE = Pattern.compile("\\s+")
    private val CURRENCY_AMOUNT = Pattern.compile(
        "(?:(?:￥|¥|人民币|RMB|CNY)\\s*([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?|[0-9]+(?:\\.\\d{1,2})?)|" +
            "([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?|[0-9]+(?:\\.\\d{1,2})?)\\s*(?:元|块|圆))"
    )
    private val SYMBOL_AMOUNT_LINE = Pattern.compile("^[¥￥]\\s*([0-9]+(?:,[0-9]{3})*(?:\\.\\d{1,2})?)$")
    private val BARE_DECIMAL_LINE = Pattern.compile("^([0-9]+(?:,[0-9]{3})*\\.\\d{2})$")
    private val LEADING_AMOUNT = Pattern.compile("^[^0-9]{0,6}([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)")
    private val DATE_LINE = Pattern.compile("^(?:19|20)[0-9]{2}[.\\-/年][0-9]{1,2}")
    private val ORDER_NO = Pattern.compile(
        "(?:订单号|订单编号|交易单号|商户单号|交易流水号|流水号|凭证号|交易号|单号)[：:\\s]*([A-Za-z0-9][A-Za-z0-9 \\t\\-]{6,49})"
    )
    private val FULL_REDUCTION = Pattern.compile("满\\s*[0-9]+\\s*元?\\s*减")
    private val POST_DISCOUNT = Pattern.compile("(?:优惠|满减|立减|折扣|折)后")
    private val DATE_FULL = Pattern.compile("((?:19|20)[0-9]{2})\\s*[年./\\-]\\s*([0-9]{1,2})\\s*[月./\\-]\\s*([0-9]{1,2})\\s*日?")
    private val DATE_MONTH_DAY = Pattern.compile("([0-9]{1,2})\\s*月\\s*([0-9]{1,2})\\s*日")
    private val TIME_HM = Pattern.compile("([0-9]{1,2})\\s*[:：]\\s*([0-9]{2})")

    private val MERCHANT_EXCLUDE = Pattern.compile(
        "^[0-9.,，。:：+\\-¥￥元]+$|\\d{1,2}月\\d{1,2}日|\\d{1,2}:\\d{2}|订单|单号|凭证|账单|余额|" +
            "零钱|余额宝|零钱通|储蓄卡|信用卡|银行卡|花呗|白条|积分|支付宝|微信支付|^微信$|^支付宝$|" +
            "退款|红包|转账|自动扣|代扣|手续费|支付成功|付款成功|交易成功|收款成功"
    )
    private val BANK_SUFFIX = Pattern.compile("(银行|储蓄卡|信用卡)$")
    private val PAREN_MERCHANT = Pattern.compile("（([^（）]{2,30})）|\\(([^()]{2,30})\\)")

    fun normalize(raw: String): String {
        if (raw.isEmpty()) return ""
        val n = try {
            Normalizer.normalize(raw, Normalizer.Form.NFKC)
        } catch (_: Exception) {
            raw
        }
        return n.trim()
    }

    fun compact(raw: String): String = WHITESPACE.matcher(normalize(raw)).replaceAll("")

    fun compact(lines: List<String>): String {
        val sb = StringBuilder()
        for (line in lines) sb.append(normalize(line))
        return WHITESPACE.matcher(sb.toString()).replaceAll("")
    }

    fun toAmount(raw: String): Double = try {
        raw.replace(",", "").toDouble()
    } catch (_: NumberFormatException) {
        0.0
    }

    fun isSaneAmount(amount: Double): Boolean =
        !amount.isNaN() && !amount.isInfinite() && amount > 0 && amount < 100000000.0

    fun moneyKey(amount: Double): String = String.format(Locale.US, "%.2f", Math.abs(amount))

    fun containsExclude(text: String, excludes: List<String>): Boolean {
        if (text.isEmpty()) return false
        val normalized = POST_DISCOUNT.matcher(normalize(text)).replaceAll("")
        if (FULL_REDUCTION.matcher(normalized).find()) return true
        for (word in excludes) {
            if (normalized.contains(word)) return true
        }
        return false
    }

    /**
     * 金额紧邻前缀的排除词检查：只看金额前最近几个字符（压缩空白后 6 字），
     * 而不是整段前缀。整段判定会把"您尾号4321的卡交易¥100.00"整条拒掉，
     * 还会跳过真金额后取到同一行里的优惠金额（"已优惠2元，实付50元"取到 2）。
     * 排除词与金额的典型邻接距离 ≤4（手续费：/立减/余额），6 字够覆盖；
     * "尾号4321的卡交易"这类间隔 10+ 字符的标识信息不再误杀。
     */
    fun adjacentPrefixExcluded(source: String, amountStart: Int, excludes: List<String>): Boolean {
        if (amountStart <= 0) return false
        val prefix = source.substring(0, amountStart)
        val sb = StringBuilder(prefix.length)
        for (c in prefix) if (!c.isWhitespace()) sb.append(c)
        val text = sb.toString()
        if (text.isEmpty()) return false
        val window = if (text.length > 6) text.substring(text.length - 6) else text
        return containsExclude(window, excludes)
    }

    fun looksLikeDateOrTime(text: String, end: Int): Boolean {
        if (end >= text.length) return false
        val c = text[end]
        return c == ':' || c == '：' || c == '年' || c == '月' ||
            c == '-' || c == '/' || c == '.' || c == '减'
    }

    /**
     * 同一段文本里的第一个可信金额。
     * @param allowBareInteger 强锚点允许整数（银行短信"消费38元"），弱锚点必须带单位或小数。
     */
    fun firstAmountIn(
        text: String,
        excludes: List<String>,
        allowBareInteger: Boolean,
        tier: AmountTier,
        anchor: String,
        index: Int = 0,
        sizePx: Int = 0
    ): AmountCandidate? {
        val source = normalize(text)
        if (source.isEmpty()) return null
        val cm = CURRENCY_AMOUNT.matcher(source)
        while (cm.find()) {
            var value = ""
            for (i in 1..cm.groupCount()) {
                val g = cm.group(i)
                if (!g.isNullOrEmpty()) {
                    value = g
                    break
                }
            }
            val amount = toAmount(value)
            if (!isSaneAmount(amount)) continue
            if (looksLikeDateOrTime(source, cm.end())) continue
            if (adjacentPrefixExcluded(source, cm.start(), excludes)) continue
            val hasCurrency = !cm.group(1).isNullOrEmpty()
            return AmountCandidate(
                value = amount,
                tier = tier,
                confidence = baseConfidence(tier) * if (hasCurrency) 1.0 else 0.96,
                anchor = anchor,
                evidence = source,
                sizePx = sizePx,
                index = index
            )
        }
        val lm = LEADING_AMOUNT.matcher(source)
        if (lm.find()) {
            val raw = lm.group(1) ?: return null
            val amount = toAmount(raw)
            if (isSaneAmount(amount) && (allowBareInteger || raw.contains("."))) {
                if (!looksLikeDateOrTime(source, lm.end(1)) &&
                    !adjacentPrefixExcluded(source, lm.start(1), excludes) &&
                    !hasLeadingNumericNoise(source, lm.start(1))
                ) {
                    return AmountCandidate(
                        value = amount,
                        tier = tier,
                        confidence = baseConfidence(tier) * if (raw.contains(".")) 1.0 else 0.9,
                        anchor = anchor,
                        evidence = source,
                        sizePx = sizePx,
                        index = index
                    )
                }
            }
        }
        return null
    }

    /** 纯数字行（金额大字）：只接受 ¥xx 或 xx.xx，日期/编号/整数一律不认。 */
    fun isolatedAmount(text: String): Double {
        val source = normalize(text)
        if (source.isEmpty()) return 0.0
        val symbol = SYMBOL_AMOUNT_LINE.matcher(source)
        if (symbol.matches()) {
            val v = toAmount(symbol.group(1) ?: "")
            return if (isSaneAmount(v)) v else 0.0
        }
        if (DATE_LINE.matcher(source).find()) return 0.0
        val bare = BARE_DECIMAL_LINE.matcher(source)
        if (bare.matches()) {
            val v = toAmount(bare.group(1) ?: "")
            return if (isSaneAmount(v)) v else 0.0
        }
        return 0.0
    }

    fun hasCurrencyUnit(text: String): Boolean {
        if (text.contains('¥') || text.contains('￥') || text.contains("元") ||
            text.contains("块") || text.contains("人民币")
        ) return true
        return text.contains("RMB", ignoreCase = true) || text.contains("CNY", ignoreCase = true)
    }

    private fun baseConfidence(tier: AmountTier): Double = when (tier) {
        AmountTier.STRONG_LABEL -> 0.95
        AmountTier.STRUCT_HEADLINE -> 0.85
        AmountTier.WEAK_LABEL -> 0.72
        AmountTier.CURRENCY_CONTEXT -> 0.68
    }

    /** 序号开头的数字不是金额（如"1. 商品"），避免 LEADING_AMOUNT 把序号当金额。 */
    private fun hasLeadingNumericNoise(source: String, start: Int): Boolean {
        if (start <= 0) return false
        val prev = source[start - 1]
        return prev == '#' || prev == '第' || prev == '(' || prev == '（'
    }

    fun extractOrderNo(text: String): String {
        val m = ORDER_NO.matcher(normalize(text))
        while (m.find()) {
            val raw = (m.group(1) ?: "").trim()
                .replace(" ", "")
                .replace("\t", "")
                .replace("-", "")
            val normalized = raw.uppercase(Locale.US)
            if (normalized.length < 8 || normalized.length > 40) continue
            var digits = 0
            for (c in normalized) if (c in '0'..'9') digits++
            if (digits < 4) continue
            // 8 位纯数字若像 YYYYMMDD 日期则不算单号
            if (normalized.length == 8 &&
                (normalized.startsWith("19") || normalized.startsWith("20"))
            ) continue
            return normalized
        }
        return ""
    }

    fun isUsableMerchant(value: String): Boolean {
        val v = normalize(value)
        if (v.length < 2 || v.length > 30) return false
        if (MERCHANT_EXCLUDE.matcher(v).find()) return false
        if (BANK_SUFFIX.matcher(v).find()) return false
        return true
    }

    fun merchantFromLines(lines: List<String>): String {
        val labels = arrayOf(
            "收款方", "商户全称", "商户名称", "收款店铺", "对方账户",
            "付款给", "转账给", "收款人", "商户"
        )
        for (i in lines.indices) {
            val line = normalize(lines[i])
            for (label in labels) {
                val idx = line.indexOf(label)
                if (idx < 0) continue
                val value = line.substring(idx + label.length).trim().trimStart('：', ':').trim()
                if (isUsableMerchant(value)) return value
                if (value.isEmpty() && i + 1 < lines.size) {
                    val next = normalize(lines[i + 1])
                    if (isUsableMerchant(next)) return next
                }
            }
        }
        return ""
    }

    fun merchantFromNotification(
        text: String, subText: String, bigText: String, lines: List<String>
    ): String {
        val body = StringBuilder()
        body.append(text).append('\n').append(subText).append('\n').append(bigText)
        for (line in lines) body.append('\n').append(line)
        val source = normalize(body.toString())
        for (label in arrayOf("商户：", "商户:", "商户名称", "收款方", "付款给", "转账给", "收款店铺", "对方账户")) {
            val idx = source.indexOf(label)
            if (idx < 0) continue
            val rest = source.substring(idx + label.length).trim()
            val endOfLine = rest.indexOf('\n')
            val value = (if (endOfLine >= 0) rest.substring(0, endOfLine) else rest)
                .trim().trimStart('：', ':').trim()
            if (isUsableMerchant(value)) return value
        }
        val m = PAREN_MERCHANT.matcher(source)
        var last = ""
        while (m.find()) {
            val v = (m.group(1) ?: m.group(2) ?: "").trim()
            if (isUsableMerchant(v)) last = v
        }
        return last
    }

    /** 页面/通知里的时间优先，但只接受最近两天的日期，避免把历史订单时间当成这笔。 */
    fun parseOccurredAt(lines: List<String>, observedAt: Long): Long {
        val text = lines.joinToString("\n")
        var year = -1
        var month = -1
        var day = -1
        val full = DATE_FULL.matcher(text)
        if (full.find()) {
            year = full.group(1)?.toIntOrNull() ?: -1
            month = full.group(2)?.toIntOrNull() ?: -1
            day = full.group(3)?.toIntOrNull() ?: -1
        } else {
            val md = DATE_MONTH_DAY.matcher(text)
            if (md.find()) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = observedAt
                year = cal.get(Calendar.YEAR)
                month = md.group(1)?.toIntOrNull() ?: -1
                day = md.group(2)?.toIntOrNull() ?: -1
            }
        }
        if (year < 2000 || month !in 1..12 || day !in 1..31) return observedAt
        var hour = -1
        var minute = -1
        val tm = TIME_HM.matcher(text)
        if (tm.find()) {
            hour = tm.group(1)?.toIntOrNull() ?: -1
            minute = tm.group(2)?.toIntOrNull() ?: -1
        }
        val cal = Calendar.getInstance()
        cal.timeInMillis = observedAt
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        if (hour in 0..23 && minute in 0..59) {
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
        }
        val at = cal.timeInMillis
        if (at < observedAt - 2L * 24 * 3600 * 1000L || at > observedAt + 2L * 3600 * 1000L) {
            return observedAt
        }
        return at
    }

    /** FNV-1a：跨进程稳定的短哈希，用于页面/捕获指纹。 */
    fun hash(input: String): String {
        var h = 1469598103934665603L
        for (b in input.toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xff)
            h *= 1099511628211L
        }
        return java.lang.Long.toHexString(h)
    }

    /** 商户名归一化：只用于本地学习计数，不影响展示。 */
    fun merchantKey(merchant: String): String {
        val n = normalize(merchant)
        val sb = StringBuilder()
        for (c in n) {
            if (c.isLetterOrDigit() || c.code in 0x4e00..0x9fff) sb.append(c)
        }
        return sb.toString().lowercase(Locale.US)
    }
}
