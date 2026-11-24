# İz — kişisel günlük

Hayatında bıraktığın izler. Gününü hızlıca yaz, ruh hâlini takip et, anılarını sakla; yıllar sonra kendi geçmişini keşfet.
Tüm veriler telefonda kalır; hesap ve bulut yok.

**İndir:** [Iz.apk](https://github.com/hamzasvr3030-dotcom/iz-gunluk/releases/latest/download/Iz.apk) (Android 8.0+)
Yeni sürümler uygulama içinden kurulur (Ayarlar › Sürüm).

## Özellikler

- **Hızlı yazım:** tek dokunuşla yeni girdi, otomatik tarih/saat, otomatik kayıt, kalın/italik/vurgu, madde, numara, yapılacak, alıntı, kelime/karakter sayacı, yazı içinde arama, odak modu, şablonlar, günün sorusu
- **Ruh hâli ve enerji:** 10 ruh hâli, 1–10 enerji, renkli duygu takvimi, aylık ruh hâli grafiği
- **Takvim ve geçmiş:** ay ve yıl görünümü, gün sayfası, kelime / tarih aralığı / etiket / ruh hâli / favori / medya filtresi, #etiketler
- **Anılar:** çoklu fotoğraf, video, ses notu, konum ve hava durumu, favoriler, fotoğraf galerisi, anı haritası (OpenStreetMap), geçmişte bugün
- **Fark yaratanlar:** geleceğe mektup (açılış tarihine kadar mühürlü), yaşam istatistikleri, aylık ve yıllık özet, cihaz üzerinde dönem özeti (internete hiçbir şey gönderilmez)
- **Gizlilik:** PIN, desen, parmak izi/yüz kilidi; gizli günlükler; ekran görüntüsü koruması
- **Yedekleme:** tam ZIP yedek (medya dahil), seçilen klasöre günlük otomatik yedek, JSON, TXT ve kitap gibi PDF
- **Alışkanlık:** nazik hatırlatıcı (gün/saat seçimi), seri, haftalık hedef, ana ekran widget'ı
- **Gelişmiş:** konuşarak yazma, sesli okuma (TTS), açık / koyu / AMOLED tema, 6 vurgu rengi, yazı tipi, boyut ve satır aralığı

## Derleme

```
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest
```

Kotlin + Jetpack Compose, minSdk 26.
