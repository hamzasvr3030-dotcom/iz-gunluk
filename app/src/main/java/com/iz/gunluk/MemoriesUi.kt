package com.iz.gunluk

import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun MemoriesScreen() {
    val c = Iz.c
    @Suppress("UNUSED_VARIABLE") val v = Store.version
    val tabs = listOf("Favoriler", "Fotoğraflar", "Mektuplar", "Harita", "Geçmişte bugün", "Kişiler")
    val vis = Store.visible()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Anılar", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(tabs) { i, t -> Pill(t, Nav.memTab == i) { if (i == 3) Nav.go(Route.Map) else Nav.memTab = i } }
        }
        Spacer(Modifier.height(12.dp))
        when (Nav.memTab) {
            0 -> {
                val favs = vis.filter { it.fav }.sortedByDescending { it.day }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    if (favs.isEmpty()) EmptyState(Icons.Rounded.FavoriteBorder, "Henüz favori anın yok", "Özel günlerini kalp simgesiyle işaretle; hepsi burada toplanır.")
                    favs.forEach { e -> EntryCard(e) { Nav.go(Route.Reader(e.id)) }; Spacer(Modifier.height(10.dp)) }
                    Spacer(Modifier.height(30.dp))
                }
            }
            1 -> {
                val ph = vis.sortedByDescending { it.day }.flatMap { e -> e.media.filter { !it.isAudio }.map { e to it } }
                if (ph.isEmpty()) EmptyState(Icons.Rounded.PhotoLibrary, "Fotoğraf yok", "Günlüklerine eklediğin fotoğraf ve videolar burada bir galeri olur.")
                else LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    var lastMonth = ""
                    for ((e, m) in ph) {
                        val mo = Tr.monthYear(e.date)
                        if (mo != lastMonth) { lastMonth = mo; item(span = { GridItemSpan(3) }) { Text(mo, color = c.sub, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) } }
                        item { Thumb(m, Modifier.aspectRatio(1f).clickable { Nav.go(Route.Reader(e.id)) }) }
                    }
                }
            }
            2 -> LettersList()
            5 -> {
                val people = vis.flatMap { e -> e.people.map { Text.fold(it) to (it to e) } }.groupBy({ it.first }, { it.second })
                    .map { (_, l) -> Triple(l.first().first, l.map { it.second }.distinctBy { it.id }, l.size) }.sortedByDescending { it.second.size }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    if (people.isEmpty()) EmptyState(Icons.Rounded.People, "Henüz kimseyi anmadın", "Yazarken @Ayşe gibi bir isim yaz; o kişiyle ilgili tüm anılar burada toplanır.")
                    people.forEach { (name, es, _) ->
                        val last = es.maxBy { it.day }
                        val top = Insights.moodCounts(es).firstOrNull()?.first
                        IzCard(onClick = { Nav.listPerson = name; Nav.tab = 1 }, pad = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFF36CFC9).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                    Text(name.take(1).uppercase(java.util.Locale.forLanguageTag("tr")), color = Color(0xFF36CFC9), fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("@$name", color = c.text, fontWeight = FontWeight.Bold)
                                    Text("${es.size} anı · son: ${Tr.ago(last.date)}", color = c.sub, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (top != null) { MoodBadge(top, 26.dp) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
            4 -> {
                val today = LocalDate.now()
                val list = Insights.onThisDay(vis, today)
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    Text("${today.dayOfMonth} ${Tr.months[today.monthValue - 1]} tarihinde geçmiş yıllarda yazdıkların", color = c.sub)
                    Spacer(Modifier.height(12.dp))
                    if (list.isEmpty()) EmptyState(Icons.Rounded.History, "Henüz bir geçmiş yok", "Bugün yazdığın her şey, gelecek yıl bugün burada karşına çıkacak.")
                    list.groupBy { it.date.year }.forEach { (y, es) ->
                        Label("${today.year - y} yıl önce · $y")
                        Spacer(Modifier.height(8.dp))
                        es.forEach { e -> EntryCard(e) { Nav.go(Route.Reader(e.id)) }; Spacer(Modifier.height(10.dp)) }
                        Spacer(Modifier.height(10.dp))
                    }
                    // bu hafta geçmiş yıllarda
                    val week = vis.filter { it.date.year < today.year && Math.abs(it.date.withYear(today.year).toEpochDay() - today.toEpochDay()) in 1..3 }.sortedByDescending { it.day }
                    if (week.isNotEmpty()) {
                        SectionTitle("Bu günlerin yakınında")
                        week.take(6).forEach { e -> EntryCard(e) { Nav.go(Route.Reader(e.id)) }; Spacer(Modifier.height(10.dp)) }
                    }
                    Spacer(Modifier.height(30.dp))
                }
            }
        }
    }
}

// ============================ Geleceğe mektup ============================
@Composable
fun LettersList() {
    val c = Iz.c
    val today = LocalDate.now().toEpochDay()
    val letters = Store.letters.sortedBy { it.openDay }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        IzCard(onClick = { Nav.go(Route.LetterEdit()) }, color = c.accentSoft) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ForwardToInbox, null, tint = c.accent); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Geleceğe mektup yaz", style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text("Kendine bir mektup yaz, açılacağı günü seç. O güne kadar mühürlü kalır.", color = c.sub, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        letters.forEach { l ->
            val open = l.isOpen(today)
            val written = Instant.ofEpochMilli(l.written).atZone(ZoneId.systemDefault()).toLocalDate()
            IzCard(onClick = { if (open) Nav.go(Route.LetterRead(l.id)) else Nav.toast = "${l.openDay - today} gün sonra açılacak" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (open) c.accentSoft else c.card2), contentAlignment = Alignment.Center) {
                        Icon(if (open) Icons.Rounded.Drafts else Icons.Rounded.Lock, null, tint = if (open) c.accent else c.sub)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.title.ifBlank { "Mektup" }, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (open) "${Tr.long(written)} tarihinde yazıldı" + if (!l.read) " · yeni" else ""
                            else "${Tr.long(LocalDate.ofEpochDay(l.openDay))} tarihinde açılacak · ${l.openDay - today} gün",
                            color = if (open && !l.read) c.accent else c.sub, style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (!open) {
                    Spacer(Modifier.height(10.dp))
                    val total = (l.openDay - written.toEpochDay()).coerceAtLeast(1)
                    LinearProgressIndicator(progress = { ((today - written.toEpochDay()).toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(30.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterEditScreen(id: String?) {
    val c = Iz.c
    val old = id?.let { i -> Store.letters.firstOrNull { it.id == i } }
    var title by remember { mutableStateOf(old?.title ?: "") }
    var body by remember { mutableStateOf(old?.body ?: "") }
    var openDay by remember { mutableLongStateOf(old?.openDay ?: LocalDate.now().plusYears(1).toEpochDay()) }
    var pick by remember { mutableStateOf(false) }
    val presets = listOf("1 ay" to 1L, "6 ay" to 6L, "1 yıl" to 12L, "5 yıl" to 60L)
    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        BackBar("Geleceğe mektup", { Nav.back() }) {
            TextButton(enabled = body.isNotBlank(), onClick = {
                Store.upsertLetter((old ?: Letter(openDay = openDay, title = "", body = "")).copy(title = title, body = body, openDay = openDay))
                Nav.toast = "Mektup mühürlendi · ${Tr.long(LocalDate.ofEpochDay(openDay))}"
                Nav.back()
            }) { Text("Mühürle", fontWeight = FontWeight.Bold) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Text("Ne zaman açılsın?", color = c.sub, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (t, m) -> val d = LocalDate.now().plusMonths(m).toEpochDay(); Pill(t, openDay == d) { openDay = d } }
                Pill("Tarih seç", false, Icons.Rounded.Event) { pick = true }
            }
            Spacer(Modifier.height(8.dp))
            Text("Açılış: ${Tr.longDay(LocalDate.ofEpochDay(openDay))}", color = c.accent, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(18.dp))
            BasicTextField(title, { title = it }, singleLine = true, cursorBrush = SolidColor(c.accent),
                textStyle = TextStyle(color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                decorationBox = { inner -> if (title.isEmpty()) Text("Başlık: Sevgili gelecekteki ben…", color = c.faint, fontSize = 22.sp, fontWeight = FontWeight.Bold); inner() })
            Spacer(Modifier.height(14.dp))
            BasicTextField(body, { body = it }, cursorBrush = SolidColor(c.accent), textStyle = writingStyle(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                decorationBox = { inner -> if (body.isEmpty()) Text("Şu an neler yaşıyorsun? Neyi umuyorsun? Kendine ne hatırlatmak istersin?", style = writingStyle().copy(color = c.faint)); inner() })
        }
    }
    if (pick) {
        val st = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(openDay).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { pick = false }, confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { ms ->
                    val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    if (d > LocalDate.now().toEpochDay()) openDay = d else Nav.toast = "Gelecekte bir gün seç"
                }
                pick = false
            }) { Text("Tamam") }
        }) { DatePicker(st) }
    }
}

@Composable
fun LetterReadScreen(id: String) {
    val c = Iz.c
    val l = Store.letters.firstOrNull { it.id == id }
    if (l == null) { LaunchedEffect(Unit) { Nav.back() }; return }
    LaunchedEffect(l.id) { if (!l.read) Store.upsertLetter(l.copy(read = true)) }
    var del by remember { mutableStateOf(false) }
    val written = Instant.ofEpochMilli(l.written).atZone(ZoneId.systemDefault()).toLocalDate()
    Column(Modifier.fillMaxSize().background(c.bg)) {
        BackBar("", { Nav.back() }) { IconButton(onClick = { del = true }) { Icon(Icons.Rounded.DeleteOutline, "Sil", tint = c.sub) } }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Label("Geçmişten gelen mektup")
            Spacer(Modifier.height(8.dp))
            Text("${Tr.long(written)} tarihinde, ${Tr.ago(written).lowercase()} yazdın.", color = c.sub)
            Spacer(Modifier.height(16.dp))
            Text(l.title.ifBlank { "Sevgili ben," }, style = MaterialTheme.typography.headlineMedium, color = c.text)
            Spacer(Modifier.height(16.dp))
            Text(l.body, style = writingStyle())
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { Nav.replace(Route.Editor(question = "Geçmişteki kendine cevap: “${l.title.ifBlank { "Mektup" }}”")) }, shape = CircleShape) {
                Icon(Icons.Rounded.Reply, null); Spacer(Modifier.width(6.dp)); Text("Günlüğüne cevap yaz")
            }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (del) AlertDialog(onDismissRequest = { del = false }, title = { Text("Mektup silinsin mi?") },
        confirmButton = { TextButton(onClick = { del = false; Nav.back(); Store.deleteLetter(l.id) }) { Text("Sil", color = c.danger) } },
        dismissButton = { TextButton(onClick = { del = false }) { Text("Vazgeç") } })
}

// ============================ Anı haritası ============================
@Composable
fun MapScreen() {
    val c = Iz.c
    val pts = Store.visible().filter { it.lat != null && it.lon != null }
    var sel by remember { mutableStateOf<Entry?>(null) }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        if (pts.isEmpty()) Column {
            BackBar("Anı haritası", { Nav.back() })
            EmptyState(Icons.Rounded.Map, "Haritada henüz anı yok", "Yazarken konum düğmesine dokun; o anı haritada iğne olarak görünür.")
        } else {
            AndroidView(factory = { cx ->
                Configuration.getInstance().userAgentValue = cx.packageName
                Configuration.getInstance().osmdroidBasePath = cx.cacheDir.resolve("osm")
                Configuration.getInstance().osmdroidTileCache = cx.cacheDir.resolve("osm/tiles")
                MapView(cx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    for (e in pts) {
                        val mk = Marker(this)
                        mk.position = GeoPoint(e.lat!!, e.lon!!)
                        mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        mk.icon = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL; setSize(44, 44)
                            setColor((Moods[e.mood]?.color ?: c.accent).toArgb()); setStroke(5, android.graphics.Color.WHITE)
                        }
                        mk.setOnMarkerClickListener { _, _ -> sel = e; true }
                        overlays.add(mk)
                    }
                    addOnFirstLayoutListener { _, _, _, _, _ ->
                        if (pts.size == 1) { controller.setZoom(14.0); controller.setCenter(GeoPoint(pts[0].lat!!, pts[0].lon!!)) }
                        else {
                            val box = BoundingBox.fromGeoPoints(pts.map { GeoPoint(it.lat!!, it.lon!!) })
                            runCatching { zoomToBoundingBox(box.increaseByScale(1.4f), false) }
                        }
                    }
                }
            }, modifier = Modifier.fillMaxSize(), onRelease = { it.onDetach() })
            Box(Modifier.statusBarsPadding().padding(12.dp).clip(CircleShape).background(c.card)) {
                BackBarIcon { Nav.back() }
            }
            Text("${pts.size} anı · © OpenStreetMap", color = c.sub, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(20.dp).clip(CircleShape).background(c.card).padding(horizontal = 12.dp, vertical = 6.dp))
            sel?.let { e ->
                Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
                    EntryCard(e) { Nav.go(Route.Reader(e.id)) }
                }
            }
        }
    }
}

@Composable
private fun BackBarIcon(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri", tint = Iz.c.text) }
}
