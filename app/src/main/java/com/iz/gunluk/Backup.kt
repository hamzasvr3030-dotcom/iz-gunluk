package com.iz.gunluk

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Backup {
    fun stamp(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private fun json(entries: List<Entry>, letters: List<Letter>) = JSONObject()
        .put("app", "iz").put("format", 2).put("exported", System.currentTimeMillis())
        .put("entries", Codec.entriesToJson(entries)).put("letters", Codec.lettersToJson(letters))
        .put("habits", Habits.toJson()).put("customMoods", JSONArray(Moods.customJson()))

    sealed interface ImportResult {
        data class Ok(val n: Int) : ImportResult
        data object NeedPass : ImportResult
        data object WrongPass : ImportResult
        data object Fail : ImportResult
    }

    private fun size(ctx: Context, uri: Uri): Long =
        runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")!!.use { it.statSize } }.getOrDefault(-1L)

    /** Parola verilmişse ZIP'i şifreleyerek yazar (.izb). */
    private fun writeBackup(out: OutputStream, pass: String) {
        if (pass.isEmpty()) writeZip(out) else writeZip(BackupCrypto.encrypting(out, pass))
    }

    /** Tam yedek: veriler + tüm medya dosyaları tek ZIP içinde. */
    fun writeZip(out: OutputStream) {
        Store.flush()
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry("iz.json"))
            z.write(json(Store.entries.toList(), Store.letters.toList()).toString(2).toByteArray()); z.closeEntry()
            val used = Store.entries.flatMap { it.media.map { m -> m.file } }.toSet()
            for (f in Store.mediaDir.listFiles().orEmpty()) if (f.name in used) {
                z.putNextEntry(ZipEntry("media/" + f.name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry()
            }
        }
    }

    suspend fun exportZip(ctx: Context, uri: Uri, pass: String = Prefs.backupPass): Boolean = withContext(Dispatchers.IO) {
        runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { writeBackup(it, pass) } }.isSuccess
    }

    suspend fun exportJson(ctx: Context, uri: Uri, list: List<Entry>): Boolean = withContext(Dispatchers.IO) {
        runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json(list, Store.letters.toList()).toString(2).toByteArray()) } }.isSuccess
    }

    fun text(list: List<Entry>): String = buildString {
        for (e in list.sortedBy { it.day * 10000 + (it.time.replace(":", "").toIntOrNull() ?: 0) }) {
            append("━━━━━━━━━━━━━━━━━━━━━━━━\n")
            append(Tr.longDay(e.date)); if (e.time.isNotEmpty()) append(" · ").append(e.time); append('\n')
            Moods[e.mood]?.let { append("Ruh hâli: ").append(it.label) }
            if (e.energy > 0) append("   Enerji: ").append(e.energy).append("/10")
            if (e.mood != null || e.energy > 0) append('\n')
            if (e.place.isNotBlank()) append("Konum: ").append(e.place).append('\n')
            if (e.weather.isNotBlank()) append("Hava: ").append(e.weather).append('\n')
            if (e.allTags.isNotEmpty()) append(e.allTags.joinToString(" ") { "#$it" }).append('\n')
            append('\n')
            if (e.title.isNotBlank()) append(e.title.uppercase(java.util.Locale.forLanguageTag("tr"))).append("\n\n")
            append(e.plain.trim()).append("\n\n")
        }
    }

    suspend fun exportTxt(ctx: Context, uri: Uri, list: List<Entry>): Boolean = withContext(Dispatchers.IO) {
        runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text(list).toByteArray()) } }.isSuccess
    }

    /** ZIP veya JSON yedeğini içe aktarır, mevcut verilerle birleştirir. Dönen: eklenen/ güncellenen girdi sayısı. */
    suspend fun import(ctx: Context, uri: Uri, pass: String? = null): ImportResult = withContext(Dispatchers.IO) {
        try {
            val head = ctx.contentResolver.openInputStream(uri)!!.use { s -> ByteArray(4).let { b -> b.copyOf(maxOf(0, s.read(b))) } }
            var root: JSONObject? = null
            if (head.contentEquals(BackupCrypto.MAGIC)) {
                if (pass.isNullOrEmpty()) return@withContext ImportResult.NeedPass
                val total = size(ctx, uri)
                if (total <= 0) return@withContext ImportResult.Fail
                if (!BackupCrypto.verify(ctx.contentResolver.openInputStream(uri)!!, total, pass)) return@withContext ImportResult.WrongPass
                BackupCrypto.decrypting(ctx.contentResolver.openInputStream(uri)!!, total, pass).use { root = readZip(it) }
            } else if (head.size >= 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) {
                ctx.contentResolver.openInputStream(uri)!!.use { root = readZip(it) }
            } else {
                val s = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                root = if (s.trimStart().startsWith("[")) JSONObject().put("entries", JSONArray(s)) else JSONObject(s)
            }
            val r = root ?: return@withContext ImportResult.Fail
            val es = r.optJSONArray("entries")?.let { Codec.entriesFrom(it.toString()) } ?: emptyList()
            val ls = r.optJSONArray("letters")?.let { Codec.lettersFrom(it.toString()) } ?: emptyList()
            withContext(Dispatchers.Main) {
                r.optJSONArray("customMoods")?.let { a ->
                    val before = Moods.custom.map { it.key }.toSet()
                    val keep = Moods.custom.toList()
                    Moods.loadCustom(a.toString())
                    val incoming = Moods.custom.filter { it.key !in before }
                    Moods.custom.clear(); Moods.custom.addAll(keep + incoming); Prefs.save()
                }
                Store.importAll(es, ls, r.optJSONObject("habits"))
            }
            ImportResult.Ok(es.size)
        } catch (_: Exception) { ImportResult.Fail }
    }

    /** Kasa'dan şifreli bulut yedeğini geri yükle (medya dahil). */
    fun restoreZip(input: InputStream) {
        val r = readZip(input) ?: return
        val es = r.optJSONArray("entries")?.let { Codec.entriesFrom(it.toString()) } ?: emptyList()
        val ls = r.optJSONArray("letters")?.let { Codec.lettersFrom(it.toString()) } ?: emptyList()
        val latch = java.util.concurrent.CountDownLatch(1)
        android.os.Handler(android.os.Looper.getMainLooper()).post { try { Store.importAll(es, ls, r.optJSONObject("habits")) } finally { latch.countDown() } }
        latch.await(20, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun readZip(input: InputStream): JSONObject? {
        var root: JSONObject? = null
        ZipInputStream(input.buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val name = e.name
                when {
                    name == "iz.json" -> root = JSONObject(z.readBytes().decodeToString())
                    name.startsWith("media/") && !e.isDirectory -> {
                        val f = File(Store.mediaDir, File(name).name)   // yol saldırısına karşı yalnızca dosya adı
                        if (!f.exists()) f.outputStream().use { z.copyTo(it) }
                    }
                }
            }
        }
        return root
    }

    /** Kullanıcının seçtiği klasöre günde en fazla bir kez otomatik tam yedek. */
    suspend fun autoBackup(ctx: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val u = Prefs.autoBackupUri
        if (u.isEmpty()) return@withContext false
        if (!force && System.currentTimeMillis() - Prefs.lastAutoBackup < 20 * 3600_000L) return@withContext false
        try {
            val dir = DocumentFile.fromTreeUri(ctx, Uri.parse(u)) ?: return@withContext false
            val pass = Prefs.backupPass
            val name = "Iz-yedek-${stamp()}." + if (pass.isEmpty()) "zip" else "izb"
            dir.findFile(name)?.delete()
            val f = dir.createFile(if (pass.isEmpty()) "application/zip" else "application/octet-stream", name) ?: return@withContext false
            ctx.contentResolver.openOutputStream(f.uri, "wt")!!.use { writeBackup(it, pass) }
            // en fazla 7 otomatik yedek tut
            dir.listFiles().filter { it.name?.startsWith("Iz-yedek-") == true }.sortedByDescending { it.name }.drop(7).forEach { it.delete() }
            withContext(Dispatchers.Main) { Prefs.lastAutoBackup = System.currentTimeMillis(); Prefs.save() }
            true
        } catch (_: Exception) { false }
    }

    // ============================ Kitap gibi PDF ============================
    suspend fun exportPdf(ctx: Context, uri: Uri, list: List<Entry>, title: String, withPhotos: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            // 1. geçiş: ayların hangi sayfada başladığını bul; 2. geçiş: içindekilerle yaz
            val dry = Book(list, title, withPhotos, null).also { it.write(object : java.io.OutputStream() { override fun write(b: Int) {} }) }
            ctx.contentResolver.openOutputStream(uri, "wt")!!.use { Book(list, title, withPhotos, dry.monthPages).write(it) }
        }.isSuccess
    }

    private class Book(val list: List<Entry>, val title: String, val photos: Boolean, val toc: Map<String, Int>?) {
        val monthPages = LinkedHashMap<String, Int>()
        val accent = Color.rgb(247, 149, 95)
        val sorted = list.sortedBy { it.day * 10000 + (it.time.replace(":", "").toIntOrNull() ?: 0) }
        val months = sorted.map { Tr.monthYear(it.date) }.distinct()
        val W = 420; val H = 595; val M = 44f
        val doc = PdfDocument()
        var page: PdfDocument.Page? = null
        lateinit var cv: Canvas
        var y = 0f
        var n = 0
        val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = Color.rgb(30, 34, 44); typeface = serif }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 15f; color = Color.rgb(16, 24, 40); typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.rgb(214, 104, 60); typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); letterSpacing = 0.12f }
        val foot = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.rgb(140, 148, 165); textAlign = Paint.Align.CENTER }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            n++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
            page = p; cv = p.canvas; y = M
            if (n > 1) cv.drawText("$n", W / 2f, H - 20f, foot)
        }

        fun layout(text: CharSequence, paint: TextPaint, spacing: Float = 1.25f): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, (W - 2 * M).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, spacing).build()

        fun draw(l: StaticLayout) {
            for (i in 0 until l.lineCount) {
                val top = l.getLineTop(i).toFloat(); val bottom = l.getLineBottom(i).toFloat()
                if (y + (bottom - top) > H - M) newPage()
                cv.save(); cv.translate(M, y - top); cv.clipRect(0f, top, W.toFloat(), bottom); l.draw(cv); cv.restore()
                y += bottom - top
            }
        }

        fun write(out: OutputStream) {
            // kapak
            newPage()
            val cc = Covers.colors(Prefs.cover)
            cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, W.toFloat(), H.toFloat(), cc[0].toArgb(), Color.rgb(10, 14, 23), android.graphics.Shader.TileMode.CLAMP)
            })
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            cv.drawRect(M, 200f, M + 40f, 204f, p)
            val big = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
            y = 220f; val tl = layout(title, big, 1.1f); cv.save(); cv.translate(M, y); tl.draw(cv); cv.restore()
            val s = list.minOfOrNull { it.day }?.let { a -> "${Tr.long(LocalDate.ofEpochDay(a))} — ${Tr.long(LocalDate.ofEpochDay(list.maxOf { it.day }))}" } ?: ""
            val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(154, 166, 191); textSize = 11f }
            cv.drawText(s, M, y + tl.height + 26f, sub)
            cv.drawText("${list.size} gün · ${list.sumOf { it.words }} kelime", M, y + tl.height + 44f, sub)
            if (Prefs.name.isNotBlank()) cv.drawText(Prefs.name, M, H - 60f, sub)

            // içindekiler
            val perPage = 22
            months.chunked(perPage).forEachIndexed { pi, chunk ->
                newPage(); y = 90f
                if (pi == 0) { draw(layout("İçindekiler", TextPaint(head).apply { textSize = 22f })); y += 8f; cv.drawRect(M, y, M + 30f, y + 3f, Paint().apply { color = accent }); y += 24f }
                val tp = TextPaint(body).apply { textSize = 11.5f }
                val np = TextPaint(body).apply { textSize = 11.5f; textAlign = Paint.Align.RIGHT; color = accent }
                chunk.forEach { m ->
                    cv.drawText(m, M, y, tp); cv.drawText((toc?.get(m) ?: 0).toString(), W - M, y, np)
                    val n = sorted.count { Tr.monthYear(it.date) == m }
                    cv.drawText("$n gün", M + 150f, y, TextPaint(foot).apply { textAlign = Paint.Align.LEFT })
                    y += 19f
                }
            }

            var lastMonth = ""
            for (e in sorted) {
                val month = Tr.monthYear(e.date)
                if (month != lastMonth) {
                    newPage(); lastMonth = month; monthPages[month] = n
                    y = 160f
                    val mp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; color = Color.rgb(16, 24, 40); typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
                    draw(layout(month, mp)); y += 6f
                    cv.drawRect(M, y, M + 30f, y + 3f, Paint().apply { color = accent }); y += 30f
                } else if (y > H - M - 80) newPage() else y += 18f
                val meta = buildString {
                    append(Tr.upper(Tr.longDay(e.date))); if (e.time.isNotEmpty()) append(" · ").append(e.time)
                    Moods[e.mood]?.let { append(" · ").append(Tr.upper(it.label)) }
                }
                draw(layout(meta, small)); y += 4f
                if (e.title.isNotBlank()) { draw(layout(e.title, head)); y += 4f }
                val extra = listOf(e.place, e.weather).filter { it.isNotBlank() }.joinToString(" · ")
                if (extra.isNotEmpty()) { draw(layout(extra, foot.apply { textAlign = Paint.Align.LEFT })); foot.textAlign = Paint.Align.CENTER; y += 4f }
                if (e.plain.isNotBlank()) draw(layout(e.plain.trim(), body, 1.35f))
                if (photos) for (m in e.photos.take(3)) drawPhoto(Store.media(m.file))
            }
            page?.let { doc.finishPage(it) }
            doc.writeTo(out); doc.close()
        }

        fun drawPhoto(f: File) {
            if (!f.exists()) return
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, o)
            if (o.outWidth <= 0) return
            var s = 1; while (o.outWidth / s > 900) s *= 2
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return
            val maxW = W - 2 * M; val maxH = 230f
            val sc = minOf(maxW / bmp.width, maxH / bmp.height)
            val w = bmp.width * sc; val h = bmp.height * sc
            y += 10f
            if (y + h > H - M) newPage()
            cv.drawBitmap(bmp, null, RectF(M + (maxW - w) / 2, y, M + (maxW - w) / 2 + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
            y += h + 6f
            bmp.recycle()
        }
    }
}
