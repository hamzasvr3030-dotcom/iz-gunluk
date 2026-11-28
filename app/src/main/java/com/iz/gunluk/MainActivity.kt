package com.iz.gunluk

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface Route {
    data object Tabs : Route
    data class Editor(val id: String? = null, val day: Long? = null, val template: String? = null, val question: String? = null, val draft: String? = null, val draftMood: Int = 0, val draftSource: String = "") : Route
    data object Entries : Route
    data class Person(val name: String) : Route
    data object Places : Route
    data class PlaceR(val name: String) : Route
    data class Year(val year: Int) : Route
    data object Weekly : Route
    data class Versions(val id: String) : Route
    data class Reader(val id: String) : Route
    data class Day(val day: Long) : Route
    data object Settings : Route
    data class LetterEdit(val id: String? = null) : Route
    data class LetterRead(val id: String) : Route
    data class Summary(val from: Long, val to: Long, val title: String) : Route
    data object Map : Route
    data object Trash : Route
}

object Nav {
    val stack = mutableStateListOf<Route>(Route.Tabs)
    var tab by mutableIntStateOf(0)
    var memTab by mutableIntStateOf(6)
    var listQuery by mutableStateOf("")
    var listTag by mutableStateOf<String?>(null)
    var listMood by mutableStateOf<String?>(null)
    var listPerson by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)
    val top get() = stack.last()
    fun go(r: Route) { stack.add(r) }
    fun back(): Boolean { if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }; if (tab != 0) { tab = 0; return true }; return false }
    fun replace(r: Route) { stack.removeAt(stack.lastIndex); stack.add(r) }
    fun home() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun write(day: Long? = null, question: String? = null) {
        // bugüne ait boş olmayan bir taslak/yazı varsa yeni girdi yine açılır: günlük birden çok girdiye izin verir
        go(Route.Editor(day = day, question = question))
    }
}

class MainActivity : FragmentActivity() {
    private var update by mutableStateOf<Updater.Rel?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            Lock.locked = Lock.enabled
            if (Prefs.openToEditor) Nav.go(Route.Editor())
        }
        handle(intent)
        applySecure()
        if (Build.VERSION.SDK_INT >= 33 && Prefs.remindOn) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        lifecycleScope.launch {
            if (System.currentTimeMillis() - Prefs.long("lastUpdCheck") > 6 * 3600_000L) {
                Prefs.putLong("lastUpdCheck", System.currentTimeMillis())
                Updater.check()?.let { if (Updater.newer(it.version, Updater.current(this@MainActivity)) && Prefs.str("skipVer") != it.version) update = it }
            }
        }
        setContent {
            IzTheme {
                LaunchedEffect(Prefs.secure) { applySecure() }
                val dark = Iz.c.dark
                LaunchedEffect(dark) {
                    WindowCompat.getInsetsController(window, window.decorView).run { isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark }
                }
                LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); Night.tick++ } }
                Root(update) { update = null }
            }
        }
    }

    fun applySecure() {
        if (Prefs.secure) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    private fun handle(i: Intent?) {
        if (i?.action == "com.iz.action.DRAFT") {
            val t = i.getStringExtra("text").orEmpty()
            Nav.home(); Nav.go(Route.Editor(draft = t, draftMood = i.getIntExtra("mood", 0), draftSource = i.getStringExtra("source").orEmpty()))
            i.action = null; return
        }
        when (i?.getStringExtra("open") ?: return) {
            "write" -> { Nav.home(); Nav.go(Route.Editor()) }
            "voice" -> { Nav.home(); Nav.go(Route.Editor(template = "_voice")) }
            "memories" -> { Nav.home(); Nav.tab = 2; Nav.memTab = 4 }
            "letters" -> { Nav.home(); Nav.tab = 2; Nav.memTab = 2 }
            "year" -> { Nav.home(); Nav.go(Route.Year(LocalDate.now().year)) }
            "weekly" -> { Nav.home(); Nav.go(Route.Weekly) }
            "home" -> { Nav.home(); Nav.tab = 0 }
        }
        i.removeExtra("open")
    }

    override fun onStart() { super.onStart(); Lock.onStart(); IzWidget.update(this) }
    override fun onResume() { super.onResume(); Lock.onResume() }
    override fun onStop() {
        super.onStop(); Lock.onStop(); MediaKit.Player.stop()
        lifecycleScope.launch { Backup.autoBackup(applicationContext) }
    }
}

@Composable
fun Root(update: Updater.Rel?, dismissUpdate: () -> Unit) {
    val c = Iz.c
    Box(Modifier.fillMaxSize().background(c.bg)) {
        if (Lock.locked) {
            AuthScreen { Lock.locked = false }
            return@Box
        }
        BackHandler(enabled = Nav.stack.size > 1 || Nav.tab != 0) { Nav.back() }
        AnimatedContent(
            targetState = Nav.top,
            transitionSpec = {
                val forward = Nav.stack.size > 1 && targetState == Nav.top && targetState !is Route.Tabs
                if (forward) (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(220))) togetherWith fadeOut(tween(160))
                else (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 8 }) togetherWith (fadeOut(tween(160)) + slideOutHorizontally(tween(260)) { it / 5 })
            },
            label = "nav",
        ) { r ->
            when (r) {
                Route.Tabs -> Tabs()
                is Route.Editor -> EditorScreen(r)
                is Route.Reader -> ReaderScreen(r.id)
                is Route.Day -> DayScreen(r.day)
                Route.Settings -> SettingsScreen()
                is Route.LetterEdit -> LetterEditScreen(r.id)
                is Route.LetterRead -> LetterReadScreen(r.id)
                is Route.Summary -> SummaryScreen(r)
                Route.Map -> MapScreen()
                Route.Trash -> TrashScreen()
                Route.Entries -> Column(Modifier.fillMaxSize().background(Iz.c.bg)) { BackBar("Tüm girdiler", { Nav.back() }); Box(Modifier.weight(1f)) { ListScreen() } }
                is Route.Person -> PersonScreen(r.name)
                Route.Places -> PlacesScreen()
                is Route.PlaceR -> PlaceScreen(r.name)
                is Route.Year -> YearScreen(r.year)
                Route.Weekly -> WeeklyScreen()
                is Route.Versions -> VersionsScreen(r.id)
            }
        }
        update?.let { UpdateDialog(it, dismissUpdate) }
        LockOffer()
        Nav.toast?.let { msg ->
            LaunchedEffect(msg) { kotlinx.coroutines.delay(2200); Nav.toast = null }
            Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
                Text(msg, color = c.text, modifier = Modifier.clip(CircleShape).background(c.card2).padding(horizontal = 18.dp, vertical = 10.dp))
            }
        }
    }
}

private data class TabItem(val label: String, val icon: ImageVector, val iconOn: ImageVector)

@Composable
fun Tabs() {
    val c = Iz.c
    val items = listOf(
        TabItem("Bugün", Icons.Rounded.EditNote, Icons.Rounded.EditNote),
        TabItem("Takvim", Icons.Rounded.CalendarMonth, Icons.Rounded.CalendarMonth),
        TabItem("Anılar", Icons.Rounded.AutoAwesome, Icons.Rounded.AutoAwesome),
        TabItem("İçgörü", Icons.Rounded.Insights, Icons.Rounded.Insights),
        TabItem("Ben", Icons.Rounded.Person, Icons.Rounded.Person),
    )
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(Nav.tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                when (t) {
                    0 -> HomeScreen()
                    1 -> CalendarScreen()
                    2 -> MemoriesScreen()
                    3 -> InsightScreen()
                    else -> MeScreen()
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.card).navigationBarsPadding().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, it ->
                val on = Nav.tab == i
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { Nav.tab = i },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.clip(CircleShape).background(if (on) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = 16.dp, vertical = 4.dp)
                    ) { Icon(if (on) it.iconOn else it.icon, it.label, tint = if (on) c.accent else c.sub, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.height(2.dp))
                    Text(it.label, style = MaterialTheme.typography.labelMedium, color = if (on) c.text else c.sub, maxLines = 1, overflow = TextOverflow.Clip)
                }
            }
        }
    }
}

@Composable
fun UpdateDialog(r: Updater.Rel, dismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var prog by remember { mutableStateOf<Float?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (prog == null) dismiss() },
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text("Yeni sürüm: ${r.version}") },
        text = {
            Column {
                Text(r.notes.ifBlank { "Yenilikler ve iyileştirmeler." }, maxLines = 12, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text("Yazıların ve anıların güncellemede korunur.", color = Iz.c.sub, style = MaterialTheme.typography.bodyMedium)
                prog?.let { Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth()) }
                err?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Iz.c.danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = prog == null || prog == 1f, onClick = {
                scope.launch {
                    err = null; prog = 0f
                    val f = Updater.download(ctx, r) { p -> prog = p }
                    if (f == null) { err = Updater.lastError; prog = null } else { if (!Updater.install(ctx, f)) err = "Bilinmeyen kaynaklardan yükleme iznini verip tekrar dokun." ; prog = 1f }
                }
            }) { Text(if (prog == 1f) "Kur" else "Güncelle") }
        },
        dismissButton = {
            TextButton(onClick = { Prefs.putStr("skipVer", r.version); dismiss() }) { Text("Sonra") }
        },
    )
}

fun todayKey() = LocalDate.now().toEpochDay()

/** İlk açılışta bir kez: günlüğü 6 haneli PIN ve parmak iziyle kilitlemeyi öner. */
@Composable
fun LockOffer() {
    val c = Iz.c
    val act = LocalContext.current as? FragmentActivity
    var setup by remember { mutableStateOf(false) }
    if (Prefs.lockAsked || Lock.enabled || Store.decoy) { if (!Prefs.lockAsked && Lock.enabled) { Prefs.lockAsked = true; Prefs.save() }; return }
    fun done() { Prefs.lockAsked = true; Prefs.save() }
    if (setup) {
        PinSetup("6 haneli PIN belirle", "Uygulamayı açarken sorulacak", onCancel = { setup = false; done() }) { p ->
            Lock.set("pin", p); done(); setup = false
            if (act != null && Lock.canBiometric(act)) Lock.biometric(act, "Parmak izini de ekle") { Prefs.biometric = true; Prefs.save(); Nav.toast = "PIN ve parmak izi açık" }
            Nav.toast = "Günlüğün kilitlendi"
        }
        return
    }
    AlertDialog(onDismissRequest = { done() }, icon = { Icon(Icons.Rounded.Lock, null, tint = c.accent) },
        title = { Text("Günlüğünü kilitle") },
        text = { Text("İz'i 6 haneli bir PIN ve parmak iziyle koruyabilirsin. Yazıların zaten cihazda şifreli tutuluyor; kilit, telefonunu başkası açtığında da güvende olmalarını sağlar.") },
        confirmButton = { TextButton(onClick = { setup = true }) { Text("PIN oluştur") } },
        dismissButton = { TextButton(onClick = { done() }) { Text("Şimdi değil") } })
}
