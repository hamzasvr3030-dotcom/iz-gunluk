package com.iz.gunluk

import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(r: Route.Editor) {
    val c = Iz.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val initial = remember(r) {
        r.id?.let { Store.get(it) } ?: Store.newEntry(r.day?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()).let { e ->
            val t = r.template?.let { Templates[it] }
            when {
                t != null -> e.copy(title = t.title, body = t.body, template = t.key, tags = listOf(t.tag))
                r.question != null -> e.copy(question = r.question)
                else -> e
            }
        }
    }
    var e by remember(r) { mutableStateOf(initial) }
    var body by remember(r) { mutableStateOf(TextFieldValue(initial.body, TextRange(initial.body.length))) }
    var saved by remember { mutableStateOf(true) }
    var focus by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var hit by remember { mutableIntStateOf(0) }
    var details by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var pickTemplate by remember { mutableStateOf(r.template == "_pick") }
    var datePick by remember { mutableStateOf(false) }
    var timePick by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaKit.Recorder?>(null) }
    var recTick by remember { mutableLongStateOf(0L) }
    var locBusy by remember { mutableStateOf(false) }
    val bodyFocus = remember { FocusRequester() }

    fun update(f: (Entry) -> Entry) { e = f(e); saved = false }
    // geri al / yinele: yazma duraksamalarında ve satır sonlarında anlık görüntü alınır
    val undo = remember { mutableStateListOf<TextFieldValue>() }
    val redo = remember { mutableStateListOf<TextFieldValue>() }
    var lastSnap by remember { mutableLongStateOf(0L) }
    fun applyBody(v: TextFieldValue) { if (v.text != body.text) saved = false; body = v; e = e.copy(body = v.text) }
    fun setBody(v: TextFieldValue) {
        if (v.text != body.text) {
            val now = System.currentTimeMillis()
            val boundary = v.text.length < body.text.length - 1 || v.text.length > body.text.length + 1 || v.text.endsWith("\n") || v.text.endsWith(" ")
            if (undo.isEmpty() || now - lastSnap > 1200 || boundary) { undo.add(body); if (undo.size > 200) undo.removeAt(0); lastSnap = now }
            redo.clear()
        }
        applyBody(v)
    }
    fun undoOnce() { if (undo.isNotEmpty()) { redo.add(body); applyBody(undo.removeAt(undo.lastIndex)) } }
    fun redoOnce() { if (redo.isNotEmpty()) { undo.add(body); applyBody(redo.removeAt(redo.lastIndex)) } }
    fun insertAtCursor(ins: String, lineMode: Boolean = false) {
        val v = body; val at = v.selection.end; val pre = v.text.substring(0, at); val post = v.text.substring(at)
        val t = if (lineMode) (if (pre.isEmpty() || pre.endsWith("\n")) "" else "\n") + ins + (if (post.startsWith("\n")) "" else "\n")
            else (if (pre.isEmpty() || pre.endsWith(" ") || pre.endsWith("\n")) "" else " ") + ins
        setBody(TextFieldValue(pre + t + post, TextRange(at + t.length)))
    }

    fun persist() {
        val cur = e.copy(body = body.text)
        val existed = Store.get(cur.id) != null
        if (cur.isEmpty) { if (existed) Store.delete(cur.id) }
        else Store.upsert(cur.copy(updated = System.currentTimeMillis()))
        saved = true
    }

    // Otomatik kayıt: yazmayı bıraktıktan 0,6 sn sonra
    LaunchedEffect(e, body.text) { if (!saved) { delay(600); persist() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { if (!saved) persist() }
    DisposableEffect(Unit) { onDispose { recorder?.stop()?.let { m -> e = e.copy(media = e.media + m) }; if (!saved) persist() } }

    fun close() { if (!saved) persist(); Nav.back() }
    BackHandler { if (focus) focus = false else if (findOpen) { findOpen = false; query = "" } else close() }

    // ----- başlatıcılar -----
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        scope.launch { val ms = uris.mapNotNull { MediaKit.import(ctx, it, "photo") }; if (ms.isNotEmpty()) update { it.copy(media = it.media + ms) } }
    }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) scope.launch { MediaKit.import(ctx, u, "video")?.let { m -> update { it.copy(media = it.media + m) } } }
    }
    val inlinePhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) scope.launch { MediaKit.import(ctx, u, "photo")?.let { m -> update { it.copy(media = it.media + m) }; insertAtCursor(RichText.photoToken(m.file), lineMode = true) } }
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        // "sesli not + yazı": tanıyıcı sesi de döndürdüyse ses notu olarak sakla
        res.data?.data?.let { au -> scope.launch { MediaKit.import(ctx, au, "audio")?.let { m -> update { it.copy(media = it.media + m.copy(kind = "audio")) } } } }
        val t = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!t.isNullOrBlank()) {
            val cur = body; val at = cur.selection.end
            val pre = cur.text.substring(0, at); val sep = if (pre.isEmpty() || pre.endsWith(" ") || pre.endsWith("\n")) "" else " "
            val ins = sep + t.replaceFirstChar { it.uppercase() }
            setBody(TextFieldValue(pre + ins + cur.text.substring(at), TextRange(at + ins.length)))
        }
    }
    fun dictate(keepAudio: Boolean = false) {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR").putExtra(RecognizerIntent.EXTRA_PROMPT, if (keepAudio) "Konuş: hem ses notu hem yazı olacak" else "Konuş, ben yazayım")
        if (keepAudio) i.putExtra("android.speech.extra.GET_AUDIO_FORMAT", "audio/AMR").putExtra("android.speech.extra.GET_AUDIO", true)
        try { Lock.suppress = true; speech.launch(i) } catch (_: Exception) { Lock.suppress = false; Nav.toast = "Bu cihazda konuşma tanıma bulunamadı" }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { val rc = MediaKit.Recorder(ctx); if (rc.start()) recorder = rc else Nav.toast = "Kayıt başlatılamadı" } else Nav.toast = "Mikrofon izni gerekli"
    }
    fun addLocation() {
        locBusy = true
        scope.launch {
            val l = MediaKit.location(ctx)
            if (l == null) { Nav.toast = "Konum alınamadı (konum açık mı?)"; locBusy = false; return@launch }
            val name = MediaKit.placeName(ctx, l.latitude, l.longitude).ifBlank { "%.4f, %.4f".format(l.latitude, l.longitude) }
            val w = MediaKit.weather(l.latitude, l.longitude)
            update { it.copy(place = name, lat = l.latitude, lon = l.longitude, weather = w.ifBlank { it.weather }) }
            locBusy = false
        }
    }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { m ->
        if (m.values.any { it }) addLocation() else Nav.toast = "Konum izni verilmedi"
    }
    LaunchedEffect(Unit) {
        if (r.template == "_voice") dictate()
        else if (r.id == null && !pickTemplate) { delay(250); runCatching { bodyFocus.requestFocus() } }
        if (r.id == null && Prefs.weatherAuto && MediaKit.hasLocation(ctx)) addLocation()
    }
    LaunchedEffect(recorder) { while (recorder != null) { recTick = System.currentTimeMillis() - recorder!!.started; delay(250) } }

    // ----- biçimlendirme -----
    fun wrap(m: String) {
        val v = body; val s = v.selection.min; val en = v.selection.max
        val t = v.text
        if (s != en) {
            val inner = t.substring(s, en)
            if (s >= m.length && t.startsWith(m, s - m.length) && t.startsWith(m, en)) {
                setBody(TextFieldValue(t.substring(0, s - m.length) + inner + t.substring(en + m.length), TextRange(s - m.length, en - m.length)))
            } else setBody(TextFieldValue(t.substring(0, s) + m + inner + m + t.substring(en), TextRange(s + m.length, en + m.length)))
        } else setBody(TextFieldValue(t.substring(0, s) + m + m + t.substring(s), TextRange(s + m.length)))
    }
    fun prefix(p: String) {
        val v = body; val t = v.text
        val ls = t.lastIndexOf('\n', (v.selection.min - 1).coerceAtLeast(-1)).let { if (it < 0) 0 else it + 1 }
        val line = t.substring(ls)
        val known = listOf("- ", "> ", "[ ] ", "[x] ") + Regex("^\\d{1,3}\\. ").find(line)?.value.let { listOfNotNull(it) }
        val cur = known.firstOrNull { line.startsWith(it) }
        val np = if (p == "1. ") {
            // önceki satırdaki numarayı devam ettir
            val prev = t.substring(0, (ls - 1).coerceAtLeast(0)).substringAfterLast('\n')
            Regex("^(\\d{1,3})\\. ").find(prev)?.groupValues?.get(1)?.toInt()?.plus(1)?.let { "$it. " } ?: "1. "
        } else p
        val (nt, shift) = when {
            cur == null -> t.substring(0, ls) + np + line to np.length
            cur == np || (p == "1. " && cur.matches(Regex("\\d{1,3}\\. "))) -> t.substring(0, ls) + line.removePrefix(cur) to -cur.length
            else -> t.substring(0, ls) + np + line.removePrefix(cur) to np.length - cur.length
        }
        setBody(TextFieldValue(nt, TextRange((v.selection.end + shift).coerceIn(ls, nt.length))))
    }
    /** Enter'a basınca madde/numara/yapılacak satırını otomatik sürdür. */
    fun onBodyChange(v: TextFieldValue) {
        val old = body.text
        if (v.text.length == old.length + 1 && v.selection.collapsed && v.selection.start > 0 && v.text[v.selection.start - 1] == '\n') {
            val at = v.selection.start - 1
            val prevLine = v.text.substring(0, at).substringAfterLast('\n')
            val num = Regex("^(\\d{1,3})\\. ").find(prevLine)
            val pre = when {
                prevLine.startsWith("- ") -> "- "; prevLine.startsWith("[ ] ") || prevLine.startsWith("[x] ") -> "[ ] "
                prevLine.startsWith("> ") -> "> "; num != null -> "${num.groupValues[1].toInt() + 1}. "; else -> null
            }
            if (pre != null) {
                val marker = if (pre.matches(Regex("\\d+\\. "))) num!!.value else pre
                if (prevLine == marker || prevLine == "[x] ") {   // boş madde → listeden çık
                    val ls = at - prevLine.length
                    val nt = v.text.substring(0, ls) + v.text.substring(at + 1)
                    setBody(TextFieldValue(nt, TextRange(ls))); return
                }
                val nt = v.text.substring(0, at + 1) + pre + v.text.substring(at + 1)
                setBody(TextFieldValue(nt, TextRange(at + 1 + pre.length))); return
            }
        }
        setBody(v)
    }

    val hits = remember(body.text, query) { Text.findAll(body.text, query) }
    fun jump(i: Int) { if (hits.isEmpty()) return; hit = Math.floorMod(i, hits.size); val p = hits[hit]; body = body.copy(selection = TextRange(p, p + query.length)); runCatching { bodyFocus.requestFocus() } }

    val words = remember(body.text) { Text.words(RichText.plain(body.text)) }
    val mood = Moods[e.mood]

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        // ----- üst çubuk -----
        AnimatedVisibility(!focus) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { close() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri", tint = c.text) }
                Column(Modifier.weight(1f).clip(RoundS).clickable { datePick = true }.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text(Tr.upper(Tr.longDay(e.date)), style = MaterialTheme.typography.labelSmall, color = c.accent, maxLines = 1)
                    Text(if (saved) "Kaydedildi · ${e.time}" else "Kaydediliyor…", style = MaterialTheme.typography.bodyMedium, color = c.sub,
                        modifier = Modifier.clickable { timePick = true })
                }
                IconButton(onClick = { findOpen = !findOpen; if (!findOpen) query = "" }) { Icon(Icons.Rounded.Search, "Metinde ara", tint = c.sub) }
                IconButton(onClick = { focus = true }) { Icon(Icons.Rounded.CenterFocusWeak, "Odak modu", tint = c.sub) }
                IconButton(onClick = { update { it.copy(fav = !it.fav) } }) {
                    Icon(if (e.fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favori", tint = if (e.fav) Color(0xFFFF5C8A) else c.sub)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Daha", tint = c.sub) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Şablon ekle") }, { menu = false; pickTemplate = true }, leadingIcon = { Icon(Icons.Rounded.Dashboard, null) })
                        DropdownMenuItem({ Text(if (e.hidden) "Gizliliği kaldır" else "Gizli günlük yap") }, {
                            menu = false
                            if (!e.hidden && !Lock.canProtectHidden) Nav.toast = "Önce Ayarlar > Gizlilik'ten PIN oluştur"
                            else { update { it.copy(hidden = !it.hidden) }; if (!e.hidden) Lock.hiddenOpen = true }
                        }, leadingIcon = { Icon(Icons.Rounded.Lock, null) })
                        DropdownMenuItem({ Text("Tarihi değiştir") }, { menu = false; datePick = true }, leadingIcon = { Icon(Icons.Rounded.Event, null) })
                        DropdownMenuItem({ Text("Saati değiştir") }, { menu = false; timePick = true }, leadingIcon = { Icon(Icons.Rounded.Schedule, null) })
                        DropdownMenuItem({ Text("Sil", color = c.danger) }, { menu = false; confirmDelete = true }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = c.danger) })
                    }
                }
            }
        }
        AnimatedVisibility(findOpen && !focus) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(CircleShape).background(c.card).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = c.sub, modifier = Modifier.size(18.dp))
                BasicTextField(query, { query = it; hit = 0 }, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 12.dp),
                    decorationBox = { inner -> if (query.isEmpty()) Text("Bu yazıda ara", color = c.faint); inner() })
                Text(if (query.isBlank()) "" else if (hits.isEmpty()) "0" else "${hit + 1}/${hits.size}", color = c.sub, style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { jump(hit - 1) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Önceki", tint = c.sub) }
                IconButton(onClick = { jump(hit + 1) }) { Icon(Icons.Rounded.KeyboardArrowDown, "Sonraki", tint = c.sub) }
            }
        }

        // ----- içerik -----
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            if (focus) Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), contentAlignment = Alignment.TopEnd) {
                Text("Odak modundan çık", color = c.faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clip(CircleShape).clickable { focus = false }.padding(8.dp))
            }
            if (e.question.isNotBlank() && !focus) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.clip(RoundS).background(c.accentSoft).padding(12.dp)) {
                    Icon(Icons.Rounded.Lightbulb, null, tint = c.accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(e.question, color = c.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                e.title, { v -> update { it.copy(title = v.replace("\n", "")) } },
                textStyle = TextStyle(color = c.text, fontSize = (24 * Prefs.fontScale).sp, fontWeight = FontWeight.Bold, fontFamily = Palette.family(Prefs.font)),
                cursorBrush = SolidColor(c.accent), keyboardOptions = KeyboardOptions(capitalization = if (Prefs.autoCap) KeyboardCapitalization.Sentences else KeyboardCapitalization.None, autoCorrectEnabled = Prefs.autoCorrect),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner -> if (e.title.isEmpty()) Text("Başlık (isteğe bağlı)", color = c.faint, fontSize = (24 * Prefs.fontScale).sp, fontWeight = FontWeight.Bold); inner() },
            )
            if (!focus) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaChip(mood?.icon ?: Icons.Rounded.Mood, (mood?.label ?: "Ruh hâli") + if (e.extraMoods.isNotEmpty()) " +${e.extraMoods.size}" else "", mood?.color ?: c.sub) { details = true }
                    MetaChip(Icons.Rounded.Bolt, if (e.energy > 0) "Enerji ${e.energy}/10" else "Enerji", if (e.energy > 0) Color(0xFFFFC53D) else c.sub) { details = true }
                    if (e.place.isNotBlank()) MetaChip(Icons.Rounded.Place, e.place, c.sub) { details = true }
                    if (e.weather.isNotBlank()) MetaChip(Icons.Rounded.WbSunny, e.weather, c.sub) { details = true }
                    e.allTags.forEach { t -> MetaChip(Icons.Rounded.Tag, t, c.accent) { details = true } }
                    if (e.hidden) MetaChip(Icons.Rounded.Lock, "Gizli", c.sub) { }
                }
            }
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                body, ::onBodyChange,
                textStyle = writingStyle(),
                cursorBrush = SolidColor(c.accent),
                visualTransformation = RichText.Editor(c.accent, c.faint, c.highlight, if (findOpen) query else ""),
                keyboardOptions = KeyboardOptions(capitalization = if (Prefs.autoCap) KeyboardCapitalization.Sentences else KeyboardCapitalization.None, autoCorrectEnabled = Prefs.autoCorrect),
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp).ruled(Prefs.ruled, c.line, 17f * Prefs.fontScale * Prefs.lineHeight).focusRequester(bodyFocus),
                decorationBox = { inner ->
                    if (body.text.isEmpty()) Text(e.question.ifBlank { "Bugün neler oldu? Aklından geçenleri yaz…" }, style = writingStyle().copy(color = c.faint))
                    inner()
                },
            )
            // medya
            val inl = e.inlineFiles
            val inlineMedia = e.media.filter { it.file in inl }
            val other = e.media.filter { it.file !in inl }
            if (inlineMedia.isNotEmpty() && !focus) {
                Spacer(Modifier.height(12.dp))
                Text("Metindeki fotoğraflar", color = c.faint, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                MediaStrip(inlineMedia, small = true)
            }
            if (other.isNotEmpty() && !focus) {
                Spacer(Modifier.height(12.dp))
                MediaStrip(other, onRemove = { m -> update { it.copy(media = it.media - m) } })
            }
            Spacer(Modifier.height(80.dp))
        }

        // ----- kayıt çubuğu -----
        recorder?.let { rc ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(CircleShape).background(c.danger.copy(alpha = 0.15f)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.danger)); Spacer(Modifier.width(10.dp))
                Text("Ses kaydı · ${MediaKit.fmt(recTick)}", color = c.text, modifier = Modifier.weight(1f))
                Text("Bitir", color = c.danger, fontWeight = FontWeight.Bold, modifier = Modifier.clip(CircleShape).clickable {
                    rc.stop()?.let { m -> update { it.copy(media = it.media + m) } }; recorder = null
                }.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }

        // ----- alt araç çubuğu -----
        if (!focus) {
            Row(Modifier.fillMaxWidth().background(c.card).navigationBarsPadding().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Tool(Icons.AutoMirrored.Rounded.Undo, "Geri al", if (undo.isEmpty()) c.faint else null) { undoOnce() }
                    Tool(Icons.AutoMirrored.Rounded.Redo, "Yinele", if (redo.isEmpty()) c.faint else null) { redoOnce() }
                    VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp), color = c.line)
                    Tool(Icons.Rounded.Title, "Başlık") { prefix("## ") }
                    Tool(Icons.Rounded.FormatBold, "Kalın") { wrap("**") }
                    Tool(Icons.Rounded.FormatItalic, "İtalik") { wrap("*") }
                    Tool(Icons.Rounded.BorderColor, "Vurgula") { wrap("==") }
                    Tool(Icons.AutoMirrored.Rounded.FormatListBulleted, "Madde") { prefix("- ") }
                    Tool(Icons.Rounded.FormatListNumbered, "Numaralı") { prefix("1. ") }
                    Tool(Icons.Rounded.CheckBox, "Yapılacak") { prefix("[ ] ") }
                    Tool(Icons.Rounded.FormatQuote, "Alıntı") { prefix("> ") }
                    Tool(Icons.Rounded.Tag, "Etiket") {
                        val v = body; val at = v.selection.end; val pre = v.text.substring(0, at)
                        val ins = (if (pre.isEmpty() || pre.endsWith(" ") || pre.endsWith("\n")) "" else " ") + "#"
                        setBody(TextFieldValue(pre + ins + v.text.substring(at), TextRange(at + ins.length)))
                    }
                    Tool(Icons.Rounded.AlternateEmail, "Kişi") { insertAtCursor("@") }
                    VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp), color = c.line)
                    Tool(Icons.Rounded.AddPhotoAlternate, "Metne fotoğraf") { Lock.suppress = true; inlinePhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    Tool(Icons.Rounded.Image, "Fotoğraf") { Lock.suppress = true; photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    Tool(Icons.Rounded.Videocam, "Video") { Lock.suppress = true; video.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
                    Tool(Icons.Rounded.Mic, "Ses notu", if (recorder != null) c.danger else null) {
                        if (recorder != null) { recorder!!.stop()?.let { m -> update { it.copy(media = it.media + m) } }; recorder = null }
                        else { micPerm.launch(Manifest.permission.RECORD_AUDIO) }
                    }
                    Tool(Icons.Rounded.KeyboardVoice, "Konuşarak yaz") { dictate() }
                    Tool(Icons.Rounded.RecordVoiceOver, "Sesli not + yazı") { dictate(keepAudio = true) }
                    Tool(if (locBusy) Icons.Rounded.HourglassTop else Icons.Rounded.MyLocation, "Konum ve hava") {
                        if (MediaKit.hasLocation(ctx)) addLocation() else { locPerm.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }
                    }
                    Tool(Icons.Rounded.Tune, "Ayrıntılar") { details = true }
                }
                Text("$words kelime · ${body.text.length} kr", style = MaterialTheme.typography.labelMedium, color = c.faint, modifier = Modifier.padding(horizontal = 8.dp))
            }
        } else {
            Text("$words kelime", style = MaterialTheme.typography.labelMedium, color = c.faint, modifier = Modifier.align(Alignment.CenterHorizontally).navigationBarsPadding().padding(8.dp))
        }
    }

    // ----- sayfalar ve iletişim kutuları -----
    if (details) ModalBottomSheet(onDismissRequest = { details = false }, containerColor = c.card) {
        DetailsSheet(e, onChange = { f -> update(f) }, onLocation = {
            if (MediaKit.hasLocation(ctx)) addLocation() else { locPerm.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }
        }, locBusy = locBusy)
    }
    if (pickTemplate) ModalBottomSheet(onDismissRequest = { pickTemplate = false }, containerColor = c.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Şablon seç", style = MaterialTheme.typography.titleLarge, color = c.text)
            Spacer(Modifier.height(12.dp))
            Templates.all.forEach { t ->
                Row(Modifier.fillMaxWidth().clip(RoundS).clickable {
                    val sep = if (body.text.isBlank()) "" else "\n\n"
                    val nt = body.text + sep + t.body
                    setBody(TextFieldValue(nt, TextRange(nt.length)))
                    update { it.copy(title = it.title.ifBlank { t.title }, template = t.key, tags = (it.tags + t.tag).distinct()) }
                    pickTemplate = false
                }.padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Dashboard, null, tint = c.accent); Spacer(Modifier.width(14.dp))
                    Column {
                        Text(t.name, color = c.text, style = MaterialTheme.typography.titleMedium)
                        Text(RichText.plain(t.body).lines().filter { it.isNotBlank() }.take(2).joinToString(" · "), color = c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    if (datePick) {
        val st = rememberDatePickerState(initialSelectedDateMillis = e.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { datePick = false }, confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { ms -> update { it.copy(day = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) } }
                datePick = false
            }) { Text("Tamam") }
        }, dismissButton = { TextButton(onClick = { datePick = false }) { Text("Vazgeç") } }) { DatePicker(st) }
    }
    if (timePick) {
        val (h, m) = e.time.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 12) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        val st = rememberTimePickerState(h, m, true)
        AlertDialog(onDismissRequest = { timePick = false }, confirmButton = {
            TextButton(onClick = { update { it.copy(time = "%02d:%02d".format(st.hour, st.minute)) }; timePick = false }) { Text("Tamam") }
        }, dismissButton = { TextButton(onClick = { timePick = false }) { Text("Vazgeç") } }, text = { TimePicker(st) })
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Bu girdi silinsin mi?") },
        text = { Text("Yazı ve eklenen medya kalıcı olarak silinir.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; saved = true; Store.delete(e.id); Nav.back() }) { Text("Sil", color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Vazgeç") } },
    )
}

@Composable
fun MetaChip(icon: ImageVector, text: String, tint: Color, onClick: () -> Unit) {
    val c = Iz.c
    Row(Modifier.clip(CircleShape).background(c.card).border(1.dp, c.line, CircleShape).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp))
        Text(text, color = c.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
    }
}

@Composable
private fun Tool(icon: ImageVector, desc: String, tint: Color? = null, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, desc, tint = tint ?: Iz.c.sub, modifier = Modifier.size(22.dp)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsSheet(e: Entry, onChange: ((Entry) -> Entry) -> Unit, onLocation: () -> Unit, locBusy: Boolean) {
    val c = Iz.c
    var tagIn by remember { mutableStateOf("") }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("Ruh hâli", style = MaterialTheme.typography.titleMedium, color = c.text)
        Spacer(Modifier.height(10.dp))
        Text("Birden fazla seçebilirsin; ilk seçtiğin ana ruh hâli olur.", color = c.sub, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        MoodMultiPicker(e.moods, compact = true) { l -> onChange { it.copy(mood = l.firstOrNull(), extraMoods = l.drop(1)) } }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enerji", style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            Text(if (e.energy == 0) "Puanlanmadı" else "${e.energy}/10", color = c.sub)
            if (e.energy > 0) TextButton(onClick = { onChange { it.copy(energy = 0) } }) { Text("Temizle") }
        }
        Slider(value = e.energy.toFloat(), onValueChange = { v -> onChange { it.copy(energy = v.toInt()) } }, valueRange = 0f..10f, steps = 9)
        Spacer(Modifier.height(12.dp))
        Text("Etiketler", style = MaterialTheme.typography.titleMedium, color = c.text)
        Text("Yazının içinde #etiket yazarak da ekleyebilirsin.", color = c.sub, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            e.tags.forEach { t -> Pill("#$t ✕", true) { onChange { it.copy(tags = it.tags - t) } } }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(tagIn, { tagIn = it.replace(" ", "") }, placeholder = { Text("yeni etiket") }, singleLine = true, modifier = Modifier.weight(1f), shape = CircleShape)
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = { val t = Text.normTag(tagIn); if (t.isNotEmpty()) onChange { it.copy(tags = (it.tags + t).distinct()) }; tagIn = "" }) { Text("Ekle") }
        }
        val known = remember { Store.entries.flatMap { it.allTags }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.map { it.first }.take(16) }
        val sugg = known.filter { it !in e.allTags }
        if (sugg.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sugg.forEach { t -> Pill("#$t") { onChange { it.copy(tags = (it.tags + t).distinct()) } } }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Konum ve hava durumu", style = MaterialTheme.typography.titleMedium, color = c.text)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(e.place, { v -> onChange { it.copy(place = v) } }, label = { Text("Yer") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundS,
            leadingIcon = { Icon(Icons.Rounded.Place, null) })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(e.weather, { v -> onChange { it.copy(weather = v) } }, label = { Text("Hava") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundS,
            leadingIcon = { Icon(Icons.Rounded.WbSunny, null) })
        Spacer(Modifier.height(8.dp))
        Row {
            FilledTonalButton(onClick = onLocation, enabled = !locBusy) {
                Icon(Icons.Rounded.MyLocation, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (locBusy) "Alınıyor…" else "Şu anki konum")
            }
            Spacer(Modifier.width(8.dp))
            if (e.place.isNotBlank() || e.lat != null) TextButton(onClick = { onChange { it.copy(place = "", lat = null, lon = null, weather = "") } }) { Text("Kaldır") }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Favori anı", color = c.text, modifier = Modifier.weight(1f))
            Switch(e.fav, { v -> onChange { it.copy(fav = v) } })
        }
    }
}

@Composable
fun MediaStrip(media: List<Media>, onRemove: ((Media) -> Unit)? = null, small: Boolean = false) {
    val c = Iz.c
    val ctx = LocalContext.current
    var playing by remember { mutableStateOf<String?>(null) }
    val visual = media.filter { !it.isAudio }
    val audio = media.filter { it.isAudio }
    if (visual.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(visual) { m ->
            Box {
                Thumb(m, Modifier.size(if (small) 64.dp else if (visual.size == 1) 220.dp else 130.dp).clickable { MediaKit.open(ctx, m) })
                if (onRemove != null) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable { onRemove(m) }.padding(4.dp)) {
                    Icon(Icons.Rounded.Close, "Kaldır", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    audio.forEach { m ->
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(c.card).border(1.dp, c.line, CircleShape).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { playing = if (MediaKit.Player.toggle(m) { playing = null }) m.file else null }) {
                Icon(if (playing == m.file) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Çal", tint = c.accent)
            }
            Text("Ses notu", color = c.text, modifier = Modifier.weight(1f))
            Text(MediaKit.fmt(m.dur), color = c.sub, style = MaterialTheme.typography.labelLarge)
            if (onRemove != null) IconButton(onClick = { MediaKit.Player.stop(); onRemove(m) }) { Icon(Icons.Rounded.Close, "Kaldır", tint = c.sub) }
            else Spacer(Modifier.width(14.dp))
        }
    }
}
