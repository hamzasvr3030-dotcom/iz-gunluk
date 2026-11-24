package com.iz.gunluk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Günlük burç yorumu: Milliyet Pembenar (iş, para, aşk, sağlık bölümleri), olmazsa Hürriyet Mahmure.
 * Yalnızca burç adı istenir; kişisel hiçbir veri gönderilmez. Gün başına bir kez çekilir, önbelleğe alınır.
 */
object Horoscope {
    data class Part(val title: String, val text: String)
    data class Reading(val sign: String, val day: Long, val parts: List<Part>, val source: String, val url: String) {
        fun json() = JSONObject().put("sign", sign).put("day", day).put("source", source).put("url", url)
            .put("parts", JSONArray(parts.map { JSONObject().put("t", it.title).put("x", it.text) }))
        companion object {
            fun from(o: JSONObject) = Reading(o.getString("sign"), o.getLong("day"),
                o.getJSONArray("parts").let { a -> (0 until a.length()).map { Part(a.getJSONObject(it).optString("t"), a.getJSONObject(it).getString("x")) } },
                o.optString("source"), o.optString("url"))
        }
    }

    fun milliyetUrl(slug: String) = "https://www.milliyet.com.tr/pembenar/astroloji/$slug-burcu-gunluk-yorum/"
    fun hurriyetUrl(slug: String) = "https://www.hurriyet.com.tr/mahmure/astroloji/$slug-burcu/"

    fun cached(sign: String, day: Long = LocalDate.now().toEpochDay()): Reading? =
        Prefs.str("horo").takeIf { it.isNotEmpty() }?.let { runCatching { Reading.from(JSONObject(it)) }.getOrNull() }?.takeIf { it.sign == sign && it.day == day }

    suspend fun get(sign: String, force: Boolean = false): Reading? {
        val today = LocalDate.now().toEpochDay()
        if (!force) cached(sign, today)?.let { return it }
        val s = Signs[sign]
        val r = withContext(Dispatchers.IO) {
            runCatching { fetch(milliyetUrl(s.slug))?.let { parseMilliyet(it) } }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?.let { Reading(sign, today, it, "Milliyet Pembenar", milliyetUrl(s.slug)) }
                ?: runCatching { fetch(hurriyetUrl(s.slug))?.let { parseHurriyet(it) } }.getOrNull()?.takeIf { it.isNotEmpty() }
                    ?.let { Reading(sign, today, it, "Hürriyet Mahmure", hurriyetUrl(s.slug)) }
        }
        if (r != null) Prefs.putStr("horo", r.json().toString())
        return r
    }

    private fun fetch(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130 Mobile Safari/537.36")
        c.setRequestProperty("Accept-Language", "tr-TR,tr;q=0.9")
        if (c.responseCode != 200) return null
        return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private val pRx = Regex("(?s)<p[^>]*>(.*?)</p>")
    private val strongRx = Regex("(?s)^\\s*<strong>(.*?)</strong>\\s*")

    fun parseMilliyet(html: String): List<Part> {
        val i = html.indexOf("horoscope-tabs__content__main-inner")
        if (i < 0) return emptyList()
        val end = html.indexOf("</section>", i).let { if (it < 0) html.length else it }
        val box = html.substring(i, end)
        return pRx.findAll(box).mapNotNull { m ->
            val inner = m.groupValues[1]
            val st = strongRx.find(inner)
            val title = st?.groupValues?.get(1)?.let { clean(it).trimEnd(':', ' ') } ?: ""
            val text = clean(if (st != null) inner.substring(st.range.last + 1) else inner)
            text.takeIf { it.length > 20 }?.let { Part(title, it) }
        }.toList()
    }

    fun parseHurriyet(html: String): List<Part> {
        val i = html.indexOf("horoscope-detail-tab-content")
        if (i < 0) return emptyList()
        val end = html.indexOf("horoscope-detail-footer", i).let { if (it < 0) html.length else it }
        return pRx.findAll(html.substring(i, end)).map { clean(it.groupValues[1]) }
            .filter { it.length > 40 && !it.endsWith("...") }.map { Part("", it) }.toList()
    }

    private val ents = mapOf(
        "amp" to "&", "quot" to "\"", "apos" to "'", "lt" to "<", "gt" to ">", "nbsp" to " ", "rsquo" to "’", "lsquo" to "‘",
        "rdquo" to "”", "ldquo" to "“", "hellip" to "…", "ndash" to "–", "mdash" to "—",
        "ouml" to "ö", "Ouml" to "Ö", "uuml" to "ü", "Uuml" to "Ü", "ccedil" to "ç", "Ccedil" to "Ç",
        "acirc" to "â", "Acirc" to "Â", "icirc" to "î", "ucirc" to "û", "eacute" to "é",
    )

    /** Etiketleri atar, HTML varlıklarını çözer, boşlukları sadeleştirir. */
    fun clean(s: String): String {
        val noTags = s.replace(Regex("<br\\s*/?>"), " ").replace(Regex("<[^>]+>"), "")
        val dec = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);").replace(noTags) { m ->
            val k = m.groupValues[1]
            when {
                k.startsWith("#x") || k.startsWith("#X") -> k.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                k.startsWith("#") -> k.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> ents[k] ?: m.value
            }
        }
        return dec.replace(Regex("\\s+"), " ").trim()
    }
}
