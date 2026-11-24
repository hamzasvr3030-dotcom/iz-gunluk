package com.iz.gunluk

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

object MediaKit {
    /** Seçilen dosyayı uygulamanın kendi klasörüne kopyalar; galeriden silinse bile anı kaybolmaz. */
    suspend fun import(ctx: Context, uri: Uri, kind: String): Media? = withContext(Dispatchers.IO) {
        try {
            val mime = ctx.contentResolver.getType(uri) ?: ""
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
                ?: when (kind) { "video" -> "mp4"; "audio" -> "m4a"; else -> "jpg" }
            val name = "${kind}_${UUID.randomUUID().toString().take(12)}.$ext"
            val out = Store.media(name)
            ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { o -> i.copyTo(o, 1 shl 16) } } ?: return@withContext null
            val dur = if (kind == "video" || kind == "audio") duration(out) else 0L
            Media(name, if (mime.startsWith("video")) "video" else kind, dur)
        } catch (_: Exception) { null }
    }

    fun duration(f: File): Long = try {
        val r = android.media.MediaMetadataRetriever(); r.setDataSource(f.path)
        val d = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release(); d
    } catch (_: Exception) { 0L }

    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

    fun open(ctx: Context, m: Media) {
        val f = Store.media(m.file)
        val type = when { m.isVideo -> "video/*"; m.isAudio -> "audio/*"; else -> "image/*" }
        try {
            Lock.suppress = true
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(ctx, f), type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) { Lock.suppress = false }
    }

    fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

    // ---------------- ses notu ----------------
    class Recorder(private val ctx: Context) {
        private var rec: MediaRecorder? = null
        var file: File? = null; private set
        var started = 0L; private set

        fun start(): Boolean = try {
            val f = Store.media("audio_${UUID.randomUUID().toString().take(12)}.m4a")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000); r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.path); r.prepare(); r.start()
            rec = r; file = f; started = System.currentTimeMillis(); true
        } catch (_: Exception) { rec?.release(); rec = null; false }

        fun stop(): Media? {
            val r = rec ?: return null
            rec = null
            return try {
                r.stop(); r.release()
                val f = file!!
                Media(f.name, "audio", System.currentTimeMillis() - started)
            } catch (_: Exception) { r.release(); file?.delete(); null }
        }
    }

    /** Aynı anda tek ses çalınır. */
    object Player {
        private var mp: MediaPlayer? = null
        var playing: String? = null; private set
        fun toggle(m: Media, onEnd: () -> Unit): Boolean {
            if (playing == m.file) { stop(); return false }
            stop()
            return try {
                mp = MediaPlayer().apply {
                    setDataSource(Store.media(m.file).path); prepare(); start()
                    setOnCompletionListener { stop(); onEnd() }
                }
                playing = m.file; true
            } catch (_: Exception) { stop(); false }
        }
        fun stop() { try { mp?.stop() } catch (_: Exception) { }; mp?.release(); mp = null; playing = null }
    }

    // ---------------- konum ve hava ----------------
    fun hasLocation(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun location(ctx: Context): Location? {
        if (!hasLocation(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 30 * 60_000) return last
        if (Build.VERSION.SDK_INT >= 30 && providers.isNotEmpty()) {
            val fresh = withTimeoutOrNull(12_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    try {
                        lm.getCurrentLocation(providers.first(), null, ContextCompat.getMainExecutor(ctx)) { cont.resume(it) }
                    } catch (_: Exception) { cont.resume(null) }
                }
            }
            if (fresh != null) return fresh
        }
        return last
    }

    @Suppress("DEPRECATION")
    suspend fun placeName(ctx: Context, lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext ""
            val a = Geocoder(ctx, Locale.forLanguageTag("tr")).getFromLocation(lat, lon, 1)?.firstOrNull() ?: return@withContext ""
            listOfNotNull(a.subLocality ?: a.thoroughfare, a.subAdminArea ?: a.locality, a.adminArea)
                .filter { it.isNotBlank() }.distinct().take(2).joinToString(", ")
        } catch (_: Exception) { "" }
    }

    /** Open-Meteo (anahtar gerektirmez). Yalnızca kullanıcı istediğinde konum gönderilir. */
    suspend fun weather(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val u = URL("https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code".format(Locale.US, lat, lon))
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000; c.setRequestProperty("User-Agent", "Iz-Android")
            if (c.responseCode != 200) return@withContext ""
            val o = JSONObject(c.inputStream.bufferedReader().readText()).getJSONObject("current")
            "%d°C · %s".format(Math.round(o.getDouble("temperature_2m")), Tr.weatherText(o.getInt("weather_code")))
        } catch (_: Exception) { "" }
    }
}
