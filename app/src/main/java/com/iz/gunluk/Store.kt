package com.iz.gunluk

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.Executors

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Store.init(this)
        Reminders.createChannel(this)
        Reminders.schedule(this)
    }
}

/** Tüm veriler cihazda: files/data (json) ve files/media. İnternete hiçbir şey gönderilmez. */
object Store {
    lateinit var ctx: Context
    val entries = mutableStateListOf<Entry>()
    val letters = mutableStateListOf<Letter>()
    /** Her değişiklikte artar; türetilmiş hesapların yeniden yapılması için. */
    var version by mutableIntStateOf(0)
    private val io = Executors.newSingleThreadExecutor()

    val dataDir get() = File(ctx.filesDir, "data").apply { mkdirs() }
    val mediaDir get() = File(ctx.filesDir, "media").apply { mkdirs() }
    fun media(name: String) = File(mediaDir, name)

    fun init(c: Context) {
        ctx = c.applicationContext
        entries.clear(); letters.clear()
        entries.addAll(read("entries.json")?.let { Codec.entriesFrom(it) } ?: emptyList())
        letters.addAll(read("letters.json")?.let { Codec.lettersFrom(it) } ?: emptyList())
        // yazarken eklenip sonra kaldırılan medya dosyalarını temizle (1 günden eski, hiçbir girdide kullanılmayan)
        val used = entries.flatMap { it.media.map { m -> m.file } }.toSet()
        io.execute { mediaDir.listFiles()?.filter { it.name !in used && System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() } }
    }

    private fun read(name: String): String? {
        val f = File(dataDir, name)
        for (cand in listOf(f, File(f.path + ".bak"))) {
            try { if (cand.exists()) { val s = cand.readText(); if (s.isNotBlank()) { org.json.JSONArray(s); return s } } } catch (_: Exception) { }
        }
        return null
    }

    private fun write(name: String, text: String) {
        val f = File(dataDir, name)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(text)
        if (f.exists()) f.copyTo(File(f.path + ".bak"), overwrite = true)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    private fun persist() {
        version++
        val e = Codec.entriesToJson(entries.toList()).toString()
        val l = Codec.lettersToJson(letters.toList()).toString()
        io.execute {
            try { write("entries.json", e); write("letters.json", l) } catch (_: Exception) { }
        }
        IzWidget.update(ctx)
    }

    /** Bekleyen yazmaları bitirir (dışa aktarma öncesi). */
    fun flush() { io.submit { }.get() }

    // -------- girdiler --------
    fun get(id: String) = entries.firstOrNull { it.id == id }

    fun upsert(e: Entry) {
        val i = entries.indexOfFirst { it.id == e.id }
        if (i >= 0) entries[i] = e else entries.add(e)
        persist()
    }

    fun delete(id: String) {
        val e = get(id) ?: return
        entries.removeAll { it.id == id }
        val used = entries.flatMap { it.media.map { m -> m.file } }.toSet()
        e.media.filter { it.file !in used }.forEach { media(it.file).delete() }
        persist()
    }

    fun newEntry(day: LocalDate = LocalDate.now()) = Entry(day = day.toEpochDay(), time = nowTime())
    fun nowTime(): String = LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }

    /** Gizli kayıtlar oturumda açılmadıkça hiçbir listede görünmez. */
    fun visible(): List<Entry> = entries.filter { !it.hidden || Lock.hiddenOpen }

    val writtenDays: Set<Long> get() = entries.filter { !it.isEmpty }.map { it.day }.toSet()

    fun today() = visible().filter { it.day == LocalDate.now().toEpochDay() }.sortedBy { it.time }

    /** Ana ekrandan hızlı ruh hâli: bugünün son girdisine işlenir, yoksa yalnız ruh hâli içeren bir girdi açılır. */
    fun setTodayMood(m: String) {
        val t = entries.filter { it.day == LocalDate.now().toEpochDay() }.maxByOrNull { it.time + it.created }
        if (t != null) upsert(t.copy(mood = m, updated = System.currentTimeMillis()))
        else upsert(newEntry().copy(mood = m))
    }

    // -------- mektuplar --------
    fun upsertLetter(l: Letter) {
        val i = letters.indexOfFirst { it.id == l.id }
        if (i >= 0) letters[i] = l else letters.add(l)
        persist()
    }
    fun deleteLetter(id: String) { letters.removeAll { it.id == id }; persist() }

    /** İçe aktarma: id'ye göre birleştir. */
    fun importAll(es: List<Entry>, ls: List<Letter>) {
        val merged = Codec.merge(entries.toList(), es)
        entries.clear(); entries.addAll(merged)
        val lm = LinkedHashMap<String, Letter>(); letters.forEach { lm[it.id] = it }; ls.forEach { lm[it.id] = it }
        letters.clear(); letters.addAll(lm.values)
        persist()
    }
}

/** Ayarlar: SharedPreferences + Compose durumu. */
object Prefs {
    private lateinit var sp: SharedPreferences

    var theme by mutableStateOf("dark")         // light, dark, amoled, system
    var accent by mutableIntStateOf(0)           // Palette.accents indeksi
    var font by mutableStateOf("sans")           // sans, serif, mono, round
    var fontScale by mutableStateOf(1f)
    var lineHeight by mutableStateOf(1.5f)
    var name by mutableStateOf("")
    var lockType by mutableStateOf("none")       // none, pin, pattern
    var biometric by mutableStateOf(false)
    var lockDelay by mutableIntStateOf(0)        // saniye
    var secure by mutableStateOf(false)
    var remindOn by mutableStateOf(false)
    var remindHour by mutableIntStateOf(21)
    var remindMin by mutableIntStateOf(0)
    var remindDays by mutableIntStateOf(0b1111111) // bit0 = Pazartesi
    var memoryNotif by mutableStateOf(true)
    var goal by mutableIntStateOf(5)             // haftada kaç gün
    var openToEditor by mutableStateOf(false)
    var autoBackupUri by mutableStateOf("")
    var lastAutoBackup by mutableStateOf(0L)
    var showQuestion by mutableStateOf(true)
    var weatherAuto by mutableStateOf(false)

    fun init(c: Context) {
        sp = c.getSharedPreferences("iz", Context.MODE_PRIVATE)
        theme = sp.getString("theme", "dark")!!
        accent = sp.getInt("accent", 0)
        font = sp.getString("font", "sans")!!
        fontScale = sp.getFloat("fontScale", 1f)
        lineHeight = sp.getFloat("lineHeight", 1.5f)
        name = sp.getString("name", "")!!
        lockType = sp.getString("lockType", "none")!!
        biometric = sp.getBoolean("biometric", false)
        lockDelay = sp.getInt("lockDelay", 0)
        secure = sp.getBoolean("secure", false)
        remindOn = sp.getBoolean("remindOn", false)
        remindHour = sp.getInt("remindHour", 21)
        remindMin = sp.getInt("remindMin", 0)
        remindDays = sp.getInt("remindDays", 0b1111111)
        memoryNotif = sp.getBoolean("memoryNotif", true)
        goal = sp.getInt("goal", 5)
        openToEditor = sp.getBoolean("openToEditor", false)
        autoBackupUri = sp.getString("autoBackupUri", "")!!
        lastAutoBackup = sp.getLong("lastAutoBackup", 0L)
        showQuestion = sp.getBoolean("showQuestion", true)
        weatherAuto = sp.getBoolean("weatherAuto", false)
    }

    fun save() {
        sp.edit()
            .putString("theme", theme).putInt("accent", accent).putString("font", font)
            .putFloat("fontScale", fontScale).putFloat("lineHeight", lineHeight).putString("name", name)
            .putString("lockType", lockType).putBoolean("biometric", biometric).putInt("lockDelay", lockDelay)
            .putBoolean("secure", secure).putBoolean("remindOn", remindOn).putInt("remindHour", remindHour)
            .putInt("remindMin", remindMin).putInt("remindDays", remindDays).putBoolean("memoryNotif", memoryNotif)
            .putInt("goal", goal).putBoolean("openToEditor", openToEditor).putString("autoBackupUri", autoBackupUri)
            .putLong("lastAutoBackup", lastAutoBackup).putBoolean("showQuestion", showQuestion)
            .putBoolean("weatherAuto", weatherAuto)
            .apply()
    }

    fun str(k: String) = sp.getString(k, "") ?: ""
    fun putStr(k: String, v: String) = sp.edit().putString(k, v).apply()
    fun long(k: String) = sp.getLong(k, 0L)
    fun putLong(k: String, v: Long) = sp.edit().putLong(k, v).apply()
}
