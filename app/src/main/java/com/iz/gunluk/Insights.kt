package com.iz.gunluk

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Tüm istatistik hesapları saf fonksiyonlardır (birim testleriyle doğrulanır). */
object Insights {

    /** Bugünden (veya bugün yazılmadıysa dünden) geriye kesintisiz yazılan gün sayısı. */
    fun streak(days: Set<Long>, today: Long = LocalDate.now().toEpochDay()): Int {
        var d = if (today in days) today else today - 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }

    fun longestStreak(days: Set<Long>): Int {
        var best = 0; var cur = 0; var prev = Long.MIN_VALUE
        for (d in days.sorted()) { cur = if (d == prev + 1) cur + 1 else 1; best = maxOf(best, cur); prev = d }
        return best
    }

    /** Bu hafta (Pazartesi başlangıçlı) yazılan gün sayısı. */
    fun weekCount(days: Set<Long>, today: LocalDate = LocalDate.now()): Int {
        val mon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay()
        return (mon..mon + 6).count { it in days }
    }

    fun moodCounts(list: List<Entry>): List<Pair<Mood, Int>> =
        list.mapNotNull { Moods[it.mood] }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }

    /** Her gün için baskın ruh hâli: gündeki en son girdinin ruh hâli. */
    fun dayMoods(list: List<Entry>): Map<Long, Mood> = list.filter { it.mood != null }
        .groupBy { it.day }.mapValues { (_, v) -> Moods[v.maxBy { it.time + it.created }.mood]!! }

    data class Summary(
        val entries: Int, val daysWritten: Int, val words: Int, val avgWords: Int,
        val longest: Entry?, val topMood: Pair<Mood, Int>?, val avgEnergy: Double?,
        val topTags: List<Pair<String, Int>>, val topWords: List<Pair<String, Int>>,
        val bestMonth: Pair<YearMonth, Int>?, val bestWeekday: Pair<Int, Int>?,
        val highlights: List<String>, val photos: Int, val favorites: Int, val places: List<String>,
        val brightest: Entry?, val moodTrend: Double?,
    )

    private val stop = setOf(
        "ve", "ile", "bir", "bu", "şu", "o", "da", "de", "ki", "mi", "mı", "mu", "mü", "ama", "fakat", "çok", "daha", "en",
        "gibi", "için", "ne", "ben", "sen", "biz", "siz", "onlar", "benim", "bana", "beni", "seni", "sana", "onu", "ona",
        "her", "hiç", "şey", "şeyler", "kadar", "sonra", "önce", "zaten", "yine", "hem", "ya", "veya", "ise", "olarak",
        "olan", "oldu", "olduğu", "var", "yok", "değil", "bugün", "dün", "yarın", "gün", "biraz", "artık", "diye", "çünkü",
        "nasıl", "neden", "niye", "kendi", "bütün", "tüm", "şimdi", "sadece", "bile", "işte", "evet", "hayır", "bunu", "buna",
        "bunlar", "şunu", "onun", "bizim", "sizin", "olur", "olsun", "olmak", "etti", "ettim", "yaptım", "yaptı", "oldum",
        "çıktım", "gittim", "geldim", "the", "and", "to", "of", "a", "in", "is", "it", "that", "i",
    )

    fun summary(list: List<Entry>): Summary {
        val words = list.sumOf { it.words }
        val days = list.map { it.day }.toSet()
        val tags = list.flatMap { it.allTags }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.take(8)
        val freq = HashMap<String, Int>()
        for (e in list) for (w in Text.wordList(e.title + " " + e.plain)) if (w.length >= 3 && w !in stop && !w.all { it.isDigit() }) freq[w] = (freq[w] ?: 0) + 1
        val topWords = freq.toList().sortedByDescending { it.second }.take(12)
        val months = list.groupBy { YearMonth.from(it.date) }.mapValues { it.value.map { e -> e.day }.toSet().size }
        val wd = list.map { it.day }.toSet().groupingBy { LocalDate.ofEpochDay(it).dayOfWeek.value }.eachCount()
        val energies = list.filter { it.energy > 0 }.map { it.energy }
        val scored = list.filter { Moods[it.mood] != null }
        val brightest = scored.maxByOrNull { Moods[it.mood]!!.score * 10 + it.energy }
        val trend = if (scored.size >= 4) {
            val s = scored.sortedBy { it.day }; val h = s.size / 2
            s.drop(h).map { Moods[it.mood]!!.score }.average() - s.take(h).map { Moods[it.mood]!!.score }.average()
        } else null
        return Summary(
            entries = list.size, daysWritten = days.size, words = words,
            avgWords = if (list.isEmpty()) 0 else words / list.size,
            longest = list.maxByOrNull { it.words }?.takeIf { it.words > 0 },
            topMood = moodCounts(list).firstOrNull(),
            avgEnergy = energies.takeIf { it.isNotEmpty() }?.average(),
            topTags = tags, topWords = topWords,
            bestMonth = months.maxByOrNull { it.value }?.toPair(),
            bestWeekday = wd.maxByOrNull { it.value }?.toPair(),
            highlights = highlights(list, topWords.map { it.first }.toSet()),
            photos = list.sumOf { it.photos.size }, favorites = list.count { it.fav },
            places = list.map { it.place }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.map { it.first }.take(6),
            brightest = brightest, moodTrend = trend,
        )
    }

    /**
     * Cihaz üzerinde, internete hiçbir şey göndermeden "özet": dönemin anahtar kelimelerini en çok
     * içeren ve favori/ yüksek ruh hâlli girdilerden gelen cümleleri seçer (çıkarımsal özet).
     */
    fun highlights(list: List<Entry>, keys: Set<String>, max: Int = 5): List<String> {
        data class S(val text: String, val score: Double, val day: Long)
        val cands = ArrayList<S>()
        for (e in list) {
            val bonus = (if (e.fav) 1.5 else 0.0) + (Moods[e.mood]?.score ?: 3) * 0.15
            for (sent in e.plain.split(Regex("(?<=[.!?…])\\s+|\\n+"))) {
                val t = sent.trim().trimStart('•', '“', '☐', '☑', ' ').trimEnd('”')
                val w = Text.wordList(t)
                if (w.size < 5 || w.size > 40) continue
                val hit = w.count { it in keys }
                cands += S(t, hit.toDouble() / Math.sqrt(w.size.toDouble()) + bonus, e.day)
            }
        }
        val picked = cands.sortedByDescending { it.score }.distinctBy { it.day }.take(max)
        return picked.sortedBy { it.day }.map { it.text }
    }

    /** Son n ayın her biri için yazılan gün sayısı (eskiden yeniye). */
    fun monthlyDays(list: List<Entry>, n: Int = 12, now: YearMonth = YearMonth.now()): List<Pair<YearMonth, Int>> {
        val byMonth = list.groupBy { YearMonth.from(it.date) }.mapValues { it.value.map { e -> e.day }.toSet().size }
        return (n - 1 downTo 0).map { now.minusMonths(it.toLong()) }.map { it to (byMonth[it] ?: 0) }
    }

    /** Aydaki her gün için ruh hâli puanı ortalaması (yoksa null). */
    fun moodLine(list: List<Entry>, ym: YearMonth): List<Double?> {
        val m = list.filter { YearMonth.from(it.date) == ym && Moods[it.mood] != null }.groupBy { it.date.dayOfMonth }
        return (1..ym.lengthOfMonth()).map { d -> m[d]?.map { Moods[it.mood]!!.score }?.average() }
    }

    /** Geçmiş yıllarda bugünle aynı ay-gündeki girdiler. */
    fun onThisDay(list: List<Entry>, today: LocalDate = LocalDate.now()): List<Entry> =
        list.filter { it.date.monthValue == today.monthValue && it.date.dayOfMonth == today.dayOfMonth && it.date.year < today.year }
            .sortedByDescending { it.day }
}
