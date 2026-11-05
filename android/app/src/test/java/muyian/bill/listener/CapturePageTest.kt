package muyian.bill.listener

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** PageDoc：一次归一化、行边界、几何排序与区域裁剪。 */
class CapturePageTest {

    private fun item(
        text: String,
        top: Int = 0,
        left: Int = 0,
        size: Int = 30
    ) = UiText(text, left = left, top = top, bottom = top + size, sizePx = size)

    @Test
    fun normalizeOnceAndKeepLineBoundary() {
        val doc = PageDoc.of("pkg", listOf(item("支付"), item("成功", top = 60)), 1L)
        assertEquals(listOf("支付", "成功"), doc.texts())
        assertEquals("支付 成功", doc.flowText)
    }

    @Test
    fun sortsByTopThenLeftAndDropsBlankLines() {
        val doc = PageDoc.of(
            "pkg",
            listOf(
                item("右", top = 100, left = 200),
                item("  ", top = 0),
                item("左", top = 100, left = 10),
                item("上", top = 10)
            ),
            1L
        )
        assertEquals(listOf("上", "左", "右"), doc.texts())
        assertFalse(doc.isEmpty)
    }

    @Test
    fun regionAroundClampsToDocument() {
        val doc = PageDoc.of(
            "pkg",
            (0..4).map { item("L$it", top = it * 60) },
            1L
        )
        assertEquals(listOf(0, 1, 2), doc.regionAround(0, 2))
        assertEquals(listOf(0, 1, 2, 3, 4), doc.regionAround(-1, 2))
        assertEquals(listOf(2, 3, 4), doc.regionAround(4, 2))
        assertTrue(doc.regionAround(2, 0).contains(2))
    }

    @Test
    fun ofLinesSynthesizesSequentialGeometry() {
        val doc = PageDoc.ofLines("pkg", listOf("支付成功", "¥38.50", "  "), 9L)
        assertEquals(listOf("支付成功", "¥38.50"), doc.texts())
        assertEquals(0, doc.line(0)?.top)
        assertEquals(60, doc.line(1)?.top)
        assertEquals(30, doc.line(1)?.sizePx)
    }
}
