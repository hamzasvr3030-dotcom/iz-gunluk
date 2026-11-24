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
import kotlinx.coroutines.launch
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
        // Kapak
        val cover = Covers.colors(Prefs.cover)
        Box(Modifier.fillMaxWidth().padding(top = 14.dp).clip(Round).background(Brush.linearGradient(cover))) {
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)))))
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Tr.upper(Prefs.coverTitle.ifBlank { "Günlüğüm" }), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { Nav.go(Route.Settings) }, modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))) {
                        Icon(Icons.Rounded.Settings, "Ayarlar", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(Tr.longDay(today), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (Prefs.name.isNotBlank()) "${Tr.greeting(LocalTime.now().hour)}, ${Prefs.name}" else Tr.greeting(LocalTime.now().hour),
                    color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyLarge,
                )
                Text("Bugün nasıl geçti?", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        if (Store.loadError) {
            Spacer(Modifier.height(12.dp))
            IzCard(color = c.danger.copy(alpha = 0.15f)) {
                Text("Bazı kayıtlar okunamadı", color = c.danger, fontWeight = FontWeight.Bold)
                Text("Verilerin zarar görmesin diye kaydetme durduruldu. Ayarlar > Yedekten geri yükle ile son yedeğini yükleyebilirsin.", color = c.text, style = MaterialTheme.typography.bodyMedium)
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

        if (Prefs.showHabits && Habits.defs.isNotEmpty()) { Spacer(Modifier.height(12.dp)); HabitsCard() }

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

        WeeklyCard(today)

        if (Prefs.showSaying) { Spacer(Modifier.height(12.dp)); SayingCard(Sayings.forDay(today.toEpochDay())) }
        if (Prefs.showHoroscope) { Spacer(Modifier.height(12.dp)); HoroscopeCard() }

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

@Composable
fun HabitsCard() {
    val c = Iz.c
    val day = LocalDate.now().toEpochDay()
    IzCard(pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Bugünkü alışkanlıklar", modifier = Modifier.weight(1f))
            val n = Habits.defs.count { Habits.done(it, Habits.value(day, it.key)) }
            Text("$n/${Habits.defs.size}", color = c.sub, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        Habits.defs.forEach { d ->
            val v = Habits.value(day, d.key)
            val ok = Habits.done(d, v)
            Row(Modifier.fillMaxWidth().clip(RoundS).clickable(enabled = d.kind == "check") { Habits.toggle(day, d) }.padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(if (ok) c.accent else c.card2), contentAlignment = Alignment.Center) {
                    Icon(if (ok && d.kind == "check") Icons.Rounded.Check else Habits.icon(d.icon), null, tint = if (ok) c.onAccent else c.sub, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, color = c.text)
                    if (d.kind != "check") Text("${fmtD(v)} / ${fmtD(d.target)} ${d.unit}", color = if (ok) c.accent else c.faint, style = MaterialTheme.typography.labelMedium)
                    else { val st = Habits.streak(d); if (st > 1) Text("$st gündür", color = c.faint, style = MaterialTheme.typography.labelMedium) }
                }
                if (d.kind != "check") {
                    val step = if (d.kind == "hours") 0.5 else 1.0
                    Box(Modifier.size(32.dp).clip(CircleShape).background(c.card2).clickable(enabled = v > 0) { Habits.set(day, d.key, (v - step).coerceAtLeast(0.0)) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Remove, "Azalt", tint = c.sub, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(32.dp).clip(CircleShape).background(c.accentSoft).clickable { Habits.set(day, d.key, v + step) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, "Artır", tint = c.accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SayingCard(s: Saying) {
    val c = Iz.c
    val ctx = androidx.compose.ui.platform.LocalContext.current
    IzCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FormatQuote, null, tint = c.accent, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
            Label("Günün sözü", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(s.text, style = MaterialTheme.typography.titleMedium, color = c.text, fontStyle = FontStyle.Italic)
        Spacer(Modifier.height(6.dp))
        Text("— ${s.by}", color = c.sub, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Bunu yaz", icon = Icons.Rounded.Edit) { Nav.write(question = "“${s.text}” — ${s.by}. Bu söz sana ne düşündürüyor?") }
            Pill("Paylaş", icon = Icons.Rounded.Share) { ShareCard.forSaying(ctx, s) }
        }
    }
}

@Composable
fun HoroscopeCard() {
    val c = Iz.c
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val sign = Signs[Prefs.sign]
    var r by remember(sign.key) { mutableStateOf(Horoscope.cached(sign.key)) }
    var loading by remember(sign.key) { mutableStateOf(false) }
    var failed by remember(sign.key) { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    fun load(force: Boolean) {
        loading = true; failed = false
        scope.launch { val x = Horoscope.get(sign.key, force); if (x != null) r = x else failed = r == null; loading = false }
    }
    LaunchedEffect(sign.key) { if (r == null) load(false) }
    IzCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFB37FEB), modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Label("${sign.name} burcu · bugün", Color(0xFFB37FEB))
                Text(sign.dates, color = c.faint, style = MaterialTheme.typography.labelMedium)
            }
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else IconButton(onClick = { load(true) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Refresh, "Yenile", tint = c.sub, modifier = Modifier.size(18.dp)) }
        }
        Spacer(Modifier.height(8.dp))
        val x = r
        when {
            x != null -> {
                val parts = if (open) x.parts else x.parts.take(1)
                parts.forEachIndexed { i, p ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    if (p.title.isNotBlank()) Text(p.title, color = c.text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(p.text, color = c.sub, maxLines = if (open) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kaynak: ${x.source}", color = c.faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).clip(CircleShape).clickable {
                        Lock.suppress = true
                        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(x.url))) }.onFailure { Lock.suppress = false }
                    }.padding(vertical = 4.dp))
                    if (x.parts.size > 1 || x.parts.firstOrNull()?.text.orEmpty().length > 220)
                        Text(if (open) "Daha az" else "Devamı", color = c.accent, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).clickable { open = !open }.padding(6.dp))
                }
            }
            loading -> Text("Yorum getiriliyor…", color = c.sub)
            failed -> Text("Yorum alınamadı. İnternet bağlantını kontrol edip yenile.", color = c.sub)
        }
    }
}

/** Pazar–salı arası: haftanın kısa özeti. */
@Composable
fun WeeklyCard(today: LocalDate) {
    val c = Iz.c
    val dow = today.dayOfWeek.value
    if (dow in 3..6) return
    val mon = if (dow == 7) today.minusDays(6) else today.minusDays((dow - 1).toLong() + 7)
    val sun = mon.plusDays(6)
    val list = Store.visible().filter { !it.isEmpty && it.day in mon.toEpochDay()..sun.toEpochDay() }
    if (list.isEmpty()) return
    val s = remember(Store.version, mon) { Insights.summary(list) }
    val title = if (dow == 7) "Bu haftaya geriye bak" else "Geçen haftana geriye bak"
    Spacer(Modifier.height(12.dp))
    IzCard(onClick = { Nav.go(Route.Summary(mon.toEpochDay(), sun.toEpochDay(), title.removeSuffix(" geriye bak").replace("haftana", "hafta").replace("haftaya", "hafta"))) },
        color = c.accentSoft) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.EventNote, null, tint = c.accent, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
            Label(title, modifier = Modifier.weight(1f))
            Text("${mon.dayOfMonth}–${sun.dayOfMonth} ${Tr.months[sun.monthValue - 1]}", color = c.sub, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("${s.daysWritten}/7", "gün yazdın", Modifier.weight(1f))
            Stat(fmtNum(s.words), "kelime", Modifier.weight(1f))
            s.topMood?.let { (m, _) -> Stat(m.label, "en sık his", Modifier.weight(1f), color = m.color) }
        }
        s.highlights.firstOrNull()?.let {
            Spacer(Modifier.height(10.dp))
            Text("“$it”", color = c.text, fontStyle = FontStyle.Italic, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        val habitLine = Habits.defs.map { d -> d to (0..6).count { Habits.done(d, Habits.value(mon.toEpochDay() + it, d.key)) } }.filter { it.second > 0 }
        if (habitLine.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(habitLine.joinToString(" · ") { "${it.first.name} ${it.second}/7" }, color = c.sub, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
