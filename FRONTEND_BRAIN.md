# FRONTEND_BRAIN — CRM Lite

> **Amaç:** Bu dosya frontend'in **güncel durumunun tek doğru kaynağıdır**
> (single source of truth). `PROJECTBRAIN.md`'nin frontend kardeşidir; backend
> tarafı için o dosya geçerlidir. Hem projeye sonradan dönen geliştirici hem de
> sıfırdan bağlam kuran bir AI agent bu dosyayı okuyarak "nerede kaldık, neden
> böyle yapıldı, sırada ne var" sorularını cevaplayabilmelidir.
>
> **Son güncelleme:** 2026-08-06 (**🐞 `secondary` BUTON KENARLIĞI + SEKME
> KİMLİĞİ.** Bildirim tek bir butondu ("Offer selection'da `Add to basket`'te
> border yok"), kök neden **`shared/ui/Button`**'da çıktı ve **tüm uygulamayı**
> etkiliyordu: taban `border-transparent`, `secondary` varyantı
> `border-line-strong` yazıyordu — **ikisi de düz tek-sınıflı `border-color`
> yardımcısı**, yani aynı özgüllükte, kazanan üretilen CSS'te sonra yazılan.
> Ölçüldü: `.border-line-strong` **14459**, `.border-transparent` **14517** →
> transparan kazanıyordu, **hiçbir secondary buton kenarlık göstermiyordu**.
> Düzeltme desende: taban artık yalnız kenarlık **kalınlığını** veriyor, rengini
> her varyant kendi bildiriyor. **Disabled davranışı değişmedi** —
> `disabled:border-transparent` sınıf+sözde-sınıf olduğu için daha özgül ve düz
> sınıfı yener; mock'un DS'i de aynısını yapıyor
> (`.eds-btn:disabled{…border-color:transparent!important}`), yani **pasif
> butonun kenarlıksız olması mock kuralı**, düzelen şey **etkin** butonun
> kenarlığı. ⚠ Ders: aynı CSS özelliğini hedefleyen iki düz Tailwind yardımcısını
> aynı elemana yazmak sınıf sırasından bağımsız **belirsiz** sonuç verir.
> **Sekme kimliği:** Keycloak login `infra/.../crm-lite/login/resources/img/
> favicon.svg`'yi sunarken `:4200`'de sekme Angular'ın varsayılan `favicon.ico`
> + `Frontend` başlığına düşüyordu. Aynı SVG `frontend/public/favicon.svg`'ye
> **birebir** kopyalandı, `index.html` ona bağlandı, başlık **`CRM Lite`** oldu,
> Angular'ın `favicon.ico`'su **silindi** (bırakılsa çıplak `/favicon.ico`
> isteği onu sunmaya devam ederdi). `<title>` i18n'e girmiyor: statik markup,
> runtime i18n oraya ulaşmıyor (FE-ADR-012 §a) ve metin iki dilde de aynı.
> **428/428 test** (+1 regresyon), lint + konvansiyon (116) + prod build yeşil;
> kayıt: scope **§4.34**.
> Önceki: 2026-08-06 (**🎨 SATIŞ AKIŞI 2. GÖRSEL UYUM TURU — 5 iş,
> 3'ü paylaşılan katmanda; 1 madde KARAR BEKLİYOR.** ⚠ Göreve konu olan
> `offer-selection.html` / `product-configuration.html` / `submit-order.html`
> **repoda yok ve hiç olmadı** (§4.30'un tekrarı) — markup yine bundle'ın
> `__resourceBlobs`'undan okundu. **İŞ 1 — sepet GRUPLU:** kampanya artık tek
> giriş (adı + `{campaignId} · Campaign` + girintili üye teklifler + paket
> toplamı), tekil teklif düz satır. **Veri modeli değişmedi** — `SaleBasketStore`
> düz `BasketItem[]` tutmayı sürdürüyor (kablo düz `items[]` alır, AC-SALE-01-08
> tüm sepeti sayar); görünüm için `groups` projeksiyonu eklendi. ⚠ **Davranış
> değişikliği:** mock'ta üye başına kaldır düğmesi yok → X **paketin tamamını**
> kaldırıyor (`addCampaign`'in simetriği). Sayaç **entry** sayıyor ("1 item").
> Kaldır düğmesi için `IconButton`'a **`bare` varyantı + `size=24` + `title`**
> geldi (eklemeli; 6 tüketicinin hiçbiri değişmedi). **İŞ 2 — stepper:** mock'ta
> **iki ayrı** şerit var (7 farkla), `Stepper`'a **`variant`** girdisi eklendi,
> Create Customer etkilenmedi. 🟡 **ÇİZGİ EŞİTLİĞİ KARARI KULLANICIDA:** ölçüm —
> **bizde çizgiler EŞİT** (§4.32'nin `grow` düzeltmesi), **mock'ta EŞİT DEĞİL**
> ve bu yapısal (öğeler `flex:1 1 0`, çizgi etiketten artanı doldurur). Karar
> gelene kadar **mevcut eşit geometri korunuyor**; numara biçimi (`1/2/3`)
> onaylıydı, uygulandı. **İŞ 3:** "This product needs no configuration." ve
> anahtarı silindi — mock'un `hasFields` dalının **else kolu yok**; kart
> başlığında bitiyor (AC-SALE-01-21 hâlâ karşılanıyor). **İŞ 4 — onay modalı
> DESENDE düzeltildi:** mock'un **iki** onay dialogu da **yatay** ve buton
> çubuğunun üstünde **çizgi yok**; `ConfirmDialog` artık butonlarını gövdede
> çiziyor (**`Modal` hiç değişmedi** → form dialoglarının gerçek footer çizgisi
> yerinde). `tone` yalnız rozet (danger daire / info **düz ikon**) ve onay
> butonunu (danger / primary+check) taşıyor. Gövde parametreli: hangi hesap + ne
> kadar; **`MSG-SALE-ORDER-CONFIRM` artık render edilmiyor** (sorusunu başlık
> soruyor), anahtar katalogda kaldı. Para biçimlendirmesi `sales/money.ts`'e
> **tek** kaynağa çıkarıldı. **İŞ 5 — §2B.8 GERİ ALINDI:** 201'de sihirbaz
> `/customers/{n}?tab=account&account=…`'a **otomatik** gidiyor; yerinde başarı
> bandı + "Back to customer" + 3 anahtar silindi. Gerekçe çürüdü: sipariş
> numarası **flash ile** taşınıyor. Mekanizma ikiye bölündü — *konum* query
> param, *mesaj* mevcut `CustomerFlashService` (**parametre taşıyacak** şekilde
> genişletildi, anahtar hâlâ çözülmemiş gidiyor); `AccountSection`'a
> **`initiallyExpanded`** (değer başına bir kez, yalnız listedeyse). 🔴 Mock'un
> `eds_pending_sale` localStorage enjeksiyonu **taşınmadı** — ürünler backend'de
> gerçek, Customer Info `GET /api/products?accountNumber=` ile **sunucudan**
> okuyor. Hesap listesi sayfalanmadığı için "o sayfaya atla" konusuz (§4.24/5).
> **427/427 test** (+10), lint + konvansiyon (116) + prod build yeşil; kayıt:
> scope **§4.33** + `shared-ui-design-notes` (IconButton/ConfirmDialog/Stepper).
> Önceki: 2026-08-05 (**🎨 MOCK GÖRSEL UYUM TURU — 11 uyuşmazlık,
> 4'ü paylaşılan katmanda; davranış değişmedi.** Tüm mock değerleri bundle'ın
> `__resourceBlobs`'u çözülerek **gerçek markup'tan** okundu. **A1/A2'nin kök
> nedeni h1 margin'i değildi:** Angular yönlendirilen bileşeni
> `<router-outlet>`'in **kardeşi** olarak basıyor, outlet DOM'da kalıyor ve
> kabuğun `flex flex-col gap-5` `<main>`'inde **bir flex item olarak bir gap
> tüketiyordu** — her sayfanın başlığı 20px aşağıdaydı. Tek kural
> (`router-outlet{display:none}`) tüm ekranları düzeltti. **A3:** mock'un logosu
> (`src/assets/etiya-logo.svg`) header'a girdi; `angular.json`'a ikinci asset
> kökü eklendi. **A4 DÜZELTİLMEDİ — mock da içeriğe bağlı:** bundle genelinde
> `table-layout` sıfır eşleşme, sabit kolon genişliği yok; TR başlıklar kısa
> olduğu için kolonların yeniden ölçülmesi tarayıcının doğru davranışı (kayıt
> §4.32). **B1 `shared/patterns/Stepper`:** `flex-1`'in sıfır basis'i **adımları**
> eşitliyordu, bağlayıcı da "eşit adım − bu etiket" kadar kalıyordu; `grow`
> (basis auto) ile her adım **eşit pay** alıyor ve payı adımın tek büyüyen
> çocuğu olan bağlayıcı yutuyor → iki çizgi eşit. Her iki sihirbazda doğrulandı.
> **C1 — §4.30 EK'in `flowPanel` kararı GERİ ALINDI:** panelin dialogu
> **büyütmesi** bu turda kusur olarak bildirildi ve mock'un markup'ı bildirimi
> doğruladı (`overflow:visible` + **mutlak** panel → liste dışarı taşar, dialog
> boyutu değişmez). Hesap dialogu `overflowVisible`'a geçti ve
> **`Select.flowPanel` girdisi tümüyle silindi** (tüketicisi kalmadı, tek işlevi
> yanlış sayılan davranıştı). **C2:** ürünsüz hesap artık mock'un kesikli
> kutusu — ama metin **analistin `MSG-PROD-NONE`'u** (MSG-* kontrat metnidir,
> FE-ADR-012 §c/§f); Pasif hesapta "Start new sale" mock'un `aria-disabled`
> span'i (+`role="button"`, a11y iyileştirmesi). **D1–D6 Offer Selection:**
> sekmeler **Catalog/Campaign**, tik hücresi koşulsuz kutu (kolon kayması
> bitti), sepetteki satır **kalıcı vurgulu + inert**, zebra (tek indeks
> `bg-page`), "Includes" **üye başına bir satır** (`{offerId} · {offerName}` +
> çevrilmiş servis tipi — ham enum artık **Category kolonunda ve filtrede de**
> basılmıyor), sayacın iki modu ("{n} selected" ↔ "{n} offers/campaigns").
> Mock'un fixture id/adları kodlanmadı; alanlar `GET /api/campaigns`'ten.
> **`bg-selected`: temada TEK token** — mock aynı token adına iki farklı inline
> fallback yazıyor, ikisi de bizim tek token'ımıza eşlendi, yeni hex
> uydurulmadı. **417/417 test** (+7), lint + konvansiyon + prod build yeşil;
> kayıt: scope **§4.32** + `shared-ui-design-notes` (flowPanel kaldırıldı).
> Önceki: 2026-08-05 (**🔓 CUSTOMER SEARCH: ACCOUNT NUMBER + ORDER
> NUMBER AKTİF — üç turdur bloke olan §1.7/§1.8 KAPANDI.** Bu turda **backend'e
> de dokunuldu** (görevin açık kapsamı): bloke edenin ta kendisi olan
> `CustomerBusinessRules.checkNoUnsupportedCrossServiceSearchCriterion` **silindi**
> ve yerine gerçek çözümleme kondu — `accountNumber` → `GET /api/accounts/{n}`,
> `orderNumber` → `GET /api/orders/{n}` (ADR-005 §Addendum 2026-08-05).
> **§1.7b'de yapılan kontrat karşılaştırması burada karşılığını verdi:** iki yanıt
> da zaten `customerNumber` taşıdığı için **hiçbir yeni uç açılmadı**, hiçbir
> kontrat değişmedi. **Frontend tarafı gerçekten küçüktü, §1.7a'nın öngördüğü
> gibi:** iki `FormControl`'ün `disabled: true`'su kalktı, ikisi
> `FILTERABLE_CONTROLS`'a girdi (Search'ü aktive ederler, istekte giderler, 400
> alan hatası bağlanır, Clear temizler), `CustomerSearchCriteria`'ya **string**
> olarak eklendiler. **Yeni bir sayısal giriş mekanizması YAZILMADI** — mevcut
> `TextInput.digitsOnly` girdisi bağlandı (yapıştırma + mobil klavye dahil;
> `type="number"` bilinçle kullanılmadı, `e`/`+`/`-`/ondalık ayırıcıyı geçirirdi).
> Değerler **String** kalıyor: KR-11/KR-12 sabit genişlikte kimliklerdir,
> `Number('0261000010')` baştaki sıfırı yerdi. **İstemci hiçbir filtreleme
> yapmıyor** — KR-02 sunucu kuralıdır, yüklenmiş tabloda eleme yok.
> `UI-SEARCH-DEFERRED-HINT` katalogdan **silindi** ve §2.24 onunla kapandı.
> KR-01 vs AC-CUST-01-03 çelişkisi araştırıldı: **v8-2 metninde çelişki YOK**,
> ikisi de "birebir" diyor → **exact** (backend `document-delta.md` §Conflict #7).
> **409/409 test** (+9), lint + konvansiyon + prod build yeşil; backend 91/91.
> Kayıtlar: scope **§1.7c** + §2.24. ⚠️ `format:check` repo genelinde 162 dosyada
> kırmızı (dokunulmayanlar dahil) — **önceden var olan** bir durum, bu turda
> düzeltilmedi.
> Önceki: 2026-08-04 (**🐞 HESAP MODALI — İKİNCİ TUR.** BUG-1 onaylandı
> (kapandı). **BUG-2 ilk turda kapanmamıştı:** düzeltme desende doğru kurulmuştu
> ama **hata raporundaki dialoga uygulanmamıştı** (`account-form-dialog`
> `overflowVisible` almamıştı — uygulama hatası). İkinci turda üç şey birden
> çözüldü: (1) o eksik uygulama, (2) **alanlar alt alta** — mock gövdesi
> `flex-direction:column; gap:16px` ile Account name ve Billing address'i tam
> genişlik/alt alta koyuyor; bizdeki `grid-cols-2` **mock'tan değil bizdendi**,
> `flex flex-col gap-4` yapıldı, (3) **dialog artık dropdown kadar uzuyor** —
> `overflowVisible` kırpmayı bitirir ama paneli mutlak bırakır (liste footer'ın
> üstüne biner, dialog uzamaz); istenen bu olmadığı için `shared/ui/Select`'e
> **`flowPanel`** girdisi eklendi: panel **akış içinde** render edilir → FormField
> → dialog büyür, liste hiçbir kenarı aşmaz, `max-h-65` tavanı korunduğu için
> büyüme sınırlı kalır. Hesap dialogu bu yüzden `overflowVisible` **kullanmıyor**;
> gövde scroll'u + `max-h-full` emniyet olarak duruyor. Billing address + inline
> panelin City/District Select'lerinin üçü de `flowPanel`. Adres sayısı hiçbir
> modda sınırlı değil (12 ve 9 seçenekli testlerle pinlendi). **İŞ-3 YİNE
> AÇILMADI ama bu kez ÇALIŞAN İKİLİ üzerinde kanıtlandı:** account/order
> servisleri gerçekten tam ve ayakta — **ancak 501'i onlar üretmiyor,
> `customer-service` üretiyor** ve ona `c89a6a9`'dan beri dokunulmamış.
> `podman cp` ile alınan **deploy edilmiş `app.jar`**'dan
> `CustomerBusinessRules.class` çıkarıldı: `"…is not implemented yet"` +
> `MSG-FEATURE-NOT-IMPLEMENTED` + `checkNoUnsupportedCrossServiceSearchCriterion`
> sabitleri **hâlâ içinde**. ⚠ **Swagger yanıltıcı:** canlı api-docs iki
> parametreyi listeliyor çünkü springdoc yalnız **controller imzasını** yansıtır
> (parametreler zaten hep imzadaydı) ve servis katmanındaki kuralı göremez —
> dokümante edilen tek yanıt `200`. Canlı uçtan uca çağrı token gerektirdiği ve
> realm'deki tek istemci public + `directAccessGrants=false` olduğu için
> yapılamadı. **Yapılabilen kısım yapıldı:** iletilen iki Swagger canlı
> `/v3/api-docs`'tan çekilip istemci tiplerimizle karşılaştırıldı — **sapma yok**
> (order 5 şema + account 7 alan birebir); `product-involvements` /
> `product-ids` uçları backend-backend akışı olduğu için bilinçle tüketilmedi.
> **365/365 test** (+2), lint + konvansiyon + prod build yeşil; kayıtlar:
> scope **§4.30 EK** + **§1.7b**, `shared-ui-design-notes` (`flowPanel`).
> ⚠️ Çalışan `frontend:local` imajı 02.08 tarihli — bu düzeltmeler ancak
> `--build frontend` sonrası ekranda görünür (§4.29 dersi).
> Önceki: 2026-08-03 (**🐞 HESAP MODALI HATA TURU — 2 hata
> düzeltildi, 1 iş backend'e takıldı.** **BUG-1:** Create/Edit billing account
> modalına mock'un **inline "New address" paneli** geldi (ayrı popup DEĞİL —
> hata raporu popup diyordu, mock panel kullanıyor, tasarımda mock bağlayıcı:
> FE-ADR-011). Panel yalnız **toplar**; adres oluşturma FR-ADDR-02 olduğu ve
> customer-service'e ait olduğu için `features/account/` onu **çağırmaz** —
> draft `newAddressRequested` ile yukarı çıkar, **Customer Info** onu mevcut
> `CustomerDetailStore.addAddress()` ile yazar (Address sekmesi ve §2.7
> sihirbazıyla **aynı tek yol**; ikinci servis yazılmadı) ve sonucu
> `createdAddressId` ile geri verir → panel kapanır, adres **otomatik seçilir**.
> Panel açıkken modalın Save'i disabled (mock kuralı). **BUG-2 kök nedeni
> genişlik değildi:** `shared/patterns/Modal`'ın gövdesi `overflow-y-auto`,
> yani bir **scroll container**, ve o da `Select`'in **mutlak konumlu** panelini
> kırpıyordu; modalımız zaten mock'tan genişti (560 > 480). Düzeltme **desende**:
> yeni `overflowVisible` girdisi (set edilince gövde `overflow-visible` +
> panel `max-h-full`'ü bırakır) — mock'un kendisi bunu modal-başına bir özellik
> olarak kullanıyor, hepsini flip etmek uzun dialogların scroll'unu götürürdü.
> Dropdown barındıran **her** dialog açtı: hesap + **adres** + **demografik**.
> Modal ölçeği mock'un **480**'ini kazandı ve yeniden harflendi
> (sm 420/md 480/lg 560/xl 680) — tüm tüketiciler yeniden etiketlendiği için
> **hesap dialogu dışında hiçbir genişlik değişmedi**. `Button`'a `ghost`
> varyantı yazıldı (design-note'un "mock kullanmıyor" tespiti yanlıştı — 14 kez
> geçiyor). **İŞ 3 DURDURULDU:** Customer Search'ün `accountNumber`/`orderNumber`
> filtreleri **aktifleştirilmedi** — `origin/dev` HEAD'inde
> `CustomerBusinessRules` hâlâ **koşulsuz 501** atıyor, API dokümanı hâlâ 501
> diyor ve `c89a6a9..origin/dev` aralığında customer-service'e hiç dokunulmamış;
> açılsaydı ekran ilk tuşta 501 alırdı (kayıt: scope §1.7a). ⚠ Ayrıca:
> göreve konu olan `docs/source/mock-ui/billing-account-modal.html` **repoda
> yok ve hiç olmadı** — markup, bundle'ın gzip+base64 `__resourceBlobs`'u
> çözülerek gerçek kaynağından okundu. **363/363 test** (+7), lint +
> konvansiyon + prod build yeşil; kayıtlar: scope **§4.30** + §1.7a,
> `mock-ui-analysis §7.2` (480/520 eksikti — düzeltildi),
> `shared-ui-design-notes §2` (ghost tespiti — düzeltildi).
> Önceki: 2026-07-31 (**📦 ÜRÜN GÖRÜNTÜLEME CANLI (FR-PROD-01..02)**
> — `product-service` dev'e girdi (salt okunur Faz A), Customer Info'nun ürün
> bölümü **"Coming soon"dan çıkıp gerçek işlevine kavuştu**. Yeni kardeş feature
> **`features/product/`** (model / data / state / section), hesap modülünün
> kalıbı birebir izlendi. Ürünler **mock'taki yerinde** duruyor: genişleyen
> hesap satırının içinde (AC-PROD-01-01) — bunun için `shared/patterns/Table`'a
> **eklemeli** satır genişletme (`appTableExpansion` + `isExpanded`) geldi;
> şablon projelenmezse desen eskisiyle birebir aynı. **İki kardeş feature
> birbirini görmüyor:** `AccountSection` bir `rowExpansion` TemplateRef'i alıyor,
> context'i yalnız `accountNumber` **string**'i; kompozisyon tek yerde, Customer
> Info'da. **Sayfalama YOK** (FR-PROD-01 tanımlamıyor, uç düz dizi döndürüyor —
> `Pagination` kullanılmadı, `page`/`size` gönderilmiyor); **pasif ürünler
> listede kalıyor** (AC-PROD-01-03, istemci filtrelemiyor/sıralamıyor);
> kampanya kimliği **yalnız public `campaign_code`**; türetilen alanlar
> (durum/servis tipi/kampanya toplamı) sunucuda hesaplanıyor, istemci geleni
> gösteriyor; alt ürün ebeveyninin Service Address'ini gösteriyor — zincir
> backend'de yürünüyor, detay **tek GET**. FR-PROD-02 **modal** (AC-PROD-02-01
> "detail modal" olarak kayıtlı; mock'un ürün markup'ı bundle'da yok, düzen
> Customer Info'nun dokümante okuma grid'inden türetildi — kayıt: scope §4.28/4).
> `MSG-PROD-NONE` frontend-only (200 `[]`), `MSG-PROD-NOT-FOUND` (404) proje
> eklentisi olarak kataloğa + `i18n.spec.ts` backend listesine eklendi.
> **Kapsam dışı bırakıldı:** `Deactivate product` (yazma ucu yok → disabled,
> A3 kuralı tek kontrole uygulandı), Offer Selection / Product Configuration /
> Submit Order (üçü **tek bir satış akışı** — order-service yok, birlikte
> yapılacak), `GET /api/offers` + `/api/campaigns` (hazır ama tek tüketicisi o
> akış → istemci sarmalayıcısı yazılmadı). Teklif fiyatları **analist onayı
> bekliyor**; karar: UI backend ne dönerse gösterir (bu turda hiçbir fiyat
> ekranda yok). **301/301 test** (+25), lint + konvansiyon + prod build yeşil;
> kayıtlar: scope §1B, §4.28 + **FE-ADR-013 §Amendment B**.
> Önceki: 2026-07-29 (**🐞 HATA DÜZELTME TURU** — 6 bulgunun 4'ü
> düzeltildi, 2'si backend bağımlılığı olarak kayda geçti. Düzeltilenler:
> Customer Search'ün sonuç başlığı artık browse modunda **"All customers"**
> (yeni anahtar `UI-SEARCH-BROWSE-HEADING`; arama yapılınca "Search results");
> adres kartları ile "Add new address" döşemesi `auto-rows-fr` ile **birebir
> aynı yükseklikte**; müşteri listesi **kendi iç scroll'una** kavuştu (kopan
> halka ekranın host flex zinciriydi — `Table` deseni bilinçle scroll'suz,
> desende değil ekranda çözüldü); `shared/ui/TextInput`'a **`digitsOnly`**
> girdisi eklendi (fiili rakam kısıtı; `inputMode` yalnız klavye ipucuydu) ve
> Search'ün ID number / Customer ID / GSM ile Create'in Nationality ID alanına
> bağlandı; **mükerrer NAT ID artık adım 1'de** `LBL-NEXT`'te
> `GET /api/customers?nationalityId=` ile sorulup alan altında
> `MSG-CUST-DUP-NATID` ile engelleniyor — create sonundaki 409 **emniyet olarak
> duruyor** (yarış koşulu için). ⚡ **Bu tur backend'e de dokunuldu (ekip
> onayıyla, CLAUDE.md'nin "frontend görevinde backend'e dokunma" kuralının açık
> istisnası):** soft-deleted bir müşterinin NAT ID'si hiçbir okuma ucundan
> görünmediği için erken kontrol o vakayı kaçırıyordu; **ADR-005 §Addendum** ile
> `GET /api/customers/nationality-id-availability` eklendi — kuralı create
> yolunun kullandığı metotla yanıtlıyor, tek alan (`available`) döndürüyor,
> advisory. Artık **silinmiş müşterinin kimlik numarası da adım 1'de**
> yakalanıyor. Backend'e bağımlı kalanlar: **AC-ADDR-04-04 `MSG-ADDR-IN-USE`**
> kontrolü customer-service'te hâlâ **no-op** (scope §5.9 — FE hata-gösterme
> yolu hazır ve testli, istemci taklidi yazılmadı) ve **Account Description**
> alanı account-service kontratında **yok** (scope §1A.7 — fazladan alan 400
> `MSG-ACCT-IMMUTABLE-FIELD` ile reddediliyor). **FE 276/276 + BE 65/65 test**
> (origin/dev'in auth/logout PR'ı #17 merge edildikten sonraki sayı), lint +
> konvansiyon + prod build yeşil; kayıtlar: scope §4.26/§4.27, §5.9,
> §5.10 (kapandı), §1A.7 + ADR-005 §Addendum.
> Önceki: 2026-07-25 (**🏁 KAPSAM-İÇİ EKRANLARIN TAMAMI BİTTİ:
> Create Customer wizard yazıldı (FR-CUST-03, KR-10)** — 3 adımlı wizard
> (`/customers/new`): demografik + yerel adres taslakları (paylaşılan adres
> dialog'u `draft` modunda) + iletişim → **tek atomik POST**; MERNIS backend'de
> (istemci stub'a gitmez, ADR-009); 409 dup-natid/MERNIS/400/503 hataları
> alan+banner+adım-atlama ile. Yeni desen: **Stepper**. Search'ün "Create new
> customer" butonu aktifleşti. **256/256 test**, lint + konvansiyon + prod
> build yeşil; kayıt: scope §4.25. Aynı gün önce:
> **💳 HESAP BÖLÜMÜ CANLI (FR-ACCT-01..04, KR-11)** —
> Customer Info'nun hesap sekmesi gerçek işlevine kavuştu: liste (pasifler
> görünür kalır, sunucu sırası), oluştur/güncelle (KR-11 numara salt metin),
> sil=pasifleştir (409 guard'ları dialog'da). `features/account/section/`
> AccountSection — FE-ADR-003 §Consequences'ın öngördüğü tek kardeş-feature
> importuyla customer tüketiyor. Yeni desen: **StatusBadge**. Ürün bölümü hâlâ
> "Coming soon" (product-service yok). **242/242 test**, lint + konvansiyon +
> prod build yeşil; kayıtlar: scope §1A (tamamlandı) + §4.24. Aynı gün önce:
> **🎉 İKİNCİ İŞ EKRANI: Customer Info yazıldı** —
> `/customers/:customerNumber`: demografik görüntüle/düzenle (FR-CUST-02/04),
> adres CRUD + primary + şehir→ilçe cascade (FR-ADDR-01..05), iletişim
> görüntüle/güncelle (FR-CNTC-01/02), müşteri silme onay+toast (FR-CUST-05);
> hesap sekmesi F6'ya **rezerve** (API çağrısı yok), ürün bölümü "Coming soon"
> (A3). Yeni desenler: **Tabs, ConfirmDialog** (+Modal `headerless`).
> Search satır linki **aktifleşti** (§2A.5). **231/231 test**, lint +
> konvansiyon + prod build yeşil; kayıtlar: scope §4.22–4.23. Aynı gün önce:
> **`shared/patterns/` katmanı kuruldu** —
> Customer Search'ün inline desenleri 6 paylaşılan desene çıkarıldı: Table,
> EmptyState, Skeleton, Pagination (Search tüketiyor) + Toast, Modal (sonraki
> ekranlar için hazır); ekran davranışı/testid'ler birebir korundu, **201/201
> test**, lint + konvansiyon + prod build yeşil; kayıt: scope §4.21. Aynı gün
> önce: **🎉 İLK İŞ EKRANI: Customer Search yazıldı** —
> golden path uçtan uca canlı: browse + filtre + sayfalama + 12 durum;
> parametreli i18n (FE-ADR-012 §h) uygulandı; placeholder kaldırıldı.
> **169/169 test**, lint + konvansiyon + prod build yeşil. Önceki aynı gün:
> veri katmanı (customer/account/lookup — kontratlar Swagger'la 4/4 doğrulandı)
> ve `shared/ui` 7 EDS bileşeni. En eski: 2026-07-23 iskelet — Angular 22.0.8
> standalone + zoneless projesi oluşturuldu; sürümler exact pinlendi; ESLint
> (katman sınırı + quality kuralları) + Prettier; FE-ADR-003 klasör iskeleti;
> Tailwind 4 + EDS token teması (gerçek değerler) + Inter self-hosted; runtime
> i18n altyapısı (en/tr, 51 analist + 10 proje messageKey, feature-önekli UI
> katalogu). **build + lint + test (6/6) yeşil. Henüz hiç ekran/bileşen/servis
> yok — yalnız iskelet.** Önceki: karar-kayıt sistemi FE-ADR-001..013.)
>
> **Bu dosyayı güncel tut:** Her anlamlı değişiklikten sonra ilgili bölümü ve
> "Sırada Ne Var" listesini güncelle.

---

## 1. Proje Özeti

CRM Lite'ın Angular tabanlı web arayüzü. Backend'in `api-gateway` BFF'i
(`http://localhost:8080`) üzerinden konuşur; kendi kimlik doğrulaması **yoktur**
— oturum Keycloak + gateway tarafından yönetilir.

**Mevcut durum:** 🏗️ **İskelet kuruldu, ekran yok.** `frontend/` projesi
oluşturuldu; konfigürasyon, tema ve i18n altyapısı hazır ve **build+lint+test
yeşil**. Ekranlar/bileşenler henüz yazılmadı (bir sonraki faz).

- **Framework:** Angular **22.0.8** (standalone components, NgModule YOK)
- **Dil:** TypeScript **6.0.3** (strict mode, tüm katılık bayrakları açık)
- **Runtime:** Node.js **22.23.1** (LTS "Jod")
- **Stil:** Tailwind CSS **4.3.3** + Etiya EDS Lite token'ları
- **State:** Angular Signals + ince servis katmanı (NgRx YOK)
- **Bileşen kütüphanesi:** **YOK** — EDS bileşenleri kendimiz yazıyoruz
- **Konum:** `frontend/` (bu repo; ayrı repo DEĞİL)
- **Kapsam:** Customer Search + Create Customer + Customer Info
  (demografik/adres/iletişim) + **hesap bölümü (FR-ACCT — 2026-07-24'te kapsama
  girdi; FE-ADR-013 §Amendment A, `scope-and-conflicts.md` §1A)** + **ürün
  görüntüleme (FR-PROD-01..02 — 2026-07-31'de girdi; §Amendment B, §1B)**.
  **Satış ekranları (Offer Selection / Product Configuration / Submit Order)
  kapsam dışı** — üçü tek bir akış ve order-service yok; birlikte yapılacak.

**Doküman dili konvansiyonu** (bilinçli, backend'i taklit ediyor):
`FRONTEND_BRAIN.md` ve `docs/frontend/*.md` **Türkçe** (PROJECTBRAIN ile aynı);
`docs/frontend/adr/FE-ADR-*.md` **İngilizce** (`docs/architecture/adr/` ile aynı,
çünkü sürekli ADR-001..012'ye referans veriyorlar ve birlikte okunuyorlar).

---

## 2. Teknoloji Seçimleri (kesin sürümlerle)

> Aşağıdaki her numara **2026-07-23'te** npm registry, Node.js release index ve
> Docker Hub'dan **okundu** — hafızadan yazılmadı. Doğrulama komutları
> FE-ADR-002 §Verification bölümünde.

> **Tümü `package.json`'da EXACT pinli** (`^`/`~` yok), `package-lock.json`
> commit'li. Aşağıdakiler **kurulu gerçek sürümlerdir** (2026-07-23).

| Bileşen | **Pinlenen sürüm** | Not |
|---|---|---|
| `@angular/*` (core/common/forms/router/platform-browser/compiler) | **22.0.8** | Tümü tek sürüm |
| `@angular/cli`, `@angular/build`, `@angular/compiler-cli` | **22.0.8** | `ng new` en güncel 22.x'i (22.0.8) kurdu; tooling da 22.0.8'e hizalandı (FE-ADR-002 tablosu güncellendi) |
| **TypeScript** | **6.0.3** | ⚠️ **En güncel TS DEĞİL.** `@angular/compiler-cli` → `peerDependencies: { typescript: ">=6.0 <6.1" }`. En güncel TS **7.0.2** ve **uyumsuz** — yükseltme Angular'ın peer aralığına bağlı |
| **Node.js** | **22.23.1** | `engines` alanında sabit. "Jod" LTS, maintenance; EOL **2027-04-30** |
| `rxjs` | **7.8.2** · `tslib` **2.8.1** | |
| `tailwindcss` + `@tailwindcss/postcss` | **4.3.3** | `.postcssrc.json` ile Angular build'e bağlı; Angular 22 `@angular/build` yerleşik Tailwind desteği taşıyor |
| `@fontsource-variable/inter` | **5.3.0** | Inter variable, **self-hosted**; `index.css` tüm subsetleri (latin-ext = Türkçe) taşır. Build'de 7 woff2 asset üretiyor |
| `zone.js` | **YOK** | **Zoneless.** `provideZonelessChangeDetection()` app.config'te açık (FE-ADR-006 §7) |
| ESLint / angular-eslint / typescript-eslint | **10.7.0** / **22.1.0** / **8.62.1** | Flat config (`eslint.config.js`) |
| Prettier | **3.9.6** | `.prettierrc` |
| Test | **vitest 4.1.10** + jsdom 28.1.0 | Angular 22 `@angular/build:unit-test` builder'ı (yeni varsayılan) |
| Build/runtime image | `node:22.23.1-alpine` / `nginx:1.30.4-alpine` | Container (FE-ADR-010), henüz Dockerfile yazılmadı |

**Pinleme politikası:** `package.json`'da **kesin sürüm** yazılır (`^` yok, `~`
yok, `latest` asla), `package-lock.json` commit'lenir. Backend'in
`<spring-cloud.version>2025.1.2</spring-cloud.version>` disiplininin aynısı.

> ⚙️ **Ortam notu:** Node 22.23.1 makineye **ZIP dağıtımıyla** kuruldu
> (`C:\tools\node-v22.23.1-win-x64`), `~/.bashrc` PATH'e ekledi. **Yalnız Git
> Bash içinde geçerli** — sistem PATH'indeki `C:\Program Files\nodejs` (v23.11.1)
> duruyor; cmd/PowerShell hâlâ onu görür. `npm`/`ng`/`npx` komutlarını **Git
> Bash'te** çalıştır; VS Code terminal varsayılanı Git Bash (MINGW64) olmalı.
> (`scope-and-conflicts.md` §4.3)

### Bilinçli olarak KULLANILMAYANLAR

| Reddedilen | Neden | Karar |
|---|---|---|
| Angular Material / PrimeNG | Kendi görsel dillerini dayatıyorlar; EDS zaten tam tanımlı — sürekli override savaşı olur | FE-ADR-011 §c |
| Headless bileşen kütüphanesi | Çözdüğü problem (combobox/autocomplete/multi-select overlay'leri) mock'ta **yok**; tek `Select` paneli + `DatePicker` için orantısız | FE-ADR-011 §c |
| NgRx / Redux türevleri | Uygulama state'inin ezici çoğunluğu istek-kapsamlı; paylaşılan mutable state 3 kalem. Boilerplate karşılığında hiçbir şey alınmıyor | FE-ADR-006 §4 |
| `@angular/localize` (derleme-zamanı i18n) | Dil başına ayrı bundle → dil değişimi tam sayfa yeniden yükleme → **AC-LANG-01-02 ihlali** | FE-ADR-012 §a |
| Harici i18n kütüphanesi | Katalog küçük ve tam bilinen; signal'lar zaten gereken reaktiviteyi veriyor | FE-ADR-012 §a |
| Template-driven forms | Wizard'ın adımlar-arası/alanlar-arası kuralları ve `FormArray` ile pratik değil; tipli form yok | FE-ADR-007 §1 |
| CORS | Auth tasarımıyla aktif olarak çatışıyor (SameSite=Lax + `X-XSRF-TOKEN` yalnız same-origin) | FE-ADR-004 §4 |
| Ayrı frontend repo'su | Kontrat kayması; atomik revert imkânsız | FE-ADR-001 §3 |
| Hazır tablo bileşeni | Gereken davranış gösterim + zebra + tek link kolonu; sıralama sunucuda sabit | FE-ADR-011 §e |

---

## 3. Klasör Yapısı

> ✅ **Oluşturuldu.** Aşağıda `[✓]` = mevcut, `[ ]` = boş iskelet (`.gitkeep`),
> `[→]` = sonraki fazda gelecek dosya.

```
frontend/
├── [✓] package.json            # exact sürümler + engines: node 22.23.1
├── [✓] package-lock.json       # commit'li
├── [✓] .nvmrc                  # 22.23.1
├── [✓] tsconfig.json           # strict + strictTemplates + noUnusedLocals/Parameters
├── [✓] eslint.config.js        # katman sınırı + quality (flat config)
├── [✓] .prettierrc             # printWidth 100, singleQuote
├── [✓] .postcssrc.json         # @tailwindcss/postcss
├── [✓] proxy.conf.json         # ng serve → gateway:8080 (FE-ADR-004: /api,/oauth2,/login,/logout)
├── [✓] Dockerfile              # multi-stage node:22.23.1-alpine → nginx:1.30.4-alpine (FE-ADR-010)
├── [✓] nginx.conf              # SPA fallback + /api,/oauth2,/login,/logout → api-gateway:8080
└── src/
    ├── [✓] styles.css          # ⭐ Tailwind import + EDS @theme + Inter + z-index utility
    ├── [✓] main.ts / index.html
    ├── [✓] environments/       # environment.ts / .production.ts — SADECE build bayrağı (API host YOK, FE-ADR-004 §2)
    └── app/
        ├── [✓] app.ts / app.html / app.config.ts   # kök (router-outlet + providers)
        ├── [✓] app.routes.ts    # Shell + authGuard altında lazy feature'lar
        ├── [✓] layout/          # ⚠️ 4. klasör: uygulama kabuğu (§4.12) — shared olamaz (core enjekte ediyor)
        │   └── shell.ts / shell.html   # header + sidenav + main (mock §4)
        ├── core/                # singleton'lar; features'a ASLA bakmaz
        │   ├── [✓] i18n/        # runtime i18n altyapısı (§6B)
        │   │   ├── i18n.service.ts · translate.pipe.ts · language.ts · index.ts
        │   │   └── catalog/     # labels.ts (LBL) · messages.ts (MSG) · ui.ts (UI) · index.ts
        │   ├── [✓] auth/        # auth.service.ts (signals) · auth.guard.ts · session.model.ts
        │   ├── [✓] http/        # api-error.ts · field-errors.ts · 2 interceptor · provide-core-http.ts
        │   ├── [✓] lookup/      # ✅ 2026-07-25: LookupApiService (statuses/types/cities/districts)
        │   │                    #   + LookupCacheService (signal önbellek — FE-ADR-006 §1/§6 buraya atadı)
        │   │   └── lookup.model.ts · lookup-api.service.ts · lookup-cache.service.ts (+spec)
        │   └── [✓] catalog/   # ✅ 2026-08-03: ürün KATALOĞU (offers / campaigns /
        │       │              #   characteristics) — İKİ feature besliyor (product + satış
        │       │              #   sihirbazı); kardeş import yasak olduğu için core'da,
        │       │              #   emsal core/lookup (scope §2B.10). Hiçbir ucu sayfalamıyor
        │       └── catalog.model.ts · catalog-api.service.ts (+spec) · index.ts
        ├── shared/              # core'a ve features'a ASLA bakmaz
        │   ├── [✓] ui/          # ✅ 2026-07-25: 7 EDS bileşeni yazıldı (FE-ADR-011 §d; PasswordInput YOK)
        │   │   ├── icon/ button/ icon-button/ form-field/
        │   │   ├── text-input/ select/ date-picker/   # hepsi spec'li (98 test)
        │   │   └── index.ts     # barrel; metinler ÇÖZÜLMÜŞ girer (§4.14) — bileşen çeviri yapmaz
        │   └── [✓] patterns/    # ✅ 2026-07-25: DS'de olmayan kompozit desenler (FE-ADR-011 §d, mock §7.2)
        │       ├── table/ empty-state/ skeleton/ pagination/   # Customer Search tüketiyor
        │       │                    #   table: 2026-07-31 opsiyonel satır genişletme
        │       │                    #   (appTableExpansion + isExpanded) — eklemeli, §4.28/2
        │       ├── toast/ modal/ tabs/ confirm-dialog/   # Customer Info tüketiyor (modal: headerless varyantlı)
        │       │                    #   modal: 2026-08-03 `overflowVisible` girdisi (BUG-2 —
        │       │                    #   gövdenin overflow-y-auto'su Select panelini kırpıyordu)
        │       │                    #   + 4. boyut: sm 420 / md 480 / lg 560 / xl 680 (§4.30)
        │       │                    #   confirm-dialog: 2026-08-03 `tone` girdisi (danger|info)
        │       │                    #   — §2.7 submit onayı info daire + primary Yes (§2B.15)
        │       │                    #   pagination/page-size.ts: 2026-08-03 KR-04 whitelist'in
        │       │                    #   TEK kaynağı (PAGE_SIZE_OPTIONS · PageSize · isPageSize)
        │       ├── status-badge/    # ✅ 2026-07-25 (F6): nokta+metin durum rozeti (mock §6.4)
        │       ├── stepper/         # ✅ 2026-07-25 (F7): pasif wizard göstergesi (mock §6.3)
        │       └── index.ts         # barrel; sözleşme shared/ui ile aynı (saf sunum, çözülmüş metin,
        │                            #   testId zorunlu; kayıt: scope §4.21/4.23); stepper/badge/card → ekranı gelince
        └── features/
            ├── [✓] access-denied/           # 403 rol reddi sayfası (MSG-AUTH-FORBIDDEN)
            ├── customer/
            │   ├── [✓] customer.routes.ts   # lazy chunk kökü ('' Search, ':customerNumber' Detail,
            │   │                            #   ':customerNumber/sales/new' satış sihirbazı)
            │   ├── [✓] search/              # ✅ Customer Search (golden path) — satır linki artık Detail'e gider
            │   ├── [✓] detail/              # ✅ 2026-07-25: Customer Info (tabs; demografik+iletişim dialogları,
            │   │                            #   silme ConfirmDialog; hesap sekmesi F6'ya rezerve, ürün Coming soon)
            │   ├── [✓] create/              # ✅ 2026-07-25 (F7): 3 adımlı wizard — atomik POST; adresler
            │   │                            #   yerel taslak; hata→adım atlama; başarıda flash→Info toast'ı
            │   ├── [✓] address/             # ✅ ALT MODÜL (FE-ADR-003 §3): AddressCards + AddressFormDialog
            │   │                            #   (Detail API modunda, Create `draft` modunda tüketiyor)
            │   ├── [ ] contact/             # alt modül klasörü boş — iletişim dialog'u şimdilik detail/ içinde
            │   ├── [✓] model/               # ✅ 2026-07-25: Customer/Address/Contact/Page/filter tipleri
            │   ├── [✓] data/                # ✅ CustomerApiService (liste+filtre, CRUD, adres, iletişim) (+spec)
            │   ├── [✓] state/               # ✅ CustomerListStore (+spec) · CustomerDetailStore (3 bağımsız
            │   │                            #   bölüm okuması + mutasyonlar) · CustomerFlashService (eds_flash karşılığı)
            │   └── [✓] sales/               # ✅ 2026-08-03: §2.7 SATIŞ SİHİRBAZI (FR-SALE-01/02) —
            │                                #   sale-wizard (tek route, 3 adım; üç store'u BURADA
            │                                #   provide eder, root'ta değil) + offer-selection-step +
            │                                #   product-configuration-step + submit-order-step (+spec).
            │                                #   features/order/ altında DEĞİL: adım 2 müşterinin
            │                                #   adreslerini + FR-ADDR-02 dialog'unu kullanıyor, orada
            │                                #   olsa order→customer kardeş yönü açılırdı (scope §2B.9)
            ├── [✓] account/                 # ✅ 2026-07-25: FR-ACCT (kardeş feature) — F6'da bölüm canlı
            │   ├── [✓] model/               #   AccountResponse/Create/Update — K-8: 223 YOK, KR-11 salt okunur
            │   ├── [✓] data/                #   AccountApiService (/api/accounts/**, 224-only) (+spec)
            │   ├── [✓] state/               #   AccountListStore (+mutasyonlar: create/update/delete → reload) (+spec)
            │   └── [✓] section/             # ✅ F6: AccountSection + AccountFormDialog (+spec) — Customer
            │                                #   2026-08-03 (BUG-1): + BillingAddressPanel — modalın İÇİNDE
            │                                #   genişleyen "New address" paneli. Adresi KENDİ oluşturmaz
            │                                #   (FR-ADDR-02 customer-service'in): draft yukarı çıkar,
            │                                #   Customer Info addAddress() ile yazar (scope §4.30)
            │                                #   Info'nun hesap sekmesine expose edilen bileşen (FE-ADR-003
            │                                #   §Consequences); adresler BillingAddressOption ile yukarıdan girer.
            │                                #   2026-07-31: opsiyonel `rowExpansion` TemplateRef girdisi —
            │                                #   context YALNIZ accountNumber string'i (product'ı hiç görmez)
            ├── [✓] product/                 # ✅ 2026-07-31: FR-PROD-01..02 (kardeş feature) — SALT OKUNUR
            │   ├── [✓] model/               #   ProductRow / ProductDetail / ProductServiceAddress —
            │   │                            #   sayfalama zarfı YOK, yazma tipi YOK (Faz A)
            │   ├── [✓] data/                #   ProductApiService — tam 2 GET (+spec); /api/offers ve
            │   │                            #   /api/campaigns bilinçle sarmalanmadı (§1B.10)
            │   ├── [✓] state/               #   ProductListStore (+spec) · ProductDetailStore (404 notFound
            │   │                            #   ayrı durum) — filtreleme/sıralama/sayfalama YOK
            │   └── [✓] section/             #   ProductSection (+spec) + ProductDetailDialog — genişleyen
            │                                #   hesap satırının içinde render edilir; girdi tek: accountNumber
            └── [✓] order/                   # ✅ 2026-08-03: SİPARİŞ DOMAİNİ (kardeş feature) —
                ├── [✓] model/               #   OrderItem/SubmitOrder/Order + BasketItem +
                │                            #   REQUIRED_SERVICE_TYPES; liste/iptal tipi YOK (uç yok)
                ├── [✓] data/                #   OrderApiService — tam 2 metot: submit + getByNumber
                └── [✓] state/               #   SaleBasketStore (+spec) — Signal-only, HİÇBİR Storage;
                                             #   OrderSubmitStore (+spec) — çift submit kilidi +
                                             #   HTTP status'a göre hata haritası (scope §2B.12)
```

**Katman lint'i doğrulandı:** `shared/`'dan `core/`'a import denemesi ESLint
tarafından FE-ADR-003 mesajıyla **reddediliyor** (kanıtlandı, 2026-07-23).

**İçe aktarma yönü tek yönlüdür:** `features → core | shared`, `core → shared`.
`shared` hiçbir şeye bakmaz. Feature'lar birbirine bakmaz.

**`address/` ve `contact/` neden alt modül?** Backend **ADR-001** adres ve
iletişimi ayrı servis yapmayı bilinçle reddetti — bunlar customer agregatının
iç modülleri. Frontend, backend'in reddettiği sınırı icat etmez. (FE-ADR-003 §3)

---

## 4. Backend Bağlantı Noktaları (özet)

Tam kontrat: `docs/api/customer-service.md`, `docs/api/authentication.md`,
`docs/frontend/mock-ui-analysis.md` §5A.4.

| Konu | Kural |
|---|---|
| **Taban URL** | **YOK.** Tüm çağrılar göreli path (`/api/...`). `environment.ts`'te API host'u bulunmaz (FE-ADR-004 §2) |
| **Proxy'lenen prefix'ler** | `/api`, `/oauth2`, `/login`, `/logout` — hem `ng serve` hem nginx |
| **Oturum** | Tek kaynak `GET /api/session/me`. Token tarayıcıya **hiç gelmiyor** |
| **CSRF** | Angular `HttpClient` varsayılanı yeterli (`XSRF-TOKEN` → `X-XSRF-TOKEN`) |
| **Login** | `/oauth2/authorization/keycloak`'a **tam sayfa yönlendirme** |
| **Logout** | `POST /logout` + CSRF header, tam sayfa navigasyon |
| **Liste** | `GET /api/customers?page=0&size=15`; **`sort` parametresi YOK** (sıralama sunucuda sabit) |
| **Sayfa boyutu** | Varsayılan **15**; API **yalnız 15/30/50** kabul eder, başkası 400 `MSG-VALIDATION-ERROR` (KR-04, ADR-005 §Amendment). Tek kaynak: `shared/patterns/pagination/page-size.ts` — hiçbir ekran sayfa boyutu literal'i yazmaz (scope §4.29) |
| **Tarih** | Taşımada daima ISO `YYYY-MM-DD`; gösterimde `dd.MM.yyyy` |
| **Hata** | `messageKey` kullanılır; backend'in `message` alanı **kullanıcıya asla gösterilmez** |
| **Detay ≠ liste değil** | Detay endpoint'inde adres/iletişim **yok** → Customer Info 3 ayrı çağrı yapar |
| **Satış uçları sayfalamaz** | `GET /api/offers`, `/api/campaigns`, `/api/offers/{id}/characteristics` düz dizi döndürür, query param almaz → filtreleme **istemcide** (scope §2B.4/§2B.14) |
| **`POST /api/orders` idempotent DEĞİL** | İkinci istek İKİNCİ ürün kümesi yaratır (ADR-016 §5.3b). İstemci iki bağımsız kilit uygular: `OrderSubmitStore.locked()` + butonun kaldırılması. Başarılı submit **asla** kilidi açmaz |
| **Sipariş yanıtı adı/adresi geri vermez** | `OrderItemResponse` yalnız `offerId`/`productId`/`amount` (ADR-016 §3) → Submit ekranı adları kendi sepetinden, tutarları `201`den okur |

> **Dev proxy (FE-ADR-004 uygulandı, 2026-07-23).** `ng serve` artık
> `proxy.conf.json` ile `/api`, `/oauth2`, `/login`, `/logout` prefix'lerini
> `http://localhost:8080`'e (gateway) yönlendiriyor. Tarayıcı **tek origin**
> (`:4200`) görür → CORS yok; `SameSite=Lax` oturum çerezi ve `X-XSRF-TOKEN`
> çalışır. Bu, backend **ADR-007/008**'in BFF + aynı-origin tasarımının frontend
> karşılığıdır (ADR-008 §4 tam olarak bu proxy'yi öneriyor). OAuth yönlendirme
> zincirinin `:4200`'de kalması için proxy `xfwd: true` ile `X-Forwarded-*`
> header'larını iletir (gateway `forward-headers-strategy: framework`, §Addendum).
> `environment.*.ts` yalnızca `production` bayrağı taşır; **API host içermez** —
> host değişikliği yalnız `proxy.conf.json` + `nginx.conf`'ta, sıfır satır TS.

> **Hata + auth akışı (FE-ADR-008/005 uygulandı, 2026-07-24).** `core/http`
> her `HttpErrorResponse`'u tek tip `ApiError { status, messageKey, fieldErrors,
> … }`'a çevirir; kullanıcıya giden tek metin kaynağı **`messageKey`** (i18n
> kataloğundan çözülür), ham backend `message`'ı **asla** DOM'a sızmaz — yalnız
> log'a. Alan hataları (`validationErrors`) normalize edilir: değeri katalog
> anahtarı olanlar (ör. `MSG-VAL-EMAIL`) doğrudan, ham İngilizce olanlar
> `UI-FIELD-INVALID`'e düşürülür. Interceptor sırası **[auth (dış), normalize
> (iç)]**; auth katmanı 401 (login yönlendir) / 403-CSRF (probe tazele + 1 kez
> tekrar) / 403-FORBIDDEN (yönlendirme yok) üçünü **`messageKey` ile ayırır**.
> Zincir: `messageKey → i18n.translate() → ekran`. Detay: FE-ADR-008 §5,
> FE-ADR-005 §4.

---

## 5. Sırada Ne Var (Roadmap / Öncelik)

### 5.1 Bloke edenler
- [x] ~~**Node 22.23.1 kurulumu**~~ ✅ ZIP dağıtımıyla kuruldu
      (`C:\tools\node-v22.23.1-win-x64`, Git Bash PATH); build/lint/test bu
      sürümle geçti (`scope-and-conflicts.md` §4.3)
- [x] ~~**OAuth redirect origin**~~ ✅ **ÇÖZÜLDÜ (23.07.2026)** — gateway'e
      `forward-headers-strategy: framework`; realm'e `:4200` redirect URI +
      webOrigin + post-logout URI; `keycloak-init` her `up`'ta yeniden
      uyguluyor; nginx `$host`'u geçiriyor. Çalışan stack'te doğrulandı —
      PKCE `S256` ve `directAccessGrants=false` korunuyor
      (FE-ADR-004 §Addendum)
- [x] ~~**Frontend host portu**~~ ✅ **4200** (FE-ADR-010 §5)

> ⚠️ **Gateway'in yeni config'i alması için `config-server` ve `api-gateway`
> yeniden build + deploy edilmeli** — `forward-headers-strategy` config-repo'da,
> yani config-server'ın classpath'ine gömülü. Keycloak tarafında `up` yeterli
> (`keycloak-init` her seferinde uyguluyor, mevcut `keycloak_db` dahil).

### 5.2 İskelet ✅ TAMAMLANDI (2026-07-23)
- [x] ~~`ng new` standalone + zoneless + strict + routing~~
- [x] ~~Exact sürüm pinleme + `.nvmrc` + `package-lock.json`~~
- [x] ~~Tailwind 4 + EDS token teması (gerçek değerler) + Inter self-hosted~~
- [x] ~~ESLint (katman sınırı + quality) + Prettier — kurallar kanıtlandı~~
- [x] ~~`core/i18n/` runtime i18n altyapısı + katalog bütünlük testi~~
- [x] ~~`proxy.conf.json` (dev proxy → gateway:8080) + `environment.*` (yalnız build bayrağı, API host YOK)~~ ✅ 2026-07-23
- [x] ~~`core/http` (`ApiError` normalizasyonu + auth/CSRF interceptor'ları) + `core/auth` (`AuthService` + session probe)~~ ✅ 2026-07-24 — kanıt testi 13/13
- [x] ~~Routing iskeleti + `authGuard`/`crmUserGuard` + uygulama kabuğu (header/sidenav/dil/logout) + 403 sayfası~~ ✅ 2026-07-24 — lazy chunk'lar build'de doğrulandı, 26/26 test
- [x] ~~Test altyapısı (component testi kanıtlandı) + `data-testid`/i18n denetimi (`npm run check:conventions`)~~ ✅ 2026-07-24 — 31/31 test; `docs/frontend/testing-conventions.md`

### 5.2b 🔴 ÖNCE BUNLAR — ekran yazımını bloke ediyor
Sonraki iş kaleminin **ilk iki adımı**. İkisi de küçük, ikisi de olmadan
Customer Search yazılamaz (`customer-search-analysis.md` §10):

- [x] ~~**`Page` zarfının gerçek JSON şekli** (§4.4)~~ ✅ **KAPANDI (2026-07-25):**
      çalışan stack'in Swagger'ı (customer-controller) düz `PageImpl` şeklini
      doğruladı — metadata üst seviyede + `first/last`; tip `page.model.ts`'te
      birebir, sunucu bayrakları kullanılıyor. ⚠ Backend'e `serialization-mode`
      pinleme önerisi geçerli (şekil framework varsayılanı, §4.4).
      **Ekran yazımını bloke eden madde kalmadı.**
- [x] ~~**Parametreli i18n**~~ ✅ **UYGULANDI (2026-07-25, FE-ADR-012 §h):**
      `translate(key, params?)` + `t` pipe argümanı; isimli yer tutucular
      (`UI-PAGINATION-RANGE` canlı örnek), eksik parametre warn + yerinde kalır,
      spec'te EN↔TR yer tutucu eşitliği iddiası (`scope §4.13`)

### 5.3 Bileşen katmanı → tasarım notu hazır
📄 `docs/frontend/shared-ui-design-notes.md` — API, varyant, durum, erişilebilirlik
ve `data-testid` sözleşmeleri yazıldı. Yazım sırası:

- [x] ~~`Icon` → `Button` → `IconButton` → `FormField` → `TextInput` → `Select`~~
      ✅ **2026-07-25 yazıldı** — ikon verisi mock bundle'ın lucide shim'inden
      **birebir** çıkarıldı (38 ikon); tüm metin girdileri çözülmüş string
      (`'KEY' | t`, `scope-and-conflicts §4.14`); CVA bileşenlerinde `disabled`
      YOK — tek kaynak `control.disable()` (FormControlDirective çakışması)
- [x] ~~`DatePicker`~~ ✅ **2026-07-25 yazıldı** — **custom panel** kararıyla
      (§4.15): maskeli `DD.MM.YYYY` + takvim popover; form değeri daima ISO;
      ay/gün adları `Intl` + `locale` girdisi; roving-tabindex klavye gezinme
- [x] ~~`shared/patterns/` — tablo, boş durum, sayfalama, toast, skeleton~~
      ✅ **2026-07-25 yazıldı** — 6 desen: `Table` (kolon tanımı + hücre
      şablonları dışarıdan, iş-anahtarlı satır testid), `EmptyState`,
      `Skeleton`, `Pagination` (0-tabanlı `pageChange`, kesme kuralı tek yerde),
      `Toast` (6sn otomatik kapanma, `role="status"`) ve `Modal` (odak tuzağı +
      Escape + `aria-modal`, 420/560/680 tema token'ları) — Modal/Toast sonraki
      ekranlar için önceden kuruldu. Customer Search inline desenlerinden
      refactor edildi, ekran davranışı birebir; uygulama kayıtları scope §4.21.
      Stepper/tabs/badge/card ekranları gelince
- [x] ~~Her bileşen kendi spec'iyle gelir~~ ✅ 7 ui bileşeni + 6 desen / 13 spec —
      toplam **201/201 test**, lint + `check:conventions` + prod build yeşil
      (2026-07-25)

### 5.4 Ekranlar (kapsam içi)
- [x] ~~**Customer Search**~~ ✅ **YAZILDI (2026-07-25)** — golden path canlı:
      `features/customer/search/` (component + şablon + 14 testlik spec).
      Browse modu açılışta (AC-CUST-01-00), Search boşken pasif (AC-CUST-01-02),
      KR-01 parametre eşlemesi (istemci yeniden uygulamaz), KR-04 `size` daima
      açık, 12 durumun tümü (skeleton/boş×2/hata bandı/yeniden yükleme çubuğu…),
      ~~501 filtreleri disabled~~ (**05.08.2026: Account/Order Number filtreleri
      AKTİF** — backend KR-02'yi gerçekten çözüyor, scope §1.7c).
      Placeholder ekran + `UI-PLACEHOLDER-*` kaldırıldı;
      rota `''` artık Search. Satır linki Detail gelene kadar **pasif** (§2A.5),
      "Create new customer" **disabled** (wizard gelince `routerLink`). Uygulama
      kayıtları: `scope-and-conflicts §4.20`
- [x] ~~**Create Customer**~~ ✅ **YAZILDI (2026-07-25, F7)** —
      `features/customer/create/`: mock §6.3 birebir (h1 + Stepper + wizard
      kartı). Adım 1 demografik (8 alan; VR-NAME/NATID/BIRTHDATE/AGE Next'te),
      adım 2 adresler (paylaşılan AddressCards + AddressFormDialog **draft
      modunda** — API'siz yerel taslaklar, tam-biri-primary), adım 3 iletişim
      (VR-EMAIL/MOBILE/PHONE) → **tek atomik `POST /api/customers`** (kısmi
      istek yok). MERNIS backend'de (KR-10); 409 `MSG-CUST-DUP-NATID` (ADR-003)
      + MERNIS 400/503 + alan-bazlı 400'ler i18n'le, sahibi olan adıma
      atlayarak. Başarı: flash → yeni müşterinin Info sayfasında toast.
      Kayıt: scope §4.25
- [x] ~~**Customer Info**~~ ✅ **YAZILDI (2026-07-25)** — `features/customer/detail/`:
      4 sekme (mock §6.4 birebir). Demografik görüntüle/düzenle (FR-CUST-02/04,
      DÜZ PUT gövdesi), adres liste/ekle/düzenle/sil/primary (FR-ADDR-01..05;
      cascade `core/lookup`'tan; silme onayı ConfirmDialog, 409 guard'ları
      dialog hata durumunda), iletişim görüntüle/güncelle (FR-CNTC-01/02,
      Email→Home→Mobile→Fax sırası), müşteri silme (FR-CUST-05: onay → 204 →
      flash toast ile Search'e dönüş; 409 `MSG-CUST-HAS-PRODUCTS` mock'un hata
      durumu). **Hesap sekmesi F6'ya rezerve** (düzen var, API çağrısı YOK —
      §4.23/1); ürün bölümü **"Coming soon"** inert (A3). 5 `MSG-ACCT-*`
      anahtarı kataloğa çoktan eklendi (§1A.6 kapandı — `messages.ts`).
      **+ F6 (aynı gün): hesap sekmesi CANLI** — `features/account/section/`
      AccountSection: liste (pasifler görünür, sunucu sırası korunur,
      AC-ACCT-01-03/04), oluştur (`{customerId, accountName, addressId}` —
      tip seçilemez, K-8 sunucuda), güncelle (yalnız `{accountName, addressId}`;
      KR-11 numara dialog'da salt metin), sil=pasifleştir (ConfirmDialog; 409
      `MSG-ACCT-HAS-PRODUCTS`/`MSG-ACCT-NOT-ACTIVE` dialog hata durumunda —
      istemci taklit etmez). Kayıt: scope §4.24
- [x] ~~**Ürün görüntüleme (Customer Info ürün bölümü)**~~ ✅ **YAZILDI
      (2026-07-31, FR-PROD-01..02)** — `features/product/`: liste genişleyen
      hesap satırının içinde (AC-PROD-01-01), kolonlar Product ID / Product name
      / Campaign name / Campaign ID (public `campaign_code`) / Status / Action;
      **pasifler listede kalır**, **sayfalama yok**, kampanyasız üründe `-`.
      `eye` → FR-PROD-02 detay **modal**'ı (offer name/id, spec id, campaign,
      service address — alt üründe ebeveynin adresi, tek GET); `ban`
      (Deactivate) **disabled** (yazma ucu yok). Boş hesap → `MSG-PROD-NONE`
      (frontend-only), bilinmeyen ürün → 404 `MSG-PROD-NOT-FOUND`.
      `shared/patterns/Table`'a eklemeli satır genişletme geldi. Kayıtlar:
      scope §1B + §4.28, FE-ADR-013 §Amendment B
- [x] ~~**Ürün satış sihirbazı (§2.7)**~~ ✅ **YAZILDI (2026-08-03, FR-SALE-01/02)** —
      `features/customer/sales/`: TEK route
      `/customers/:customerNumber/sales/new?accountNumber=…`, üç adım
      (Offer Selection / Product Configuration / Submit Order), mock `.dc.html`
      düzenleri birebir. Sipariş **domaini** `features/order/` (model +
      `OrderApiService` + `SaleBasketStore` + `OrderSubmitStore`), katalog
      **`core/catalog/`** (iki feature'ı besliyor — `core/lookup` emsali).
      Sihirbaz `features/customer/` altında, çünkü adım 2 müşterinin adreslerini
      ve FR-ADDR-02 dialog'unu kullanıyor; `features/order/` altında olsa yeni
      bir `order → customer` kardeş yönü açılırdı (FE-ADR-003; scope §2B.9).
      **Sepet hiçbir yere yazılmaz** — Signal-only, wizard-kapsamlı store,
      akıştan çıkınca yok olur (AC-SALE-01-16); mock'un `eds_sale_*`
      localStorage'ı taşınmadı. **Çift submit iki bağımsız kilitle** engelleniyor
      (`POST /api/orders` idempotent DEĞİL — ADR-016 §5.3b). Order Number
      submit'e kadar `—` (scope §3.6 seçenek (a)); başarı **yerinde** gösteriliyor
      (§2B.8). Giriş noktası: genişleyen hesap satırında `LBL-START-NEW-SALE`,
      Aktif hesapta etkin / Pasif hesapta disabled (AC-SALE-01-01/02).
      Kayıtlar: scope §2B.9–2B.16

### 5.5 Container
- [x] ~~`frontend/Dockerfile` (multi-stage) + `nginx.conf`~~ ✅ 2026-07-24
- [x] ~~`infra/docker-compose.yml`'e **yalnız yeni servis bloğu** ekle~~ ✅ 2026-07-24 — `git diff`: **35 ekleme, 0 silme** (kanıtlandı)
- [ ] PROJECTBRAIN §10'daki "Compose 8 servis" notu **9** olmalı — backend dokümanı, frontend'den düzenlenmiyor (§5.8)

### 5.6 Karar bekleyenler (bloke etmiyor)
- [x] ~~zone.js vs zoneless~~ → **zoneless** (uygulandı) ·
      ~~import-boundary lint~~ → **eklendi** (FE-ADR-003, kanıtlandı) ·
      ~~katalog bütünlük testi~~ → **eklendi** (`i18n.spec.ts`)
- [ ] CI wiring (§4.6) · `data-testid` lint yok-kararı (§4.8, bilinçli)
- [ ] Header rol metni (§2.20 — kaldırılacak) · Sidenav 11px (caption'a)
- [x] ~~Per page listesi~~ ✅ **15/30/50, varsayılan 15** (KR-04 whitelist'i;
      ~~20/50/100~~ geçersiz). Tek kaynak `shared/patterns/pagination/page-size.ts`;
      `Pagination` deseni `PageSize` tipiyle çalışır, whitelist dışı değer
      **derlenmez** (scope §2.3/§2.4 + §4.29)

---

## 6. Tasarım Referansı

> ⭐ **`docs/frontend/mock-ui-analysis.md` — CRM Lite frontend tasarımının TEK
> REFERANS KAYNAĞIDIR.**

Analistlerden gelen mock UI bundle'ından
(`docs/source/mock-ui/Guncel_Etiya_CRM_Lite_Full_App.html`) çıkarılıp
**2026-07-23'te doğrulanmıştır** (bundle açıldı, 7 ekran + token CSS'leri +
EDS bileşen bundle'ı incelendi).

**Bağlayıcı kurallar:**

- Renk, boşluk, tipografi, yarıçap, elevation, motion ve layout değerleri
  **oradaki tablolardan** alınır. Değer **uydurulmaz**, mock HTML'inden yeniden
  yorumlanmaz.
- Mock HTML'inin **satır içi CSS fallback'lerinin bir kısmı gerçek token
  değerleriyle uyuşmuyor** (`var(--eds-space-10, 48px)` ama token 40px;
  `--eds-type-title-size` diye bir token hiç yok). Daima **token dosyası
  değeri** esastır — §0'daki düzeltme tablosu.
- Ekran düzeni, tablo kolonları, buton yerleşimi, doğrulama mesajları ve boş
  durum metinleri §6'daki ekran detaylarından alınır.
- Bir değer yanlışsa **önce `mock-ui-analysis.md` düzeltilir**, sonra kod yazılır.
- **Mock görsel referanstır, veri sözleşmesi değildir.** Alan adı, tip, format
  veya arama semantiği çakışmasında **backend kontratı kazanır** (§5A,
  FE-ADR-013 §e).
- Turuncu zemin üzerine **asla beyaz metin** yazılmaz
  (`--eds-color-text-on-brand` = `#242441`).
- Bileşenler yalnız **semantic** token (`--eds-color-*`) tüketir; primitive
  paletler (`--eds-orange-*`, `--eds-ink-*`) doğrudan kullanılmaz.
- **z-index literal'i yasak** — yalnız `--eds-z-*` token'ları (§2.15).
- **`data-testid` her etkileşimli elemanda zorunludur**; mock'ta olmaması
  gerekçe değildir (§8, FE-ADR-009).
- **Kullanıcıya görünen hiçbir metin şablona gömülmez** — tamamı i18n
  kataloğundan gelir (§6B, FE-ADR-012).

---

## 6A. EDS Token → Tailwind Sınıfı Haritası

> ⭐ **Sonraki ekranlarda referans alınacak harita.** Değerler
> `src/styles.css` `@theme` bloğunda tanımlı; her biri
> `docs/frontend/mock-ui-analysis.md` §2'deki gerçek değer.
>
> 🔴 **Kural (FE-ADR-011 §f):** yalnız aşağıdaki sınıflar kullanılır. Keyfi hex
> (`bg-[#3B82F6]`) veya keyfi piksel (`p-[13px]`, `w-[137px]`) **yasak**.
> Primitive palet (orange/ink rampaları) bilinçli olarak expose edilmedi — bir
> `bg-orange-500` utility'si **yoktur**, çünkü componentler yalnız semantic
> token tüketir (FE-ADR-011 §b).

### Renk (utility bağlamı: `bg-*`, `text-*`, `border-*`)

| Tailwind sınıf kökü | Değer | EDS semantic token |
|---|---|---|
| `brand` | `#F58220` | action-primary-bg, border-focus, border-selected |
| `brand-hover` | `#DB7013` | action-primary-bg-hover |
| `brand-active` | `#B85C0D` | action-primary-bg-active, text-brand (link) |
| `on-brand` | `#242441` | action-primary-text (**turuncu üstüne asla beyaz**) |
| `page` | `#F7F7FB` | bg-page |
| `surface` | `#FFFFFF` | bg-surface |
| `sunken` | `#EFEFF6` | bg-surface-sunken, bg-disabled, action-disabled-bg |
| `selected` | `#FEF6EE` | bg-selected |
| `inverse` | `#242441` | bg-inverse (navy) |
| `overlay` | `rgba(36,36,65,.5)` | bg-overlay (modal karartma) |
| `ink` | `#242441` | text-primary |
| `ink-soft` | `#57577E` | text-secondary |
| `ink-muted` | `#6E6E96` | text-tertiary |
| `ink-faint` | `#9C9CBC` | text-placeholder, text-disabled, action-disabled-text |
| `on-inverse` | `#FFFFFF` | text-inverse |
| `line` | `#DEDEEB` | border-default |
| `line-strong` | `#C6C6DB` | border-input |
| `line-hover` | `#9C9CBC` | border-hover |
| `secondary-action` | `#313152` | action-secondary-text (eklendi 2026-07-25, §4.16) |
| `success` / `success-fg` / `success-surface` / `success-border` | `#16A34A` / `#15803D` / `#F0FDF4` / `#BBF7D0` | feedback+status success (icon/text/bg/border) |
| `danger` / `danger-hover` / `danger-fg` / `danger-surface` / `danger-border` | `#DC2626` / `#B91C1C` / `#B91C1C` / `#FEF2F2` / `#FECACA` | danger (icon+action / hover / text / bg / border) |
| `warning` / `warning-fg` / `warning-surface` / `warning-border` | `#CA8A04` / `#854D0E` / `#FEFCE8` / `#FDE68A` | warning |
| `info` / `info-fg` / `info-surface` / `info-border` | `#2563EB` / `#1D4ED8` / `#EFF6FF` / `#BFDBFE` | info |

Örnek kombinasyonlar: birincil buton `bg-brand text-on-brand`; kart
`bg-surface border border-line rounded-lg`; hata kutusu
`bg-danger-surface border border-danger-border text-danger-fg`; danger buton
`bg-danger text-white`.

### Ölçü, tipografi, efekt

| Kategori | Sınıflar | Değer haritası |
|---|---|---|
| **Boşluk** (`p-`,`m-`,`gap-`,`h-`,`w-`) | `1 2 3 4 5 6 8 10 12` | 4·8·12·16·20·24·32·**40**·48px (Tailwind 4px tabanı EDS ile birebir). Ara adım (`p-7`,`p-9`,`p-11`) **kullanılmaz** |
| **Kontrol yüksekliği** | `h-8` `h-10` `h-12` | 32 / **40 (varsayılan)** / 48px |
| **Yarıçap** | `rounded-sm md lg full` | 4 / 6 / 8 / 999px |
| **Yazı tipi** | `font-sans` `font-mono` | Inter Variable / JetBrains Mono (mono fallback — woff2 henüz yok) |
| **Ağırlık** | `font-regular medium semibold` | 400 / 500 / 600 (**700+ yok**) |
| **Font boyutu** (+line-height) | `text-caption label body-sm code body body-lg h3 h2 h1 display` | 12/12/13/13/14/16/16/20/24/32px |
| **Elevation** | `shadow-e0 e1 e2 e3 e4` | none / header / dropdown / modal / toast |
| **Süre** | `duration-100 150 200 300 400` | ms |
| **Easing** | `ease-standard enter exit` | EDS cubic-bezier'leri |
| **z-index** | `z-sticky-header z-dropdown z-overlay z-modal z-toast z-tooltip` | 100/1000/1300/1400/1500/1600 (**sayısal literal yasak**) |
| **Tabular rakam** | `.eds-tabular-nums` sınıfı | ID/tarih/tutar kolonları için zorunlu |
| **Focus halkası** | `shadow-focus-ring` (form kontrolleri) / `shadow-focus-ring-offset` (buton `focus-visible`) | §2.6 gerçek değerleri; eklendi 2026-07-25 (§4.16) |
| **Animasyon** | `animate-eds-spin` (900ms, spinner — `data-eds-motion-exempt` ile) / `animate-panel-in` (200ms ease-enter, dropdown/datepicker panel girişi) | §2.14/§3.7; eklendi 2026-07-25 (§4.16) |

---

## 6B. i18n Kullanımı (FE-ADR-012)

**Altyapı:** `src/app/core/i18n/`. Signal tabanlı, dış kütüphane yok, zoneless
uyumlu.

**Şablonda çeviri** — `t` pipe (impure; dil değişince otomatik günceller):
```html
<button [attr.data-testid]="'customer-search-submit-button'">
  {{ 'LBL-SEARCH' | t }}
</button>
<h1>{{ 'UI-SEARCH-TITLE' | t }}</h1>
```

**TS'te çeviri** — `I18nService.translate(key)`:
```ts
private readonly i18n = inject(I18nService);
const msg = this.i18n.translate(apiError.messageKey); // MSG-* → yerelleştirilmiş metin
```

**Dil değiştirme** (AC-LANG-01-02 anında, AC-LANG-01-03 kalıcı):
```ts
this.i18n.setLanguage('tr');        // localStorage'a yazar, tüm ekran anında TR
const lang = this.i18n.lang();      // read-only signal
```

**Üç katalog, tek çatı** (`core/i18n/catalog/`):
| Dosya | Önek | Kaynak | Sayı |
|---|---|---|---|
| `labels.ts` | `LBL-*` | analist (docx §4) | 21 |
| `messages.ts` | `MSG-*` | analist (docx §3, 21) + **proje-yazımı** (10, işaretli) | 31 |
| `ui.ts` | `UI-*` | proje (mock EN metinleri) | ~55 tohum |

**Bağlayıcı kurallar:**
- `MSG-*` / `LBL-*` isimleri **asla değiştirilmez** — backend/analist kontratı;
  `error.messageKey` ile birebir eşleşir.
- Backend'in `message` alanı **kullanıcıya gösterilmez** — metin daima
  `messageKey`'den (FE-ADR-008 §2).
- Bilinmeyen anahtar → `UI-ERROR-GENERIC` + `console.warn` (sessiz yutulmaz).
- Türkçe karakterler `.ts`'te UTF-8 literal (`\uXXXX` yok).
- Varsayılan dil **`en`** (tarayıcı diline bakılmaz); `crm.lang` localStorage.
- Login redirect'ine `?ui_locales=` eklenecek (`I18nService.keycloakUiLocales()`)
  — auth servisi geldiğinde bağlanır.
- **Feature-bazlı organizasyon:** UI anahtarları `UI-{FEATURE}-{ELEMENT}`
  önekli; feature dolunca ilgili anahtarlar `features/customer/<f>/i18n.ts`'e
  taşınabilir (şekil aynı: `key → {en, tr}`, taşıma, yeniden yazım değil).

**Katalog bütünlük testi** (`core/i18n/i18n.spec.ts`): her anahtarın EN+TR
karşılığı dolu mu, ve dokümante her backend `messageKey` katalogda var mı —
eksikse test kırmızı (FE-ADR-008 kararı).

---

## 7. Karar Kaydı (FE-ADR'ler)

Bağlayıcı frontend kararları `docs/frontend/adr/` altındadır. Bunlarla çelişen
her metin geçersizdir.

| ADR | Konu |
|---|---|
| **FE-ADR-001** | Frontend bu repo'da, `frontend/` altında; ayrı repo değil, Maven modülü değil |
| **FE-ADR-002** | Standalone components, TS strict, kesin sürüm pinleme |
| **FE-ADR-003** | Feature-bazlı mimari + `core/` + `shared/`; adres/iletişim alt modül |
| **FE-ADR-004** | Same-origin + göreli path; CORS bilinçli reddedildi |
| **FE-ADR-005** | BFF cookie-session; **Angular'da login formu YAZILMAZ** (yasaklar bölümü) |
| **FE-ADR-006** | Signals + ince servis katmanı; NgRx reddedildi |
| **FE-ADR-007** | Reactive Forms; istemci validasyonu yalnız UX, otorite backend |
| **FE-ADR-008** | `messageKey` kontrat; backend `message` alanı asla basılmaz |
| **FE-ADR-009** | `data-testid` konvansiyonu |
| **FE-ADR-010** | Multi-stage container; compose'a **yalnız ekleme** |
| **FE-ADR-011** | Tailwind + gerçek EDS token'ları; bileşen kütüphanesi yok |
| **FE-ADR-012** | Runtime i18n; iki katalog, tek çatı |
| **FE-ADR-013** | Kapsam yönetimi: mock backend'den geniş. **+ Amendment A (2026-07-24):** FR-ACCT kapsama girdi (account-service geldi); ürün bölümü için "Coming soon" davranış kuralı tanımlandı. **+ Amendment B (2026-07-31):** FR-PROD-01..02 (görüntüleme) kapsama girdi (product-service geldi) — ürün bölümü "Coming soon"dan çıktı; satış akışının üç ekranı order-service'e bağlı olarak birlikte ertelendi |

**Kapsam ve çelişki kaydı:** `docs/frontend/scope-and-conflicts.md` — backend'in
`document-delta.md` disiplininin karşılığı. Her satır bir durum etiketi taşır
(karar bekliyor / analiste soruldu / karara bağlandı).

---

## 8. AI Agent İçin Kurallar

> Kod önermeden **önce** bu bölümü ve §6'yı oku.

### 8.1 Önce oku
1. `FRONTEND_BRAIN.md` (bu dosya)
2. `docs/frontend/mock-ui-analysis.md` — **her tasarım değeri buradan**
3. `docs/frontend/adr/FE-ADR-*.md` — bağlayıcı kararlar
4. `docs/frontend/scope-and-conflicts.md` — neyin kapsam dışı olduğu
5. Backend kontratı: `docs/api/authentication.md`,
   `docs/api/customer-service.md`, `docs/architecture/adr/ADR-005..011`

### 8.2 Mutlak yasaklar
- ❌ **Login formu yazma.** Kullanıcı adı/şifre alanı, login route'u, login
  component'i YOK. Kimlik girişi yalnız Keycloak sayfasında (FE-ADR-005 §P1).
- ❌ **Token saklama/okuma/parse etme.** `localStorage`/`sessionStorage`'a token
  yazma, JWT decode etme, `Authorization: Bearer` header'ı set etme — hepsi
  yasak. Tarayıcıda token zaten yok (FE-ADR-005 §P3).
- ❌ **ROPC / Direct Grant** — testte, script'te, Postman'da bile (FE-ADR-005 §P2).
- ❌ **Keyfi hex renk veya keyfi piksel değeri.** Yalnız tanımlı token'lar.
  `#3B82F6`, `p-[13px]`, `w-[137px]` yasak (FE-ADR-011 §f).
- ❌ **z-index sayısal literal'i.** Yalnız `--eds-z-*` (mock-ui-analysis §2.15).
- ❌ **Şablona gömülü kullanıcı metni.** Her metin çeviri anahtarından
  (FE-ADR-012 §b).
- ❌ **`data-testid`siz etkileşimli eleman** (FE-ADR-009 §1).
- ❌ **Backend'in `message` alanını kullanıcıya gösterme** (FE-ADR-008 §2).
- ❌ **Backend'i olmayan işlev için sahte veri/placeholder** (FE-ADR-013 §a).
- ❌ **`infra/docker-compose.yml`'de mevcut satırları değiştirme.** Yalnız yeni
  servis bloğu eklenir (FE-ADR-010 §4).
- ❌ **`backend/` altında değişiklik.** Frontend görevlerinde backend'e dokunma;
  bulgu varsa `scope-and-conflicts.md` §5'e yaz.
- ❌ **Otomatik commit/push** (CLAUDE.md).

### 8.3 Zorunlular
- ✅ Tasarım değeri gerekince **mock HTML'ini yeniden yorumlama** —
  `mock-ui-analysis.md` tablolarından al.
- ✅ Mock ile backend çakışırsa **backend kazanır**; çakışmayı
  `scope-and-conflicts.md`'ye yaz.
- ✅ Yeni bir karar/çelişki çıkarsa **önce kayıt, sonra kod**.
- ✅ Standalone component yaz; NgModule üretme.
- ✅ Reactive Forms kullan; template-driven üretme.
- ✅ State için signal; `BehaviorSubject` ile state tutma.
- ✅ Göreli path kullan; base URL sabiti oluşturma.
- ✅ Her `shared/ui/` bileşeninde klavye + odak + ARIA (FE-ADR-011 §g).
- ✅ Sürüm eklerken **kesin numara** yaz; `^`/`~`/`latest` kullanma.
- ✅ **Her yeni etkileşimli eleman `data-testid` ile gelir.** Buton, input,
  select, datepicker, link, tablo satırı, form, modal, dil değiştirici —
  hepsi. Yazıldığı anda eklenir, test yazarken sonradan değil: `data-testid`i
  olmayan eleman **eksik elemandır**. İsim `{feature}-{section}-{element}`,
  kebab-case, İngilizce; dinamik satırlar **iş anahtarıyla** (`…-row-1001`),
  asla indeksle. Stil seçicisi olarak kullanılmaz, iş mantığı okumaz.
  (FE-ADR-009; nasıl yazılacağı: `docs/frontend/testing-conventions.md`)
- ✅ **Kullanıcıya görünen hiçbir metin doğrudan yazılmaz.** Her metin katalog
  anahtarından gelir (`{{ 'UI-…' | t }}`). Kapsam yalnız görünen yazı değil;
  `title`, `aria-label`, `placeholder`, `alt` da kullanıcıya/ekran okuyucuya
  ulaşır, onlar da bağlanır. Backend'in `message` alanı ise hiçbir koşulda
  basılmaz — yalnız `messageKey` çözülür. (FE-ADR-012 §b, FE-ADR-008 §2)

> İkisi de `npm run check:conventions` ile mekanik olarak denetlenir
> (`frontend/scripts/check-conventions.mjs`). Kod önermeden önce çalıştır —
> susturma mekanizması **yoktur**, çözüm her zaman eksiği tamamlamaktır.

### 8.4 Emin olmadığında
Bir bilgiye dosyalardan ulaşamıyorsan **uydurma** — *"dosyalarda yok,
netleştirilmeli"* diye işaretle ve `scope-and-conflicts.md`'ye satır ekle.
Bu projede bilinmeyen bir şeyi tahminle doldurmak, boş bırakmaktan daha
maliyetlidir.

---

## 9. Bilinen Teknik Borç / Notlar

- ~~**Hiç kod yok.**~~ ✅ **Güncel (2026-07-24):** iskelet, core (http/auth/i18n),
  routing + guard'lar, uygulama kabuğu, container ve test altyapısı **kuruldu**.
  Henüz **hiçbir iş ekranı** yok — sırada Customer Search (§5.4).
- ~~**3 bloke edici karar**~~ ✅ üçü de çözüldü (Node sürümü, OAuth redirect
  origin'i, frontend portu — §5.1). **Bugünkü bloke ediciler §5.2b'de**:
  `Page` zarfının şekli ve parametreli i18n.
- **`Page` zarfının JSON alan adları doğrulanmadı** — `Page<CustomerDetailResponse>`
  dönüyor ama serileştirme modu ayarlanmamış; çalışan instance'ta teyit gerekiyor.
- **Logout SSO'yu sonlandırmıyor** — uygulama oturumu kapanıyor (`JSESSIONID`
  siliniyor) ama Keycloak SSO oturumu yaşıyor; "Giriş yap" parola sormadan
  giriyor. Sebep ve çözüm backend'de (`scope-and-conflicts §5.7`).
- **Erişilebilirlik bizim yükümüz** — bileşen kütüphanesi kullanmama kararının
  gerçek bedeli bu (FE-ADR-011 §g). Her bileşende gözetilecek.
- **Validasyon kuralları bilinçli olarak çift yazılıyor** (istemci + sunucu).
  Backend kataloğu değişirse frontend'in takip etmesi gerekir.
- **Backend'e bildirilmiş 5 bulgu** var (`scope-and-conflicts.md` §5) — biri
  gerçek bir arama hatası (`İ` ile başlayan isimler bulunamıyor).
- **Angular 6 ayda bir major çıkarıyor** — sürüm yükseltme planlı bir iş olarak
  ele alınmalı, pasif sürüklenme olarak değil (FE-ADR-002).

---

## 10. Analistler İçin — Uygulamayı Ayağa Kaldırma

Bu bölüm **teknik olmayan okuyucu** içindir: projeyi çekip çalıştırmak,
şu ana kadar nelerin bittiğini görmek ve neyin **henüz olmadığını** bilmek.

### 10.1 Şu an ne var, ne yok

| ✅ Çalışıyor | ❌ Henüz yok |
|---|---|
| Keycloak üzerinden **giriş** (CRM Lite temalı login sayfası) | Ürün **pasifleştirme** ("Deactivate product" — arka uçta yazma işlemi yok, buton kapalı) |
| **Müşteri Arama** — giriş sonrası ana ekran: tüm müşteriler listelenir, filtrelenir, sayfalanır | Arama'daki "Account number"/"Order number" filtreleri (arka uç entegrasyonu bekliyor) |
| **Müşteri Oluşturma** — "Create new customer" ile açılan 3 adımlı sihirbaz: kimlik bilgileri, adres(ler), iletişim; kayıt tek işlemde oluşur, kimlik doğrulaması (MERNİS) otomatik yapılır | |
| **Müşteri Bilgisi** — satırdaki müşteri numarasına tıklayınca açılır: bilgileri görüntüleme/düzenleme, adres ekleme/düzenleme/silme/birincil yapma, iletişim güncelleme, müşteri silme (onaylı) | |
| **Fatura hesapları** — Müşteri Bilgisi'nin "Customer account" sekmesi: listeleme (silinen hesaplar "Pasif" olarak listede kalır), hesap oluşturma, ad/adres güncelleme, silme (onaylı; hesap numarası hiçbir zaman düzenlenemez) | Satış ekranları (Teklif seçimi / Ürün konfigürasyonu / Sipariş gönderimi) — üçü tek bir akış, sipariş servisi geldiğinde birlikte yapılacak |
| **Ürünler** — hesap satırındaki oku tıklayınca o hesabın ürünleri açılır (pasif ürünler de listede kalır); göz ikonuyla ürün detayı (teklif adı/ID, spesifikasyon ID, kampanya, servis adresi) görüntülenir | |
| Uygulama **çerçevesi**: üst bar, sol menü, kullanıcı adı, çıkış | |
| **TR/EN dil değiştirici** — anında, tercih hatırlanıyor | |
| **Yetki reddi** (403) ve **oturum kapatıldı** sayfaları | |
| Backend'e güvenli erişim (oturum + CSRF + hata çevirisi) | |

> "Account number" ve "Order number" arama filtreleri hesap/sipariş arama
> entegrasyonu gelene kadar kapalı. Ürün tablosundaki **"Deactivate product"**
> düğmesi kapalıdır: ürün servisi bu aşamada **yalnız okuma** yapıyor, hiçbir
> değiştirme işlemi tanımlı değil. Bunların dışında **kapsam içi tüm ekranlar
> canlıdır.**

### 10.2 Gereksinimler (tek seferlik)

- **Podman** veya **Docker Desktop**
- Kaynak kodu çekmek için **Git**

Angular/Node kurmanıza **gerek yok** — her şey container içinde derleniyor.

### 10.3 Ayağa kaldırma — tek komut

```bash
git clone <repo>
cd crm-lite-project/infra
podman compose -p crm-lite up -d --build
```

> `-p crm-lite` **önemli**: proje adını sabitler, aynı makinedeki başka
> container'larla karışmayı önler. Docker kullanıyorsanız `podman` yerine
> `docker` yazın.

İlk çalıştırma **10–20 dakika** sürebilir (imajlar derleniyor). Sonraki
açılışlar 1–2 dakika.

Her şeyin hazır olduğunu şununla görürsünüz — **9 servisin tamamı `healthy`**
olmalı:

```bash
podman ps --format "{{.Names}}\t{{.Status}}"
```

### 10.4 Portlar — hangisini ne zaman kullanacaksınız

| Port | Ne | Siz açıyor musunuz? |
|---|---|---|
| **4200** | **Uygulama** | ✅ **Tarayıcıda yalnız bunu açın** |
| 8180 | Keycloak (giriş sayfası) | ⚠️ Elle açmayın — giriş sırasında **kendiliğinden** gelir |
| 8080 | api-gateway (arka uç kapısı) | ❌ Elle açmayın |
| 8888 / 8761 / 5432 | Yapılandırma / servis kayıt / veritabanı | ❌ Dokunmayın |

> 🔴 **En sık yapılan hata:** tarayıcıya `localhost:8080` yazmak. Orası
> uygulamanın arka kapısıdır; ham JSON görürsünüz ve giriş akışı bozulur.
> **Daima `localhost:4200`.**

### 10.5 Kullanım sırası

1. Tarayıcıda **`http://localhost:4200`** açın.
2. Otomatik olarak **Keycloak giriş sayfasına** yönlendirilirsiniz (adres
   çubuğunda `localhost:8180` görünür — **normaldir**).
3. Giriş yapın:

   | Kullanıcı | Şifre | Sonuç |
   |---|---|---|
   | `ayilmaz` | `crm-dev` | ✅ Girer |
   | `edemir` | `crm-dev` | ✅ Girer |
   | `mkaya` | `crm-dev` | ❌ **Hesap kapalı** — giriş sayfasında hata verir (bilinçli test kullanıcısı) |

4. `localhost:4200` adresine dönersiniz; üst barda kullanıcı adınız görünür.
5. Üst bardaki **EN / TR** ile dili değiştirin — sayfa yenilenmeden değişir.
6. Sol menünün altındaki **çıkış** ikonuyla oturumu kapatın.

> ⚠️ **Çıkışta bilinen davranış:** "Oturumunuz kapatıldı" sayfasına inersiniz,
> ama **"Giriş yap"a basınca şifre sorulmadan** tekrar girersiniz. Uygulama
> oturumu gerçekten kapanıyor; kapanmayan şey Keycloak'ın kendi oturumu. Bu
> **bilinen ve kayıtlı** bir eksik, arka uçta giderilecek
> (`scope-and-conflicts §5.7`). Tam çıkışı denemek için **gizli pencere**
> kullanın.

### 10.6 Kapatma

```bash
podman compose -p crm-lite down          # durdurur, veriyi korur
podman compose -p crm-lite down -v       # veritabanını da siler (sıfırdan başlar)
```

### 10.7 Bizden ne bekliyoruz

Cevap bekleyen sorular `docs/frontend/scope-and-conflicts.md` içinde
**🟡 analiste soruldu** etiketiyle duruyor. Öncelikli olanlar:

- **§2A.1** — "Second name" ile arama ayrı bir alan olarak isteniyor mu?
- **§2A.2** — "Role" ile **filtreleme** bir gereksinim mi? (Evetse arka uçta yeni
  parametre gerekir.)
- **§2A.3** — Ekran ilk açıldığında hiç müşteri yoksa gösterilecek metin.
- **§2.19 / §2.24** — Boş sonuç metni ve pasif alanların ipucu metni.
- **§3.5** — Katalogda karşılığı olmayan 10 mesajın metnini siz mi vereceksiniz?

Her satır **kimin kararı beklediğini** ve **neyi bloke ettiğini** yazar.
