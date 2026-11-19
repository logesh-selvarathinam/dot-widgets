package com.nandhini.dotwidgets

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.NetworkStatsManager
import android.app.usage.StorageStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.os.storage.StorageManager
import android.provider.CalendarContract
import android.telephony.TelephonyManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

fun Context.has(perm: String) = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

// ------------------------------------------------------------------ steps

object Steps {
    data class Today(val steps: Int, val activeHours: Int, val hourly: IntArray, val yesterday: Int, val goalDays: Int)

    private fun readSensor(ctx: Context): Float? {
        if (!ctx.has(Manifest.permission.ACTIVITY_RECOGNITION)) return null
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        val latch = CountDownLatch(1)
        var value: Float? = null
        val thread = HandlerThread("steps").apply { start() }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                value = e.values[0]
                latch.countDown()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL, Handler(thread.looper))
        latch.await(3, TimeUnit.SECONDS)
        sm.unregisterListener(listener)
        thread.quitSafely()
        return value
    }

    /** Reads the hardware step counter and works out today's steps. Call from a background thread. */
    fun sample(ctx: Context) {
        val v = readSensor(ctx) ?: return
        val p = Prefs(ctx)
        val today = LocalDate.now().toString()
        val lastDay = p.str("st_day", "")
        var base = p.float("st_base", -1f)
        val last = p.float("st_last", -1f)
        var hourly = p.str("st_hours", "").split(",").mapNotNull { it.toIntOrNull() }.toMutableList()
        if (hourly.size != 24) hourly = MutableList(24) { 0 }

        val hasBase = p.sp.contains("st_base")
        val sameDay = lastDay == today && hasBase
        val prevToday = if (sameDay && last >= 0f) (last - base).coerceAtLeast(0f) else 0f

        if (!sameDay) {
            if (lastDay.isNotEmpty() && hasBase && last >= 0f) {
                val y = (last - base).toInt().coerceAtLeast(0)
                p.put("st_yday", y)
                if (y >= p.stepGoal) p.put("pet_days", p.int("pet_days", 0) + 1)
            }
            // steps walked between the last reading and now count towards today
            base = if (hasBase && last >= 0f && v >= last) last else v
            hourly = MutableList(24) { 0 }
            p.put("st_day", today)
        } else if (last >= 0f && v < last) {
            // phone restarted, the counter went back to zero: keep today's progress
            base -= last
        }
        val nowToday = (v - base).coerceAtLeast(0f)
        val delta = (nowToday - prevToday).toInt().coerceAtLeast(0)
        val hour = ZonedDateTime.now().hour
        hourly[hour] = hourly[hour] + delta

        p.put("st_base", base)
        p.put("st_last", v)
        p.put("st_today", nowToday.toInt())
        p.put("st_hours", hourly.joinToString(","))
    }

    fun today(ctx: Context): Today? {
        if (!ctx.has(Manifest.permission.ACTIVITY_RECOGNITION)) return null
        val p = Prefs(ctx)
        val isToday = p.str("st_day", "") == LocalDate.now().toString()
        val steps = if (isToday) p.int("st_today", 0) else 0
        val hourly = if (isToday) p.str("st_hours", "").split(",").mapNotNull { it.toIntOrNull() } else emptyList()
        val h = IntArray(24) { hourly.getOrElse(it) { 0 } }
        return Today(steps, h.count { it >= 250 }, h, p.int("st_yday", 0), p.int("pet_days", 0))
    }
}

// ------------------------------------------------------------------ battery

object Battery {
    data class State(val level: Int, val charging: Boolean, val plugged: Boolean, val tempC: Float, val watts: Float?, val fullInMin: Int?)

    fun read(ctx: Context): State {
        val i: Intent? = ctx.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val level = if (lvl >= 0 && scale > 0) lvl * 100 / scale else bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val plugged = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val mv = i?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        var watts: Float? = null
        val cur = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: Int.MIN_VALUE
        if (cur != Int.MIN_VALUE && cur != 0 && mv > 0) {
            var amps = abs(cur) / 1_000_000f
            if (amps < 0.01f) amps = abs(cur) / 1000f // some phones report mA instead of µA
            val w = amps * mv / 1000f
            if (w in 0.2f..150f) watts = w
        }
        val rem = bm?.computeChargeTimeRemaining() ?: -1L
        val fullIn = if (plugged && rem > 0) (rem / 60000L).toInt() else null
        return State(level.coerceIn(0, 100), charging, plugged, temp, watts, fullIn)
    }
}

// ------------------------------------------------------------------ weather (Open-Meteo, no key needed)

object Weather {
    data class Now(val temp: Int, val code: Int, val hi: Int, val lo: Int, val isDay: Boolean, val hourly: List<Float>, val at: Long)

    fun cached(ctx: Context): Now? {
        val s = Prefs(ctx).str("wx_json", "")
        if (s.isEmpty()) return null
        return try {
            val o = JSONObject(s)
            val h = o.getJSONArray("h")
            Now(o.getInt("t"), o.getInt("c"), o.getInt("hi"), o.getInt("lo"), o.getBoolean("d"),
                List(h.length()) { h.getDouble(it).toFloat() }, o.getLong("at"))
        } catch (e: Exception) { null }
    }

    fun refreshIfStale(ctx: Context, maxAgeMin: Int = 30) {
        val at = Prefs(ctx).long("wx_at", 0L)
        if (System.currentTimeMillis() - at > maxAgeMin * 60_000L) refresh(ctx)
    }

    /** Network call: background thread only. */
    fun refresh(ctx: Context): Boolean {
        val p = Prefs(ctx)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
            "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto"
        return try {
            val con = URL(url).openConnection() as HttpURLConnection
            con.connectTimeout = 8000
            con.readTimeout = 8000
            val body = con.inputStream.bufferedReader().use { it.readText() }
            con.disconnect()
            val j = JSONObject(body)
            val cur = j.getJSONObject("current")
            val daily = j.getJSONObject("daily")
            val times = j.getJSONObject("hourly").getJSONArray("time")
            val temps = j.getJSONObject("hourly").getJSONArray("temperature_2m")
            val nowIso = cur.getString("time").substring(0, 13) // yyyy-MM-ddTHH
            var start = 0
            for (k in 0 until times.length()) if (times.getString(k).startsWith(nowIso)) { start = k; break }
            val h = JSONArray()
            for (k in start until minOf(start + 8, temps.length())) h.put(temps.getDouble(k))
            val o = JSONObject()
                .put("t", Math.round(cur.getDouble("temperature_2m")).toInt())
                .put("c", cur.getInt("weather_code"))
                .put("d", cur.optInt("is_day", 1) == 1)
                .put("hi", Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt())
                .put("lo", Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt())
                .put("h", h)
                .put("at", System.currentTimeMillis())
            p.put("wx_json", o.toString())
            p.put("wx_at", System.currentTimeMillis())
            true
        } catch (e: Exception) { false }
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        in 85..86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> "—"
    }
}

// ------------------------------------------------------------------ calendar

object Cal {
    data class Event(val title: String, val begin: Long, val end: Long, val allDay: Boolean, val color: Int)

    fun next(ctx: Context): Event? {
        if (!ctx.has(Manifest.permission.READ_CALENDAR)) return null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, now - 86_400_000L)
            android.content.ContentUris.appendId(it, now + 3 * 86_400_000L)
        }.build()
        val proj = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR
        )
        return try {
            ctx.contentResolver.query(uri, proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                var allDayFallback: Event? = null
                while (c.moveToNext()) {
                    val e = Event(c.getString(0) ?: "(No title)", c.getLong(1), c.getLong(2), c.getInt(3) == 1, c.getInt(4))
                    if (e.end <= now) continue
                    if (e.allDay) { if (allDayFallback == null) allDayFallback = e; continue }
                    return@use e
                }
                allDayFallback
            }
        } catch (e: Exception) { null }
    }
}

// ------------------------------------------------------------------ media (needs notification access)

object Media {
    data class Track(val title: String, val artist: String, val app: String, val pkg: String, val playing: Boolean,
                     val art: Bitmap?, val progress: Float?)

    fun controller(ctx: Context): MediaController? {
        if (!NotifService.isEnabled(ctx)) return null
        return try {
            val msm = ctx.getSystemService(MediaSessionManager::class.java)
            val list = msm.getActiveSessions(ComponentName(ctx, NotifService::class.java))
            list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        } catch (e: Exception) { null }
    }

    fun track(ctx: Context): Track? {
        val c = controller(ctx) ?: return null
        val md = c.metadata ?: return null
        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING
        val dur = md.getLong(MediaMetadata.METADATA_KEY_DURATION)
        var prog: Float? = null
        if (st != null && dur > 0) {
            var pos = st.position
            if (playing && st.lastPositionUpdateTime > 0) {
                pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
            }
            prog = (pos.toFloat() / dur).coerceIn(0f, 1f)
        }
        val app = try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(c.packageName, 0)).toString()
        } catch (e: Exception) { c.packageName }
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
        return Track(
            md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown",
            md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            app, c.packageName, playing, art, prog
        )
    }

    fun control(ctx: Context, what: String) {
        val c = controller(ctx) ?: return
        val t = c.transportControls
        when (what) {
            "prev" -> t.skipToPrevious()
            "next" -> t.skipToNext()
            "play" -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
        }
    }
}

// ------------------------------------------------------------------ notification counts (filled by NotifService)

object Notifs {
    data class App(val pkg: String, val label: String, val count: Int)

    fun read(ctx: Context): List<App>? {
        if (!NotifService.isEnabled(ctx)) return null
        val s = Prefs(ctx).str("nd_json", "[]")
        return try {
            val a = JSONArray(s)
            List(a.length()) { val o = a.getJSONObject(it); App(o.getString("p"), o.getString("l"), o.getInt("n")) }
        } catch (e: Exception) { emptyList() }
    }
}

// ------------------------------------------------------------------ usage access: screen time + mobile data

object Usage {
    data class Screen(val onMs: Long, val unlocks: Int, val hourlyMin: IntArray)

    fun granted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    fun screen(ctx: Context): Screen? {
        if (!granted(ctx)) return null
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return null
        val start = startOfToday()
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(start, now) ?: return null
        val e = UsageEvents.Event()
        val hourly = LongArray(24)
        var onSince: Long? = null
        var first = true
        var total = 0L
        var unlocks = 0
        fun addSpan(a: Long, b: Long) {
            if (b <= a) return
            total += b - a
            var t = a
            while (t < b) {
                val h = ((t - start) / 3_600_000L).toInt().coerceIn(0, 23)
                val hourEnd = start + (h + 1) * 3_600_000L
                val segEnd = minOf(b, hourEnd)
                hourly[h] += segEnd - t
                t = segEnd
            }
        }
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> { if (onSince == null) onSince = e.timeStamp; first = false }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    val s = onSince ?: if (first) start else null
                    if (s != null) addSpan(s, e.timeStamp)
                    onSince = null
                    first = false
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
            }
        }
        onSince?.let { addSpan(it, now) }
        return Screen(total, unlocks, IntArray(24) { (hourly[it] / 60_000L).toInt() })
    }

    /** Mobile data used today in bytes, or null without usage access. */
    @Suppress("DEPRECATION")
    fun mobileBytesToday(ctx: Context): Long? {
        if (!granted(ctx)) return null
        return try {
            val nsm = ctx.getSystemService(NetworkStatsManager::class.java)
            val b = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, startOfToday(), System.currentTimeMillis())
            b.rxBytes + b.txBytes
        } catch (e: Exception) { null }
    }

    data class Storage(val usedBytes: Long, val totalBytes: Long)

    fun storage(ctx: Context): Storage? = try {
        val ssm = ctx.getSystemService(StorageStatsManager::class.java)
        val total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
        val free = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
        Storage(total - free, total)
    } catch (e: Exception) { null }
}

// ------------------------------------------------------------------ network speed

object Net {
    data class Speed(val downBps: Double, val upBps: Double, val history: List<Double>, val live: Boolean, val label: String)

    private fun bytes() = Pair(TrafficStats.getTotalRxBytes(), TrafficStats.getTotalTxBytes())

    /** Called each minute: average speed since the last call. */
    fun sample(ctx: Context) {
        val p = Prefs(ctx)
        val (rx, tx) = bytes()
        val t = SystemClock.elapsedRealtime()
        val lrx = p.long("nt_rx", -1L)
        val ltx = p.long("nt_tx", -1L)
        val lt = p.long("nt_t", -1L)
        if (lrx >= 0 && lt in 1 until t && rx >= lrx && tx >= ltx) {
            val secs = (t - lt) / 1000.0
            store(p, (rx - lrx) / secs, (tx - ltx) / secs, false)
        }
        p.put("nt_rx", rx); p.put("nt_tx", tx); p.put("nt_t", t)
    }

    /** Tap on the widget: measure for 1.5 s. Background thread only. */
    fun live(ctx: Context) {
        val (rx1, tx1) = bytes()
        val t1 = SystemClock.elapsedRealtime()
        Thread.sleep(1500)
        val (rx2, tx2) = bytes()
        val secs = (SystemClock.elapsedRealtime() - t1) / 1000.0
        store(Prefs(ctx), (rx2 - rx1).coerceAtLeast(0) / secs, (tx2 - tx1).coerceAtLeast(0) / secs, true)
    }

    private fun store(p: Prefs, down: Double, up: Double, live: Boolean) {
        val hist = p.str("nt_hist", "").split(",").mapNotNull { it.toDoubleOrNull() }.takeLast(29) + down
        p.put("nt_hist", hist.joinToString(",") { "%.0f".format(java.util.Locale.US, it) })
        p.put("nt_down", down.toFloat()); p.put("nt_up", up.toFloat())
        p.put("nt_live", live); p.put("nt_at", System.currentTimeMillis())
    }

    fun read(ctx: Context): Speed {
        val p = Prefs(ctx)
        val hist = p.str("nt_hist", "").split(",").mapNotNull { it.toDoubleOrNull() }
        val liveFresh = p.bool("nt_live", false) && System.currentTimeMillis() - p.long("nt_at", 0) < 90_000
        return Speed(p.float("nt_down", 0f).toDouble(), p.float("nt_up", 0f).toDouble(), hist, liveFresh, label(ctx))
    }

    private fun label(ctx: Context): String {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "OFFLINE"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WI-FI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val op = ctx.getSystemService(TelephonyManager::class.java)?.networkOperatorName.orEmpty()
                if (op.isBlank()) "MOBILE" else "MOBILE · ${op.uppercase()}"
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "CONNECTED"
        }
    }

    fun wifiOn(ctx: Context): Boolean = try {
        ctx.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled
    } catch (e: Exception) { false }
}

// ------------------------------------------------------------------ torch, DND

object Torch {
    private fun cameraId(cm: CameraManager): String? = cm.cameraIdList.firstOrNull {
        cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    fun isOn(ctx: Context) = Prefs(ctx).bool("torch_on", false)

    fun toggle(ctx: Context) {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = try { cameraId(cm) } catch (e: Exception) { null } ?: return
        // ask the system for the real state first (the user may have used quick settings)
        var real: Boolean? = null
        val latch = CountDownLatch(1)
        val thread = HandlerThread("torch").apply { start() }
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id && real == null) { real = enabled; latch.countDown() }
            }
        }
        cm.registerTorchCallback(cb, Handler(thread.looper))
        latch.await(600, TimeUnit.MILLISECONDS)
        cm.unregisterTorchCallback(cb)
        thread.quitSafely()
        val target = !(real ?: isOn(ctx))
        try {
            cm.setTorchMode(id, target)
            Prefs(ctx).put("torch_on", target)
        } catch (e: Exception) { }
    }
}

object Dnd {
    fun granted(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
    fun isOn(ctx: Context) =
        ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

    fun toggle(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return
        nm.setInterruptionFilter(
            if (isOn(ctx)) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        )
    }
}

// ------------------------------------------------------------------ compass

object Compass {
    fun available(ctx: Context): Boolean {
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
            (sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null && sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null)
    }

    /** Reads the heading for about a second. Background thread only. */
    fun sample(ctx: Context) {
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return
        val rv = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val mag = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val thread = HandlerThread("compass").apply { start() }
        val handler = Handler(thread.looper)
        val rot = FloatArray(9)
        val orient = FloatArray(3)
        var g: FloatArray? = null
        var m: FloatArray? = null
        var sx = 0.0
        var sy = 0.0
        var n = 0
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                var ok = false
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> { SensorManager.getRotationMatrixFromVector(rot, e.values); ok = true }
                    Sensor.TYPE_ACCELEROMETER -> g = e.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> m = e.values.clone()
                }
                if (!ok && g != null && m != null) ok = SensorManager.getRotationMatrix(rot, null, g, m)
                if (ok) {
                    SensorManager.getOrientation(rot, orient)
                    sx += cos(orient[0].toDouble()); sy += sin(orient[0].toDouble()); n++
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (rv != null) sm.registerListener(l, rv, SensorManager.SENSOR_DELAY_GAME, handler)
        else if (acc != null && mag != null) {
            sm.registerListener(l, acc, SensorManager.SENSOR_DELAY_GAME, handler)
            sm.registerListener(l, mag, SensorManager.SENSOR_DELAY_GAME, handler)
        }
        Thread.sleep(1200)
        sm.unregisterListener(l)
        thread.quitSafely()
        if (n > 0) {
            var deg = Math.toDegrees(atan2(sy, sx)).toFloat()
            if (deg < 0) deg += 360f
            val p = Prefs(ctx)
            p.put("cmp_deg", deg)
            p.put("cmp_at", System.currentTimeMillis())
        }
    }
}

// ------------------------------------------------------------------ sun and Rahu kalam

object Sun {
    data class Day(val rise: Long, val set: Long)

    /** Sunrise equation (accurate to a minute or two). */
    fun times(date: LocalDate, lat: Double, lon: Double): Day? {
        val rad = PI / 180.0
        val n = date.toEpochDay() - 10957.0 + 0.0008 // days since 2000-01-01 12:00 TT
        val jStar = n - lon / 360.0
        val m = ((357.5291 + 0.98560028 * jStar) % 360 + 360) % 360
        val c = 1.9148 * sin(m * rad) + 0.0200 * sin(2 * m * rad) + 0.0003 * sin(3 * m * rad)
        val lambda = ((m + c + 180 + 102.9372) % 360 + 360) % 360
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(m * rad) - 0.0069 * sin(2 * lambda * rad)
        val decl = asin(sin(lambda * rad) * sin(23.4397 * rad))
        val cosW = (sin(-0.833 * rad) - sin(lat * rad) * sin(decl)) / (cos(lat * rad) * cos(decl))
        if (cosW < -1 || cosW > 1) return null // polar day or night
        val w = acos(cosW) / rad
        fun toMs(j: Double) = ((j - 2440587.5) * 86_400_000.0).toLong()
        return Day(toMs(jTransit - w / 360.0), toMs(jTransit + w / 360.0))
    }

    fun today(ctx: Context): Day? {
        val p = Prefs(ctx)
        return times(LocalDate.now(), p.lat, p.lon)
    }

    data class Span(val name: String, val start: Long, val end: Long, val color: Int)

    /** Rahu kalam, Yamagandam, Gulikai and Abhijit muhurta for today. */
    fun periods(ctx: Context): List<Span>? {
        val d = today(ctx) ?: return null
        val part = (d.set - d.rise) / 8
        // index by day of week, Sunday = 0. Values are 1-based parts of the day.
        val dow = LocalDate.now().dayOfWeek.value % 7
        val rahu = intArrayOf(8, 2, 7, 5, 6, 4, 3)[dow]
        val yama = intArrayOf(5, 4, 3, 2, 1, 7, 6)[dow]
        val guli = intArrayOf(7, 6, 5, 4, 3, 2, 1)[dow]
        fun span(name: String, i: Int, color: Int) = Span(name, d.rise + (i - 1) * part, d.rise + i * part, color)
        val mid = (d.rise + d.set) / 2
        val half = (d.set - d.rise) / 30
        return listOf(
            span("Rahu", rahu, C.RED),
            span("Yama", yama, C.ORANGE),
            span("Gulikai", guli, C.GREY),
            Span("Abhijit", mid - half, mid + half, C.GREEN),
        )
    }

    fun nowMs(): Long = Instant.now().toEpochMilli()
}
