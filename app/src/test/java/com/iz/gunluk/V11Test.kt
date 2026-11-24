package com.iz.gunluk

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class V11Test {
    private fun enc(data: ByteArray, pass: String): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypting(out, pass).use { s -> var i = 0; while (i < data.size) { val n = minOf(7777, data.size - i); s.write(data, i, n); i += n } }
        return out.toByteArray()
    }

    @Test fun backupCryptoRoundTrip() {
        val data = Random(3).nextBytes(200_000)
        val b = enc(data, "gizli-parola")
        assertArrayEquals(BackupCrypto.MAGIC, b.copyOfRange(0, 4))
        assertTrue(BackupCrypto.verify(ByteArrayInputStream(b), b.size.toLong(), "gizli-parola"))
        val back = BackupCrypto.decrypting(ByteArrayInputStream(b), b.size.toLong(), "gizli-parola").readBytes()
        assertArrayEquals(data, back)
    }

    @Test fun backupCryptoRejectsWrongPassOrTamper() {
        val b = enc("merhaba dünya".toByteArray(), "doğru")
        assertFalse(BackupCrypto.verify(ByteArrayInputStream(b), b.size.toLong(), "yanlış"))
        val t = b.copyOf(); t[40] = (t[40].toInt() xor 1).toByte()
        assertFalse(BackupCrypto.verify(ByteArrayInputStream(t), t.size.toLong(), "doğru"))
    }

    @Test fun horoscopeCleanDecodesEntities() {
        assertEquals("Bugün güçlü ve özgürsün…", Horoscope.clean("<span>Bug&uuml;n g&uuml;&ccedil;l&uuml;</span> ve &ouml;zg&uuml;rs&uuml;n&hellip;"))
        assertEquals("A & B 'x' ş", Horoscope.clean("A &amp; B &#39;x&#39; &#x15F;"))
        assertEquals("iki satır", Horoscope.clean("iki<br/>   satır"))
    }

    @Test fun horoscopeParsesMilliyet() {
        val html = """<html><div class="horoscope-tabs__content__main-inner">
            <p><strong>İş ve kariyer:</strong> Bug&uuml;n yeni bir fırsat kapını çalabilir, dikkatli ol.</p>
            <p><strong>Aşk ve ilişkiler:</strong> Partnerinle konuşmak için uygun bir gün olabilir.</p>
            <p>kısa</p></div></section>"""
        val p = Horoscope.parseMilliyet(html)
        assertEquals(2, p.size)
        assertEquals("İş ve kariyer", p[0].title)
        assertTrue(p[0].text.startsWith("Bugün yeni"))
        assertEquals("Aşk ve ilişkiler", p[1].title)
        assertTrue(Horoscope.parseMilliyet("<html>yok</html>").isEmpty())
    }

    @Test fun horoscopeParsesHurriyet() {
        val html = """<div class="horoscope-detail-tab-content"><p><strong>Aygül Aydın'la burç yorumları...</strong></p>
            <p>Bugün enerjin yüksek; uzun zamandır ertelediğin işleri bitirmek için çok uygun bir gün.</p></div><div class="horoscope-detail-footer">"""
        val p = Horoscope.parseHurriyet(html)
        assertEquals(1, p.size)
        assertTrue(p[0].text.startsWith("Bugün enerjin"))
    }

    @Test fun peopleMentions() {
        assertEquals(listOf("Ayşe", "Mehmet"), Text.people("Bugün @Ayşe ve @Mehmet ile çay içtik. @Ayşe çok güldü."))
        assertTrue(Text.people("mail: ali@ornek.com").isEmpty())
        assertTrue(Text.people("@a tek harf").isEmpty())
    }

    @Test fun headingsAndInlinePhotos() {
        val s = "## Sabah\nKahve içtim.\n[[foto:abc.jpg]]\nSonra yürüdüm."
        assertEquals(listOf("abc.jpg"), RichText.inlinePhotos(s))
        val b = RichText.blocks(s)
        assertEquals(3, b.size)
        assertEquals(RichText.PhotoBlock("abc.jpg"), b[1])
        val plain = RichText.plain(s)
        assertFalse(plain.contains("foto:"))
        assertFalse(plain.contains("##"))
        assertTrue(plain.startsWith("Sabah"))
        assertEquals("[[foto:x.png]]", RichText.photoToken("x.png"))
    }

    @Test fun entryMultiMoodJson() {
        val e = Entry(day = 1, time = "10:00", body = "@Zeynep ile #sinema", mood = "mutlu", extraMoods = listOf("yorgun", "heyecanli"))
        val back = Codec.entriesFrom(Codec.entriesToJson(listOf(e)).toString()).single()
        assertEquals(listOf("mutlu", "yorgun", "heyecanli"), back.moods)
        assertEquals(listOf("Zeynep"), back.people)
    }

    @Test fun habitsDoneRateStreak() {
        val d = HabitDef("su", "Su", "count", 8.0, "water", "bardak")
        val c = HabitDef("spor", "Spor", "check", 1.0, "sport")
        Habits.log.clear()
        val t = 20_000L
        Habits.log[t] = mapOf("su" to 8.0, "spor" to 1.0)
        Habits.log[t - 1] = mapOf("su" to 5.0, "spor" to 1.0)
        Habits.log[t - 2] = mapOf("spor" to 1.0)
        assertTrue(Habits.done(d, 8.0)); assertFalse(Habits.done(d, 7.0))
        assertEquals(1, Habits.streak(d, t))
        assertEquals(3, Habits.streak(c, t))
        assertEquals(3f / 30f, Habits.rate(c, 30, t), 1e-6f)
        val j = Habits.toJson()
        Habits.log.clear(); Habits.load(j)
        assertEquals(5.0, Habits.value(t - 1, "su"), 0.0)
        Habits.log.clear()
    }

    @Test fun sayingsAndSigns() {
        assertTrue(Sayings.all.size >= 50)
        assertEquals(Sayings.forDay(100), Sayings.forDay(100))
        assertNotEquals(Sayings.forDay(100), Sayings.forDay(101))
        assertEquals("yay", Signs["olmayan"].key)
        assertEquals(12, Signs.all.map { it.slug }.toSet().size)
    }
}
