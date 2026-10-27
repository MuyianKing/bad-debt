package com.muyian.debt.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 线性/实体图标。路径数据是 24x24 viewBox 的 SVG 子集，
 * 这里负责解析 + Compose Canvas 绘制。
 */
object IconParser {
    private val strokeCache = HashMap<String, Path>()
    private val filledCache = HashMap<String, Path>()

    fun path(name: String, solid: Boolean): Path {
        val cache = if (solid) filledCache else strokeCache
        cache[name]?.let { return it }
        val body = if (solid) ICON_FILLED_PATHS[name] ?: ICON_PATHS[name] else ICON_PATHS[name]
        val path = Path().apply {
            if (solid) fillType = PathFillType.EvenOdd
            if (body != null) parse(body, this)
        }
        cache[name] = path
        return path
    }

    private fun parse(body: String, path: Path) {
        val tagRe = Regex("<(line|circle|rect|polyline|polygon|path)\\b([^>]*)/?>")
        for (m in tagRe.findAll(body)) {
            val tag = m.groupValues[1]
            val attrs = HashMap<String, String>()
            Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"").findAll(m.groupValues[2]).forEach {
                attrs[it.groupValues[1]] = it.groupValues[2]
            }
            val f = { key: String -> attrs[key]?.toFloatOrNull() ?: 0f }
            when (tag) {
                "line" -> {
                    path.moveTo(f("x1"), f("y1"))
                    path.lineTo(f("x2"), f("y2"))
                }
                "circle" -> {
                    val cx = f("cx"); val cy = f("cy"); val r = f("r")
                    path.addOval(Rect(cx - r, cy - r, cx + r, cy + r))
                }
                "rect" -> {
                    val x = f("x"); val y = f("y"); val w = f("width"); val h = f("height")
                    val rx = attrs["rx"]?.toFloatOrNull() ?: 0f
                    if (rx > 0f) {
                        path.addRoundRect(RoundRect(x, y, x + w, y + h, rx, rx))
                    } else {
                        path.addRect(Rect(x, y, x + w, y + h))
                    }
                }
                "polyline", "polygon" -> {
                    val nums = (attrs["points"] ?: "").trim().split(Regex("[\\s,]+"))
                        .mapNotNull { it.toFloatOrNull() }
                    if (nums.size >= 4) {
                        path.moveTo(nums[0], nums[1])
                        var i = 2
                        while (i + 1 < nums.size) {
                            path.lineTo(nums[i], nums[i + 1]); i += 2
                        }
                        if (tag == "polygon") path.close()
                    }
                }
                "path" -> parsePathData(attrs["d"] ?: "", path)
            }
        }
    }

    private fun parsePathData(d: String, path: Path) {
        val tokens = Regex("[MmLlHhVvCcSsQqTtAaZz]|-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?").findAll(d)
            .map { it.value }.toList()
        var i = 0
        var cmd = ' '
        var cx = 0f; var cy = 0f
        var sx = 0f; var sy = 0f
        var lastCtrlX = 0f; var lastCtrlY = 0f
        var lastCmd = ' '
        fun num(): Float = tokens[i++].toFloat()
        fun hasNum(): Boolean = i < tokens.size && tokens[i][0].let { it.isDigit() || it == '-' || it == '.' }
        while (i < tokens.size) {
            if (tokens[i].length == 1 && tokens[i][0].isLetter()) {
                cmd = tokens[i][0]; i++
            }
            val rel = cmd.isLowerCase()
            when (cmd.uppercaseChar()) {
                'M' -> {
                    var x = num(); var y = num()
                    if (rel) { x += cx; y += cy }
                    path.moveTo(x, y); cx = x; cy = y; sx = x; sy = y
                    cmd = if (rel) 'l' else 'L'
                }
                'L' -> {
                    var x = num(); var y = num()
                    if (rel) { x += cx; y += cy }
                    path.lineTo(x, y); cx = x; cy = y
                }
                'H' -> {
                    var x = num(); if (rel) x += cx
                    path.lineTo(x, cy); cx = x
                }
                'V' -> {
                    var y = num(); if (rel) y += cy
                    path.lineTo(cx, y); cy = y
                }
                'C' -> {
                    var x1 = num(); var y1 = num(); var x2 = num(); var y2 = num(); var x = num(); var y = num()
                    if (rel) { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy }
                    path.cubicTo(x1, y1, x2, y2, x, y)
                    lastCtrlX = x2; lastCtrlY = y2; cx = x; cy = y
                }
                'S' -> {
                    var x2 = num(); var y2 = num(); var x = num(); var y = num()
                    if (rel) { x2 += cx; y2 += cy; x += cx; y += cy }
                    val x1 = if (lastCmd in "CcSs") cx * 2 - lastCtrlX else cx
                    val y1 = if (lastCmd in "CcSs") cy * 2 - lastCtrlY else cy
                    path.cubicTo(x1, y1, x2, y2, x, y)
                    lastCtrlX = x2; lastCtrlY = y2; cx = x; cy = y
                }
                'Q' -> {
                    var x1 = num(); var y1 = num(); var x = num(); var y = num()
                    if (rel) { x1 += cx; y1 += cy; x += cx; y += cy }
                    path.quadraticBezierTo(x1, y1, x, y)
                    lastCtrlX = x1; lastCtrlY = y1; cx = x; cy = y
                }
                'T' -> {
                    var x = num(); var y = num()
                    if (rel) { x += cx; y += cy }
                    val x1 = if (lastCmd in "QqTt") cx * 2 - lastCtrlX else cx
                    val y1 = if (lastCmd in "QqTt") cy * 2 - lastCtrlY else cy
                    path.quadraticBezierTo(x1, y1, x, y)
                    lastCtrlX = x1; lastCtrlY = y1; cx = x; cy = y
                }
                'A' -> {
                    val rx = num(); val ry = num(); val rot = num()
                    val large = num() != 0f; val sweep = num() != 0f
                    var x = num(); var y = num()
                    if (rel) { x += cx; y += cy }
                    arcTo(path, cx, cy, rx, ry, rot, large, sweep, x, y)
                    cx = x; cy = y
                }
                'Z' -> {
                    path.close(); cx = sx; cy = sy
                }
                else -> { i++ }
            }
            lastCmd = cmd
        }
    }

    /** SVG 端点参数 -> 圆心参数，采样成折线（图标里的圆弧只有圆形，采样足够平滑）。 */
    private fun arcTo(
        path: Path, x1: Float, y1: Float, rxIn: Float, ryIn: Float, rotationDeg: Float,
        largeArc: Boolean, sweep: Boolean, x2: Float, y2: Float
    ) {
        var rx = abs(rxIn); var ry = abs(ryIn)
        if (rx == 0f || ry == 0f) { path.lineTo(x2, y2); return }
        val phi = Math.toRadians(rotationDeg.toDouble())
        val cosPhi = cos(phi); val sinPhi = sin(phi)
        val dx2 = (x1 - x2) / 2.0; val dy2 = (y1 - y2) / 2.0
        val x1p = cosPhi * dx2 + sinPhi * dy2
        val y1p = -sinPhi * dx2 + cosPhi * dy2
        var rxD = rx.toDouble(); var ryD = ry.toDouble()
        val lambda = (x1p * x1p) / (rxD * rxD) + (y1p * y1p) / (ryD * ryD)
        if (lambda > 1) {
            val s = sqrt(lambda); rxD *= s; ryD *= s; rx = rxD.toFloat(); ry = ryD.toFloat()
        }
        val sign = if (largeArc != sweep) 1 else -1
        var num = rxD * rxD * ryD * ryD - rxD * rxD * y1p * y1p - ryD * ryD * x1p * x1p
        val den = rxD * rxD * y1p * y1p + ryD * ryD * x1p * x1p
        if (num < 0) num = 0.0
        val coef = sign * sqrt(if (den == 0.0) 0.0 else num / den)
        val cxp = coef * (rxD * y1p / ryD)
        val cyp = coef * (-ryD * x1p / rxD)
        val cx = cosPhi * cxp - sinPhi * cyp + (x1 + x2) / 2.0
        val cy = sinPhi * cxp + cosPhi * cyp + (y1 + y2) / 2.0
        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            var a = Math.toDegrees(kotlin.math.acos((dot / len).coerceIn(-1.0, 1.0)))
            if (ux * vy - uy * vx < 0) a = -a
            return a
        }
        val ux = (x1p - cxp) / rxD; val uy = (y1p - cyp) / ryD
        val vx = (-x1p - cxp) / rxD; val vy = (-y1p - cyp) / ryD
        val start = angle(1.0, 0.0, ux, uy)
        var delta = angle(ux, uy, vx, vy)
        if (!sweep && delta > 0) delta -= 360.0
        if (sweep && delta < 0) delta += 360.0
        val steps = 32
        for (s in 0..steps) {
            val a = Math.toRadians(start + delta * s / steps)
            val px = cosPhi * rxD * cos(a) - sinPhi * ryD * sin(a) + cx
            val py = sinPhi * rxD * cos(a) + cosPhi * ryD * sin(a) + cy
            // 圆弧起点就是当前点，必须延续当前子路径；moveTo 会把填充剪影切碎成薄片
            path.lineTo(px.toFloat(), py.toFloat())
        }
    }
}

@Composable
fun AppIcon(
    name: String,
    color: Color,
    size: Dp = 22.dp,
    strokeWidth: Float = 2f,
    solid: Boolean = false,
    modifier: Modifier = Modifier
) {
    val path = remember(name, solid) { IconParser.path(name, solid) }
    Canvas(modifier.then(Modifier.size(size))) {
        val s = this.size.width / 24f
        scale(s, s, pivot = Offset.Zero) {
            if (solid) {
                drawPath(path, color, style = Fill)
                drawPath(path, color, style = Stroke(0.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            } else {
                drawPath(path, color, style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
