package com.iz.gunluk

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** kind: "check" (yaptım/yapmadım), "count" (bardak, sayfa…), "hours" (uyku saati) */
data class HabitDef(val key: String, val name: String, val kind: String, val target: Double, val icon: String, val unit: String = "") {
    fun json() = JSONObject().put("key", key).put("name", name).put("kind", kind).put("target", target).put("icon", icon).put("unit", unit)
    companion object {
        fun from(o: JSONObject) = HabitDef(o.getString("key"), o.getString("name"), o.optString("kind", "check"), o.optDouble("target", 1.0), o.optString("icon", "star"), o.optString("unit"))
    }
}

object Habits {
    val defs = mutableStateListOf<HabitDef>()
    /** gün (epochDay) → alışkanlık anahtarı → değer */
    val log = mutableStateMapOf<Long, Map<String, Double>>()

    val defaults = listOf(
        HabitDef("uyku", "Uyku", "hours", 8.0, "sleep", "saat"),
        HabitDef("su", "Su", "count", 8.0, "water", "bardak"),
        HabitDef("spor", "Spor", "check", 1.0, "sport"),
        HabitDef("okuma", "Okuma", "check", 1.0, "book"),
    )

    val icons: List<Pair<String, ImageVector>> = listOf(
        "sleep" to Icons.Rounded.Bedtime, "water" to Icons.Rounded.WaterDrop, "sport" to Icons.Rounded.FitnessCenter,
        "book" to Icons.Rounded.MenuBook, "walk" to Icons.Rounded.DirectionsWalk, "pray" to Icons.Rounded.SelfImprovement,
        "food" to Icons.Rounded.Restaurant, "code" to Icons.Rounded.Code, "music" to Icons.Rounded.MusicNote,
        "pill" to Icons.Rounded.Medication, "nophone" to Icons.Rounded.PhonelinkOff, "study" to Icons.Rounded.School,
        "star" to Icons.Rounded.Star, "heart" to Icons.Rounded.Favorite,
    )
    fun icon(k: String) = icons.firstOrNull { it.first == k }?.second ?: Icons.Rounded.Star

    fun value(day: Long, key: String) = log[day]?.get(key) ?: 0.0
    /** Etken analizi için: o gün uyku saati (girildiyse) ve spor yapılıp yapılmadığı (o gün kayıt varsa). */
    fun sleepOf(day: Long): Double? = log[day]?.get("uyku")?.takeIf { it > 0 }
    fun sportOf(day: Long): Boolean? = log[day]?.let { m -> if (defs.none { it.key == "spor" }) null else (m["spor"] ?: 0.0) >= 1.0 }

    fun done(d: HabitDef, v: Double) = when (d.kind) { "check" -> v >= 1.0; else -> v >= d.target }

    fun set(day: Long, key: String, v: Double) {
        val m = (log[day] ?: emptyMap()).toMutableMap()
        if (v <= 0.0) m.remove(key) else m[key] = v
        if (m.isEmpty()) log.remove(day) else log[day] = m
        Store.saveHabits()
    }

    fun toggle(day: Long, d: HabitDef) = set(day, d.key, if (done(d, value(day, d.key))) 0.0 else 1.0)

    fun addDef(name: String, kind: String, target: Double, icon: String, unit: String) {
        defs += HabitDef("h" + System.currentTimeMillis().toString(36), name.trim(), kind, if (kind == "check") 1.0 else target.coerceAtLeast(1.0), icon, unit.trim())
        Store.saveHabits()
    }
    /** Tanım silinir; geçmiş kayıtlar istatistik için saklanır. */
    fun removeDef(key: String) { defs.removeAll { it.key == key }; Store.saveHabits() }
    fun moveDef(key: String, up: Boolean) {
        val i = defs.indexOfFirst { it.key == key }; val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in defs.indices) return
        val d = defs.removeAt(i); defs.add(j, d); Store.saveHabits()
    }

    /** Son [days] günde hedefe ulaşılan gün oranı. */
    fun rate(d: HabitDef, days: Int = 30, today: Long = LocalDate.now().toEpochDay()): Float =
        (0 until days).count { done(d, value(today - it, d.key)) } / days.toFloat()

    fun streak(d: HabitDef, today: Long = LocalDate.now().toEpochDay()): Int {
        var x = if (done(d, value(today, d.key))) today else today - 1
        var n = 0
        while (done(d, value(x, d.key))) { n++; x-- }
        return n
    }

    fun toJson(): JSONObject {
        val l = JSONObject()
        for ((day, m) in log) l.put(day.toString(), JSONObject(m.mapValues { it.value }))
        return JSONObject().put("defs", JSONArray(defs.map { it.json() })).put("log", l)
    }

    fun load(o: JSONObject?) {
        defs.clear(); log.clear()
        if (o == null) { defs.addAll(defaults); return }
        val a = o.optJSONArray("defs")
        if (a == null) defs.addAll(defaults) else for (i in 0 until a.length()) defs += HabitDef.from(a.getJSONObject(i))
        val l = o.optJSONObject("log") ?: return
        for (k in l.keys()) {
            val m = l.getJSONObject(k)
            log[k.toLong()] = m.keys().asSequence().associateWith { m.getDouble(it) }
        }
    }

    /** İçe aktarmada birleştir: tanımlar anahtara göre, kayıtlar gün+anahtara göre. */
    fun merge(o: JSONObject) {
        val a = o.optJSONArray("defs")
        if (a != null) for (i in 0 until a.length()) { val d = HabitDef.from(a.getJSONObject(i)); if (defs.none { it.key == d.key }) defs += d }
        val l = o.optJSONObject("log") ?: return
        for (k in l.keys()) {
            val m = l.getJSONObject(k); val day = k.toLong()
            log[day] = (log[day] ?: emptyMap()) + m.keys().asSequence().associateWith { m.getDouble(it) }
        }
    }
}
