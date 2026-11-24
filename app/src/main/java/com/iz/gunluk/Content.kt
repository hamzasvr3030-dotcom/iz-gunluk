package com.iz.gunluk

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate

data class Mood(val key: String, val label: String, val color: Color, val icon: ImageVector, val score: Int)

object Moods {
    val all = listOf(
        Mood("mutlu", "Mutlu", Color(0xFFFFC53D), Icons.Rounded.SentimentVerySatisfied, 5),
        Mood("huzurlu", "Huzurlu", Color(0xFF36CFC9), Icons.Rounded.Spa, 4),
        Mood("sakin", "Sakin", Color(0xFF69B1FF), Icons.Rounded.SelfImprovement, 4),
        Mood("heyecanli", "Heyecanlı", Color(0xFFFF7A45), Icons.Rounded.Celebration, 5),
        Mood("odakli", "Odaklanmış", Color(0xFF2F7BFF), Icons.Rounded.CenterFocusStrong, 4),
        Mood("yorgun", "Yorgun", Color(0xFF9E8CFF), Icons.Rounded.Bedtime, 2),
        Mood("uzgun", "Üzgün", Color(0xFF5B6B8C), Icons.Rounded.SentimentDissatisfied, 1),
        Mood("ofkeli", "Öfkeli", Color(0xFFFF4D4F), Icons.Rounded.LocalFireDepartment, 1),
        Mood("kararsiz", "Kararsız", Color(0xFFB5A58A), Icons.Rounded.QuestionMark, 3),
        Mood("karisik", "Karışık", Color(0xFFD46BCF), Icons.Rounded.Cloud, 3),
    )
    private val map = all.associateBy { it.key }
    operator fun get(k: String?) = k?.let { map[it] }
}

data class Template(val key: String, val name: String, val title: String, val body: String, val tag: String)

object Templates {
    val all = listOf(
        Template("seyahat", "Seyahat günlüğü", "Yolculuk notları",
            "**Neredeyim?** \n\n**Bugün neler gördüm?**\n- \n\n**En güzel an:** \n\n**Tattığım yeni bir şey:** \n\n**Tanıştığım biri:** \n", "seyahat"),
        Template("okul", "Okul günlüğü", "Okulda bugün",
            "**Bugün öğrendiğim en ilginç şey:** \n\n**Zorlandığım konu:** \n\n**Yapılacaklar**\n[ ] \n[ ] \n\n**Yarın için not:** \n", "okul"),
        Template("duygu", "Duygu günlüğü", "Duygularım",
            "**Şu an ne hissediyorum?** \n\n**Bu duyguyu ne tetikledi?** \n\n**Bedenimde nerede hissediyorum?** \n\n**Kendime ne söylemek isterim?** \n> \n", "duygu"),
        Template("sukran", "Şükran", "Minnet duyduklarım",
            "Bugün minnet duyduğum üç şey:\n1. \n2. \n3. \n\n**Beni gülümseten küçük an:** \n", "şükran"),
        Template("aksam", "Akşam değerlendirmesi", "Günün değerlendirmesi",
            "**Bugün iyi giden:** \n\n**Daha iyi olabilecek:** \n\n**Yarın tek bir şey yapacak olsam:** \n", "değerlendirme"),
        Template("hedef", "Hedef ve plan", "Hedeflerim",
            "**Bu haftanın hedefi:** \n\n**Adımlar**\n[ ] \n[ ] \n[ ] \n\n**Neden önemli?** \n", "hedef"),
        Template("ruya", "Rüya günlüğü", "Rüyam",
            "**Rüyada neler oldu?** \n\n**Kimler vardı?** \n\n**Uyandığımda nasıl hissettim?** \n", "rüya"),
    )
    operator fun get(k: String) = all.firstOrNull { it.key == k }
}

object Questions {
    val all = listOf(
        "Bugün seni en çok şaşırtan şey neydi?",
        "Bugün kime teşekkür etmek isterdin?",
        "Bugün kendinle ilgili fark ettiğin bir şey var mı?",
        "Bugünü tek kelimeyle anlatsan hangisi olurdu?",
        "Bugün seni ne güldürdü?",
        "Bugün ertelediğin bir şey var mı? Neden?",
        "Şu an en çok neyi merak ediyorsun?",
        "Bugün öğrendiğin küçük ama değerli bir şey neydi?",
        "Bugün kendine nasıl iyi davrandın?",
        "Bir yıl sonraki kendine bugünden ne söylemek isterdin?",
        "Bugün enerjini en çok ne yükseltti, ne düşürdü?",
        "Bugün hangi an keşke biraz daha uzun sürseydi?",
        "Son zamanlarda seni en çok ne düşündürüyor?",
        "Bugün kiminle en anlamlı sohbeti yaptın?",
        "Bugün cesaret gerektiren bir şey yaptın mı?",
        "Bugün neyi bırakmaya hazırsın?",
        "Bugün dinlediğin, okuduğun ya da izlediğin en iyi şey neydi?",
        "Bugün kendinle gurur duyduğun bir an oldu mu?",
        "Çocukken bugünkü hâlini görsen ne derdin?",
        "Bugün seni yoran neydi? Onu nasıl hafifletebilirsin?",
        "Bugün hangi kokuyu, sesi ya da tadı hatırlamak istersin?",
        "Bugün bir şeyi farklı yapabilseydin ne olurdu?",
        "Şu an hayatında en çok neye minnettarsın?",
        "Bu hafta sana iyi gelen bir alışkanlık var mı?",
        "Bugün kimi düşündün?",
        "Bugün hangi kararı verdin, nasıl hissettirdi?",
        "Kafanda dönüp duran bir düşünceyi buraya bırak.",
        "Bugün dışarıda ne gördün? Hava, insanlar, sokak...",
        "Bugün kendine bir not bıraksan ne yazardın?",
        "Bugün en sakin hissettiğin an hangisiydi?",
        "Bugün bir şeye ilk kez mi denk geldin?",
        "Yakın zamanda hayal ettiğin bir şey var mı?",
        "Bugün ne yedin, en güzeli hangisiydi?",
        "Bugün bir şey için endişelendin mi? Gerçekten olur mu sence?",
        "Bugün kimseye söylemediğin bir düşünce var mı?",
        "Bugün zaman nasıl aktı: hızlı mı yavaş mı?",
        "Bugün hangi şarkı aklında kaldı?",
        "Bugün kime yardım ettin ya da kim sana yardım etti?",
        "Bu ay için küçük bir hedef koysan ne olurdu?",
        "Bugün en çok hangi duyguyu hissettin, neden?",
        "Bugün telefonsuz geçen bir an var mıydı?",
        "Bugün seni sen yapan bir şey yaptın mı?",
        "Bugün yarım kalan bir iş var mı? Yarın nereden başlarsın?",
        "Bugün kendini en çok nerede, hangi ortamda iyi hissettin?",
        "Bugün bir hata yaptıysan, ondan ne öğrendin?",
        "Bugün birinin sana söylediği akılda kalan bir söz var mı?",
        "Şu an penceren dışında ne var?",
        "Bugün neyin tadını çıkardın?",
        "Bugünün en sıradan anı neydi? Aslında güzel miydi?",
        "Bu günü bir fotoğraf olarak hatırlasan, karede ne olurdu?",
    )
    fun forDay(day: Long = LocalDate.now().toEpochDay()): String = all[(Math.floorMod(day * 7919, all.size.toLong())).toInt()]
}

object Quotes {
    val all = listOf(
        "Bazen en güzel günler, sıradan olduğunu düşündüğümüz günlerdir.",
        "Yazdığın her satır, yarınki sana bir hediye.",
        "Bir gün daha, bir anı daha.",
        "Unutmak istemediğin her şey buraya.",
        "Küçük anlar, büyük hikâyelerin başlangıcıdır.",
        "Bugünü yaz; yarın onu yeniden yaşamak isteyeceksin.",
        "Hiçbir gün boş geçmez; sadece yazılmamış kalır.",
    )
    fun forDay(day: Long) = all[Math.floorMod(day, all.size.toLong()).toInt()]
}

/** Türkçe tarih biçimleri. */
object Tr {
    val months = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
    val monthsShort = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")
    val days = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")
    val daysShort = listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz")

    fun long(d: LocalDate) = "${d.dayOfMonth} ${months[d.monthValue - 1]} ${d.year}"
    fun longDay(d: LocalDate) = "${long(d)} · ${days[d.dayOfWeek.value - 1]}"
    fun short(d: LocalDate) = "${d.dayOfMonth} ${monthsShort[d.monthValue - 1]}"
    fun monthYear(d: LocalDate) = "${months[d.monthValue - 1]} ${d.year}"
    fun upper(s: String) = s.uppercase(java.util.Locale.forLanguageTag("tr"))

    fun ago(from: LocalDate, to: LocalDate = LocalDate.now()): String {
        val y = to.year - from.year
        val days = to.toEpochDay() - from.toEpochDay()
        return when {
            days == 0L -> "Bugün"
            days == 1L -> "Dün"
            from.dayOfMonth == to.dayOfMonth && from.monthValue == to.monthValue && y > 0 -> "$y yıl önce bugün"
            days < 30 -> "$days gün önce"
            days < 365 -> "${days / 30} ay önce"
            else -> "${days / 365} yıl önce"
        }
    }

    fun greeting(hour: Int) = when (hour) {
        in 5..11 -> "Günaydın"
        in 12..17 -> "İyi günler"
        in 18..22 -> "İyi akşamlar"
        else -> "İyi geceler"
    }

    fun weatherText(code: Int) = when (code) {
        0 -> "Açık"
        1 -> "Çoğunlukla açık"
        2 -> "Parçalı bulutlu"
        3 -> "Kapalı"
        45, 48 -> "Sisli"
        51, 53, 55, 56, 57 -> "Çiseleme"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Yağmurlu"
        71, 73, 75, 77, 85, 86 -> "Karlı"
        95, 96, 99 -> "Fırtınalı"
        else -> "Değişken"
    }
}
