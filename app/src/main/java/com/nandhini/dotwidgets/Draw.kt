package com.nandhini.dotwidgets

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object C {
    val CARD_LIGHT = 0xFFFAFAFA.toInt()
    val CARD_DARK = 0xFF26262A.toInt()
    val INK = 0xFF111111.toInt()
    val PAPER = 0xFFF5F5F7.toInt()
    val RED = 0xFFFF453A.toInt()
    val GREEN = 0xFF30D158.toInt()
    val BLUE = 0xFF0A84FF.toInt()
    val ORANGE = 0xFFFF9F0A.toInt()
    val PURPLE = 0xFFBF5AF2.toInt()
    val CYAN = 0xFF64D2FF.toInt()
    val GREY = 0xFF8E8E93.toInt()
    val OFF_DARK = 0xFF48484C.toInt()
    val OFF_LIGHT = 0xFFD8D8D8.toInt()
    val TILE_DARK = 0xFF3A3A3E.toInt()
    val SKY = 0xFF2C5AA0.toInt()
    val SKY_NIGHT = 0xFF1B2A4A.toInt()
}

enum class Font { REG, MED, MONO }

/**
 * Draws in a virtual coordinate box whose short side is 160 units, so the
 * same design works on any widget size. vw/vh are the box's width/height.
 */
class Pen(val canvas: Canvas, pxW: Int, pxH: Int, val dark: Boolean) {
    val unit = min(pxW, pxH) / 160f
    val vw = pxW / unit
    val vh = pxH / unit
    val wide get() = vw >= 250f
    val fg = if (dark) C.PAPER else C.INK
    val off = if (dark) C.OFF_DARK else C.OFF_LIGHT
    val p = 14f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    init { canvas.scale(unit, unit) }

    fun card(color: Int, radius: Float = 22f) {
        fill.color = color
        canvas.drawRoundRect(RectF(0f, 0f, vw, vh), radius, radius, fill)
    }

    fun rect(l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 0f) {
        fill.color = color
        canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, fill)
    }

    fun dot(x: Float, y: Float, r: Float, color: Int) {
        fill.color = color
        canvas.drawCircle(x, y, r, fill)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        stroke.color = color; stroke.strokeWidth = w
        canvas.drawLine(x1, y1, x2, y2, stroke)
    }

    private fun paint(size: Float, color: Int, font: Font, align: Paint.Align, alpha: Int): Paint = text.apply {
        textSize = size
        this.color = color
        this.alpha = alpha
        textAlign = align
        typeface = when (font) {
            Font.REG -> Typeface.create("sans-serif", Typeface.NORMAL)
            Font.MED -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            Font.MONO -> Typeface.MONOSPACE
        }
        letterSpacing = when (font) { Font.MONO -> 0.08f; Font.MED -> if (size > 26f) -0.03f else 0f; else -> 0f }
    }

    /** Draws text with its baseline at y. Returns the drawn width. */
    fun text(s: String, x: Float, y: Float, size: Float, color: Int = fg, font: Font = Font.REG,
             align: Paint.Align = Paint.Align.LEFT, alpha: Int = 255, maxW: Float = 0f): Float {
        val pt = paint(size, color, font, align, alpha)
        val str = if (maxW > 0f) ellipsize(s, maxW, pt) else s
        canvas.drawText(str, x, y, pt)
        return pt.measureText(str)
    }

    fun mono(s: String, x: Float, y: Float, color: Int = fg, align: Paint.Align = Paint.Align.LEFT,
             alpha: Int = 160, size: Float = 10.5f, maxW: Float = 0f) =
        text(s, x, y, size, color, Font.MONO, align, alpha, maxW)

    fun measure(s: String, size: Float, font: Font = Font.REG): Float = paint(size, fg, font, Paint.Align.LEFT, 255).measureText(s)

    /** Largest font size (up to max) at which s fits in width w. */
    fun fit(s: String, w: Float, max: Float, font: Font = Font.MED): Float {
        val m = measure(s, max, font)
        return if (m <= w) max else max * w / m
    }

    private fun ellipsize(s: String, maxW: Float, pt: Paint): String {
        if (pt.measureText(s) <= maxW) return s
        var t = s
        while (t.isNotEmpty() && pt.measureText("$t…") > maxW) t = t.dropLast(1)
        return "$t…"
    }

    // ---- dots

    /** A row of n dots from x to x + width, the first `on` filled. */
    fun dotRow(x: Float, y: Float, width: Float, n: Int, on: Int, cOn: Int, cOff: Int = off,
               accentAt: Int = -1, accent: Int = C.RED, maxR: Float = 2.6f) {
        val step = width / n
        val r = min(step * 0.38f, maxR)
        for (i in 0 until n) dot(x + step * i + step / 2, y, r, if (i == accentAt) accent else if (i < on) cOn else cOff)
    }

    /** Dots around a circle, clockwise from 12 o'clock. */
    fun dotRing(cx: Float, cy: Float, r: Float, n: Int, on: Int, dotR: Float, cOn: Int, cOff: Int = off,
                accentAt: Int = -1, accent: Int = C.RED) {
        for (i in 0 until n) {
            val a = i * 2 * Math.PI / n - Math.PI / 2
            dot(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat(), dotR,
                if (i == accentAt) accent else if (i < on) cOn else cOff)
        }
    }

    fun ring(cx: Float, cy: Float, r: Float, w: Float, frac: Float, color: Int, track: Int) {
        stroke.strokeWidth = w
        stroke.color = track
        canvas.drawCircle(cx, cy, r, stroke)
        stroke.color = color
        val f = frac.coerceIn(0f, 1f)
        if (f > 0.001f) canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 360f * f, false, stroke)
        // laps beyond 100% show as a dot at the ring's end, like a sports watch
        if (frac > 1f) {
            val a = Math.toRadians((360.0 * (frac % 1f)) - 90.0)
            dot(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat(), w / 2f, color)
        }
    }

    /** Dot-matrix text. Returns width. Each cell is `s` units. */
    fun dotText(str: String, x: Float, y: Float, s: Float, color: Int, ghost: Boolean = true): Float {
        var cx = x
        for (ch in str) {
            val g = DotFont.glyph(ch)
            val cols = g[0].length
            for ((row, line) in g.withIndex()) for ((col, b) in line.withIndex()) {
                if (b == '1') dot(cx + col * s + s / 2, y + row * s + s / 2, s * 0.4f, color)
                else if (ghost) dot(cx + col * s + s / 2, y + row * s + s / 2, s * 0.4f, (color and 0x00FFFFFF) or 0x18000000)
            }
            cx += (cols + 1) * s
        }
        return cx - x - s
    }

    fun dotTextWidth(str: String, s: Float): Float = str.sumOf { DotFont.glyph(it)[0].length + 1 } * s - s

    /** A dot icon from DotFont.icons, centred at (cx, cy) with total height h. */
    fun icon(name: String, cx: Float, cy: Float, h: Float, color: Int) {
        val g = DotFont.icons[name] ?: return
        val s = h / g.size
        val w = g[0].length * s
        val x0 = cx - w / 2
        val y0 = cy - h / 2
        for ((row, line) in g.withIndex()) for ((col, b) in line.withIndex()) {
            if (b == 'X') dot(x0 + col * s + s / 2, y0 + row * s + s / 2, s * 0.42f, color)
        }
    }

    /** Draws a bitmap into a rounded square (album art). */
    fun image(bmp: Bitmap, l: Float, t: Float, size: Float, radius: Float) {
        val sh = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val m = Matrix()
        val sc = size / min(bmp.width, bmp.height)
        m.setScale(sc, sc)
        m.postTranslate(l - (bmp.width * sc - size) / 2, t - (bmp.height * sc - size) / 2)
        sh.setLocalMatrix(m)
        val pt = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = sh }
        canvas.drawRoundRect(RectF(l, t, l + size, t + size), radius, radius, pt)
    }
}

object DotFont {
    private val g = mapOf(
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        ':' to listOf("0", "0", "1", "0", "1", "0", "0"),
        '.' to listOf("0", "0", "0", "0", "0", "0", "1"),
        '-' to listOf("000", "000", "000", "111", "000", "000", "000"),
        '%' to listOf("11001", "11010", "00010", "00100", "01000", "01011", "10011"),
        ' ' to listOf("00", "00", "00", "00", "00", "00", "00"),
    )

    fun glyph(c: Char) = g[c] ?: g[' ']!!

    val icons: Map<String, List<String>> = mapOf(
        "bulb" to listOf("..XXX..", ".X...X.", "X.....X", "X.....X", ".X...X.", "..X.X..", "..XXX..", "..XXX..", "...X..."),
        "wifi" to listOf(".XXXXXXX.", "X.......X", "..XXXXX..", ".X.....X.", "...XXX...", "..X...X..", "....X...."),
        "moon" to listOf("..XXX..", ".XX....", "XX.....", "XX.....", "XX.....", ".XX...X", "..XXXX."),
        "camera" to listOf("..XXX....", "XXXXXXXXX", "X..XXX..X", "X.X...X.X", "X..XXX..X", "X.......X", "XXXXXXXXX"),
        "note" to listOf("..XXXXX", "..X...X", "..X...X", "..X...X", "XXX.XXX", "XXX.XXX"),
        "sun" to listOf("....X....", ".X.....X.", "...XXX...", "..XXXXX..", "X.XXXXX.X", "..XXXXX..", "...XXX...", ".X.....X.", "....X...."),
        "cloud" to listOf("...XXX....", "..X...XX..", ".X......X.", "X........X", "X........X", ".XXXXXXXX."),
        "rain" to listOf("...XXX....", "..X...XX..", ".X......X.", "X........X", ".XXXXXXXX.", "..........", "..X..X..X.", ".X..X..X.."),
        "storm" to listOf("...XXX....", "..X...XX..", ".X......X.", "X........X", ".XXXXXXXX.", "....XX....", "...XX.....", "....XX....", "...X......"),
        "fog" to listOf("XXXXXXXXX", ".........", ".XXXXXXX.", ".........", "XXXXXXXXX", ".........", "..XXXXX.."),
        "snow" to listOf("X...X...X", ".X..X..X.", "..X.X.X..", "XXXXXXXXX", "..X.X.X..", ".X..X..X.", "X...X...X"),
        "bolt" to listOf("...XX", "..XX.", ".XX..", "XXXX.", "..XX.", ".XX..", ".X...", "X...."),
        "prev" to listOf("X.....X", "X....XX", "X...XXX", "X..XXXX", "X...XXX", "X....XX", "X.....X"),
        "next" to listOf("X.....X", "XX....X", "XXX...X", "XXXX..X", "XXX...X", "XX....X", "X.....X"),
        "play" to listOf("XX.....", "XXXX...", "XXXXXX.", "XXXXXXX", "XXXXXX.", "XXXX...", "XX....."),
        "pause" to listOf("XX...XX", "XX...XX", "XX...XX", "XX...XX", "XX...XX", "XX...XX", "XX...XX"),
        "reset" to listOf("..XXX.X", ".X...XX", "X...XXX", "X......", "X.....X", ".X...X.", "..XXX.."),
        "down" to listOf("..X..", "..X..", "..X..", "X.X.X", ".XXX.", "..X.."),
        "up" to listOf("..X..", ".XXX.", "X.X.X", "..X..", "..X..", "..X.."),
        "pin" to listOf(".XXX.", "X...X", "X...X", ".X.X.", "..X.."),
    )
}
