package com.iz.gunluk

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/** Ortak köşe ölçeği: çip 8 · kart 14 · büyük kart 18 · alt sayfa 26. */
val Round = RoundedCornerShape(18.dp)
val RoundS = RoundedCornerShape(14.dp)
val RoundChip = RoundedCornerShape(8.dp)
val RoundSheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

@Composable
fun IzCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, pad: Dp = 18.dp, color: Color = Iz.c.card, content: @Composable ColumnScope.() -> Unit) {
    val c = Iz.c
    Column(
        modifier.fillMaxWidth().clip(Round).background(color)
            .border(BorderStroke(1.dp, c.line), Round)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .animateContentSize().padding(pad),
        content = content,
    )
}

@Composable
fun Label(text: String, color: Color = Iz.c.accent, modifier: Modifier = Modifier) =
    Text(Tr.upper(text), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Iz.c.text, modifier = Modifier.weight(1f))
        if (action != null) Text(action, color = Iz.c.accent, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clip(CircleShape).clickable { onAction?.invoke() }.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
fun Pill(text: String, selected: Boolean = false, icon: ImageVector? = null, color: Color = Iz.c.accent, onClick: () -> Unit) {
    val c = Iz.c
    Row(
        Modifier.clip(CircleShape).background(if (selected) color.copy(alpha = 0.18f) else c.card2)
            .border(1.dp, if (selected) color.copy(alpha = 0.6f) else Color.Transparent, CircleShape)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (selected) color else c.sub, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = if (selected) color else c.text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun MoodBadge(m: Mood, size: Dp = 36.dp, selected: Boolean = true) {
    Box(Modifier.size(size).clip(CircleShape).background(m.color.copy(alpha = if (selected) 0.22f else 0.08f)), contentAlignment = Alignment.Center) {
        Icon(m.icon, m.label, tint = if (selected) m.color else m.color.copy(alpha = 0.5f), modifier = Modifier.size(size * 0.56f))
    }
}

/** Tek seçim (ana ruh hâli) için kısayol. */
@Composable
fun MoodPicker(selected: String?, compact: Boolean = false, onPick: (String?) -> Unit) =
    MoodMultiPicker(listOfNotNull(selected), compact, multi = false) { onPick(it.firstOrNull()) }

/** Çoklu seçim: ilk seçilen ana ruh hâlidir ("ana" rozeti), diğerleri ek duygulardır. */
@Composable
fun MoodMultiPicker(selected: List<String>, compact: Boolean = false, multi: Boolean = true, onChange: (List<String>) -> Unit) {
    val c = Iz.c
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (m in Moods.all) {
            val idx = selected.indexOf(m.key)
            val on = idx >= 0
            Box {
                Column(
                    Modifier.width(if (compact) 58.dp else 64.dp).clip(RoundS)
                        .background(if (on) m.color.copy(alpha = 0.16f) else Color.Transparent)
                        .border(1.dp, if (on) m.color.copy(alpha = 0.7f) else c.line, RoundS)
                        .clickable {
                            onChange(when {
                                on -> selected - m.key
                                multi -> selected + m.key
                                else -> listOf(m.key)
                            })
                        }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(m.icon, null, tint = if (on || selected.isEmpty()) m.color else m.color.copy(alpha = 0.45f), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(m.label, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp), color = if (on) c.text else c.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (multi && idx == 0 && selected.size > 1) Text("ana", color = c.onAccent, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).clip(CircleShape).background(m.color).padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }
    }
}

/** Çizgili kâğıt: yazı satırlarının altına ince çizgiler. */
fun Modifier.ruled(enabled: Boolean, color: Color, lineHeightSp: Float, topPx: Float = 0f): Modifier = if (!enabled) this else this.drawBehind {
    val lh = lineHeightSp * density * fontScale
    var y = topPx + lh - lh * 0.18f
    while (y < size.height) { drawLine(color, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1.2f); y += lh }
}

@Composable
fun BackBar(title: String = "", onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri", tint = Iz.c.text) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = Iz.c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun Thumb(m: Media, modifier: Modifier = Modifier) {
    val c = Iz.c
    Box(modifier.clip(RoundS).background(c.card2), contentAlignment = Alignment.Center) {
        when {
            m.isAudio -> Icon(Icons.Rounded.Mic, null, tint = c.accent)
            else -> AsyncImage(model = Store.media(m.file), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (m.isVideo) Box(Modifier.align(Alignment.BottomStart).padding(6.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Videocam, null, tint = Color.White, modifier = Modifier.size(12.dp))
                if (m.dur > 0) { Spacer(Modifier.width(3.dp)); Text(MediaKit.fmt(m.dur), color = Color.White, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

/** Liste kartı: ruh hâli şeridi, başlık, özet, ilk fotoğraf, etiketler. */
@Composable
fun EntryCard(e: Entry, showDate: Boolean = true, query: String = "", onClick: () -> Unit) {
    val c = Iz.c
    val mood = Moods[e.mood]
    if (e.hidden && !Lock.hiddenOpen) {
        IzCard(onClick = onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = c.sub); Spacer(Modifier.width(10.dp))
                Text("Gizli kayıt · ${Tr.short(e.date)}", color = c.sub)
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().clip(Round).background(c.card).border(1.dp, c.line, Round).clickable { onClick() },
    ) {
        Box(Modifier.width(5.dp).heightIn(min = 86.dp).background(mood?.color ?: c.line))
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Tr.upper((if (showDate) Tr.short(e.date) + " · " + Tr.days[e.date.dayOfWeek.value - 1] else Tr.days[e.date.dayOfWeek.value - 1]) + if (e.time.isNotEmpty()) " · ${e.time}" else ""),
                    style = MaterialTheme.typography.labelSmall, color = c.sub, modifier = Modifier.weight(1f), maxLines = 1,
                )
                if (e.hidden) Icon(Icons.Rounded.Lock, null, tint = c.sub, modifier = Modifier.size(14.dp))
                if (e.fav) Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFFF5C8A), modifier = Modifier.size(14.dp).padding(start = 2.dp))
                if (mood != null) { Spacer(Modifier.width(6.dp)); MoodBadge(mood, 24.dp) }
                e.extraMoods.mapNotNull { Moods[it] }.take(2).forEach { Spacer(Modifier.width(2.dp)); MoodBadge(it, 20.dp) }
            }
            Spacer(Modifier.height(6.dp))
            val title = e.title.ifBlank { mood?.let { "${it.label} bir gün" } ?: "Başlıksız" }
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val ex = e.plain.replace('\n', ' ').trim()
            if (ex.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                val shown = if (query.isNotBlank()) Text.findAll(ex, query).firstOrNull()?.let { p -> (if (p > 40) "…" else "") + ex.substring(maxOf(0, p - 40)) } ?: ex else ex
                Text(RichText.render(shown, c.accent, c.sub, c.highlight, query), style = MaterialTheme.typography.bodyMedium, color = c.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val meta = listOfNotNull(
                e.place.takeIf { it.isNotBlank() },
                e.media.count { it.isAudio }.takeIf { it > 0 }?.let { "$it ses" },
                e.media.count { it.isVideo }.takeIf { it > 0 }?.let { "$it video" },
                "${e.words} kelime".takeIf { e.words > 0 },
                e.people.take(2).joinToString(" ") { "@$it" }.takeIf { it.isNotEmpty() },
            )
            if (e.allTags.isNotEmpty() || meta.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (e.place.isNotBlank()) { Icon(Icons.Rounded.Place, null, tint = c.faint, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(2.dp)) }
                    Text((e.allTags.take(3).map { "#$it" } + meta).joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = c.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        e.photos.firstOrNull()?.let { Thumb(it, Modifier.padding(10.dp).size(78.dp)) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, sub: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = Iz.c
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = c.accent, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text(sub, color = c.sub, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); Button(onClick = { onAction?.invoke() }, shape = CircleShape) { Text(action) } }
    }
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = Iz.c.text) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Iz.c.sub, maxLines = 2)
    }
}

@Composable
fun SettingRow(title: String, sub: String? = null, icon: ImageVector? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    val c = Iz.c
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) { Icon(icon, null, tint = c.accent, modifier = Modifier.size(19.dp)) }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, color = c.sub, style = MaterialTheme.typography.bodyMedium)
        }
        trailing?.invoke()
    }
}

@Composable
fun Group(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Iz.c
    if (title != null) Label(title, c.sub, Modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().clip(Round).background(c.card).border(1.dp, c.line, Round), content = content)
}
