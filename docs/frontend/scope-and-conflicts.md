# Frontend Kapsam ve Çelişki Kaydı

Backend'in `docs/requirements/document-delta.md` disiplininin frontend
karşılığı (FE-ADR-013 §f). **Hiçbir kapsam dışı bırakma ve hiçbir mock/backend
çelişkisi sessizce çözülmez** — her biri burada bir satırla yaşar.

Son güncelleme: **2026-07-24**

## Durum etiketleri

| Etiket | Anlamı |
|---|---|
| 🔴 **karar bekliyor** | Karar verilmedi; geliştirmeyi bloke ediyor veya edebilir |
| 🟡 **analiste soruldu** | Analiste/ekibe iletildi, cevap bekleniyor |
| 🟢 **karara bağlandı** | Karar alındı ve ilgili ADR'ye/dokümana işlendi |
| ⚪ **kayıt** | Bilgi amaçlı; aksiyon gerektirmiyor |

---

## 1. Kapsam dışı bırakılanlar (FE-ADR-013 §b)

| # | Öğe | Domain | Backend durumu | UI davranışı | Durum |
|---|---|---|---|---|---|
| 1.1 | **Offer Selection** ekranı (tamamı) | FR-PROD + FR-SALE | Servis yok | Geliştirilmez | 🟢 karara bağlandı |
| 1.2 | **Product Configuration** ekranı (tamamı) | FR-PROD | Servis yok | Geliştirilmez | 🟢 karara bağlandı |
| 1.3 | **Submit Order** ekranı (tamamı) | FR-SALE | Servis yok | Geliştirilmez | 🟢 karara bağlandı |
| 1.4 | Customer Info → **hesap bölümü** (Account name/number/type/status, Create/Edit/Delete account, Billing address) | FR-ACCT | Servis yok | Sekme **hiç render edilmez** | 🟢 karara bağlandı |
| 1.5 | Customer Info → **ürün bölümü** (Product offer, Campaign, View/Deactivate product) | FR-PROD | Servis yok | Render edilmez | 🟢 karara bağlandı |
| 1.6 | Customer Info → hesap satırı aksiyonları (Start new sale, Transfer, Service address change) | FR-SALE | Servis yok | Render edilmez | 🟢 karara bağlandı |
| 1.7 | Customer Search → `accountNumber` filtresi | FR-ACCT | Parametre **tanınıyor**, `501 MSG-FEATURE-NOT-IMPLEMENTED` | Alan render edilir, **disabled** | 🟢 karara bağlandı |
| 1.8 | Customer Search → `orderNumber` filtresi | FR-SALE | Parametre **tanınıyor**, `501` | Alan render edilir, **disabled** | 🟢 karara bağlandı |
| 1.9 | `PasswordInput` bileşeni | — | — | `shared/ui/`'da yazılmaz (FE-ADR-005 §P5) | 🟢 karara bağlandı |
| 1.10 | Mock **Login v2** ekranı | FR-AUTH | Keycloak teması tamamlandı | Angular'a port edilmez (FE-ADR-005 §P4) | 🟢 karara bağlandı |

**Kapsam içi:** Customer Search, Create Customer, Customer Info'nun demografik +
adres + iletişim bölümleri, referans veri (cities/districts/lookups), auth
kabuğu.

---

## 2. Mock ↔ Backend kontrat uyuşmazlıkları

Tam gerekçeler: `docs/frontend/mock-ui-analysis.md` §5A.
**Genel kural: çakışmada backend kontratı kazanır (FE-ADR-013 §e).**

| # | Konu | Mock | Backend | Karar | Durum |
|---|---|---|---|---|---|
| 2.1 | **"Second name" filtresi** | Ayrı filtre alanı **yok** (önceki analiz yanlış listelemişti); tabloda **kolon** olarak var | Tek `firstName` parametresi **First+Middle birleşiminde** kelime-başı arar (KR-01) | Tek kutu, etiketi **"Name"**; `middleName` yalnız tablo kolonu | 🟢 karara bağlandı |
| 2.2 | **"Role" filtresi** | Filtre **yok**; tabloda kolon var | Liste endpoint'inde `role` parametresi yok; response'ta `role` alanı var | **Role ile filtreleme yapılmaz**; yalnız kolon | 🟢 karara bağlandı |
| 2.3 | **Per page değerleri** | 15 / 25 / 50, varsayılan **15** | API varsayılanı **20**; KR-04 (analist) **15/30/50** — üç farklı liste | **Varsayılan 20.** Seçenek listesi 20'yi içerecek şekilde kurulur | 🟢 karara bağlandı |
| 2.4 | Per page **seçenek listesi** | 15/25/50 | Varsayılan 20; API **pozitif her `size` değerini kabul ediyor** (ADR-005) | **20 / 50 / 100**, varsayılan **20**. 50 ve 100 için backend tarafında ayrı bir hazırlık gerekmiyor — `size` parametresi serbest | 🟢 karara bağlandı |
| 2.5 | **Kriter birleşimi** | Dolu kriterler OR'lanır | Backend de OR'luyor (isim grubu içi AND) | Uyumlu, değişiklik yok | 🟢 karara bağlandı |
| 2.6 | **Doğum tarihi formatı** | `DD.MM.YYYY` (taşımada) | ISO `YYYY-MM-DD` | Taşıma **ISO**; gösterim `dd.MM.yyyy` | 🟢 karara bağlandı |
| 2.7 | **Cinsiyet değerleri** | `male` / `female` | `"Male"` / `"Female"` | Backend | 🟢 karara bağlandı |
| 2.8 | **Adres alanları** | `city`, `district` (string ad), `houseNo`, `description` | `cityId`, `districtId` (Long), `houseFlatNumber`, `addressDescription`, `primary` | Backend; Select'ler **id** taşır | 🟢 karara bağlandı |
| 2.9 | **Şehir/ilçe kaynağı** | Hardcoded (3 şehir, 10 ilçe) | `GET /api/cities`, `/api/cities/{id}/districts` | Backend | 🟢 karara bağlandı |
| 2.10 | **İletişim alanları** | `mobile`, `home` | `mobilePhone`, `homePhone` | Backend | 🟢 karara bağlandı |
| 2.11 | **Müşteri kimliği** | `customerId` (7 hane) | Response alanı **`customerNumber`**; query param adı `customerId` kalıyor | Backend; ikisi karıştırılmaz | 🟢 karara bağlandı |
| 2.12 | **Birincil adres** | `primaryIndex` (dizi indeksi) | `PATCH .../addresses/{addressId}/primary` | Backend (kalıcı id) | 🟢 karara bağlandı |
| 2.13 | **NATID tekilliği** | Client-side, 2 sabit ID | `409 MSG-CUST-DUP-NATID` (soft-deleted dahil, ADR-003) **+ MERNIS** (`400`/`503`) — mock'ta MERNIS hiç yok | Backend; üç hata da UI'da karşılanır | 🟢 karara bağlandı |
| 2.14 | **Türkçe diakritik** | Mock katlıyor (`yilmaz`→`Yılmaz` eşleşir) | Backend katlamıyor | Backend: **diakritik-duyarlı**; fold Angular'a taşınmaz | 🟢 karara bağlandı |
| 2.15 | **Pasif müşteriler** | Listeden çıkarılıyor | `status_id = ACTV AND deleted_date IS NULL` | Uyumlu | ⚪ kayıt |
| 2.16 | **z-index** | Modal `1000`, toast `1200` → toast modal'ın altında kalır | EDS token'ları: modal 1400, toast 1500 | Yalnız `--eds-z-*` token'ları; sayısal literal yasak | 🟢 karara bağlandı |
| 2.17 | **`data-testid`** | 7 ekranda da **0 adet** | — | Her etkileşimli elemanda zorunlu (FE-ADR-009) | 🟢 karara bağlandı |
| 2.18 | **Sidenav etiket boyutu** | `11px` (token dışı) | — | **Caption'a yuvarlanır: `--eds-type-caption-size` (12px)**. Token dışı değer yazılmaz (FE-ADR-011 §f) | 🟢 karara bağlandı |
| 2.19 | **Boş sonuç metni** | "No customer found" + "Use *Create new customer* above…" | Analist kataloğu: `MSG-CUST-NOT-FOUND` = "No customer found! Would you like to create the customer?" | Hangisi geçerli? | 🟡 **analiste soruldu** |
| 2.20 | **Header rol metni** | `"Mobility · Resp. Sales Rep."` | `/api/session/me` yalnız `username`, `subject`, `roles` döndürüyor | **Kaldırılır.** Header'da avatar + `/api/session/me`'den gelen gerçek `username` gösterilir. Mock'un metni prototip örnek verisidir; olmayan veri gösterilmez (FE-ADR-013 §a). Backend'e yeni alan **eklenmez** | 🟢 karara bağlandı |
| 2.21 | **Sidenav öğelerinin işlevi** | B2C/B2B/menu/thumbs-up yalnız görsel durum değiştiriyor | — | **Yalnız B2C aktif.** Diğer üç ikon görünür kalır ama **tıklama hiçbir şey tetiklemez** (navigasyon yok, aktif durum değişmez). `aria-disabled` + `cursor: default` | 🟢 karara bağlandı |
| 2.22 | **Header dil seçici** | Statik `EN` metni | — | AC-LANG-01-01 gereği **gerçek TR/EN değiştirici** olacak | 🟢 karara bağlandı |
| 2.23 | **Login ekranı** | Login v2 tasarımı | Keycloak `crm-lite` teması mock'u birebir uyguluyor | Angular'da yazılmaz | 🟢 karara bağlandı |
| 2.24 | **501 filtrelerinin ipucu metni** | — | — | Disabled alanların tooltip/helper metni ne diyecek? Öneri: *"Available when the account/order module is released."* | 🟡 **analiste soruldu** |

---

## 2A. Customer Search (golden path) — ekran bazlı uyuşmazlıklar

Ekran yazılmadan önce netleşmesi gerekenler. Analiz:
`docs/frontend/customer-search-analysis.md`.

> **Not:** 2A.1 ve 2A.2 için **proje kararı zaten var** (§2.1, §2.2 — 🟢) ve
> geliştirme bunlarla ilerler; buradaki satırlar o kararları geri almaz, analist
> **onayını** ister. Onay gelmezse karar aynen geçerli kalır — bu maddeler
> geliştirmeyi **bloke etmiyor**.

| # | Konu | Mock ne diyor | Backend ne veriyor | Analiste sorulan | Durum |
|---|---|---|---|---|---|
| 2A.1 | **"Second name" arama davranışı** | Tabloda **kolon** var; filtre alanı **yok** | Ayrı `middleName` parametresi **yok**; `firstName` parametresi First+Middle **birleşiminde** kelime-başı arıyor (KR-01) | Kullanıcı "Nur" yazıp **yalnız ikinci adı** "Nur" olanları bulmak isterse bu karşılanmalı mı? Bugünkü davranış: "Zeynep Nur" da "Nur Ali" de gelir, ayrım yapılamaz. Tek kutunun etiketi **"Name"** olarak mı kalsın, yoksa "Name / Second name" gibi mi ifade edilsin? | 🟡 **analiste soruldu** (proje kararı §2.1 geçerli) |
| 2A.2 | **"Role" ile filtreleme** | Tabloda **kolon** var; filtre alanı **yok** | Liste endpoint'inde `role` parametresi **yok**; yanıtta `role` alanı **var** | Role'e göre filtreleme bir gereksinim mi? Değilse kolon salt bilgi olarak kalır (bugünkü plan). Gereksinimse **backend'e yeni parametre** gerekir — bu bir backend iş kalemidir, frontend tek başına çözemez | 🟡 **analiste soruldu** (proje kararı §2.2 geçerli) |
| 2A.3 | **Boş sonuç: iki farklı durum** | Tek boş-durum metni var | Aynı 200 + boş liste, iki farklı anlam | Ekran **ilk açıldığında** (hiç arama yapılmadan) sistemde müşteri yoksa gösterilecek metin, **arama sonucu boş** metninden farklı olmalı. `MSG-CUST-NOT-FOUND` ("Müşteri bulunamadı! Oluşturmak ister misiniz?") aramasız durumda yanıltıcı. Aramasız durum için **analist metni var mı**, yoksa proje-yazımı mı olsun? | 🟡 **analiste soruldu** |
| 2A.4 | **Sayfalama sayfa butonları** | `‹ 1 2 3 ›` — sabit 3 sayfa | `totalPages` 7+ olabilir (137 kayıt / 20) | Sayfa sayısı fazlayken kısaltma nasıl olsun? (`1 … 4 5 6 … 12` mi, yalnız ileri/geri mi, "Sayfa 4 / 12" mi?) Mock'ta karşılığı yok | 🟡 **analiste soruldu** |
| 2A.5 | **Sonuç satırına tıklama** | Satır tıklanınca Customer Info'ya gidiyor | Detay endpoint'i **var** (`GET /api/customers/{customerNumber}`) | Detay ekranı bu fazda yazılmayacak. Satır tıklaması şimdilik **pasif** mi kalsın, yoksa Detail ile birlikte mi açılsın? Öneri: `routerLink` hazır yazılır, hedef rota gelene kadar devre dışı | 🟡 **karar bekliyor** (bloke etmiyor) |

---

## 3. Analist dokümanı ↔ backend çelişkileri (frontend'i etkileyenler)

Backend tarafındaki tam kayıt: `docs/requirements/document-delta.md` §Open
conflicts. Aşağıdakiler frontend'i doğrudan bağlar.

| # | Çelişki | Durum |
|---|---|---|
| 3.1 | **KR-04 sayfa boyutu:** analist UI varsayılanı 15 (15/30/50) vs API varsayılanı 20 | 🟢 **karara bağlandı** — 20 (bkz. 2.3) |
| 3.2 | **FR-AUTH-01 uygulama içi login formu varsayıyor** | 🟢 **karara bağlandı** — ADR-006 süperseder; Keycloak sayfası + tema. `document-delta.md` #6'da kayıtlı |
| 3.3 | **AC-CUST-01-02** ("LBL-SEARCH boşken pasif") ↔ **AC-CUST-01-00** (login sonrası tüm müşteriler) gerilimi | 🟢 **karara bağlandı** — ekran açılışında **otomatik kritersiz çağrı** (browse modu) yapılır; `Search` butonu yalnız **yeniden** aramayı tetikler ve tüm alanlar boşken pasif kalır. İki AC de karşılanır |
| 3.4 | **ADR-011 analist onayı** (workbook USERS tablosunun Keycloak lehine terk edilmesi) | 🟡 **analiste soruldu** — ADR-011 status hâlâ "Proposed" |
| 3.5 | Katalogda karşılığı olmayan 10 backend anahtarı (`MSG-VALIDATION-ERROR`, `MSG-AUTH-*`, `MSG-ADDR-LAST-DELETE`, …) | 🟡 **analiste soruldu** — metinleri biz mi yazacağız, analist mi verecek? Şimdilik proje-yazımı, işaretli |

---

## 4. Teknik açık kararlar

| # | Konu | Detay | Durum |
|---|---|---|---|
| 4.1 | **OAuth redirect origin** | 🟢 **ÇÖZÜLDÜ (2026-07-23)** — üç eşgüdümlü değişiklik: (1) `api-gateway.yml`'e `server.forward-headers-strategy: framework`; (2) realm'de `crm-bff` client'ına `:4200` redirect URI + webOrigin + post-logout URI eklendi; (3) `keycloak-init` her `up`'ta bunları yeniden uyguluyor (`--import-realm` yalnız ilk açılışta çalıştığı için mevcut `keycloak_db`'ler de kapsanıyor); (4) nginx `$host`'u olduğu gibi iletiyor. Çalışan stack'te doğrulandı: PKCE `S256` ve `directAccessGrants=false` korunuyor. Detay: FE-ADR-004 §Addendum | 🟢 **karara bağlandı** |
| 4.2 | **Frontend host portu** | **4200** (boşta; kullanılanlar 8888/8761/8080/5432/8180). 4.1 çözüldüğü için sabitlendi | 🟢 karara bağlandı |
| 4.3 | **Node sürümü** | Karar: **22.23.1** + npm **10.9.8**. Makinede **ZIP dağıtımı** ile kuruldu (`C:\tools\node-v22.23.1-win-x64`), MSI ile değil: MSI 22.23.1'i kurulu 23.11.1 üzerine bir *downgrade* sayıp `WIX_DOWNGRADE_DETECTED` ile reddediyor, kaldırma ise yönetici hakkı istiyor. PATH `~/.bashrc` ile önceliklendirildi. ⚠️ **Bu yalnız Git Bash içinde geçerli** — sistem PATH'indeki `C:\Program Files\nodejs` (v23.11.1) duruyor, cmd/PowerShell hâlâ onu görüyor. Container tarafı etkilenmez (`node:22.23.1-alpine`). ⚠️ Node 22 maintenance'ta, EOL **2027-04-30** | 🟢 **karara bağlandı ve doğrulandı** |
| 4.4 | **`Page` zarfının JSON şekli** | **Backend hatası değil** — `Page<CustomerDetailResponse>` dönüyor ve Spring'in `PageImpl` serileştirmesi kullanılıyor; `spring.data.web.pageable.serialization-mode` ayarlanmadığı için şekil *framework varsayılanına* bağlı, yani bilinçli bir kontrat değil. **Öneri (backend):** modu açıkça pinlemek ya da açık bir sayfa DTO'su döndürmek — böylece Spring Boot yükseltmesi kontratı sessizce değiştiremez. Frontend tarafı: zarf tipi tek yerde (`data/` katmanı) tanımlanır, ekranlara sızmaz | 🟡 **backend'e önerildi** |
| 4.5 | **zone.js / zoneless** | 🟢 **UYGULANDI** — zoneless; `provideZonelessChangeDetection()` app.config'te açık, zone.js bağımlılık değil. `@angular/core@22.0.8` `peerDependenciesMeta: { zone.js: { optional: true } }` (FE-ADR-006 §7) | 🟢 karara bağlandı |
| 4.6 | **CI wiring** | **Ayrı iş.** Frontend job'ı backend Maven job'ından bağımsız çalışır; frontend lint hatası backend release'ini bloke etmez, tersi de geçerli (FE-ADR-001) | 🟢 karara bağlandı |
| 4.7 | **Import-boundary lint** | 🟢 **EKLENDI** — ESLint `no-restricted-imports` ile `core/`→`features/` ve `shared/`→`core/`|`features/` yasak; ihlal FE-ADR-003 mesajıyla reddediliyor (kanıtlandı 2026-07-23) | 🟢 karara bağlandı |
| 4.8 | **`data-testid` lint kuralı** | **Eklenmeyecek (bilinçli).** Angular şablonlarında "etkileşimli eleman" tespiti güvenilmez; false-positive üretip `eslint-disable` yorumlarının çoğalmasına ve kuralın değer kaybetmesine yol açar. Zorlama mekanizması: **PR review + E2E testlerin kendisi** (seçici yoksa test yazılamaz) | 🟢 karara bağlandı |
| 4.9 | **Katalog bütünlük testi** | 🟢 **YAZILDI** — `frontend/src/app/core/i18n/i18n.spec.ts`: her anahtarın EN+TR karşılığı dolu + dokümante 22 backend `messageKey` katalogda mevcut; eksikse test kırmızı. 6/6 test geçiyor | 🟢 karara bağlandı |
| 4.10 | **Tarih formatı istisnası** | FE-ADR-012 §e genel ilkesi "kültüre bağlı biçimlendirme yerelleştirilir"; tarih için **sabit `dd.MM.yyyy`** istisnası bilinçli alındı. Geri alınmak istenirse buradan izlenir | 🟢 **karara bağlandı** (istisna kayıtlı) |
| 4.12 | **`src/app/layout/` — dördüncü üst klasör** | Uygulama kabuğu (header + sidenav) FE-ADR-003 §1'in üç katmanının hiçbirine oturmuyor: `core/` değil (servis değil, bileşen), `shared/` **olamaz** (core'dan `AuthService`/`I18nService` enjekte ediyor; import-boundary lint'i bunu reddediyor), `features/` değil (kullanıcıya dönük bir "yetenek" değil, her ekranın çerçevesi). Bu yüzden tek bileşenli ince bir **`layout/`** klasörü eklendi. Değerlendirilen alternatifler: (a) kabuğu `shared/`e presentational yapıp core'u input/output ile geçirmek — iskelet aşamasında gereksiz dumb/smart ayrımı ve yine bir yerde container gerekiyor; (b) `features/shell/` demek — ADR'nin "yetenek" tanımını eğip bükmek. `layout/` yalnız aşağı doğru (`core` + `shared`) bakar; lint kuralları `core/` ve `shared/`i kısıtladığı için ihlal riski yok | 🟢 karara bağlandı |
| 4.11 | **`validationErrors` alan yolu tekdüze değil → frontend'de çözüldü** | Backend handler'ları farklı biçim üretiyor (kaynaktan doğrulandı 2026-07-24): body `@Valid` → **tam yol** (`demographic.firstName`, `addresses[0].cityId`, `contactMedium.email`); `@RequestParam` (`handleConstraintViolation:72`) → **yalnız son segment** (`firstName`); tip uyuşmazlığı → param adı. Ayrıca `demographic`/`addresses`/`contactMedium` **çıplak** gelebiliyor ve **hiçbir form kontrolüne karşılık gelmiyor** — naif "yaprak ada göre eşleştir" yaklaşımı bunları sessizce yutar (kullanıcı 400 alır, ekranda hiçbir şey görmez). **Backend'e dokunulmadan** çözüldü: `core/http` her yolu bir kez ayrıştırıp `leaf`/`scope`/`index`/`structural` üretiyor; `matchFieldErrors()` eşleşenlerin YANINDA **`unmatched`** döndürüyor, böylece sessiz yutma API düzeyinde imkânsız; `fieldErrorsAt(scope,index)` wizard'da doğru adres satırını hedefliyor. Ham İngilizce değerli alanlardan **anlamı tek olanlar** (`cityId`/`districtId`/`gender`/`addresses`/`demographic`/`contactMedium`) frontend anahtarına eşlendi; serbest metin alanları (`street`, `houseFlatNumber`, `addressDescription`) bilinçle generic bırakıldı — zarf hangi kısıtın patladığını söylemediği için "zorunludur" demek yanlış olabilirdi (`@NotBlank` + `@Size` birlikte). Bu bilinçli tercihler `INTENTIONALLY_GENERIC` listesinde **açıkça** duruyor; listede olmayan bir alan generic'e düşerse `console.warn` üretiliyor (FE-ADR-008 §3 "gap geliştiriciye görünür, kullanıcıya görünmez") — böylece backend'in ekleyeceği yeni bir alan sessizce generic'e düşmüyor. 22/22 test | 🟢 **karara bağlandı** |

---

## 5. Backend'e bildirilecek bulgular

| # | Bulgu | Detay | Durum |
|---|---|---|---|
| 5.1 | **`İ` ile başlayan isimler aramada bulunamıyor** | `CustomerSpecifications.wordStart` karşılaştırmanın iki tarafını farklı motorda küçültüyor: kolon tarafı SQL `lower()`, terim tarafı Java `toLowerCase()`. Ölçüm (2026-07-23, canlı stack): Postgres `lower('İ')` → `i` (U+0069); Java `"İ".toLowerCase()` → `i` + **U+0307** (iki kod noktası). Kanıt: `SELECT lower('İbrahim') LIKE 'i̇brahim%'` → **`false`**. **Hiçbir Java `Locale` değeri ikisini birden düzeltmez** (`ROOT` `I`'yı düzeltir `İ`'yi bozar, `tr` tam tersi). Doğru düzeltme: Java'da küçültmeyi bırakıp arama terimini de SQL `lower()`'dan geçirmek | 🟡 **backend'e bildirildi** |
| 5.2 | **Test verisi hatayı yakalayamıyor** | `ind` tablosundaki seed isimleri ASCII'ye sadeleştirilmiş (`Yildiz`, `Sahin`) — Türkçe büyük harf içeren tek kayıt yok, bu yüzden 21 entegrasyon testi 5.1'i kaçırıyor | 🟡 **backend'e bildirildi** |
| 5.3 | **`docs/runbooks/auth-testing.md` satır 106 bayat** | Satır 3.9 hâlâ *"no custom project theme exists"* diyor; tema commit `285815d` ile geldi | 🟡 **bildirildi, düzeltme onayı bekliyor** |
| 5.4 | **PROJECTBRAIN §9.2 madde 2 bayat** | "Keycloak proje teması" kutusu işaretsiz ama tema tamamlanmış ve doğrulanmış (AC-AUTH-01'in tüm UI kriterleri karşılanıyor) | 🟡 **bildirildi, düzeltme onayı bekliyor** |
| 5.5 | **`functional-requirements.md` iç çelişkisi** | §Message keys `MSG-CUST-HAS-PRODUCTS` ve `MSG-ADDR-IN-USE`'u *"as used by the backend"* listesine koyuyor; §Deferred aynı iki kontrolü **no-op** ilan ediyor. Mock'ta "aktif ürünü var, silinemez" ekranı var ama backend bu hatayı bugün döndürmüyor | 🟡 **bildirildi** |

---

| 5.6 | **Swagger UI / api-docs anonim değil** | PROJECTBRAIN §4.7 *"Docs sayfası login'siz görülebilir"* diyor, ama çalışan stack'te `GET /swagger-ui.html` ve `GET /v3/api-docs/customer-service` **401 `MSG-AUTH-UNAUTHORIZED`** dönüyor (2026-07-23 ölçümü). Ya çalışan imajlar ADR-012 commit'inden eski, ya da `crm.security.permit-paths` beklendiği gibi uygulanmıyor. Frontend'i bloke etmiyor ama `Page` zarfının şemasını Swagger'dan okumayı engelledi (§4.4) | 🟡 **backend'e bildirildi** |
| 5.8 | **PROJECTBRAIN §10 "Compose 8 servis" notu bayat** | `infra/docker-compose.yml`'e `frontend` servisi eklendi (FE-ADR-010, 2026-07-24; `git diff` = 35 ekleme / 0 silme, mevcut hiçbir servise dokunulmadı). Servis sayısı artık **9**. PROJECTBRAIN backend dokümanı olduğu için frontend tarafından düzenlenmedi | 🟡 **backend'e bildirildi** |
| 5.7 | **Logout CSRF, tam-sayfa gezinme POST'u ile taşınamıyor** | FE-ADR-005 §3 "tam sayfa gezinme + `X-XSRF-TOKEN` header" öngörüyordu; ancak tarayıcıda gezinen bir POST yalnız `<form>` ile yapılır ve form, header değil `_csrf` **parametresi** taşır. Gateway'in `SpaCsrfTokenRequestHandler`'ı (kaynaktan doğrulandı 2026-07-24) parametre yolunu **XOR-decode** ediyor (BREACH koruması), header yolunu ise ham token ile karşılaştırıyor — `XSRF-TOKEN` çerezi **ham** token tuttuğundan yalnız **header (XHR)** yolu geçerli. **Karar:** logout `HttpClient` POST (header → CSRF geçerli) + ardından `/`'a tam-sayfa gezinme (`core/auth/auth.service.ts`). Keycloak `end_session` 302'sini XHR sürükleyemiyor, fakat SSO oturumu `id_token_hint` ile **sunucu tarafında** yine de sonlanıyor. **Kusursuz tarayıcı-yönlü RP-logout** (Keycloak tarayıcı çerezlerinin de temizlenmesi) isteniyorsa backend bir olanak eklemeli: (a) logout `end_session` URL'ini JSON'da döndürsün, Angular `window.location.assign` etsin; ya da (b) gezinilebilir bir GET logout. **(c)** `crm.security.post-logout-redirect-uri` hiçbir yml'de tanımlı değil → yalnız Java varsayılanı `http://localhost:8080/` geçerli; `:4200` olmalı (ya da `X-Forwarded-*`'tan türetilmeli).<br>**🔴 CANLIDA DOĞRULANDI (2026-07-24)** — iki somut belirti: (1) XHR, gateway'in cross-origin 302'sini takip ederken **askıda kalabiliyor**; sign-out navigasyonu yanıta zincirlendiği için buton *hiçbir şey yapmıyordu*. (2) SSO ölmediğinden `/`'a inince guard → Keycloak → **sessiz re-login** → `/customers`; kullanıcı çıkış yapamamış sanıyor. **Frontend azaltmaları (uygulandı):** `logout()` artık `timeout(3000)` ile sınırlı — yanıt gelmese de çıkış tamamlanır (regresyon testi var); ve varış noktası guard'ın dışındaki `/signed-out` sayfası (form değil, yalnız tam-sayfa "Giriş yap" bağlantısı). Bunlar belirtiyi görünür/deterministik yapar ama **SSO'yu öldürmez** — onun için (a) şart. | 🟡 **backend'e önerildi** |

---

## 6. Bakım kuralı

Yeni bir kapsam dışı bırakma, mock/backend farkı veya karar ortaya çıktığında
**önce buraya bir satır eklenir**, sonra kod yazılır. Durumu değişen satırın
etiketi güncellenir; satır silinmez — kapanmış kararların izi kalır.
Bir satır 🟢 olduğunda ilgili ADR'ye veya
`docs/frontend/mock-ui-analysis.md`'ye de işlenmiş olmalıdır.
