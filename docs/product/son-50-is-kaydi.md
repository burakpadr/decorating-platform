# Son 50 İşin Kaydı

> İş kalemi **BOYA-2**, Devreye Alma Önkoşulları. `is-akis-sureci.md` §12'nin ikinci yarısı.

Bu doküman, işletmenin dolduracağı iki dosyayı ve her kolonun ne anlama geldiğini tanımlar.
Şablonlar `api/src/main/resources/calibration/` altında; teknik tarafı aynı dizindeki `README.md`
anlatır.

## Neden bu veri

§12'nin ifadesiyle:

> **Son 50 işin kaydı.** Ev tipi, m², yapılan işler, verilen fiyat, gerçekleşen maliyet. Bu veri hem
> fiyat listesinin doğrulanmasını sağlar hem de katsayıların ilk kalibrasyonunu mümkün kılar.

Sistemdeki fiyat listesi (`REAL-2026-01`) şu an **piyasa araştırmasına dayalı** rakamlar taşıyor —
BOYA-1 kapatıldı ama rakamlar işletmenin defterinden geçmedi. Bu 50 kayıt, o listenin bu işletmenin
gerçek maliyetlerini üretip üretmediğini gösterecek tek kanıt. Kayıt gelmeden fiyat motorunun
ürettiği hiçbir rakam doğrulanmış sayılamaz.

## Üç kural

1. **Kişisel veri yazılmaz.** İsim, telefon, adres, kapı numarası yok — sadece ilçe. Bu kayıtlar
   silinmiyor: sonraki her kalibrasyon bu veriye göre ölçülecek. Kalıcı olarak tutulabilmesinin tek
   şartı, kişi hakkında değil iş hakkında olması. Şemada isim/telefon/adres kolonu yok ve
   eklenirse test derlemeyi kırar.
2. **Bütün para alanları KDV hariç.** İşçilik ve malzeme KDV oranları henüz belli değil (BOYA-3,
   mali müşavir), dolayısıyla KDV dahil bir tutar burada KDV'sine ayrıştırılamaz. Faturayı kesen
   işletme hangi oranı uyguladığını biliyor; çıkarma işlemi bir kez, veriyi girerken yapılır.
3. **Maliyet, satış fiyatı değil.** `actual_cost` işin işletmeye kaça mal olduğu; `quoted_total_ex_vat`
   müşteriye verilen fiyat. İkisi karışırsa kâr marjı iki kez sayılır ve fiyat listesi olduğundan
   pahalı görünür.

Eksik alan sorun değil. Zorunlu olanlar dışında bilinmeyen kolonlar boş bırakılır — mobilyalı olup
olmadığı yazılmamış bir iş de m² başına maliyet hakkında kanıttır. Uydurma değer yazmak, boş
bırakmaktan kötüdür.

## Dosya 1 — `historical-jobs-template.csv`

İş başına bir satır. En yakın tarihli 50 iş.

| Kolon | Zorunlu | Ne yazılır |
|---|---|---|
| `job_ref` | ● | İşletmenin kendi referansı (fatura no, defter satırı). Tekrarlanamaz — aynı iş iki kez girilirse ortalamalar bozulur, sistem ikinci kaydı reddeder. |
| `completed_on` | ● | İşin bittiği tarih, `2026-03-14` biçiminde. |
| `district_code` | | İlçe, büyük harf ve Türkçe karaktersiz: `KADIKOY`, `USKUDAR`. Artık çalışılmayan bir ilçe de yazılabilir. |
| `layout` | | Ev tipi: `STUDIO`, `ONE_PLUS_ONE`, `TWO_PLUS_ONE`, `THREE_PLUS_ONE`, `FOUR_PLUS_ONE`. |
| `scope` | | İşin kapsamı: `WHOLE_HOME` (ev geneli) veya `SELECTED_ROOMS` (seçili odalar). |
| `furnishing` | | `EMPTY` (boş), `PARTIAL` (kısmen eşyalı), `FURNISHED` (eşyalı). İşçilik katsayısını doğrudan besler. |
| `wall_condition` | | Duvar durumu: `GOOD`, `MINOR`, `MAJOR`. |
| `gross_area_m2` | ○ | Brüt m². |
| `net_area_m2` | ○ | Net m². İkisinden **en az biri** zorunlu; ikisi de varsa brüt→net katsayısı bu veriden çıkar. |
| `door_count` | | Boyanan kapı sayısı. |
| `quoted_total_ex_vat` | ● | Müşteriye verilen fiyat, KDV hariç. |
| `actual_total_ex_vat` | | Kesilen fatura KDV hariç — **verilen fiyattan farklıysa**. Aynıysa boş bırakın. |
| `actual_cost` | ● | Gerçekleşen toplam maliyet (işçilik + malzeme), KDV hariç. |
| `actual_labour_cost` | | Maliyetin işçilik kısmı. |
| `actual_material_cost` | | Maliyetin malzeme kısmı. İkisi de yazıldıysa toplamı `actual_cost`'a eşit olmalı; tutmazsa kayıt reddedilir. |
| `actual_days` | | İşin kaç gün sürdüğü. |
| `crew_size` | | Kaç kişi çalıştı. |
| `notes` | | Serbest not: revizyon, ek iş, gecikme sebebi. Virgül kullanacaksanız hücreyi çift tırnağa alın. |

● zorunlu · ○ ikisinden biri zorunlu

### Örnek satır

| `job_ref` | `completed_on` | `district_code` | `layout` | `scope` | `furnishing` | `gross_area_m2` | `net_area_m2` | `quoted_total_ex_vat` | `actual_cost` | `actual_days` |
|---|---|---|---|---|---|---|---|---|---|---|
| 2026-0148 | 2026-03-14 | KADIKOY | THREE_PLUS_ONE | WHOLE_HOME | FURNISHED | 112 | 92 | 68000 | 52000 | 3 |

## Dosya 2 — `historical-job-items-template.csv`

"Yapılan işler" — iş başına, yapılan her kalem için bir satır. Bu dosya olmadan da fiyat listesinin
toplam seviyesi doğrulanabilir; kalem kalem doğrulama ancak bununla mümkün.

| Kolon | Ne yazılır |
|---|---|
| `job_ref` | Birinci dosyadaki referansın aynısı. |
| `code` | Aşağıdaki kod listesinden. Bir işte aynı kod iki satır olamaz — miktarları toplayıp tek satır yazın. |
| `quantity` | Miktar, kodun birimine göre. Birim yazılmaz: her kodun birimi zaten belli. |

### Kalem kodları

| Kod | Türkçe | Birim |
|---|---|---|
| `WALL_PAINT` | Duvar boyası | m² |
| `CEILING_PAINT` | Tavan boyası | m² |
| `PATCH_FILLING` | Macun tamiri | m² |
| `SKIM_COAT` | Saten alçı | m² |
| `PRIMER` | Astar | m² |
| `STAIN_BLOCK_PRIMER` | İzolasyon astarı | m² |
| `WALLPAPER_STRIPPING` | Duvar kağıdı sökümü | m² |
| `DOOR_PAINT` | Kapı boyası | adet |
| `TRIM_PAINT` | Pervaz boyası | adet |
| `RADIATOR_PAINT` | Petek boyası | adet |
| `DOWNLIGHT_CUTTING` | Spot kesimi | adet |
| `CORNICE_CUTTING` | Kartonpiyer kesimi | oda |
| `MASKING` | Örtü / koruma | oda |
| `MOBILIZATION` | Nakliye ve kurulum | iş başına 1 |

Listede olmayan bir iş yaptıysanız (zemin, alçıpan, elektrik) kendi kodunuzu yazın — sistem onu
reddetmez, "fiyat listesinde karşılığı yok" diye raporlar. O kalemi listeye eklemek gerekip
gerekmediği ayrı bir karar.

## Veri geldikten sonra ne oluyor

Kayıtlar yüklendiği anda iki rapor çalışır ve BOYA-2'nin kabul kriteri ancak o zaman karşılanır:

1. **Fiyat listesinin doğrulanması.** İş başına gerçekleşen m² maliyeti, fiyat listesinin aynı iş
   için ürettiği maliyetle karşılaştırılır. Liste sistematik olarak düşük çıkıyorsa her teklifte
   para kaybediliyor demektir; yüksek çıkıyorsa iş kaybediliyordur.
2. **Katsayıların ilk kalibrasyonu.** Bu veriden doğrudan çıkan katsayılar: gerçekleşen kâr marjı
   (`margin_ratio`), günlük ekip maliyeti (`actual_labour_cost` ÷ `actual_days`), brüt→net oranı
   (`net_area_m2` ÷ `gross_area_m2`) ve ilçe farkları. Her biri şu an placeholder.

Sonuç yeni bir fiyat listesi sürümü olur; mevcut sürüm düzeltilmez, yerine geçilir (bkz. karar
kaydı 0010). Oda bazlı ölçüye dayanan katsayılar — tavan yüksekliği, çevre çarpanları, boşluk
oranları — bu veriden çıkmaz; onlar sistem çalışmaya başladıktan sonra, gerçek tekliflerle
karşılaştırılarak kalibre edilir (Faz 2).

**Kaç kayıt yeterli?** §12'nin 50 hedefi, marjın işten işe dalgalanmasını görebilmek için. Daha az
kayıtla da başlanabilir ama 20-30 tamamlanmış işin altında tek bir istisnai iş ortalamayı
sürüklüyor; o noktada rakam güven vermez.
