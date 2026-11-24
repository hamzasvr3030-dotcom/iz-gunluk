package com.iz.gunluk

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val c = Iz.c
    val ctx = LocalContext.current
    val act = ctx as? FragmentActivity
    val scope = rememberCoroutineScope()
    var lockSetup by remember { mutableStateOf<String?>(null) }   // "pin" | "pattern" | "verify:<sonraki>"
    var firstSecret by remember { mutableStateOf<String?>(null) }
    var timePick by remember { mutableStateOf(false) }
    var pdfSheet by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var upd by remember { mutableStateOf<Updater.Rel?>(null) }
    var updMsg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    fun save() { Prefs.save() }

    // ----- dosya başlatıcıları -----
    val zipOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u ->
        if (u != null) scope.launch { busy = "Yedekleniyor…"; Nav.toast = if (Backup.exportZip(ctx, u)) "Tam yedek kaydedildi" else "Yedekleme başarısız"; busy = null }
    }
    val jsonOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) scope.launch { Nav.toast = if (Backup.exportJson(ctx, u, Store.entries.toList())) "JSON kaydedildi" else "Kaydedilemedi" }
    }
    val txtOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { u ->
        if (u != null) scope.launch { Nav.toast = if (Backup.exportTxt(ctx, u, Store.entries.filter { !it.hidden }.toList())) "Metin dosyası kaydedildi" else "Kaydedilemedi" }
    }
    val importIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) scope.launch {
            busy = "İçe aktarılıyor…"
            val n = Backup.import(ctx, u)
            Nav.toast = if (n == null) "Bu dosya okunamadı" else "$n kayıt içe aktarıldı"; busy = null
        }
    }
    val treeIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
        if (u != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            Prefs.autoBackupUri = u.toString(); save()
            scope.launch { Nav.toast = if (Backup.autoBackup(ctx, force = true)) "Otomatik yedek açıldı, ilk yedek alındı" else "Klasöre yazılamadı" }
        }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun launch(f: () -> Unit) { Lock.suppress = true; f() }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            BackBar("Ayarlar", { Nav.back() })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                Group("Profil") {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedTextField(Prefs.name, { Prefs.name = it.take(30); save() }, label = { Text("Adın (selamlamada görünür)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundS)
                    }
                }

                Group("Görünüm") {
                    Column(Modifier.padding(16.dp)) {
                        Text("Tema", color = c.text)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("light" to "Açık", "dark" to "Koyu", "amoled" to "Siyah", "system" to "Sistem").forEach { (k, t) -> Pill(t, Prefs.theme == k) { Prefs.theme = k; save() } }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("sans" to "Modern", "serif" to "Klasik", "mono" to "Daktilo", "round" to "El yazısı").forEach { (k, t) -> Pill(t, Prefs.font == k) { Prefs.font = k; save() } }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Yazı boyutu · %${(Prefs.fontScale * 100).toInt()}", color = c.text)
                        Slider(Prefs.fontScale, { Prefs.fontScale = it; save() }, valueRange = 0.8f..1.5f, steps = 6)
                        Text("Satır aralığı · ${"%.1f".format(Prefs.lineHeight)}", color = c.text)
                        Slider(Prefs.lineHeight, { Prefs.lineHeight = it; save() }, valueRange = 1.2f..2.0f, steps = 7)
                        Box(Modifier.fillMaxWidth().clip(RoundS).background(c.card2).padding(14.dp)) {
                            Text("Bazen en güzel günler, sıradan olduğunu düşündüğümüz günlerdir. Bugün de öyle bir gündü.", style = writingStyle())
                        }
                    }
                }

                Group("Yazma") {
                    SettingRow("Açılışta doğrudan yazma ekranı", "Uygulamayı açınca boş sayfa gelsin", Icons.Rounded.EditNote, onClick = { Prefs.openToEditor = !Prefs.openToEditor; save() }) {
                        Switch(Prefs.openToEditor, { Prefs.openToEditor = it; save() })
                    }
                    SettingRow("Günün sorusu", "Ana sayfada her gün farklı bir soru", Icons.Rounded.Lightbulb, onClick = { Prefs.showQuestion = !Prefs.showQuestion; save() }) {
                        Switch(Prefs.showQuestion, { Prefs.showQuestion = it; save() })
                    }
                    SettingRow("Konum ve havayı otomatik ekle", "Yeni girdide (izin verildiyse)", Icons.Rounded.WbSunny, onClick = { Prefs.weatherAuto = !Prefs.weatherAuto; save() }) {
                        Switch(Prefs.weatherAuto, { Prefs.weatherAuto = it; save() })
                    }
                }

                Group("Gizlilik ve güvenlik") {
                    SettingRow("Uygulama kilidi", when (Prefs.lockType) { "pin" -> "PIN"; "pattern" -> "Desen"; else -> "Kapalı" }, Icons.Rounded.Lock)
                    Row(Modifier.padding(start = 66.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Kapalı", Prefs.lockType == "none") { if (Lock.enabled) lockSetup = "verify:none" }
                        Pill("PIN", Prefs.lockType == "pin") { lockSetup = if (Lock.enabled) "verify:pin" else "pin" }
                        Pill("Desen", Prefs.lockType == "pattern") { lockSetup = if (Lock.enabled) "verify:pattern" else "pattern" }
                    }
                    if (Lock.enabled) {
                        val bioOk = act != null && Lock.canBiometric(act)
                        SettingRow("Parmak izi / yüz tanıma", if (bioOk) "Kilidi biyometrik doğrulamayla aç" else "Bu cihazda kayıtlı biyometrik yok", Icons.Rounded.Fingerprint) {
                            Switch(Prefs.biometric && bioOk, { v -> if (v && act != null) Lock.biometric(act, "Biyometrik kilidi aç") { Prefs.biometric = true; save() } else { Prefs.biometric = false; save() } }, enabled = bioOk)
                        }
                        SettingRow("Kilitlenme süresi", "Uygulamadan çıktıktan sonra", Icons.Rounded.Timer)
                        Row(Modifier.padding(start = 66.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Hemen", 30 to "30 sn", 60 to "1 dk", 300 to "5 dk").forEach { (s, t) -> Pill(t, Prefs.lockDelay == s) { Prefs.lockDelay = s; save() } }
                        }
                    }
                    SettingRow("Ekran görüntüsü koruması", "Ekran görüntüsünü ve son uygulamalar önizlemesini engeller", Icons.Rounded.Screenshot, onClick = { Prefs.secure = !Prefs.secure; save() }) {
                        Switch(Prefs.secure, { Prefs.secure = it; save() })
                    }
                    SettingRow("Gizli günlükler", "Yazarken ⋮ menüsünden 'Gizli günlük yap'. Gizli kayıtlar akışta görünmez, açmak için doğrulama ister.", Icons.Rounded.VisibilityOff)
                    SettingRow("Veriler cihazında kalır", "Hesap yok, bulut yok. Yalnızca sen istediğinde yedek alınır; hava durumu için konum yalnız sen istediğinde gönderilir.", Icons.Rounded.Shield)
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
                    SettingRow("Geçmişte bugün bildirimi", "“Tam 1 yıl önce bugün…”", Icons.Rounded.History, onClick = { Prefs.memoryNotif = !Prefs.memoryNotif; save() }) {
                        Switch(Prefs.memoryNotif, { Prefs.memoryNotif = it; save() })
                    }
                    SettingRow("Haftalık yazma hedefi", "${Prefs.goal} gün", Icons.Rounded.Flag)
                    Row(Modifier.padding(start = 66.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 7).forEach { g -> Pill("$g gün", Prefs.goal == g) { Prefs.goal = g; save(); IzWidget.update(ctx) } }
                    }
                }

                Group("Yedekleme ve dışa aktarma") {
                    SettingRow("Tam yedek al (ZIP)", "Yazılar, mektuplar, fotoğraf ve sesler", Icons.Rounded.Backup, onClick = { launch { zipOut.launch("Iz-yedek-${Backup.stamp()}.zip") } })
                    SettingRow("Yedekten geri yükle", "ZIP veya JSON · mevcut kayıtlarla birleştirilir", Icons.Rounded.Restore, onClick = { launch { importIn.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*")) } })
                    SettingRow("Otomatik yedek", if (Prefs.autoBackupUri.isEmpty()) "Kapalı · bir klasör seç (ör. Google Drive klasörü)"
                        else "Açık · son: " + (if (Prefs.lastAutoBackup > 0) java.time.Instant.ofEpochMilli(Prefs.lastAutoBackup).atZone(java.time.ZoneId.systemDefault()).toLocalDate().let { Tr.long(it) } else "henüz yok"),
                        Icons.Rounded.CloudSync, onClick = { launch { treeIn.launch(null) } }) {
                        if (Prefs.autoBackupUri.isNotEmpty()) TextButton(onClick = { Prefs.autoBackupUri = ""; save() }) { Text("Kapat") }
                    }
                    SettingRow("JSON olarak dışa aktar", null, Icons.Rounded.DataObject, onClick = { launch { jsonOut.launch("Iz-${Backup.stamp()}.json") } })
                    SettingRow("Metin (TXT) olarak dışa aktar", "Gizli kayıtlar hariç", Icons.Rounded.Description, onClick = { launch { txtOut.launch("Iz-${Backup.stamp()}.txt") } })
                    SettingRow("Kitap gibi PDF", "Seçtiğin dönemi kitap düzeninde PDF yap", Icons.Rounded.MenuBook, onClick = { pdfSheet = true })
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
                }
                Text("İz · hayatında bıraktığın izler", color = c.faint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }

        // ----- kilit kurulumu -----
        lockSetup?.let { st ->
            when {
                st.startsWith("verify:") -> AuthScreen("Mevcut kilidi doğrula", "Değişiklik için önce doğrula", onCancel = { lockSetup = null }) {
                    val next = st.removePrefix("verify:")
                    if (next == "none") { Lock.clear(); lockSetup = null; Nav.toast = "Kilit kapatıldı" } else lockSetup = next
                }
                else -> Column(Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(if (firstSecret == null) (if (st == "pin") "Yeni PIN belirle" else "Yeni desen çiz") else "Tekrar gir", style = MaterialTheme.typography.headlineSmall, color = c.text)
                    Spacer(Modifier.height(6.dp))
                    Text(if (st == "pin") "4–8 haneli bir PIN seç, sonra Tamam'a dokun" else "En az 4 noktayı birleştir", color = c.sub)
                    Spacer(Modifier.height(28.dp))
                    val done: (String) -> Unit = { s ->
                        if (firstSecret == null) firstSecret = s
                        else if (firstSecret == s) { Lock.set(st, s); firstSecret = null; lockSetup = null; Nav.toast = "Kilit ayarlandı" }
                        else { firstSecret = null; Nav.toast = "Eşleşmedi, baştan dene" }
                    }
                    key(firstSecret) {
                        if (st == "pin") PinPad(flexible = true) { p, reset -> done(p); reset() } else PatternPad(done)
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { lockSetup = null; firstSecret = null }) { Text("Vazgeç") }
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
    if (pdfSheet) ModalBottomSheet(onDismissRequest = { pdfSheet = false }, containerColor = c.card) { PdfSheet { pdfSheet = false } }
    upd?.let { UpdateDialog(it) { upd = null } }
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
            val title = if (choice == 3) "${Prefs.name.ifBlank { "Benim" }} günlüğüm" else "${ranges[choice].first.replace("Bu ", "")} · ${from.year}"
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
