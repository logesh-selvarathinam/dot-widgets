package com.nandhini.dotwidgets

import android.app.AlarmManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint.Align
import android.text.format.DateFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object Renderers {
    private val IN = Locale("en", "IN")
    private fun num(n: Int) = NumberFormat.getIntegerInstance(IN).format(n)

    private fun is24(ctx: Context) = DateFormat.is24HourFormat(ctx)
    private fun clock(ctx: Context, t: ZonedDateTime) = t.format(DateTimeFormatter.ofPattern(if (is24(ctx)) "HH:mm" else "h:mm"))
    private fun hm(ctx: Context, ms: Long): String {
        val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        return t.format(DateTimeFormatter.ofPattern(if (is24(ctx)) "HH:mm" else "h:mm a", Locale.ENGLISH))
    }

    fun draw(ctx: Context, kind: Kind, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        when (kind) {
            Kind.CLOCK -> clockWidget(ctx, Pen(c, w, h, true))
            Kind.BATTERY -> battery(ctx, Pen(c, w, h, false))
            Kind.WEATHER -> weather(ctx, Pen(c, w, h, true))
            Kind.CALENDAR -> calendar(ctx, Pen(c, w, h, false))
            Kind.ACTIVITY -> activity(ctx, Pen(c, w, h, true))
            Kind.TOGGLES -> toggles(ctx, Pen(c, w, h, true))
            Kind.NOW_PLAYING -> nowPlaying(ctx, Pen(c, w, h, true))
            Kind.WORLD_CLOCK -> worldClock(ctx, Pen(c, w, h, false))
            Kind.FOCUS -> focus(ctx, Pen(c, w, h, true))
            Kind.SUN -> sun(ctx, Pen(c, w, h, true))
            Kind.DATA -> data(ctx, Pen(c, w, h, true))
            Kind.STEPS -> steps(ctx, Pen(c, w, h, false))
            Kind.TORCH -> torch(ctx, Pen(c, w, h, true))
            Kind.RAHU -> rahu(ctx, Pen(c, w, h, false))
            Kind.NOTIFS -> notifs(ctx, Pen(c, w, h, true))
            Kind.PET -> pet(ctx, Pen(c, w, h, true))
            Kind.SCREEN -> screen(ctx, Pen(c, w, h, false))
            Kind.CHARGING -> charging(ctx, Pen(c, w, h, true))
            Kind.NETSPEED -> netSpeed(ctx, Pen(c, w, h, false))
            Kind.COMPASS -> compass(ctx, Pen(c, w, h, true))
            Kind.WEEKS -> weeks(ctx, Pen(c, w, h, true))
        }
        return bmp
    }

    fun error(ctx: Context, w: Int, h: Int, e: Exception): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pen = Pen(Canvas(bmp), w, h, true)
        pen.card(C.CARD_DARK)
        pen.mono("● ERROR", pen.p, pen.p + 10, C.RED, alpha = 255)
        pen.text(e.javaClass.simpleName, pen.p, pen.vh / 2, 13f, maxW = pen.vw - 2 * pen.p)
        pen.text(e.message ?: "", pen.p, pen.vh / 2 + 18, 11f, alpha = 150, maxW = pen.vw - 2 * pen.p)
        return bmp
    }

    /** A centred "set me up" message used when a permission is missing. */
    private fun setup(pen: Pen, title: String, line1: String, line2: String = "Open Dot Widgets") {
        pen.mono("● $title", pen.p, pen.p + 10, C.RED, alpha = 255)
        pen.text(line1, pen.p, pen.vh - pen.p - 20, 13f, font = Font.MED, maxW = pen.vw - 2 * pen.p)
        pen.text(line2, pen.p, pen.vh - pen.p - 4, 11f, alpha = 140, maxW = pen.vw - 2 * pen.p)
    }

    // 1 ---------------------------------------------------------- clock and date
    private fun clockWidget(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_DARK)
        val now = ZonedDateTime.now()
        val day = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase()
        val date = now.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
        val alarm = ctx.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime
        val alarmTxt = if (alarm != null) "ALARM ${hm(ctx, alarm).uppercase()}" else "NO ALARM"
        val time = clock(ctx, now)
        mono("● $day", p, p + 10, C.RED, alpha = 255)
        if (wide) {
            val size = fit(time, vw * 0.6f, min(vh * 0.42f, 70f))
            text(time, p - 2, p + 22 + size * 0.74f, size, font = Font.MED)
            text(date, vw - p, p + 22 + size * 0.74f - 22, 22f, font = Font.MED, align = Align.RIGHT)
            mono(alarmTxt, vw - p, p + 22 + size * 0.74f, align = Align.RIGHT)
        } else {
            val size = fit(time, vw - 2 * p, 52f)
            text(time, p - 1, p + 18 + size * 0.74f, size, font = Font.MED)
            text(date, p, vh - p - 26, 17f, font = Font.MED)
            mono(alarmTxt, p, vh - p - 12)
        }
        // minutes of this hour as 60 dots, the current one in red
        dotRow(p, vh - p + 2, vw - 2 * p, 60, now.minute, fg, off, accentAt = now.minute, maxR = 2f)
    }

    // 2 ---------------------------------------------------------- battery rings
    private fun battery(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_LIGHT)
        val b = Battery.read(ctx)
        val col = when { b.level <= 15 -> C.RED; b.level <= 30 -> C.ORANGE; else -> C.GREEN }
        mono("BATTERY", p, p + 10)
        if (b.charging) icon("bolt", vw - p - 4, p + 6, 12f, C.GREEN)
        val r = min(vw, vh) * 0.27f
        val cx = vw / 2
        val cy = vh / 2 + 2
        dotRing(cx, cy, r, 36, (b.level * 36 / 100f).roundToInt(), 2.5f, col, C.OFF_LIGHT)
        ring(cx, cy, r - 12, 7f, b.level / 100f, C.INK, 0xFFE8E8E8.toInt())
        val s = fit("${b.level}", r, 22f)
        text("${b.level}", cx, cy + s * 0.36f, s, font = Font.MED, align = Align.CENTER)
        val status = if (b.charging) "Charging" else "On battery"
        text(status, p, vh - p, 12f, maxW = vw / 2)
        text("${b.tempC.roundToInt()}°C", vw - p, vh - p, 12f, font = Font.MED, align = Align.RIGHT, alpha = 180)
    }

    // 3 ---------------------------------------------------------- weather
    private fun weather(ctx: Context, pen: Pen) = with(pen) {
        val wx = Weather.cached(ctx)
        card(if (wx?.isDay == false) C.SKY_NIGHT else C.SKY)
        val city = Prefs(ctx).city
        icon("pin", p + 3, p + 4, 8f, C.PAPER)
        text(city, p + 10, p + 9, 13f, font = Font.MED, maxW = vw - 2 * p - 34)
        if (wx == null) {
            text("Tap to load", p, vh - p - 18, 15f, font = Font.MED)
            text("Needs internet", p, vh - p - 2, 11f, alpha = 160)
            return@with
        }
        val iconName = when (wx.code) {
            0, 1 -> if (wx.isDay) "sun" else "moon"
            2, 3 -> "cloud"
            45, 48 -> "fog"
            in 51..67, in 80..82 -> "rain"
            in 71..77, 85, 86 -> "snow"
            in 95..99 -> "storm"
            else -> "cloud"
        }
        icon(iconName, vw - p - 12, p + 10, 20f, if (iconName == "sun") C.ORANGE else C.PAPER)
        text("${wx.temp}°", p - 2, p + 58, 46f, font = Font.MED)
        text("${Weather.describe(wx.code)} · H${wx.hi} L${wx.lo}", p, vh - p - 26, 11.5f, maxW = vw - 2 * p)
        // next hours as dot columns, taller = warmer
        val hrs = wx.hourly
        if (hrs.isNotEmpty()) {
            val lo = hrs.min()
            val hi = max(hrs.max(), lo + 1f)
            val n = hrs.size
            val step = (vw - 2 * p) / n
            for ((i, t) in hrs.withIndex()) {
                val dots = 2 + ((t - lo) / (hi - lo) * 4).roundToInt()
                for (k in 0 until dots) dot(p + step * i + step / 2, vh - p - 1 - k * 3.4f, 1.3f,
                    (C.PAPER and 0x00FFFFFF) or ((110 + k * 25).coerceAtMost(255) shl 24))
            }
        }
        val ago = (System.currentTimeMillis() - wx.at) / 60000
        if (ago > 90) mono("${ago / 60}H OLD", vw - p, p + 34, align = Align.RIGHT, size = 9f)
    }

    // 4 ---------------------------------------------------------- calendar and next event
    private fun calendar(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_LIGHT)
        val now = ZonedDateTime.now()
        mono(now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).uppercase(), p, p + 10, C.RED, alpha = 255)
        text("${now.dayOfMonth}", p - 2, p + 52, 42f, font = Font.MED)
        mono(now.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(), vw - p, p + 10, align = Align.RIGHT)
        if (!ctx.has(android.Manifest.permission.READ_CALENDAR)) {
            text("Allow calendar", p + 10, vh - p - 16, 12.5f, font = Font.MED)
            text("in Dot Widgets", p + 10, vh - p, 11f, alpha = 140)
            rect(p, vh - p - 28, p + 3, vh - p + 2, C.RED, 1.5f)
            return@with
        }
        val e = Cal.next(ctx)
        if (e == null) {
            text("No more events", p + 10, vh - p - 16, 12.5f, font = Font.MED)
            text("Enjoy your day", p + 10, vh - p, 11f, alpha = 140)
            rect(p, vh - p - 28, p + 3, vh - p + 2, C.GREEN, 1.5f)
            return@with
        }
        val col = if (e.color != 0) e.color or 0xFF000000.toInt() else C.RED
        rect(p, vh - p - 28, p + 3, vh - p + 2, col, 1.5f)
        text(e.title, p + 10, vh - p - 16, 12.5f, font = Font.MED, maxW = vw - 2 * p - 10)
        val startsToday = Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
        val whenTxt = when {
            e.allDay -> "All day"
            e.begin <= System.currentTimeMillis() -> "Now · until ${hm(ctx, e.end)}"
            startsToday -> "${hm(ctx, e.begin)} – ${hm(ctx, e.end)}"
            else -> Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)) + " " + hm(ctx, e.begin)
        }
        text(whenTxt, p + 10, vh - p, 11f, alpha = 140, maxW = vw - 2 * p - 10)
    }

    // 5 ---------------------------------------------------------- activity rings
    private fun activity(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_DARK)
        val t = Steps.today(ctx) ?: return@with setup(pen, "ACTIVITY", "Allow activity")
        val prefs = Prefs(ctx)
        val km = t.steps * prefs.strideM / 1000f
        val kcal = (t.steps * 0.04f).roundToInt()
        val cx = if (wide) p + 52 else vw / 2
        val cy = if (wide) vh / 2 else p + 50
        val r = 44f
        ring(cx, cy, r, 10f, t.steps / prefs.stepGoal.toFloat(), C.RED, 0xFF4A1D1B.toInt())
        ring(cx, cy, r - 13, 10f, t.activeHours / 10f, C.GREEN, 0xFF173B22.toInt())
        ring(cx, cy, r - 26, 10f, kcal / 300f, C.BLUE, 0xFF0F2B4D.toInt())
        val x = if (wide) cx + r + 18 else p
        val kmTxt = String.format(Locale.US, "%.1f km", km)
        if (wide) {
            text("${num(t.steps)} steps", x, p + 30, 16f, C.RED, Font.MED)
            text("${t.activeHours} active hours", x, p + 54, 14f, C.GREEN, Font.MED)
            text("$kcal kcal · $kmTxt", x, p + 78, 14f, C.BLUE, Font.MED)
        } else {
            val w1 = text(num(t.steps), p, vh - p - 14, 12.5f, C.RED, Font.MED)
            text(" steps", p + w1, vh - p - 14, 11.5f, C.RED)
            text("${t.activeHours} hrs", vw - p, vh - p - 14, 12.5f, C.GREEN, Font.MED, Align.RIGHT)
            text("$kcal kcal", p, vh - p + 1, 11.5f, C.BLUE)
            text(kmTxt, vw - p, vh - p + 1, 11.5f, align = Align.RIGHT, alpha = 160)
        }
    }

    // 6 ---------------------------------------------------------- quick toggles
    private fun toggles(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_DARK)
        val g = 8f
        val pad = 10f
        val tw = (vw - 2 * pad - g) / 2
        val th = (vh - 2 * pad - g) / 2
        data class T(val icon: String, val label: String, val on: Boolean, val onBg: Int, val onFg: Int)
        val list = listOf(
            T("bulb", "TORCH", Torch.isOn(ctx), C.PAPER, C.INK),
            T("wifi", "WI-FI", Net.wifiOn(ctx), C.BLUE, C.PAPER),
            T("moon", "DND", Dnd.isOn(ctx), C.PURPLE, C.PAPER),
            T("camera", "CAMERA", false, C.PAPER, C.INK),
        )
        list.forEachIndexed { i, t ->
            val l = pad + (i % 2) * (tw + g)
            val top = pad + (i / 2) * (th + g)
            rect(l, top, l + tw, top + th, if (t.on) t.onBg else C.TILE_DARK, 16f)
            val fgc = if (t.on) t.onFg else if (t.icon == "moon") C.PURPLE else C.PAPER
            icon(t.icon, l + tw / 2, top + th / 2 - 5, min(th * 0.34f, 20f), fgc)
            mono(t.label, l + tw / 2, top + th - 8, fgc, Align.CENTER, alpha = 200, size = 8.5f)
        }
    }

    // 7 ---------------------------------------------------------- now playing
    private fun nowPlaying(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_DARK)
        if (!NotifService.isEnabled(ctx)) return@with setup(pen, "NOW PLAYING", "Allow notification access", "Tap to open settings")
        val t = Media.track(ctx)
        val art = vh / 2 - p - 2
        if (t?.art != null) image(t.art, p, p, art, 12f)
        else {
            rect(p, p, p + art, p + art, C.ORANGE, 12f)
            icon("note", p + art / 2, p + art / 2, art * 0.42f, C.INK)
        }
        val tx = p + art + 12
        if (t == null) {
            text("Nothing playing", tx, p + art / 2, 15f, font = Font.MED, maxW = vw - tx - p)
            text("Play music in any app", tx, p + art / 2 + 16, 11.5f, alpha = 140, maxW = vw - tx - p)
        } else {
            text(t.title, tx, p + art / 2 - 2, 15f, font = Font.MED, maxW = vw - tx - p)
            val sub = listOf(t.artist, t.app).filter { it.isNotBlank() }.joinToString(" · ")
            text(sub, tx, p + art / 2 + 15, 11.5f, alpha = 140, maxW = vw - tx - p)
        }
        // progress as dots
        val progN = 50
        val on = ((t?.progress ?: 0f) * progN).roundToInt()
        dotRow(p, vh / 2 + 6, vw - 2 * p, progN, on, C.ORANGE, C.OFF_DARK, accentAt = if (t?.progress != null) on else -1, maxR = 1.6f)
        // three control zones across the bottom half
        val cy = vh * 0.76f
        val third = vw / 3
        val sz = min(18f, vh * 0.12f)
        icon("prev", third / 2, cy, sz, C.PAPER)
        icon(if (t?.playing == true) "pause" else "play", vw / 2, cy, sz + 4, C.PAPER)
        icon("next", vw - third / 2, cy, sz, C.PAPER)
    }

    // 8 ---------------------------------------------------------- world clock
    private fun worldClock(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_LIGHT)
        val prefs = Prefs(ctx)
        val here = ZonedDateTime.now()
        val zone = try { ZoneId.of(prefs.worldZone) } catch (e: Exception) { ZoneId.of("UTC") }
        val there = here.withZoneSameInstant(zone)
        val diffMin = (there.offset.totalSeconds - here.offset.totalSeconds) / 60
        val sign = if (diffMin >= 0) "+" else "−"
        val diff = "$sign${kotlin.math.abs(diffMin) / 60}:${String.format(Locale.US, "%02d", kotlin.math.abs(diffMin) % 60)}"
        val dayShift = when {
            there.toLocalDate().isAfter(here.toLocalDate()) -> " TMRW"
            there.toLocalDate().isBefore(here.toLocalDate()) -> " YDAY"
            else -> ""
        }
        val half = vh / 2
        mono(prefs.city.uppercase(), p, p + 10, maxW = vw - 2 * p)
        text(clock(ctx, here), p - 1, half - 10, fit(clock(ctx, here), vw - 2 * p, 34f), font = Font.MED)
        rect(p, half - 0.5f, vw - p, half + 0.5f, 0xFFDDDDDD.toInt())
        val w1 = mono(prefs.worldLabel.uppercase(), p, half + 20, maxW = vw - 2 * p - 60)
        mono(" $diff$dayShift", p + w1, half + 20, C.RED, alpha = 255)
        text(clock(ctx, there), p - 1, vh - p - 2, fit(clock(ctx, there), vw - 2 * p, 34f), font = Font.MED, alpha = 120)
        // a tiny day/night dot for the other city
        dot(vw - p - 4, half + 16, 4f, if (there.hour in 6..17) C.ORANGE else C.BLUE)
    }

    // 9 ---------------------------------------------------------- focus timer
    private fun focus(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_DARK)
        val f = Focus.read(ctx)
        val label = when (f.state) { Focus.RUNNING -> "● FOCUS"; Focus.PAUSED -> "● PAUSED"; else -> "● READY" }
        mono(label, p, p + 10, C.PURPLE, alpha = 255)
        val cx = vw / 2
        val cy = vh * 0.47f
        val r = min(vh * 0.27f, vw * 0.3f)
        val frac = if (f.totalMs > 0) 1f - f.leftMs.toFloat() / f.totalMs else 0f
        dotRing(cx, cy, r + 7, 40, (frac * 40).roundToInt(), 1.7f, C.PURPLE, C.OFF_DARK)
        ring(cx, cy, r - 2, 5f, frac, C.PURPLE, 0xFF3A2A48.toInt())
        val mins = ((f.leftMs + 59_999) / 60_000).toInt()
        text("$mins", cx, cy + 7, 24f, font = Font.MED, align = Align.CENTER)
        mono(if (f.state == Focus.IDLE) "TAP" else "MIN", cx, cy + 19, align = Align.CENTER, size = 8f)
        text("Session ${f.session} of 4", p, vh - p, 11f, alpha = 150)
        icon("reset", vw - p - 6, vh - p - 4, 11f, C.GREY)
    }

    // 10 --------------------------------------------------------- sunrise and sunset
    private fun sun(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_DARK)
        mono("DAYLIGHT", p, p + 10)
        val d = Sun.today(ctx) ?: return@with text("No sunrise today", p, vh / 2, 13f)
        val now = System.currentTimeMillis()
        val t = ((now - d.rise).toFloat() / (d.set - d.rise))
        val left = p + 4
        val right = vw - p - 4
        val base = vh - p - 30
        val height = min(vh * 0.42f, (right - left) * 0.45f)
        line(left - 4, base, right + 4, base, 1f, C.OFF_DARK)
        val n = 26
        for (i in 0..n) {
            val f = i / n.toFloat()
            val x = left + f * (right - left)
            val y = base - sin(f * Math.PI).toFloat() * height
            dot(x, y, 1.5f, if (t in 0f..1f && f <= t) C.ORANGE else C.OFF_DARK)
        }
        if (t in 0f..1f) {
            val x = left + t * (right - left)
            val y = base - sin(t * Math.PI).toFloat() * height
            dot(x, y, 6.5f, C.ORANGE)
        } else {
            val toRise = if (now < d.rise) d.rise - now else Sun.times(LocalDate.now().plusDays(1), Prefs(ctx).lat, Prefs(ctx).lon)?.rise?.minus(now)
            if (toRise != null) mono("SUNRISE IN ${toRise / 3_600_000}H ${(toRise / 60_000) % 60}M", vw / 2, base - height / 2, align = Align.CENTER, size = 9f)
            icon("moon", vw - p - 8, p + 8, 12f, C.PAPER)
        }
        text("↑ ${hm(ctx, d.rise)}", p, vh - p, 12f)
        text("↓ ${hm(ctx, d.set)}", vw - p, vh - p, 12f, C.ORANGE, align = Align.RIGHT)
        val len = (d.set - d.rise) / 60_000
        mono("${len / 60}H ${len % 60}M", vw - p, p + 10, align = Align.RIGHT, size = 9f)
    }

    // 11 --------------------------------------------------------- data and storage
    private fun data(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_DARK)
        val prefs = Prefs(ctx)
        mono("MOBILE DATA TODAY", p, p + 10)
        val bytes = Usage.mobileBytesToday(ctx)
        if (bytes == null) {
            text("Allow usage access", p, p + 36, 13f, font = Font.MED)
        } else {
            val gb = bytes / 1e9
            val lim = prefs.dataLimitGb
            val used = if (gb < 1) String.format(Locale.US, "%.0f MB", bytes / 1e6) else String.format(Locale.US, "%.2f GB", gb)
            val w1 = text(used, p - 1, p + 42, 26f, font = Font.MED)
            text(" / ${trim(lim)} GB", p + w1, p + 42, 12f, alpha = 140)
            val frac = (gb / lim).toFloat()
            dotRow(p, p + 56, vw - 2 * p, 20, (frac * 20).roundToInt().coerceAtMost(20),
                if (frac > 0.9f) C.RED else C.GREEN, C.OFF_DARK)
        }
        val st = Usage.storage(ctx)
        if (st != null) {
            val used = (st.usedBytes / 1e9).roundToInt()
            val total = marketedGb(st.totalBytes)
            mono("STORAGE $used / $total GB", p, vh - p - 12)
            dotRow(p, vh - p + 1, vw - 2 * p, 20, (st.usedBytes * 20 / st.totalBytes).toInt(), C.BLUE, C.OFF_DARK)
        }
    }

    private fun trim(f: Float) = if (f == f.toInt().toFloat()) f.toInt().toString() else String.format(Locale.US, "%.1f", f)
    private fun marketedGb(b: Long): Int {
        val gb = b / 1e9
        return listOf(32, 64, 128, 256, 512, 1024).firstOrNull { it >= gb } ?: gb.roundToInt()
    }

    // 12 --------------------------------------------------------- step counter (S1)
    private fun steps(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_LIGHT)
        val t = Steps.today(ctx) ?: return@with setup(pen, "STEPS", "Allow activity")
        val goal = Prefs(ctx).stepGoal
        mono("● STEPS", p, p + 10, alpha = 200)
        dot(p + 3.5f, p + 6.5f, 3f, C.RED)
        val s = num(t.steps).replace(",", "")
        val cell = min(5f, (vw - 2 * p) / (s.length * 6 - 1))
        val y = p + 24
        dotText(s, p, y, cell, C.INK)
        val frac = t.steps / goal.toFloat()
        val on = (frac * 20).toInt().coerceAtMost(20)
        dotRow(p, vh - p - 20, vw - 2 * p, 20, on, C.INK, C.OFF_LIGHT, accentAt = if (on < 20) on else -1)
        mono("GOAL ${num(goal)} · ${(frac * 100).roundToInt()}%", p, vh - p)
    }

    // 13 --------------------------------------------------------- flashlight
    private fun torch(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_DARK)
        val on = Torch.isOn(ctx)
        val r = min(vw, vh) * 0.24f
        val cx = vw / 2
        val cy = vh / 2 - 8
        if (on) dotRing(cx, cy, r + 10, 28, 28, 1.6f, C.PAPER)
        dot(cx, cy, r, if (on) C.PAPER else C.TILE_DARK)
        icon("bulb", cx, cy, r * 0.95f, if (on) C.INK else C.PAPER)
        mono(if (on) "TORCH ON" else "TORCH OFF", cx, vh - p, align = Align.CENTER, color = if (on) C.PAPER else C.GREY)
    }

    // 14 --------------------------------------------------------- Rahu kalam (U1)
    private fun rahu(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_LIGHT)
        val list = Sun.periods(ctx) ?: return@with text("No sunrise today", p, vh / 2, 13f)
        val d = Sun.today(ctx)!!
        val now = System.currentTimeMillis()
        val dow = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase()
        mono("$dow · TODAY", p, p + 10)
        val current = list.firstOrNull { now in it.start until it.end }
        val (nowTxt, nowCol) = when {
            current == null && (now < d.rise || now > d.set) -> "NIGHT" to C.GREY
            current == null -> "CLEAR" to C.GREEN
            else -> current.name.uppercase() to current.color
        }
        mono("● NOW: $nowTxt", vw - p, p + 10, nowCol, Align.RIGHT, alpha = 255)
        // timeline from sunrise to sunset
        val n = if (wide) 48 else 32
        val x0 = p
        val x1 = vw - p
        val ty = p + 30
        val step = (x1 - x0) / n
        for (i in 0 until n) {
            val tm = d.rise + (d.set - d.rise) * (i + 0.5) / n
            val span = list.firstOrNull { tm.toLong() in it.start until it.end }
            dot(x0 + step * i + step / 2, ty, min(step * 0.38f, 2.8f), span?.color ?: C.OFF_LIGHT)
        }
        if (now in d.rise..d.set) {
            val x = x0 + (now - d.rise).toFloat() / (d.set - d.rise) * (x1 - x0)
            line(x, ty - 8, x, ty + 8, 1.5f, C.INK)
        }
        mono(hm(ctx, d.rise), x0, ty + 18, size = 8.5f)
        mono(hm(ctx, d.set), x1, ty + 18, align = Align.RIGHT, size = 8.5f)
        val rows = list.map { "${it.name}  ${hm(ctx, it.start)}–${hm(ctx, it.end)}" to it.color }
        if (wide) {
            rows.forEachIndexed { i, (s, col) ->
                val x = if (i % 2 == 0) p else vw / 2 + 4
                val y = vh - p - 22 + (i / 2) * 20
                dot(x + 3, y - 4, 3.5f, col)
                text(s, x + 11, y, 12f, maxW = vw / 2 - 20)
            }
        } else {
            rows.forEachIndexed { i, (s, col) ->
                val y = ty + 40 + i * 17
                dot(p + 3, y - 4, 3f, col)
                text(s, p + 10, y, 10.5f, maxW = vw - 2 * p - 10)
            }
        }
    }

    // 15 --------------------------------------------------------- notification dots (U2)
    private fun notifs(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_DARK)
        val apps = Notifs.read(ctx) ?: return@with setup(pen, "UNREAD", "Allow notification access")
        val total = apps.sumOf { it.count }
        mono("UNREAD", p, p + 10)
        mono("$total", vw - p, p + 10, if (total > 0) C.RED else C.GREY, Align.RIGHT, alpha = 255)
        if (apps.isEmpty()) {
            dot(vw / 2, vh / 2 - 4, 7f, C.GREEN)
            text("All clear", vw / 2, vh - p, 13f, font = Font.MED, align = Align.CENTER)
            return@with
        }
        val shown = apps.take(3)
        val perRow = 12
        val step = (vw - 2 * p) / perRow
        var y = p + 30
        shown.forEach { a ->
            val col = appColor(a.pkg)
            val n = min(a.count, perRow * 2)
            for (i in 0 until n) dot(p + step * (i % perRow) + step / 2, y + (i / perRow) * 9, min(step * 0.36f, 3.6f), col)
            if (a.count > perRow * 2) mono("+", vw - p, y + 12, col, Align.RIGHT, alpha = 255)
            y += if (n > perRow) 30 else 21
        }
        // legend
        var x = p
        val ly = vh - p
        shown.forEach { a ->
            val label = "${a.label.take(9)} ${a.count}"
            val w = measure(label, 10f)
            if (x + w + 10 > vw - p) return@forEach
            dot(x + 3, ly - 3.5f, 3f, appColor(a.pkg))
            text(label, x + 9, ly, 10f, alpha = 200)
            x += w + 18
        }
    }

    private fun appColor(pkg: String): Int = when {
        "whatsapp" in pkg -> C.GREEN
        "gmail" in pkg || pkg.endsWith(".gm") -> C.RED
        "slack" in pkg -> C.PURPLE
        "telegram" in pkg -> C.CYAN
        "instagram" in pkg -> 0xFFFF375F.toInt()
        "youtube" in pkg -> C.RED
        "teams" in pkg || "outlook" in pkg -> C.BLUE
        "messag" in pkg || "mms" in pkg -> C.GREEN
        else -> listOf(C.BLUE, C.ORANGE, C.CYAN, C.PURPLE, C.PAPER)[kotlin.math.abs(pkg.hashCode()) % 5]
    }

    // 16 --------------------------------------------------------- dot pet (U3)
    private val petBody = listOf(
        "0011111100",
        "0111111110",
        "11E1111E11",
        "1111111111",
        "111MMMM111",
        "0111111110",
        "0011001100",
    )

    private fun pet(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_DARK)
        val t = Steps.today(ctx) ?: return@with setup(pen, "DOT PET", "Allow activity to hatch")
        val goal = Prefs(ctx).stepGoal
        val frac = t.steps / goal.toFloat()
        val hour = ZonedDateTime.now().hour
        val mood = when {
            hour >= 23 || hour < 6 -> 0 // sleeping
            frac >= 1f -> 3
            frac >= 0.5f -> 2
            frac >= 0.15f || hour < 10 -> 1
            else -> -1 // hungry
        }
        val level = 1 + t.goalDays
        mono("PIXEL · LV $level", p, p + 10)
        val cell = min(7f, (vw - 2 * p) / 12f)
        val w = 10 * cell
        val x0 = vw / 2 - w / 2
        val bob = if (ZonedDateTime.now().minute % 2 == 0) 0f else -2f
        val y0 = p + 22 + bob
        petBody.forEachIndexed { r, rowStr ->
            rowStr.forEachIndexed { c, ch ->
                val x = x0 + c * cell + cell / 2
                val y = y0 + r * cell + cell / 2
                when (ch) {
                    '1' -> dot(x, y, cell * 0.42f, C.PAPER)
                    'E' -> when (mood) {
                        0 -> line(x - cell * 0.35f, y, x + cell * 0.35f, y, cell * 0.3f, C.INK) // eyes closed
                        -1 -> dot(x, y + cell * 0.15f, cell * 0.25f, C.INK)
                        else -> dot(x, y, cell * 0.32f, C.INK)
                    }
                    'M' -> dot(x, y, cell * 0.42f, if (mood >= 2) C.RED else C.PAPER)
                }
            }
        }
        when (mood) {
            0 -> { mono("z", x0 + w + 4, y0 + 6, C.CYAN, alpha = 255); mono("Z", x0 + w + 10, y0 - 2, C.CYAN, alpha = 255) }
            3 -> { dot(x0 + w + 6, y0 + 4, 2.4f, C.RED); dot(x0 + w + 12, y0 - 3, 1.8f, C.RED); dot(x0 - 6, y0 + 2, 2f, C.RED) }
        }
        val (line1, line2) = when (mood) {
            0 -> "Sleeping" to "Walked ${num(t.steps)} today"
            3 -> "Full and happy" to "Goal reached · LV up at midnight"
            2 -> "Happy" to "${num(goal - t.steps)} steps to feed"
            1 -> "Okay" to "${num(goal - t.steps)} steps to feed"
            else -> "Hungry" to "Take me for a walk"
        }
        text(line1, p, vh - p - 14, 12.5f, font = Font.MED, color = if (mood == -1) C.ORANGE else C.PAPER)
        text(line2, p, vh - p, 10.5f, alpha = 150, maxW = vw - 2 * p)
    }

    // 17 --------------------------------------------------------- screen time (U4)
    private fun screen(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_LIGHT)
        mono("SCREEN TODAY", p, p + 10)
        val s = Usage.screen(ctx) ?: return@with run {
            text("Allow usage access", p, vh - p - 16, 13f, font = Font.MED)
            text("Open Dot Widgets", p, vh - p, 11f, alpha = 140)
        }
        val mins = (s.onMs / 60_000).toInt()
        val txt = if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
        text(txt, p - 1, p + 44, fit(txt, vw - 2 * p, 30f), font = Font.MED)
        text("${s.unlocks} unlocks", p, p + 62, 11.5f, alpha = 140)
        val hourNow = ZonedDateTime.now().hour
        val step = (vw - 2 * p) / 24
        for (h in 0 until 24) {
            val dots = (s.hourlyMin[h] / 10f).roundToInt().coerceIn(0, 6)
            val col = if (h == hourNow) C.RED else C.INK
            if (dots == 0) dot(p + step * h + step / 2, vh - p, 1.2f, C.OFF_LIGHT)
            for (k in 0 until dots) dot(p + step * h + step / 2, vh - p - k * 3.6f, 1.35f, col)
        }
    }

    // 18 --------------------------------------------------------- charging meter (U5)
    private fun charging(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_DARK)
        val b = Battery.read(ctx)
        val fast = (b.watts ?: 0f) >= 15f
        val (label, col) = when {
            b.plugged && b.level >= 100 -> "● FULL" to C.GREEN
            b.plugged && fast -> "● FAST CHARGING" to C.GREEN
            b.plugged -> "● CHARGING" to C.GREEN
            else -> "● ON BATTERY" to C.GREY
        }
        mono(label, p, p + 10, col, alpha = 255, maxW = vw - 2 * p - 14)
        if (b.plugged) icon("bolt", vw - p - 4, p + 6, 12f, C.GREEN)
        val lvl = "${b.level}%"
        text(lvl, p - 1, p + 50, fit(lvl, vw - 2 * p, 36f), font = Font.MED)
        val parts = mutableListOf<String>()
        if (b.plugged) {
            b.fullInMin?.let { parts += if (it >= 60) "Full in ${it / 60}h ${it % 60}m" else "Full in $it min" }
            b.watts?.let { parts += String.format(Locale.US, "%.0f W", it) }
        } else parts += "Unplugged"
        parts += "${b.tempC.roundToInt()}°C"
        text(parts.joinToString(" · "), p, p + 68, 11f, alpha = 160, maxW = vw - 2 * p,
            color = if (b.tempC >= 42f) C.ORANGE else C.PAPER)
        // segmented pill bar
        val n = 20
        val g = 2f
        val segW = (vw - 2 * p - g * (n - 1)) / n
        val on = (b.level / 5f).roundToInt()
        for (i in 0 until n) {
            val x = p + i * (segW + g)
            rect(x, vh - p - 10, x + segW, vh - p, when {
                i < on -> if (b.level <= 15) C.RED else C.GREEN
                i == on && b.plugged -> C.PAPER
                else -> C.OFF_DARK
            }, segW / 2)
        }
    }

    // 19 --------------------------------------------------------- network speed (U6)
    private fun netSpeed(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_LIGHT)
        val s = Net.read(ctx)
        mono(s.label, p, p + 10, maxW = vw - 2 * p)
        fun fmt(bps: Double): Pair<String, String> {
            val mbit = bps * 8 / 1e6
            return when {
                mbit >= 10 -> String.format(Locale.US, "%.0f", mbit) to "Mb/s"
                mbit >= 1 -> String.format(Locale.US, "%.1f", mbit) to "Mb/s"
                else -> String.format(Locale.US, "%.0f", bps * 8 / 1e3) to "kb/s"
            }
        }
        val (d, du) = fmt(s.downBps)
        val (u, uu) = fmt(s.upBps)
        icon("down", p + 4, p + 30, 10f, C.GREEN)
        val w1 = text(d, p + 13, p + 36, 22f, font = Font.MED)
        text(" $du", p + 13 + w1, p + 36, 11f, alpha = 140)
        icon("up", p + 4, p + 56, 10f, C.BLUE)
        val w2 = text(u, p + 13, p + 62, 22f, font = Font.MED)
        text(" $uu", p + 13 + w2, p + 62, 11f, alpha = 140)
        // last 30 minutes of download speed
        val hist = s.history.takeLast(30)
        if (hist.isNotEmpty()) {
            val peak = max(hist.max(), 1.0)
            val step = (vw - 2 * p) / 30
            hist.forEachIndexed { i, v ->
                val dots = 1 + (v / peak * 5).roundToInt()
                val x = p + step * (30 - hist.size + i) + step / 2
                for (k in 0 until dots) dot(x, vh - p - 12 - k * 3f, 1.1f, if (i >= hist.size - 3) C.GREEN else C.GREY)
            }
        }
        mono(if (s.live) "LIVE · TAP AGAIN" else "AVG LAST MIN · TAP FOR LIVE", p, vh - p + 1, size = 8f, alpha = 120)
    }

    // 20 --------------------------------------------------------- dot compass (U7)
    private fun compass(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_DARK)
        if (!Compass.available(ctx)) return@with text("No compass sensor", p, vh / 2, 13f)
        val prefs = Prefs(ctx)
        val at = prefs.long("cmp_at", 0L)
        val deg = prefs.float("cmp_deg", 0f)
        val cx = vw / 2
        val cy = vh / 2 - 8
        val r = min(vw, vh) * 0.32f
        for (i in 0 until 36) {
            val a = i * 10.0 * Math.PI / 180
            val big = i % 9 == 0
            dot(cx + r * sin(a).toFloat(), cy - r * cos(a).toFloat(), if (big) 2.6f else 1.4f, if (i == 0) C.RED else C.GREY)
        }
        mono("N", cx, cy - r + 14, C.RED, Align.CENTER, alpha = 255, size = 9f)
        if (at > 0) {
            val a = deg * Math.PI / 180
            for (j in 1..7) {
                val d = j * (r - 12) / 7f
                dot(cx + d * sin(a).toFloat(), cy - d * cos(a).toFloat(), if (j == 7) 3f else 2.1f, if (j >= 5) C.RED else C.PAPER)
            }
            dot(cx, cy, 3.2f, C.PAPER)
            val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            val dir = dirs[(((deg + 22.5f) % 360) / 45).toInt()]
            mono("$dir ${deg.roundToInt()}° · ${hm(ctx, at).uppercase()}", cx, vh - p, align = Align.CENTER, alpha = 200)
        } else {
            dot(cx, cy, 3.2f, C.PAPER)
            mono("TAP TO READ", cx, vh - p, align = Align.CENTER, alpha = 200)
        }
    }

    // 21 --------------------------------------------------------- year in weeks (U8)
    private fun weeks(ctx: Context, pen: Pen) = with(pen) {
        card(C.CARD_DARK)
        val today = LocalDate.now()
        val week = ((today.dayOfYear - 1) / 7 + 1).coerceAtMost(52)
        mono("${today.year} IN WEEKS", p, p + 10)
        mono("${52 - week} LEFT", vw - p, p + 10, C.RED, Align.RIGHT, alpha = 255)
        val cols = 13
        val rowsN = 4
        val gw = (vw - 2 * p) / cols
        val gh = min(gw, (vh - 2 * p - 46) / rowsN)
        val r = min(gw, gh) * 0.32f
        val top = p + 22
        for (i in 0 until 52) {
            val x = p + (i % cols) * gw + gw / 2
            val y = top + (i / cols) * gh + gh / 2
            dot(x, y, r, when {
                i + 1 < week -> C.PAPER
                i + 1 == week -> C.RED
                else -> C.OFF_DARK
            })
        }
        val pct = (today.dayOfYear * 100f / today.lengthOfYear()).roundToInt()
        text("Week $week of 52", p, vh - p, 12f, font = Font.MED)
        text("$pct%", vw - p, vh - p, 12f, align = Align.RIGHT, alpha = 150)
    }
}
