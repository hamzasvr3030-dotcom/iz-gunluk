package com.iz.gunluk

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StatsScreen() {
    val c = Iz.c
    @Suppress("UNUSED_VARIABLE") val v = Store.version
    var ym by remember { mutableStateOf(YearMonth.now()) }
    val all = Store.visible().filter { !it.isEmpty }
    val month = all.filter { YearMonth.from(it.date) == ym }
    val days = all.map { it.day }.toSet()
    val ms = remember(Store.version, ym, Lock.hiddenOpen) { Insights.summary(month) }
    val total = remember(Store.version, Lock.hiddenOpen) { Insights.summary(all) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        if (all.isEmpty()) { EmptyState(Icons.Rounded.Insights, "Veriler yazdıkça birikir", "Birkaç gün yazdıktan sonra ruh hâlin, alışkanlıkların ve değişimin burada görünür."); return@Column }

        // Genel
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IzCard(Modifier.weight(1f)) { Stat("${Insights.streak(days)}", "Güncel seri") }
            IzCard(Modifier.weight(1f)) { Stat("${Insights.longestStreak(days)}", "En uzun seri") }
            IzCard(Modifier.weight(1f)) { Stat("${days.size}", "Toplam gün") }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IzCard(Modifier.weight(1f)) { Stat(fmtNum(total.words), "Toplam kelime") }
            IzCard(Modifier.weight(1f)) { Stat("${total.photos}", "Fotoğraf") }
            IzCard(Modifier.weight(1f)) { Stat(total.bestMonth?.let { Tr.monthsShort[it.first.monthValue - 1] + " ${it.first.year % 100}" } ?: "–", "En çok yazılan ay") }
        }

        // Ay seçimi
        Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ym = ym.minusMonths(1) }) { Icon(Icons.Rounded.ChevronLeft, "Önceki ay", tint = c.text) }
            Text(Tr.monthYear(ym.atDay(1)), style = MaterialTheme.typography.titleLarge, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { if (ym < YearMonth.now()) ym = ym.plusMonths(1) }) { Icon(Icons.Rounded.ChevronRight, "Sonraki ay", tint = if (ym < YearMonth.now()) c.text else c.faint) }
        }
        IzCard {
            Row {
                Stat("${ms.daysWritten}/${ym.lengthOfMonth()}", "Bu ay yazılan gün", Modifier.weight(1f))
                Stat(ms.topMood?.first?.label ?: "–", "En çok seçilen ruh hâli", Modifier.weight(1f), ms.topMood?.first?.color ?: c.text)
            }
            Spacer(Modifier.height(14.dp))
            Row {
                Stat(ms.longest?.words?.toString() ?: "–", "En uzun yazı (kelime)", Modifier.weight(1f))
                Stat(ms.avgEnergy?.let { "%.1f".format(it) } ?: "–", "Ortalama enerji", Modifier.weight(1f))
            }
        }

        // Aylık ruh hâli grafiği
        SectionTitle("Ruh hâli akışı")
        IzCard {
            val line = Insights.moodLine(all, ym)
            MoodLineChart(line)
            Spacer(Modifier.height(10.dp))
            Row { Text("1", color = c.faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)); Text("${ym.lengthOfMonth()}", color = c.faint, style = MaterialTheme.typography.labelMedium) }
            ms.moodTrend?.let { t ->
                Spacer(Modifier.height(8.dp))
                Text(when { t > 0.4 -> "Ayın ikinci yarısında ruh hâlin yükseldi."; t < -0.4 -> "Ayın ikinci yarısında ruh hâlin biraz düştü."; else -> "Ruh hâlin bu ay dengeli seyretti." },
                    color = c.sub, style = MaterialTheme.typography.bodyMedium)
            }
        }
        SectionTitle("Duygu dağılımı")
        IzCard {
            val counts = Insights.moodCounts(month)
            if (counts.isEmpty()) Text("Bu ay ruh hâli seçilmemiş.", color = c.sub)
            val max = counts.maxOfOrNull { it.second } ?: 1
            counts.forEach { (m, n) ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    MoodBadge(m, 28.dp); Spacer(Modifier.width(10.dp))
                    Text(m.label, color = c.text, modifier = Modifier.width(92.dp), maxLines = 1)
                    Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(c.line)) {
                        Box(Modifier.fillMaxWidth(n.toFloat() / max).fillMaxHeight().clip(CircleShape).background(m.color))
                    }
                    Text("$n", color = c.sub, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                }
            }
        }

        // Yazma alışkanlığı: son 12 ay
        SectionTitle("Yazma alışkanlığı")
        IzCard {
            val md = Insights.monthlyDays(all)
            BarChart(md.map { it.second.toFloat() }, md.map { Tr.monthsShort[it.first.monthValue - 1].take(1) }, md.indexOfFirst { it.first == ym })
            Spacer(Modifier.height(8.dp))
            Text("Son 12 ayda her ay kaç gün yazdığın", color = c.sub, style = MaterialTheme.typography.bodyMedium)
        }
        SectionTitle("Haftanın günleri")
        IzCard {
            val wd = (1..7).map { d -> days.count { LocalDate.ofEpochDay(it).dayOfWeek.value == d }.toFloat() }
            BarChart(wd, Tr.daysShort, wd.indexOf(wd.maxOrNull() ?: 0f))
            total.bestWeekday?.let { Spacer(Modifier.height(8.dp)); Text("En çok ${Tr.days[it.first - 1].lowercase()} günleri yazıyorsun.", color = c.sub, style = MaterialTheme.typography.bodyMedium) }
        }
        if (total.topTags.isNotEmpty()) {
            SectionTitle("En çok kullanılan etiketler")
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                total.topTags.forEach { (t, n) -> Pill("#$t · $n") { Nav.listTag = t; Nav.go(Route.Entries) } }
            }
        }

        if (Habits.defs.isNotEmpty() && Habits.log.isNotEmpty()) {
            SectionTitle("Alışkanlıklar · son 30 gün")
            IzCard {
                val today = LocalDate.now().toEpochDay()
                Habits.defs.forEachIndexed { i, d ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    val rate = Habits.rate(d, 30)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Habits.icon(d.icon), null, tint = c.accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text(d.name, color = c.text, modifier = Modifier.weight(1f))
                        val extra = if (d.kind != "check") {
                            val vals = (0 until 30).map { Habits.value(today - it, d.key) }.filter { it > 0 }
                            if (vals.isNotEmpty()) " · ort. ${fmtD(Math.round(vals.average() * 10) / 10.0)} ${d.unit}" else ""
                        } else ""
                        Text("%${(rate * 100).toInt()}$extra", color = c.sub, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (k in 29 downTo 0) {
                            val ok = Habits.done(d, Habits.value(today - k, d.key))
                            Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(3.dp)).background(if (ok) c.accent else c.line))
                        }
                    }
                    val st = Habits.streak(d)
                    if (st > 1) Text("$st gündür devam ediyor", color = c.faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        SectionTitle("Özetler")
        IzCard(onClick = { Nav.go(Route.Summary(ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay(), "${Tr.monthYear(ym.atDay(1))} özeti")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = c.accent); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Bu ayın özeti", style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text("Öne çıkan anlar, kelimeler ve duygular · cihaz üzerinde", color = c.sub, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val y = LocalDate.now().year
        IzCard(onClick = { Nav.go(Route.Summary(LocalDate.of(y, 1, 1).toEpochDay(), LocalDate.of(y, 12, 31).toEpochDay(), "$y yılı özeti")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CalendarViewMonth, null, tint = c.accent); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("$y yıllık özet", style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text("Bir yılın anıları ve istatistikleri", color = c.sub, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

fun fmtNum(n: Int): String = "%,d".format(n).replace(',', '.')

@Composable
fun MoodLineChart(values: List<Double?>) {
    val c = Iz.c
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val w = size.width; val h = size.height
        for (k in 1..5) { val yy = h - (k - 1) / 4f * h; drawLine(c.line, Offset(0f, yy), Offset(w, yy), 1f) }
        val n = values.size
        if (n < 2) return@Canvas
        val pts = values.mapIndexedNotNull { i, v -> v?.let { Offset(i / (n - 1f) * w, h - ((it - 1) / 4f).toFloat() * h) } }
        if (pts.size >= 2) {
            val p = Path(); p.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) { val a = pts[i - 1]; val b = pts[i]; val mx = (a.x + b.x) / 2; p.cubicTo(mx, a.y, mx, b.y, b.x, b.y) }
            drawPath(p, c.accent, style = Stroke(5f, cap = StrokeCap.Round))
        }
        values.forEachIndexed { i, v ->
            if (v != null) {
                val col = Moods.all.filter { it.score == Math.round(v).toInt() }.firstOrNull()?.color ?: c.accent
                drawCircle(col, 9f, Offset(i / (n - 1f) * w, h - ((v - 1) / 4f).toFloat() * h))
            }
        }
    }
}

@Composable
fun BarChart(values: List<Float>, labels: List<String>, highlight: Int = -1) {
    val c = Iz.c
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val n = values.size; val gap = size.width / n; val bw = gap * 0.56f
            values.forEachIndexed { i, v ->
                val bh = (v / max) * size.height
                drawRoundRect(c.line, Offset(i * gap + (gap - bw) / 2, 0f), Size(bw, size.height), CornerRadius(bw / 2))
                if (v > 0) drawRoundRect(if (i == highlight) c.accent else c.accent.copy(alpha = 0.55f), Offset(i * gap + (gap - bw) / 2, size.height - bh), Size(bw, bh), CornerRadius(bw / 2))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row { labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = c.faint, style = MaterialTheme.typography.labelMedium, maxLines = 1) } }
    }
}

// ============================ Dönem / yıllık özet ============================
@Composable
fun SummaryScreen(r: Route.Summary) {
    val c = Iz.c
    val list = Store.visible().filter { !it.isEmpty && it.day in r.from..r.to }
    val s = remember(r, Store.version) { Insights.summary(list) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        BackBar(r.title, { Nav.back() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(Modifier.clip(CircleShape).background(c.accentSoft).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, tint = c.accent, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                Text("Cihaz üzerinde hazırlandı · hiçbir şey internete gönderilmedi", color = c.accent, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(14.dp))
            if (list.isEmpty()) { EmptyState(Icons.Rounded.AutoAwesome, "Bu dönemde kayıt yok", "Yazdıkça özetin burada oluşur."); return@Column }
            IzCard {
                Text(storyLine(s, list), style = MaterialTheme.typography.bodyLarge, color = c.text)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IzCard(Modifier.weight(1f)) { Stat("${s.daysWritten}", "Yazılan gün") }
                IzCard(Modifier.weight(1f)) { Stat(fmtNum(s.words), "Kelime") }
                IzCard(Modifier.weight(1f)) { Stat("${s.photos}", "Fotoğraf") }
            }
            if (s.highlights.isNotEmpty()) {
                SectionTitle("Öne çıkan satırlar")
                s.highlights.forEach { h ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Box(Modifier.width(3.dp).height(40.dp).clip(CircleShape).background(c.accent)); Spacer(Modifier.width(12.dp))
                        Text("“$h”", fontStyle = FontStyle.Italic, color = c.text)
                    }
                }
            }
            if (s.topWords.isNotEmpty()) {
                SectionTitle("Sık geçen kelimeler")
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val mx = s.topWords.first().second.toFloat()
                    s.topWords.forEach { (w, n) ->
                        Text(w, color = c.accent.copy(alpha = 0.45f + 0.55f * n / mx), style = if (n / mx > 0.6f) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clip(CircleShape).clickable { Nav.listQuery = w; Nav.go(Route.Entries) }.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            val counts = Insights.moodCounts(list)
            if (counts.isNotEmpty()) {
                SectionTitle("Duygular")
                Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
                    counts.forEach { (m, n) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(m.color)) }
                }
                Spacer(Modifier.height(8.dp))
                Text(counts.take(4).joinToString(" · ") { "${it.first.label} ${it.second}" }, color = c.sub, style = MaterialTheme.typography.bodyMedium)
            }
            if (r.to - r.from > 40) {
                SectionTitle("Aylara göre")
                val y = LocalDate.ofEpochDay(r.from).year
                val md = (1..12).map { m -> list.filter { it.date.monthValue == m }.map { it.day }.toSet().size.toFloat() }
                IzCard { BarChart(md, Tr.monthsShort.map { it.take(1) }, md.indexOf(md.maxOrNull() ?: 0f)) }
                val photos = list.sortedBy { it.day }.flatMap { e -> e.photos.map { e to it } }
                if (photos.isNotEmpty()) {
                    SectionTitle("$y yılından kareler")
                    photos.chunked(3).take(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (e, m) -> Thumb(m, Modifier.weight(1f).aspectRatio(1f).clickable { Nav.go(Route.Reader(e.id)) }) }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            s.brightest?.let { e ->
                SectionTitle("En parlak gün")
                EntryCard(e) { Nav.go(Route.Reader(e.id)) }
            }
            s.longest?.let { e ->
                SectionTitle("En uzun yazı · ${e.words} kelime")
                EntryCard(e) { Nav.go(Route.Reader(e.id)) }
            }
            if (s.places.isNotEmpty()) {
                SectionTitle("Gittiğin yerler")
                Text(s.places.joinToString(" · "), color = c.text)
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Özet paragrafı: sayılardan doğal dilde kısa bir anlatı. */
fun storyLine(s: Insights.Summary, list: List<Entry>): String = buildString {
    append("Bu dönemde ${s.daysWritten} gün yazdın ve toplam ${fmtNum(s.words)} kelime biriktirdin.")
    s.topMood?.let { append(" En çok “${it.first.label.lowercase()}” hissettin (${it.second} kez).") }
    s.avgEnergy?.let { append(" Enerjin ortalama ${"%.1f".format(it)}/10 idi.") }
    if (s.topTags.isNotEmpty()) append(" Gündeminde en çok ${s.topTags.take(3).joinToString(", ") { "#" + it.first }} vardı.")
    s.bestWeekday?.let { append(" En çok ${Tr.days[it.first - 1].lowercase()} günleri yazdın.") }
    if (s.favorites > 0) append(" ${s.favorites} anıyı favorilerine ekledin.")
    s.moodTrend?.let { if (it > 0.4) append(" Dönem ilerledikçe ruh hâlin yükseldi.") else if (it < -0.4) append(" Dönemin sonuna doğru biraz yoruldun; kendine nazik ol.") }
    if (list.size == 1) clear().append("Bu dönemde tek bir kayıt var. Yazdıkça özet zenginleşir.")
}

