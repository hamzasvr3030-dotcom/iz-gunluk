package com.iz.gunluk

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LogicTest {
    private val t = LocalDate.of(2026, 11, 24).toEpochDay()

    @Test fun streakCountsFromTodayOrYesterday() {
        assertEquals(3, Insights.streak(setOf(t, t - 1, t - 2, t - 5), t))
        assertEquals(2, Insights.streak(setOf(t - 1, t - 2), t))      // bugün henüz yazılmadı, seri bozulmaz
        assertEquals(0, Insights.streak(setOf(t - 2), t))
        assertEquals(4, Insights.longestStreak(setOf(1, 2, 3, 4, 8, 9)))
    }

    @Test fun weekCountMondayBased() {
        val d = LocalDate.of(2026, 11, 24) // Salı
        val mon = LocalDate.of(2026, 11, 23).toEpochDay()
        assertEquals(2, Insights.weekCount(setOf(mon, mon + 1, mon - 1), d))
    }

    @Test fun plainRemovesMarkers() {
        assertEquals("Bugün çok güzel bir gündü", RichText.plain("Bugün **çok** *güzel* bir ==gün==dü"))
        assertEquals("• madde\n☐ iş\n☑ bitti", RichText.plain("- madde\n[ ] iş\n[x] bitti"))
        assertEquals("2*3=6", RichText.plain("2*3=6"))
    }

    @Test fun spansAreOnRawText() {
        val s = "a **kalın** b"
        val sp = RichText.spans(s)
        val bold = sp.first { it.kind == 'b' }
        assertEquals("kalın", s.substring(bold.start, bold.end))
        assertEquals(2, sp.count { it.kind == 'm' })
    }

    @Test fun hashtagsAndFold() {
        assertEquals(listOf("okul", "İş"), Text.hashtags("Bugün #okul vardı, sonra #İş. mail@x.com #a"))
        assertEquals("is", Text.normTag("#İş").let { Text.fold(it) })
        assertEquals(listOf(6), Text.findAll("Bugün ŞEKER aldım", "seker"))
        assertEquals(3, Text.words("Bir, iki üç!"))
    }

    @Test fun jsonRoundTripAndMerge() {
        val e = Entry(day = t, time = "21:05", title = "Başlık", body = "Metin #aile", mood = "mutlu", energy = 7,
            media = listOf(Media("photo_1.jpg", "photo")), lat = 41.0, lon = 29.0, fav = true)
        val back = Codec.entriesFrom(Codec.entriesToJson(listOf(e)).toString()).single()
        assertEquals(e, back)
        assertEquals(listOf("aile"), back.allTags)
        val newer = e.copy(title = "Yeni", updated = e.updated + 10)
        val other = Entry(day = t - 1, body = "x")
        val m = Codec.merge(listOf(e, other), listOf(newer))
        assertEquals(2, m.size)
        assertEquals("Yeni", m.first { it.id == e.id }.title)
        assertEquals("Yeni", Codec.merge(listOf(newer), listOf(e)).single().title)   // eski yedek yeniyi ezmez
    }

    @Test fun nullMoodRoundTrip() {
        val e = Entry(body = "a")
        assertNull(Codec.entriesFrom(Codec.entriesToJson(listOf(e)).toString()).single().mood)
    }

    @Test fun summaryAndHighlights() {
        val list = listOf(
            Entry(day = t - 2, body = "Bugün denizde yüzdüm ve deniz çok soğuktu ama harika hissettim.", mood = "mutlu", energy = 8, tags = listOf("tatil")),
            Entry(day = t - 1, body = "Deniz kenarında uzun bir yürüyüş yaptık, akşam balık yedik.", mood = "huzurlu", energy = 6, tags = listOf("tatil")),
            Entry(day = t, body = "Yorgunum.", mood = "yorgun", energy = 3),
        )
        val s = Insights.summary(list)
        assertEquals(3, s.daysWritten)
        assertEquals("tatil", s.topTags.first().first)
        assertTrue(s.topWords.any { it.first.startsWith("deniz") })
        assertEquals(2, s.highlights.size)
        assertEquals(17.0 / 3, s.avgEnergy!!, 1e-9)
        assertEquals("mutlu", s.brightest!!.mood)
    }

    @Test fun onThisDayAndMoodLine() {
        val today = LocalDate.of(2026, 11, 24)
        val past = Entry(day = LocalDate.of(2025, 11, 24).toEpochDay(), body = "geçen yıl", mood = "sakin")
        val other = Entry(day = LocalDate.of(2025, 11, 23).toEpochDay(), body = "değil")
        assertEquals(listOf(past), Insights.onThisDay(listOf(past, other), today))
        val line = Insights.moodLine(listOf(past), YearMonth.of(2025, 11))
        assertEquals(30, line.size)
        assertEquals(4.0, line[23]!!, 0.0)
        assertNull(line[0])
    }

    @Test fun questionsAreStable() {
        assertEquals(Questions.forDay(t), Questions.forDay(t))
        assertTrue(Questions.all.map { Questions.forDay(it.length.toLong() + t) }.toSet().size > 1)
    }

    @Test fun letterOpens() {
        val l = Letter(openDay = t + 365, title = "", body = "x")
        assertFalse(l.isOpen(t)); assertTrue(l.isOpen(t + 365))
    }
}
