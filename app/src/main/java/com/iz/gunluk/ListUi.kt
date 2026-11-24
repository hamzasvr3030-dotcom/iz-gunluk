package com.iz.gunluk

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen() {
    val c = Iz.c
    @Suppress("UNUSED_VARIABLE") val v = Store.version
    var favOnly by remember { mutableStateOf(false) }
    var mediaOnly by remember { mutableStateOf(false) }
    var newest by remember { mutableStateOf(true) }
    var range by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var rangePick by remember { mutableStateOf(false) }
    var auth by remember { mutableStateOf(false) }
    val q = Nav.listQuery

    val all = Store.visible().filter { !it.isEmpty }
    val tags = remember(Store.version) { all.flatMap { it.allTags }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.map { it.first } }
    val people = remember(Store.version) { all.flatMap { it.people }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.map { it.first } }
    val list = all.filter { e ->
        (q.isBlank() || Text.fold(e.title + "\n" + e.plain + "\n" + e.place + "\n" + e.allTags.joinToString(" ")).contains(Text.fold(q))) &&
            (Nav.listMood == null || Nav.listMood in e.moods) &&
            (Nav.listPerson == null || e.people.any { Text.fold(it) == Text.fold(Nav.listPerson!!) }) &&
            (Nav.listTag == null || Nav.listTag in e.allTags) &&
            (!favOnly || e.fav) && (!mediaOnly || e.media.isNotEmpty()) &&
            (range == null || e.day in range!!.first..range!!.second)
    }.sortedBy { it.day * 10000 + (it.time.replace(":", "").toIntOrNull() ?: 0) }.let { if (newest) it.reversed() else it }
    val grouped = list.groupBy { YearMonth.from(it.date) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Günlük", style = MaterialTheme.typography.headlineMedium, color = c.text)
                        Text("${all.size} kayıt · ${all.map { it.day }.toSet().size} gün", color = c.sub, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (Store.entries.any { it.hidden }) IconButton(onClick = { if (Lock.hiddenOpen) Lock.hiddenOpen = false else auth = true }) {
                        Icon(if (Lock.hiddenOpen) Icons.Rounded.LockOpen else Icons.Rounded.Lock, "Gizli kayıtlar", tint = if (Lock.hiddenOpen) c.accent else c.sub)
                    }
                    IconButton(onClick = { newest = !newest }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sırala", tint = c.sub) }
                }
                // arama
                Row(Modifier.fillMaxWidth().clip(CircleShape).background(c.card).border(1.dp, c.line, CircleShape).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, tint = c.sub, modifier = Modifier.size(20.dp))
                    BasicTextField(q, { Nav.listQuery = it }, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 16.sp), cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 14.dp),
                        decorationBox = { inner -> if (q.isEmpty()) Text("Kelime, yer veya #etiket ara", color = c.faint); inner() })
                    if (q.isNotEmpty()) Icon(Icons.Rounded.Close, "Temizle", tint = c.sub, modifier = Modifier.clip(CircleShape).clickable { Nav.listQuery = "" })
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Pill("Favoriler", favOnly, Icons.Rounded.Favorite) { favOnly = !favOnly } }
                    item { Pill("Medyalı", mediaOnly, Icons.Rounded.Image) { mediaOnly = !mediaOnly } }
                    item {
                        Pill(range?.let { "${Tr.short(LocalDate.ofEpochDay(it.first))} – ${Tr.short(LocalDate.ofEpochDay(it.second))}" } ?: "Tarih aralığı", range != null, Icons.Rounded.DateRange) {
                            if (range != null) range = null else rangePick = true
                        }
                    }
                    items(Moods.all) { m -> Pill(m.label, Nav.listMood == m.key, m.icon, m.color) { Nav.listMood = if (Nav.listMood == m.key) null else m.key } }
                }
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tags) { t -> Pill("#$t", Nav.listTag == t) { Nav.listTag = if (Nav.listTag == t) null else t } }
                    }
                }
                if (people.isNotEmpty() || Nav.listPerson != null) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((listOfNotNull(Nav.listPerson) + people).distinct()) { pp -> Pill("@$pp", Nav.listPerson == pp, Icons.Rounded.Person) { Nav.listPerson = if (Nav.listPerson == pp) null else pp } }
                    }
                }
                if (all.isEmpty()) EmptyState(Icons.Rounded.EditNote, "Henüz bir şey yazmadın", "İlk satırın, yıllar sonra okuyacağın bir anının başlangıcı olacak.", "İlk günlüğünü yaz") { Nav.write() }
                else if (list.isEmpty()) EmptyState(Icons.Rounded.SearchOff, "Eşleşen kayıt yok", "Filtreleri değiştirmeyi dene.")
                else if (q.isNotBlank() || Nav.listMood != null || Nav.listTag != null || Nav.listPerson != null || favOnly || mediaOnly || range != null)
                    Text("${list.size} sonuç", color = c.sub, modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }
            grouped.forEach { (ym, es) ->
                item(key = "h$ym") {
                    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(Tr.monthYear(ym.atDay(1)), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                        Text("${es.size} kayıt", color = c.faint, style = MaterialTheme.typography.labelMedium)
                    }
                }
                items(es, key = { it.id }) { e -> EntryCard(e, query = q) { Nav.go(Route.Reader(e.id)) }; Spacer(Modifier.height(10.dp)) }
            }
        }
        FloatingActionButton(onClick = { Nav.write() }, containerColor = c.accent, contentColor = c.onAccent, shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) { Icon(Icons.Rounded.Add, "Yeni girdi") }
    }
    if (rangePick) {
        val st = rememberDateRangePickerState()
        DatePickerDialog(onDismissRequest = { rangePick = false }, confirmButton = {
            TextButton(onClick = {
                val a = st.selectedStartDateMillis; val b = st.selectedEndDateMillis ?: a
                if (a != null && b != null) range = Instant.ofEpochMilli(a).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() to Instant.ofEpochMilli(b).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                rangePick = false
            }) { Text("Uygula") }
        }, dismissButton = { TextButton(onClick = { rangePick = false }) { Text("Vazgeç") } }) {
            DateRangePicker(st, Modifier.weight(1f), title = { Text("Tarih aralığı seç", Modifier.padding(16.dp)) })
        }
    }
    if (auth) AuthScreen("Gizli kayıtlar", "Gizli kayıtları göstermek için doğrula", mode = AuthMode.Hidden, onCancel = { auth = false }) { Lock.hiddenOpen = true; auth = false }
}

// ============================ Takvim ============================
@Composable
fun CalendarScreen() {
    val c = Iz.c
    @Suppress("UNUSED_VARIABLE") val v = Store.version
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var yearView by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    val vis = Store.visible().filter { !it.isEmpty }
    val moods = remember(Store.version, Lock.hiddenOpen) { Insights.dayMoods(vis) }
    val written = remember(Store.version, Lock.hiddenOpen) { vis.map { it.day }.toSet() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Takvim", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.weight(1f))
            Pill("Ay", !yearView) { yearView = false }
            Spacer(Modifier.width(6.dp))
            Pill("Yıl", yearView) { yearView = true }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ym = if (yearView) ym.minusYears(1) else ym.minusMonths(1) }) { Icon(Icons.Rounded.ChevronLeft, "Önceki", tint = c.text) }
            Text(if (yearView) "${ym.year}" else Tr.monthYear(ym.atDay(1)), style = MaterialTheme.typography.titleLarge, color = c.text,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clickable { ym = YearMonth.now(); selected = LocalDate.now() })
            IconButton(onClick = { ym = if (yearView) ym.plusYears(1) else ym.plusMonths(1) }) { Icon(Icons.Rounded.ChevronRight, "Sonraki", tint = c.text) }
        }
        AnimatedContent(yearView to ym, transitionSpec = {
            val dir = if (targetState.second > initialState.second) 1 else -1
            (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * dir } + fadeOut())
        }, label = "cal") { (yv, m) ->
            if (!yv) MonthGrid(m, moods, written, selected) { d -> selected = d }
            else Column {
                for (row in (1..12).chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (mo in row) {
                            val mm = YearMonth.of(m.year, mo)
                            Column(Modifier.weight(1f).clip(RoundS).background(c.card).clickable { ym = mm; yearView = false }.padding(8.dp)) {
                                Text(Tr.months[mo - 1], style = MaterialTheme.typography.labelLarge, color = c.text)
                                Spacer(Modifier.height(6.dp))
                                MiniMonth(mm, moods, written)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                val yd = written.count { LocalDate.ofEpochDay(it).year == m.year }
                Text("${m.year} yılında $yd gün yazdın.", color = c.sub, modifier = Modifier.padding(vertical = 8.dp))
                if (yd > 0) OutlinedButton(onClick = {
                    Nav.go(Route.Summary(LocalDate.of(m.year, 1, 1).toEpochDay(), LocalDate.of(m.year, 12, 31).toEpochDay(), "${m.year} yılı özeti"))
                }, shape = CircleShape) { Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Yıllık özeti aç") }
            }
        }
        if (!yearView) {
            Spacer(Modifier.height(10.dp))
            // renk açıklaması
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Moods.all.forEach { m -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(m.color)); Spacer(Modifier.width(4.dp))
                    Text(m.label, color = c.faint, style = MaterialTheme.typography.labelMedium)
                } }
            }
            Spacer(Modifier.height(16.dp))
            // seçili gün
            val dayList = vis.filter { it.day == selected.toEpochDay() }.sortedBy { it.time }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Tr.longDay(selected), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                if (!selected.isAfter(LocalDate.now())) TextButton(onClick = { Nav.go(Route.Editor(day = selected.toEpochDay())) }) { Icon(Icons.Rounded.Add, null); Text("Yaz") }
            }
            Spacer(Modifier.height(6.dp))
            if (dayList.isEmpty()) Text(if (selected.isAfter(LocalDate.now())) "Bu gün henüz gelmedi." else "Bu güne ait kayıt yok.", color = c.sub)
            dayList.forEach { e -> EntryCard(e, showDate = false) { Nav.go(Route.Reader(e.id)) }; Spacer(Modifier.height(10.dp)) }
            val past = vis.filter { it.date.monthValue == selected.monthValue && it.date.dayOfMonth == selected.dayOfMonth && it.date.year != selected.year }
            if (past.isNotEmpty()) TextButton(onClick = { Nav.go(Route.Day(selected.toEpochDay())) }) { Text("Diğer yıllarda bu gün: ${past.size} kayıt") }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun MonthGrid(ym: YearMonth, moods: Map<Long, Mood>, written: Set<Long>, selected: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = Iz.c
    val first = ym.atDay(1)
    val lead = first.dayOfWeek.value - 1
    val today = LocalDate.now()
    Column {
        Row { Tr.daysShort.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = c.faint, style = MaterialTheme.typography.labelMedium) } }
        Spacer(Modifier.height(6.dp))
        val cells = lead + ym.lengthOfMonth()
        for (w in 0 until (cells + 6) / 7) {
            Row {
                for (i in 0..6) {
                    val n = w * 7 + i - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..ym.lengthOfMonth()) {
                            val d = ym.atDay(n); val k = d.toEpochDay()
                            val m = moods[k]
                            val sel = d == selected
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                                    .background(when { m != null -> m.color.copy(alpha = 0.85f); k in written -> c.accent.copy(alpha = 0.35f); else -> c.card })
                                    .border(if (sel) 2.dp else if (d == today) 1.dp else 0.dp, if (sel) c.text else if (d == today) c.accent else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable { onPick(d) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$n", color = if (m != null) Color(0xFF0A0E17) else if (d.isAfter(today)) c.faint else c.text,
                                    fontWeight = if (d == today || m != null) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniMonth(ym: YearMonth, moods: Map<Long, Mood>, written: Set<Long>) {
    val c = Iz.c
    val lead = ym.atDay(1).dayOfWeek.value - 1
    val cells = lead + ym.lengthOfMonth()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (w in 0 until (cells + 6) / 7) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0..6) {
                val n = w * 7 + i - lead + 1
                val k = if (n in 1..ym.lengthOfMonth()) ym.atDay(n).toEpochDay() else null
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(2.dp)).background(when {
                    k == null -> Color.Transparent
                    moods[k] != null -> moods[k]!!.color
                    k in written -> c.accent.copy(alpha = 0.5f)
                    else -> c.line
                }))
            }
        }
    }
}
