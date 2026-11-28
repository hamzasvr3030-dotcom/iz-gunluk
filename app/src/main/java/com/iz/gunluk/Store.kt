package com.iz.gunluk

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Store.init(this)
        Reminders.createChannel(this)
        Reminders.schedule(this)
        QuickMood.schedule(this)
        Capsules.register(this)
        kasa.sdk.KasaSdk.init(this, backup = { out -> Backup.writeZip(out) }, restore = { inp -> Backup.restoreZip(inp) })
    }
}

/**
 * Tüm veriler cihazda: files/data (json, Keystore anahtarıyla AES-256 şifreli) ve files/media.
 * Sahte PIN ile açıldığında files/data/decoy içindeki ayrı, boş bir günlük kullanılır.
 */
object Store {
    lateinit var ctx: Context
    val entries = mutableStateListOf<Entry>()
    val letters = mutableStateListOf<Letter>()
    /** Her değişiklikte artar; türetilmiş hesapların yeniden yapılması için. */
    var version by mutableIntStateOf(0)
    /** Şifreli veri okunamadıysa true: üzerine yazmayı engeller, kullanıcı uyarılır. */
    var loadError by mutableStateOf(false)
    var decoy by mutableStateOf(false); private set
    private val io = Executors.newSingleThreadExecutor()

    private val baseDir get() = File(ctx.filesDir, "data").apply { mkdirs() }
    val dataDir get() = (if (decoy) File(baseDir, "decoy") else baseDir).apply { mkdirs() }
    val mediaDir get() = File(ctx.filesDir, "media").apply { mkdirs() }
    fun media(name: String) = File(mediaDir, name)

    fun init(c: Context) {
        ctx = c.applicationContext
        load()
        // yazarken eklenip sonra kaldırılan medya dosyalarını temizle (1 günden eski, hiçbir girdide kullanılmayan)
        if (!loadError) runCatching { Trash.sweep() }
        val used = (entries.toList() + Trash.items()).flatMap { it.media.map { m -> m.file } }.toSet()
        if (!loadError) io.execute {
            val decoyUsed = runCatching { File(baseDir, "decoy/entries.json").takeIf { it.exists() }?.let { Codec.entriesFrom(String(Crypto.open(it.readBytes()))) }?.flatMap { e -> e.media.map { it.file } } }.getOrNull().orEmpty().toSet()
            mediaDir.listFiles()?.filter { it.name !in used && it.name !in decoyUsed && System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        }
    }

    private fun load() {
        loadError = false
        Trash.reset()
        entries.clear(); letters.clear()
        entries.addAll(read("entries.json")?.let { Codec.entriesFrom(it) } ?: emptyList())
        letters.addAll(read("letters.json")?.let { Codec.lettersFrom(it) } ?: emptyList())
        Habits.load(read("habits.json")?.let { runCatching { JSONObject(it) }.getOrNull() })
        version++
        // eski şifresiz dosyaları şifreli hâle getir
        if (!loadError && listOf("entries.json", "letters.json").any { f -> File(dataDir, f).let { it.exists() && !Crypto.isSealed(it.readBytes()) } }) persist()
    }

    private fun read(name: String): String? {
        val f = File(dataDir, name)
        var existed = false
        for (cand in listOf(f, File(f.path + ".bak"))) {
            if (!cand.exists()) continue
            existed = true
            try {
                val s = String(Crypto.open(cand.readBytes()))
                if (s.isNotBlank()) { if (s.trimStart().startsWith("[")) org.json.JSONArray(s) else JSONObject(s); return s }
            } catch (_: Exception) { }
        }
        if (existed && f.length() > 0) loadError = true
        return null
    }

    private fun write(name: String, text: String) {
        val f = File(dataDir, name)
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(Crypto.seal(text.toByteArray()))
        if (f.exists()) f.copyTo(File(f.path + ".bak"), overwrite = true)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    @Volatile private var pending: Triple<String, String, String>? = null
    @Volatile private var pendingDir: File? = null
    private val scheduled = AtomicBoolean(false)

    /** Hızlı arka arkaya kayıtlar tek yazmada birleşir; şifreleme ana iş parçacığını yormaz. */
    private fun persist() {
        version++
        if (loadError) return
        pending = Triple(Codec.entriesToJson(entries.toList()).toString(), Codec.lettersToJson(letters.toList()).toString(), Habits.toJson().toString())
        pendingDir = dataDir
        if (!scheduled.getAndSet(true)) io.execute {
            scheduled.set(false)
            val p = pending ?: return@execute
            try { write("entries.json", p.first); write("letters.json", p.second); write("habits.json", p.third) } catch (_: Exception) { }
        }
        IzWidget.update(ctx)
    }

    /** Bekleyen yazmaları bitirir (dışa aktarma öncesi). */
    fun flush() { io.submit { }.get() }

    fun saveHabits() = persist()

    /** Sahte PIN ↔ gerçek günlük geçişi. */
    fun switchTo(d: Boolean) {
        if (d == decoy) return
        flush()
        decoy = d
        Versions.reset()
        load()
        IzWidget.update(ctx)
    }

    // -------- girdiler --------
    fun get(id: String) = entries.firstOrNull { it.id == id }

    fun upsert(e: Entry) {
        val i = entries.indexOfFirst { it.id == e.id }
        if (i >= 0) { Versions.record(entries[i], e); entries[i] = e } else entries.add(e)
        persist()
    }

    /** Toplu güncelleme (ör. Seyir ekran süresi): tek kayıt. */
    fun mapAll(f: (Entry) -> Entry) {
        var changed = false
        for (i in entries.indices) { val n = f(entries[i]); if (n != entries[i]) { entries[i] = n; changed = true } }
        if (changed) persist()
    }

    /** Silinen girdiler kalıcı silinmez; 30 gün çöp kutusunda bekler. */
    fun delete(id: String) {
        val e = get(id) ?: return
        entries.removeAll { it.id == id }
        Trash.put(e)
        persist()
    }

    fun restore(e: Entry) { Trash.remove(e.id); upsert(e) }

    fun purgeMedia(e: Entry) {
        val used = entries.flatMap { it.media.map { m -> m.file } }.toSet() + Trash.items().flatMap { it.media.map { m -> m.file } }
        e.media.filter { it.file !in used }.forEach { media(it.file).delete() }
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
        if (t != null) upsert(t.copy(mood = m, extraMoods = t.extraMoods - m, updated = System.currentTimeMillis()))
        else upsert(newEntry().copy(mood = m))
    }

    /** Hızlı not: bugünün "Hızlı notlar" girdisine saatli madde olarak eklenir. */
    fun quickNote(text: String) {
        val t = text.trim(); if (t.isEmpty()) return
        val today = LocalDate.now().toEpochDay()
        val e = entries.firstOrNull { it.day == today && it.template == "quick" } ?: newEntry().copy(title = "Hızlı notlar", template = "quick")
        val line = "- ${nowTime()}  ${t.replace("\n", " ")}"
        upsert(e.copy(body = if (e.body.isBlank()) line else e.body.trimEnd() + "\n" + line, updated = System.currentTimeMillis()))
    }

    // -------- mektuplar --------
    fun upsertLetter(l: Letter) {
        val i = letters.indexOfFirst { it.id == l.id }
        if (i >= 0) letters[i] = l else letters.add(l)
        persist()
    }
    fun deleteLetter(id: String) { letters.removeAll { it.id == id }; persist() }

    /** İçe aktarma: id'ye göre birleştir. */
    fun importAll(es: List<Entry>, ls: List<Letter>, habits: JSONObject?) {
        val merged = Codec.merge(entries.toList(), es)
        entries.clear(); entries.addAll(merged)
        val lm = LinkedHashMap<String, Letter>(); letters.forEach { lm[it.id] = it }; ls.forEach { lm[it.id] = it }
        letters.clear(); letters.addAll(lm.values)
        habits?.let { Habits.merge(it) }
        persist()
    }
}

/** Çöp kutusu: silinen girdiler 30 gün saklanır (şifreli). */
object Trash {
    private fun file() = File(Store.dataDir, "trash.json")
    private var cache: MutableList<Pair<Long, Entry>>? = null

    private fun all(): MutableList<Pair<Long, Entry>> {
        cache?.let { return it }
        val l = mutableListOf<Pair<Long, Entry>>()
        runCatching {
            val f = file()
            if (f.exists()) {
                val a = org.json.JSONArray(String(Crypto.open(f.readBytes())))
                for (i in 0 until a.length()) { val o = a.getJSONObject(i); l += o.getLong("at") to Entry.from(o.getJSONObject("e")) }
            }
        }
        cache = l; return l
    }

    private fun save() {
        val a = org.json.JSONArray(all().map { JSONObject().put("at", it.first).put("e", it.second.json()) })
        runCatching { file().writeBytes(Crypto.seal(a.toString().toByteArray())) }
        Store.version++
    }

    fun reset() { cache = null }
    fun list(): List<Pair<Long, Entry>> = all().sortedByDescending { it.first }
    fun items(): List<Entry> = all().map { it.second }
    fun put(e: Entry) { all().removeAll { it.second.id == e.id }; all().add(System.currentTimeMillis() to e); save() }
    fun remove(id: String) { all().removeAll { it.second.id == id }; save() }
    fun purge(id: String) { val e = all().firstOrNull { it.second.id == id }?.second; remove(id); e?.let { Store.purgeMedia(it) } }
    fun empty() { val es = items(); all().clear(); save(); es.forEach { Store.purgeMedia(it) } }
    /** 30 günden eski olanları kalıcı sil. */
    fun sweep() {
        val old = all().filter { System.currentTimeMillis() - it.first > 30L * 86_400_000 }
        if (old.isEmpty()) return
        all().removeAll(old); save(); old.forEach { Store.purgeMedia(it.second) }
    }
}

/** Ayarlar: SharedPreferences + Compose durumu. */
object Prefs {
    private lateinit var sp: SharedPreferences

    var theme by mutableStateOf("iz")         // light, dark, amoled, paper, system
    var accent by mutableIntStateOf(0)
    var font by mutableStateOf("sans")
    var fontScale by mutableStateOf(1f)
    var lineHeight by mutableStateOf(1.5f)
    var name by mutableStateOf("")
    var lockType by mutableStateOf("none")       // none, pin, pattern
    var biometric by mutableStateOf(false)
    var lockDelay by mutableIntStateOf(0)
    var secure by mutableStateOf(false)
    var remindOn by mutableStateOf(false)
    var remindHour by mutableIntStateOf(21)
    var remindMin by mutableIntStateOf(0)
    var remindDays by mutableIntStateOf(0b1111111)
    var memoryNotif by mutableStateOf(true)
    var goal by mutableIntStateOf(5)
    var openToEditor by mutableStateOf(false)
    var autoBackupUri by mutableStateOf("")
    var lastAutoBackup by mutableStateOf(0L)
    var showQuestion by mutableStateOf(true)
    var weatherAuto by mutableStateOf(false)
    // 1.1.0
    var autoCorrect by mutableStateOf(true)
    var autoCap by mutableStateOf(true)
    var ruled by mutableStateOf(false)
    var cover by mutableIntStateOf(0)
    var coverTitle by mutableStateOf("Günlüğüm")
    var showSaying by mutableStateOf(true)
    var showHoroscope by mutableStateOf(true)
    var sign by mutableStateOf("yay")
    var showHabits by mutableStateOf(true)
    var hiddenBio by mutableStateOf(true)
    var lockAsked by mutableStateOf(false)
    var icon by mutableStateOf("IconNib")
    // 2.0
    var nightAuto by mutableStateOf(true)
    var seyirLink by mutableStateOf(false)
    var kadrajSong by mutableStateOf(true)
    var moodNotif by mutableStateOf(true)
    var moodHour by mutableIntStateOf(21)
    var suggestPhotos by mutableStateOf(true)

    fun init(c: Context) {
        sp = c.getSharedPreferences("iz", Context.MODE_PRIVATE)
        theme = sp.getString("theme", "iz")!!
        accent = sp.getInt("accent", 0)
        font = sp.getString("font", "crimson")!!
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
        autoCorrect = sp.getBoolean("autoCorrect", true)
        autoCap = sp.getBoolean("autoCap", true)
        ruled = sp.getBoolean("ruled", false)
        cover = sp.getInt("cover", 0)
        coverTitle = sp.getString("coverTitle", "Günlüğüm")!!
        showSaying = sp.getBoolean("showSaying", true)
        showHoroscope = sp.getBoolean("showHoroscope", true)
        sign = sp.getString("sign", "yay")!!
        showHabits = sp.getBoolean("showHabits", true)
        hiddenBio = sp.getBoolean("hiddenBio", true)
        lockAsked = sp.getBoolean("lockAsked", false)
        icon = sp.getString("icon", "IconNib")!!
        nightAuto = sp.getBoolean("nightAuto", true)
        seyirLink = sp.getBoolean("seyirLink", false)
        kadrajSong = sp.getBoolean("kadrajSong", true)
        moodNotif = sp.getBoolean("moodNotif", true)
        moodHour = sp.getInt("moodHour", 21)
        suggestPhotos = sp.getBoolean("suggestPhotos", true)
        Moods.loadCustom(sp.getString("customMoods", "")!!)
        if (!sp.getBoolean("v2", false)) {
            // İz 2.0'a geçiş: yeni sıcak palet, Crimson Text yazı alanı
            theme = "iz"; accent = 0; if (font == "sans") font = "crimson"
            sp.edit().putBoolean("v2", true).apply(); save()
        }
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
            .putBoolean("weatherAuto", weatherAuto).putBoolean("autoCorrect", autoCorrect).putBoolean("autoCap", autoCap)
            .putBoolean("ruled", ruled).putInt("cover", cover).putString("coverTitle", coverTitle)
            .putBoolean("showSaying", showSaying).putBoolean("showHoroscope", showHoroscope).putString("sign", sign)
            .putBoolean("showHabits", showHabits).putBoolean("hiddenBio", hiddenBio).putBoolean("lockAsked", lockAsked)
            .putString("icon", icon).putString("customMoods", Moods.customJson())
            .putBoolean("nightAuto", nightAuto).putBoolean("seyirLink", seyirLink).putBoolean("kadrajSong", kadrajSong)
            .putBoolean("moodNotif", moodNotif).putInt("moodHour", moodHour).putBoolean("suggestPhotos", suggestPhotos)
            .apply()
    }

    fun str(k: String) = sp.getString(k, "") ?: ""
    fun putStr(k: String, v: String) = sp.edit().putString(k, v).apply()
    fun long(k: String) = sp.getLong(k, 0L)
    fun putLong(k: String, v: Long) = sp.edit().putLong(k, v).apply()

    /** Yedek parolası Keystore ile şifreli saklanır. */
    var backupPass: String
        get() = str("backupPass").let { if (it.isEmpty()) "" else Crypto.openString(it) }
        set(v) = putStr("backupPass", if (v.isEmpty()) "" else Crypto.sealString(v))
}
