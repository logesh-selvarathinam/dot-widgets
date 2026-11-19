package com.nandhini.dotwidgets

import android.content.Context
import android.content.SharedPreferences

/** All user settings and cached widget state live in one SharedPreferences file. */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.applicationContext.getSharedPreferences("dw", Context.MODE_PRIVATE)

    fun str(key: String, def: String) = sp.getString(key, def) ?: def
    fun int(key: String, def: Int) = sp.getInt(key, def)
    fun long(key: String, def: Long) = sp.getLong(key, def)
    fun float(key: String, def: Float) = sp.getFloat(key, def)
    fun bool(key: String, def: Boolean) = sp.getBoolean(key, def)

    fun put(key: String, v: Any?) {
        val e = sp.edit()
        when (v) {
            null -> e.remove(key)
            is String -> e.putString(key, v)
            is Int -> e.putInt(key, v)
            is Long -> e.putLong(key, v)
            is Float -> e.putFloat(key, v)
            is Double -> e.putFloat(key, v.toFloat())
            is Boolean -> e.putBoolean(key, v)
            else -> e.putString(key, v.toString())
        }
        e.apply()
    }

    // ---- user settings ----
    var city: String
        get() = str("city", "Chennai")
        set(v) = put("city", v)
    var lat: Double
        get() = float("lat", 13.0827f).toDouble()
        set(v) = put("lat", v)
    var lon: Double
        get() = float("lon", 80.2707f).toDouble()
        set(v) = put("lon", v)
    var stepGoal: Int
        get() = int("stepGoal", 10000)
        set(v) = put("stepGoal", v)
    var strideM: Float
        get() = float("strideM", 0.72f)
        set(v) = put("strideM", v)
    var worldZone: String
        get() = str("worldZone", "America/New_York")
        set(v) = put("worldZone", v)
    var worldLabel: String
        get() = str("worldLabel", "New York")
        set(v) = put("worldLabel", v)
    var dataLimitGb: Float
        get() = float("dataLimitGb", 2f)
        set(v) = put("dataLimitGb", v)
    var focusMin: Int
        get() = int("focusMin", 25)
        set(v) = put("focusMin", v)
}
