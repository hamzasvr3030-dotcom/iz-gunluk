package com.iz.gunluk

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val c = Iz.c
    val ctx = LocalContext.current
    val act = ctx as? FragmentActivity
    val scope = rememberCoroutineScope()
    // "pin" | "pattern" | "decoy" | "hidden" | "verify:<sonraki>"  (sonraki: none, pin, pattern, decoy, decoyOff, hidden, hiddenOff)
    var lockSetup by remember { mutableStateOf<String?>(null) }
    var firstSecret by remember { mutableStateOf<String?>(null) }
    var timePick by remember { mutableStateOf(false) }
    var pdfSheet by remember { mutableStateOf(false) }
    var moodSheet by remember { mutableStateOf(false) }
    var habitSheet by remember { mutableStateOf(false) }
    var passDialog by remember { mutableStateOf(false) }
    var importPass by remember { mutableStateOf<Uri?>(null) }
    var importWrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var upd by remember { mutableStateOf<Updater.Rel?>(null) }
    var updMsg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    fun save() { Prefs.save() }

    // ----- dosya başlatıcıları -----
    val zipOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u ->
        if (u != null) scope.launch { busy = "Yedekleniyor…"; Nav.toast = if (Backup.exportZip(ctx, u, "")) "Tam yedek kaydedildi" else "Yedekleme başarısız"; busy = null }
    }
    val izbOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { u ->
        if (u != null) scope.launch { busy = "Şifreli yedek hazırlanıyor…"; Nav.toast = if (Backup.exportZip(ctx, u, Prefs.backupPass)) "Parolalı yedek kaydedildi" else "Yedekleme başarısız"; busy = null }
    }
    val jsonOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) scope.launch { Nav.toast = if (Backup.exportJson(ctx, u, Store.entries.toList())) "JSON kaydedildi (şifresiz)" else "Kaydedilemedi" }
    }
    val txtOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { u ->
        if (u != null) scope.launch { Nav.toast = if (Backup.exportTxt(ctx, u, Store.entries.filter { !it.hidden }.toList())) "Metin dosyası kaydedildi" else "Kaydedilemedi" }
    }
    fun runImport(u: Uri, pass: String?) {
        scope.launch {
            busy = "İçe aktarılıyor…"
            when (val r = Backup.import(ctx, u, pass)) {
                is Backup.ImportResult.Ok -> { Nav.toast = "${r.n} kayıt içe aktarıldı"; importPass = null }
                Backup.ImportResult.NeedPass -> { importWrong = false; importPass = u }
                Backup.ImportResult.WrongPass -> { importWrong = true; importPass = u }
                Backup.ImportResult.Fail -> { Nav.toast = "Bu dosya okunamadı"; importPass = null }
            }
            busy = null
        }
    }
    val importIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) runImport(u, null) }
    val treeIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            Prefs.autoBackupUri = u.toString(); save()
            scope.launch { Nav.toast = if (Backup.autoBackup(ctx, force = true)) "Otomatik yedek açıldı, ilk yedek alındı" else "Klasöre yazılamadı" }
        }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun launch(f: () -> Unit) { Lock.suppress = true; f() }
    fun needVerify(next: String) { lockSetup = if (Lock.enabled) "verify:$next" else next }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            BackBar("Ayarlar", { Nav.back() })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                if (Store.decoy) {
                    // Sahte günlükte hassas ayarlar gizlenir; sıradan bir günlük gibi görünür.
                    Spacer(Modifier.height(4.dp))
                }
                Group("Profil") {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(Prefs.name, { Prefs.name = it.take(30); save() }, label = { Text("Adın (selamlamada görünür)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundS)
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(Prefs.coverTitle, { Prefs.coverTitle = it.take(40); save() }, label = { Text("Günlüğünün adı (kapakta)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundS)
                    }
                }

                Group("Görünüm") {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tema", color = c.text)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("iz" to "İz · gece lambası", "paper" to "Kâğıt", "light" to "Açık", "dark" to "Koyu", "amoled" to "Siyah", "contrast" to "Yüksek kontrast", "system" to "Sistem").forEach { (k, t) -> Pill(t, Prefs.theme == k) { Prefs.theme = k; save() } }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Günlük kapağı", color = c.text)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Covers.all.forEachIndexed { i, (name, cols) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(width = 54.dp, height = 72.dp).clip(RoundS).background(Brush.linearGradient(cols))
                                        .border(3.dp, if (Prefs.cover == i) c.text else androidx.compose.ui.graphics.Color.Transparent, RoundS)
                                        .clickable { Prefs.cover = i; save() })
                                    Text(name, color = if (Prefs.cover == i) c.text else c.sub, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Vurgu rengi", color = c.text)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Palette.accents.forEachIndexed { i, (_, col) ->
                                Box(Modifier.size(38.dp).clip(CircleShape).background(col).border(3.dp, if (Prefs.accent == i) c.text else col, CircleShape).clickable { Prefs.accent = i; save(); IzWidget.update(ctx) })
                            }
                        }
                        Text(Palette.accents.getOrElse(Prefs.accent) { Palette.accents[0] }.first, color = c.sub, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Yazı tipi", color = c.text)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("crimson" to "Crimson (defter)", "sans" to "Inter", "serif" to "Klasik", "mono" to "Daktilo", "round" to "El yazısı").forEach { (k, t) -> Pill(t, Prefs.font == k) { Prefs.font = k; save() } }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Yazı boyutu · %${(Prefs.fontScale * 100).toInt()}", color = c.text)
                        Slider(Prefs.fontScale, { Prefs.fontScale = it; save() }, valueRange = 0.8f..1.5f, steps = 6)
                        Text("Satır aralığı · ${"%.1f".format(Prefs.lineHeight)}", color = c.text)
                        Slider(Prefs.lineHeight, { Prefs.lineHeight = it; save() }, valueRange = 1.2f..2.0f, steps = 7)
                        Box(Modifier.fillMaxWidth().clip(RoundS).background(c.card2).ruled(Prefs.ruled, c.line, 17f * Prefs.fontScale * Prefs.lineHeight, 14f * ctx.resources.displayMetrics.density).padding(14.dp)) {
                            Text("Bazen en güzel günler, sıradan olduğunu düşündüğümüz günlerdir. Bugün de öyle bir gündü.", style = writingStyle())
                        }
                    }
                    SettingRow("Çizgili kâğıt", "Yazarken satır çizgileri", Icons.Rounded.Notes, onClick = { Prefs.ruled = !Prefs.ruled; save() }) {
                        Switch(Prefs.ruled, { Prefs.ruled = it; save() })
                    }
                    Column(Modifier.padding(16.dp)) {
                        Text("Uygulama simgesi", color = c.text)
                        Text("Değiştirince ana ekrandaki kısayol kaldırılabilir; uygulama çekmecesinden yeniden ekleyebilirsin.", color = c.sub, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIcons.all.forEach { o ->
                                val on = Prefs.icon == o.alias
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundS).clickable {
                                    if (!on) { AppIcons.apply(ctx, o.alias); Nav.toast = "Simge değişti: ${o.name}" }
                                }.padding(4.dp)) {
                                    Box(Modifier.size(58.dp).clip(CircleShape).border(3.dp, if (on) c.accent else androidx.compose.ui.graphics.Color.Transparent, CircleShape)) {
                                        Image(painterResource(o.bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        Image(painterResource(o.fg), null, Modifier.fillMaxSize().scale(1.45f), contentScale = ContentScale.Fit)
                                    }
                                    Text(o.name, color = if (on) c.text else c.sub, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }

                Group("Ana sayfa") {
                    SettingRow("Günün sözü", "Her gün değişen bir söz; paylaşabilirsin", Icons.Rounded.FormatQuote, onClick = { Prefs.showSaying = !Prefs.showSaying; save() }) {
                        Switch(Prefs.showSaying, { Prefs.showSaying = it; save() })
                    }
                    SettingRow("Günün sorusu", "Her gün farklı bir yazma sorusu", Icons.Rounded.Lightbulb, onClick = { Prefs.showQuestion = !Prefs.showQuestion; save() }) {
                        Switch(Prefs.showQuestion, { Prefs.showQuestion = it; save() })
                    }
                    SettingRow("Günlük burç yorumu", "${Signs[Prefs.sign].name} · internetten günde bir kez çekilir", Icons.Rounded.AutoAwesome, onClick = { Prefs.showHoroscope = !Prefs.showHoroscope; save() }) {
                        Switch(Prefs.showHoroscope, { Prefs.showHoroscope = it; save() })
                    }
                    if (Prefs.showHoroscope) FlowRow(Modifier.padding(start = 66.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Signs.all.forEach { s -> Pill(s.name, Prefs.sign == s.key) { Prefs.sign = s.key; save() } }
                    }
                    SettingRow("Alışkanlık takibi", "Uyku, su, spor, okuma… ana sayfada", Icons.Rounded.TaskAlt, onClick = { Prefs.showHabits = !Prefs.showHabits; save() }) {
                        Switch(Prefs.showHabits, { Prefs.showHabits = it; save() })
                    }
                    SettingRow("Alışkanlıkları düzenle", Habits.defs.joinToString(", ") { it.name }.ifEmpty { "Hiç yok" }, Icons.Rounded.Tune, onClick = { habitSheet = true })
                }

                Group("Yazma") {
                    SettingRow("Açılışta doğrudan yazma ekranı", "Uygulamayı açınca boş sayfa gelsin", Icons.Rounded.EditNote, onClick = { Prefs.openToEditor = !Prefs.openToEditor; save() }) {
                        Switch(Prefs.openToEditor, { Prefs.openToEditor = it; save() })
                    }
                    SettingRow("Yazım düzeltme", "Klavyenin otomatik düzeltme ve önerileri", Icons.Rounded.Spellcheck, onClick = { Prefs.autoCorrect = !Prefs.autoCorrect; save() }) {
                        Switch(Prefs.autoCorrect, { Prefs.autoCorrect = it; save() })
                    }
                    SettingRow("Cümle başında büyük harf", null, Icons.Rounded.TextFields, onClick = { Prefs.autoCap = !Prefs.autoCap; save() }) {
                        Switch(Prefs.autoCap, { Prefs.autoCap = it; save() })
                    }
                    SettingRow("Konum ve havayı otomatik ekle", "Yeni girdide (izin verildiyse)", Icons.Rounded.WbSunny, onClick = { Prefs.weatherAuto = !Prefs.weatherAuto; save() }) {
                        Switch(Prefs.weatherAuto, { Prefs.weatherAuto = it; save() })
                    }
                    SettingRow("Gece yazma modu", "22:00'den sonra sıcak renk, düşük kontrast, geniş satır aralığı", Icons.Rounded.Bedtime, onClick = { Prefs.nightAuto = !Prefs.nightAuto; save() }) {
                        Switch(Prefs.nightAuto, { Prefs.nightAuto = it; save() })
                    }
                    SettingRow("Günün fotoğrafları önerisi", "Akşam, o gün çektiklerinden seçip ekle", Icons.Rounded.PhotoLibrary, onClick = { Prefs.suggestPhotos = !Prefs.suggestPhotos; save() }) {
                        Switch(Prefs.suggestPhotos, { Prefs.suggestPhotos = it; save() })
                    }
                    SettingRow("Kadraj'dan günün şarkısı", "Yeni girdiye o gün en çok dinlenen şarkı eklenir", Icons.Rounded.MusicNote, onClick = { Prefs.kadrajSong = !Prefs.kadrajSong; save() }) {
                        Switch(Prefs.kadrajSong, { Prefs.kadrajSong = it; save() })
                    }
                    SettingRow("Ruh hâllerim", if (Moods.custom.isEmpty()) "Kendi ruh hâlini ekle" else Moods.custom.joinToString(", ") { it.label }, Icons.Rounded.Mood, onClick = { moodSheet = true })
                }

                if (!Store.decoy) Group("Gizlilik ve güvenlik") {
                    SettingRow("Uygulama kilidi", when (Prefs.lockType) { "pin" -> "${Lock.mainLen()} haneli PIN"; "pattern" -> "Desen"; else -> "Kapalı" }, Icons.Rounded.Lock)
                    Row(Modifier.padding(start = 66.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Kapalı", Prefs.lockType == "none") { if (Lock.enabled) lockSetup = "verify:none" }
                        Pill("6 haneli PIN", Prefs.lockType == "pin") { needVerify("pin") }
                        Pill("Desen", Prefs.lockType == "pattern") { needVerify("pattern") }
                    }
                    if (Prefs.lockType == "pin" && Lock.mainLen() != Lock.PIN_LEN) Text("Eski ${Lock.mainLen()} haneli PIN'in çalışmaya devam eder. 6 haneliye geçmek için “6 haneli PIN”e dokun.",
                        color = c.sub, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 66.dp, end = 12.dp, bottom = 12.dp))
                    if (Lock.enabled) {
                        val bioOk = act != null && Lock.canBiometric(act)
                        SettingRow("Parmak izi ile aç", if (bioOk) "Açılışta parmak izi sorulur; PIN her zaman çalışır" else "Bu cihazda kayıtlı parmak izi yok", Icons.Rounded.Fingerprint) {
                            Switch(Prefs.biometric && bioOk, { v -> if (v && act != null) Lock.biometric(act, "Parmak izini doğrula") { Prefs.biometric = true; save() } else { Prefs.biometric = false; save() } }, enabled = bioOk)
                        }
                        SettingRow("Kilitlenme süresi", "Uygulamadan çıktıktan sonra", Icons.Rounded.Timer)
                        Row(Modifier.padding(start = 66.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Hemen", 30 to "30 sn", 60 to "1 dk", 300 to "5 dk").forEach { (s, t) -> Pill(t, Prefs.lockDelay == s) { Prefs.lockDelay = s; save() } }
                        }
                    }
                    if (Prefs.lockType == "pin") {
                        SettingRow("Sahte PIN", if (Lock.decoySet) "Açık · bu PIN boş, ayrı bir günlük açar" else "Biri seni PIN'i söylemeye zorlarsa: ayrı, boş bir günlük açar",
                            Icons.Rounded.TheaterComedy, onClick = { needVerify(if (Lock.decoySet) "decoyOff" else "decoy") }) {
                            if (Lock.decoySet) TextButton(onClick = { needVerify("decoyOff") }) { Text("Kaldır") }
                        }
                    }
                    SettingRow("Gizli kayıtlar için ayrı PIN", if (Lock.hiddenPinSet) "Açık · gizli günlükler yalnızca bu PIN ile açılır" else "Gizli günlükleri ana kilitten farklı bir PIN ile koru",
                        Icons.Rounded.VisibilityOff, onClick = { needVerify(if (Lock.hiddenPinSet) "hiddenOff" else "hidden") }) {
                        if (Lock.hiddenPinSet) TextButton(onClick = { needVerify("hiddenOff") }) { Text("Kaldır") }
                    }
                    if (Lock.hiddenPinSet && act != null && Lock.canBiometric(act)) SettingRow("Gizli kayıtlarda parmak izi", null, Icons.Rounded.Fingerprint, onClick = { Prefs.hiddenBio = !Prefs.hiddenBio; save() }) {
                        Switch(Prefs.hiddenBio, { Prefs.hiddenBio = it; save() })
                    }
                    SettingRow("Ekran görüntüsü koruması", "Ekran görüntüsünü ve son uygulamalar önizlemesini engeller", Icons.Rounded.Screenshot, onClick = { Prefs.secure = !Prefs.secure; save() }) {
                        Switch(Prefs.secure, { Prefs.secure = it; save() })
                    }
                    SettingRow("Veriler şifreli", "Yazıların, mektupların ve alışkanlıkların cihazda AES-256 ile şifrelenir; anahtar telefonun güvenli donanımında durur. Fotoğraf/ses dosyaları şifrelenmez.", Icons.Rounded.EnhancedEncryption)
                    SettingRow("Veriler cihazında kalır", "Hesap yok, bulut yok. Burç yorumu için yalnızca burç adı istenir; konum yalnız sen istediğinde kullanılır.", Icons.Rounded.Shield)
                }

                Group("Hatırlatıcılar ve hedef") {
                    SettingRow("Nazik hatırlatıcı", if (Prefs.remindOn) "%02d:%02d · o gün yazmadıysan".format(Prefs.remindHour, Prefs.remindMin) else "Kapalı", Icons.Rounded.NotificationsActive,
                        onClick = { timePick = true }) {
                        Switch(Prefs.remindOn, { v ->
                            Prefs.remindOn = v; save(); Reminders.schedule(ctx)
                            if (v && Build.VERSION.SDK_INT >= 33) { notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        })
                    }
                    if (Prefs.remindOn) Row(Modifier.padding(start = 66.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Tr.daysShort.forEachIndexed { i, d ->
                            val on = (Prefs.remindDays shr i) and 1 == 1
                            Box(Modifier.size(34.dp).clip(CircleShape).background(if (on) c.accent else c.card2).clickable { Prefs.remindDays = Prefs.remindDays xor (1 shl i); save() }, contentAlignment = Alignment.Center) {
                                Text(d, color = if (on) c.onAccent else c.sub, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    SettingRow("Bildirimden hızlı ruh hâli", if (Prefs.moodNotif) "%02d:30 · 5 emoji, uygulamayı açmadan".format(Prefs.moodHour) else "Kapalı", Icons.Rounded.EmojiEmotions,
                        onClick = { Prefs.moodHour = listOf(19, 20, 21, 22).let { l -> l[(l.indexOf(Prefs.moodHour) + 1) % l.size] }; save(); QuickMood.schedule(ctx) }) {
                        Switch(Prefs.moodNotif, { Prefs.moodNotif = it; save(); QuickMood.schedule(ctx); if (it && Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) })
                    }
                    SettingRow("Geçmişte bugün bildirimi", "“Tam 1 yıl önce bugün…”", Icons.Rounded.History, onClick = { Prefs.memoryNotif = !Prefs.memoryNotif; save() }) {
                        Switch(Prefs.memoryNotif, { Prefs.memoryNotif = it; save() })
                    }
                    SettingRow("Haftalık yazma hedefi", "${Prefs.goal} gün", Icons.Rounded.Flag)
                    Row(Modifier.padding(start = 66.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 7).forEach { g -> Pill("$g gün", Prefs.goal == g) { Prefs.goal = g; save(); IzWidget.update(ctx) } }
                    }
                }

                if (!Store.decoy) Group("Yedekleme ve dışa aktarma") {
                    val hasPass = Prefs.backupPass.isNotEmpty()
                    SettingRow("Yedek parolası", if (hasPass) "Açık · yedekler parolayla şifrelenir (.izb)" else "Kapalı · yedeklerin şifresiz ZIP olur", Icons.Rounded.Password, onClick = { passDialog = true }) {
                        if (hasPass) TextButton(onClick = { Prefs.backupPass = ""; Nav.toast = "Yedek parolası kaldırıldı" }) { Text("Kaldır") }
                    }
                    if (hasPass) SettingRow("Parolalı tam yedek al", "Yazılar, mektuplar, alışkanlıklar, fotoğraf ve sesler", Icons.Rounded.Backup, onClick = { launch { izbOut.launch("Iz-yedek-${Backup.stamp()}.izb") } })
                    SettingRow(if (hasPass) "Şifresiz tam yedek (ZIP)" else "Tam yedek al (ZIP)", "Yazılar, mektuplar, alışkanlıklar, fotoğraf ve sesler", Icons.Rounded.Archive, onClick = { launch { zipOut.launch("Iz-yedek-${Backup.stamp()}.zip") } })
                    SettingRow("Yedekten geri yükle", "IZB, ZIP veya JSON · mevcut kayıtlarla birleştirilir", Icons.Rounded.Restore, onClick = { launch { importIn.launch(arrayOf("*/*")) } })
                    SettingRow("Otomatik yedek", if (Prefs.autoBackupUri.isEmpty()) "Kapalı · bir klasör seç (ör. Google Drive klasörü)"
                        else "Açık${if (hasPass) " · parolalı" else ""} · son: " + (if (Prefs.lastAutoBackup > 0) java.time.Instant.ofEpochMilli(Prefs.lastAutoBackup).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let { Tr.long(it) } else "henüz yok"),
                        Icons.Rounded.CloudSync, onClick = { launch { treeIn.launch(null) } }) {
                        if (Prefs.autoBackupUri.isNotEmpty()) TextButton(onClick = { Prefs.autoBackupUri = ""; save() }) { Text("Kapat") }
                    }
                    SettingRow("JSON olarak dışa aktar", "Şifresiz, yalnızca metin", Icons.Rounded.DataObject, onClick = { launch { jsonOut.launch("Iz-${Backup.stamp()}.json") } })
                    SettingRow("Metin (TXT) olarak dışa aktar", "Gizli kayıtlar hariç", Icons.Rounded.Description, onClick = { launch { txtOut.launch("Iz-${Backup.stamp()}.txt") } })
                    SettingRow("Kitap gibi PDF", "Seçtiğin dönemi kitap düzeninde PDF yap", Icons.Rounded.MenuBook, onClick = { pdfSheet = true })
                }

                Group("Çöp kutusu") {
                    val n = Trash.list().size
                    SettingRow("Silinenler", if (n == 0) "Boş · silinen girdiler 30 gün burada kalır" else "$n girdi · 30 gün sonra kalıcı olarak silinir", Icons.Rounded.DeleteOutline, onClick = { Nav.go(Route.Trash) })
                }

                Group("Uygulama") {
                    SettingRow("Sürüm ${Updater.current(ctx)}", updMsg ?: "Güncellemeler uygulama içinden kurulur", Icons.Rounded.SystemUpdate, onClick = {
                        checking = true; updMsg = "Denetleniyor…"
                        scope.launch {
                            val r = Updater.check(); checking = false
                            updMsg = when {
                                r == null -> Updater.lastError ?: "Denetlenemedi"
                                Updater.newer(r.version, Updater.current(ctx)) -> { upd = r; "Yeni sürüm: ${r.version}" }
                                else -> "En güncel sürümü kullanıyorsun"
                            }
                        }
                    }) { if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                    SettingRow("Proje sayfası", "github.com/${Updater.REPO}", Icons.Rounded.Code, onClick = {
                        launch { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${Updater.REPO}"))) }
                    })
                    SettingRow("Kısayollar", "Simgeye basılı tut: Yeni girdi, Hızlı not, Sesli günlük. Bildirim paneline “İz'e yaz” kutucuğunu, ana ekrana “Ruh hâli” widget'ını ekleyebilirsin.", Icons.Rounded.Widgets)
                }
                Text("İz · hayatında bıraktığın izler", color = c.faint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        // ----- kilit kurulumu -----
        lockSetup?.let { st ->
            val close = { lockSetup = null; firstSecret = null }
            when {
                st.startsWith("verify:") -> AuthScreen("Mevcut kilidi doğrula", "Değişiklik için önce doğrula", mode = AuthMode.Verify, onCancel = close) {
                    when (val next = st.removePrefix("verify:")) {
                        "none" -> { Lock.clear(); close(); Nav.toast = "Kilit kapatıldı" }
                        "decoyOff" -> { Lock.clearDecoy(); close(); Nav.toast = "Sahte PIN kaldırıldı" }
                        "hiddenOff" -> { Lock.clearHiddenPin(); close(); Nav.toast = "Gizli kayıt PIN'i kaldırıldı" }
                        else -> lockSetup = next
                    }
                }
                st == "pin" -> PinSetup("6 haneli PIN belirle", "Uygulamayı açarken sorulacak", onCancel = close) { p ->
                    Lock.set("pin", p); close()
                    if (act != null && Lock.canBiometric(act) && !Prefs.biometric) Lock.biometric(act, "Parmak izini de ekle") { Prefs.biometric = true; Prefs.save(); Nav.toast = "PIN ve parmak izi açık" }
                    Nav.toast = "PIN ayarlandı"
                }
                st == "decoy" -> PinSetup("Sahte PIN belirle", "Gerçek PIN'inden farklı olmalı. Girildiğinde boş, ayrı bir günlük açılır.", onCancel = close) { p ->
                    if (Lock.check(p)) { Nav.toast = "Gerçek PIN ile aynı olamaz" } else { Lock.setDecoy(p); Nav.toast = "Sahte PIN ayarlandı" }
                    close()
                }
                st == "hidden" -> PinSetup("Gizli kayıt PIN'i", "Gizli günlükleri açmak için sorulacak", onCancel = close) { p ->
                    Lock.setHiddenPin(p); Lock.hiddenOpen = false; close(); Nav.toast = "Gizli kayıtlar artık bu PIN ile açılır"
                }
                else -> Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(if (firstSecret == null) "Yeni desen çiz" else "Tekrar çiz", style = MaterialTheme.typography.headlineSmall, color = c.text)
                    Spacer(Modifier.height(6.dp))
                    Text("En az 4 noktayı birleştir", color = c.sub)
                    Spacer(Modifier.height(28.dp))
                    key(firstSecret) {
                        PatternPad { s ->
                            if (firstSecret == null) firstSecret = s
                            else if (firstSecret == s) { Lock.set("pattern", s); Lock.clearDecoy(); close(); Nav.toast = "Desen ayarlandı" }
                            else { firstSecret = null; Nav.toast = "Eşleşmedi, baştan dene" }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = close) { Text("Vazgeç") }
                }
            }
        }
        busy?.let { b ->
            Box(Modifier.fillMaxSize().background(c.bg.copy(alpha = 0.7f)).clickable(enabled = true) { }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(b, color = c.text) }
            }
        }
    }

    if (timePick) {
        val st = rememberTimePickerState(Prefs.remindHour, Prefs.remindMin, true)
        AlertDialog(onDismissRequest = { timePick = false }, title = { Text("Hatırlatma saati") }, text = { TimePicker(st) },
            confirmButton = { TextButton(onClick = {
                Prefs.remindHour = st.hour; Prefs.remindMin = st.minute; Prefs.remindOn = true; save(); Reminders.schedule(ctx); timePick = false
                if (Build.VERSION.SDK_INT >= 33) { notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }) { Text("Tamam") } },
            dismissButton = { TextButton(onClick = { timePick = false }) { Text("Vazgeç") } })
    }
    if (passDialog) PasswordDialog("Yedek parolası", "En az 6 karakter. Parolayı unutursan bu yedekler açılamaz; bir yere not et.", confirm = true,
        onDismiss = { passDialog = false }) { p -> Prefs.backupPass = p; passDialog = false; Nav.toast = "Yedekler artık parolalı" }
    importPass?.let { u ->
        PasswordDialog("Yedek parolalı", if (importWrong) "Parola yanlış, tekrar dene" else "Bu yedeği açmak için parolasını gir", confirm = false,
            onDismiss = { importPass = null }) { p -> importPass = null; runImport(u, p) }
    }
    if (pdfSheet) ModalBottomSheet(onDismissRequest = { pdfSheet = false }, containerColor = c.card) { PdfSheet { pdfSheet = false } }
    if (moodSheet) ModalBottomSheet(onDismissRequest = { moodSheet = false }, containerColor = c.card) { MoodEditor() }
    if (habitSheet) ModalBottomSheet(onDismissRequest = { habitSheet = false }, containerColor = c.card) { HabitEditor() }
    upd?.let { UpdateDialog(it) { upd = null } }
}


@Composable
fun PasswordDialog(title: String, sub: String, confirm: Boolean, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    val c = Iz.c
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val ok = a.length >= (if (confirm) 6 else 1) && (!confirm || a == b)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                Text(sub, color = c.sub, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(a, { a = it }, label = { Text("Parola") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), shape = RoundS)
                if (confirm) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(b, { b = it }, label = { Text("Parola (tekrar)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), shape = RoundS,
                        isError = b.isNotEmpty() && a != b)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(a) }, enabled = ok) { Text("Tamam") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } })
}

/** Kendi ruh hâllerini ekleme / silme. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodEditor() {
    val c = Iz.c
    var label by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(0) }
    var icon by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(3) }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
        Text("Ruh hâllerim", style = MaterialTheme.typography.titleLarge, color = c.text)
        Text("Hazır listede olmayan duyguları ekle. Grafiklerde puanına göre yer alır.", color = c.sub, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Moods.custom.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                MoodBadge(m, 32.dp); Spacer(Modifier.width(10.dp))
                Text(m.label, color = c.text, modifier = Modifier.weight(1f))
                Text("puan ${m.score}", color = c.faint, style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { Moods.custom.remove(m); Prefs.save() }) { Icon(Icons.Rounded.Close, "Sil", tint = c.sub) }
            }
        }
        if (Moods.custom.isNotEmpty()) HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 10.dp))
        Text("Yeni ruh hâli", color = c.text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(label, { label = it.take(16) }, label = { Text("Adı (ör. Özlem)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundS)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Moods.colorPool.forEachIndexed { i, col ->
                Box(Modifier.size(30.dp).clip(CircleShape).background(col).border(3.dp, if (color == i) c.text else col, CircleShape).clickable { color = i })
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Moods.iconPool.forEachIndexed { i, (_, ic) ->
                Box(Modifier.size(38.dp).clip(CircleShape).background(if (icon == i) Moods.colorPool[color].copy(alpha = 0.25f) else c.card2).clickable { icon = i }, contentAlignment = Alignment.Center) {
                    Icon(ic, null, tint = if (icon == i) Moods.colorPool[color] else c.sub, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Nasıl bir his? · ${listOf("", "çok kötü", "kötü", "orta", "iyi", "çok iyi")[score]}", color = c.text)
        Slider(score.toFloat(), { score = it.toInt() }, valueRange = 1f..5f, steps = 3)
        Button(onClick = {
            val (ik, ic) = Moods.iconPool[icon]
            Moods.custom += Mood("c_" + System.currentTimeMillis().toString(36), label.trim(), Moods.colorPool[color], ic, score, true, ik)
            Prefs.save(); label = ""
        }, enabled = label.isNotBlank() && Moods.all.none { it.label.equals(label.trim(), true) }, shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("Ekle", fontWeight = FontWeight.Bold) }
    }
}

/** Alışkanlık tanımları. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitEditor() {
    val c = Iz.c
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("check") }
    var target by remember { mutableStateOf("8") }
    var unit by remember { mutableStateOf("") }
    var icon by remember { mutableIntStateOf(0) }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
        Text("Alışkanlıklar", style = MaterialTheme.typography.titleLarge, color = c.text)
        Text("Ana sayfadan her gün işaretle; istatistiklerde ilerlemeni gör. Silmek geçmiş kayıtları silmez.", color = c.sub, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Habits.defs.forEach { d ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Habits.icon(d.icon), null, tint = c.accent); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, color = c.text)
                    Text(when (d.kind) { "check" -> "Yaptım / yapmadım"; "hours" -> "Hedef ${fmtD(d.target)} ${d.unit.ifBlank { "saat" }}"; else -> "Hedef ${fmtD(d.target)} ${d.unit}" },
                        color = c.faint, style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = { Habits.moveDef(d.key, true) }) { Icon(Icons.Rounded.KeyboardArrowUp, "Yukarı", tint = c.sub) }
                IconButton(onClick = { Habits.removeDef(d.key) }) { Icon(Icons.Rounded.Close, "Sil", tint = c.sub) }
            }
        }
        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 10.dp))
        Text("Yeni alışkanlık", color = c.text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(name, { name = it.take(20) }, label = { Text("Adı (ör. Meditasyon)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundS)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Yaptım/yapmadım", kind == "check") { kind = "check" }
            Pill("Sayı", kind == "count") { kind = "count" }
            Pill("Saat", kind == "hours") { kind = "hours"; if (unit.isBlank()) unit = "saat" }
        }
        if (kind != "check") {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(target, { target = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, label = { Text("Günlük hedef") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), shape = RoundS)
                OutlinedTextField(unit, { unit = it.take(12) }, label = { Text("Birim") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundS)
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Habits.icons.forEachIndexed { i, (_, ic) ->
                Box(Modifier.size(38.dp).clip(CircleShape).background(if (icon == i) c.accentSoft else c.card2).clickable { icon = i }, contentAlignment = Alignment.Center) {
                    Icon(ic, null, tint = if (icon == i) c.accent else c.sub, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = {
            Habits.addDef(name, kind, target.toDoubleOrNull() ?: 1.0, Habits.icons[icon].first, unit); name = ""
        }, enabled = name.isNotBlank(), shape = CircleShape, modifier = Modifier.fillMaxWidth()) { Text("Ekle", fontWeight = FontWeight.Bold) }
    }
}

/** Çöp kutusu: silinen girdiler 30 gün saklanır. */
@Composable
fun TrashScreen() {
    val c = Iz.c
    var tick by remember { mutableIntStateOf(0) }
    val items = remember(tick, Store.version, Store.decoy) { Trash.list() }
    var confirmAll by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        BackBar("Çöp kutusu", { Nav.back() }) {
            if (items.isNotEmpty()) TextButton(onClick = { confirmAll = true }) { Text("Boşalt", color = c.danger) }
        }
        if (items.isEmpty()) EmptyState(Icons.Rounded.DeleteOutline, "Çöp kutusu boş", "Sildiğin girdiler 30 gün boyunca burada durur, istersen geri alırsın.")
        else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.second.id }) { (at, e) ->
                val left = 30 - ((System.currentTimeMillis() - at) / 86_400_000L).toInt()
                IzCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(Tr.longDay(e.date), color = c.faint, style = MaterialTheme.typography.labelMedium)
                        Text(e.title.ifBlank { e.plain.take(60).ifBlank { "Başlıksız" } }, color = c.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (e.plain.isNotBlank()) Text(e.plain.take(140), color = c.sub, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$left gün sonra silinecek", color = c.faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { Trash.purge(e.id); tick++ }) { Text("Kalıcı sil", color = c.danger) }
                            TextButton(onClick = { Store.restore(e); tick++; Nav.toast = "Geri alındı" }) { Text("Geri al") }
                        }
                    }
                }
            }
        }
    }
    if (confirmAll) AlertDialog(onDismissRequest = { confirmAll = false }, title = { Text("Çöp kutusu boşaltılsın mı?") },
        text = { Text("${items.size} girdi ve fotoğraf/sesleri kalıcı olarak silinir. Geri alınamaz.") },
        confirmButton = { TextButton(onClick = { Trash.empty(); tick++; confirmAll = false }) { Text("Boşalt", color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Vazgeç") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfSheet(close: () -> Unit) {
    val c = Iz.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var choice by remember { mutableIntStateOf(1) }
    var photos by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val ranges = listOf(
        "Bu ay" to (today.withDayOfMonth(1) to today),
        "Bu yıl" to (today.withDayOfYear(1) to today),
        "Geçen yıl" to (today.minusYears(1).withDayOfYear(1) to today.minusYears(1).withMonth(12).withDayOfMonth(31)),
        "Tümü" to (LocalDate.of(1970, 1, 1) to today.plusYears(10)),
    )
    val (from, to) = ranges[choice].second
    val list = Store.entries.filter { !it.hidden && !it.isEmpty && it.day in from.toEpochDay()..to.toEpochDay() }
    val out = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { u ->
        if (u != null) scope.launch {
            busy = true
            val title = if (choice == 3) Prefs.coverTitle.ifBlank { "${Prefs.name.ifBlank { "Benim" }} günlüğüm" } else "${ranges[choice].first.replace("Bu ", "")} · ${from.year}"
            Nav.toast = if (Backup.exportPdf(ctx, u, list, title, photos)) "PDF hazır" else "PDF oluşturulamadı"
            busy = false; close()
        }
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("Kitap gibi PDF", style = MaterialTheme.typography.titleLarge, color = c.text)
        Text("Kapak, ay bölümleri ve sayfa numaralarıyla A5 kitap düzeni. Gizli kayıtlar eklenmez.", color = c.sub, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ranges.forEachIndexed { i, (t, _) -> Pill(t, choice == i) { choice = i } } }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Fotoğrafları ekle", color = c.text, modifier = Modifier.weight(1f)); Switch(photos, { photos = it })
        }
        Text("${list.size} kayıt", color = c.sub)
        Spacer(Modifier.height(14.dp))
        Button(onClick = { Lock.suppress = true; out.launch("Iz-kitap-${Backup.stamp()}.pdf") }, enabled = list.isNotEmpty() && !busy, shape = CircleShape, modifier = Modifier.fillMaxWidth()) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.onAccent) else Text("PDF oluştur", fontWeight = FontWeight.Bold)
        }
    }
}

fun fmtD(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(v)
