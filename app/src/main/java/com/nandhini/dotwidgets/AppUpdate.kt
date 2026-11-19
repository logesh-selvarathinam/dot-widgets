package com.nandhini.dotwidgets

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Checks GitHub Releases for a newer APK, downloads it and opens the Android installer. */
object AppUpdate {
    private const val LATEST = "https://api.github.com/repos/logesh-selvarathinam/dot-widgets/releases/latest"

    data class Release(val code: Long, val name: String, val apkUrl: String, val notes: String)

    fun currentCode(ctx: Context): Long = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode
    fun currentName(ctx: Context): String = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"

    /** Network call: background thread only. Returns the latest release, or null if it can't be read. */
    fun latest(): Release? = try {
        val con = URL(LATEST).openConnection() as HttpURLConnection
        con.setRequestProperty("Accept", "application/vnd.github+json")
        con.connectTimeout = 10_000
        con.readTimeout = 10_000
        val j = JSONObject(con.inputStream.bufferedReader().use { it.readText() })
        con.disconnect()
        val tag = j.getString("tag_name") // like "v12"
        val code = tag.trimStart('v', 'V').substringBefore('.').toLong()
        val assets = j.getJSONArray("assets")
        var url: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
        }
        if (url == null) null else Release(code, j.optString("name", tag), url, j.optString("body", ""))
    } catch (e: Exception) { null }

    /** Downloads the APK into app storage. Background thread only. */
    fun download(ctx: Context, r: Release, progress: (Int) -> Unit): File? = try {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "DotWidgets-${r.code}.apk")
        var con = URL(r.apkUrl).openConnection() as HttpURLConnection
        con.instanceFollowRedirects = true
        con.connectTimeout = 15_000
        con.readTimeout = 30_000
        // GitHub answers with a redirect to its file host; follow it by hand if needed
        var hops = 0
        while (con.responseCode in 300..399 && hops < 5) {
            val next = con.getHeaderField("Location")
            con.disconnect()
            con = URL(next).openConnection() as HttpURLConnection
            hops++
        }
        val total = con.contentLengthLong
        con.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var n: Int
                while (input.read(buf).also { n = it } >= 0) {
                    output.write(buf, 0, n)
                    done += n
                    if (total > 0) progress((done * 100 / total).toInt())
                }
            }
        }
        con.disconnect()
        out
    } catch (e: Exception) { null }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun askInstallPermission(a: Activity) {
        a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
    }

    fun install(a: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.files", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        a.startActivity(i)
    }
}
