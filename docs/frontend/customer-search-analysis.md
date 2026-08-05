# Customer Search — Ekran Öncesi Analiz (Golden Path)

Sonraki fazın (b) parçası. **Kod değil, ekran yazılmadan önce netleşmesi
gerekenler.** Kaynaklar: `docs/api/customer-service.md` §List+filter,
`docs/frontend/mock-ui-analysis.md` §6.2, ADR-005, KR-01/KR-04.

Son güncelleme: **2026-07-24**

---

## 1. Neden golden path?

Bu ekran, **tek başına tüm mimari yığını uçtan uca kanıtlayan** en küçük iş
parçası:

| Katman | Bu ekranda kanıtlanan |
|---|---|
| Auth | Korumalı rota + guard + gerçek oturumla `/api/**` çağrısı |
| HTTP/interceptor | 200 dışı **her** durum: 400, 401, 403, 501, 503, ağ hatası |
| Hata sözleşmesi | `messageKey → i18n → ekran` zinciri ilk kez gerçek veriyle |
| i18n | Dolu bir ekranda anında dil değişimi (AC-LANG-01-02) |
| Tasarım sistemi | 6 EDS bileşeninin **hepsi** burada kullanılıyor |
| Kontrat | `Page<CustomerDetailResponse>` zarfı ilk kez gerçek yanıtla doğrulanıyor (§4.4 açık maddesi burada kapanır) |

Ayrıca **giriş noktası**: AC-CUST-01-00 gereği login sonrası ilk görülen ekran.
Diğer iki ekran (Create, Detail) buradan başlar — Detail'e satır tıklamasıyla,
Create'e "Create new customer" ile. Yani sırayı doğal olarak bu belirliyor.

**Risk açısından da doğru sıra:** çoğunlukla **okuma**. Yanlış giden bir şey veri
bozmaz. Create wizard'ı (3 adım, atomik POST, MERNIS, 409 çakışması) altyapı
kanıtlanmadan yazılırsa hata ayıklaması çok daha pahalı olur.

---

## 2. Kapsanan gereksinimler

| Madde | İçerik | Nasıl karşılanıyor |
|---|---|---|
| **AC-CUST-01-00** | Login sonrası tüm (aktif) müşteriler listelenir | Açılışta kritersiz `GET /api/customers` (browse modu) |
| **AC-CUST-01-02** | Tüm filtreler boşken `Search` pasif | Buton disabled; browse çağrısı butondan bağımsız |
| **AC-CUST-01-07** | Sayısal alanlara sayısal olmayan giriş → 400 | İstemci `inputMode` + backend 400 `validationErrors` |
| **KR-01** | Ad araması **kelime-başı**, First+Middle birleşiminde | Backend semantiği; frontend katlama/normalize **yapmaz** |
| **KR-04** | Sayfalama + sabit A-Z sıralama | `page`/`size` gönderilir; `sort` **gönderilmez** |
| **MSG-CUST-NOT-FOUND** | Boş sonuç mesajı | Boş durum kartı (§6) |

**Kapsam dışı (FE-ADR-013):** `accountNumber` ve `orderNumber` filtreleri —
alanlar **render edilir ama disabled**, isteğe **hiçbir zaman eklenmez**. Backend
onlara `501 MSG-FEATURE-NOT-IMPLEMENTED` döner; bu 501'i görmek **frontend
hatası** demektir (FE-ADR-008 §6).

> ⚠️ **GEÇERSİZ (05.08.2026).** Yukarıdaki paragraf tarihseldir. Her iki filtre de
> artık **kapsam içi ve canlı** (FE-ADR-013 §Amendment C): backend numarayı sahibi
> olan servisten çözüp **sahip müşteriyi** döndürüyor (KR-02/AC-CUST-01-04) ve ikisi
> de **birebir** eşleşir. Alanlar enabled, değerler istekte gider; rakam kısıtı
> paylaşılan `TextInput.digitsOnly` girdisiyle. KR-02 bir **sunucu** kuralıdır —
> ekran yüklenmiş tabloyu filtrelemez.

---

## 3. İstek sözleşmesi

```
GET /api/customers?page=0&size=20[&firstName=…][&lastName=…]
                                 [&nationalityId=…][&customerId=…][&gsmNumber=…]
```

- Tüm parametreler **opsiyonel**; hiçbiri yoksa **browse modu**.
- **Boş alan parametre olarak gönderilmez** (boş string ≠ filtre yok).
- `size` **daima açıkça** gönderilir — API varsayılanı 20, UI varsayılanı da 20
  (§2.3), ama örtük bırakmak ikisinin ileride ayrışmasına açık kapı bırakır.
- `sort` parametresi **yok** — sıralama sunucuda sabit (KR-04).
- Dolu kriter grupları backend'de **OR**'lanır; isim grubu içi AND.

### Mock filtresi → backend parametresi eşlemesi

| # | Mock etiketi | Backend parametresi | Eşleşme | Not |
|---|---|---|---|---|
| 1 | ID number | `nationalityId` | tam | max 11 hane, sayısal |
| 2 | Customer ID | `customerId` | tam | **yanıttaki karşılığı `customerNumber`** |
| 3 | Account number | `accountNumber` | — | 🚫 disabled, gönderilmez |
| 4 | GSM number | `gsmNumber` | **prefix** | sayısal |
| 5 | Name | `firstName` | **kelime-başı, First+Middle** | ⚠️ etiket "Name" (§2.1) |
| 6 | Last name | `lastName` | kelime-başı | |
| 7 | Order number | `orderNumber` | — | 🚫 disabled, gönderilmez |

> ⚠️ **`customerId` / `customerNumber` tuzağı:** sorgu parametresi `customerId`,
> yanıt alanı `customerNumber`. İkisi karıştırılmamalı (ADR-005). Tabloda
> gösterilen ve satır `data-testid`'ine giren değer **`customerNumber`**.

---

## 4. Yanıt sözleşmesi

Satırlar **tam detay** kontratı (`Page<CustomerDetailResponse>`) — liste ve detay
aynı alanları taşır:

| Alan | Tip | Tabloda |
|---|---|---|
| `customerNumber` | number | ✅ "Customer ID" kolonu |
| `firstName` | string | ✅ |
| `middleName` | string \| **null** | ✅ "Second name" |
| `lastName` | string | ✅ |
| `role` | string | ✅ |
| `nationalityId` | string | ✅ "ID number" |
| `fatherName`, `motherName`, `birthDate`, `gender`, `status` | — | ❌ listede gösterilmez (detayda var) |

`middleName` **null olabilir** → boş hücre; `"null"` yazısı veya `—` uydurulmaz.

> 🔴 **AÇIK: `Page` zarfının şekli.** Backend `PageImpl` serileştirmesi kullanıyor
> ve `spring.data.web.pageable.serialization-mode` ayarlanmamış → şekil framework
> varsayılanına bağlı, bilinçli bir kontrat değil (`scope-and-conflicts §4.4`,
> backend'e önerildi). **İlk iş:** çalışan stack'ten gerçek yanıtı alıp
> `content` / `totalElements` / `totalPages` / `number` / `size` alan adlarını
> doğrulamak. Zarf tipi **yalnız `features/customer/data/`** içinde tanımlanır,
> ekranlara sızmaz — böylece şekil değişirse tek dosya etkilenir.

---

## 5. Sayfalama (KR-04)

- Varsayılan **15**; seçenekler **15 / 30 / 50** (§2.4, 29.07.2026 revizyonu —
  KR-04 birebir). API whitelist dışı `size` değerlerine **400** döner
  (ADR-005 §Amendment), yani bu liste backend whitelist'iyle aynı olmak
  **zorunda**; eski 20/50/100 kararı geçersiz.
- Sayfa boyutu değişince **`page=0`'a dönülür** (aksi halde boş sayfada kalınır).
- Filtre değişip yeni arama yapılınca da `page=0`.
- Aralık metni ("1–15 / 137") **i18n parametreli** anahtar gerektirir → §8.
- `‹ 1 2 3 ›` kontrolleri `IconButton` + sayfa butonları; her biri `data-testid`.

---

## 6. Ekranın TÜM durumları

Her biri ayrı ayrı tasarlanır — "sonra bakarız" bırakılan durum, sahada boş ekran
olarak çıkar.

| # | Durum | Tetikleyici | UI |
|---|---|---|---|
| 1 | **Açılış / yükleniyor** | İlk browse çağrısı | Tablo alanında skeleton; filtreler etkin, `Search` pasif |
| 2 | **Dolu** | 200 + `content.length > 0` | Tablo + sayfalama |
| 3 | **Boş (browse)** | 200 + `content` boş, filtre yok | "Henüz müşteri yok" — **`MSG-CUST-NOT-FOUND` değil** (arama yapılmadı) |
| 4 | **Boş (arama)** | 200 + `content` boş, filtre var | `MSG-CUST-NOT-FOUND` + `Create new customer` çağrısı (mock §6.2) |
| 5 | **Yeniden yükleniyor** | Sayfa/boyut/filtre değişimi | Mevcut tablo **korunur** + üstte ince progress; ekran boşalmaz |
| 6 | **400 doğrulama** | Sayısal alana harf (AC-CUST-01-07) | `ApiFieldError` → ilgili `FormField`'a; eşleşmeyenler form üstü banner (`matchFieldErrors().unmatched`) |
| 7 | **401** | Oturum düştü | Interceptor tam sayfa login'e yönlendirir — **ekran hiçbir şey yapmaz** |
| 8 | **403 FORBIDDEN** | Rol yok | `/access-denied` (guard/interceptor) |
| 9 | **403 CSRF** | Token bayat | Interceptor probe + **1 kez** tekrar; kullanıcı fark etmez |
| 10 | ~~**501**~~ | ~~Disabled filtre gönderildi~~ | ⚪ **Artık üretilemez (05.08.2026):** hiçbir backend `MSG-FEATURE-NOT-IMPLEMENTED` döndürmüyor. FE-ADR-008 §6'nın "501 = frontend hatası" kuralı yürürlükte kalıyor (genel kural), ama bu ekranda tetikleyecek bir kriter yok |
| 11 | **503** | Servis kapalı | Toast: `MSG-SERVICE-UNAVAILABLE`; tablo son iyi haliyle kalır |
| 12 | **Ağ / 5xx** | Bağlantı yok | `MSG-INTERNAL-ERROR` + **Tekrar dene** aksiyonu |

> Durum 5 bilinçli: her istekte tabloyu boşaltmak, sayfa değiştiren kullanıcıya
> ekranı "sıfırlanıyor" hissi verir. Mevcut veri korunur, yalnız yükleme
> göstergesi eklenir.

---

## 7. Gerekli `shared/ui` bileşenleri

| Bileşen | Kullanım | Zorunlu mu? |
|---|---|---|
| `FormField` | 7 filtre alanı | ✅ |
| `TextInput` | 7 filtre alanı | ✅ |
| `Button` | `Search` (primary, fullWidth, iconLeading), `Clear` (secondary), `Create new customer` (primary, sm) | ✅ |
| `Icon` | search, chevron-left/right, search-x (boş durum), user-plus | ✅ |
| `IconButton` | Sayfalama ok'ları | ✅ |
| `Select` | Per page (sm) | ✅ |
| `DatePicker` | — | ❌ **kullanılmıyor** (§7 tasarım notu: sonraya) |

Ayrıca `shared/patterns`: tablo, boş durum kartı, sayfalama, toast, skeleton.

---

## 8. i18n anahtarları

**Mevcut (kullanıma hazır):** `UI-SEARCH-TITLE`, `-FILTER-HEADING`,
`-RESULTS-HEADING`, `-FILTER-ID-NUMBER`, `-FILTER-CUSTOMER-ID`,
`-FILTER-ACCOUNT-NUMBER`, `-FILTER-GSM-NUMBER`, `-FILTER-NAME`,
`-FILTER-LAST-NAME`, `-FILTER-ORDER-NUMBER`, `-COL-*` (6), `-EMPTY-TITLE`,
`-EMPTY-BODY`, `-CREATE-NEW`, `-PER-PAGE`, `-DEFERRED-HINT`; hata tarafında
`MSG-CUST-NOT-FOUND`, `MSG-VALIDATION-ERROR`, `MSG-SERVICE-UNAVAILABLE`,
`MSG-INTERNAL-ERROR`.

**Eklenmesi gerekenler:**

| Anahtar | Neden |
|---|---|
| `UI-SEARCH-SUBMIT` / `UI-SEARCH-CLEAR` | Buton metinleri |
| `UI-SEARCH-BROWSE-EMPTY-TITLE` / `-BODY` | Durum 3 — aramasız boş, `MSG-CUST-NOT-FOUND`'dan farklı |
| `UI-COMMON-RETRY` | Durum 12 |
| `UI-PAGINATION-RANGE` | **Parametreli** ("{from}–{to} / {total}") |
| `UI-PAGINATION-PREV` / `-NEXT` | `IconButton` `aria-label`ları |
| `UI-SEARCH-LOADING` | Skeleton `aria-label` |
| Placeholder'lar (§6.2 tablosu) | `11-digit ID number`, `e.g. 3068231`, `05XX XXX XX XX` … |

> 🔴 **AÇIK: parametreli anahtar mekanizması yok.** Mevcut `translate(key)` düz
> string döndürüyor; `UI-PAGINATION-RANGE` gibi yer tutuculu metinler için
> `translate(key, params)` imzası gerekiyor. Bu **i18n altyapısına küçük bir
> ekleme** ve ekran yazılmadan **önce** yapılmalı (§10).

---

## 9. `data-testid` planı

```
customer-search-page
customer-search-filter-form
customer-search-filter-id-number-input        customer-search-filter-customer-id-input
customer-search-filter-account-number-input   (disabled)
customer-search-filter-gsm-number-input       customer-search-filter-name-input
customer-search-filter-last-name-input        customer-search-filter-order-number-input (disabled)
customer-search-submit-button                 customer-search-clear-button
customer-search-create-new-button
customer-search-results-table
customer-search-results-row-{customerNumber}          ← iş anahtarı, ASLA indeks
customer-search-results-row-{customerNumber}-open-link
customer-search-empty-state                   customer-search-browse-empty-state
customer-search-error-banner                  customer-search-loading
customer-search-pagination                    customer-search-pagination-prev / -next
customer-search-pagination-page-{n}           customer-search-page-size-select
```

---

## 10. Netleştirmem gereken açık noktalar

| # | Konu | Neden bloke ediyor / etmiyor |
|---|---|---|
| 1 | **`Page` zarfının gerçek alan adları** | 🔴 **Bloke eder.** Çalışan stack'ten tek bir istekle çözülür — ekran yazımının ilk adımı |
| 2 | **Parametreli i18n** (`{from}–{to} / {total}`) | 🔴 **Bloke eder.** Altyapıya küçük ekleme; ekrandan önce yapılmalı. Onayın gerekiyor |
| 3 | **Satır tıklaması nereye gider?** | 🟡 Detay ekranı henüz yok. Öneri: satır `routerLink` **hazır yazılır**, hedef rota gelene kadar pasif. Yoksa Detail yazılırken tabloya geri dönmek gerekir |
| 4 | **Browse-boş vs arama-boş metni** | 🟡 Durum 3 için analist metni yok. Öneri: proje-yazımı `UI-SEARCH-BROWSE-EMPTY-*`, işaretli (FE-ADR-008 §7 kalıbı) |
| 5 | **"Second name" ve "Role"** | 🟡 `scope-and-conflicts §2A` — proje kararı var, **analist onayı** isteniyor |
| 6 | ~~**501 filtrelerinin ipucu metni**~~ | ⚪ **KAPANDI (05.08.2026)** — alanlar disabled değil, ipucu metni silindi (`§2.24`, `§1.7c`) |
| 7 | **Sayfalama kaç sayfa butonu gösterecek?** | 🟡 Mock 3 gösteriyor; 137 sonuçta 7 sayfa olur. Kısaltma kuralı (`1 … 4 5 6 … 12`) mock'ta yok — karar gerekiyor |
| 8 | **Türkçe diakritik** | ⚪ Karar verildi: backend'e göre **diakritik-duyarlı**, frontend katlama yapmaz (§2.14). Backend'e bildirilen `İ` hatası (§5.1) **açık** — arama sonuçlarında görülebilir |

**1 ve 2 çözülmeden ekran yazımına başlanmaz.** Diğerleri paralel ilerleyebilir.
