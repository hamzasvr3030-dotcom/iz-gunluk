package com.iz.gunluk

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// ============================ Ekosistem: Rota, Seyir, Kadraj ============================
object Eco {
    const val ROTA = "com.rota.app"; const val SEYIR = "com.seyir.ekran"; const val KADRAJ = "com.perde.oynatici"

    fun installed(ctx: Context, pkg: String) = runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)
    private fun call(ctx: Context, auth: String, method: String, arg: String? = null): Bundle? = runCatching {
        if (ctx.packageManager.resolveContentProvider(auth, 0) == null) return null
        ctx.contentResolver.call(Uri.parse("content://$auth"), method, arg, null)
    }.getOrNull()

    /** Rota'dan bugünün özeti: tamamlanan görevler ve alışkanlıklar. */
    suspend fun rotaToday(ctx: Context): String? = withContext(Dispatchers.IO) {
        call(ctx, "$ROTA.eco", "today")?.getString("text")?.trim()?.takeIf { it.isNotBlank() }
    }

    /** Seyir'den günlük ekran süresi (dakika). Yalnızca kullanıcı izin verdiyse çağrılır. */
    suspend fun seyirMinutes(ctx: Context, from: Long, to: Long): Map<Long, Int> = withContext(Dispatchers.IO) {
        runCatching {
            val a = JSONArray(call(ctx, "$SEYIR.eco", "days", "$from,$to")?.getString("json") ?: "[]")
            (0 until a.length()).associate { a.getJSONObject(it).let { o -> o.getLong("day") to (o.getLong("screenMs") / 60_000).toInt() } }
        }.getOrDefault(emptyMap())
    }

    /** Kadraj'dan o gün en çok dinlenen şarkı: call("song", epochDay) → title, artist. */
    suspend fun kadrajSong(ctx: Context, day: Long): String? = withContext(Dispatchers.IO) {
        call(ctx, "$KADRAJ.eco", "song", day.toString())?.let { b ->
            val t = b.getString("title").orEmpty(); val a = b.getString("artist").orEmpty()
            if (t.isBlank()) null else if (a.isBlank()) t else "$t — $a"
        }
    }

    /** Seyir bağlantısı açıksa son 120 günün ekran süresini girdilere işler (günde bir kez). */
    suspend fun syncSeyir(ctx: Context, force: Boolean = false) {
        if (!Prefs.seyirLink) return
        val today = LocalDate.now().toEpochDay()
        if (!force && Prefs.long("seyirSync") == today) return
        val m = seyirMinutes(ctx, today - 120, today)
        if (m.isEmpty()) return
        withContext(Dispatchers.Main) {
            Store.mapAll { e -> m[e.day]?.let { if (e.screenMin != it) e.copy(screenMin = it) else e } ?: e }
            Prefs.putLong("seyirSync", today)
        }
    }

    /** Ruh hâli puanından (1–5) en yakın ana duygu. */
    fun moodFromScore(s: Int): String? = when (s) { 5 -> "mutlu"; 4 -> "huzurlu"; 3 -> "sakin"; 2 -> "yorgun"; 1 -> "uzgun"; else -> null }
}

/**
 * İz'in ekosistem sağlayıcısı (yalnızca Kasa'nın güvendiği uygulamalar):
 *  - call("moods", "from,to") → json [{day, mood 1–5}]  (Seyir: uyku ve ruh hâli)
 *  - call("today") → wrote, mood, streak                  (Rota/Kasa özeti)
 */
class EcoProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        if (callingPackage != ctx.packageName && !kasa.sdk.KasaSdk.trusted(ctx, callingPackage)) throw SecurityException("Ekosistem dışı uygulama")
        val vis = Store.entries.filter { !it.hidden && !it.isEmpty }
        return when (method) {
            "moods" -> {
                val today = LocalDate.now().toEpochDay()
                val (a, b) = arg?.split(',')?.mapNotNull { it.trim().toLongOrNull() }?.let { (it.getOrNull(0) ?: today - 30) to (it.getOrNull(1) ?: today) } ?: (today - 30 to today)
                val arr = JSONArray()
                Factors.dayScores(vis.filter { it.day in a..b }).toSortedMap().forEach { (d, s) -> arr.put(JSONObject().put("day", d).put("mood", Math.round(s).toInt().coerceIn(1, 5))) }
                Bundle().apply { putString("json", arr.toString()) }
            }
            "today" -> {
                val t = LocalDate.now().toEpochDay()
                Bundle().apply {
                    putBoolean("wrote", vis.any { it.day == t })
                    putString("mood", vis.filter { it.day == t && it.mood != null }.maxByOrNull { it.time }?.mood)
                    putInt("streak", Insights.streak(vis.map { it.day }.toSet()))
                }
            }
            else -> null
        }
    }
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

// ============================ Girdi sürüm geçmişi ============================
/** Düzenlenen girdilerin eski hâlleri (şifreli, girdi başına en fazla 30). */
object Versions {
    data class V(val at: Long, val e: Entry)
    private var cache: MutableMap<String, MutableList<V>>? = null
    private fun file() = File(Store.dataDir, "versions.json")

    private fun all(): MutableMap<String, MutableList<V>> {
        cache?.let { return it }
        val m = mutableMapOf<String, MutableList<V>>()
        runCatching {
            val f = file()
            if (f.exists()) {
                val o = JSONObject(String(Crypto.open(f.readBytes())))
                for (k in o.keys()) { val a = o.getJSONArray(k); m[k] = (0 until a.length()).map { a.getJSONObject(it).let { x -> V(x.getLong("at"), Entry.from(x.getJSONObject("e"))) } }.toMutableList() }
            }
        }
        cache = m; return m
    }
    private fun save() {
        val o = JSONObject(); all().forEach { (k, l) -> o.put(k, JSONArray(l.map { JSONObject().put("at", it.at).put("e", it.e.json()) })) }
        runCatching { val f = file(); val tmp = File(f.path + ".tmp"); tmp.writeBytes(Crypto.seal(o.toString().toByteArray())); tmp.renameTo(f) || run { f.delete(); tmp.renameTo(f) } }
    }
    fun reset() { cache = null }

    /** Metin anlamlı değiştiyse ve son sürümden 10 dk geçtiyse eskiyi saklar. */
    fun record(old: Entry, new: Entry) {
        if (old.body == new.body && old.title == new.title) return
        if (old.body.isBlank() && old.title.isBlank()) return
        val l = all().getOrPut(old.id) { mutableListOf() }
        val last = l.lastOrNull()
        if (last != null && System.currentTimeMillis() - last.at < 10 * 60_000L) return
        if (last != null && last.e.body == old.body && last.e.title == old.title) return
        l += V(System.currentTimeMillis(), old)
        while (l.size > 30) l.removeAt(0)
        save()
    }
    fun list(id: String): List<V> = all()[id].orEmpty().sortedByDescending { it.at }
    fun count(id: String) = all()[id]?.size ?: 0
}

// ============================ Bildirimden hızlı ruh hâli ============================
object QuickMood {
    const val CH = "iz_mood"; const val ASK = "com.iz.gunluk.MOOD_ASK"; const val SET = "com.iz.gunluk.MOOD_SET"
    val five = listOf("mutlu" to "😄", "huzurlu" to "🙂", "sakin" to "😐", "uzgun" to "😔", "ofkeli" to "😤")

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 2, Intent(ctx, QuickMoodReceiver::class.java).setAction(ASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        if (!Prefs.moodNotif) return
        var t = LocalDateTime.now().withHour(Prefs.moodHour).withMinute(30).withSecond(0).withNano(0)
        if (!t.isAfter(LocalDateTime.now())) t = t.plusDays(1)
        am.setWindow(AlarmManager.RTC_WAKEUP, t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), 15 * 60_000L, pi)
    }

    fun ask(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Akşam ruh hâli", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Uygulamayı açmadan tek dokunuşla ruh hâli" })
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val today = LocalDate.now().toEpochDay()
        if (Store.entries.any { it.day == today && it.mood != null }) return
        val b = NotificationCompat.Builder(ctx, CH).setSmallIcon(R.drawable.ic_note).setContentTitle("Bugün nasılsın?")
            .setContentText("Bir emojiye dokun; İz kaydetsin.").setAutoCancel(true).setColor(IzAmber.toArgb())
            .setContentIntent(Reminders.open(ctx, "write", 31))
        five.forEachIndexed { i, (k, em) ->
            b.addAction(0, em, PendingIntent.getBroadcast(ctx, 300 + i, Intent(ctx, QuickMoodReceiver::class.java).setAction(SET).putExtra("mood", k), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        try { NotificationManagerCompat.from(ctx).notify(30, b.build()) } catch (_: SecurityException) { }
    }
}

class QuickMoodReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            QuickMood.ASK -> { QuickMood.ask(context); QuickMood.schedule(context) }
            QuickMood.SET -> {
                intent.getStringExtra("mood")?.let { Store.setTodayMood(it) }
                NotificationManagerCompat.from(context).cancel(30)
                IzWidgetLarge.update(context)
            }
        }
    }
}

// ============================ Konum tetikli zaman kapsülü ============================
object Capsules {
    const val ARRIVE = "com.iz.gunluk.CAPSULE"
    fun canWatch(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun canBackground(ctx: Context) = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pi(ctx: Context, l: Letter) = PendingIntent.getBroadcast(ctx, 500 + (l.id.hashCode() and 0xffff), Intent(ctx, CapsuleReceiver::class.java).setAction(ARRIVE).putExtra("id", l.id),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Yer mektupları için yakınlık uyarısı kurar (açılış tarihi gelmemiş olsa da varış kaydedilir). */
    @android.annotation.SuppressLint("MissingPermission")
    fun register(ctx: Context) {
        if (!canWatch(ctx)) return
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        Store.letters.filter { it.byPlace && !it.arrived }.forEach { l -> runCatching { lm.addProximityAlert(l.lat!!, l.lon!!, l.radius.toFloat(), -1, pi(ctx, l)) } }
    }
    fun unregister(ctx: Context, l: Letter) { runCatching { ctx.getSystemService(LocationManager::class.java)?.removeProximityAlert(pi(ctx, l)) } }
}

class CapsuleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Capsules.ARRIVE || !intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)) return
        val id = intent.getStringExtra("id") ?: return
        val l = Store.letters.firstOrNull { it.id == id } ?: return
        if (l.arrived) return
        Store.upsertLetter(l.copy(arrived = true))
        Capsules.unregister(context, l)
        if (l.isOpen()) Reminders.notify(context, 40 + (id.hashCode() and 0xff), "Bir zaman kapsülü açıldı", "${l.placeName.ifBlank { "Bu yere" }} vardın. Geçmişteki sen sana burada bir mektup bıraktı.", "letters")
    }
}

// ============================ Günün fotoğrafları önerisi ============================
object DayPhotos {
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    fun granted(ctx: Context) = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED ||
        (Build.VERSION.SDK_INT >= 34 && ContextCompat.checkSelfPermission(ctx, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED)

    /** Bugün çekilen fotoğraflar (kamera ve ekran görüntüsü hariç yeni olanlar önce). */
    suspend fun today(ctx: Context, day: LocalDate = LocalDate.now(), max: Int = 24): List<Uri> = withContext(Dispatchers.IO) {
        if (!granted(ctx)) return@withContext emptyList()
        val from = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val to = from + 86_400_000L
        val out = mutableListOf<Uri>()
        runCatching {
            ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.RELATIVE_PATH),
                "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?", arrayOf(from.toString(), to.toString()), "${MediaStore.Images.Media.DATE_TAKEN} DESC")?.use { c ->
                val idc = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID); val pc = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                while (c.moveToNext() && out.size < max) {
                    val path = if (pc >= 0) c.getString(pc).orEmpty() else ""
                    if (path.contains("Screenshot", true)) continue
                    out += ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idc))
                }
            }
        }
        out
    }
}

/** Harita için sayılı balon. */
fun countBubble(n: Int, color: Int): Bitmap {
    val s = (64 + minOf(n, 30) * 2).coerceAtMost(124)
    val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888); val cv = Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = android.graphics.Color.WHITE; cv.drawCircle(s / 2f, s / 2f, s / 2f, p)
    p.color = color; cv.drawCircle(s / 2f, s / 2f, s / 2f - 6, p)
    p.color = android.graphics.Color.rgb(26, 15, 9); p.textSize = s * 0.38f; p.textAlign = Paint.Align.CENTER; p.isFakeBoldText = true
    cv.drawText(n.toString(), s / 2f, s / 2f - (p.descent() + p.ascent()) / 2, p)
    return b
}
