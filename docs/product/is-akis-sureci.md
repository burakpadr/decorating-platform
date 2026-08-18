# İş Akış Süreci

*Otomatik teklif sistemi — uçtan uca süreç tanımı*

> Sürüm 1.0 · Ağustos 2026 · Bu doküman müşteri talebinin girişinden işin alınmasına kadar tüm süreci, rol dağılımını, karar noktalarını ve istisna akışlarını tanımlar.

## İçindekiler

1. [Yönetici Özeti](#1-yönetici-özeti)
2. [Roller ve Sorumluluklar](#2-roller-ve-sorumluluklar)
3. [Süreç Haritası](#3-süreç-haritası)
4. [Detaylı İş Akışı](#4-detaylı-iş-akışı)
5. [Müşteri Ne Görüyor](#5-müşteri-ne-görüyor)
6. [Günlük İş Akışınız](#6-günlük-iş-akışınız)
7. [Karar Noktaları](#7-karar-noktaları)
8. [İstisna ve Hata Akışları](#8-istisna-ve-hata-akışları)
9. [Bildirim Akışı](#9-bildirim-akışı)
10. [Zaman ve Kapasite](#10-zaman-ve-kapasite)
11. [Sorumluluk Matrisi](#11-sorumluluk-matrisi)
12. [Devreye Alma Hazırlıkları](#12-devreye-alma-hazırlıkları)

---

## 1. Yönetici Özeti

### Bugün nasıl çalışıyor

Fiyat teklifi vermek için eve gitmek zorunludur. Randevulaşma, yol, keşif, hesap ve teklifi iletme — teklif başına yaklaşık iki buçuk saat. Bu sürenin büyük kısmı, sonunda işe dönüşmeyecek müşteriler için harcanmaktadır. Günde verilebilecek teklif sayısı yolun izin verdiği kadardır.

|                                   | Mevcut süreç  | Yeni süreç     |
|-----------------------------------|---------------|----------------|
| **Teklif başına operatör zamanı** | **~2,5 saat** | **~90 saniye** |
| Randevu telefonu                  | 5 dk          | —              |
| Gidiş-dönüş yol                   | 60–90 dk      | —              |
| Yerinde keşif                     | 20–30 dk      | —              |
| Hesap ve teklif                   | 15 dk         | —              |
| Teklifi iletme                    | 5 dk          | —              |
| Panelde inceleme ve onay          | —             | 90 sn          |

### Ne değişiyor

Keşifler tamamen ortadan kalkmıyor. Sistem **basit işleri keşifsiz fiyatlandırıyor**, karmaşık ve riskli olanları ayıklayarak keşfi gerçekten gerekli olan yerlere yönlendiriyor.

Sizin süreçteki rolünüz "eve gitmek"ten "ekranda onaylamak"a dönüşüyor. Karar hâlâ sizde — sistem hiçbir teklifi siz görmeden müşteriye göndermiyor.

### Beklenen etki

100 talep senaryosu üzerinden (oranlar sistem çalıştıkça ölçülecek varsayımlardır):

|                          | Mevcut süreç  | Yeni süreç   |
|--------------------------|---------------|--------------|
| Keşifsiz fiyatlanan      | 0             | ~80 talep    |
| Keşif gereken            | 100 talep     | ~20 talep    |
| Panel başında geçen süre | —             | ~2,5 saat    |
| Keşif için harcanan süre | ~225 saat     | ~45 saat     |
| **Toplam**               | **~225 saat** | **~48 saat** |

> Kazanç iki yönlü. Birincisi doğrudan zaman tasarrufu. İkincisi ve belki daha önemlisi: **keşfe gittiğiniz müşteriler artık ön elemeden geçmiş oluyor.** Fotoğraf çekme zahmetine girmiş ve bir fiyat aralığı görmüş müşteri, telefonda fiyat sorup kapatan müşteriden çok daha ciddidir.

### Ek kazanç: talep hacmi görünür oluyor

Sistem, teklif almadan ayrılan ziyaretçileri de kaydediyor. Hangi ilçeden kaç talep geldiği, hangi büyüklükte evlerin sorulduğu, hangi aşamada vazgeçildiği — bugün hiç sahip olmadığınız veriler. Bu, ileride nereye ekip açacağınız veya hangi ilçeye reklam vereceğiniz kararlarının temeli olur.

## 2. Roller ve Sorumluluklar

| Rol | Sorumluluk |
|---|---|
| **[M] Müşteri** | Ev bilgilerini girer, oda listesini onaylar, fotoğrafları çeker, telefonunu doğrular, teklifi değerlendirir, kabul veya red kararı verir |
| **[O] Operatör** <br>*(siz veya ofis)* | Teklifleri inceler ve onaylar, gerektiğinde düzeltir, keşfe çevirir, kabul eden müşterileri arar, iş bitiminde gerçekleşen tutarı girer, fiyat listesini güncel tutar |
| **[S] Sistem** | Kaba fiyat hesaplar, oda listesi türetir, fotoğraf kalitesini kontrol eder, fotoğrafları analiz eder, metraj ve fiyat hesaplar, güven değerlendirir, bildirimleri gönderir, süreleri takip eder, verileri süresi gelince siler |

### Kritik ilke: karar sizde kalıyor

> Sistemin hesapladığı hiçbir teklif, siz onaylamadan müşteriye gitmez. Sistem işin hesabını yapar ve dikkatinizi çekmesi gereken noktaları işaretler; göndermek veya göndermemek kararı sizde.
>
> İleride, sistem yeterince doğru çalıştığı kanıtlandıktan sonra, basit ve yüksek güvenli işlerde otomatik gönderim açılabilir. Bu ayrı bir karardır ve ancak elinizde karşılaştırma verisi olduğunda verilir.

### Operatör kimliği

Süreç tek operatör varsayımıyla tasarlanmıştır. Panel mobilde çalışacak şekilde kurgulanmıştır — şantiyede, iki iş arasında, telefondan kullanılabilir. Masa başında oturmayı gerektirmez.

İleride ofisten bir kişi bu işi devralacaksa sistem buna hazırdır; ek kullanıcı tanımlamak yapısal bir değişiklik gerektirmez.

## 3. Süreç Haritası

```
       MÜŞTERİ                      SİSTEM                       OPERATÖR
       ───────                      ──────                       ────────

     0 Siteye girer
         │
     1 Ev bilgilerini    ──────►  Servis alanı kontrolü
       girer (8 soru)             Kaba aralık hesabı
         │                              │
         ◄────────────────────────  Aralık gösterilir
         │
         ├─ (vazgeçer) ─────────►  Talep kaydı tutulur
         │
     2 Oda listesini     ◄──────  Oda listesi türetilir
       onaylar
         │
       Fotoğrafları      ──────►  Anlık kalite kontrolü
       çeker (~28)                Arka planda yükleme
         │
     3 Telefonunu        ──────►  SMS kodu doğrulama
       doğrular
         │
         ◄────────────────────────  "Teklifin hazırlanıyor"
         │
     4                            Fotoğraf analizi
                                  Metraj ve fiyat hesabı
                                  Güven değerlendirmesi
                                        │
                         ┌──────────────┼──────────────┐
                         │              │              │
                     Fotoğraf       Yeterli        Riskli /
                     sorunlu                       düşük güven
                         │              │              │
         ◄─── Tekrar çekim isteği       ▼              ▼
                                  ┌─────────────────────────┐
     5                            │   ONAY KUYRUĞU          │──►  İnceler
                                  └─────────────────────────┘     (60-90 sn)
                                                                      │
                                                  ┌───────────────────┼──────────┐
                                                  ▼                   ▼          ▼
                                              Onayla            Düzelt      Keşfe çevir
                                                  │                   │          │
     6   ◄──── SMS: "Teklifin hazır"  ◄───────────┴───────────────────┘          │
         │                                                                       │
       Teklifi görür                                                             │
         │                                                                       │
     7 Kabul eder        ──────►  Aranacaklar listesi  ◄────────────────────────-─┘
         │                              │
         │                              └─────────────────────────►  Arar, tarihi
         │                                                          belirler
         ◄──────────────────────────────────────────────────────────────┘
         │
     8 İş yapılır                                                 Gerçekleşen
                                                                  tutarı girer
```

## 4. Detaylı İş Akışı

### Aşama 0 — Giriş

#### 0.1 · **[M]** Siteye giriş

Müşteri arama motorundan veya sosyal medyadan siteye gelir. Tek ekranda hizmetin ne olduğu anlatılır ve tek buton bulunur: "Ücretsiz fiyat al".

> **Kayıt gerekmez.** Telefon, e-posta, üyelik istenmez. · **Süre:** —

### Aşama 1 — Ön Hesaplama

Amaç: müşteriye hiçbir şey istemeden önce somut bir değer vermek. Bu aşama tamamen anonim ve ücretsizdir.

#### 1.1 · **[M]** Ev bilgileri

İlçe, evin kaç m² olduğu (brüt/net seçimiyle), oda sayısı (3+1 formatında), ve nerelerin boyanacağı.

> **Neden ilçe ilk soru:** servis alanı dışındaki ziyaretçi burada elenir, boşuna form doldurmaz. · **Süre:** ~20 sn

#### 1.2 · **[M]** İş kapsamı

Boya sırasında evde eşya olup olmayacağı, kapıların boyanıp boyanmayacağı, boyanacaksa kaç kapı olduğu ve kapılarda renk değişimi olup olmayacağı.

> **Dikkat:** "eşyalı mı" değil, "boya *günü* eşya olacak mı" diye sorulur. Müşteriler sıklıkla taşınmadan önce boyatır. · **Süre:** ~20 sn

#### 1.3 · **[M]** Duvar durumu

Dört seçenekli tek soru: duvarlar iyi mi, ufak tefek çatlak var mı, belirgin çatlak/dökülme var mı, yoksa emin değil mi.

> **"Alçı işi var mı" diye sorulmaz.** Müşterilerin çoğu macun ile saten alçının farkını bilmez, verdiği cevap iyimser tarafta olur. Gözle görülebilen bir şey sorulur. "Emin değilim" seçeneği bilinçli olarak vardır ve muhtemelen en çok seçilen olacaktır. · **Süre:** ~10 sn

#### 1.4 · **[S]** Kaba aralık hesabı

Sistem oda sayısından oda dağılımını, oradan duvar ve tavan metrajını hesaplar; beyan edilen duvar durumuna göre alçı miktarını tahmin eder ve fiyat listesinden bir aralık üretir.

> **Aralık genişliği sabit değil.** Müşteri ne kadar net bilgi verdiyse aralık o kadar daralır. "Emin değilim" seçilmişse belirgin biçimde genişler. · **Süre:** anında

#### 1.5 · **[M]** Sonuç ekranı ve karar

Aralık gösterilir, girilen bilgiler özetlenir, ve **aralığın neden geniş olduğu açıkça yazılır** ("duvar durumunu bilmediğimiz için"). Üç seçenek: kesin fiyat almak için devam etmek, aralığı SMS ile almak, veya ayrılmak.

> **Aralığın geniş olması kusur değil.** Dürüsttür ve daraltma isteği ikinci aşamanın satışını kendiliğinden yapar.

> **En büyük kayıp noktası burasıdır.** Aralığı gören ama devam etmeyen müşteri kaybedilir ve telefon numarası olmadığı için ulaşılamaz. Bu yüzden "aralığı SMS ile gönder" seçeneği göründüğünden çok daha önemlidir — bu aşamada numara bırakan müşteriye sonradan dönülebilir.

#### Masaüstünden gelen müşteri

Bilgisayardan giren müşteri "kesin fiyat al" dediğinde ekranda bir **QR kod** çıkar: telefonuyla okutur, kaldığı yerden devam eder. Uygulama indirmesi gerekmez, tarayıcıdan devam eder. Bilgisayar ekranı da "telefonundan devam ediyorsun" durumuna geçer.

### Aşama 2 — Fotoğraf Çekimi

Amaç: evin gerçek durumunu tespit etmek. Bu aşamaya ancak müşteri bir fiyat aralığı görüp devam etmeye karar verdikten sonra geçilir.

#### 2.1 · **[S]** Oda listesi türetilir

Aşama 1'deki oda sayısı ve kapsamdan çekilecek alanların listesi çıkarılır. 3+1 tüm ev için: salon, üç yatak odası, mutfak, banyo, koridor.

#### 2.2 · **[M]** Oda listesini onaylar

Liste müşteriye gösterilir, toplam fotoğraf sayısı ve tahmini süre yazılır. Müşteri alan ekleyip çıkarabilir — ikinci banyo, çalışma odası, giyinme odası, balkon için hazır butonlar bulunur.

> **Neden onaylatılıyor:** "3+1" dört oda demek, ama tüm ev boyanacaksa mutfak, banyo ve koridor da listeye girer. Müşterinin beklentisi baştan kurulmalı — ortada bırakılan çekim, baştan söylenmiş uzun listeden kötüdür.

#### 2.3 · **[M]** Çekim rehberi

Üç kural tek ekranda: ışıkları aç, fotoğraflarda kimse görünmesin, telefonu sabit tut. Ayrıca fotoğrafların ne için kullanılacağı ve ne kadar süre saklanacağı bilgisi burada verilir ve onayı alınır.

#### 2.4 · **[M]** Oda oda çekim

Listeden alan seçilir, istenen kareler çekilir, önizlenir, onaylanır. Alan tipine göre istenen kare sayısı değişir:

| Alan                              | İstenen kareler       | Adet |
|-----------------------------------|-----------------------|------|
| Salon, yatak odası, çalışma odası | 4 duvar + tavan       | 5    |
| Mutfak                            | 2 karşıt köşe + tavan | 3    |
| Banyo                             | 1 genel + tavan       | 2    |
| Koridor                           | 2 karşıt köşe + tavan | 3    |

> Mutfak ve banyoda az kare istenmesinin sebebi, bu alanlarda duvarların çoğunun fayans ve dolapla kaplı olması — boyanacak yüzey azdır. · **Süre:** 3+1 ev için ~8 dk, 28 fotoğraf

#### 2.5 · **[S]** Anlık kalite kontrolü

Her fotoğraf çekildiği anda telefonda kontrol edilir: bulanık mı, çok karanlık mı, çözünürlük yeterli mi. Sorunluysa müşteriye o an söylenir ve tekrar çekmesi istenir.

> **Neden önemli:** Videoda bunu ancak saatler sonra, analiz bittiğinde fark edersiniz. Fotoğrafta o an yakalanır ve müşteri hâlâ evin içindeyken düzeltilir.

#### 2.6 · **[M]** Sorunlu bölge yakın çekimi

Zorunlu kareler bittikten sonra her odada sorulur: "Bu odada çatlak, leke veya dökülme var mı? Varsa yakından çek." Sınırsız ve atlanabilir.

> Analiz açısından muhtemelen en değerli kareler bunlar olacak — geniş açı fotoğrafta ince çatlak görünmez.

#### 2.7 · **[S]** Arka planda yükleme

Her fotoğraf onaylandığı an yüklenmeye başlar; müşteri sonraki odaya geçerken yükleme devam eder.

> **Neden sona bırakılmıyor:** 28 fotoğrafın sonda tek seferde yüklenmesi, kopan bağlantıda tüm emeğin kaybı demek. O müşteri geri gelmez.

### Aşama 3 — Kimlik Doğrulama

#### 3.1 · **[M]** Telefon numarası ve SMS kodu

Numara girilir, gelen kod doğrulanır.

> **Neden en sonda:** Baştan numara isteyen sistemler ziyaretçinin yarısını kaybeder. Bu noktada müşteri zaten 8 dakika emek harcamıştır, bırakmaz. · **İnce detay:** doğrulama adımı, fotoğraf yüklemesi arka planda devam ederken gösterilir — ölü bekleme süresi doldurulmuş olur. · **Süre:** ~30 sn

#### 3.2 · **[S]** Bekleme ekranı

Müşteriye teklifin ne zaman hazır olacağı söylenir ve ekrandan çıkabileceği belirtilir. Hazır olduğunda SMS gelecektir.

> **Söylenen süre çalışma saatlerine göre hesaplanır.** Gece 23:00'te gelen talebe "2 saat içinde" demek yalan olur; "yarın sabah 10:00'a kadar" denir.

### Aşama 4 — Analiz <span style="font-weight:normal;font-size:9.5pt;color:#777">(müşteriye görünmez)</span>

#### 4.1 · **[S]** Fotoğraf analizi

Her oda kendi fotoğraflarıyla birlikte ayrı ayrı analiz edilir. Tespit edilen konular:

| Tespit                                  | Fiyata etkisi                            |
|-----------------------------------------|------------------------------------------|
| Yüzey kaplaması (boya / fayans / ahşap) | Boyanmayan alan metrajdan düşülür        |
| Mevcut renk tonu                        | Koyudan açığa geçişte üçüncü kat gerekir |
| Macun ihtiyacı (kısmi tamir)            | Ayrı kalem, duvar başına oran            |
| Saten alçı ihtiyacı (tüm yüzey)         | Ayrı ve pahalı kalem                     |
| Çatlak seviyesi                         | Yapısal ise keşif zorunlu                |
| Nem ve küf                              | Aktifse izolasyon astarı + keşif         |
| Duvar kağıdı                            | Söküm kalemi                             |
| Kartonpiyer ve spot sayısı              | Kesim işçiliği                           |
| Eşya durumu                             | İşçilik farkı                            |
| Kapı, pencere, petek sayısı             | Metraj düşümü ve ek kalemler             |

> **Süre:** odalar paralel işlenir, toplam birkaç dakika

> **Sistem fiyat hesaplamıyor, durum tespiti yapıyor.** Fotoğraf analizi yalnız "bu duvarda ne var" sorusuna cevap verir. Parayı, sabit kurallarla çalışan ayrı bir hesap motoru hesaplar. Bu ayrım, aynı eve iki kez aynı fiyatın çıkmasını ve bir rakamın nereden geldiğinin her zaman izlenebilmesini garanti eder.

#### 4.2 · **[S]** Metraj ve fiyat hesabı

Tespitler ve beyan edilen m² birleştirilerek duvar/tavan metrajı, kalem miktarları, işçilik süresi ve toplam fiyat hesaplanır. Ayrıca işin kaç gün süreceği çıkarılır.

#### 4.3 · **[S]** Güven değerlendirmesi

Sistem kendi tespitlerine ne kadar güvendiğini ölçer ve üç sonuçtan birine karar verir:

| Sonuç         | Koşul                                       | Ne olur                           |
|---------------|---------------------------------------------|-----------------------------------|
| Onaya hazır   | Güven yeterli, riskli bulgu yok             | Onay kuyruğuna düşer              |
| Tekrar çekim  | Belirli kareler kullanılamaz                | Müşteriye o kare için istek gider |
| Keşif gerekli | Düşük güven, yüksek tutar veya riskli bulgu | Kuyruğa keşif işaretiyle düşer    |

> **Riskli bulgular:** aktif nem, yapısal çatlak, duvarların %40'ından fazlasında saten alçı, eksik alan. Bunlarda güven yüksek olsa da gidilmesi gerekir — sorun görünenin ardındadır.

### Aşama 5 — Onay

#### 5.1 · **[O]** Talebi inceler

Panelde tek ekran. Sırayla: ev özeti, teklif tutarı ve güven yüzdesi, **uyarı bayrakları**, kalem listesi, ve katlanmış halde fotoğraf bölümü.

> **Bayraklar en üstte, fotoğraflar en altta.** Her talebi 28 fotoğraf tek tek gözden geçirerek incelemeniz gerekirse sistem zaman kazandırmıyor demektir. Bayrak yoksa fotoğraf bölümüne hiç dokunmadan onaylayabilirsiniz. · **Hedef süre:** 60–90 sn

#### 5.2 · **[O]** Karar verir

| Aksiyon          | Sonuç                                                     |
|------------------|-----------------------------------------------------------|
| Onayla ve gönder | Teklif müşteriye gider, geçerlilik süresi işlemeye başlar |
| Düzelt ve gönder | Kalem veya miktar değiştirilir, sonra gider               |
| Keşfe çevir      | Otomatik teklif verilmez, aranacaklar listesine düşer     |
| İptal et         | Spam, alakasız fotoğraf, servis dışı adres                |

> Ekranda tutarın hemen altında **maliyet ve kâr marjı** görünür. Bu bilgi müşteriye asla gitmez. Marj hedefin altına düştüğünde uyarı basılır — bu genelde minimum tutarın devreye girmesinden veya bölgenin uzak olmasından kaynaklanır.

> **Yaptığınız her düzeltme kaydediliyor.** Hangi kalemi ne kadar değiştirdiğiniz, hangi tespite bağlı olduğu — hepsi tutulur. Birkaç ay sonra şöyle bir cümle çıkarılabilir hale gelir: *"Saten alçı miktarını son 40 talebin 12'sinde düşürdün, ortalama %35."* Sistemin zamanla isabetli hale gelmesi bu kayıtlar sayesinde olur.

### Aşama 6 — Teklifin İletilmesi

#### 6.1 · **[S]** SMS gönderilir

Müşteriye "teklifin hazır" mesajı ve link gider.

> **SMS'te tutar yazılmaz.** Rakamın kalem kırılımıyla birlikte görülmesi gerekir. SMS'te çıplak sayı gören müşteri, bağlamsız değerlendirip teklifi hiç açmadan eleyebilir.

#### 6.2 · **[M]** Teklifi görür

Numarasıyla giriş yapar ve teklif ekranını açar. Ekranda dört şey vardır:

- **Kalem bazlı kırılım** — "duvar boyası 34.900, saten alçı 9.800, kapılar 5.400". Güveni yaratan asıl şey budur; tek rakam pazarlık davet eder, kırılım açıklama yapar.
- **Tahmini süre** — "2 iş günü". İnsanlar fiyattan çok evlerinin ne kadar işgal edileceğini merak eder.
- **Geçerlilik tarihi**
- **Koşullar** — eşya farkı ve revizyon şartı

> Ayrıca "sorum var" bağlantısı WhatsApp'a yönlendirir.

### Aşama 7 — Sonuç

#### 7.1 · **[M]** Karar verir

Kabul eder, soru sorar, reddeder veya sessiz kalır. Kabul ederse tek soru daha sorulur: "Sizi ne zaman arayalım? Sabah / Öğleden sonra / Akşam".

> Buton "randevu al" değil "teklifi kabul et" der — sistemde takvim olmadığı için veremeyeceğimiz bir şey vaat edilmez. Altında yazar: "Bir iş günü içinde sizi arayıp başlangıç tarihini birlikte belirleyeceğiz."

#### 7.2 · **[O]** Müşteriyi arar

Panelde **Aranacaklar** listesinde görünür: müşteri, ilçe, tutar, kabul zamanı, kaç saattir beklediği ve tercih ettiği arama saati. Arama sonrası tek dokunuşla işaretlenir: iş alındı / düşünüyor / ulaşılamadı / vazgeçti.

> Tarih ve detaylar telefonda konuşulur. Sistem randevu yönetmez — boya işlerinde takvim hava durumuna, ekip müsaitliğine ve işlerin uzamasına bağlı olarak sürekli değişir, bunu yazılıma sıkıştırmak fayda yerine sorun üretir.

> **Bu listenin gecikmesi doğrudan iş kaybıdır.** Takvim olmadığı için hiçbir mekanizma sizi kendiliğinden hatırlatmaz. Bu yüzden bekleme süresi eşiği geçtiğinde satır işaretlenir ve size bildirim gider. Kuyrukta en üstte durur — teklif onaylamaktan daha aceledir, çünkü müşteri kararını vermiştir. İki gün aranmamış bir kabul, rakibe gitmiş bir iştir.

### Aşama 8 — İş Sonrası

#### 8.1 · **[O]** Gerçekleşen tutarı girer

İş bittiğinde kısa bir form: gerçekleşen tutar, kaç gün sürdü, fiilen ne yapıldı.

> **30 saniyeden uzun sürmemeli**, aksi halde doldurulmaz.

> Bu adım ürünün parçası gibi görünmez ama sistemin zamanla düzelmesinin tek yoludur. Tahmin ile gerçekleşen arasındaki fark ölçülmezse, katsayıların doğru olup olmadığı asla bilinemez. 20-30 iş sonrasında elinizde şu soruların cevabı olur: sistem hangi kalemde fazla, hangisinde eksik tahmin ediyor? Hangi ev tipinde isabetli?

## 5. Müşteri Ne Görüyor

Süreci müşteri tarafından, ekran ekran özet.

| \#  | Ekran             | İçerik                                         | Süre  |
|-----|-------------------|------------------------------------------------|-------|
| 1   | Karşılama         | Hizmet tanıtımı, tek buton                     | —     |
| 2   | Ev bilgileri      | İlçe, m², oda sayısı, boyanacak yerler         | 20 sn |
| 3   | İş kapsamı        | Eşya, kapılar, renk değişimi                   | 20 sn |
| 4   | Duvar durumu      | Dört seçenekli tek soru                        | 10 sn |
| 5   | **Fiyat aralığı** | Aralık, özet, neden geniş olduğu, devam butonu | —     |
| 6   | Oda listesi onayı | Çekilecek alanlar, toplam süre, ekle/çıkar     | 15 sn |
| 7   | Çekim rehberi     | Üç kural, veri kullanım onayı                  | 15 sn |
| 8   | Çekim ekranı      | Oda listesi, kare kare çekim, önizleme         | 8 dk  |
| 9   | Telefon doğrulama | Numara + SMS kodu                              | 30 sn |
| 10  | Bekleme           | Teklifin ne zaman hazır olacağı                | —     |
| 11  | *SMS*             | Teklifin hazır + link                          | —     |
| 12  | **Teklif ekranı** | Kalem kırılımı, süre, geçerlilik, koşullar     | —     |
| 13  | Kabul             | Kabul butonu + arama saati tercihi             | 10 sn |

**Müşterinin aktif olarak harcadığı toplam süre: yaklaşık 10 dakika.** Bunun 8 dakikası fotoğraf çekimidir ve ancak müşteri bir fiyat aralığı gördükten sonra başlar.

### Müşteri neden bu süreci tamamlar

Süreç, karşılığını almadan hiçbir şey istememek üzerine kurulmuştur:

| Adım    | Müşteri ne veriyor    | Karşılığında ne alıyor        |
|---------|-----------------------|-------------------------------|
| Aşama 1 | 1 dakika, 8 cevap     | Somut fiyat aralığı           |
| Aşama 2 | 8 dakika, 28 fotoğraf | Kesin, kalem kırılımlı teklif |
| Aşama 3 | Telefon numarası      | Teklife erişim                |

Sıra tersine çevrilse — girişte fotoğraf ve numara istenirse — ziyaretçilerin büyük kısmı ilk ekranda ayrılır.

## 6. Günlük İş Akışınız

Operatör olarak sistemle kurduğunuz temas noktaları.

### Gün içinde

| Ne zaman                        | Ne yapılır                                            | Süre     |
|---------------------------------|-------------------------------------------------------|----------|
| Yeni talep bildirimi geldiğinde | Paneli aç, incele, onayla veya düzelt                 | 60–90 sn |
| Kabul bildirimi geldiğinde      | Aranacaklar listesinden müşteriyi ara, tarihi belirle | 5 dk     |
| Keşif işaretli talep geldiğinde | Müşteriyi ara, keşif için gün belirle                 | 5 dk     |

### Günlük rutin

- **Sabah:** gece gelen bekleyen teklifleri gözden geçir. Panelde rozetli sayı kaç bekleyen olduğunu gösterir.
- **Gün içi:** bildirim geldiğinde araya sıkıştır. Panel mobil çalıştığı için şantiyede, arabada, iki iş arasında yapılabilir.
- **Akşam:** aranacaklar listesini kontrol et, gecikmiş olan var mı bak.

### Dönemsel işler

| Ne zaman                           | Ne yapılır               | Neden                          |
|------------------------------------|--------------------------|--------------------------------|
| İş bittiğinde                      | Gerçekleşen tutarı gir   | Sistemin isabetinin ölçülmesi  |
| Malzeme veya işçilik zamlandığında | Fiyat listesini güncelle | Tekliflerin güncel kalması     |
| Ayda bir                           | Düzeltme raporuna bak    | Sistem hangi kalemde yanılıyor |

### Fiyat listesi güncelleme

Enflasyon ortamında bu iş üç ayda bir gelecektir. Panelde **toplu zam** aksiyonu vardır: "tüm işçilik kalemlerine %15" tek işlemle uygulanır ve yeni bir liste versiyonu üretir.

> **Eski teklifler değişmez.** Fiyat listesi güncellendiğinde daha önce gönderilmiş teklifler etkilenmez; her teklif hangi liste versiyonuyla hesaplandığını kendi içinde taşır. Müşteri iki hafta önceki teklifle geldiğinde ne konuştuğunuz her zaman izlenebilir.

İşçilik ve malzeme ayrı tutulur, çünkü farklı ritimlerde değişirler. Boya fiyatı arttığında yalnız malzemeye zam yapılabilir.

### Sistemin sizden istemediği şeyler

Süreçte bilinçli olarak size yük bindirilmeyen noktalar:

- Müşteriyle randevulaşmak için telefon trafiği — müşteri kendi başlatır
- Metraj hesaplamak — sistem hesaplar, siz kontrol edersiniz
- Teklifi yazmak ve iletmek — otomatik
- Hatırlatma takibi — sistem geçerlilik hatırlatmasını kendisi gönderir
- Kötü fotoğraflarla uğraşmak — sistem müşteriden kendisi tekrar ister
- Veri silme takibi — süresi gelen fotoğraflar otomatik silinir

## 7. Karar Noktaları

Süreçte akışın dallandığı yerler ve kararı kimin verdiği.

| \#  | Karar                         | Veren                               | Sonuçlar                                       |
|-----|-------------------------------|-------------------------------------|------------------------------------------------|
| 1   | İlçe servis alanında mı       | Sistem                              | Değilse akış biter, bekleme listesi önerilir   |
| 2   | Kesin fiyat istenecek mi      | Müşteri                             | İstemezse talep kaydı kalır, SMS bırakabilir   |
| 3   | Fotoğraf kalitesi yeterli mi  | Sistem                              | Değilse o an tekrar çekim istenir              |
| 4   | Analiz güveni yeterli mi      | Sistem                              | Yeterli → onay kuyruğu, değil → keşif          |
| 5   | Riskli bulgu var mı           | Sistem                              | Varsa güven yüksek olsa da keşif               |
| 6   | Beyan ile tespit çelişiyor mu | Sistem                              | Kapı sayısı → bayrak; eşya → müşteriye sorulur |
| 7   | **Teklif gönderilecek mi**    | **Operatör**                        | Onayla / düzelt / keşfe çevir / iptal          |
| 8   | Marj yeterli mi               | Sistem uyarır, operatör karar verir | Düşükse bayrak basılır                         |
| 9   | Teklif kabul edilecek mi      | Müşteri                             | Kabul / soru / red / sessizlik                 |
| 10  | İş ne zaman başlayacak        | Operatör ve müşteri, telefonda      | Sistem dışında                                 |

### Beyan ile tespit çeliştiğinde

İki farklı çelişki türü vardır ve farklı ele alınırlar.

#### Kapı sayısı

Müşteri 8 dedi, fotoğraflarda 6 görünüyor. İkisi de *aynı anı* gözlemliyor, biri yanılıyor. Sistem tespitini müşterinin beyanının üzerine yazmaz — operatöre bayrak basar, karar operatöre kalır. Müşteri dolap kapağını saymış olabilir, model de kadraj dışında kalan kapıyı kaçırmış olabilir.

#### Eşya durumu

Müşteri "boş olacak" dedi, fotoğraflarda eşya var. Bu farklı bir durum: fotoğraf evin *bugününü* gösteriyor, boya *ileride* yapılacak. Sistem gelecekteki durumu göremez. En yaygın senaryo, taşınmadan önce teklif alan kiracıdır.

Bu yüzden sistem beyanı ezmez, müşteriye tek soru sorar: *"Fotoğraflarda eşya görünüyor. Boya günü ev boş olacak mı?"*

Kötüye kullanım riski teklife koşul yazarak karşılanır: *"Bu fiyat boş ev içindir. Boya günü evde eşya bulunması halinde işçilik farkı uygulanır."*

## 8. İstisna ve Hata Akışları

Ters gidebilecek şeyler ve sistemin her birine verdiği cevap.

### Müşteri kaynaklı

| Durum                                     | Sistemin davranışı                                                                                                                                                   |
|-------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Fotoğraf bulanık veya karanlık            | Telefonda o an uyarı, tekrar çekim istenir. Aynı kare üçüncü kez reddedilirse kabul edilir ve düşük kalite işaretlenir — sonsuz döngüde sıkışan müşteri geri gelmez. |
| Yanlış kare çekilmiş (tavan yerine yer)   | Telefonda yakalanmaz, analizde anlaşılır. Müşteriye o kare için tek tekrar isteği gider; SMS'teki link doğrudan o karenin çekim ekranını açar.                       |
| Çekimi yarıda bırakır                     | Taslak saklanır, kaldığı yerden devam edebilir. Aşama 1'de SMS bırakmışsa hatırlatma gönderilebilir.                                                                 |
| Yanlış m² girer                           | Operatör panelde hesaplanan metrajı görür; ev tipine göre bariz uyumsuzluk varsa düzeltir.                                                                           |
| Brüt m² gireceğine net girer (veya tersi) | Formda brüt/net seçimi zorunlu tutularak baştan engellenir.                                                                                                          |
| Sadece iyi durumdaki odaları çeker        | Zorunlu kare listesi tüm odaları kapsar; eksik alan varsa analiz bunu bildirir ve keşif önerilir.                                                                    |
| Aynı numarayla tekrar tekrar dener        | Numara başına günlük ve aylık analiz kotası. Aşan talep reddedilmez, operatöre işaretli düşer.                                                                       |
| Teklife hiç cevap vermez                  | Geçerlilik bitmeden hatırlatma, sonra teklif kapanır. Yeni talep açabilir.                                                                                           |
| Servis dışı ilçeden gelir                 | İlk soruda elenir; isterse "açılınca haber verin" listesine kaydolur.                                                                                                |

### Sistem kaynaklı

| Durum                                      | Sistemin davranışı                                                                                                                  |
|--------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| Analiz yanlış tespit eder                  | Operatör panelde düzeltir. Düzeltme kaydedilir ve kalibrasyon verisine eklenir.                                                     |
| Bir odanın analizi başarısız olur          | Yalnız o oda tekrar denenir, tüm analiz değil. Üç denemeden sonra operatöre işaretlenir.                                            |
| Fiyat gerçekçi olmayacak kadar düşük çıkar | Marj uyarısı basılır. Minimum iş tutarı zaten bir taban koyar — küçük işler, büyük işin oranı olarak fiyatlanmaz.                   |
| Analiz emin olamaz                         | Fiyat kaydırılmaz, **aralık genişletilir**. Boyada sürprizler tek yönlü gelir; ortalamaya çekmek sistematik eksik tahmine yol açar. |
| SMS gönderimi başarısız olur               | Tekrar denenir, kayıt tutulur. Kalıcı hatada operatöre bildirilir.                                                                  |
| Yükleme kopar                              | Fotoğraflar tek tek yüklendiği için yalnız o kare kaybolur, tekrar çekilir.                                                         |

### Operasyon kaynaklı

| Durum                                | Sistemin davranışı                                                                                                                                                |
|--------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Teklif uzun süre onaylanmaz          | Kuyrukta bekleme süresi görünür, en eski önce sıralanır. Müşteriye söylenen süre çalışma saatlerine göre hesaplandığı için gece gelen talep sabah SLA'sına düşer. |
| Kabul eden müşteri aranmaz           | Bekleme süresi eşiği geçtiğinde satır işaretlenir, operatöre bildirim gider, kuyrukta en üste çıkar.                                                              |
| Fiyat listesi güncellenmeyi unutulur | Marj uyarıları çoğalarak bunu görünür kılar. Liste versiyonunun yaşı panelde görünür.                                                                             |
| Müşteri "verilerimi silin" der       | Panelde silme talebi olarak açılır, operatör onayına düşer. Onay ekranı neyin silineceğini ve yasal olarak neyin kalacağını ayrı ayrı gösterir.                   |

## 9. Bildirim Akışı

Kim, ne zaman, ne alıyor.

### Müşteriye giden

| Tetikleyen olay        | İçerik                                       |
|------------------------|----------------------------------------------|
| Aralığı SMS ile istedi | Fiyat aralığı + devam linki                  |
| Teklif onaylandı       | "Teklifin hazır" + link. **Tutar yazılmaz.** |
| Fotoğraf yetersiz      | Hangi kare olduğu + doğrudan o karenin linki |
| Keşif gerekli          | Sebep + "sizi arayacağız"                    |
| Geçerlilik yaklaşıyor  | Bitiş tarihi + link                          |
| Teklif süresi doldu    | Yeni talep linki                             |
| Teklifi kabul etti     | Arama sözü + tercih ettiği saat teyidi       |

### Operatöre giden

| Tetikleyen olay                | İçerik                                    |
|--------------------------------|-------------------------------------------|
| Yeni talep onaya düştü         | İlçe, ev özeti, tahmini tutar, link       |
| Müşteri teklifi kabul etti     | Müşteri, tutar, tercih ettiği arama saati |
| Aranacaklar listesinde gecikme | Kaç saattir beklediği                     |
| Silme talebi geldi             | Panelde kapatılamaz uyarı                 |

Operatör bildirimleri SMS veya WhatsApp ile gider — uygulama bildirimi güvenilmez, çünkü panel sürekli açık tutulmaz.

### SMS metinleri kısa tutulmalı

Teknik ama maliyeti doğrudan etkileyen bir detay: içinde ç, ğ, ı, ö, ş, ü geçen SMS'ler farklı kodlanır ve mesaj başına 160 yerine 70 karakter sığar. Uzun mesajlar üç kat faturaya dönüşür. Metinler bu yüzden kısa yazılmıştır.

## 10. Zaman ve Kapasite

### Teklif başına süre karşılaştırması

| Adım                         | Mevcut        | Yeni        |
|------------------------------|---------------|-------------|
| Randevulaşma                 | 5 dk          | —           |
| Yol (gidiş-dönüş)            | 60–90 dk      | —           |
| Yerinde keşif                | 20–30 dk      | —           |
| Hesap ve teklif hazırlama    | 15 dk         | —           |
| Panelde inceleme ve onay     | —             | 1,5 dk      |
| Teklifi iletme               | 5 dk          | —           |
| **Toplam (operatör zamanı)** | **~2,5 saat** | **~1,5 dk** |

Kabul edilen tekliflerde ayrıca ~5 dakikalık bir arama vardır.

### 100 talep senaryosu

Aşağıdaki oranlar tahmindir ve sistem çalışmaya başladıktan sonra ölçülecektir. Yapıyı göstermek için verilmiştir.

|                                        | Adet | Operatör zamanı |
|----------------------------------------|------|-----------------|
| Panelde onaylanan (keşifsiz)           | ~80  | 2,0 saat        |
| Bunlardan kabul edilen (%25 varsayımı) | ~20  | 1,7 saat        |
| Keşfe düşen                            | ~20  | 45 saat         |
| **Yeni süreç toplamı**                 |      | **~48 saat**    |
| Mevcut süreçte aynı 100 talep          | 100  | ~225 saat       |

> Kritik nokta şu: **mevcut süreçte 100 talebe cevap vermek fiziken mümkün değil.** 225 saat, tek kişi için yaklaşık altı haftalık tam zamanlı çalışma demek. Bugün bu talepler ya reddediliyor ya da telefonda kaba rakam verilerek geçiştiriliyor. Sistemin asıl getirdiği şey zaman tasarrufundan çok **kapasite artışı**.

### Keşiflerin niteliği değişiyor

Yeni süreçte keşfe gittiğiniz 20 müşteri, mevcut süreçtekilerden farklı bir gruptur:

- Formu doldurmuş, 28 fotoğraf çekmiş, telefonunu doğrulamıştır — ciddiyeti kanıtlanmıştır
- Bir fiyat aralığı görmüş ve devam etmeyi seçmiştir — bütçesi uyuşmuyorsa çoktan ayrılmıştır
- Fotoğrafları elinizde olduğu için keşfe hazırlıklı gidersiniz

Yani keşif başına dönüşüm oranı da yükselmesi beklenir. Bu, doğrulanması gereken bir varsayımdır; sistem `iş alındı / vazgeçti` kayıtlarını tuttuğu için ölçülebilir olacaktır.

### Ölçülecek göstergeler

| Gösterge                        | Neden önemli               |
|---------------------------------|----------------------------|
| Aşama 1 tamamlama oranı         | Form çok mu uzun           |
| Aşama 1 → Aşama 2 geçiş oranı   | Aralık ikna edici mi       |
| Fotoğraf çekimi tamamlama oranı | 28 fotoğraf çok mu         |
| Keşif oranı                     | Sistem ne kadar iş çözüyor |
| Teklif kabul oranı              | Fiyatlar piyasaya uygun mu |
| Operatör düzeltme oranı         | Sistem ne kadar isabetli   |
| Tahmin / gerçekleşen sapması    | Katsayılar doğru mu        |

## 11. Sorumluluk Matrisi

| Süreç adımı                   | Müşteri        | Operatör        | Sistem      |
|-------------------------------|----------------|-----------------|-------------|
| Ev bilgilerinin girilmesi     | Yapar          | —               | Doğrular    |
| Servis alanı kontrolü         | —              | —               | Yapar       |
| Kaba fiyat hesabı             | —              | —               | Yapar       |
| Oda listesinin belirlenmesi   | Onaylar        | —               | Türetir     |
| Fotoğraf çekimi               | Yapar          | —               | Yönlendirir |
| Fotoğraf kalite kontrolü      | —              | —               | Yapar       |
| Durum tespiti                 | —              | Denetler        | Yapar       |
| Metraj hesabı                 | —              | Denetler        | Yapar       |
| Fiyat hesabı                  | —              | Denetler        | Yapar       |
| **Teklifin gönderilmesi**     | —              | **Karar verir** | Uygular     |
| Keşif kararı                  | —              | Onaylar         | Önerir      |
| Teklifin değerlendirilmesi    | Yapar          | —               | Hatırlatır  |
| Başlangıç tarihi belirlenmesi | Katılır        | Yapar           | —           |
| İşin yürütülmesi              | —              | Yapar           | —           |
| Gerçekleşen tutarın kaydı     | —              | Yapar           | Saklar      |
| Fiyat listesi güncelliği      | —              | Yapar           | Uyarır      |
| Veri silme                    | Talep edebilir | Onaylar         | Uygular     |

### Süreçteki tek insan kontrol noktası

> Tabloda dikkat edilmesi gereken satır **"teklifin gönderilmesi"**. Sistemin bütün otomasyonu bu noktada bir insan onayına bağlanır. Bu, bilinçli bir tasarım kararıdır ve sistemin kalite güvencesinin tamamıdır.
>
> Bu yüzden panelde toplu onay özelliği **yoktur**. Tek tıkla yirmi teklif göndermek cazip görünür ama bu tek kontrol noktasını ortadan kaldırır.

## 12. Devreye Alma Hazırlıkları

Sistem çalışmaya başlamadan önce tamamlanması gereken işler. Bunların çoğu yazılım geliştirmeden bağımsızdır ve paralel yürütülebilir.

### Öncelikli: fiyat verisinin çıkarılması

> Bu adım atlanamaz. Sistemin fiyat hesabı, işletmenin gerçek maliyetlerine dayanmak zorundadır. Doküman içindeki rakamlar piyasa araştırmasına dayalı **başlangıç değerleridir**, gerçek değil.

Gereken iki şey:

1.  **Kalem bazlı maliyet listesi.** Duvar boyası, tavan, macun, saten alçı, kapı, pervaz, petek gibi her kalem için metrekare veya adet başına işçilik ve malzeme maliyeti. Ayrıca her kalemin ne kadar sürdüğü.
2.  **Son 50 işin kaydı.** Ev tipi, m², yapılan işler, verilen fiyat, gerçekleşen maliyet. Bu veri hem fiyat listesinin doğrulanmasını sağlar hem de katsayıların ilk kalibrasyonunu mümkün kılar.

> Bu iki kalemin hangi kolonlarla toplanacağı ve kaydın nereye gireceği: `son-50-is-kaydi.md`.

### İşletmeden gelmesi gerekenler

| Konu                                       | Not                                                |
|--------------------------------------------|----------------------------------------------------|
| Kalem bazlı işçilik ve malzeme maliyetleri | Sistemin temeli                                    |
| Hedef kâr marjı                            | Ve altına düşünce uyarı verilecek eşik             |
| Ekip büyüklüğü ve günlük ekip maliyeti     | Süre ve minimum tutar hesabı için                  |
| Minimum iş tutarı                          | Küçük işlerde taban                                |
| İlçe bazlı fiyat farkları                  | Başlangıçta hepsi eşit alınabilir, sonra ayarlanır |
| Teklif geçerlilik süresi                   | Kaç gün                                            |
| Çalışma saatleri                           | Müşteriye söylenecek süre hesabı için              |
| SMS sağlayıcı seçimi ve başlık tescili     | **Erken başlanmalı** — tescil süreci zaman alır    |

### Danışmanlardan gelmesi gerekenler

| Konu                                                | Kimden          |
|-----------------------------------------------------|-----------------|
| KDV oranları ve fatura düzeni                       | Mali müşavir    |
| Aydınlatma metni ve veri onay metinleri             | Hukuk danışmanı |
| Teklif koşulları metni (eşya farkı, revizyon şartı) | Hukuk danışmanı |
| SMS'lerin bilgilendirme / ticari ileti ayrımı       | Hukuk danışmanı |

### Devreye alma sırası

Sistem tek seferde değil, dört aşamada devreye alınabilir. Her aşama tek başına değerlidir ve sıradaki aşama beklenmeden kullanılmaya başlanabilir.

| Sıra | Ne devreye girer                                                                                            | Kazanç                                                                                         |
|------|-------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| 1    | Fiyat hesap motoru ve fiyat listesi yönetimi <span style="color:#777">(iç araç, müşteri arayüzü yok)</span> | Bilgileri kendiniz girip teklif hesaplatabilirsiniz. Hesabın doğruluğu ilk günden test edilir. |
| 2    | Aşama 1 ve ilçe sayfaları                                                                                   | Site yayına girer, talep hacmi ve trafik verisi akmaya başlar. Fotoğraf ve analiz henüz yok.   |
| 3    | Aşama 2, analiz ve operatör paneli                                                                          | Keşifsiz teklif verilmeye başlanır. Sistemin ana faydası burada devreye girer.                 |
| 4    | Bildirimler, aranacaklar, otomatik silme                                                                    | Elle takip gereken işler otomatikleşir.                                                        |

> Bu sıra bilinçlidir. En riskli varsayım — fiyat hesabının işletmenin gerçek rakamlarını üretip üretmediği — ilk adımda, hiç müşteri arayüzü yazılmadan test edilir. Yanlışsa en ucuz noktada düzeltilir.

### İlk aylarda beklenen

Sistem ilk günden mükemmel çalışmaz. Beklenmesi gereken:

- İlk haftalarda düzeltme oranı yüksek olacaktır — bu bir arıza değil, kalibrasyonun kendisidir
- Keşif oranı başlangıçta beklenenden yüksek çıkabilir; güven eşikleri temkinli ayarlanmıştır
- 20-30 tamamlanmış işten sonra katsayılar gerçek veriyle güncellenir ve isabet belirgin şekilde artar
- Otomatik gönderim, ancak bu kalibrasyon tamamlandıktan sonra değerlendirilir

---

Bu doküman iş akış süreçlerini tanımlar. Ürün ve tasarım kararlarının gerekçeleri için `v1-tasarim-dokumani.pdf`, teknik implementasyon detayları için `../engineering/implementation-spec.md` dokümanına bakınız.
