# Max TV kurulum ve kullanım rehberi

**APK indir:** https://github.com/MustafaCanDere/maxtv/raw/main/MaxTV.apk

## Ne yapar?
Android TV için sade bir kanal oynatıcıdır. İnternetteki herkese açık ve **ücretsiz** yayınların listelerini
(iptv-org topluluk listeleri) indirir ve kumandayla izlemenizi sağlar.

Hazır listeler: Türkiye, Türkçe (tüm ülkeler), Haber, Spor, Çocuk, Müzik, Film, Belgesel, Dünya (tümü, binlerce kanal),
★ Favoriler, ve **Kendi listem (URL)**: yasal olarak erişim hakkınız olan herhangi bir M3U adresi.

> Not: beIN Sports, S Sport, Digiturk/D-Smart/Tivibu gibi ücretli kanallar bu listelerde yoktur ve olmamalıdır.
> Bunlar için o servisin kendi uygulaması ve aboneliği gerekir. Listelerdeki bazı yayınlar zaman zaman kapanabilir
> veya bölge kısıtlı olabilir; açılmayan kanalda ▲/▼ ile bir sonrakine geçin.

## TV'ye kurulum (en kolay yol: USB bellek)
1. `MaxTV.apk` dosyasını bilgisayara indirip bir USB belleğe kopyalayın.
2. TV'de **Ayarlar → Cihaz Tercihleri → Güvenlik ve kısıtlamalar → Bilinmeyen kaynaklar** bölümüne gidin ve
   dosya yöneticisine izin verin. (Menü adı markaya göre biraz farklı olabilir.)
3. Play Store'dan TV'ye ücretsiz bir dosya yöneticisi kurun (ör. **File Commander** veya **X-plore**).
4. USB belleği TV'ye takın, dosya yöneticisinden `MaxTV.apk`'yı açın ve **Yükle** deyin.
5. Uygulama ana ekranda **Max TV** adıyla görünür.

### Alternatifler
- **Telefondan:** Hem telefona hem TV'ye *Send Files to TV* uygulamasını kurun, APK'yı telefondan TV'ye gönderin.
- **Downloader uygulaması:** APK'yı bir indirme bağlantısına (ör. Google Drive bağlantısı) koyup TV'deki
  *Downloader* uygulamasına adresi yazın.
- **ADB (ileri seviye):** TV'de Geliştirici seçenekleri → USB/Ağ hata ayıklamayı açın, sonra
  `adb connect TV_IP_ADRESI` ve `adb install MaxTV.apk`.

## Kumanda tuşları
| Ekran | Tuş | İşlev |
|---|---|---|
| Liste | OK | Kanalı aç |
| Liste | OK'a uzun bas veya MENU | Favorilere ekle/çıkar |
| İzlerken | ▲ / CH+ | Sonraki kanal |
| İzlerken | ▼ / CH- | Önceki kanal |
| İzlerken | OK / INFO | Kanal bilgisini göster/gizle |
| İzlerken | Rakamlar | O numaralı kanala atla |
| İzlerken | Geri | Listeye dön |

## Kod olmadan daha fazla kanal
- TV'de anten veya uydu girişi varsa **Ayarlar → Kanallar → Otomatik kanal arama** ile Türksat'tan
  yüzlerce ücretsiz kanal gelir (internet gerekmez).
- Resmî ücretsiz uygulamalar: **tabii / TRT İzle**, **Kanal D**, **Show TV**, **ATV**, **NOW**, **TV8** vb.
  kanalların kendi uygulamaları ve web sitelerinde canlı yayın vardır.

## Kendiniz derlemek isterseniz
Ubuntu/Debian'da: `sudo apt install openjdk-17-jdk aapt apksigner zipalign dalvik-exchange android-sdk-platform-23`
sonra bu klasörde `bash build.sh`. Gradle veya Android Studio gerekmez. `maxtv.keystore` dosyasını saklayın;
güncellemeleri eskisinin üzerine kurabilmek için aynı anahtarla imzalanmaları gerekir.
