package com.iz.gunluk

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File

/** Instagram hikâyesi oranında (1080×1920) anı / söz kartı üretip paylaşır. */
object ShareCard {
    fun render(quote: String, footer: String, sub: String, c1: Color, c2: Color): Bitmap {
        val w = 1080; val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), c1.toArgb(), c2.toArgb(), Shader.TileMode.CLAMP) }
        cv.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        // yumuşak daireler
        val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; alpha = 22 }
        cv.drawCircle(w * 0.85f, h * 0.12f, 260f, soft); cv.drawCircle(w * 0.1f, h * 0.9f, 340f, soft)
        // kart
        val card = RectF(80f, 420f, w - 80f, h - 420f)
        cv.drawRoundRect(card, 56f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; alpha = 70 })
        val qm = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; alpha = 140; textSize = 200f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
        cv.drawText("“", card.left + 50f, card.top + 210f, qm)
        val size = when { quote.length < 90 -> 66f; quote.length < 180 -> 54f; quote.length < 300 -> 44f; else -> 38f }
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = size; typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL) }
        val txt = if (quote.length > 420) quote.take(417).trimEnd() + "…" else quote
        val tw = (card.width() - 140).toInt()
        val sl = StaticLayout.Builder.obtain(txt, 0, txt.length, tp, tw).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.25f).build()
        val top = card.top + 240f + ((card.height() - 420f) - sl.height).coerceAtLeast(0f) / 2
        cv.save(); cv.translate(card.left + 70f, top); sl.draw(cv); cv.restore()
        val fp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 38f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); letterSpacing = 0.08f }
        cv.drawText(footer, card.left + 70f, card.bottom - 110f, fp)
        val sp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; alpha = 190; textSize = 34f }
        cv.drawText(sub, card.left + 70f, card.bottom - 60f, sp)
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; alpha = 200; textSize = 40f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER }
        cv.drawText("İz · hayatında bıraktığın izler", w / 2f, h - 180f, brand)
        return bmp
    }

    fun share(ctx: Context, bmp: Bitmap) {
        val f = File(ctx.cacheDir, "share/iz-kart.png"); f.parentFile?.mkdirs()
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = MediaKit.uri(ctx, f)
        Lock.suppress = true
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Kartı paylaş"))
    }

    /** Bir girdiden en anlamlı cümleyi seçip kart yapar. */
    fun forEntry(ctx: Context, e: Entry, text: String? = null) {
        val m = Moods[e.mood]
        val q = text ?: Insights.highlights(listOf(e), Text.wordList(e.plain).toSet(), 1).firstOrNull() ?: e.plain.take(300)
        val c1 = m?.color ?: Palette.accents.getOrElse(Prefs.accent) { Palette.accents[0] }.second
        share(ctx, render(q.ifBlank { e.title }, Tr.upper(Tr.longDay(e.date)), listOfNotNull(e.title.takeIf { it.isNotBlank() }, m?.label, e.place.takeIf { it.isNotBlank() }).joinToString(" · "),
            c1, Color(0xFF0A0E17)))
    }

    fun forSaying(ctx: Context, s: Saying) {
        val cs = Covers.colors(Prefs.cover)
        share(ctx, render(s.text, Tr.upper("Günün sözü"), "— ${s.by}", cs[0], cs[1]))
    }
}
