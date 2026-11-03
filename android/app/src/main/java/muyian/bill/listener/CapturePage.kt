package muyian.bill.listener

/**
 * 解析期的一行文本：文本已归一化，几何字段保留给"大字金额/卡片区域"判断。
 * 采集层仍是 UiText；进入解析器后只消费 PageDoc，避免每个锚点都重新 normalize。
 */
data class DocLine(
    val index: Int,
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val sizePx: Int,
    val className: String,
    val viewId: String,
    val depth: Int
)

/**
 * 页面文档：一次归一化，分类、金额、商户、单号共用同一份行数据。
 * flowText 行内去空白、行间留空格，既防跨行拼词，又保留跨节点标记的窗口匹配。
 */
class PageDoc private constructor(
    val pkg: String,
    val observedAt: Long,
    val lines: List<DocLine>
) {
    val flowText: String = CaptureText.compactNormalized(lines.map { it.text })
    val indices: List<Int> = lines.indices.toList()

    val isEmpty: Boolean
        get() = lines.isEmpty()

    fun text(index: Int): String = lines.getOrNull(index)?.text ?: ""

    fun line(index: Int): DocLine? = lines.getOrNull(index)

    fun regionAround(index: Int, radius: Int): List<Int> {
        if (lines.isEmpty()) return emptyList()
        if (index < 0) return indices
        val start = maxOf(0, index - radius)
        val end = minOf(lines.size - 1, index + radius)
        if (start > end) return emptyList()
        return (start..end).toList()
    }

    fun texts(): List<String> = lines.map { it.text }

    companion object {
        fun of(pkg: String, items: List<UiText>, observedAt: Long): PageDoc {
            val sorted = items
                .filter { it.text.isNotBlank() }
                .sortedWith(compareBy<UiText> { it.top }.thenBy { it.left })
            val lines = ArrayList<DocLine>(sorted.size)
            for (item in sorted) {
                val text = CaptureText.normalize(item.text)
                if (text.isBlank()) continue
                lines.add(
                    DocLine(
                        index = lines.size,
                        text = text,
                        left = item.left,
                        top = item.top,
                        right = item.right,
                        bottom = item.bottom,
                        sizePx = item.sizePx,
                        className = item.className,
                        viewId = item.viewId,
                        depth = item.depth
                    )
                )
            }
            return PageDoc(pkg, observedAt, lines)
        }

        /** 通知/纯文本没有几何信息：按顺序合成行，字号统一，仅供锚点/单位逻辑使用。 */
        fun ofLines(pkg: String, rawLines: List<String>, observedAt: Long): PageDoc {
            val lines = ArrayList<DocLine>(rawLines.size)
            for ((i, raw) in rawLines.withIndex()) {
                val text = CaptureText.normalize(raw)
                if (text.isBlank()) continue
                lines.add(
                    DocLine(
                        index = lines.size,
                        text = text,
                        left = 0,
                        top = i * 60,
                        right = 0,
                        bottom = i * 60 + 48,
                        sizePx = 30,
                        className = "",
                        viewId = "",
                        depth = 0
                    )
                )
            }
            return PageDoc(pkg, observedAt, lines)
        }
    }
}
