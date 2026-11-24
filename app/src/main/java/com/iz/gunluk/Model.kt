package com.iz.gunluk

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Girdiye eklenmiş medya: dosya adı uygulamanın media/ klasöründedir. */
data class Media(val file: String, val kind: String, val dur: Long = 0) {
    val isPhoto get() = kind == "photo"
    val isVideo get() = kind == "video"
    val isAudio get() = kind == "audio"

    fun json() = JSONObject().put("file", file).put("kind", kind).put("dur", dur)

    companion object {
        fun from(o: JSONObject) = Media(o.getString("file"), o.optString("kind", "photo"), o.optLong("dur"))
    }
}

data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
    /** Girdinin ait olduğu gün (LocalDate.toEpochDay). */
    val day: Long = LocalDate.now().toEpochDay(),
    /** Saat "HH:mm". */
    val time: String = "",
    val title: String = "",
    val body: String = "",
    val mood: String? = null,
    /** 0 = puanlanmadı, 1..10 */
    val energy: Int = 0,
    val tags: List<String> = emptyList(),
    val media: List<Media> = emptyList(),
    val place: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val weather: String = "",
    val fav: Boolean = false,
    val hidden: Boolean = false,
    val template: String = "",
    val question: String = "",
    /** Ana ruh hâline ek olarak seçilen diğer duygular. */
    val extraMoods: List<String> = emptyList(),
) {
    val moods: List<String> get() = listOfNotNull(mood) + extraMoods
    /** Metin içindeki @kişi etiketleri. */
    val people: List<String> get() = Text.people(body)
    /** Metnin içine yerleştirilmiş fotoğraflar. */
    val inlineFiles: Set<String> get() = RichText.inlinePhotos(body).toSet()
    val date: LocalDate get() = LocalDate.ofEpochDay(day)
    val plain: String get() = RichText.plain(body)
    val words: Int get() = Text.words(plain)
    val photos get() = media.filter { it.isPhoto }
    /** Elle eklenen etiketler + metin içindeki #etiketler. */
    val allTags: List<String> get() = (tags + Text.hashtags(body)).map { Text.normTag(it) }.filter { it.isNotEmpty() }.distinct()
    val isEmpty get() = title.isBlank() && body.isBlank() && mood == null && media.isEmpty() && energy == 0

    fun json(): JSONObject = JSONObject()
        .put("id", id).put("created", created).put("updated", updated).put("day", day).put("time", time)
        .put("title", title).put("body", body).put("mood", mood ?: JSONObject.NULL).put("energy", energy)
        .put("tags", JSONArray(tags)).put("media", JSONArray(media.map { it.json() }))
        .put("place", place).put("lat", lat ?: JSONObject.NULL).put("lon", lon ?: JSONObject.NULL)
        .put("weather", weather).put("fav", fav).put("hidden", hidden).put("template", template).put("question", question).put("extraMoods", JSONArray(extraMoods))

    companion object {
        fun from(o: JSONObject): Entry {
            val tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            val media = o.optJSONArray("media")?.let { a -> (0 until a.length()).map { Media.from(a.getJSONObject(it)) } } ?: emptyList()
            return Entry(
                id = o.getString("id"), created = o.optLong("created"), updated = o.optLong("updated"),
                day = o.optLong("day"), time = o.optString("time"), title = o.optString("title"), body = o.optString("body"),
                mood = if (o.isNull("mood")) null else o.optString("mood").ifEmpty { null },
                energy = o.optInt("energy"), tags = tags, media = media, place = o.optString("place"),
                lat = if (o.isNull("lat") || !o.has("lat")) null else o.getDouble("lat"),
                lon = if (o.isNull("lon") || !o.has("lon")) null else o.getDouble("lon"),
                weather = o.optString("weather"), fav = o.optBoolean("fav"), hidden = o.optBoolean("hidden"),
                template = o.optString("template"), question = o.optString("question"),
                extraMoods = o.optJSONArray("extraMoods")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            )
        }
    }
}

/** Geleceğe mektup: openDay gelene kadar içeriği gösterilmez. */
data class Letter(
    val id: String = UUID.randomUUID().toString(),
    val written: Long = System.currentTimeMillis(),
    val openDay: Long,
    val title: String,
    val body: String,
    val read: Boolean = false,
) {
    fun isOpen(today: Long = LocalDate.now().toEpochDay()) = today >= openDay
    fun json(): JSONObject = JSONObject().put("id", id).put("written", written).put("openDay", openDay)
        .put("title", title).put("body", body).put("read", read)

    companion object {
        fun from(o: JSONObject) = Letter(o.getString("id"), o.optLong("written"), o.getLong("openDay"),
            o.optString("title"), o.optString("body"), o.optBoolean("read"))
    }
}

object Codec {
    fun entriesToJson(list: List<Entry>) = JSONArray(list.map { it.json() })
    fun entriesFrom(s: String): List<Entry> {
        val a = JSONArray(s)
        return (0 until a.length()).mapNotNull { runCatching { Entry.from(a.getJSONObject(it)) }.getOrNull() }
    }
    fun lettersToJson(list: List<Letter>) = JSONArray(list.map { it.json() })
    fun lettersFrom(s: String): List<Letter> {
        val a = JSONArray(s)
        return (0 until a.length()).mapNotNull { runCatching { Letter.from(a.getJSONObject(it)) }.getOrNull() }
    }

    /** İki listeyi id'ye göre birleştirir; aynı girdide daha yeni güncellenen kazanır. */
    fun merge(base: List<Entry>, incoming: List<Entry>): List<Entry> {
        val m = LinkedHashMap<String, Entry>()
        base.forEach { m[it.id] = it }
        incoming.forEach { e -> val old = m[e.id]; if (old == null || e.updated >= old.updated) m[e.id] = e }
        return m.values.toList()
    }
}

object Text {
    private val tagRx = Regex("(?<![\\p{L}\\p{N}_])#([\\p{L}\\p{N}_]{2,40})")
    private val personRx = Regex("(?<![\\p{L}\\p{N}_.])@([\\p{L}\\p{N}_]{2,30})")
    fun people(s: String): List<String> = personRx.findAll(s).map { it.groupValues[1] }.distinctBy { fold(it) }.toList()
    private val wordRx = Regex("[\\p{L}\\p{N}']+")

    fun hashtags(s: String): List<String> = tagRx.findAll(s).map { it.groupValues[1] }.toList()
    fun normTag(s: String) = s.trim().removePrefix("#").lowercase(java.util.Locale.forLanguageTag("tr"))
    fun words(s: String) = wordRx.findAll(s).count()
    fun wordList(s: String) = wordRx.findAll(s).map { it.value.lowercase(java.util.Locale.forLanguageTag("tr")) }.toList()

    /** Türkçe büyük/küçük harf ve aksan duyarsız karşılaştırma için sadeleştirir. */
    fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s.lowercase(java.util.Locale.forLanguageTag("tr"))) sb.append(when (c) {
            'ı', 'i', 'î' -> 'i'; 'ş' -> 's'; 'ğ' -> 'g'; 'ü', 'û' -> 'u'; 'ö' -> 'o'; 'ç' -> 'c'; 'â' -> 'a'
            else -> c
        })
        return sb.toString()
    }

    /** Metin içinde (katlanmış) aramanın eşleşme başlangıçları. Uzunluk korunur. */
    fun findAll(text: String, q: String): List<Int> {
        if (q.isBlank()) return emptyList()
        val t = fold(text); val k = fold(q)
        val out = ArrayList<Int>(); var i = t.indexOf(k)
        while (i >= 0) { out += i; i = t.indexOf(k, i + maxOf(1, k.length)) }
        return out
    }
}
