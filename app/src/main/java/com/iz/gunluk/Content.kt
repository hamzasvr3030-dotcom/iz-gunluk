package com.iz.gunluk

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.time.LocalDate

data class Mood(val key: String, val label: String, val color: Color, val icon: ImageVector, val score: Int, val custom: Boolean = false, val iconKey: String = "")

object Moods {
    /** Kullanıcının eklediği ruh hâllerinde seçilebilen simgeler. */
    val iconPool: List<Pair<String, ImageVector>> = listOf(
        "star" to Icons.Rounded.Star, "heart" to Icons.Rounded.Favorite, "bolt" to Icons.Rounded.Bolt,
        "sun" to Icons.Rounded.WbSunny, "moon" to Icons.Rounded.DarkMode, "music" to Icons.Rounded.MusicNote,
        "coffee" to Icons.Rounded.LocalCafe, "pets" to Icons.Rounded.Pets, "plane" to Icons.Rounded.Flight,
        "book" to Icons.Rounded.MenuBook, "sport" to Icons.Rounded.FitnessCenter, "sick" to Icons.Rounded.Sick,
        "psych" to Icons.Rounded.Psychology, "nature" to Icons.Rounded.Park, "water" to Icons.Rounded.WaterDrop,
        "rain" to Icons.Rounded.Umbrella,
    )
    val colorPool = listOf(0xFFFF6B6B, 0xFFFFA94D, 0xFFFFD43B, 0xFF94D82D, 0xFF38D9A9, 0xFF3BC9DB, 0xFF4DABF7,
        0xFF748FFC, 0xFF9775FA, 0xFFDA77F2, 0xFFF783AC, 0xFFADB5BD).map { Color(it) }

    val custom = androidx.compose.runtime.mutableStateListOf<Mood>()
    val all: List<Mood> get() = builtIn + custom
    operator fun get(k: String?) = k?.let { key -> builtIn.firstOrNull { it.key == key } ?: custom.firstOrNull { it.key == key } }

    fun customJson(): String = org.json.JSONArray(custom.map {
        org.json.JSONObject().put("key", it.key).put("label", it.label).put("color", it.color.value.toLong().toString()).put("icon", it.iconKey).put("score", it.score)
    }).toString()

    fun loadCustom(s: String) {
        custom.clear()
        if (s.isBlank()) return
        runCatching {
            val a = org.json.JSONArray(s)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val ik = o.optString("icon")
                custom += Mood(o.getString("key"), o.getString("label"), Color(o.getString("color").toULong()),
                    iconPool.firstOrNull { it.first == ik }?.second ?: Icons.Rounded.Star, o.optInt("score", 3), true, ik)
            }
        }
    }

    val builtIn = listOf(
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
}

data class Saying(val text: String, val by: String)

/** Her gün değişen sözler. Atıfı tartışmalı olan sözler bilerek alınmadı; kaynağı belirsizse "Atasözü" / "Anonim". */
object Sayings {
    val all = listOf(
        Saying("Hayatta en hakiki mürşit ilimdir.", "Mustafa Kemal Atatürk"),
        Saying("Yaratılanı severiz Yaradan'dan ötürü.", "Yunus Emre"),
        Saying("Yaşamak bir ağaç gibi tek ve hür ve bir orman gibi kardeşçesine.", "Nâzım Hikmet"),
        Saying("Bedava yaşıyoruz, bedava; hava bedava, bulut bedava.", "Orhan Veli Kanık"),
        Saying("Bir insanı sevmekle başlar her şey.", "Sait Faik Abasıyanık"),
        Saying("Aynı nehirde iki kez yıkanılmaz.", "Herakleitos"),
        Saying("Sorgulanmamış bir hayat yaşanmaya değmez.", "Sokrates"),
        Saying("Bin millik yolculuk tek bir adımla başlar.", "Lao Tzu"),
        Saying("Hayat geriye bakılarak anlaşılır ama ileriye bakılarak yaşanır.", "Søren Kierkegaard"),
        Saying("Kâğıt, insanlardan daha sabırlıdır.", "Anne Frank"),
        Saying("Yaşamayı öğrenmek bütün bir ömür alır.", "Seneca"),
        Saying("Cesaret edemediğimiz şey zor olduğu için değil; cesaret edemediğimiz için zordur.", "Seneca"),
        Saying("Ruh, düşüncelerin rengini alır.", "Marcus Aurelius"),
        Saying("Bizi üzen olaylar değil, olaylar hakkındaki yargılarımızdır.", "Epiktetos"),
        Saying("Bildiğim tek şey, hiçbir şey bilmediğimdir.", "Sokrates"),
        Saying("Kendini bil.", "Delfi Tapınağı yazıtı"),
        Saying("Yurtta sulh, cihanda sulh.", "Mustafa Kemal Atatürk"),
        Saying("Sanatsız kalan bir milletin hayat damarlarından biri kopmuş demektir.", "Mustafa Kemal Atatürk"),
        Saying("Gelin tanış olalım, işi kolay kılalım; sevelim sevilelim, dünya kimseye kalmaz.", "Yunus Emre"),
        Saying("Damlaya damlaya göl olur.", "Atasözü"),
        Saying("Sabrın sonu selamettir.", "Atasözü"),
        Saying("Ağaç yaşken eğilir.", "Atasözü"),
        Saying("Bugünün işini yarına bırakma.", "Atasözü"),
        Saying("Gülme komşuna, gelir başına.", "Atasözü"),
        Saying("Ak akçe kara gün içindir.", "Atasözü"),
        Saying("Bir elin nesi var, iki elin sesi var.", "Atasözü"),
        Saying("Dost kara günde belli olur.", "Atasözü"),
        Saying("Her işte bir hayır vardır.", "Atasözü"),
        Saying("Söz gümüşse sükût altındır.", "Atasözü"),
        Saying("Tatlı dil yılanı deliğinden çıkarır.", "Atasözü"),
        Saying("Gönül ne kahve ister ne kahvehane, gönül sohbet ister kahve bahane.", "Atasözü"),
        Saying("Bir kahvenin kırk yıl hatırı vardır.", "Atasözü"),
        Saying("Akıl akıldan üstündür.", "Atasözü"),
        Saying("Ayağını yorganına göre uzat.", "Atasözü"),
        Saying("Komşu komşunun külüne muhtaçtır.", "Atasözü"),
        Saying("Ne ekersen onu biçersin.", "Atasözü"),
        Saying("Yavaş yavaş, yürüyen yol alır.", "Atasözü"),
        Saying("İşleyen demir ışıldar.", "Atasözü"),
        Saying("Bilmemek ayıp değil, öğrenmemek ayıp.", "Atasözü"),
        Saying("Her karanlık gecenin bir aydınlık sabahı vardır.", "Söz"),
        Saying("Umut fakirin ekmeğidir.", "Atasözü"),
        Saying("Bir gülün yüzünden bin dikene su verilir.", "Atasözü"),
        Saying("Gün doğmadan neler doğar.", "Atasözü"),
        Saying("Dünyayı güzellik kurtaracak.", "Fyodor Dostoyevski"),
        Saying("İnsan, yalnızca kalbiyle doğru görebilir.", "Antoine de Saint-Exupéry"),
        Saying("Sen bir gülü sevdiysen, onun için harcadığın zamandır onu önemli kılan.", "Antoine de Saint-Exupéry"),
        Saying("Düşünüyorum, öyleyse varım.", "René Descartes"),
        Saying("Hayal gücü bilgiden daha önemlidir.", "Albert Einstein"),
        Saying("Mutlu aileler birbirine benzer; her mutsuz ailenin mutsuzluğu kendine özgüdür.", "Lev Tolstoy"),
        Saying("Bir şeyin değerini bilmek için onu kaybetmek gerekmez; ona dikkatle bakmak yeter.", "Anonim"),
        Saying("Bugün, dün endişelendiğin yarındır.", "Anonim"),
        Saying("Küçük adımlar da seni bir yere götürür.", "Anonim"),
        Saying("Yavaş gitmekten korkma, durmaktan kork.", "Çin atasözü"),
        Saying("Ağaç dikmenin en iyi zamanı yirmi yıl önceydi; ikinci en iyi zamanı bugündür.", "Anonim"),
        Saying("Yedi kez düş, sekiz kez kalk.", "Japon atasözü"),
        Saying("Sakin denizler usta denizci yetiştirmez.", "Anonim"),
        Saying("Hızlı gitmek istiyorsan yalnız git; uzağa gitmek istiyorsan birlikte git.", "Afrika atasözü"),
        Saying("Bedenine iyi bak; içinde yaşayacağın tek yer orası.", "Jim Rohn"),
        Saying("Yazmak, düşünmenin en yavaş ve en dürüst hâlidir.", "Anonim"),
        Saying("Her gün yeni bir sayfa; ne yazacağına sen karar verirsin.", "Anonim"),
        Saying("Unutma: hatırlamaya değer günler, çoğu zaman sıradan görünür.", "Anonim"),
        Saying("Bir gün bu günleri özleyeceksin; şimdiden biraz sev.", "Anonim"),
        Saying("Dinlenmek de yolun bir parçasıdır.", "Anonim"),
        Saying("Mükemmel olmasına gerek yok; sadece başla.", "Anonim"),
        Saying("İyi bakarsan iyi görürsün.", "Anonim"),
        Saying("Bazı günler sadece nefes almak bile yeterlidir.", "Anonim"),
        Saying("Teşekkür etmek, sahip olduklarını yeterli kılar.", "Anonim"),
    )
    fun forDay(day: Long): Saying = all[Math.floorMod(day * 31 + 7, all.size.toLong()).toInt()]
}

data class Sign(val key: String, val name: String, val dates: String, val slug: String)

object Signs {
    val all = listOf(
        Sign("koc", "Koç", "21 Mart – 20 Nisan", "koc"), Sign("boga", "Boğa", "21 Nisan – 21 Mayıs", "boga"),
        Sign("ikizler", "İkizler", "22 Mayıs – 22 Haziran", "ikizler"), Sign("yengec", "Yengeç", "23 Haziran – 22 Temmuz", "yengec"),
        Sign("aslan", "Aslan", "23 Temmuz – 22 Ağustos", "aslan"), Sign("basak", "Başak", "23 Ağustos – 22 Eylül", "basak"),
        Sign("terazi", "Terazi", "23 Eylül – 22 Ekim", "terazi"), Sign("akrep", "Akrep", "23 Ekim – 21 Kasım", "akrep"),
        Sign("yay", "Yay", "22 Kasım – 21 Aralık", "yay"), Sign("oglak", "Oğlak", "22 Aralık – 21 Ocak", "oglak"),
        Sign("kova", "Kova", "22 Ocak – 19 Şubat", "kova"), Sign("balik", "Balık", "20 Şubat – 20 Mart", "balik"),
    )
    operator fun get(k: String) = all.firstOrNull { it.key == k } ?: all[8]
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
    fun weekday(d: LocalDate) = days[d.dayOfWeek.value - 1]

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
