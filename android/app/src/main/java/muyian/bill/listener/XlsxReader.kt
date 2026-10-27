package muyian.bill.listener

import android.util.Xml
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.ArrayList
import java.util.HashMap
import org.xmlpull.v1.XmlPullParser
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** xlsx 读取：只把 sharedStrings 与第一个 sheet 解压成「行号 -> 单元格文本」，不做账单语义。 */
internal object XlsxReader {
    fun read(file: File): ArrayList<HashMap<Int, String>> {
        val shared = readSharedStringsFromFile(file)
        return readFirstSheetFromFile(file, shared)
    }

fun cell(row: HashMap<Int, String>, col: Int): String {
    if (col < 0) return ""
    return row.get(col) ?: ""
}

/** "A1"/"AB12" 里的字母部分转 0 基列号 */
private fun colIndexOf(ref: String?): Int {
    if (ref == null || ref.isEmpty()) return 0
    var n = 0
    var i = 0
    while (i < ref.length) {
        val c = ref.get(i)
        if (c < 'A' || c > 'Z') break
        n = n * 26 + (c.code - 64)
        i = i + 1
    }
    return n - 1
}

private fun openZipAt(file: File): ZipInputStream {
    return ZipInputStream(FileInputStream(file))
}

private fun readSharedStringsFromFile(file: File): ArrayList<String> {
    val out = ArrayList<String>()
    val zis = openZipAt(file)
    try {
        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            if (entry.name == "xl/sharedStrings.xml") {
                out.addAll(readSharedStrings(zis))
                break
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    } finally {
        zis.close()
    }
    return out
}

private fun readFirstSheetFromFile(file: File, shared: ArrayList<String>): ArrayList<HashMap<Int, String>> {
    val zis = openZipAt(file)
    try {
        var entry: ZipEntry? = zis.nextEntry
        while (entry != null) {
            val name = entry.name ?: ""
            if (name.startsWith("xl/worksheets/") && name.endsWith(".xml")) {
                return readSheetRows(zis, shared)
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    } finally {
        zis.close()
    }
    return ArrayList()
}

/** sharedStrings.xml：把每个 <si> 的所有 <t> 文本拼成一个字符串 */
private fun readSharedStrings(input: InputStream): ArrayList<String> {
    val out = ArrayList<String>()
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(input, null)
    var inSi = false
    var inT = false
    val buf = StringBuilder()
    var ev = parser.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        if (ev == XmlPullParser.START_TAG) {
            val n = parser.name
            if (n == "si") {
                inSi = true
                buf.setLength(0)
            } else if (n == "t") {
                inT = true
            }
        } else if (ev == XmlPullParser.TEXT) {
            if (inSi && inT) buf.append(parser.text)
        } else if (ev == XmlPullParser.END_TAG) {
            val n = parser.name
            if (n == "t") {
                inT = false
            } else if (n == "si") {
                inSi = false
                out.add(buf.toString())
            }
        }
        ev = parser.next()
    }
    return out
}

/**
 * sheet XML：按 <row> 聚出 {列号: 文本}。t="s" 是 sharedStrings 下标，
 * t="inlineStr" 的文本在 <is><t> 里，其余原样取 <v>。
 */
private fun readSheetRows(input: InputStream, shared: ArrayList<String>): ArrayList<HashMap<Int, String>> {
    val rows = ArrayList<HashMap<Int, String>>()
    val parser = Xml.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    parser.setInput(input, null)
    var cur: HashMap<Int, String>? = null
    var col = 0
    var cellType = ""
    var inV = false
    var inT = false
    val buf = StringBuilder()
    var ev = parser.eventType
    while (ev != XmlPullParser.END_DOCUMENT) {
        if (ev == XmlPullParser.START_TAG) {
            val n = parser.name
            if (n == "row") {
                cur = HashMap()
            } else if (n == "c" && cur != null) {
                col = colIndexOf(parser.getAttributeValue(null, "r"))
                cellType = parser.getAttributeValue(null, "t") ?: ""
                buf.setLength(0)
                inV = false
                inT = false
            } else if (n == "v") {
                inV = true
            } else if (n == "t") {
                inT = true
            }
        } else if (ev == XmlPullParser.TEXT) {
            if (inV || inT) buf.append(parser.text)
        } else if (ev == XmlPullParser.END_TAG) {
            val n = parser.name
            if (n == "v") {
                inV = false
            } else if (n == "t") {
                inT = false
            } else if (n == "c" && cur != null) {
                var v = buf.toString()
                if (cellType == "s") {
                    val idx = v.toIntOrNull() ?: -1
                    v = if (idx >= 0 && idx < shared.size) shared.get(idx) else ""
                }
                cur.put(col, v)
            } else if (n == "row" && cur != null) {
                rows.add(cur)
                cur = null
            }
        }
        ev = parser.next()
    }
    return rows
}
}
