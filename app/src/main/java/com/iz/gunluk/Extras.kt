package com.iz.gunluk

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.LocalDate

// ============================ Büyük widget: tek dokunuşla ruh hâli ============================
class IzWidgetLarge : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { update(context) }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MOOD) {
            intent.getStringExtra("mood")?.let { Store.setTodayMood(it) }   // Store.persist widget'ları da yeniler
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val MOOD = "com.iz.gunluk.W_MOOD"
        val keys = listOf("mutlu", "huzurlu", "heyecanli", "yorgun", "uzgun", "ofkeli")
        private val cells = mapOf("mutlu" to R.id.m_mutlu, "huzurlu" to R.id.m_huzurlu, "heyecanli" to R.id.m_heyecanli,
            "yorgun" to R.id.m_yorgun, "uzgun" to R.id.m_uzgun, "ofkeli" to R.id.m_ofkeli)
        private val imgs = mapOf("mutlu" to R.id.mi_mutlu, "huzurlu" to R.id.mi_huzurlu, "heyecanli" to R.id.mi_heyecanli,
            "yorgun" to R.id.mi_yorgun, "uzgun" to R.id.mi_uzgun, "ofkeli" to R.id.mi_ofkeli)

        fun update(ctx: Context) {
            try {
                val m = AppWidgetManager.getInstance(ctx)
                val ids = m.getAppWidgetIds(ComponentName(ctx, IzWidgetLarge::class.java))
                if (ids.isEmpty()) return
                val v = RemoteViews(ctx.packageName, R.layout.widget_large)
                val today = LocalDate.now()
                v.setTextViewText(R.id.w_date, Tr.upper("${today.dayOfMonth} ${Tr.months[today.monthValue - 1]} · ${Tr.days[today.dayOfWeek.value - 1]}"))
                val todays = Store.entries.filter { it.day == today.toEpochDay() }
                val mood = todays.filter { it.mood != null }.maxByOrNull { it.time + it.created }?.mood
                v.setTextViewText(R.id.w_title, Moods[mood]?.let { "Bugün ${it.label.lowercase()}" } ?: "Bugün nasıl geçti?")
                val st = Insights.streak(Store.writtenDays)
                v.setTextViewText(R.id.w_sub, if (st > 0) "Seri: $st gün · bu hafta ${Insights.weekCount(Store.writtenDays)}/${Prefs.goal}" else "Ruh hâlini seç, birkaç satır yaz")
                for (k in keys) {
                    val pi = PendingIntent.getBroadcast(ctx, 200 + keys.indexOf(k), Intent(ctx, IzWidgetLarge::class.java).setAction(MOOD).putExtra("mood", k),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    v.setOnClickPendingIntent(cells[k]!!, pi)
                    v.setInt(imgs[k]!!, "setImageAlpha", if (mood == null || mood == k) 255 else 90)
                }
                v.setOnClickPendingIntent(R.id.w_write, Reminders.open(ctx, "write", 111))
                v.setOnClickPendingIntent(R.id.w_note, PendingIntent.getActivity(ctx, 112, Intent(ctx, QuickNoteActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                v.setOnClickPendingIntent(R.id.w_root, Reminders.open(ctx, "home", 113))
                m.updateAppWidget(ids, v)
            } catch (_: Exception) { }
        }
    }
}

// ============================ Bildirim paneli kısayolu (kilit ekranından da erişilir) ============================
class WriteTile : TileService() {
    override fun onClick() {
        val go = {
            val i = Intent(this, MainActivity::class.java).putExtra("open", "write").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 120, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            else @Suppress("DEPRECATION") startActivityAndCollapse(i)
        }
        if (isLocked) unlockAndRun { go() } else go()
    }
}

// ============================ Hızlı not ============================
/** Widget'tan, kısayoldan açılan küçük pencere: uygulamayı açmadan tek satır not. Kayıtları göstermez, kilit istemez. */
class QuickNoteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IzTheme {
                val c = Iz.c
                var text by remember { mutableStateOf("") }
                val fr = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                Box(Modifier.fillMaxSize().clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { finish() }.imePadding(),
                    contentAlignment = Alignment.BottomCenter) {
                    Column(Modifier.fillMaxWidth().padding(12.dp).clip(Round).background(c.card)
                        .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) { }.padding(18.dp)) {
                        Label("Hızlı not · ${Store.nowTime()}")
                        Spacer(Modifier.height(10.dp))
                        BasicTextField(text, { text = it }, textStyle = writingStyle(), cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).focusRequester(fr),
                            decorationBox = { inner -> if (text.isEmpty()) Text("Aklından geçen…", style = writingStyle().copy(color = c.faint)); inner() })
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Bugünün “Hızlı notlar” girdisine eklenir", color = c.faint, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("Kaydet", color = c.onAccent, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(CircleShape).background(if (text.isBlank()) c.line else c.accent)
                                    .clickable(enabled = text.isNotBlank()) { Store.quickNote(text); finish() }.padding(horizontal = 18.dp, vertical = 10.dp))
                        }
                    }
                }
            }
        }
    }
}

// ============================ Uygulama simgesi ============================
object AppIcons {
    data class Opt(val alias: String, val name: String, val bg: Int, val fg: Int)
    val all = listOf(
        Opt("IconNib", "Kalem ucu", R.drawable.ic_nib_bg, R.drawable.ic_nib_fg),
        Opt("IconBook", "Defter", R.drawable.ic_book_bg, R.drawable.ic_book_fg),
        Opt("IconMoon", "Hilal", R.drawable.ic_moon_bg, R.drawable.ic_moon_fg),
        Opt("IconSteps", "Ayak izi", R.drawable.ic_steps_bg, R.drawable.ic_steps_fg),
        Opt("IconClassic", "Klasik İz", R.drawable.ic_launcher_bg, R.drawable.ic_launcher_fg),
    )

    fun apply(ctx: Context, alias: String) {
        val pm = ctx.packageManager
        // önce yenisini aç, sonra diğerlerini kapat: arada simgesiz kalmasın
        pm.setComponentEnabledSetting(ComponentName(ctx, "${ctx.packageName}.$alias"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (o in all) if (o.alias != alias)
            pm.setComponentEnabledSetting(ComponentName(ctx, "${ctx.packageName}.${o.alias}"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        Prefs.icon = alias; Prefs.save()
    }
}

