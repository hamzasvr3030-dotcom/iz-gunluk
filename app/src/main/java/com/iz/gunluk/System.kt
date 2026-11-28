package com.iz.gunluk

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
import android.os.Build
import android.provider.Settings
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

// ============================ Hatırlatıcılar ============================
object Reminders {
    const val CH = "iz_gentle"

    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Nazik hatırlatıcılar", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Günlük yazma hatırlatması, geçmişte bugün ve açılan mektuplar"
        })
    }

    /** Bir sonraki günlük tetiklemeyi kurar (hatırlatıcı kapalıysa da anılar/mektuplar için 10:00'da). */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        val (h, m) = if (Prefs.remindOn) Prefs.remindHour to Prefs.remindMin else 10 to 0
        var t = LocalDateTime.now().withHour(h).withMinute(m).withSecond(0).withNano(0)
        if (!t.isAfter(LocalDateTime.now())) t = t.plusDays(1)
        val at = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi)
    }

    fun dayOn(d: LocalDate) = (Prefs.remindDays shr (d.dayOfWeek.value - 1)) and 1 == 1

    fun open(ctx: Context, what: String, rc: Int): PendingIntent = PendingIntent.getActivity(ctx, rc,
        Intent(ctx, MainActivity::class.java).putExtra("open", what).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun notify(ctx: Context, id: Int, title: String, text: String, what: String) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(ctx, CH).setSmallIcon(R.drawable.ic_note).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setAutoCancel(true)
            .setColor(Palette.accents.getOrElse(Prefs.accent) { Palette.accents[0] }.second.toArgb())
            .setContentIntent(open(ctx, what, id)).build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) { }
    }

    fun fire(ctx: Context) {
        val today = LocalDate.now()
        val todayKey = today.toEpochDay()
        val wrote = Store.entries.any { it.day == todayKey && !it.isEmpty }
        if (Prefs.remindOn && dayOn(today) && !wrote) {
            val q = if (Prefs.showQuestion) Questions.forDay(todayKey) else "Birkaç satır bile yeter."
            notify(ctx, 10, "Bugün nasıl geçti?", q, "write")
        }
        if (Prefs.memoryNotif && Prefs.long("memNotified") != todayKey) {
            val past = Insights.onThisDay(Store.entries.filter { !it.hidden }, today).firstOrNull()
            if (past != null) {
                val y = today.year - past.date.year
                notify(ctx, 11, "Tam $y yıl önce bugün…", past.title.ifBlank { past.plain.take(120) }.ifBlank { "O gün neler yaşamıştın?" }, "memories")
                Prefs.putLong("memNotified", todayKey)
            }
        }
        for (l in Store.letters.filter { it.isOpen(todayKey) && !it.read }) {
            if (Prefs.str("letterNotified").contains(l.id)) continue
            notify(ctx, 20 + (l.id.hashCode() and 0xfff), "Geçmişten bir mektubun var", "“${l.title.ifBlank { "Mektup" }}” bugün açıldı. ${Tr.long(java.time.Instant.ofEpochMilli(l.written).atZone(ZoneId.systemDefault()).toLocalDate())} tarihinde yazmıştın.", "letters")
            Prefs.putStr("letterNotified", Prefs.str("letterNotified") + "," + l.id)
        }
        IzWidget.update(ctx)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.fire(context)
        Reminders.schedule(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { Reminders.schedule(context); QuickMood.schedule(context); Capsules.register(context); IzWidget.update(context) }
}

// ============================ Ana ekran widget'ı ============================
class IzWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { update(context) }

    companion object {
        fun update(ctx: Context) {
            IzWidgetLarge.update(ctx)
            try {
                val m = AppWidgetManager.getInstance(ctx)
                val ids = m.getAppWidgetIds(ComponentName(ctx, IzWidget::class.java))
                if (ids.isEmpty()) return
                val v = RemoteViews(ctx.packageName, R.layout.widget)
                val today = LocalDate.now()
                v.setTextViewText(R.id.w_date, Tr.upper("${today.dayOfMonth} ${Tr.months[today.monthValue - 1]} · ${Tr.days[today.dayOfWeek.value - 1]}"))
                val todays = Store.entries.filter { it.day == today.toEpochDay() }
                val mood = todays.mapNotNull { Moods[it.mood] }.lastOrNull()
                v.setTextViewText(R.id.w_title, when {
                    mood != null -> mood.label
                    todays.any { !it.isEmpty } -> "Bugün yazdın"
                    else -> "Bugün nasıl geçti?"
                })
                val st = Insights.streak(Store.writtenDays)
                v.setTextViewText(R.id.w_sub, if (st > 0) "Seri: $st gün · bu hafta ${Insights.weekCount(Store.writtenDays)}/${Prefs.goal}" else "Bir gün daha, bir anı daha.")
                v.setInt(R.id.w_mood, "setColorFilter", (mood?.color ?: Palette.accents[0].second).toArgb())
                v.setOnClickPendingIntent(R.id.w_write, Reminders.open(ctx, "write", 101))
                v.setOnClickPendingIntent(R.id.w_root, Reminders.open(ctx, "home", 102))
                m.updateAppWidget(ids, v)
            } catch (_: Exception) { }
        }
    }
}

// ============================ Uygulama içi güncelleme ============================
object Updater {
    const val REPO = "hamzasvr3030-dotcom/iz-gunluk"
    data class Rel(val version: String, val notes: String, val apkUrl: String, val size: Long)

    @Volatile var lastError: String? = null

    fun current(ctx: Context): String = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0" } catch (_: Exception) { "0" }

    fun newer(a: String, b: String): Boolean {
        val x = a.split('.').map { it.toIntOrNull() ?: 0 }; val y = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
        return false
    }

    suspend fun check(): Rel? = withContext(Dispatchers.IO) {
        lastError = null
        try {
            val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
            c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("User-Agent", "Iz-Android")
            c.connectTimeout = 10000; c.readTimeout = 10000
            val code = c.responseCode
            if (code != 200) { lastError = if (code == 403 || code == 429) "GitHub geçici olarak sınırladı, birkaç dakika sonra dene" else "Sunucu yanıtı: HTTP $code"; return@withContext null }
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            val assets = o.getJSONArray("assets")
            var url = ""; var size = 0L
            for (i in 0 until assets.length()) { val a = assets.getJSONObject(i); if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); size = a.optLong("size") } }
            if (url.isEmpty()) { lastError = "Sürümde APK dosyası yok"; null } else Rel(o.getString("tag_name").removePrefix("v"), o.optString("body"), url, size)
        } catch (e: Exception) { lastError = "İnternete bağlanılamadı (${e.javaClass.simpleName})"; null }
    }

    suspend fun download(ctx: Context, r: Rel, progress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        lastError = null
        val f = File(ctx.cacheDir, "update/Iz-${r.version}.apk"); f.parentFile?.mkdirs()
        if (f.exists() && r.size > 0 && f.length() == r.size) { progress(1f); return@withContext f }
        val part = File(f.path + ".part")
        try {
            var url = r.apkUrl; var c: HttpURLConnection; var hops = 0
            while (true) {
                c = URL(url).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false; c.setRequestProperty("User-Agent", "Iz-Android")
                c.connectTimeout = 15000; c.readTimeout = 30000
                val code = c.responseCode
                if (code in 300..399 && hops++ < 6) { url = c.getHeaderField("Location") ?: break; c.disconnect(); continue }
                if (code != 200) { lastError = "İndirme hatası: HTTP $code"; return@withContext null }
                break
            }
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
            c.inputStream.use { i -> FileOutputStream(part).use { o ->
                val buf = ByteArray(64 * 1024); var done = 0L
                while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; if (total > 0) progress(done.toFloat() / total) }
            } }
            if (r.size > 0 && part.length() != r.size) { lastError = "İndirme yarım kaldı, tekrar dene"; part.delete(); return@withContext null }
            f.delete(); part.renameTo(f)
            f.parentFile?.listFiles()?.filter { it.name != f.name }?.forEach { it.delete() }
            f
        } catch (e: Exception) { lastError = "İndirme kesildi (${e.javaClass.simpleName})"; part.delete(); null }
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    fun install(ctx: Context, f: File): Boolean {
        if (!canInstall(ctx)) {
            Lock.suppress = true
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        lastError = null
        Store.flush()
        try {
            val pi = ctx.packageManager.packageInstaller
            val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(ctx.packageName)
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("base.apk", 0, f.length()).use { out -> f.inputStream().use { it.copyTo(out, 1 shl 16) }; s.fsync(out) }
                val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val sender = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java).setPackage(ctx.packageName), flags or PendingIntent.FLAG_UPDATE_CURRENT).intentSender
                s.commit(sender)
            }
        } catch (e: Exception) {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return true
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -999)) {
            android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION") val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { try { context.startActivity(it) } catch (_: Exception) { Updater.lastError = "Kurulum ekranı açılamadı" } }
            }
            android.content.pm.PackageInstaller.STATUS_SUCCESS -> Updater.lastError = null
            else -> Updater.lastError = "Kurulum başarısız: " + (intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "bilinmeyen hata")
        }
    }
}
