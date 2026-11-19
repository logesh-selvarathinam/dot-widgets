package com.nandhini.dotwidgets

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.app.Notification
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs work off the main thread and keeps a receiver alive until it finishes. */
val bg = Executors.newSingleThreadExecutor()

fun BroadcastReceiver.async(block: () -> Unit) {
    val pending = goAsync()
    bg.execute {
        try { block() } catch (e: Exception) { } finally { pending.finish() }
    }
}

// ------------------------------------------------------------------ widget providers

abstract class BaseWidget(private val kind: Kind) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        async {
            Updater.render(ctx, kind, ids)
            Ticker.ensure(ctx)
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, options: Bundle) {
        async { Updater.render(ctx, kind, intArrayOf(id)) }
    }

    override fun onEnabled(ctx: Context) {
        async {
            Ticker.ensure(ctx)
            Refresh.forKind(ctx, kind)
            Updater.update(ctx, kind)
        }
    }
}

object Updater {
    private val main = Handler(Looper.getMainLooper())
    private val pendingKinds = mutableSetOf<Kind>()

    fun ids(ctx: Context, kind: Kind): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, kind.provider))

    fun placedKinds(ctx: Context) = Kind.entries.filter { ids(ctx, it).isNotEmpty() }

    fun updateAll(ctx: Context) = Kind.entries.forEach { update(ctx, it) }

    fun update(ctx: Context, kind: Kind) {
        val ids = ids(ctx, kind)
        if (ids.isNotEmpty()) render(ctx, kind, ids)
    }

    /** Coalesces bursts of updates (for example many notifications arriving at once). */
    fun updateSoon(ctx: Context, vararg kinds: Kind) {
        synchronized(pendingKinds) {
            val wasEmpty = pendingKinds.isEmpty()
            pendingKinds.addAll(kinds)
            if (!wasEmpty) return
        }
        main.postDelayed({
            val list = synchronized(pendingKinds) { pendingKinds.toList().also { pendingKinds.clear() } }
            bg.execute { list.forEach { try { update(ctx, it) } catch (e: Exception) { } } }
        }, 1200)
    }

    fun render(ctx: Context, kind: Kind, ids: IntArray) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val density = ctx.resources.displayMetrics.density
        for (id in ids) {
            val o = mgr.getAppWidgetOptions(id)
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 170
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 170
            // keep bitmaps inside the RemoteViews memory limit
            val scale = minOf(density, 1100f / maxOf(wDp, hDp))
            val w = (wDp * scale).toInt().coerceAtLeast(60)
            val h = (hDp * scale).toInt().coerceAtLeast(60)
            val bmp = try { Renderers.draw(ctx, kind, w, h) } catch (e: Exception) { Renderers.error(ctx, w, h, e) }
            val rv = RemoteViews(ctx.packageName, when (kind.layout) {
                Layout.SINGLE -> R.layout.w_single
                Layout.GRID4 -> R.layout.w_grid4
                Layout.MEDIA -> R.layout.w_media
                Layout.FOCUS -> R.layout.w_focus
            })
            rv.setImageViewBitmap(R.id.img, bmp)
            rv.setContentDescription(R.id.img, kind.label)
            Actions.bind(ctx, kind, rv)
            try { mgr.updateAppWidget(id, rv) } catch (e: Exception) { }
        }
    }
}

// ------------------------------------------------------------------ taps

object Actions {
    const val TORCH = "torch"
    const val DND = "dnd"
    const val MEDIA = "media"
    const val FOCUS_TOGGLE = "focus_toggle"
    const val FOCUS_RESET = "focus_reset"
    const val FOCUS_DONE = "focus_done"
    const val REFRESH = "refresh"
    const val COMPASS = "compass"
    const val NET_LIVE = "net_live"

    private fun broadcast(ctx: Context, action: String, code: Int, extra: String? = null): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).setAction(action)
        if (extra != null) i.putExtra("x", extra)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun activity(ctx: Context, intent: Intent, code: Int): PendingIntent {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved = intent.resolveActivity(ctx.packageManager) != null
        val target = if (resolved) intent else Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(ctx, code, target, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun app(ctx: Context, code: Int) = activity(ctx, Intent(ctx, MainActivity::class.java), code)

    fun bind(ctx: Context, kind: Kind, rv: RemoteViews) {
        val base = kind.ordinal * 10
        val main: PendingIntent = when (kind) {
            Kind.CLOCK, Kind.WORLD_CLOCK -> activity(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS), base)
            Kind.BATTERY, Kind.CHARGING -> activity(ctx, Intent(Intent.ACTION_POWER_USAGE_SUMMARY), base)
            Kind.CALENDAR -> activity(ctx, Intent(Intent.ACTION_VIEW,
                CalendarContract.CONTENT_URI.buildUpon().appendPath("time").appendPath(System.currentTimeMillis().toString()).build()), base)
            Kind.DATA -> activity(ctx, Intent(Settings.ACTION_DATA_USAGE_SETTINGS), base)
            Kind.TORCH -> broadcast(ctx, TORCH, base)
            Kind.COMPASS -> broadcast(ctx, COMPASS, base)
            Kind.NETSPEED -> broadcast(ctx, NET_LIVE, base)
            Kind.WEATHER, Kind.STEPS, Kind.ACTIVITY, Kind.PET, Kind.NOTIFS, Kind.SCREEN ->
                if (needsSetup(ctx, kind)) app(ctx, base) else broadcast(ctx, REFRESH, base, kind.name)
            else -> app(ctx, base)
        }
        rv.setOnClickPendingIntent(R.id.img, main)

        when (kind.layout) {
            Layout.GRID4 -> {
                rv.setOnClickPendingIntent(R.id.z1, broadcast(ctx, TORCH, base + 1))
                rv.setOnClickPendingIntent(R.id.z2, activity(ctx, Intent(Settings.Panel.ACTION_WIFI), base + 2))
                rv.setOnClickPendingIntent(R.id.z3,
                    if (Dnd.granted(ctx)) broadcast(ctx, DND, base + 3)
                    else activity(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), base + 3))
                rv.setOnClickPendingIntent(R.id.z4, activity(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), base + 4))
            }
            Layout.MEDIA -> {
                if (!NotifService.isEnabled(ctx)) {
                    val pi = activity(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS), base + 1)
                    listOf(R.id.z1, R.id.z2, R.id.z3, R.id.z4).forEach { rv.setOnClickPendingIntent(it, pi) }
                } else {
                    rv.setOnClickPendingIntent(R.id.z1, broadcast(ctx, MEDIA, base + 1, "prev"))
                    rv.setOnClickPendingIntent(R.id.z2, broadcast(ctx, MEDIA, base + 2, "play"))
                    rv.setOnClickPendingIntent(R.id.z3, broadcast(ctx, MEDIA, base + 3, "next"))
                    val pkg = Media.controller(ctx)?.packageName
                    val launch = pkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) }
                    rv.setOnClickPendingIntent(R.id.z4, if (launch != null) activity(ctx, launch, base + 4) else app(ctx, base + 4))
                }
            }
            Layout.FOCUS -> {
                rv.setOnClickPendingIntent(R.id.z1, broadcast(ctx, FOCUS_TOGGLE, base + 1))
                rv.setOnClickPendingIntent(R.id.z3, broadcast(ctx, FOCUS_TOGGLE, base + 3))
                rv.setOnClickPendingIntent(R.id.z2, broadcast(ctx, FOCUS_RESET, base + 2))
            }
            Layout.SINGLE -> {}
        }
    }

    private fun needsSetup(ctx: Context, kind: Kind): Boolean = when (kind) {
        Kind.STEPS, Kind.ACTIVITY, Kind.PET -> !ctx.has(android.Manifest.permission.ACTIVITY_RECOGNITION)
        Kind.NOTIFS -> !NotifService.isEnabled(ctx)
        Kind.SCREEN -> !Usage.granted(ctx)
        else -> false
    }
}

object Refresh {
    /** Fetches fresh data for one widget kind. Background thread only. */
    fun forKind(ctx: Context, kind: Kind) {
        when (kind) {
            Kind.WEATHER -> Weather.refresh(ctx)
            Kind.STEPS, Kind.ACTIVITY, Kind.PET -> Steps.sample(ctx)
            Kind.COMPASS -> Compass.sample(ctx)
            Kind.NETSPEED -> Net.live(ctx)
            else -> {}
        }
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val x = intent.getStringExtra("x")
        async {
            when (intent.action) {
                Actions.TORCH -> { Torch.toggle(ctx); Updater.update(ctx, Kind.TORCH); Updater.update(ctx, Kind.TOGGLES) }
                Actions.DND -> { Dnd.toggle(ctx); Updater.update(ctx, Kind.TOGGLES) }
                Actions.MEDIA -> {
                    Media.control(ctx, x ?: "play")
                    Thread.sleep(500)
                    Updater.update(ctx, Kind.NOW_PLAYING)
                }
                Actions.FOCUS_TOGGLE -> { Focus.toggle(ctx); Updater.update(ctx, Kind.FOCUS) }
                Actions.FOCUS_RESET -> { Focus.reset(ctx); Updater.update(ctx, Kind.FOCUS) }
                Actions.FOCUS_DONE -> { Focus.finish(ctx); Updater.update(ctx, Kind.FOCUS) }
                Actions.COMPASS -> { Compass.sample(ctx); Updater.update(ctx, Kind.COMPASS) }
                Actions.NET_LIVE -> { Net.live(ctx); Updater.update(ctx, Kind.NETSPEED) }
                Actions.REFRESH -> {
                    val kind = x?.let { runCatching { Kind.valueOf(it) }.getOrNull() } ?: return@async
                    Refresh.forKind(ctx, kind)
                    Updater.update(ctx, kind)
                    if (kind == Kind.STEPS || kind == Kind.ACTIVITY || kind == Kind.PET) {
                        Updater.update(ctx, Kind.STEPS); Updater.update(ctx, Kind.ACTIVITY); Updater.update(ctx, Kind.PET)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ the minute tick

object Ticker {
    fun ensure(ctx: Context) {
        schedule(ctx)
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            "dw-refresh", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        )
    }

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, TickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (Updater.placedKinds(ctx).isEmpty()) { am.cancel(pi); return }
        val next = (System.currentTimeMillis() / 60_000L + 1) * 60_000L + 150
        // RTC (not RTC_WAKEUP): the alarm waits while the screen is off, so the phone is never woken up for it
        try {
            if (am.canScheduleExactAlarms()) am.setExact(AlarmManager.RTC, next, pi)
            else am.setWindow(AlarmManager.RTC, next, 20_000L, pi)
        } catch (e: SecurityException) {
            am.setWindow(AlarmManager.RTC, next, 20_000L, pi)
        }
    }

    /** Everything that should happen once a minute. Background thread only. */
    fun tick(ctx: Context) {
        val placed = Updater.placedKinds(ctx).toSet()
        if (placed.any { it == Kind.STEPS || it == Kind.ACTIVITY || it == Kind.PET }) Steps.sample(ctx)
        if (Kind.NETSPEED in placed) Net.sample(ctx)
        if (Kind.WEATHER in placed) Weather.refreshIfStale(ctx)
        Focus.read(ctx).let { if (it.state == Focus.RUNNING && it.leftMs <= 0L) Focus.finish(ctx) }
        placed.forEach { kind ->
            // the compass only changes when tapped
            if (kind != Kind.COMPASS) try { Updater.update(ctx, kind) } catch (e: Exception) { }
        }
    }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Ticker.schedule(ctx)
        async { Ticker.tick(ctx) }
    }
}

class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        async {
            Ticker.ensure(ctx)
            Ticker.tick(ctx)
            Updater.update(ctx, Kind.COMPASS)
        }
    }
}

class RefreshWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        Ticker.schedule(ctx) // restarts the minute tick if the system dropped it
        val placed = Updater.placedKinds(ctx)
        if (placed.any { it == Kind.STEPS || it == Kind.ACTIVITY || it == Kind.PET }) Steps.sample(ctx)
        if (Kind.WEATHER in placed) Weather.refreshIfStale(ctx, 25)
        Updater.updateAll(ctx)
        return Result.success()
    }
}

// ------------------------------------------------------------------ focus timer

object Focus {
    const val IDLE = 0
    const val RUNNING = 1
    const val PAUSED = 2

    data class State(val state: Int, val leftMs: Long, val totalMs: Long, val session: Int)

    fun read(ctx: Context): State {
        val p = Prefs(ctx)
        val total = p.focusMin * 60_000L
        val st = p.int("fc_state", IDLE)
        val left = when (st) {
            RUNNING -> (p.long("fc_end", 0) - System.currentTimeMillis()).coerceAtLeast(0)
            PAUSED -> p.long("fc_left", total)
            else -> total
        }
        return State(st, left, total, p.int("fc_session", 1))
    }

    fun toggle(ctx: Context) {
        val p = Prefs(ctx)
        val s = read(ctx)
        when (s.state) {
            RUNNING -> { p.put("fc_state", PAUSED); p.put("fc_left", s.leftMs); cancelAlarm(ctx) }
            else -> {
                val end = System.currentTimeMillis() + (if (s.state == PAUSED) s.leftMs else s.totalMs)
                p.put("fc_state", RUNNING); p.put("fc_end", end); setAlarm(ctx, end)
            }
        }
    }

    fun reset(ctx: Context) {
        val p = Prefs(ctx)
        p.put("fc_state", IDLE); p.put("fc_session", 1); cancelAlarm(ctx)
    }

    fun finish(ctx: Context) {
        val p = Prefs(ctx)
        if (p.int("fc_state", IDLE) != RUNNING) return // already finished by the alarm or the tick
        cancelAlarm(ctx)
        val session = p.int("fc_session", 1)
        p.put("fc_state", IDLE)
        p.put("fc_session", if (session >= 4) 1 else session + 1)
        notifyDone(ctx, session)
    }

    private fun alarmIntent(ctx: Context) = PendingIntent.getBroadcast(ctx, 2,
        Intent(ctx, ActionReceiver::class.java).setAction(Actions.FOCUS_DONE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun setAlarm(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(ctx))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(ctx))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(ctx))
        }
    }

    private fun cancelAlarm(ctx: Context) = ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx))

    private fun notifyDone(ctx: Context, session: Int) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("focus", "Focus timer", NotificationManager.IMPORTANCE_HIGH))
        if (!ctx.has(android.Manifest.permission.POST_NOTIFICATIONS)) return
        val n = Notification.Builder(ctx, "focus")
            .setSmallIcon(R.drawable.ic_fg)
            .setContentTitle("Focus session $session done")
            .setContentText(if (session >= 4) "Great work. Take a long break." else "Take a 5 minute break.")
            .setAutoCancel(true)
            .build()
        nm.notify(42, n)
    }
}

// ------------------------------------------------------------------ notification listener: unread dots + now playing

class NotifService : NotificationListenerService() {
    private var msm: MediaSessionManager? = null
    private val watched = mutableListOf<MediaController>()
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> watch(list ?: emptyList()) }
    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = Updater.updateSoon(this@NotifService, Kind.NOW_PLAYING)
        override fun onMetadataChanged(metadata: MediaMetadata?) = Updater.updateSoon(this@NotifService, Kind.NOW_PLAYING)
    }

    override fun onListenerConnected() {
        recount()
        try {
            val m = getSystemService(MediaSessionManager::class.java)
            val cn = ComponentName(this, NotifService::class.java)
            m.addOnActiveSessionsChangedListener(sessionsListener, cn)
            watch(m.getActiveSessions(cn))
            msm = m
        } catch (e: Exception) { }
    }

    override fun onListenerDisconnected() {
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (e: Exception) { }
        watched.forEach { it.unregisterCallback(mediaCallback) }
        watched.clear()
    }

    private fun watch(list: List<MediaController>) {
        watched.forEach { it.unregisterCallback(mediaCallback) }
        watched.clear()
        list.forEach { it.registerCallback(mediaCallback, Handler(Looper.getMainLooper())); watched.add(it) }
        Updater.updateSoon(this, Kind.NOW_PLAYING)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val all = try { activeNotifications ?: emptyArray() } catch (e: Exception) { emptyArray() }
        val counts = LinkedHashMap<String, Int>()
        for (n in all) {
            if (n.packageName == packageName || n.isOngoing) continue
            if (n.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            counts[n.packageName] = (counts[n.packageName] ?: 0) + 1
        }
        val arr = JSONArray()
        counts.entries.sortedByDescending { it.value }.take(4).forEach { (pkg, c) ->
            val label = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) { pkg.substringAfterLast('.') }
            arr.put(JSONObject().put("p", pkg).put("l", label).put("n", c))
        }
        Prefs(this).put("nd_json", arr.toString())
        Updater.updateSoon(this, Kind.NOTIFS)
    }

    companion object {
        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(ctx, NotifService::class.java)
            return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
        }
    }
}

fun openUri(ctx: Context, uri: String) = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
