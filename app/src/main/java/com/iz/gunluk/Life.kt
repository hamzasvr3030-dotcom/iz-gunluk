package com.iz.gunluk

import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt

// ============================ Günlük türleri ============================
data class Kind(val key: String, val name: String, val emoji: String, val color: Color, val template: String)

object Kinds {
    val all = listOf(
        Kind("minnet", "Minnet", "🙏", Color(0xFFF7955F), "sukran"),
        Kind("ruya", "Rüya", "🌙", Color(0xFF9B8CFF), "ruya"),
        Kind("seyahat", "Seyahat", "🧭", Color(0xFF36CFC9), "seyahat"),
        Kind("okul", "Okul", "📚", Color(0xFFEC6EA2), "okul"),
    )
    operator fun get(k: String?) = all.firstOrNull { it.key == k }
    /** Eski girdilerde tür alanı yoktu: şablondan çıkar. */
    fun of(e: Entry): Kind? = get(e.kind) ?: all.firstOrNull { it.template == e.template }
}

// ============================ Duygu çarkı ============================
/** 10 ana duygunun altındaki ince duygular. */
object Wheel {
    val fine: Map<String, List<String>> = linkedMapOf(
        "mutlu" to listOf("neşeli", "gururlu", "minnettar", "umutlu", "keyifli"),
        "huzurlu" to listOf("rahat", "güvende", "dingin", "hafiflemiş"),
        "sakin" to listOf("dengeli", "uyumlu", "sabırlı", "düşünceli"),
        "heyecanli" to listOf("coşkulu", "meraklı", "istekli", "şaşkın"),
        "odakli" to listOf("kararlı", "üretken", "motive", "verimli"),
        "yorgun" to listOf("bitkin", "uykulu", "tükenmiş", "isteksiz"),
        "uzgun" to listOf("yalnız", "kırgın", "özlem dolu", "hayal kırıklığı"),
        "ofkeli" to listOf("sinirli", "gergin", "kıskanç", "haksızlığa uğramış"),
        "kararsiz" to listOf("kaygılı", "endişeli", "şüpheli", "bunalmış"),
        "karisik" to listOf("buruk", "nostaljik", "boğuk", "duygusal"),
    )
    fun parentOf(f: String) = fine.entries.firstOrNull { f in it.value }?.key
}

// ============================ [[Girdi bağlantıları]] ============================
object Links {
    private val rx = Regex("\\[\\[([^\\[\\]\\n]{1,80})]]")
    fun targets(body: String): List<String> = rx.findAll(body).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.distinct().toList()

    /** Hedef metni bir girdiye çözer: tarih (2026-11-28 ya da 28.11.2026) veya başlık. */
    fun resolve(target: String, all: List<Entry>, self: String? = null): Entry? {
        val t = target.trim()
        val date = runCatching { LocalDate.parse(t) }.getOrNull()
            ?: Regex("^(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})$").find(t)?.let { m -> runCatching { LocalDate.of(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull() }
        val cand = all.filter { it.id != self && !it.isEmpty }
        if (date != null) return cand.filter { it.day == date.toEpochDay() }.maxByOrNull { it.words }
        val f = Text.fold(t)
        return cand.firstOrNull { Text.fold(it.title) == f } ?: cand.firstOrNull { it.title.isNotBlank() && Text.fold(it.title).startsWith(f) }
    }

    fun outgoing(e: Entry, all: List<Entry>): List<Pair<String, Entry?>> = targets(e.body).map { it to resolve(it, all, e.id) }
    fun backlinks(e: Entry, all: List<Entry>): List<Entry> = all.filter { o -> o.id != e.id && targets(o.body).any { resolve(it, all, o.id)?.id == e.id } }

    /** Yazarken "[[" sonrası öneri: başlıklı girdiler. */
    fun suggest(prefix: String, all: List<Entry>, limit: Int = 6): List<Entry> {
        val f = Text.fold(prefix)
        return all.filter { it.title.isNotBlank() && (f.isEmpty() || Text.fold(it.title).contains(f)) }.sortedByDescending { it.day }.take(limit)
    }
}

/** İmlecin hemen önündeki @kişi / #etiket / [[bağlantı yazımı. */
object Typing {
    data class Token(val kind: Char, val start: Int, val text: String)
    fun at(text: String, cursor: Int): Token? {
        if (cursor <= 0 || cursor > text.length) return null
        val pre = text.substring(0, cursor)
        val open = pre.lastIndexOf("[[")
        if (open >= 0 && !pre.substring(open).contains("]]") && !pre.substring(open).contains('\n')) return Token('[', open, pre.substring(open + 2))
        var i = cursor - 1
        while (i >= 0 && (pre[i].isLetterOrDigit() || pre[i] == '_')) i--
        if (i < 0) return null
        val c = pre[i]
        if (c != '@' && c != '#') return null
        if (i > 0 && (pre[i - 1].isLetterOrDigit() || pre[i - 1] == '.')) return null
        return Token(c, i, pre.substring(i + 1))
    }
}

// ============================ Kişiler ve yerler ============================
data class Person(val name: String, val entries: List<Entry>) { val days get() = entries.map { it.day }.distinct().size }
data class Place(val name: String, val lat: Double?, val lon: Double?, val entries: List<Entry>) { val photos get() = entries.sumOf { it.photos.size } }

object People {
    fun all(list: List<Entry>): List<Person> = list.flatMap { e -> e.people.map { Text.fold(it) to (it to e) } }
        .groupBy({ it.first }, { it.second }).map { (_, l) -> Person(l.first().first, l.map { it.second }.distinctBy { it.id }.sortedByDescending { it.day }) }
        .sortedByDescending { it.entries.size }
    fun of(name: String, list: List<Entry>): Person = Person(name, list.filter { e -> e.people.any { Text.fold(it) == Text.fold(name) } }.sortedByDescending { it.day })
    fun known(list: List<Entry>) = all(list).map { it.name }
}

object Places {
    fun all(list: List<Entry>): List<Place> = list.filter { it.place.isNotBlank() }.groupBy { Text.fold(it.place.substringBefore(',').trim()) }
        .map { (_, es) -> val withPos = es.firstOrNull { it.lat != null }; Place(es.first().place.substringBefore(',').trim(), withPos?.lat, withPos?.lon, es.sortedByDescending { it.day }) }
        .sortedByDescending { it.entries.size }
}

// ============================ Etken analizi ============================
/** Bir etkenin ruh hâline etkisi: etkenli günlerin ortalaması − etkensiz günlerin ortalaması (1–5 ölçeği). */
data class Factor(val key: String, val emoji: String, val label: String, val delta: Double, val withDays: Int, val withoutDays: Int, val days: Set<Long>) {
    val percent get() = (delta / 4.0 * 100).roundToInt()
}

object Factors {
    /** Günlük ruh hâli puanı (gündeki girdilerin ortalaması). */
    fun dayScores(list: List<Entry>): Map<Long, Double> = list.filter { Moods[it.mood] != null }.groupBy { it.day }
        .mapValues { (_, v) -> v.map { Moods[it.mood]!!.score.toDouble() }.average() }

    private fun factor(key: String, emoji: String, label: String, scores: Map<Long, Double>, has: (Long) -> Boolean?): Factor? {
        val w = scores.filter { has(it.key) == true }; val wo = scores.filter { has(it.key) == false }
        if (w.size < 3 || wo.size < 3) return null
        return Factor(key, emoji, label, w.values.average() - wo.values.average(), w.size, wo.size, w.keys)
    }

    /**
     * Uyku, spor, kişiler, hava, etiketler ve (izin varsa) ekran süresi. Tamamen cihazda; internet yok.
     * [sleepOf]/[sportOf] alışkanlık kayıtlarından gelir (yoksa null).
     */
    fun analyze(list: List<Entry>, sleepOf: (Long) -> Double? = { null }, sportOf: (Long) -> Boolean? = { null }): List<Factor> {
        val scores = dayScores(list)
        if (scores.size < 6) return emptyList()
        val byDay = list.groupBy { it.day }
        val out = mutableListOf<Factor>()
        fun sleep(d: Long): Double? = byDay[d]?.map { it.sleep }?.firstOrNull { it > 0 } ?: sleepOf(d)
        factor("sleep", "😴", "7 saatten fazla uyku", scores) { d -> sleep(d)?.let { it >= 7 } }?.let { out += it }
        val sportTags = setOf("spor", "yürüyüş", "koşu", "fitness", "yüzme", "bisiklet", "antrenman", "yoga", "salon")
        factor("sport", "🏃", "Yürüyüş / spor", scores) { d -> sportOf(d) ?: (byDay[d]?.any { e -> e.allTags.any { it in sportTags } } == true) }?.let { out += it }
        factor("people", "👥", "Arkadaşlarla vakit", scores) { d -> byDay[d]?.any { it.people.isNotEmpty() } == true }?.let { out += it }
        factor("screen", "📱", "Ekran süresi > 5 sa", scores) { d -> byDay[d]?.map { it.screenMin }?.firstOrNull { it >= 0 }?.let { it > 300 } }?.let { out += it }
        val sunny = Regex("güneş|açık|sunny|clear", RegexOption.IGNORE_CASE); val rainy = Regex("yağmur|kar|sağanak|rain|snow", RegexOption.IGNORE_CASE)
        factor("sun", "☀", "Güneşli hava", scores) { d -> byDay[d]?.map { it.weather }?.firstOrNull { it.isNotBlank() }?.let { sunny.containsMatchIn(it) } }?.let { out += it }
        factor("rain", "🌧", "Yağışlı hava", scores) { d -> byDay[d]?.map { it.weather }?.firstOrNull { it.isNotBlank() }?.let { rainy.containsMatchIn(it) } }?.let { out += it }
        // en sık 6 etiket ve 4 kişi
        val tags = list.flatMap { it.allTags }.groupingBy { it }.eachCount().filter { it.value >= 3 && it.key !in sportTags }.entries.sortedByDescending { it.value }.take(6)
        for (t in tags) factor("#" + t.key, "#", "#${t.key}", scores) { d -> byDay[d]?.any { t.key in it.allTags } == true }?.let { out += it }
        for (p in People.all(list).take(4)) factor("@" + p.name, "@", "@${p.name} ile", scores) { d -> byDay[d]?.any { e -> e.people.any { Text.fold(it) == Text.fold(p.name) } } == true }?.let { out += it }
        return out.filter { abs(it.delta) >= 0.15 }.sortedByDescending { abs(it.delta) }
    }

    /** Gözlemci, yargısız bir cümle. */
    fun sentence(f: Factor): String {
        val pct = abs(f.percent)
        val dir = if (f.delta > 0) "daha iyi" else "daha düşük"
        return when (f.key) {
            "sleep" -> "7 saatten fazla uyuduğun günlerde ruh hâlin ortalama %$pct $dir."
            "screen" -> "Ekran süren 5 saati aştığında ruh hâlin ortalama %$pct $dir."
            else -> "${f.label} olan günlerde ruh hâlin ortalama %$pct $dir."
        }
    }
}

// ============================ Haftalık duygu mektubu ============================
object WeeklyLetter {
    data class Week(val from: LocalDate, val to: LocalDate, val entries: List<Entry>)
    fun week(list: List<Entry>, ref: LocalDate = LocalDate.now()): Week {
        val mon = ref.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val sun = mon.plusDays(6)
        return Week(mon, sun, list.filter { it.day in mon.toEpochDay()..sun.toEpochDay() && !it.isEmpty })
    }

    /** Hafta özetini sıcak bir dille yazar (cihaz içi, şablon + istatistik). */
    fun write(w: Week, name: String = ""): String {
        val es = w.entries
        val hi = if (name.isNotBlank()) "Sevgili $name," else "Sevgili ben,"
        if (es.isEmpty()) return "$hi\n\nBu hafta pek yazmadın; olsun. Bazen yaşamak, yazmaktan önce gelir. Önümüzdeki hafta tek bir satırla başlamaya ne dersin?\n\nSevgiyle,\nİz"
        val days = es.map { it.day }.distinct().size
        val moods = es.mapNotNull { Moods[it.mood] }
        val top = moods.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val avg = moods.map { it.score }.average().takeIf { !it.isNaN() }
        val people = es.flatMap { it.people }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(2).map { "@" + it.key }
        val tags = es.flatMap { it.allTags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { "#" + it.key }
        val best = es.filter { Moods[it.mood] != null }.maxByOrNull { Moods[it.mood]!!.score * 1000 + it.words }
        val fine = es.flatMap { it.fine }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val words = es.sumOf { it.words }
        return buildString {
            append(hi).append("\n\n")
            append("Bu hafta $days gün yazdın, toplam $words kelime. ")
            if (top != null) append("En sık hissettiğin duygu \"${top.label.lowercase(java.util.Locale.forLanguageTag("tr"))}\" oldu")
            if (fine != null) append(", özellikle de kendini $fine hissettin")
            if (top != null) append(". ")
            avg?.let { append(when { it >= 4 -> "Genel olarak aydınlık bir haftaydı. "; it >= 3 -> "Dengeli, kendi ritminde bir haftaydı. "; else -> "Biraz ağır bir hafta geçirdin; bunu fark etmen bile değerli. " }) }
            if (people.isNotEmpty()) append("Yazılarında en çok ${people.joinToString(" ve ")} vardı. ")
            if (tags.isNotEmpty()) append("Haftanın izleri: ${tags.joinToString(", ")}. ")
            best?.let { append("\n\nEn güzel anlardan biri ${Tr.weekday(it.date)} günüydü") ; if (it.title.isNotBlank()) append(": \"${it.title}\""); append(". ") }
            append("\n\nKendine nazik ol; gelecek hafta da buradayım.\n\nSevgiyle,\nİz")
        }
    }
}

// ============================ Yılın İzi ============================
object YearStory {
    data class Story(
        val year: Int, val days: Int, val entries: Int, val words: Int,
        val topWord: String?, val happiestMonth: Int?, val topPerson: String?, val topPlace: String?,
        val moodShare: List<Pair<Mood, Int>>, val photos: Int, val longestStreak: Int, val line: String,
    )

    fun build(list: List<Entry>, year: Int): Story {
        val es = list.filter { it.date.year == year && !it.isEmpty }
        val s = Insights.summary(es)
        val monthMood = es.filter { Moods[it.mood] != null }.groupBy { it.date.monthValue }.filter { it.value.size >= 2 }
            .mapValues { (_, v) -> v.map { Moods[it.mood]!!.score }.average() }.maxByOrNull { it.value }?.key
        val person = People.all(es).firstOrNull()?.name
        val place = Places.all(es).firstOrNull()?.name
        val moods = Insights.moodCounts(es)
        val streak = Insights.longestStreak(es.map { it.day }.toSet())
        val calm = moods.filter { it.first.key in setOf("huzurlu", "sakin") }.sumOf { it.second }
        val line = when {
            es.isEmpty() -> "Bu yılın sayfaları henüz boş."
            calm * 3 >= es.size -> "Bu yıl daha çok dinledin, daha az acele ettin."
            moods.firstOrNull()?.first?.score ?: 3 >= 4 -> "Bu yıl gülümsemeyi unutmadın."
            streak >= 30 -> "Bu yıl kendine her gün biraz zaman ayırdın."
            else -> "Bu yıl iniş çıkışlarınla, olduğun gibi yazdın."
        }
        return Story(year, s.daysWritten, s.entries, s.words, s.topWords.firstOrNull()?.first, monthMood, person, place, moods, s.photos, streak, line)
    }
}

// ============================ Gece yazma modu ============================
object Night {
    fun active(hour: Int = java.time.LocalTime.now().hour) = Prefs.nightAuto && (hour >= 22 || hour < 5)
    /** Compose'da dakikada bir yeniden değerlendirilir. */
    private val tickState = androidx.compose.runtime.mutableIntStateOf(0)
    var tick: Int get() = tickState.intValue
        set(v) { tickState.intValue = v }
    val on: Boolean get() { tick; return active() }
}
