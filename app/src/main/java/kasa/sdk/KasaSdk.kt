package kasa.sdk

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Kasa ekosistem modülü (her uygulamada aynı dosya).
 *
 * - Çökme toplayıcı: yakalanmamış hatalar Kasa'ya iletilir; Kasa özel depoda issue açar.
 * - Açılış testi: Kasa kurulumdan sonra [HealthActivity]'yi açar, uygulama sağlıklı açılınca Kasa'ya "health" bildirir.
 * - Veri yedeği: Kasa, [KasaBridge] üzerinden uygulamanın yedek dosyasını alır/geri verir (Kasa şifreler).
 * - Ekosistem teması ve ortak kilit (Kasa Kimlik).
 *
 * Bütün bağlar internet kullanmaz; yalnızca aşağıdaki imzalarla imzalanmış uygulamalar arasında çalışır.
 */
object KasaSdk {
    const val KASA = "com.kasa.merkez"
    const val ECO = "com.kasa.merkez.eco"
    const val ACTION_KIMLIK = "com.kasa.merkez.KIMLIK"

    /** Ekosistemin imza sertifikaları (SHA-256). */
    val TRUSTED = setOf(
        "fd10fa2dfcacab2cb4c5c4eeaf36b03e08f5ec2f57815b750ba097d14c21a4fb", // ortak anahtar
        "808c01b59a1feee65e5272b51f89a00b69700b3fadded0c1239dd398a9ab1667", // Nüsha anahtarı
    )

    internal var backupFn: ((OutputStream) -> Unit)? = null
    internal var restoreFn: ((InputStream) -> Unit)? = null
    @Volatile private var app: Application? = null

    /** Application.onCreate içinde çağrılır. [backup]/[restore] verilmezse veri yedeği desteklenmez. */
    fun init(a: Application, backup: ((OutputStream) -> Unit)? = null, restore: ((InputStream) -> Unit)? = null) {
        app = a; backupFn = backup; restoreFn = restore
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { report(a, e, t.name) }
            prev?.uncaughtException(t, e)
        }
        Thread { runCatching { flush(a) } }.start()
    }

    private fun dir(c: Context) = File(c.filesDir, "kasa_crash").apply { mkdirs() }

    private fun version(c: Context): String = runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "?"

    private fun report(c: Context, e: Throwable, thread: String) {
        val trace = e.stackTraceToString().take(12000)
        val f = File(dir(c), "${System.currentTimeMillis()}.txt")
        f.writeText("${version(c)}\n$thread\n$trace")
        if (send(c, f)) f.delete()
    }

    private fun send(c: Context, f: File): Boolean {
        val lines = f.readText().split('\n', limit = 3)
        if (lines.size < 3) return true
        val r = call(c, "crash", Bundle().apply {
            putString("pkg", c.packageName); putString("ver", lines[0]); putString("thread", lines[1]); putString("trace", lines[2])
            putLong("at", f.nameWithoutExtension.toLongOrNull() ?: System.currentTimeMillis())
        })
        return r?.getBoolean("ok") == true
    }

    /** Kasa kapalıyken biriken çökme kayıtlarını gönderir. */
    fun flush(c: Context) { dir(c).listFiles()?.sortedBy { it.name }?.forEach { if (send(c, it)) it.delete() } }

    fun kasaInstalled(c: Context) = trusted(c, KASA)

    fun call(c: Context, method: String, extras: Bundle? = null): Bundle? {
        if (!kasaInstalled(c)) return null
        return runCatching { c.contentResolver.call(Uri.parse("content://$ECO"), method, c.packageName, extras) }.getOrNull()
    }

    /** Kasa'dan gelen ekosistem teması. null = ortak tema kapalı. mode: "system" | "dark" | "light". */
    data class Theme(val mode: String, val accent: Long)
    fun theme(c: Context): Theme? = call(c, "theme")?.takeIf { it.getBoolean("on") }?.let { Theme(it.getString("mode") ?: "system", it.getLong("accent")) }

    /** Ortak kilit (Kasa Kimlik) açık mı? Açıksa uygulama kendi kilidi yerine [kimlikIntent] ile Kasa'ya sorar. */
    fun sharedLock(c: Context): Boolean = call(c, "lock")?.getBoolean("on") == true
    fun kimlikIntent(): Intent = Intent(ACTION_KIMLIK).setPackage(KASA)

    fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    @Suppress("DEPRECATION")
    fun trusted(c: Context, pkg: String?): Boolean {
        pkg ?: return false
        val pm = c.packageManager
        return runCatching {
            val sigs = if (Build.VERSION.SDK_INT >= 28) pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
            else pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
            sigs.orEmpty().any { sha(it.toByteArray()) in TRUSTED }
        }.getOrDefault(false)
    }
}

/** Kasa'nın açılış testi için açtığı görünmez ekran. */
class HealthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ok = KasaSdk.trusted(this, callingPackage ?: KasaSdk.KASA)
        if (ok) Thread { KasaSdk.call(this, "health", Bundle().apply { putString("pkg", packageName); putString("ver", runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()) }) }.start()
        finish()
        if (Build.VERSION.SDK_INT >= 34) overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0) else @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}

/** Kasa'nın veri yedeği köprüsü: content://<paket>.kasa/backup (oku) ve /restore (yaz). Yalnızca Kasa erişebilir. */
class KasaBridge : ContentProvider() {
    override fun onCreate() = true
    private fun guard() { if (!KasaSdk.trusted(context!!, callingPackage) || callingPackage != KasaSdk.KASA) throw SecurityException("Kasa dışı erişim") }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        guard()
        return when (method) {
            "info" -> Bundle().apply { putBoolean("backup", KasaSdk.backupFn != null); putString("ver", runCatching { context!!.packageManager.getPackageInfo(context!!.packageName, 0).versionName }.getOrNull()) }
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        guard()
        val (r, w) = ParcelFileDescriptor.createReliablePipe()
        when (uri.lastPathSegment) {
            "backup" -> {
                val fn = KasaSdk.backupFn ?: throw IllegalStateException("Yedek desteklenmiyor")
                Thread {
                    try { ParcelFileDescriptor.AutoCloseOutputStream(w).use { fn(it) } }
                    catch (e: Exception) { runCatching { w.closeWithError(e.message ?: "hata") } }
                }.start()
                return r
            }
            "restore" -> {
                val fn = KasaSdk.restoreFn ?: throw IllegalStateException("Geri yükleme desteklenmiyor")
                Thread {
                    try { ParcelFileDescriptor.AutoCloseInputStream(r).use { fn(it) } }
                    catch (e: Exception) { runCatching { r.closeWithError(e.message ?: "hata") } }
                }.start()
                return w
            }
        }
        throw IllegalArgumentException("Bilinmeyen yol")
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String = "application/octet-stream"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
