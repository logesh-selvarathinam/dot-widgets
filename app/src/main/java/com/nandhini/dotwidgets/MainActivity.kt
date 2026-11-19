package com.nandhini.dotwidgets

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var list: LinearLayout
    private val prefs by lazy { Prefs(this) }
    private val fields = mutableMapOf<String, EditText>()
    private var available: AppUpdate.Release? = null

    private val bgColor = 0xFF141416.toInt()
    private val cardColor = 0xFF26262A.toInt()
    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(28), px(20), px(40))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(bgColor); addView(list) })
    }

    override fun onResume() {
        super.onResume()
        build()
        Ticker.ensure(this)
        autoCheck()
    }

    private fun px(v: Int) = (v * dp).toInt()

    private fun build() {
        list.removeAllViews()
        fields.clear()
        title("Dot Widgets", 30f)
        note("Add widgets: long-press your home screen → Widgets → Dot Widgets.")
        available?.let { r ->
            button("Update available: ${r.name}. Tap to install", true) { confirmUpdate(r) }
        }

        section("Permissions")
        perm("Steps, activity and pet", "Counts steps with the phone's step sensor",
            has(Manifest.permission.ACTIVITY_RECOGNITION)) { ask(Manifest.permission.ACTIVITY_RECOGNITION) }
        perm("Calendar", "Shows your next event", has(Manifest.permission.READ_CALENDAR)) { ask(Manifest.permission.READ_CALENDAR) }
        perm("Location", "Finds your city for weather, sunrise and Rahu kalam",
            has(Manifest.permission.ACCESS_COARSE_LOCATION)) { ask(Manifest.permission.ACCESS_COARSE_LOCATION) }
        perm("Notifications", "Alerts you when a focus session ends",
            has(Manifest.permission.POST_NOTIFICATIONS)) { ask(Manifest.permission.POST_NOTIFICATIONS) }
        perm("Notification access", "Now playing and unread dots", NotifService.isEnabled(this)) {
            open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        perm("Usage access", "Screen time and mobile data", Usage.granted(this)) {
            open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        perm("Do not disturb access", "Lets the toggle switch DND on and off", Dnd.granted(this)) {
            open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
        val pm = getSystemService(PowerManager::class.java)
        perm("Battery: unrestricted", "Stops OxygenOS from freezing the widgets", pm.isIgnoringBatteryOptimizations(packageName)) {
            @SuppressLint("BatteryLife")
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            open(i)
        }

        section("Your city")
        field("city", "City name", prefs.city, InputType.TYPE_CLASS_TEXT)
        field("lat", "Latitude", prefs.lat.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        field("lon", "Longitude", prefs.lon.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED)
        button("Use my current location", false) { useLocation() }

        section("Steps")
        field("goal", "Daily step goal", prefs.stepGoal.toString(), InputType.TYPE_CLASS_NUMBER)
        field("stride", "Step length in metres", prefs.strideM.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)

        section("World clock")
        field("wlabel", "Second city name", prefs.worldLabel, InputType.TYPE_CLASS_TEXT)
        field("wzone", "Time zone, like America/New_York or Europe/London", prefs.worldZone, InputType.TYPE_CLASS_TEXT)

        section("Data and focus")
        field("data", "Daily mobile data limit in GB", prefs.dataLimitGb.toString(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        field("focus", "Focus session length in minutes", prefs.focusMin.toString(), InputType.TYPE_CLASS_NUMBER)

        button("Save and refresh widgets", true) { save() }
        button("Reset focus timer", false) {
            Focus.reset(this); bg.execute { Updater.update(this, Kind.FOCUS) }
            toast("Focus timer reset")
        }
        note("Tip: weather and Rahu kalam use the city above. Tap the compass and network speed widgets to update them.")

        section("App")
        note("Version ${AppUpdate.currentName(this)}")
        button("Check for updates", false) { checkUpdate(true) }
    }

    // ---- views

    private fun title(s: String, size: Float) = list.addView(TextView(this).apply {
        text = s; textSize = size; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    })

    private fun note(s: String) = list.addView(TextView(this).apply {
        text = s; textSize = 13f; setTextColor(0xFFAAAAAA.toInt()); setPadding(0, px(6), 0, px(6))
    })

    private fun section(s: String) = list.addView(TextView(this).apply {
        text = "● " + s.uppercase(Locale.ENGLISH); textSize = 12f; letterSpacing = 0.1f
        typeface = Typeface.MONOSPACE; setTextColor(0xFFFF453A.toInt()); setPadding(0, px(24), 0, px(8))
    })

    private fun rounded(color: Int, r: Int = 18) = GradientDrawable().apply { setColor(color); cornerRadius = r * dp }

    private fun perm(name: String, why: String, granted: Boolean, onAsk: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(cardColor)
            setPadding(px(16), px(12), px(12), px(12))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply { text = name; textSize = 15f; setTextColor(Color.WHITE) })
        texts.addView(TextView(this).apply { text = why; textSize = 12f; setTextColor(0xFF9A9AA0.toInt()) })
        row.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (granted) {
            row.addView(TextView(this).apply {
                text = "● ON"; typeface = Typeface.MONOSPACE; textSize = 12f; setTextColor(0xFF30D158.toInt()); setPadding(px(8), 0, px(8), 0)
            })
        } else {
            row.addView(Button(this).apply {
                text = "Allow"; isAllCaps = false; setTextColor(Color.BLACK); background = rounded(Color.WHITE, 14)
                minHeight = 0; minimumHeight = 0; setPadding(px(16), px(8), px(16), px(8))
                setOnClickListener { onAsk() }
            })
        }
        list.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = px(8) })
    }

    private fun field(key: String, label: String, value: String, type: Int) {
        list.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(0xFF9A9AA0.toInt()); setPadding(px(4), px(6), 0, px(4)) })
        val e = EditText(this).apply {
            setText(value); inputType = type; textSize = 15f; setTextColor(Color.WHITE); setHintTextColor(0xFF666666.toInt())
            background = rounded(cardColor, 14); setPadding(px(14), px(12), px(14), px(12))
        }
        fields[key] = e
        list.addView(e, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun button(s: String, primary: Boolean, onClick: () -> Unit) {
        list.addView(Button(this).apply {
            text = s; isAllCaps = false; textSize = 15f
            setTextColor(if (primary) Color.BLACK else Color.WHITE)
            background = rounded(if (primary) Color.WHITE else cardColor)
            setOnClickListener { onClick() }
        }, LinearLayout.LayoutParams(MATCH_PARENT, px(50)).apply { topMargin = px(14) })
    }

    // ---- actions

    private fun has(p: String) = checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED
    private fun ask(p: String) = requestPermissions(arrayOf(p), 7)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun open(i: Intent) {
        try { startActivity(i) } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        build()
        bg.execute { Steps.sample(this); Updater.updateAll(this) }
    }

    private fun save() {
        fun f(k: String) = fields[k]?.text?.toString()?.trim().orEmpty()
        prefs.city = f("city").ifEmpty { "Chennai" }
        f("lat").toDoubleOrNull()?.takeIf { it in -90.0..90.0 }?.let { prefs.lat = it }
        f("lon").toDoubleOrNull()?.takeIf { it in -180.0..180.0 }?.let { prefs.lon = it }
        f("goal").toIntOrNull()?.takeIf { it in 500..100_000 }?.let { prefs.stepGoal = it }
        f("stride").toFloatOrNull()?.takeIf { it in 0.3f..1.5f }?.let { prefs.strideM = it }
        prefs.worldLabel = f("wlabel").ifEmpty { "New York" }
        val zone = f("wzone")
        if (runCatching { java.time.ZoneId.of(zone) }.isSuccess) prefs.worldZone = zone
        else toast("Unknown time zone, kept ${prefs.worldZone}")
        f("data").toFloatOrNull()?.takeIf { it > 0f }?.let { prefs.dataLimitGb = it }
        f("focus").toIntOrNull()?.takeIf { it in 1..180 }?.let { prefs.focusMin = it }
        toast("Saved")
        bg.execute {
            Weather.refresh(this)
            Updater.updateAll(this)
        }
        build()
    }

    @SuppressLint("MissingPermission")
    private fun useLocation() {
        if (!has(Manifest.permission.ACCESS_COARSE_LOCATION)) { ask(Manifest.permission.ACCESS_COARSE_LOCATION); return }
        val lm = getSystemService(LocationManager::class.java)
        val last = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null) { applyLocation(last.latitude, last.longitude); return }
        val provider = if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
        toast("Finding your location…")
        try {
            lm.getCurrentLocation(provider, null, mainExecutor) { loc ->
                if (loc != null) applyLocation(loc.latitude, loc.longitude) else toast("Couldn't get a location. Turn on location and try again.")
            }
        } catch (e: Exception) { toast("Couldn't get a location") }
    }

    private fun applyLocation(lat: Double, lon: Double) {
        prefs.lat = lat
        prefs.lon = lon
        bg.execute {
            @Suppress("DEPRECATION")
            val name = runCatching { Geocoder(this, Locale.ENGLISH).getFromLocation(lat, lon, 1)?.firstOrNull()?.locality }.getOrNull()
            if (!name.isNullOrBlank()) prefs.city = name
            Weather.refresh(this)
            Updater.updateAll(this)
            runOnUiThread { build(); toast("Location set to ${prefs.city}") }
        }
    }

    // ---- in-app updates (GitHub Releases)

    private fun autoCheck() {
        val last = prefs.long("upd_checked", 0L)
        if (System.currentTimeMillis() - last > 6 * 3_600_000L) checkUpdate(false)
    }

    private fun checkUpdate(manual: Boolean) {
        if (manual) toast("Checking for updates…")
        bg.execute {
            val r = AppUpdate.latest()
            prefs.put("upd_checked", System.currentTimeMillis())
            runOnUiThread {
                when {
                    r == null -> if (manual) toast("Couldn't reach GitHub. Check your internet.")
                    r.code <= AppUpdate.currentCode(this) -> {
                        available = null
                        if (manual) toast("You're on the latest version")
                    }
                    else -> {
                        available = r
                        build()
                        if (manual) confirmUpdate(r)
                    }
                }
            }
        }
    }

    private fun confirmUpdate(r: AppUpdate.Release) {
        val notes = r.notes.lineSequence().filter { it.isNotBlank() && !it.startsWith("Co-Authored") && !it.startsWith("Claude-") }
            .take(6).joinToString("\n")
        android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Update to ${r.name}?")
            .setMessage(notes.ifBlank { "A newer version is ready." } + "\n\nYour settings and widgets are kept.")
            .setPositiveButton("Update") { _, _ -> startUpdate(r) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startUpdate(r: AppUpdate.Release) {
        if (!AppUpdate.canInstall(this)) {
            toast("Allow Dot Widgets to install apps, then come back and tap Update again")
            AppUpdate.askInstallPermission(this)
            return
        }
        toast("Downloading update…")
        bg.execute {
            var lastShown = -1
            val file = AppUpdate.download(this, r) { pct ->
                if (pct >= lastShown + 25) { lastShown = pct; runOnUiThread { toast("Downloading… $pct%") } }
            }
            runOnUiThread {
                if (file == null) toast("Download failed. Try again.")
                else AppUpdate.install(this, file)
            }
        }
    }

    @Suppress("unused")
    private fun spacer() = list.addView(View(this), LinearLayout.LayoutParams(MATCH_PARENT, px(12)))
}
