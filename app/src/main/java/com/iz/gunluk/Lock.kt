package com.iz.gunluk

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom

object Lock {
    /** Uygulama kilitli mi (kilit ekranı gösterilsin mi). */
    var locked by mutableStateOf(false)
    /** Gizli kayıtlar bu oturumda açıldı mı. */
    var hiddenOpen by mutableStateOf(false)
    /** Fotoğraf seçici gibi kendi açtığımız ekranlardan dönüşte kilitlenmesin. */
    @Volatile var suppress = false
    var stoppedAt = 0L

    val enabled get() = Prefs.lockType != "none"

    private fun hash(secret: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + ":" + secret).toByteArray()).joinToString("") { "%02x".format(it) }

    fun set(type: String, secret: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        Prefs.putStr("lockSalt", salt); Prefs.putStr("lockHash", hash(secret, salt)); Prefs.putStr("pinLen", secret.length.toString())
        Prefs.lockType = type; Prefs.save()
    }

    fun clear() { Prefs.putStr("lockHash", ""); Prefs.lockType = "none"; Prefs.biometric = false; Prefs.save() }

    fun check(secret: String) = Prefs.str("lockHash").isNotEmpty() && hash(secret, Prefs.str("lockSalt")) == Prefs.str("lockHash")

    fun canBiometric(a: FragmentActivity) =
        BiometricManager.from(a).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun biometric(a: FragmentActivity, title: String, ok: () -> Unit) {
        val p = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { ok() }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle("İz · kişisel günlük")
            .setNegativeButtonText(if (Prefs.lockType == "none") "Vazgeç" else "Şifre kullan")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build()
        try { p.authenticate(info) } catch (_: Exception) { }
    }

    private var skipNext = false
    /** Kendi açtığımız seçici/ekran yüzünden durduysak bir sonraki dönüşte kilitleme. */
    fun onStop() { if (suppress) { suppress = false; skipNext = true } else stoppedAt = System.currentTimeMillis() }
    fun onStart() {
        if (skipNext) { skipNext = false; return }
        if (enabled && stoppedAt > 0 && System.currentTimeMillis() - stoppedAt >= Prefs.lockDelay * 1000L) { locked = true; hiddenOpen = false }
    }
    /** İzin penceresi gibi durdurmayan ekranlarda bayrak açık kalmasın. */
    fun onResume() { suppress = false }
}

/**
 * Kimlik doğrulama ekranı: PIN tuş takımı veya desen + biyometrik.
 * [mode] "unlock" = uygulamayı aç, "verify" = gizli kayıtlar / ayar değişikliği için doğrula.
 */
@Composable
fun AuthScreen(title: String = "İz kilitli", sub: String = "Devam etmek için doğrula", onCancel: (() -> Unit)? = null, onOk: () -> Unit) {
    val c = Iz.c
    val ctx = LocalContext.current
    val act = ctx as? FragmentActivity
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var err by remember { mutableStateOf("") }
    fun fail() { err = "Hatalı, tekrar dene"; scope.launch { for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x) } }
    LaunchedEffect(Unit) { if (Prefs.biometric && act != null && Lock.canBiometric(act)) Lock.biometric(act, title, onOk) }
    Column(
        Modifier.fillMaxSize().background(c.bg).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Lock, null, tint = c.accent, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text(if (err.isNotEmpty()) err else sub, color = if (err.isNotEmpty()) c.danger else c.sub)
        Spacer(Modifier.height(28.dp))
        Box(Modifier.graphicsLayer { translationX = shake.value }) {
            if (Prefs.lockType == "pattern") PatternPad { if (Lock.check(it)) onOk() else fail() }
            else PinPad(length = Prefs.str("pinLen").toIntOrNull() ?: 4) { pin, reset -> if (Lock.check(pin)) onOk() else { fail(); reset() } }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (Prefs.biometric && act != null && Lock.canBiometric(act)) {
                Row(Modifier.clip(CircleShape).clickable { Lock.biometric(act, title, onOk) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Fingerprint, null, tint = c.accent); Spacer(Modifier.width(6.dp)); Text("Parmak izi / yüz", color = c.accent)
                }
            }
            if (onCancel != null) Text("Vazgeç", color = c.sub, modifier = Modifier.clip(CircleShape).clickable { onCancel() }.padding(12.dp))
        }
    }
}

@Composable
fun PinPad(length: Int = 4, flexible: Boolean = false, onDone: (String, () -> Unit) -> Unit) {
    val c = Iz.c
    var pin by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(if (flexible) maxOf(4, pin.length) else length) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) c.accent else c.line))
            }
        }
        Spacer(Modifier.height(28.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (flexible) "OK" else "", "0", "<")
        for (row in keys.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                for (k in row) {
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(if (k.isEmpty()) c.bg else c.card)
                            .clickable(enabled = k.isNotEmpty()) {
                                when (k) {
                                    "<" -> pin = pin.dropLast(1)
                                    "OK" -> if (pin.length >= 4) { val p = pin; onDone(p) { pin = "" } }
                                    else -> if (pin.length < 8) {
                                        pin += k
                                        if (!flexible && pin.length == length) { val p = pin; onDone(p) { pin = "" } }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "<" -> Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = c.sub)
                            "OK" -> Text("Tamam", color = c.accent, fontWeight = FontWeight.Bold)
                            else -> Text(k, fontSize = 26.sp, color = c.text)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}

/** 3×3 desen kilidi: noktalara sürükleyerek çiz; sonuç "0-1-2-5" gibi. */
@Composable
fun PatternPad(onDone: (String) -> Unit) {
    val c = Iz.c
    val path = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var sizePx by remember { mutableFloatStateOf(0f) }
    fun center(i: Int): Offset { val cell = sizePx / 3f; return Offset(cell * (i % 3) + cell / 2, cell * (i / 3) + cell / 2) }
    fun hit(p: Offset): Int? {
        if (sizePx == 0f) return null
        for (i in 0..8) if ((center(i) - p).getDistance() < sizePx / 9f) return i
        return null
    }
    Canvas(
        Modifier.size(270.dp).pointerInput(Unit) {
            sizePx = size.width.toFloat()
            detectDragGestures(
                onDragStart = { p -> path.clear(); hit(p)?.let { path += it }; finger = p },
                onDrag = { ch, _ -> finger = ch.position; hit(ch.position)?.let { if (it !in path) path += it } },
                onDragEnd = {
                    finger = null
                    if (path.size >= 4) onDone(path.joinToString("-"))
                    path.clear()
                },
                onDragCancel = { finger = null; path.clear() },
            )
        }
    ) {
        sizePx = size.width
        for (k in 1 until path.size) drawLine(c.accent, center(path[k - 1]), center(path[k]), 8f, StrokeCap.Round)
        if (path.isNotEmpty()) finger?.let { drawLine(c.accent.copy(alpha = 0.5f), center(path.last()), it, 8f, StrokeCap.Round) }
        for (i in 0..8) {
            val on = i in path
            drawCircle(if (on) c.accent.copy(alpha = 0.25f) else c.line, radius = size.width / 11f, center = center(i))
            drawCircle(if (on) c.accent else c.sub, radius = size.width / 40f, center = center(i))
        }
    }
}
