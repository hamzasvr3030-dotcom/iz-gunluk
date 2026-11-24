package com.iz.gunluk

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.em

/**
 * Hafif biçimlendirme dili:
 *  **kalın**   *italik*   ==vurgu==   satır başında "## " başlık, "- " madde, "1. " numara, "> " alıntı,
 *  "[ ] " / "[x] " yapılacak, tek başına "[[foto:dosya.jpg]]" satırı metin içi fotoğraf.
 * Metin düz yazı olarak saklanır; böylece yedekler her yerde okunabilir kalır.
 */
object RichText {
    private val inline = Regex("""\*\*(.+?)\*\*|==(.+?)==|(?<![*\w])\*(?!\s)([^*\n]+?)(?<!\s)\*(?![*\w])""")
    private val tag = Regex("(?<![\\p{L}\\p{N}_])#[\\p{L}\\p{N}_]{2,40}")
    private val person = Regex("(?<![\\p{L}\\p{N}_.])@[\\p{L}\\p{N}_]{2,30}")
    private val photo = Regex("^\\[\\[foto:([\\w.\\-]+)]]$")

    fun photoToken(file: String) = "[[foto:$file]]"
    fun inlinePhotos(s: String): List<String> = s.lines().mapNotNull { photo.find(it.trim())?.groupValues?.get(1) }

    sealed interface Block
    data class TextBlock(val text: String) : Block
    data class PhotoBlock(val file: String) : Block

    /** Okuma görünümü için metni yazı ve fotoğraf bloklarına böler. */
    fun blocks(s: String): List<Block> {
        val out = ArrayList<Block>(); val buf = ArrayList<String>()
        fun flush() { if (buf.isNotEmpty()) { val t = buf.joinToString("\n").trim('\n'); if (t.isNotBlank()) out += TextBlock(t); buf.clear() } }
        for (l in s.lines()) {
            val m = photo.find(l.trim())
            if (m != null) { flush(); out += PhotoBlock(m.groupValues[1]) } else buf += l
        }
        flush()
        return out
    }

    data class Span(val start: Int, val end: Int, val kind: Char)   // kind: b, i, h, m (işaret)

    /** Satır içi biçimlerin ve işaretlerin konumları (ham metin üzerinde). */
    fun spans(s: String): List<Span> {
        val out = ArrayList<Span>()
        for (m in inline.findAll(s)) {
            val r = m.range
            val (kind, ml) = when {
                m.groups[1] != null -> 'b' to 2
                m.groups[2] != null -> 'h' to 2
                else -> 'i' to 1
            }
            out += Span(r.first, r.first + ml, 'm')
            out += Span(r.first + ml, r.last + 1 - ml, kind)
            out += Span(r.last + 1 - ml, r.last + 1, 'm')
        }
        return out
    }

    private fun lineKind(line: String): Pair<String, Int>? = when {
        line.startsWith("## ") -> "head" to 3
        line.startsWith("- ") || line.startsWith("• ") -> "bullet" to 2
        line.startsWith("> ") -> "quote" to 2
        line.startsWith("[ ] ") -> "todo" to 4
        line.startsWith("[x] ") || line.startsWith("[X] ") -> "done" to 4
        photo.matches(line.trim()) -> "photo" to 0
        else -> Regex("^(\\d{1,3})\\. ").find(line)?.let { "num" to 0 }
    }

    /** İşaretsiz düz metin (arama, sayaç, dışa aktarma için). */
    fun plain(s: String): String = s.lines().mapNotNull { line ->
        val lk = lineKind(line)
        val l = when (lk?.first) {
            "head" -> line.drop(3)
            "bullet" -> "• " + line.drop(2)
            "quote" -> "“" + line.drop(2) + "”"
            "todo" -> "☐ " + line.drop(4)
            "done" -> "☑ " + line.drop(4)
            "photo" -> null
            else -> line
        }
        l?.let { x -> inline.replace(x) { it.groups[1]?.value ?: it.groups[2]?.value ?: it.groups[3]?.value ?: "" } }
    }.joinToString("\n")

    /** Okuma görünümü: işaretler gizlenir. Fotoğraf satırları atlanır (bloklarla gösterilir). */
    fun render(s: String, accent: Color, muted: Color, highlight: Color, query: String = ""): AnnotatedString = buildAnnotatedString {
        val lines = s.lines().filter { lineKind(it)?.first != "photo" }
        lines.forEachIndexed { idx, raw ->
            val lk = lineKind(raw)
            val lineStart = length
            val content = when (lk?.first) {
                "bullet" -> { pushStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)); append("•  "); pop(); raw.drop(2) }
                "todo" -> { pushStyle(SpanStyle(color = muted)); append("○  "); pop(); raw.drop(4) }
                "done" -> { pushStyle(SpanStyle(color = accent)); append("●  "); pop(); raw.drop(4) }
                "quote", "head" -> raw.drop(lk.second)
                "num" -> { val n = raw.substringBefore(". "); pushStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)); append("$n.  "); pop(); raw.substringAfter(". ") }
                else -> raw
            }
            val wrap = when (lk?.first) {
                "quote" -> SpanStyle(fontStyle = FontStyle.Italic, color = muted)
                "done" -> SpanStyle(color = muted)
                "head" -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.3.em)
                else -> null
            }
            if (wrap != null) pushStyle(wrap)
            var i = 0
            for (m in inline.findAll(content)) {
                appendTagged(content.substring(i, m.range.first), accent)
                val (txt, st) = when {
                    m.groups[1] != null -> m.groups[1]!!.value to SpanStyle(fontWeight = FontWeight.Bold)
                    m.groups[2] != null -> m.groups[2]!!.value to SpanStyle(background = highlight)
                    else -> m.groups[3]!!.value to SpanStyle(fontStyle = FontStyle.Italic)
                }
                pushStyle(st); appendTagged(txt, accent); pop()
                i = m.range.last + 1
            }
            appendTagged(content.substring(i), accent)
            if (wrap != null) pop()
            if (query.isNotBlank()) {
                val seg = toAnnotatedString().text.substring(lineStart)
                for (p in Text.findAll(seg, query)) addStyle(SpanStyle(background = highlight.copy(alpha = 0.9f)), lineStart + p, lineStart + p + query.length)
            }
            if (idx < lines.size - 1) append("\n")
        }
    }

    private fun AnnotatedString.Builder.appendTagged(s: String, accent: Color) {
        val ms = (tag.findAll(s) + person.findAll(s)).sortedBy { it.range.first }.toList()
        var i = 0
        for (m in ms) {
            if (m.range.first < i) continue
            append(s.substring(i, m.range.first))
            pushStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium)); append(m.value); pop()
            i = m.range.last + 1
        }
        append(s.substring(i))
    }

    /** Yazarken: metin aynen kalır (imleç kaymaz), biçimler canlı uygulanır, işaretler soluklaşır. */
    class Editor(private val accent: Color, private val marker: Color, private val highlight: Color, private val query: String) : VisualTransformation {
        override fun filter(text: AnnotatedString): TransformedText {
            val s = text.text
            val out = buildAnnotatedString {
                append(s)
                for (sp in spans(s)) when (sp.kind) {
                    'm' -> addStyle(SpanStyle(color = marker), sp.start, sp.end)
                    'b' -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), sp.start, sp.end)
                    'i' -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), sp.start, sp.end)
                    'h' -> addStyle(SpanStyle(background = highlight), sp.start, sp.end)
                }
                var off = 0
                for (line in s.split('\n')) {
                    val lk = lineKind(line)
                    when (lk?.first) {
                        "head" -> { addStyle(SpanStyle(color = marker), off, off + 3); addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.3.em), off + 3, off + line.length) }
                        "photo" -> addStyle(SpanStyle(color = marker, fontSize = 0.8.em, fontStyle = FontStyle.Italic), off, off + line.length)
                        "num" -> { val n = line.indexOf(". ") + 2; addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), off, off + n) }
                        "quote" -> { addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), off, off + 2); addStyle(SpanStyle(fontStyle = FontStyle.Italic), off, off + line.length) }
                        null -> {}
                        else -> addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold), off, off + lk.second)
                    }
                    off += line.length + 1
                }
                for (m in tag.findAll(s)) addStyle(SpanStyle(color = accent), m.range.first, m.range.last + 1)
                for (m in person.findAll(s)) addStyle(SpanStyle(color = accent), m.range.first, m.range.last + 1)
                if (query.isNotBlank()) for (p in Text.findAll(s, query)) addStyle(SpanStyle(background = highlight.copy(alpha = 0.95f), color = Color.Black), p, p + query.length)
            }
            return TransformedText(out, OffsetMapping.Identity)
        }

        override fun equals(other: Any?) = other is Editor && other.accent == accent && other.query == query && other.marker == marker && other.highlight == highlight
        override fun hashCode() = accent.hashCode() * 31 + query.hashCode()
    }
}
