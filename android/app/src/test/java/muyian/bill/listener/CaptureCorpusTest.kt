package muyian.bill.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 语料回放：真实页面/通知的缩影放在 `resources/capture/corpus.tsv`，
 * 每次改规则先跑这里，避免只盯着单条正则改。
 */
class CaptureCorpusTest {

    private val now = 1_761_550_000_000L
    private val options = ParseOptions(recordTransfers = false)

    private fun rule(pkg: String): AppRule =
        CaptureRules.builtinRule(pkg) ?: error("no rule for $pkg")

    @Test
    fun corpusReplaysToExpectedDecisions() {
        val stream = javaClass.getResourceAsStream("/capture/corpus.tsv")
            ?: error("missing corpus.tsv")
        val rows = stream.bufferedReader(Charsets.UTF_8).readLines()
        var checked = 0
        for (raw in rows) {
            if (raw.isBlank() || raw.startsWith("#")) continue
            val parts = raw.split('\t')
            require(parts.size >= 7) { "bad corpus row: $raw" }
            val name = parts[0]
            val pkg = parts[1]
            val source = parts[2]
            val expectKind = PageKind.valueOf(parts[3])
            val expectAmount = parts[4].toDouble()
            val expectDirection = Direction.valueOf(parts[5])
            val bodies = parts.subList(6, parts.size).joinToString("\t").split('|')

            val analysis = if (source == "notify") {
                CaptureParser.parseNotification(
                    rule(pkg),
                    NotificationObservation(
                        pkg = pkg,
                        title = bodies.firstOrNull() ?: "",
                        text = bodies.drop(1).joinToString("\n"),
                        postTime = now
                    ),
                    options
                )
            } else {
                val items = bodies.mapIndexed { i, text ->
                    UiText(text, top = i * 60, bottom = i * 60 + 48, sizePx = 30)
                }
                CaptureParser.parseScreen(
                    rule(pkg),
                    ScreenObservation(pkg = pkg, items = items, observedAt = now),
                    options
                )
            }

            assertEquals("$name kind", expectKind, analysis.kind)
            if (expectAmount > 0) {
                assertNotNull("$name facts", analysis.facts)
                assertEquals("$name amount", expectAmount, analysis.facts!!.amount, 0.001)
                assertEquals("$name direction", expectDirection, analysis.facts!!.direction)
            } else {
                assertNull("$name facts", analysis.facts)
            }
            checked++
        }
        assertEquals("corpus size", 11, checked)
    }
}
