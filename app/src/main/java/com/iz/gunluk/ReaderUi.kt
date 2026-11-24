package com.iz.gunluk

import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.Locale

/** Türkçe seslendirme; tek örnek. */
class Speaker(ctx: android.content.Context, onDone: () -> Unit) {
    private var ready = false
    private val tts: TextToSpeech = TextToSpeech(ctx.applicationContext) { st ->
        ready = st == TextToSpeech.SUCCESS
        if (ready) tts.language = Locale.forLanguageTag("tr-TR")
    }.also {
        it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { if (id == "last") onDone() }
            @Deprecated("") override fun onError(id: String?) { onDone() }
        })
    }

    fun speak(text: String): Boolean {
        if (!ready) return false
        tts.stop()
        val parts = text.split(Regex("(?<=[.!?…])\\s+|\\n+")).filter { it.isNotBlank() }
        parts.forEachIndexed { i, p -> tts.speak(p, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, if (i == parts.lastIndex) "last" else "p$i") }
        return true
    }
    fun stop() { tts.stop() }
    fun shutdown() { tts.stop(); tts.shutdown() }
}

@Composable
fun ReaderScreen(id: String) {
    val c = Iz.c
    val ctx = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = Store.version
    val e = Store.get(id)
    if (e == null) { LaunchedEffect(Unit) { Nav.back() }; return }
    if (e.hidden && !Lock.hiddenOpen) {
        AuthScreen("Gizli kayıt", "Bu kaydı görmek için doğrula", mode = AuthMode.Hidden, onCancel = { Nav.back() }) { Lock.hiddenOpen = true }
        return
    }
    var speaking by remember { mutableStateOf(false) }
    val speaker = remember { Speaker(ctx) { speaking = false } }
    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }
    var confirmDelete by remember { mutableStateOf(false) }
    val mood = Moods[e.mood]

    Column(Modifier.fillMaxSize().background(c.bg)) {
        BackBar("", { Nav.back() }) {
            IconButton(onClick = {
                speaking = if (speaking) { speaker.stop(); false } else speaker.speak(listOf(e.title, e.plain).filter { it.isNotBlank() }.joinToString(". "))
            }) { Icon(if (speaking) Icons.Rounded.StopCircle else Icons.AutoMirrored.Rounded.VolumeUp, "Seslendir", tint = if (speaking) c.accent else c.sub) }
            IconButton(onClick = { Store.upsert(e.copy(fav = !e.fav, updated = System.currentTimeMillis())) }) {
                Icon(if (e.fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favori", tint = if (e.fav) Color(0xFFFF5C8A) else c.sub)
            }
            Box {
                var shareMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { shareMenu = true }) { Icon(Icons.Rounded.Share, "Paylaş", tint = c.sub) }
                DropdownMenu(shareMenu, { shareMenu = false }) {
                    DropdownMenuItem(text = { Text("Anı kartı (görsel)") }, leadingIcon = { Icon(Icons.Rounded.Image, null) }, onClick = { shareMenu = false; ShareCard.forEntry(ctx, e) })
                    DropdownMenuItem(text = { Text("Metin olarak") }, leadingIcon = { Icon(Icons.Rounded.Notes, null) }, onClick = {
                        shareMenu = false
                        Lock.suppress = true
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Backup.text(listOf(e))), "Paylaş"))
                    })
                }
            }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Sil", tint = c.sub) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Label(Tr.longDay(e.date) + if (e.time.isNotEmpty()) " · ${e.time}" else "")
            Spacer(Modifier.height(8.dp))
            Text(Tr.ago(e.date), color = c.faint, style = MaterialTheme.typography.bodyMedium)
            if (e.title.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(e.title, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Palette.family(Prefs.font)), color = c.text)
            }
            Spacer(Modifier.height(14.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mood != null) MetaChip(mood.icon, mood.label, mood.color) { }
                e.extraMoods.mapNotNull { Moods[it] }.forEach { m -> MetaChip(m.icon, m.label, m.color) { } }
                if (e.energy > 0) MetaChip(Icons.Rounded.Bolt, "Enerji ${e.energy}/10", Color(0xFFFFC53D)) { }
                if (e.place.isNotBlank()) MetaChip(Icons.Rounded.Place, e.place, c.sub) { if (e.lat != null) Nav.go(Route.Map) }
                if (e.weather.isNotBlank()) MetaChip(Icons.Rounded.WbSunny, e.weather, c.sub) { }
                e.allTags.forEach { t -> MetaChip(Icons.Rounded.Tag, t, c.accent) { Nav.listTag = t; Nav.home(); Nav.tab = 1 } }
                e.people.forEach { pp -> MetaChip(Icons.Rounded.Person, pp, Color(0xFF36CFC9)) { Nav.listPerson = pp; Nav.home(); Nav.tab = 1 } }
            }
            if (e.question.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(e.question, color = c.accent, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
            RichText.blocks(e.body).forEach { b ->
                when (b) {
                    is RichText.TextBlock -> SelectionContainer { Text(RichText.render(b.text, c.accent, c.sub, c.highlight), style = writingStyle()) }
                    is RichText.PhotoBlock -> {
                        val m = e.media.firstOrNull { it.file == b.file } ?: Media(b.file, "photo", 0)
                        Spacer(Modifier.height(8.dp))
                        Thumb(m, Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundS).clickable { MediaKit.open(ctx, m) })
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            val rest = e.media.filter { it.file !in e.inlineFiles }
            if (rest.isNotEmpty()) { Spacer(Modifier.height(18.dp)); MediaStrip(rest) }
            Spacer(Modifier.height(24.dp))
            Text("${e.words} kelime · ${e.plain.length} karakter", color = c.faint, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(100.dp))
        }
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(20.dp), contentAlignment = Alignment.BottomEnd) {
        ExtendedFloatingActionButton(onClick = { Nav.go(Route.Editor(id = e.id)) }, containerColor = c.accent, contentColor = c.onAccent, shape = CircleShape) {
            Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(8.dp)); Text("Düzenle")
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Bu girdi silinsin mi?") },
        text = { Text("Girdi çöp kutusuna taşınır; 30 gün içinde Ayarlar > Çöp kutusu'ndan geri alabilirsin.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; Nav.back(); Store.delete(e.id); Nav.toast = "Çöp kutusuna taşındı" }) { Text("Sil", color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
    )
}

/** Bir günün tüm kayıtları: takvimden açılır. */
@Composable
fun DayScreen(day: Long) {
    val c = Iz.c
    @Suppress("UNUSED_VARIABLE") val v = Store.version
    val d = LocalDate.ofEpochDay(day)
    val list = Store.visible().filter { it.day == day }.sortedBy { it.time }
    val hiddenCount = Store.entries.count { it.day == day && it.hidden } - list.count { it.hidden }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        BackBar(Tr.long(d), { Nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Label(Tr.days[d.dayOfWeek.value - 1] + " · " + Tr.ago(d))
            Spacer(Modifier.height(14.dp))
            if (list.isEmpty()) EmptyState(Icons.Rounded.EventNote, "Bu güne ait kayıt yok",
                if (d.isAfter(LocalDate.now())) "Bu gün henüz gelmedi." else "Bu günü hatırlıyorsan şimdi yazabilirsin.",
                if (!d.isAfter(LocalDate.now())) "Bu güne yaz" else null) { Nav.go(Route.Editor(day = day)) }
            list.forEach { e -> EntryCard(e, showDate = false) { Nav.go(Route.Reader(e.id)) }; Spacer(Modifier.height(10.dp)) }
            if (hiddenCount > 0) Text("$hiddenCount gizli kayıt var · göster", color = c.accent, modifier = Modifier.clip(CircleShape).clickable { Store.entries.firstOrNull { it.day == day && it.hidden }?.let { Nav.go(Route.Reader(it.id)) } }.padding(8.dp))
            // aynı gün, önceki yıllar
            val past = Store.visible().filter { it.date.monthValue == d.monthValue && it.date.dayOfMonth == d.dayOfMonth && it.date.year != d.year }.sortedByDescending { it.day }
            if (past.isNotEmpty()) {
                SectionTitle("Diğer yıllarda bu gün")
                past.forEach { e -> EntryCard(e) { Nav.go(Route.Reader(e.id)) }; Spacer(Modifier.height(10.dp)) }
            }
            if (list.isNotEmpty() && !d.isAfter(LocalDate.now())) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { Nav.go(Route.Editor(day = day)) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Bu güne bir girdi daha ekle") }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
