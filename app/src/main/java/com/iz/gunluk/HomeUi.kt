package com.iz.gunluk

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun HomeScreen() {
    val c = Iz.c
    @Suppress("UNUSED_VARIABLE") val v = Store.version
    val today = LocalDate.now()
    val todays = Store.today()
    val todayMood = todays.mapNotNull { Moods[it.mood] }.lastOrNull()
    val days = Store.writtenDays
    val streak = Insights.streak(days)
    val week = Insights.weekCount(days)
    val past = remember(Store.version) { Insights.onThisDay(Store.visible(), today) }
    val openLetters = Store.letters.filter { it.isOpen() && !it.read }
    val q = Questions.forDay(today.toEpochDay())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(Tr.longDay(today))
                Spacer(Modifier.height(10.dp))
                Text(
                    if (Prefs.name.isNotBlank()) "${Tr.greeting(LocalTime.now().hour)}, ${Prefs.name}" else Tr.greeting(LocalTime.now().hour),
                    color = c.sub, style = MaterialTheme.typography.bodyLarge,
                )
                Text("Bugün nasıl geçti?", style = MaterialTheme.typography.displaySmall, color = c.text)
            }
            IconButton(onClick = { Nav.go(Route.Settings) }, modifier = Modifier.clip(CircleShape).background(c.card)) {
                Icon(Icons.Rounded.Settings, "Ayarlar", tint = c.sub)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Günün ruh hâli", style = MaterialTheme.typography.labelLarge, color = c.sub)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Moods.all) { m ->
                val on = todayMood?.key == m.key
                Column(
                    Modifier.clip(RoundS).background(if (on) m.color.copy(alpha = 0.18f) else c.card)
                        .clickable { Store.setTodayMood(m.key) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(m.icon, null, tint = if (todayMood == null || on) m.color else m.color.copy(alpha = 0.4f), modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(m.label, style = MaterialTheme.typography.labelMedium, color = if (on) c.text else c.sub)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        // Ana yazma kartı
        Column(
            Modifier.fillMaxWidth().clip(Round)
                .background(Brush.linearGradient(listOf(c.accent.copy(alpha = if (c.dark) 0.30f else 0.16f), c.card)))
                .clickable { Nav.write() }.padding(20.dp)
        ) {
            Text(if (todays.any { !it.isEmpty && it.body.isNotBlank() }) "Bugüne bir şey daha ekle" else "Bir gün daha, bir anı daha.",
                style = MaterialTheme.typography.titleLarge, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text("Bugün yaşadıklarını, düşündüklerini ve hissettiklerini kaydet.", color = c.sub)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(CircleShape).background(c.accent).padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, null, tint = c.onAccent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("Günlüğümü Yaz", color = c.onAccent, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.clip(CircleShape).background(c.card2).clickable { Nav.go(Route.Editor(template = "_pick")) }.padding(11.dp)) {
                    Icon(Icons.Rounded.Dashboard, "Şablon", tint = c.sub, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Box(Modifier.clip(CircleShape).background(c.card2).clickable { Nav.go(Route.Editor(template = "_voice")) }.padding(11.dp)) {
                    Icon(Icons.Rounded.KeyboardVoice, "Sesli günlük", tint = c.sub, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Bugünün girdileri
        if (todays.isNotEmpty()) {
            SectionTitle("Bugünün günlüğü")
            todays.forEach { e -> EntryCard(e, showDate = false) { Nav.go(Route.Reader(e.id)) }; Spacer(Modifier.height(10.dp)) }
        }

        // Seri ve hedef
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IzCard(Modifier.weight(1f)) {
                Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color(0xFFFF7A45))
                Spacer(Modifier.height(8.dp))
                Stat("$streak gün", if (streak > 0) "Üst üste yazdın" else "Seriyi bugün başlat")
            }
            IzCard(Modifier.weight(1f), onClick = { Nav.go(Route.Settings) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Flag, null, tint = c.accent)
                    Spacer(Modifier.weight(1f))
                    Text("$week/${Prefs.goal}", color = c.sub, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val mon = today.minusDays((today.dayOfWeek.value - 1).toLong())
                    for (i in 0..6) {
                        val d = mon.plusDays(i.toLong())
                        Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(when {
                            d.toEpochDay() in days -> c.accent
                            d == today -> c.accent.copy(alpha = 0.3f)
                            else -> c.line
                        }))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(if (week >= Prefs.goal) "Haftalık hedef tamam!" else "Haftalık hedef: ${Prefs.goal} gün", color = c.sub, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Açılan mektuplar
        openLetters.forEach { l ->
            Spacer(Modifier.height(12.dp))
            IzCard(onClick = { Nav.go(Route.LetterRead(l.id)) }, color = c.accentSoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MarkEmailUnread, null, tint = c.accent); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Geçmişten bir mektubun açıldı", style = MaterialTheme.typography.titleMedium, color = c.text)
                        Text(l.title.ifBlank { "Kendine yazdığın mektup" }, color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        // Günün sorusu
        if (Prefs.showQuestion) {
            Spacer(Modifier.height(12.dp))
            IzCard(onClick = { Nav.write(question = q) }) {
                Label("Günün sorusu")
                Spacer(Modifier.height(8.dp))
                Text(q, style = MaterialTheme.typography.titleMedium, color = c.text)
                Spacer(Modifier.height(6.dp))
                Text("Dokun ve cevabını yaz", color = c.sub, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Geçmişten bir anı
        Spacer(Modifier.height(12.dp))
        val memory = past.firstOrNull()
        IzCard(onClick = { if (memory != null) Nav.go(Route.Reader(memory.id)) }, color = c.card) {
            Label(if (memory != null) "Geçmişten bir anı · ${today.year - memory.date.year} yıl önce" else "Geçmişten bir anı", c.sub)
            Spacer(Modifier.height(8.dp))
            if (memory != null) {
                Text(memory.title.ifBlank { Tr.long(memory.date) }, style = MaterialTheme.typography.titleMedium, color = c.text)
                Spacer(Modifier.height(4.dp))
                Text("“${memory.plain.take(160).trim()}${if (memory.plain.length > 160) "…" else ""}”", fontStyle = FontStyle.Italic, color = c.sub)
                memory.photos.firstOrNull()?.let { Spacer(Modifier.height(10.dp)); Thumb(it, Modifier.fillMaxWidth().height(160.dp)) }
            } else {
                Text("“${Quotes.forDay(today.toEpochDay())}”", fontStyle = FontStyle.Italic, color = c.sub, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                Text("Bugün yazdıkların, gelecek yıl burada karşına çıkacak.", color = c.faint, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Son girdiler
        val recent = Store.visible().filter { it.day != today.toEpochDay() && !it.isEmpty }.sortedByDescending { it.day * 10000 + (it.time.replace(":", "").toIntOrNull() ?: 0) }.take(3)
        if (recent.isNotEmpty()) {
            SectionTitle("Son yazdıkların", "Tümü") { Nav.tab = 1 }
            recent.forEach { e -> EntryCard(e) { Nav.go(Route.Reader(e.id)) }; Spacer(Modifier.height(10.dp)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}
