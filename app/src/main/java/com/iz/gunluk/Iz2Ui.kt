package com.iz.gunluk

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

// ============================ Bugün: başlık ============================
/** "CUMARTESİ · 12:59 / 28 Kasım / ⛅ hava · 🔥 seri" — yazma ekranının dergi başlığı. */
@Composable
fun TodayHeader() {
    val c = Iz.c
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(20_000); now = LocalTime.now() } }
    val today = LocalDate.now()
    val todays = Store.today()
    val weather = todays.map { it.weather }.firstOrNull { it.isNotBlank() } ?: Store.entries.filter { it.day == today.toEpochDay() - 1 }.map { it.weather }.firstOrNull { it.isNotBlank() }.orEmpty()
    val streak = Insights.streak(Store.writtenDays)
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(Tr.upper("${Tr.weekday(today)} · %02d:%02d".format(now.hour, now.minute)), style = MaterialTheme.typography.labelSmall, color = c.accent)
            Text("${today.dayOfMonth} ${Tr.months[today.monthValue - 1]}", fontFamily = Crimson, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp, color = c.text)
            val bits = listOfNotNull(weather.takeIf { it.isNotBlank() }?.let { "⛅ $it" }, if (streak > 0) "🔥 $streak günlük yazma serisi" else "🔥 Seriyi bugün başlat")
            Text(bits.joinToString(" · "), color = c.sub, style = MaterialTheme.typography.bodyMedium)
        }
        if (Night.on) Text("🌙 Gece modu", color = c.accent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clip(CircleShape).background(c.accentSoft).padding(horizontal = 10.dp, vertical = 5.dp))
        IconButton(onClick = { Nav.go(Route.Settings) }) { Icon(Icons.Rounded.Settings, "Ayarlar", tint = c.sub) }
    }
}

/** Bugünün en son girdisi: serif önizleme, @kişi/#etiket vurgulu, ek çipleri. */
@Composable
fun TodayPage() {
    val c = Iz.c
    val ctx = LocalContext.current
    val todays = Store.today().filter { !it.isEmpty }
    val e = todays.maxByOrNull { it.updated }
    var energy by remember(e?.id) { mutableFloatStateOf((e?.energy ?: 0).toFloat()) }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Enerji", color = c.sub, style = MaterialTheme.typography.labelLarge)
        Slider(energy, { energy = it }, onValueChangeFinished = {
            val v = energy.toInt()
            val t = Store.today().maxByOrNull { it.updated }
            if (t != null) Store.upsert(t.copy(energy = v, updated = System.currentTimeMillis())) else Store.upsert(Store.newEntry().copy(energy = v))
        }, valueRange = 0f..10f, steps = 9, modifier = Modifier.weight(1f).padding(horizontal = 12.dp).semantics { contentDescription = "Enerji" })
        Text(if (energy.toInt() == 0) "–" else "${energy.toInt()}/10", color = c.text, fontWeight = FontWeight.Bold)
    }
    if (e == null) return
    Spacer(Modifier.height(6.dp))
    Column(Modifier.fillMaxWidth().clip(Round).background(c.card).clickable { Nav.go(Route.Editor(id = e.id)) }.padding(18.dp)) {
        Kinds.of(e)?.let { k -> Text("${k.emoji} ${k.name} günlüğü", color = k.color, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(4.dp)) }
        if (e.title.isNotBlank()) Text(e.title, fontFamily = Crimson, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = c.text)
        Text(RichText.render(e.body.take(600), c.accent, c.sub, c.highlight), style = writingStyle(), maxLines = 8, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (e.photos.isNotEmpty()) SmallChip("📷 ${e.photos.size}")
            e.media.filter { it.isAudio }.takeIf { it.isNotEmpty() }?.let { SmallChip("🎙 ${MediaKit.fmt(it.sumOf { m -> m.dur })}") }
            if (e.place.isNotBlank()) SmallChip("📍 ${e.place.substringBefore(',')}")
            if (e.song.isNotBlank()) SmallChip("🎵 ${e.song}")
            if (e.rota.isNotBlank()) SmallChip("✓ Rota")
        }
        Spacer(Modifier.height(6.dp))
        Text("${e.words} kelime · kaydedildi", color = c.faint, style = MaterialTheme.typography.labelMedium)
    }
    DayPhotosCard(e)
    LaunchedEffect(Unit) { if (Prefs.seyirLink) Eco.syncSeyir(ctx) }
}

@Composable
fun SmallChip(t: String, color: Color = Iz.c.sub) =
    Text(t, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 220.dp).clip(RoundChip).background(Iz.c.card2).padding(horizontal = 10.dp, vertical = 6.dp))

/** Akşam: o gün çekilen fotoğraflardan seçip girdiye ekleme önerisi. */
@Composable
fun DayPhotosCard(target: Entry?) {
    val c = Iz.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (!Prefs.suggestPhotos || LocalTime.now().hour < 17) return
    var granted by remember { mutableStateOf(DayPhotos.granted(ctx)) }
    var list by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val pick = remember { mutableStateListOf<Uri>() }
    val dismissed = Prefs.long("photosDismissed") == LocalDate.now().toEpochDay()
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it || DayPhotos.granted(ctx) }
    LaunchedEffect(granted) { if (granted) list = DayPhotos.today(ctx).filter { u -> target?.media?.none { false } ?: true } }
    if (dismissed) return
    if (!granted) {
        Spacer(Modifier.height(12.dp))
        IzCard(onClick = { Lock.suppress = true; perm.launch(DayPhotos.perm) }) {
            Label("Günün fotoğrafları")
            Text("Akşamları o gün çektiğin fotoğrafları önerelim mi? Fotoğraflar yalnızca sen seçince girdiye kopyalanır.", color = c.sub, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    if (list.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    IzCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Label("Günün fotoğrafları"); Text("Bugün ${list.size} fotoğraf çektin. Hangileri anı olsun?", color = c.text) }
            IconButton(onClick = { Prefs.putLong("photosDismissed", LocalDate.now().toEpochDay()); list = emptyList() }) { Icon(Icons.Rounded.Close, "Kapat", tint = c.sub) }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { u ->
                val on = u in pick
                Box(Modifier.size(84.dp).clip(RoundS).border(3.dp, if (on) c.accent else Color.Transparent, RoundS).clickable { if (on) pick.remove(u) else pick.add(u) }) {
                    AsyncImage(u, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (on) Icon(Icons.Rounded.CheckCircle, null, tint = c.accent, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp))
                }
            }
        }
        if (pick.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                scope.launch {
                    val ms = pick.mapNotNull { MediaKit.import(ctx, it, "photo") }
                    val t = Store.today().maxByOrNull { it.updated } ?: Store.newEntry()
                    Store.upsert(t.copy(media = t.media + ms, updated = System.currentTimeMillis()))
                    Prefs.putLong("photosDismissed", LocalDate.now().toEpochDay()); list = emptyList(); pick.clear()
                    Nav.toast = "${ms.size} fotoğraf bugünün girdisine eklendi"
                }
            }, shape = CircleShape) { Text("${pick.size} fotoğrafı ekle") }
        }
    }
}

// ============================ Yazarken öneriler ============================
/** @kişi, #etiket ve [[bağlantı yazarken üst satırda öneriler. */
@Composable
fun SuggestBar(text: String, cursor: Int, onReplace: (start: Int, end: Int, with: String) -> Unit) {
    val c = Iz.c
    val tok = remember(text, cursor) { Typing.at(text, cursor) } ?: return
    val all = Store.visible()
    val sugg: List<Pair<String, String>> = remember(tok, Store.version) {
        val f = Text.fold(tok.text)
        when (tok.kind) {
            '@' -> People.known(all).filter { f.isEmpty() || Text.fold(it).startsWith(f) }.take(8).map { "@$it" to "@$it " }
            '#' -> all.flatMap { it.allTags }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
                .filter { f.isEmpty() || Text.fold(it).startsWith(f) }.take(8).map { "#$it" to "#$it " }
            else -> Links.suggest(tok.text, all).map { e -> e.title to "[[${e.title}]] " } +
                listOf(LocalDate.now().minusDays(1).toString()).filter { tok.text.isEmpty() }.map { "Dün" to "[[$it]] " }
        }
    }
    if (sugg.isEmpty()) return
    Row(Modifier.fillMaxWidth().background(c.card2).horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(when (tok.kind) { '@' -> Icons.Rounded.AlternateEmail; '#' -> Icons.Rounded.Tag; else -> Icons.Rounded.Link }, null, tint = c.accent, modifier = Modifier.size(16.dp))
        sugg.forEach { (label, ins) ->
            Text(label, color = c.text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.clip(RoundChip).background(c.card).clickable { onReplace(tok.start, cursor, ins) }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
    }
}

// ============================ Günlük türleri ============================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KindPicker(selected: String, onPick: (Kind?) -> Unit) {
    val c = Iz.c
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Serbest", selected.isBlank()) { onPick(null) }
        Kinds.all.forEach { k ->
            val on = selected == k.key
            Text("${k.emoji} ${k.name}", color = if (on) Color(0xFF1A0F09) else c.text, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(CircleShape).background(if (on) k.color else k.color.copy(alpha = 0.14f)).clickable { onPick(k) }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

// ============================ Duygu çarkı ============================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FineWheel(moods: List<String>, fine: List<String>, onChange: (List<String>) -> Unit) {
    val c = Iz.c
    val parents = moods.filter { Wheel.fine.containsKey(it) }.ifEmpty { return }
    Spacer(Modifier.height(14.dp))
    Text("İnce duygular", style = MaterialTheme.typography.titleMedium, color = c.text)
    Text("Duygu çarkı: ana duygunun altında ne vardı?", color = c.sub, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
    parents.forEach { p ->
        val m = Moods[p] ?: return@forEach
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            Box(Modifier.padding(end = 4.dp)) { MoodBadge(m, 30.dp) }
            Wheel.fine[p]!!.forEach { f ->
                val on = f in fine
                Text(f, color = if (on) Color(0xFF1A0F09) else c.text, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clip(CircleShape).background(if (on) m.color else m.color.copy(alpha = 0.13f)).clickable { onChange(if (on) fine - f else fine + f) }.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
    }
}

// ============================ El yazısı / çizim sayfası ============================
private data class Stroke2(val pts: List<Offset>, val color: Color, val w: Float)

/** Parmakla çizim; PNG olarak girdinin içine fotoğraf gibi eklenir. */
@Composable
fun DrawDialog(onDone: (Media?) -> Unit) {
    val c = Iz.c
    val strokes = remember { mutableStateListOf<Stroke2>() }
    var cur by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var color by remember { mutableStateOf(Color(0xFF2B1D14)) }
    var width by remember { mutableFloatStateOf(6f) }
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val paper = Color(0xFFF8EDE5)
    val inks = listOf(Color(0xFF2B1D14), IzAmber, IzRose, Color(0xFF3B82F6), Color(0xFF16A34A), Color(0xFFDC2626))
    Dialog(onDismissRequest = { onDone(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDone(null) }) { Icon(Icons.Rounded.Close, "Vazgeç", tint = c.text) }
                Text("El yazısı / çizim", color = c.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }) { Icon(Icons.AutoMirrored.Rounded.Undo, "Geri al", tint = c.sub) }
                IconButton(onClick = { strokes.clear() }) { Icon(Icons.Rounded.DeleteSweep, "Temizle", tint = c.sub) }
                TextButton(enabled = strokes.isNotEmpty(), onClick = {
                    val w = size.width.toInt().coerceAtLeast(1); val h = size.height.toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val cv = android.graphics.Canvas(bmp); cv.drawColor(paper.toArgb())
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND }
                    strokes.forEach { s -> p.color = s.color.toArgb(); p.strokeWidth = s.w
                        val path = android.graphics.Path(); s.pts.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                        if (s.pts.size == 1) cv.drawPoint(s.pts[0].x, s.pts[0].y, p) else cv.drawPath(path, p) }
                    val name = "draw_${System.currentTimeMillis()}.png"
                    runCatching { File(Store.mediaDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    bmp.recycle()
                    onDone(Media(name, "photo"))
                }) { Text("Ekle", fontWeight = FontWeight.Bold) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp).clip(RoundS).background(paper)) {
                Canvas(Modifier.fillMaxSize().pointerInput(color, width) {
                    detectDragGestures(onDragStart = { cur = listOf(it) }, onDragEnd = { if (cur.isNotEmpty()) strokes.add(Stroke2(cur, color, width)); cur = emptyList() }) { ch, _ -> ch.consume(); cur = cur + ch.position }
                }) {
                    size = this.size
                    // çizgili kâğıt
                    var y = 48f; while (y < this.size.height) { drawLine(Color(0x22886655), Offset(0f, y), Offset(this.size.width, y), 1.5f); y += 48f }
                    fun draw(pts: List<Offset>, col: Color, w: Float) {
                        if (pts.isEmpty()) return
                        if (pts.size == 1) { drawCircle(col, w / 2, pts[0]); return }
                        val p = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                        drawPath(p, col, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                    strokes.forEach { draw(it.pts, it.color, it.w) }
                    draw(cur, color, width)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                inks.forEach { k -> Box(Modifier.size(32.dp).clip(CircleShape).background(k).border(3.dp, if (k == color) c.text else Color.Transparent, CircleShape).clickable { color = k }) }
                Spacer(Modifier.weight(1f))
                listOf(3f, 6f, 12f).forEach { w -> Box(Modifier.size(34.dp).clip(CircleShape).background(if (width == w) c.accentSoft else c.card).clickable { width = w }, contentAlignment = Alignment.Center) { Box(Modifier.size((w + 2).dp).clip(CircleShape).background(c.text)) } }
            }
        }
    }
}

// ============================ Bağlantılar (okuyucuda) ============================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinksSection(e: Entry) {
    val c = Iz.c
    val all = Store.visible()
    val out = remember(e, Store.version) { Links.outgoing(e, all) }
    val back = remember(e, Store.version) { Links.backlinks(e, all) }
    if (e.rota.isNotBlank()) {
        Spacer(Modifier.height(16.dp))
        IzCard(color = c.card2) {
            Label("Rota'dan günün özeti", Color(0xFF4ADE80))
            Spacer(Modifier.height(6.dp)); Text(e.rota.removePrefix("Rota · ").trim(), color = c.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (e.song.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(RoundS).background(Brush.linearGradient(listOf(IzRose.copy(alpha = 0.22f), c.card))).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundChip).background(IzRose), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White) }
            Column(Modifier.padding(start = 12.dp)) { Label("Günün şarkısı · Kadraj", IzRose); Text(e.song, color = c.text, fontWeight = FontWeight.SemiBold) }
        }
    }
    if (e.fine.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { e.fine.forEach { SmallChip(it, IzRose) } }
    }
    if (out.isNotEmpty()) {
        Spacer(Modifier.height(18.dp)); Label("Bağlantılar", c.sub); Spacer(Modifier.height(6.dp))
        out.forEach { (t, target) ->
            Row(Modifier.fillMaxWidth().clip(RoundS).clickable(enabled = target != null) { target?.let { Nav.go(Route.Reader(it.id)) } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Link, null, tint = if (target != null) c.accent else c.faint, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(target?.let { it.title.ifBlank { Tr.long(it.date) } } ?: "$t (bulunamadı)", color = if (target != null) c.text else c.faint)
            }
        }
    }
    if (back.isNotEmpty()) {
        Spacer(Modifier.height(18.dp)); Label("Bu girdiye bağlananlar · ${back.size}", c.sub); Spacer(Modifier.height(8.dp))
        back.forEach { b -> EntryCard(b) { Nav.go(Route.Reader(b.id)) }; Spacer(Modifier.height(8.dp)) }
    }
}

// ============================ Sürüm geçmişi ============================
@Composable
fun VersionsScreen(id: String) {
    val c = Iz.c
    val e = Store.get(id)
    val vs = remember(id, Store.version) { Versions.list(id) }
    var open by remember { mutableStateOf<Versions.V?>(null) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        BackBar("Sürüm geçmişi", { Nav.back() })
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
            item { Text("Düzenlediğin girdinin eski hâlleri cihazda şifreli saklanır. Birini seçip geri alabilirsin.", color = c.sub, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(12.dp)) }
            if (vs.isEmpty()) item { EmptyState(Icons.Rounded.History, "Henüz eski sürüm yok", "Girdiyi düzenledikçe önceki hâlleri burada birikir.") }
            items(vs) { v ->
                val dt = java.time.Instant.ofEpochMilli(v.at).atZone(java.time.ZoneId.systemDefault())
                IzCard(onClick = { open = v }, pad = 14.dp) {
                    Text("${Tr.long(dt.toLocalDate())} · %02d:%02d".format(dt.hour, dt.minute), color = c.accent, style = MaterialTheme.typography.labelLarge)
                    Text(v.e.plain.take(160).ifBlank { v.e.title }, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("${v.e.words} kelime" + if (e != null) " · şimdiki: ${e.words}" else "", color = c.faint, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    open?.let { v ->
        AlertDialog(onDismissRequest = { open = null }, title = { Text("Bu sürüm geri alınsın mı?") },
            text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) { if (v.e.title.isNotBlank()) Text(v.e.title, fontWeight = FontWeight.Bold); Text(v.e.plain, style = writingStyle()) } },
            confirmButton = { TextButton(onClick = { e?.let { cur -> Store.upsert(cur.copy(title = v.e.title, body = v.e.body, updated = System.currentTimeMillis())) }; open = null; Nav.toast = "Eski sürüm geri alındı"; Nav.back() }) { Text("Geri al") } },
            dismissButton = { TextButton(onClick = { open = null }) { Text("Vazgeç") } })
    }
}

// ============================ Anılar: zaman tüneli ============================
@Composable
fun Timeline() {
    val c = Iz.c
    val vis = Store.visible().filter { !it.isEmpty }.sortedByDescending { it.day * 10000 + (it.time.replace(":", "").toIntOrNull() ?: 0) }
    val today = LocalDate.now()
    val past = remember(Store.version) { Insights.onThisDay(vis, today) }
    val places = remember(Store.version) { Places.all(vis) }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val months = remember(vis) { vis.map { YearMonth.from(it.date) }.distinct() }
    fun groupOf(d: LocalDate): String {
        val wk = ChronoUnit.WEEKS.between(d.with(java.time.DayOfWeek.MONDAY), today.with(java.time.DayOfWeek.MONDAY))
        return when { wk == 0L -> "Bu hafta"; wk == 1L -> "Geçen hafta"; else -> Tr.monthYear(d) }
    }
    if (vis.isEmpty()) { EmptyState(Icons.Rounded.Timeline, "Zaman tünelin boş", "Yazdıkça anıların fotoğraflarıyla burada sonsuz bir şerit olur."); return }
    val groups = vis.groupBy { groupOf(it.date) }.toList()
    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), state = state, contentPadding = PaddingValues(start = 20.dp, end = 8.dp, bottom = 40.dp)) {
            past.firstOrNull()?.let { m -> item { PastCard(m, today.year - m.date.year); Spacer(Modifier.height(14.dp)) } }
            if (places.isNotEmpty()) item {
                IzCard(onClick = { Nav.go(Route.Map) }, pad = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Map, null, tint = c.accent); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text("Anı haritası · ${places.size} yer", color = c.text, fontWeight = FontWeight.SemiBold); Text(places.take(3).joinToString(" · ") { it.name }, color = c.sub, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Text("Yerler", color = c.accent, modifier = Modifier.clickable { Nav.go(Route.Places) }.padding(6.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            groups.forEach { (g, es) ->
                item(key = "h$g") { Label(Tr.upper(g), c.sub, Modifier.padding(top = 8.dp, bottom = 8.dp)) }
                items(es, key = { it.id }) { e -> TimelineCard(e); Spacer(Modifier.height(8.dp)) }
            }
        }
        // yıl/ay atlama kaydırıcısı
        if (months.size > 2) {
            var drag by remember { mutableStateOf<YearMonth?>(null) }
            Box(Modifier.width(34.dp).fillMaxHeight().padding(vertical = 12.dp).pointerInput(months) {
                detectDragGestures(onDragEnd = { drag = null }) { ch, _ ->
                    val f = (ch.position.y / size.height).coerceIn(0f, 0.999f)
                    val ym = months[(f * months.size).toInt()]; drag = ym
                    val idx = vis.indexOfFirst { YearMonth.from(it.date) == ym }
                    // başlık ve üst kartlar için kaba bir kaydırma: ilgili grubun ilk girdisi
                    val head = (if (past.isNotEmpty()) 1 else 0) + (if (places.isNotEmpty()) 1 else 0)
                    val g = groups.indexOfFirst { (_, es) -> es.any { YearMonth.from(it.date) == ym } }
                    val before = groups.take(g.coerceAtLeast(0)).sumOf { it.second.size + 1 }
                    val inner = groups.getOrNull(g)?.second?.indexOfFirst { YearMonth.from(it.date) == ym }?.coerceAtLeast(0) ?: 0
                    if (idx >= 0) scope.launch { state.scrollToItem(head + before + 1 + inner) }
                }
            }) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                    months.filterIndexed { i, _ -> i % maxOf(1, months.size / 8) == 0 }.forEach { Text(if (it.monthValue == 1 || it == months.first()) "${it.year % 100}" else Tr.monthsShort[it.monthValue - 1].take(3), color = c.faint, fontSize = 10.sp) }
                }
                drag?.let { Text(Tr.monthYear(it.atDay(1)), color = c.onAccent, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.Center).clip(CircleShape).background(c.accent).padding(horizontal = 6.dp, vertical = 4.dp)) }
            }
        }
    }
}

/** Geçmişte bugün: fotoğraf varsa arka plan, yoksa ruh hâli renginden gradyan. */
@Composable
fun PastCard(e: Entry, years: Int) {
    val mood = Moods[e.mood]
    val ph = e.photos.firstOrNull()
    Box(Modifier.fillMaxWidth().height(210.dp).clip(Round).clickable { Nav.go(Route.Reader(e.id)) }.background(Brush.linearGradient(listOf((mood?.color ?: IzAmber).copy(alpha = 0.9f), IzRose.copy(alpha = 0.7f), Color(0xFF1F1814))))) {
        if (ph != null) Thumb(ph, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(Tr.upper("Geçmişte bugün · $years yıl önce"), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
            Text("“${(e.title.ifBlank { e.plain }).take(90).trim()}…”", color = Color.White, fontFamily = Crimson, fontSize = 22.sp, lineHeight = 26.sp, fontStyle = FontStyle.Italic, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun TimelineCard(e: Entry) {
    val c = Iz.c
    val mood = Moods[e.mood]
    Row(Modifier.fillMaxWidth().clip(RoundS).background(c.card).clickable { Nav.go(Route.Reader(e.id)) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val ph = e.photos.firstOrNull()
        if (ph != null) Thumb(ph, Modifier.size(58.dp).clip(RoundChip))
        else Box(Modifier.size(58.dp).clip(RoundChip).background((mood?.color ?: c.accent).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { if (mood != null) Icon(mood.icon, null, tint = mood.color) else Icon(Icons.Rounded.EditNote, null, tint = c.sub) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mood != null && ph != null) { Icon(mood.icon, null, tint = mood.color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
                Text(e.title.ifBlank { e.plain.lineSequence().firstOrNull { it.isNotBlank() }?.take(60) ?: Tr.long(e.date) }, color = c.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val meta = buildList {
                add(Tr.weekday(e.date)); if (e.photos.isNotEmpty()) add("${e.photos.size} fotoğraf"); if (e.media.any { it.isAudio }) add("🎙")
                if (e.place.isNotBlank()) add("📍 " + e.place.substringBefore(',')); Kinds.of(e)?.let { add(it.emoji) }
            }
            Text(meta.joinToString(" · "), color = c.sub, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ============================ Kişiler ve yerler ============================
@Composable
fun PersonScreen(name: String) {
    val c = Iz.c
    val p = remember(name, Store.version) { People.of(name, Store.visible()) }
    val moods = Insights.moodCounts(p.entries)
    Column(Modifier.fillMaxSize().background(c.bg)) {
        BackBar("", { Nav.back() })
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
            item {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(IzAmber, IzRose))), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(java.util.Locale.forLanguageTag("tr")), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Text("@$name", fontFamily = Crimson, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, color = c.text)
                Text("$name ile yazdığın ${p.days} gün", color = c.sub)
                if (moods.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)) { moods.forEach { (m, n) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(m.color)) } }
                    Spacer(Modifier.height(6.dp))
                    Text("Onunla en çok " + moods.first().first.label.lowercase(java.util.Locale.forLanguageTag("tr")) + " hissettin.", color = c.sub, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(14.dp)); Label("Ortak anılar", c.sub); Spacer(Modifier.height(8.dp))
            }
            items(p.entries, key = { it.id }) { e -> EntryCard(e) { Nav.go(Route.Reader(e.id)) }; Spacer(Modifier.height(10.dp)) }
        }
    }
}

@Composable
fun PlacesList() {
    val c = Iz.c
    val places = remember(Store.version) { Places.all(Store.visible().filter { !it.isEmpty }) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        if (places.isEmpty()) EmptyState(Icons.Rounded.Place, "Henüz yer yok", "Yazarken konum düğmesine dokun; sık gittiğin yerler burada toplanır.")
        else IzCard(onClick = { Nav.go(Route.Map) }, color = c.accentSoft, pad = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Map, null, tint = c.accent); Spacer(Modifier.width(10.dp)); Text("Isı haritalı anı haritasını aç", color = c.text, fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(12.dp))
        val max = places.maxOfOrNull { it.entries.size } ?: 1
        places.forEach { p ->
            IzCard(onClick = { Nav.go(Route.PlaceR(p.name)) }, pad = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(IzAmber.copy(alpha = 0.15f + 0.6f * p.entries.size / max)), contentAlignment = Alignment.Center) { Text("${p.entries.size}", color = Color.White, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = c.text, fontWeight = FontWeight.SemiBold)
                        Text("${p.entries.size} girdi · ${p.photos} fotoğraf · son: ${Tr.ago(p.entries.first().date)}", color = c.sub, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
fun PlacesScreen() { Column(Modifier.fillMaxSize().background(Iz.c.bg)) { BackBar("Yerler", { Nav.back() }); PlacesList() } }

@Composable
fun PlaceScreen(name: String) {
    val c = Iz.c
    val p = remember(name, Store.version) { Places.all(Store.visible()).firstOrNull { it.name == name } }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        BackBar("", { Nav.back() })
        if (p == null) return@Column
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
            item {
                Label("📍 Yer")
                Text(p.name, fontFamily = Crimson, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, color = c.text)
                Text("${p.entries.size} girdi · ${p.photos} fotoğraf · ilk: ${Tr.long(p.entries.last().date)}", color = c.sub)
                val ph = p.entries.flatMap { it.photos }.take(9)
                if (ph.isNotEmpty()) { Spacer(Modifier.height(12.dp)); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(ph) { Thumb(it, Modifier.size(96.dp)) } } }
                Spacer(Modifier.height(14.dp))
            }
            items(p.entries, key = { it.id }) { e -> EntryCard(e) { Nav.go(Route.Reader(e.id)) }; Spacer(Modifier.height(10.dp)) }
        }
    }
}

// ============================ İçgörü ============================
@Composable
fun InsightScreen() {
    val c = Iz.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("İçgörü", style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.weight(1f))
            Pill("Etkenler", tab == 0) { tab = 0 }; Spacer(Modifier.width(6.dp)); Pill("İstatistik", tab == 1) { tab = 1 }
        }
        if (tab == 1) { StatsScreen(); return@Column }
        val vis = Store.visible().filter { !it.isEmpty }
        val factors = remember(Store.version, Lock.hiddenOpen) { Factors.analyze(vis, sleepOf = { Habits.sleepOf(it) }, sportOf = { Habits.sportOf(it) }) }
        var sel by remember { mutableStateOf<Factor?>(null) }
        LaunchedEffect(Prefs.seyirLink) { if (Prefs.seyirLink) Eco.syncSeyir(ctx) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // duygu çubukları: vurgulu çubuklar içgörünün dayandığı günler
            val scores = remember(Store.version) { Factors.dayScores(vis) }
            val today = LocalDate.now().toEpochDay()
            val days = (today - 29..today).toList()
            val focus = sel ?: factors.firstOrNull()
            IzCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Son 30 gün", c.sub, Modifier.weight(1f))
                    Text("🔒 cihazda hesaplandı", color = c.sub, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clip(CircleShape).background(c.card2).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    days.forEach { d ->
                        val s = scores[d]
                        val hi = focus != null && d in focus.days
                        Box(Modifier.weight(1f).fillMaxHeight(((s ?: 0.4) / 5.0).toFloat().coerceIn(0.06f, 1f)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(when { s == null -> c.line; hi -> if (focus!!.delta >= 0) IzAmber else IzRose; else -> c.sub.copy(alpha = 0.35f) }))
                    }
                }
                focus?.let { Spacer(Modifier.height(8.dp)); Text("Vurgulu çubuklar: ${it.label.lowercase(java.util.Locale.forLanguageTag("tr"))} olan günler", color = c.faint, style = MaterialTheme.typography.labelMedium) }
            }
            Spacer(Modifier.height(12.dp))
            if (factors.isEmpty()) IzCard {
                Label("Etken analizi")
                Text("Birkaç hafta ruh hâli seçip yazdıkça uyku, spor, kişiler, hava ve etiketlerin ruh hâlinle ilişkisi burada görünür. Hiçbir veri telefonundan çıkmaz.", color = c.sub)
            } else {
                IzCard(color = Brush.linearGradient(listOf(IzAmber.copy(alpha = 0.18f), c.card)).let { c.card }) {
                    Label("İçgörü")
                    Spacer(Modifier.height(6.dp))
                    Text(Factors.sentence(focus!!), fontFamily = Crimson, fontSize = 22.sp, lineHeight = 28.sp, color = c.text)
                    Text("${focus.withDays} gün etkenli · ${focus.withoutDays} gün etkensiz karşılaştırıldı", color = c.faint, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(12.dp))
                factors.take(10).forEach { f ->
                    val pos = f.delta >= 0
                    Row(Modifier.fillMaxWidth().clip(RoundS).background(if (f == focus) c.card2 else c.card).clickable { sel = f }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(f.emoji, fontSize = 20.sp, modifier = Modifier.width(32.dp))
                        Text(f.label, color = c.text, modifier = Modifier.weight(1f))
                        Text((if (pos) "+" else "−") + "%.1f".format(abs(f.delta)).replace('.', ','), color = if (pos) Color(0xFF7BD88F) else IzRose, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text("Bu bir gözlemdir, yargı değil: ilişki her zaman neden demek değildir.", color = c.faint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            IzCard(pad = 0.dp) {
                SettingRow("Seyir bağlantısı", if (!Eco.installed(ctx, Eco.SEYIR)) "Seyir yüklü değil" else if (Prefs.seyirLink) "Günlük ekran süresi ruh hâli analizine ekleniyor" else "İzin verirsen ekran süresi grafiğe eklenir",
                    Icons.Rounded.PhoneAndroid, onClick = { Prefs.seyirLink = !Prefs.seyirLink; Prefs.save(); if (Prefs.seyirLink) scope.launch { Eco.syncSeyir(ctx, true) } }) {
                    Switch(Prefs.seyirLink, { Prefs.seyirLink = it; Prefs.save(); if (it) scope.launch { Eco.syncSeyir(ctx, true) } }, enabled = Eco.installed(ctx, Eco.SEYIR))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IzCard(Modifier.weight(1f), onClick = { Nav.go(Route.Weekly) }) { Text("💌", fontSize = 26.sp); Spacer(Modifier.height(6.dp)); Text("Haftalık duygu mektubu", color = c.text, fontWeight = FontWeight.SemiBold); Text("Pazar günü hafta özeti", color = c.sub, style = MaterialTheme.typography.bodyMedium) }
                IzCard(Modifier.weight(1f), onClick = { Nav.go(Route.Year(LocalDate.now().year)) }) { Text("✨", fontSize = 26.sp); Spacer(Modifier.height(6.dp)); Text("Yılın İzi ${LocalDate.now().year}", color = c.text, fontWeight = FontWeight.SemiBold); Text("Hikâye biçiminde yıl", color = c.sub, style = MaterialTheme.typography.bodyMedium) }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

// ============================ Haftalık duygu mektubu ============================
@Composable
fun WeeklyScreen() {
    val c = Iz.c
    var ref by remember { mutableStateOf(LocalDate.now()) }
    val w = remember(ref, Store.version) { WeeklyLetter.week(Store.visible(), ref) }
    val text = remember(w) { WeeklyLetter.write(w, Prefs.name) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        BackBar("Haftalık duygu mektubu", { Nav.back() })
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { ref = ref.minusWeeks(1) }) { Icon(Icons.Rounded.ChevronLeft, "Önceki hafta", tint = c.text) }
            Text("${Tr.short(w.from)} – ${Tr.short(w.to)}", color = c.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { if (ref.plusWeeks(1) <= LocalDate.now().plusDays(6)) ref = ref.plusWeeks(1) }) { Icon(Icons.Rounded.ChevronRight, "Sonraki hafta", tint = c.text) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp).clip(Round).background(Brush.verticalGradient(listOf(IzAmber.copy(alpha = 0.10f), c.card))).padding(24.dp)) {
            Text(text, fontFamily = Crimson, fontSize = 20.sp, lineHeight = 30.sp, color = c.text)
            Spacer(Modifier.height(16.dp))
            Text("Bu mektup telefonunda, yazdıklarından hesaplandı.", color = c.faint, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ============================ Yılın İzi ============================
@Composable
fun YearScreen(year: Int) {
    val c = Iz.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = remember(year, Store.version) { YearStory.build(Store.visible(), year) }
    val pager = rememberPagerState { 6 }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { u ->
        if (u != null) scope.launch { val ok = Backup.exportPdf(ctx, u, Store.visible().filter { it.date.year == year && !it.isEmpty }, "Yılın İzi $year", true); Nav.toast = if (ok) "Kitap hazır" else "PDF oluşturulamadı" }
    }
    val bg = listOf(listOf(Color(0xFF3A1E14), Color(0xFF140F0D)), listOf(Color(0xFF4A1F33), Color(0xFF140F0D)), listOf(Color(0xFF4B2A12), Color(0xFF1F1814)),
        listOf(Color(0xFF2E1A2A), Color(0xFF140F0D)), listOf(Color(0xFF3B2416), Color(0xFF241018)), listOf(IzAmber.copy(alpha = 0.6f), IzRose.copy(alpha = 0.6f)))
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(bg[pager.currentPage]))) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { p ->
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp, vertical = 80.dp), verticalArrangement = Arrangement.Center) {
                val big = @Composable { t: String -> Text(t, fontFamily = Crimson, fontWeight = FontWeight.SemiBold, fontSize = 88.sp, lineHeight = 90.sp, color = Color.White) }
                val cap = @Composable { t: String -> Text(Tr.upper(t), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall) }
                val line = @Composable { t: String -> Text(t, fontFamily = Crimson, fontSize = 26.sp, lineHeight = 32.sp, color = Color.White) }
                when (p) {
                    0 -> { cap("Yılın İzi · $year"); big("${s.days}"); line("gün yazdın"); Spacer(Modifier.height(10.dp)); Text("${fmtNum(s.words)} kelime · ${s.entries} girdi · ${s.photos} fotoğraf", color = Color.White.copy(alpha = 0.7f)) }
                    1 -> { cap("En çok kullandığın kelime"); line("“${s.topWord ?: "—"}”"); Spacer(Modifier.height(24.dp)); cap("En uzun seri"); big("${s.longestStreak}"); line("gün üst üste") }
                    2 -> { cap("En mutlu ay"); line(s.happiestMonth?.let { Tr.months[it - 1] + " ☀" } ?: "—"); Spacer(Modifier.height(24.dp)); cap("En çok yazdığın kişi"); line(s.topPerson?.let { "@$it" } ?: "—") }
                    3 -> {
                        cap("Ruh hâli dağılımın")
                        Spacer(Modifier.height(12.dp))
                        val tot = s.moodShare.sumOf { it.second }.coerceAtLeast(1)
                        s.moodShare.take(6).forEach { (m, n) ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
                                Icon(m.icon, null, tint = m.color); Spacer(Modifier.width(10.dp))
                                Box(Modifier.weight(1f).height(14.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) { Box(Modifier.fillMaxWidth(n / tot.toFloat()).fillMaxHeight().clip(CircleShape).background(m.color)) }
                                Text("  %${n * 100 / tot}", color = Color.White)
                            }
                        }
                    }
                    4 -> { cap("En çok gittiğin yer"); line(s.topPlace?.let { "📍 $it" } ?: "—"); Spacer(Modifier.height(24.dp)); cap("Bu yıl"); line(s.line) }
                    else -> {
                        cap("Yılın İzi · $year"); line(s.line); Spacer(Modifier.height(28.dp))
                        Button(onClick = { ShareCard.share(ctx, ShareCard.render(s.line, "Yılın İzi $year", "${s.days} gün · ${fmtNum(s.words)} kelime", IzAmber, IzRose)) }, shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1A0F09))) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text("Kart olarak paylaş") }
                        Text("Kartta kişisel metin yer almaz.", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                        OutlinedButton(onClick = { Lock.suppress = true; pdf.launch("Yilin-Izi-$year.pdf") }, shape = CircleShape) { Icon(Icons.Rounded.MenuBook, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("Kapaklı PDF kitap", color = Color.White) }
                    }
                }
            }
        }
        // hikâye ilerleme çubukları
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(6) { i -> Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i <= pager.currentPage) 0.95f else 0.3f))) }
            IconButton(onClick = { Nav.back() }) { Icon(Icons.Rounded.Close, "Kapat", tint = Color.White) }
        }
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) {
            if (year > (Store.entries.minOfOrNull { it.date.year } ?: year)) TextButton(onClick = { Nav.replace(Route.Year(year - 1)) }) { Text("‹ ${year - 1}", color = Color.White.copy(alpha = 0.7f)) }
            if (year < LocalDate.now().year) TextButton(onClick = { Nav.replace(Route.Year(year + 1)) }) { Text("${year + 1} ›", color = Color.White.copy(alpha = 0.7f)) }
        }
    }
}

// ============================ Ben ============================
@Composable
fun MeScreen() {
    val c = Iz.c
    val ctx = LocalContext.current
    val vis = Store.visible().filter { !it.isEmpty }
    val days = vis.map { it.day }.toSet()
    val people = remember(Store.version) { People.all(vis) }
    val places = remember(Store.version) { Places.all(vis) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(Brush.linearGradient(listOf(IzAmber, IzRose))), contentAlignment = Alignment.Center) {
                Text(Prefs.name.take(1).ifBlank { "İ" }.uppercase(java.util.Locale.forLanguageTag("tr")), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(Prefs.name.ifBlank { "Ben" }, fontFamily = Crimson, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, color = c.text)
                Text("${days.size} gün · ${fmtNum(vis.sumOf { it.words })} kelime · 🔥 ${Insights.streak(days)}", color = c.sub, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(18.dp))
        Group("Arşivim") {
            SettingRow("Tüm girdiler", "${vis.size} girdi · ara, süz, sırala", Icons.Rounded.EditNote, onClick = { Nav.go(Route.Entries) })
            SettingRow("Kişiler", if (people.isEmpty()) "Yazarken @isim yaz" else people.take(3).joinToString(" · ") { "@${it.name}" }, Icons.Rounded.People, onClick = { Nav.tab = 2; Nav.memTab = 5 })
            SettingRow("Yerler", if (places.isEmpty()) "Konum ekledikçe birikir" else "${places.size} yer · ${places.first().name}", Icons.Rounded.Place, onClick = { Nav.go(Route.Places) })
            SettingRow("Geleceğe mektuplar", "${Store.letters.size} mektup · tarihe ya da yere bağlı", Icons.Rounded.MarkEmailUnread, onClick = { Nav.tab = 2; Nav.memTab = 2 })
        }
        Group("Hikâyem") {
            SettingRow("Yılın İzi ${LocalDate.now().year}", "Hikâye biçiminde yıllık özet ve paylaşım kartı", Icons.Rounded.AutoAwesome, onClick = { Nav.go(Route.Year(LocalDate.now().year)) })
            SettingRow("Haftalık duygu mektubu", "Haftanın sıcak bir dille özeti", Icons.Rounded.Mail, onClick = { Nav.go(Route.Weekly) })
            SettingRow("İstatistikler", "Seriler, kelimeler, ruh hâli grafiği", Icons.Rounded.BarChart, onClick = { Nav.tab = 3 })
        }
        Group("Bağlantılar") {
            SettingRow("Rota", if (Eco.installed(ctx, Eco.ROTA)) "Günün özeti yazma ekranındaki ✓ düğmesiyle eklenir" else "Rota yüklü değil", Icons.Rounded.TaskAlt)
            SettingRow("Kadraj", if (Eco.installed(ctx, Eco.KADRAJ)) "Günün şarkısı yeni girdilere eklenir" else "Kadraj yüklü değil", Icons.Rounded.MusicNote, onClick = { Prefs.kadrajSong = !Prefs.kadrajSong; Prefs.save() }) {
                Switch(Prefs.kadrajSong, { Prefs.kadrajSong = it; Prefs.save() })
            }
            SettingRow("Seyir", if (Prefs.seyirLink) "Ekran süresi içgörülere ekleniyor" else "Kapalı · İçgörü sekmesinden açılır", Icons.Rounded.PhoneAndroid, onClick = { Nav.tab = 3 })
            SettingRow("Kasa", if (kasa.sdk.KasaSdk.kasaInstalled(ctx)) "Medya dahil şifreli yedek Kasa üzerinden" else "Kasa yüklü değil", Icons.Rounded.Shield)
        }
        Group {
            SettingRow("Ayarlar", "Tema, gece modu, kilit, yedek", Icons.Rounded.Settings, onClick = { Nav.go(Route.Settings) })
            SettingRow("Çöp kutusu", null, Icons.Rounded.DeleteOutline, onClick = { Nav.go(Route.Trash) })
        }
        Spacer(Modifier.height(30.dp))
    }
}
