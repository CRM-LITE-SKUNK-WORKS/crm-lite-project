# PROJECTBRAIN — CRM Lite

> **Amaç:** Bu dosya projenin **güncel durumunun tek doğ­ru kaynağıdır** (single source of truth).
> Hem projeye sonradan dönen geliştirici, hem de sıfırdan bağlam kuran bir AI agent bu dosyayı
> okuyarak "nerede kaldık, neden böyle yapıldı, sırada ne var" sorularını cevaplayabilmelidir.
>
> **Son güncelleme:** 2026-08-13 (**ASENKRON SALE'İN İKİ BAĞIMSIZ TIKANMA NOKTASI
> GİDERİLDİ — saga artık gerçekten `COMPLETED`'e ulaşıyor:**
> **ÖNCE ŞUNU OKU: `async-sale` profili artık gerçekten çalışıyor.** 10.08.2026'daki
> ADR-018 kesimi doğruydu ama iki ayrı, birbirinden bağımsız altyapı hatası yüzünden
> saga hiçbir zaman `COMPLETED`'e ulaşamıyordu — canlı ortamda ilk denemede ikisi de
> ortaya çıktı, ikisi de düzeltildi.
> **(1) Kök neden #1 — hiçbir saga consumer'ı hiç bağlanmıyordu.** Üç
> `*-async-sale.yml` dosyasında (`account`, `product`, `order`-service) `function.definition`
> `spring.cloud.stream.function` altına yazılmıştı — bu property GERÇEKTE
> `spring.cloud.function.definition`; `stream:` altındaki hali sessizce YOK SAYILIYOR.
> Sonuç: Kafka'da hiçbir consumer group hiç oluşmuyordu (doğrulandı —
> `kafka-consumer-groups.sh --list` boştu), komutlar üretiliyor ama hiç tüketilmiyordu.
> Submit ya doğrudan 503 dönüyordu (outbox kapalıyken) ya da saga
> `AWAITING_ACCOUNT_CHECK`'te sonsuza kadar kalıp `SaleSagaRecoveryJob`'ın retry
> bütçesini tüketip `MANUAL_INTERVENTION`'a yükseliyordu — daha önceki "hep işleniyor
> kalıyor" gözlemi buydu. Fix: üç dosyada da `function:` bloğu `cloud:` altına,
> `stream:` ile KARDEŞ seviyeye taşındı.
> **(2) Kök neden #2 — saga'nın dış çağrıları 401 alıyordu.** #1 düzelince saga bir
> sonraki adımda (`prepare`) tıkandı: product-service'in `doCreate()`'i (senkron
> yoldakiyle AYNI metot) customer-service/lookup-service'e gidiyor, ama saga bir Kafka
> listener thread'inde çalıştığı için `SecurityContextHolder`'da taşınacak bir kullanıcı
> token'ı yok — `BearerTokenPropagationInterceptor` boş `Authorization` gönderiyor,
> resource server 401 dönüyor. **ADR-010'un lookup-service maddesi bu senaryoyu zaten
> "future work" olarak adlandırmıştı** ("genuine background/batch job... gets a
> client-credentials service account"). Fix: yeni Keycloak client'ı `crm-saga-worker`
> (client-credentials, `crm-user` rolü + `crm-api` audience — kullanıcı token'ıyla AYNI
> zero-trust kontrolünden geçiyor), `ServiceAccountTokenProvider` (crm-security-starter,
> önbellekli) ve `BearerTokenPropagationInterceptor`'a fallback: SecurityContext'te
> kullanıcı token'ı YOKSA buna düşer. Yalnız `crm.security.service-account.client-id`
> set'liyken aktif — bugün yalnız account/product-service'in `async-sale` profili;
> request-bound hiçbir çağrı etkilenmedi (ADR-010'a addendum, testli).
> **(3) Kolaylık — `docker-compose.async-sale.yml` (yeni).** `SPRING_PROFILES_ACTIVE=async-sale`'i
> her seferinde elle yazmamak için override dosyası:
> `docker compose -f docker-compose.yml -f docker-compose.async-sale.yml --profile eventing up -d`.
> Düz `docker compose up` (CI'nin çalıştırdığı) DEĞİŞMEDİ.
> **(4) Canlı doğrulandı, bir yan bulgu dahil.** config-server/account-service/product-service
> imajları yeniden build edildi; `keycloak_db` sıfırlanıp realm yeniden import edildi
> (yol boyunca bir hata daha bulundu: Keycloak'ın `CLIENT.DESCRIPTION` kolonu
> `VARCHAR(255)` — uzun bir description import'u tamamen çökertiyor, kısaltıldı).
> Art arda üç taze satış (`sale_saga`: 1261000150, 1261000168, 1261000176) tek
> denemede, `retry_count=0` ile `COMPLETED`'e ulaştı. Fix'ten ÖNCEKİ 4 saga kalıcı
> olarak `MANUAL_INTERVENTION` kaldı (terminal saga elle düzeltilmez — runbook kuralı,
> bilerek dokunulmadı).
> **(5) Bilinen, dokunulmamış bir yan bulgu:** `order-outbox-connector` (Debezium)
> `FAILED` durumda — `occurred_at` alanı SMT'nin beklediği `INT64` tipinde değil. Şu an
> ENGELLEYİCİ DEĞİL çünkü order-service'in in-process relay'i aynı satırları zaten
> yayınlıyor, ama account/product-service'in Debezium connector'ları RUNNING iken
> onların in-process relay'i de aktif — runbook'un açıkça uyardığı "aynı anda ikisini
> de çalıştırma" durumu (çift yayın; şu an inbox dedup ile zararsız). §9.7'nin
> "Debezium connector'ları canlı doğrulama" maddesine not düşüldü.
> Test kanıtı: `crm-security-starter` **18/18** (2 yeni test: servis-hesabı fallback'i).
> Detay: **ADR-010** (13.08.2026 eki), `docs/runbooks/eventing.md`,
> `infra/docker-compose.async-sale.yml`, `infra/keycloak/realm/crm-lite-realm.json`.)
> Önceki durum: 2026-08-10 (**ASENKRON SALE KESİMİ YAPILDI — taslak sipariş +
> kalıcı saga orkestrasyonu CANLI (ADR-018, Accepted):**
> **ÖNCE ŞUNU OKU: canlı satış yolu artık asenkron.** `POST /api/orders` **deprecated**
> — build'de duruyor çünkü (a) mevcut merge edilmiş frontend hâlâ onu çağırıyor ve
> (b) geri dönüş yolu o. Yeni akış: `POST /api/orders/drafts` → `POST /api/orders/{n}/submit`
> (**202**) → `GET /api/orders/{n}/status`. **Bir satış ASLA iki yoldan birden geçemez** —
> convention değil, yapı gereği: legacy uç her zaman YENİ bir sipariş yaratır ve o zaten
> MIDLWARE'dir, submit ise yalnız WAIT taslağı kabul eder. Testle sabitlendi
> (`legacyAndAsyncCannotBothRunOneSale`).
> **(1) ANALİST KARARLARI (10.08.2026) — üç açık soru KAPANDI:**
> **(A1)** *"Kullanıcı işleme başladığında ilgili tablolarda kayıtlar zaten
> oluşturulmalı. Kayıtlar var, ama sipariş henüz tamamlanmadığı/onaylanmadığı için
> statüleri MIDLWARE değil. Sipariş onayıyla statü MIDLWARE'e geçer. Dolayısıyla Order ID
> bu noktada gösterilebilir."* → Sipariş **Submit'ten ÖNCE** var; statüsü workbook'ta
> ZATEN BULUNAN `GNL_ST WAIT`(3). **Yeni bir DRAFT satırı UYDURULMADI** — analistin tarif
> ettiği statüye katalog zaten sahipti. **ADR-016 §8.3 ("numara yalnız submit'ten sonra")
> ve §6 ("WAIT hiç yazılmaz") SUPERSEDE edildi.**
> **(A2)** Order ID **benzersiz** olmalı; artan basit bir sequence yeterli, kuralı teknik
> ekip belirleyebilir → **KR-12 üreteci DEĞİŞTİRİLMEDİ**; statüsü düzeltildi: artık
> "analist onayı bekleyen proje-önerisi" değil, **analist gereksinimini karşılayan proje
> teknik tercihi**. Çalışan, testli bir şemayı zaten cevapladığı bir gereksinim için
> yeniden tasarlamak değişiklik için değişiklik olurdu.
> **(A3)** Teklif fiyatları **demo verisi, blocker değil** → seed değerleri aynen kaldı,
> artık **non-normatif demo fixture** olarak kayıtlı; document-delta'daki #10 / P1 / P5 /
> P10 "analist onayı bekliyor" işaretleri KAPATILDI. Gerçek bir fiyat listesi uydurulmadı.
> **(A4)** Genel hata mesajı onaylandı → spesifik anahtarlar **KORUNDU** (product-service'in
> `MSG-SALE-*`/`MSG-VAL-CHAR-*`'ları aynen aktarılıyor, gerçek erişilemezlik hâlâ
> `MSG-SERVICE-UNAVAILABLE`); `MSG-SALE-FAILED` YALNIZCA hiçbir mevcut anahtar doğruyu
> söylemediğinde kullanılan jenerik son çare.
> **(A5)** İşlenmekte olan siparişin ürünleri için **PNDG kullanılabilir** → ADR-015
> §5.5'in "uçuştaki satış için hazırlanmış, henüz commit edilmemiş" anlamı artık proje
> yorumu değil, **analist destekli**. Saga'nın ürünleri involvement'a kadar PNDG tutmasını
> mümkün kılan şey bu.
> **(A6)** Transfer / Servis Adresi Değişikliği **KAPSAM DIŞI** → yapılmadı; `GNL_TP`
> TRANSFER(8)/CANCEL(9) satırları yazılmamış halde kalıyor ve yeni işlevsellik için
> gerekçe olarak KULLANILMAZ.
> **(2) Taslak (draft) yaşam döngüsü:** `POST /api/orders/drafts` → `bsn_inter` + `cust_ord`
> **WAIT** + KR-12 numarası (Submit ekranı numarayı buradan gösterir). **Ürün yok, saga yok,
> mesaj yok.** `customerNumber` **hesaptan** çözülür, tarayıcıdan ALINMAZ.
> **AC-SALE-01-16 hâlâ yapı gereği doğru** — ve eskisinden daha güçlü bir nedenle: taslak
> SEPETİ değil SİPARİŞİ yaratıyor; WAIT siparişinin kalemi yok, ürünü yok, saga'sı yok ve
> onu adlandıran hiçbir komut yok, yani tarayıcı abandon ucunu çağırsa da çağırmasa da
> işlenemez. `DELETE /api/orders/{n}/draft` idempotent ve **yalnız WAIT** (MIDLWARE → 409
> `MSG-ORDER-NOT-DRAFT`) — bu guard, ucun KR-7'nin kapsam dışı bıraktığı submit-sonrası
> sipariş iptaline dönüşmesini engelliyor. Süresi geçen taslakları temizleyen job
> **YALNIZCA iptal eder**; hiçbir kod yolu bir taslağı submit/fulfil edemez.
> **(3) Submit = TEK yerel transaction:** kalemler + `WAIT → MIDLWARE` + `sale_saga` satırı
> + saga'nın İLK Outbox komutu **birlikte commit** olur; 202 o commit'ten SONRA döner ve
> product-service/account-service'i **beklemez**. 201 değil 202, çünkü 201 gövdesinin
> anlattığı durumda var olan bir kaynak vaat eder — Submit'ten sonra ürünler henüz yok.
> **(4) `sagaId` = `orderNumber` — ADR-017 §5.1 SUPERSEDE edildi.** Eskiden sagaId
> istemcinin `Idempotency-Key`'iydi, çünkü satışın Submit'e kadar kendi kimliği YOKTU.
> A1 o kısıtı kaldırdı. Artık: **`Idempotency-Key` = BİR HTTP komutu**, `sagaId` = satış.
> Taslak ve submit **AYRI** anahtar alır; iki yeni uç gövdeyle birlikte **path**'i de
> hash'liyor, böylece bir anahtarı iki uçta kullanmak yanlış komutun cevabının replay'i
> değil `409 MSG-IDEMPOTENCY-KEY-CONFLICT` oluyor. (Legacy uç eski body-only hash'ini
> KORUYOR: değiştirmek canlı tablodaki mevcut anahtarları deploy sonrası uyumsuz kılar ve
> uçuştaki bir retry'ı sahte 409'a çevirirdi.)
> **(5) Saga = order-service'in TEK sahibi olduğu kalıcı process manager** (`sale_saga`,
> Flyway **V5**, PK `saga_id` = order numarası, `CHECK (saga_id = order_number)`).
> Optimistic lock (`@Version`), retry bütçesi/vadesi, operatör-yüzlü `failure_code` +
> **istemci-güvenli** `failure_message_key`, sebep mesaj id'si, ve iki JSON kolonu:
> gönderilen sepet snapshot'ı (karakteristik DEĞERLERİ `order_db`'de başka hiçbir yerde
> yok — restart sonrası komutu yeniden üretmek için şart) ve ürün id'leri. **Servisler
> arası ortak saga veritabanı YOK, cross-database FK YOK.**
> Akış: hesap kontrolü → ürünler **PNDG** → hesaba **link** → **ACTV**'ye yükselt →
> `crm.sale.sale-completed` (yalnız koreografi kancası, çekirdek tutarlılıktan SONRA).
> **(6) Sıralama ADR-016 §5'in TERSİ, bilerek:** senkron akış önce ACTV yapıp sonra
> involvement yazıyordu; saga önce **link** edip sonra **activate** ediyor — bir ürün,
> hiçbir hesap onu talep etmiyorken commit edilmemeli. Bedeli: aktivasyon hatasının geri
> alacak bir involvement'ı olması. Bu yüzden account-service'e **saga-kapsamlı, İÇSEL**
> bir involvement telafisi eklendi: `cust_acct_prod_invl`'e nullable `sale_operation_id`
> (Flyway **V6**); mevcut satırlar NULL kalır ve bu yüzden **hiçbir saga tarafından
> telafi EDİLEMEZ** (güvenli varsayılan — backfill bir sahip UYDURMAK zorunda kalırdı).
> **Kullanıcıya açık unlink ucu HÂLÂ YOK** (ADR-013 §8.6 geçerli).
> **(7) Telafi başarısız olursa `MANUAL_INTERVENTION`** — ve sipariş **CANCELLED
> YAPILMAZ**: CANCELLED gerçekleşmemiş bir geri-alımı iddia ederdi. Operatörün bulması
> gereken tek durum, temiz bir rollback'ten ayırt edilebilir kalıyor (durum + gauge +
> ERROR log). Public `processingStatus` yine de `FAILED`.
> **(8) İki statü ekseni, gereksiz tekrar DEĞİL:** `orderStatus` analistin GNL_ST'si
> (WAIT/MIDLWARE/CANCELLED); `processingStatus` bu servisin KENDİ sözleşmesi
> (DRAFT/PROCESSING/COMPLETED/FAILED) ve **GNL_ST'ye EKLENMEDİ**. Tamamlanmış satış =
> **MIDLWARE + COMPLETED**, çünkü MIDLWARE analistin onay anında tanımladığı statü ve
> kabul edilmiş hiçbir gereksinim başka bir terminal ORDER statüsü tanımlamıyor —
> ikisini tek alana indirmek, tam da A1'in kaçındığı uydurmayı zorunlu kılardı.
> **(9) At-least-once altında doğruluk:** birebir kopya Inbox'ın
> `(message_id, consumer_group)` kısıtıyla yutuluyor; duruma UYMAYAN cevap sayılıyor
> (`crm_saga_unexpected_messages_total`) ve saga'yı İLERLETMİYOR — sessizce atılmıyor,
> çünkü kalıcı sıfır-olmayan oran gerçek bir sözleşme kusurunun imzasıdır; aynı saga'ya
> yarışan iki teslimatı `@Version` çözüyor (kaybeden claim + saga + giden komutla
> BİRLİKTE rollback olup yeniden teslim ediliyor).
> **(10) Atomiklik — nerede gevşetildiği DAHİL:** order-service'te saga geçişi + giden
> komut HER ZAMAN tek commit. product/account-service'te Inbox claim + giden cevap her
> zaman tek commit, ama **iş mutasyonu `REQUIRES_NEW`**: bir saga adımı İŞ hatası olarak
> başarısız olabilmeli, oysa `REQUIRED` bir metot patlayınca çağıranın transaction'ı
> rollback-only işaretlenir ve hata cevabı HİÇ yazılamazdı (reddedilen sepet DLQ'ya kadar
> sonsuz yeniden teslim). Hata yolunda yazılan bir şey yok → atomiklik zaten sağlam;
> başarı yolunda mutasyon bir an önce commit oluyor ve her komut alıcısında idempotent
> olduğu için yeniden teslim aynı cevaba yakınsıyor, ikinci bir satışa değil.
> **(11) Kurtarma broker-bağımsız:** transport'un GÖREMEDİĞİ sorun, başarıyla yayınlanmış
> ama hiç cevaplanmamış komuttur. `SaleSagaRecoveryJob` `reply-timeout` sonrası bekleyen
> komutu üstel backoff ile **idempotent** yeniden yayınlıyor, bütçe bitince
> `MANUAL_INTERVENTION`'a yükseltiyor (altıncı kopya beşincinin alamadığı cevabı almaz —
> o yük, kurtarma değil). Her saga KENDİ transaction'ında; bir optimistic-lock kaybı
> (gerçek cevap tam o an geldiğinde beklenen sonuç) yalnız o saga'ya mal oluyor.
> Terminal saga ASLA yeniden yayınlanmaz → aynı sipariş iki kez fulfil edilemez.
> **(12) Metrikler** (mevcut Micrometer/Prometheus hattı): `crm_saga_state{state}` (durum
> başına AYRI seri — kimsenin bulunmadığı durum da 0 raporlamalı), `crm_saga_stuck`,
> **`crm_saga_compensation_failures_total` (insan gerektiren TEK metrik)**,
> `crm_saga_unexpected_messages_total`, `crm_saga_command_reissues_total`,
> completed/failed sayaçları, `crm_sale_async_duration_seconds`. Kopya ve dead-letter
> BURADA TEKRAR SAYILMIYOR — onlar Inbox özelliği ve zaten `InboxMetrics`'te var; tek olay
> için iki sayı ilk dead-letter'da birbirini tutmazdı.
> **(13) Açma/kapama TEK profil:** `SPRING_PROFILES_ACTIVE=async-sale`. Profilsiz
> çalıştırmada üç anahtar da `false`, binder oluşturulmuyor, yığın **Kafka'sız** bugünkü
> gibi kalkıyor — Kafka'yı her yerel çalıştırmanın zorunlu bağımlılığı yapmak, satışla
> ilgilenmeyen herkesi bozardı. Profil kapalıyken submit **503 `MSG-SERVICE-UNAVAILABLE`**
> ile **fail-closed**: aksi halde recorder sessizce hiçbir şey yazmaz, transaction commit
> olur ve istemci hiç gönderilmemiş bir komutun cevabını sonsuza kadar bekleyen MIDLWARE
> bir siparişi poll ederdi.
> **(14) AÇIK BIRAKILAN, bilerek:** AC-SALE-01-15'in **işlem ekranı UX yorumu** —
> polled bir status kaynağının frontend'de nasıl gösterileceği (ayrı ekran mı, satır içi
> durum mu) — **PROJE YORUMU, analistin nihai UX açıklamasını BEKLİYOR**; analist onaylı
> gereksinim olarak KAYDEDİLMEDİ. Backend sözleşmesi bilerek UX-nötr.
> Test kanıtı: order-service **66/66** (22 yeni `AsyncSaleFlowIntegrationTest`),
> product-service **54/54** (7 yeni `SaleCommandHandlerTest`), account-service **61/61**
> (8 yeni `SaleCommandHandlerTest`), tüm backend reactor **BUILD SUCCESS**.
> Detay: **ADR-018**, `docs/api/order-service.md` §Asynchronous SALE,
> `docs/runbooks/eventing.md` §5b, `docs/contracts/events/registry.md`.)
> Önceki durum: 2026-08-07 (**Eventing TEMELİ UYGULANDI — güvenilir mesajlaşma
> altyapısı, SALE akışı HENÜZ KESİLMEDİ (ADR-017, Accepted):**
> **ÖNCE ŞUNU OKU: `POST /api/orders` DEĞİŞMEDİ.** ADR-016 §5'in senkron
> orkestrasyonu hâlâ TEK canlı SALE yolu; üç anahtarın ÜÇÜ DE `false` geliyor
> (`crm.messaging.outbox.enabled`, `.relay.enabled`, `crm.messaging.broker.enabled`)
> ve Kafka/Connect/Debezium opt-in bir Compose profilinde. **Uçtan uca asenkron bir
> satış ÇALIŞTIRILMADI ve iddia EDİLMİYOR.**
> **(1) Yeni paylaşılan modül `backend/crm-messaging-starter`** (crm-security-starter
> ve crm-observability-starter'a PARALEL, ayrı): versiyonlu `EventEnvelope`,
> `Destinations` isimlendirme kuralları, `MessageTypes` katalogu, transactional
> Outbox/Inbox mekaniği, relay, retention ve metrikler. **İçinde HİÇBİR servisin iş
> payload tipi YOK** — yani ona bağımlı olmak başka bir servisin modeline bağımlı olmak
> değil (ADR-017 §6). Sözleşmelerin otoritesi `docs/contracts/events/` altındaki JSON
> Schema'lar; **build bağımlılığı değiller**, bilerek.
> **(2) Broker sınırı = paket sınırı, ve TESTLE zorlanıyor:** domain/application kodu
> `org.apache.kafka.*` import EDEMEZ; `KafkaTemplate`/`@KafkaListener`/`KStream`/
> `KTable`/Kafka Streams topolojisi/Streams Binder HİÇBİR YERDE kullanılmıyor; Spring
> Cloud Stream **fonksiyonel** bağlamaları yalnız `*.messaging.adapter` paketlerinde.
> `NoBrokerTypesInDomainOrApplicationTest` (3 servis) **derlenmiş .class dosyalarını**
> tarıyor — import grep'i satır içi tam-nitelikli referansı ve generic imzadan gelen tipi
> kaçırır, ikisi de sorunsuz derlenir ve ikisi de broker classpath'te yokken sınıf
> yüklemesini patlatır. product-service'teki sürüm ayrıca **muafiyetin gerçekten
> taşıyıcı olduğunu** doğruluyor (adapter'ı silmek testi boşa geçirtemesin diye).
> **(3) Servis-YEREL Outbox/Inbox** (yeni Flyway: order V4, product V6, account V5).
> `OutboxRecorder` `Propagation.MANDATORY` — transaction dışında çağırmak bir
> code-review bulgusu değil, `IllegalTransactionStateException`. Böylece "state yazıldı
> ama mesaj kayboldu" ve "mesaj yayınlandı ama state geri alındı" **imkânsız**, dağıtık
> transaction olmadan ve broker kapalıyken. Inbox tarafında claim + iş değişikliği TEK
> transaction; nihai koruma `UNIQUE (message_id, consumer_group)` — `exists` ön-kontrolü
> yalnızca optimizasyon (iki eşzamanlı teslimat ikisi de geçebilir). Handler patlarsa
> claim ONUNLA BİRLİKTE geri alınır, yani sonraki teslimat gerçek bir ilk teslimattır.
> **(4) Relay ÇİFT, ama aynı anda TEK:** birincil Debezium Outbox Event Router
> (`infra/eventing/connectors/`, servis başına bir connector — WAL okur, uygulama thread'i
> yok); alternatif in-process `OutboxRelay` (Connect'siz ortamlar ve **test edilebilirlik**
> için — Connect gerektiren bir relay "broker 30 saniye kapandı ve geri geldi"yi
> kanıtlayamaz). **İkisi birden çalışırsa her mesaj İKİ KEZ yayınlanır** (ADR-017 §9.3).
> At-least-once bilerek: satır publisher döndükten SONRA published işaretleniyor, çünkü
> sessizce düşen mesaj geri alınamaz, kopya ise Inbox sayesinde bedava.
> **(5) Retention** yalnız `published_at IS NOT NULL` satırları siler — yayınlanmamış satır
> HİÇBİR YAŞTA silinmez; "eski olmak" zaten hâlâ gitmesi gereken mesajın SEMPTOMU, salt
> zamana bakan bir temizlik tam da alarm vermesi gereken backlog'u silerdi.
> **(6) Metrikler** mevcut Prometheus hattında: `crm_outbox_backlog_messages`,
> **`crm_outbox_oldest_unpublished_age_seconds` (alarm BUNA kurulur** — tek zehirli mesaja
> takılmış relay'de backlog küçük ve düz kalır, yaş sınırsız tırmanır), publish/failure,
> retention, `crm_inbox_duplicates_total` (**sıfırdan büyük olması NORMAL**),
> consumer failures, `crm_inbox_dead_letter_total`. `InboxGuard` handler süresince MDC'ye
> `sagaId`/`eventId` yazıyor — observability ekinde REZERVE edilen iki alan artık dolu.
> **(7) `sagaId` = istemcinin `Idempotency-Key`'i** (ADR-016 §10): satışın zaten
> istemcinin seçtiği bir kimliği var, ikinci bir id üretmek aynı satışa kimsenin
> ilişkilendiremeyeceği iki kimlik vermek olurdu. Partition key = `sagaId`, yoksa
> `aggregateId` (= order numarası) — sıra garantisi yalnız SATIŞ İÇİNDE gerekiyor.
> **[10.08.2026 itibarıyla SUPERSEDE edildi — ADR-018 §3: `sagaId` = `orderNumber`.**
> Gerekçesi o gün için doğruydu: sipariş Submit'ten önce var olmadığı için satışın kendi
> kimliği yoktu. A1 kararı bunu değiştirdi; `Idempotency-Key` artık bir HTTP komutunu
> tanımlıyor, satışı değil. Partition key kuralı aynen geçerli — iki değer artık aynı.]
> **(8) Compose'a opt-in `eventing` profile:** Kafka (KRaft, tek broker) + Kafka Connect
> (Debezium) + one-shot `kafka-connect-init`. `postgres` artık `wal_level=logical` ile
> koşuyor — çalışırken değiştirilemeyen bir ayar, ve bir geliştiricinin profili denemek
> için volume'unu silmesi gerekmemeli; Debezium koşmazken maliyeti biraz daha büyük WAL.
> Test kanıtı: crm-messaging-starter 20/20, order-service 43/43 (12 yeni: atomiklik,
> broker kesintisi, çift yayın yok, backlog/yaş gauge'ları, retention, mimari guard),
> product-service 47/47 (10 yeni: kopya, consumer restart, handler hatası, desteklenmeyen
> şema versiyonu, çözülemeyen bayt, grup bağımsızlığı, mimari guard), account-service
> 53/53 (2 yeni). `docker compose config` üç profilde de geçerli.
> Detay: `docs/runbooks/eventing.md`, `docs/contracts/events/`, ADR-017.)
> Önceki durum: 2026-08-06 (**Observability + Resilience eki UYGULANDI —
> üretim odaklı gözlemlenebilirlik ve senkron sınırların korunması:**
> **(1) Actuator + Micrometer Prometheus** her 9 deployable'a eklendi
> (`GET /actuator/prometheus`), **internal-only** — 8 servis zaten host portu
> yayınlamıyor (ADR-009) o yüzden yeni bir erişim-kontrol katmanı gerekmedi,
> `crm-security-starter` kullanan 5 servi'de `/actuator/health` ile AYNI şekilde
> `permit-paths`'e eklendi (JWT gerektirmiyor — bir Prometheus scraper Keycloak
> oturumu tutamaz). **api-gateway TEK istisna** (host portu `:8080` yayınlıyor):
> `/actuator/prometheus` orada BİLEREK mevcut `crm-user` JWT kuralının ARKASINDA
> bırakıldı (permitAll'a EKLENMEDİ) — Compose Prometheus'u bu yüzden
> api-gateway'i scrape ETMİYOR (dedicated internal management port ayrı takip).
> **(2) Yapılandırılmış JSON log'lama:** yeni paylaşılan modül
> `backend/crm-observability-starter` (crm-security-starter'a PARALEL, ayrı —
> hiç security starter'ı olmayan servisler de (mernis-stub, discovery-server,
> config-server) buna bağımlı). `logback-json-base.xml` fragment'i her servisin
> kendi `logback-spring.xml`'inden `<include>` edilir; `net.logstash.logback:
> logstash-logback-encoder` kullanılıyor — **Log4j2 YOK**, mevcut SLF4J+Logback
> yığını korunuyor. MDC alanları (`com.crm.observability.starter.MdcKeys`):
> `correlationId` (yeni `CorrelationIdFilter`, `X-Correlation-Id` header'ı okur/
> üretir, Spring Security zincirinden ÖNCE — `Ordered.HIGHEST_PRECEDENCE` —
> çalışır ki 401/403'ler de taşısın; yeni `CorrelationIdPropagationInterceptor`
> ile outbound RestClient çağrılarına da taşınır, `BearerTokenPropagationInterceptor`
> ile aynı opt-in desende), `traceId`/`spanId` (yeni `micrometer-tracing-bridge-
> brave` bağımlılığı — collector YOK, sadece MDC yazımı), `orderNumber`
> (order-service, KR-12 numarası atanır atanmaz `OrderPersistence`'ta set edilir,
> `OrderController` finally'de temizler), `exceptionType` (4 servisin
> `GlobalExceptionHandler#handleUnexpected`'inde log satırının etrafında set/
> clear), **`sagaId`/`eventId` REZERVE — hiçbir kod bunları doldurmuyor** (gelecekteki
> messaging tabanlı SALE akışı için, bkz. ADR-016 §5.4'ün "bu bir saga framework'ü
> DEĞİL" notu — bu eklenti tam olarak o geçişe hazırlanıyor). **Hassas veri
> maskeleme:** `SensitiveDataMaskingDecorator` (bir `JsonGeneratorDecorator`) JSON'a
> yazılan HER string değeri regex'ten geçiriyor (JWT, `Bearer ...`, `Cookie`/
> `Set-Cookie` header satırları — header adı kalır değer maskelenir, 11 haneli
> TC kimlik — 10 haneli KR-11/KR-12 iş numaralarına DOKUNMUYOR, credential-şekilli
> key/value çiftleri); gerçek bir `LogstashEncoder` üzerinden uçtan uca test edildi
> (`LogstashJsonMaskingIntegrationTest`) çünkü ilk MDC alanları maskelemeyen bir
> `writeString` override'ı YAKALANDI ve düzeltildi (`writeObjectField`/`writeObject`
> override'ları eksikti — Jackson'ın generic Object yazma yolu `writeString`'i
> bypass ediyordu). **(3) Resilience4j SADECE kalıcı senkron OKUMA sınırlarına:**
> Mernis doğrulama (POST ama yan etkisiz, bu yüzden retry güvenli), lookup-service
> okumaları (4 servisin HEPSİNDE), KR-02 account/order-number arama okumaları
> (customer-service), customer/address doğrulama okumaları (account+product-service).
> **order-service → product-service/account-service SALE-write sınırına HİÇBİR ŞEY
> eklenmedi** (requirement 9) — bu tam olarak messaging'e taşınacak sınır;
> `NoResilienceOnSaleWriteClientsTest` reflection ile bu iki client'ta
> `@CircuitBreaker`/`@Bulkhead`/`@Retry` YOKLUĞUNU kanıtlıyor.
> `HttpAccountServiceClient#passivateAccount` (customer-service, DELETE, idempotent
> DEĞİL) de bilerek dışarıda — hiçbir resilience4j annotation'ı yok. Sıra:
> explicit connect(2s)/read(5s) timeout (httpclient5 auto-retry KAPALI, ADR-016
> §5.3b'nin AYNI gerekçesi) → bulkhead → circuit breaker (count-based, pencere 10,
> min 5 çağrı, %50 hata eşiği, 10s open, 3 half-open deneme) → retry (max 3, 200ms,
> SADECE dönüştürülmüş `*UnavailableException` tiplerine selective). **Sahte
> fallback YOK** — `fallbackMethod` hiçbir yerde kullanılmadı; `CallNotPermittedException`/
> `BulkheadFullException` her 4 servisin `GlobalExceptionHandler`'ına eklenen yeni
> handler'larla AYNI 503 `MSG-SERVICE-UNAVAILABLE` (Mernis için `MSG-MERNIS-UNAVAILABLE`)
> sözleşmesine düşüyor — önceden bu iki exception generic 500'e düşerdi (regresyon
> olurdu). `resilience4j-micrometer` ile CB/retry/bulkhead metrikleri de Prometheus'a
> otomatik bağlandı. **(4) Compose'a opt-in `observability` profile:** Prometheus
> (`:9090`) + Loki (`:3100`) + Grafana Alloy (docker socket read-only, container
> log'larını Loki'ye taşır) + Grafana (`:3000`, datasource+dashboard otomatik
> provision). Tek dashboard "CRM Lite — Overview": request rate, error rate, p95
> latency, service health, JVM heap, GC pause, HikariCP pool, downstream HTTP
> hataları + circuit breaker state, order submit outcomes. **Gerçekten başlatılıp
> doğrulandı** (2026-08-06): Prometheus healthy + 8 hedefi scrape ediyor (bazı
> hedefler bu commit'ten ÖNCEKİ eski image'lar çalıştığı için 401/404 gösterdi —
> aktif kullanımda olabilecek stack'i yeniden başlatmamak için image'lar
> REBUILD EDİLMEDİ), Grafana healthy + dashboard otomatik yüklendi, Loki + Alloy
> başladı. Spring Cloud Gateway KORUNDU (Zuul eklenmedi), mevcut RestClient
> adapter'ları KORUNDU (OpenFeign eklenmedi). Test kanıtı: crm-observability-starter
> 9/9 (masking regex + gerçek LogstashEncoder ile uçtan uca), customer-service
> 107/107 (5 yeni resilience testi: timeout, open circuit, half-open recovery,
> safe-read retry, no-retry-on-write, bulkhead dahil), account-service 51/51,
> product-service 37/37, order-service 31/31 (yeni reflection guard testi dahil),
> lookup-service 4/4, mernis-stub 3/3 — tüm reactor `mvn compile` YEŞİL.
> Detay: `docs/runbooks/observability.md`, `docs/runbooks/resilience.md`.)
> Önceki durum: 2026-08-06 (**FR-SALE idempotency temeli UYGULANDI —
> `POST /api/orders` artık `Idempotency-Key` zorunlu (ADR-016 §10 eki):** aynı
> anahtar + aynı normalize edilmiş gövde → ilk sonuç **aynen replay** edilir
> (`Idempotency-Replayed: true`); aynı anahtar + farklı gövde → 409
> `MSG-IDEMPOTENCY-KEY-CONFLICT`; aynı anahtarla eşzamanlı ikinci istek → 409
> `MSG-IDEMPOTENCY-KEY-IN-PROGRESS`; eksik/UUID olmayan anahtar → 400
> `MSG-IDEMPOTENCY-KEY-REQUIRED`. Uygulama katmanı `IdempotencyKeyFilter`
> (Spring Security zincirinden SONRA, controller'dan ÖNCE çalışır — 401/403 hep
> önce kazanır) + yeni `idempotency_key` tablosu (`order_db`, Flyway V3,
> workbook DIŞI proje eklentisi). **Eşzamanlılık garantisi bellek-içi kontrol
> DEĞİL, veritabanı UNIQUE kısıtı**: rezervasyon INSERT'i kendi bağımsız
> transaction'ında (`REQUIRES_NEW`) commit olur; yarışı kaybeden istek satırı
> yeniden okuyup replay/conflict kararını verir. Terminal her sonuç (başarı VEYA
> ele alınmış hata: 400/404/409/503) aynı şekilde kaydedilip replay edilir —
> ayrı bir "hangi hata cache'lenir" politikası icat edilmedi. **Aynı anahtar
> product-service'e `saleOperationId` olarak GÖNDERİLİR** (ADR-015 §9 eki):
> `POST /api/products` artık bu kimliğe göre replay-safe — yeni `sale_operation`
> tablosu (`product_db`, Flyway V5, UNIQUE `sale_operation_id`) + `prod.sale_operation_id`
> kolonu (aynı migration) her ürünü yaratan operasyona etiketler. Başarısız
> yaratma denemesi rezervasyonu SİLER (temiz retry mümkün); başarılı olan
> KALICI replay kaydı bırakır — order-service'in "her sonucu cache'le" kuralından
> BİLİNÇLİ olarak farklı (gerekçe: product-service'in yazımı tek yerel
> transaction, başarısızlık zaten hiçbir şey bırakmıyor). **Belirsiz genel
> `/api/products/cancel(productIds)` KALDIRILDI, yerine sale-scoped
> `/api/products/compensate({saleOperationId, productIds})` geldi:** artık
> gerçekten idempotent (zaten pasifleştirilmiş/PASV veya confirm sonrası ACTV
> ürün → no-op, eskiden 409 `MSG-PROD-NOT-PENDING` fırlatıyordu — bu da meşru
> bir retry'ı gerçek çakışmadan ayırt edilemez kılıyordu); **başka bir
> `saleOperationId`'ye ait ürüne dokunmayı REDDEDER** (409
> `MSG-SALE-OPERATION-MISMATCH`) — "başka bir satış operasyonuna ait ürünü iptal
> edemez" garantisi budur. Angular tarafında `OrderSubmitStore` artık her
> mantıksal submit denemesi için TEK bir `crypto.randomUUID()` üretir ve AYNI
> gövdeyle yapılan tekrar denemede (timeout/hata sonrası) AYNI anahtarı
> yeniden kullanır — gövde değişirse (sepet düzenlendiyse) YENİ anahtar
> üretilir, aksi halde eski anahtar backend'in "aynı anahtar farklı gövde" 409'una
> çarpardı. **Kafka/Debezium/asenkron işleme/saga state YOK** — mevcut senkron
> akış (ADR-016 §5) değişmedi, yalnız istemci tekrar denemelerine karşı
> korunuyor. Kod: `com.crm.order.order.idempotency` (order-service),
> `com.crm.product.product.idempotency` (product-service). Test kanıtı: yeni
> idempotency testleri her iki serviste eklendi (order-service
> `OrderServiceIntegrationTest` @Order(16-19); product-service
> `ProductServiceIntegrationTest` — replay, eşzamanlı-devam-eden-operasyon,
> compensate idempotency/ownership); frontend `order-submit.store.spec.ts`'e
> anahtar-yeniden-kullanım testleri eklendi. Detay: `docs/api/order-service.md`
> §Idempotency, `docs/api/product-service.md` §Idempotency addendum, ADR-016
> §10, ADR-015 §9.)
> Önceki durum: 2026-08-05 (**KR-02 UYGULANDI — Customer Search'ün
> `accountNumber`/`orderNumber` kriterleri artık GERÇEK: 501 kapısı KALKTI.**
> `CustomerBusinessRules.checkNoUnsupportedCrossServiceSearchCriterion` **silindi**;
> `MSG-FEATURE-NOT-IMPLEMENTED` artık **hiçbir servis tarafından üretilmiyor**.
> customer-service her numarayı **sahibi olan servisin mevcut public API'siyle**
> çözüyor — `GET /api/accounts/{n}` (ADR-013 §5) ve `GET /api/orders/{n}`
> (ADR-016 §3.2, zaten bu amacı yazıyordu); ikisi de yanıtında `customerNumber`
> taşıdığı için **hiçbir yeni uç, kontrat değişikliği veya id çevirisi
> gerekmedi**. Yeni sınır paketleri `com.crm.customer.{account,order}`
> (Client + Summary + Properties + Unavailable exception) ve iki yeni
> `RestClient` bean'i — Eureka üzerinden **doğrudan**, kullanıcı token'ı taşınarak
> (ADR-010), gateway'den DEĞİL. **`account_db`/`order_db` okunmuyor, join
> yapılmıyor, tablo kopyalanmıyor.** Kurallar: yalnız **Active** hesap ve yalnız
> **MIDLWARE** sipariş eşleşir (pasif hesap = FR-ACCT-04 soft delete'i; CANCELLED =
> telafi edilmiş satış), müşterinin kendisi de aktif olmalı; **dolu ama çözülemeyen
> numara HİÇBİR ŞEYLE eşleşmez** (`cb.disjunction()` — kriteri düşürmek sorguyu
> sessizce browse moduna genişletirdi); numaralar global tekil olduğu için
> **join yok**, sayfa metadata'sı **distinct müşteri** sayar; sahip servis
> erişilemezse **503 `MSG-SERVICE-UNAVAILABLE`** (fail-closed, boş sayfa DEĞİL).
> Compose'a `depends_on` **eklenmedi** — account-service zaten customer-service'e
> bağlı, ters kenar başlangıç döngüsü yaratırdı. **KR-01 vs AC-CUST-01-03
> çelişkisi kaydedildi ve KAPANDI: v8-2 metninde çelişki YOK, ikisi de Account/Order
> Number için "birebir" diyor** (`document-delta.md` §Conflict #7).
> **`dev` ile birleştirildi (aynı gün iki PR daha girdi):** **#29** müşteri silerken
> fatura hesaplarını pasifleştirmeyi (AC-CUST-05-04) ekledi ve **aynı
> `com.crm.customer.account` paketini** başka bir amaçla yaratmıştı — paket
> **birleştirildi**: tek `AccountServiceClient` (3 metot: `listAccounts` +
> `passivateAccount` + `fetchAccount`), tek `AccountSummary` (3 alan —
> `customerNumber` aramanın ihtiyacı), tek `accountRestClient` bean'i (onların
> `nonRetryingRequestFactory`'li sürümü kazandı: DELETE idempotent değil) ve
> `AccountServiceUnavailableException` için **tek** handler (iki ayrı
> `@ExceptionHandler` Spring'i açılışta "ambiguous" ile düşürürdü). **#30** ise
> v8-2 doküman mutabakatını yaptı — bu branch'in "ayrı iş" diye kaydettiği maddenin
> ta kendisi, dolayısıyla delta #9 olarak **kapatıldı**.
> Test kanıtı (birleşme sonrası): customer-service **94/94**, frontend **409/409**.
> Detay: §4.4, ADR-005 §Addendum 2026-08-05, `docs/api/customer-service.md`.)
> Önceki durum: 2026-08-02 (**FR-SALE §2.7 UYGULANDI — satış akışı uçtan uca:**
> YENİ `order-service` (port 8087, `order_db`, `com.crm.order`) FR-SALE-01/02'yi
> hayata geçirdi (**ADR-016**); product-service'e **yazma dilimi** (**ADR-015**),
> account-service'e **involvement yazma komutu** eklendi (**ADR-013 §3.6/§7/§8**).
> **2026-07-29'dan beri kayıtlı ADR borcu KAPANDI.** Satış üç veritabanına yazıyor
> ve **dağıtık transaction YOK**: akış tek bir *commit point* etrafında
> sıralandı — yerel sipariş yazımı (MIDLWARE) → ürünler **PNDG** olarak yaratılır →
> product_id + tutar snapshot'ları yerel olarak eklenir → ürünler ACTV'ye yükseltilir →
> **account-service involvement komutu** (satışı görünür kılan adım; FR-PROD-01
> involvement güdümlü). Commit point'ten ÖNCEKİ her hata: ürünler atılır, sipariş
> CANCELLED olur. Sonrasında adım yok, dolayısıyla geri alınacak şey de yok —
> involvement-delete komutu bu yüzden YAZILMADI (ADR-013 §8.6). **Sepet backend'de
> hiç saklanmıyor** (AC-SALE-01-16 yapısal olarak sağlanıyor); sepet/karakteristik
> doğrulaması **product-service'te** yaşıyor (ADR-015 §6), order-service anahtarları
> değiştirmeden aktarıyor. **KR-12** (sipariş numarası) proje-önerisi, analist onayı
> BEKLİYOR. Uygulama sırasında bulunan iki kusur ADR-016'ya işlendi: `@Transactional`
> self-invocation ile hiç çalışmıyordu (yerel yazmalar `OrderPersistence` bean'ine
> taşındı) ve httpclient5'in 503'te otomatik retry'ı tek POST'u iki siparişe
> çeviriyordu (giden client'larda kapatıldı — `POST /api/products` idempotent değil).
> Test kanıtı: order-service 25/25, product-service 33/33, account-service 51/51.
> Detay: §4.10, `docs/api/order-service.md`.)
> Önceki durum: 2026-07-29 (**product-service UYGULANDI — salt-okunur FR-PROD-01..02
> dilimi:** FR §2.6 kapsamı `backend/product-service`'te (port 8086, `product_db`,
> `com.crm.product`) hayata geçti: 10 workbook tablosu (V1) + seed (V2),
> `GET /api/products?accountNumber=`, `GET /api/products/{id}`, `GET /api/offers`,
> `GET /api/campaigns`. **`PROD`'da hesap/müşteri kolonu YOK** — ürün↔fatura hesabı bağı
> yalnız `account_db.cust_acct_prod_invl`'de, bu yüzden liste account-service'in YENİ
> `GET /api/accounts/{n}/product-ids` ucu üzerinden **kompoze** ediliyor (Eureka ile
> doğrudan, gateway'den DEĞİL — ADR-010); product-service `account_db`'ye asla dokunmaz
> (ADR-013 §5). Servis tipi spec üzerinden türetilir (GNL_TP 10/11/12), kampanyanın
> public kimliği `cmpg.campaign_code`, kampanya fiyatı üye tekliflerden **türetilir**.
> Sayfalama YOK (FR-PROD-01 kural içermiyor); pasif ürün listede "Passive" olarak kalır;
> çocuk ürün ebeveyninin Service Address'ini gösterir (customer-service'in YENİ dahili
> `GET /api/addresses/{id}` ucuyla çözülür). Faz A salt-okunur: ürün yaratma/provizyon/
> sepet/sipariş/Kafka/Redis YOK, karakteristik tabloları yalnız şema+seed. **Lookup HTTP
> client YOK** — yazma olmadığı için sadece `LookupContract` sabitleri (ADR-002 aynen
> korunuyor: lokal katalog tablosu/seed'i yok). Belgeli workbook sapmaları: uydurulmuş
> teklif fiyatları (**10.08.2026 itibarıyla: analist onayı BEKLEMİYOR — kararı A3 ile
> "non-normatif demo fixture", blocker değil**), kampanyalı/pasif ürün fixture'ları, ve
> ürün 3/4 involvement satırları account-service'in `V3` migration'ında. Test kanıtı:
> product-service 13/13, account-service 43/43, customer-service 80/80 YEŞİL.
> **ADR-015 (product boundary) + ADR-013'e "read-side" fıkrası BORÇLU.**
> Detay: §4.9, `docs/api/product-service.md`.)
> Önceki durum: 2026-07-23 (**account-service UYGULANDI — ADR-013/014:**
> FR-ACCT-01..04 + KR-11 kapsamı `backend/account-service`'te (port 8085, `account_db`,
> `com.crm.account`) hayata geçti. KR-11 Account Number: VARCHAR(10), Luhn check-digit,
> `acct_number_seq` upsert tahsisi (`next_value` = sıradaki değer; seed sonrası 100004),
> değişmez/asla yeniden kullanılmaz. Silme = pasifleştirme (Passive satır listede kalır);
> liste yalnız 224, Active→Passive + Account Number ASC, sayfalama YOK. **K-8 (analist
> onaylı):** ilk 224 oluşturulurken müşterinin tek 223 Customer Account'u AYNI ACID
> transaction'da tembel yaratılır — API'de asla görünmez (404). `cust_acct_prod_invl`
> yalnız account-service tarafından yazılan GERÇEK yerel guard state (AC-ACCT-04-03,
> 409 MSG-ACCT-HAS-PRODUCTS); gelecekte product/order servisleri account-service
> API'si/event'i üzerinden besler, account_db'ye ASLA doğrudan yazmaz. Adres doğrulama
> customer-service'in mevcut adres API'siyle (kullanıcı token'ı taşınır, ADR-010 eki);
> customer-service kaynak kodu BU sprintte DEĞİŞMEDİ (501/no-op'ların gerçek çağrıya
> dönmesi ayrı bir takip PR'ı). Gateway `/api/accounts/**` route'u + birleşik Swagger
> dropdown'ına account-service eklendi. Testcontainers yönetimi artık kök POM'da
> merkezî (BOM 1.21.3 + docker-java 3.5.1 + surefire `-Dapi.version=1.44` pluginManagement).
> Test kanıtı: account-service 41/41; tüm reactor `mvn clean verify` YEŞİL (133 test)
> — gateway Keycloak E2E'si 8080'i bağladığı için compose stack'i kapatılarak koşuldu.
> Detay: §4.8, ADR-013/014, `docs/architecture/account-service-decisions.md`.)
> Önceki durum: 2026-07-23 (**FR/AC v8-1 Final (23.07.2026) revizyonuyla mutabakat —
> yalnız dokümantasyon:** analist dokümanı KR-11 (Account Number formatı:
> 10 haneli `[T][YY][SSSSSS][C]`, bu faz için segment sabit `1`, segment+yıl bazlı sıra
> `100000`'den başlar, kalıcı/yeniden kullanılmaz, check-digit yöntemi teknik tasarıma bırakılmış)
> ve FR-ACCT-01/02/04'ü güncelledi: liste hem Active hem Passive hesapları gösterir
> (AC-ACCT-01-03), sıralama Active→Passive sonra Account Number ASC (AC-ACCT-01-04), **silme =
> pasifleştirme** — hesap Passive statüyle listede kalmaya devam eder, aktif listeden
> KALDIRILMAZ (AC-ACCT-04-02, önceki metnin tersi). `account-service` henüz YOK — bu bir
> **belgeleme turu**; hiçbir kod değişmedi. `account-service` roadmap'te artık **sıradaki
> onaylı Sprint domain'i** (hesap-özel ADR'ler bekleniyor — ADR-013/014 bu turda AÇILMADI).
> use-case dokümanı + draw.io hâlâ eski "aktif listeden kaldır" ifadesini taşıyor, workbook'taki
> `CUST_ACCT` örnek `account_number` değerleri KR-11 formatına uymuyor — üçü de yeni açık
> çelişki olarak kaydedildi (bkz. §9B, `docs/requirements/document-delta.md`).) Önceki durum:
> 2026-07-20 (**Swagger/OpenAPI eklendi — ADR-012:** tek, birleşik Swagger UI
> sadece `api-gateway`'de (`http://localhost:8080/swagger-ui.html`); customer-service +
> lookup-service sadece `/v3/api-docs` JSON'unu üretir (`springdoc.swagger-ui.enabled: false`),
> gateway bunları proxy route'larıyla (`RewritePath`, TokenRelay YOK) birleştirir. Host portu
> AÇILMADI — ADR-009 korundu; "Try it out" mevcut BFF session cookie'sini kullanır. Detay §4.7.)
> Önceki durum: 2026-07-18 (**AUTH/SECURITY milestone'u uygulandı — ADR-006..011:**
> Keycloak 26.3.4 tek kimlik/token otoritesi (realm `crm-lite`, import `infra/keycloak/`);
> api-gateway artık **BFF** (Authorization Code + PKCE `oauth2Login`, HttpOnly session +
> XSRF çerezleri, TokenRelay, RP-initiated logout) — permitAll KALKTI; customer-service +
> lookup-service **zero-trust JWT resource server** (`backend/crm-security-starter` ortak
> modülü: imza/iss/aud/`crm-user` rol doğrulama, 401/403 kontratı); audit `*_by` kolonları
> artık Keycloak `sub`; kullanıcı token'ı lookup-service'e taşınır, mernis-stub'a ASLA
> taşınmaz; **auth-service iskeleti SİLİNDİ**; USERS/password tablosu YOK (ADR-011,
> analist onayı bekliyor). Önceki durum: 2026-07-16 FR/AC v8 Final revizyonu ile mutabakat —
> bkz. `docs/requirements/document-delta.md`: **AC-CUST-01-00** login sonrası tüm müşteri listesi;
> `GET /api/customers` artık kritersiz **browse modu** + `Page<CustomerDetailResponse>` dönüyor
> [ADR-005]; zorunlu-kriter kuralı ve `MSG-SEARCH-CRITERIA-REQUIRED` KALDIRILDI; MERNIS mesaj
> anahtarları analist kataloğuyla hizalandı: `MSG-CUST-NATID-VERIFICATION-FAILED` +
> `MSG-MERNIS-UNAVAILABLE`; varsayılan dil İngilizce; Postman koleksiyonu `docs/postman/`;
> Git akışı `CONTRIBUTING.md` + `docs/runbooks/git-workflow.md`. Önceki durum: customer
> agregatı + lookup-service + mernis-stub [ADR-001..004].)
> **Bu dosyayı güncel tut:** Her anlamlı değişiklikten sonra ilgili bölümü ve "Sırada ne var" listesini güncelle.

---

## 1. Proje Özeti

CRM Lite — Spring Boot tabanlı bir **mikroservis monorepo**'su. Altyapı çekirdeği
(config server + service discovery + API gateway) kurulu ve çalışır durumda. `customer-service`
**müşteri agregatının tamamını** (demografik + adres + iletişim, ADR-001) final Entity/Seed
workbook şemasıyla implemente ediyor; paylaşılan GNL_ST/GNL_TP kataloglarının sahibi
**`lookup-service`** (ADR-002) ve KR-10 kimlik doğrulaması için **`mernis-stub`** ayakta.
**Kimlik doğrulama UYGULANDI (ADR-006..011):** Keycloak tek otorite, api-gateway BFF,
domain servisleri zero-trust resource server; `auth-service` iskeleti SİLİNDİ (ADR-007).

- **Dil / Runtime:** Java 25
- **Framework:** Spring Boot `4.1.0`, Spring Cloud `2025.1.2`
- **Build:** Maven (monorepo, kök parent POM ile)
- **Container:** Dockerfile'lar + `infra/docker-compose.yml` (Rancher Desktop **ve** Podman uyumlu)
- **Geliştirme ortamı:** Windows 11, IntelliJ IDEA. Makinede terminalde `mvn`/`docker` PATH'te
  kurulu DEĞİL — servisler IDE'den (Run) veya Podman/Rancher üzerinden çalıştırılıyor.

---

## 2. Mimari

```
                 login redirect (Auth Code + PKCE)
   Tarayıcı ◄───────────────────────────────────► Keycloak :8180 (realm crm-lite)
      │ HttpOnly SESSION + XSRF çerezi                 ▲ token/JWKS (server-side)
      ▼                                                │
   ┌──────────────────────────────────────────────────┴┐
   │ api-gateway :8080 — BFF (Spring Cloud Gateway     │
   │ WebMVC: oauth2Login, CSRF, TokenRelay, /logout,   │
   │ /api/session/me — ADR-007/008)                    │
   └──────────┬────────────────────────────────────────┘
              │ lb:// (Eureka) + Authorization: Bearer <Keycloak JWT>
              ▼
   customer-service :8082 ── lookup-service :8083   (hepsi zero-trust JWT
        ▲     │                                      resource server — ADR-009,
        │     └──► mernis-stub :8084 (token YOK,     crm-security-starter)
        │          dış sistem simülasyonu, ADR-010)
        │
   account-service :8085 ◄──── product-service :8086
   (cust_acct_prod_invl        (product_db; FR-PROD-01 listesi
    TEK yazarı — ADR-013 §5)    /api/accounts/{n}/product-ids ile
        ▲                       KOMPOZE edilir; account_db'ye ASLA
        └───────────────────────dokunmaz. Service Address'i
   product-service adres için   customer-service'ten çözer.)
   customer-service'e de gider ─┘   (ikisi de lb:// Eureka, gateway'den DEĞİL — ADR-010)

   discovery-server :8761 (Eureka) · config-server :8888 (native/classpath repo)
```

| Servis | Port | Rol | Durum |
|---|---|---|---|
| `config-server` | 8888 | Merkezi config (Spring Cloud Config Server, native/classpath) | ✅ Çalışıyor |
| `discovery-server` | 8761 | Eureka service registry | ✅ Çalışıyor |
| `api-gateway` | 8080 | **BFF** (WebMVC): routing + Auth Code/PKCE login + session/CSRF + TokenRelay (ADR-007/008) | ✅ Çalışıyor — Keycloak login gerekli |
| **keycloak** (infra) | 8180 | **Tek kimlik/token otoritesi** — realm `crm-lite`, client `crm-bff` (public+PKCE), rol `crm-user` (ADR-006) | ✅ Compose'da — `quay.io/keycloak/keycloak:26.3.4`, `keycloak_db` |
| `crm-security-starter` | — | Ortak resource-server güvenlik modülü (kütüphane, deployable DEĞİL — ADR-009) | ✅ Kod + 16 birim testi |
| `customer-service` | 8082 | Müşteri agregatı: FR-CUST + FR-ADDR + FR-CNTC (`customer_db`) — **JWT resource server** | ✅ Çalışıyor — Postgres + lookup-service + mernis-stub (yazma işlemleri için) |
| `lookup-service` | 8083 | **Paylaşılan GNL_ST/GNL_TP kataloglarının TEK sahibi** (`lookup_db`, ADR-002) — **JWT resource server** | ✅ Çalışıyor — Postgres gerekli |
| `mernis-stub` | 8084 | Fake MERNİS/KPS doğrulama (KR-10) — DB'siz, deterministik, gateway'e AÇIK DEĞİL, **CRM token'ı görmez (ADR-010)** | ✅ Çalışıyor |
| `account-service` | 8085 | Fatura hesapları: FR-ACCT-01..04 + KR-11 (`account_db` — ADR-013/014) — **JWT resource server**; K-8 tembel 223; silme = pasifleştirme | ✅ Uygulandı (2026-07-23) — Postgres + lookup-service + customer-service (yazma işlemleri için) |
| `product-service` | 8086 | Ürün görüntüleme + katalog + **FR-SALE yazma dilimi**: FR-PROD-01..02 & ürün yaratma/confirm/cancel + karakteristik şeması (`product_db`) — **JWT resource server**; liste account-service'in `product-ids` ucu üzerinden kompoze; `account_db`'ye ASLA dokunmaz; sepet/karakteristik doğrulaması burada | ✅ Uygulandı (2026-07-29 okuma + 2026-08-02 yazma, ADR-015) — Postgres + account + customer + **lookup** |
| `order-service` | 8087 | **Satış orkestrasyonu + siparişler:** FR-SALE-01..02 (`order_db` — ADR-016/**ADR-018**) — **JWT resource server**; KR-12 sipariş numarası (taslakta tahsis edilir); **CANLI akış asenkron:** taslak → 202 submit → status polling, kalıcı `sale_saga` ile orkestrasyon ve iki yönlü telafi. Senkron `POST /api/orders` deprecated ama build'de (geri dönüş + mevcut frontend) | ✅ Uygulandı (2026-08-02), **asenkron kesim 2026-08-10** — Postgres + lookup + account + product (+ Kafka yalnız `async-sale` profilinde) |
| ~~`auth-service`~~ | ~~8081~~ | ~~Kimlik doğrulama~~ | 🗑️ **SİLİNDİ (2026-07-17, ADR-007)** — BFF gateway'de, kimlik Keycloak'ta; iskelet geri getirilmeyecek |

**Planlanan servisler (henüz YOK — analist/mimari onayı bekliyor; ayrıntı:
`docs/architecture/service-boundaries.md` yol haritası):**

| Planlanan | Muhtemel sahiplik | Durum |
|---|---|---|
| ~~auth/security milestone~~ | Keycloak tek otorite + gateway BFF + zero-trust resource server + JWT-sub audit (ADR-006..011). auth-service iskeleti SİLİNDİ | ✅ **UYGULANDI (2026-07-17)** |
| localization-service | FR-LANG merkezi etiket/mesaj kataloğu (varsayılan dil artık **İngilizce**, 16.07.2026). Backend zaten dil-bağımsız `messageKey` dönüyor | 🗓️ Planlı, başlanmadı |
| ~~account-service~~ | ACCT_TP + CUST_ACCT (fatura hesapları) — ADR-013/014 | ✅ **UYGULANDI (2026-07-23)** — bkz. §2 tablo + §4.8 |
| ~~product-service~~ | PROD_SPEC/PROD_OFR/PROD/CMPG*/PROD_CATAL* (product+catalog birleşik) | ✅ **UYGULANDI** — okuma 2026-07-29, **yazma dilimi 2026-08-02 (ADR-015)**; bkz. §4.9/§4.10 |
| ~~order-service~~ | BSN_INTER, CUST_ORD, CUST_ORD_ITEM + satış orkestrasyonu (FR-SALE) | ✅ **UYGULANDI (2026-08-02, ADR-016)** — bkz. §2 tablo + §4.10 |

**address-service / contact-service ASLA ayrı deployable OLMAYACAK** — ADR-001 gereği
customer-service'in iç modülleri.

---

## 3. Depo Yapısı

```
crm-lite-project-dev/
├── pom.xml                        # KÖK PARENT POM (packaging: pom) — ortak versiyonları yönetir
├── .dockerignore                  # Docker build context'i küçük tutar (target, .git, .idea hariç)
├── PROJECTBRAIN.md                # BU DOSYA
├── backend/
│   ├── config-server/
│   │   ├── pom.xml                # parent = com.crm:crm-lite-project
│   │   ├── Dockerfile             # root context, maven base image
│   │   └── src/main/
│   │       ├── java/com/crm/configserver/ConfigServerApplication.java  (@EnableConfigServer)
│   │       └── resources/
│   │           ├── application.yml            # profile=native, search-locations=classpath:/config-repo/
│   │           └── config-repo/                # her servisin config'i burada, servis adına göre dosyalanmış
│   │               ├── discovery-server.yml
│   │               ├── api-gateway.yml         # BFF: oauth2 client + route'lar + TokenRelay (ADR-007)
│   │               ├── customer-service.yml    # crm.security.issuer dahil (ADR-009)
│   │               ├── lookup-service.yml
│   │               └── mernis-stub.yml
│   ├── discovery-server/
│   │   ├── pom.xml                # parent = com.crm:crm-lite-project
│   │   ├── Dockerfile             # root context, maven base image
│   │   └── src/main/
│   │       ├── java/com/crm/discovery/DiscoveryServerApplication.java  (@EnableEurekaServer)
│   │       └── resources/application.yml
│   ├── api-gateway/
│   │   ├── pom.xml
│   │   ├── Dockerfile
│   │   └── src/main/
│   │       ├── java/com/crm/gateway/
│   │       │   ├── ApiGatewayApplication.java
│   │       │   └── config/SecurityConfig.java   # permitAll (geçici)
│   │       └── resources/application.yml
│   ├── crm-security-starter/          # ADR-009: ortak resource-server güvenlik autoconfig'i
│   │   ├── pom.xml                    # kütüphane — spring-boot-maven-plugin repackage YOK
│   │   └── src/main/java/com/crm/security/starter/
│   │       ├── CrmResourceServerAutoConfiguration.java  (varsayılan chain; her bean @ConditionalOnMissingBean)
│   │       ├── KeycloakRealmRoleConverter.java  AudienceValidator.java
│   │       ├── RestAuthenticationEntryPoint/RestAccessDeniedHandler (401/403 JSON kontratı)
│   │       ├── JwtAuditorAware.java   (sub → audit; fallback "system")
│   │       ├── BearerTokenPropagationInterceptor.java   (opt-in, ADR-010; kullanıcı token'ı
│   │       │     yoksa ServiceAccountTokenProvider'a düşer — ADR-010 eki 13.08.2026)
│   │       └── ServiceAccountTokenProvider.java   (client-credentials, önbellekli; sadece
│   │             crm.security.service-account.client-id set'liyken bean olarak var olur —
│   │             bugün yalnız account/product-service'in async-sale profili)
│   └── customer-service/
│       ├── pom.xml                # parent = com.crm:crm-lite-project + annotationProcessorPaths(lombok) fix
│       ├── Dockerfile             # root context, maven base image
│       └── src/main/
│           ├── java/com/crm/customer/
│           │   ├── CustomerServiceApplication.java
│           │   ├── common/exception/    (BusinessException, ErrorResponse, GlobalExceptionHandler, MessageKeys)
│           │   └── customer/
│           │       ├── controller/CustomerController.java
│           │       ├── service/CustomerService.java + service/impl/CustomerServiceImpl.java
│           │       ├── rules/CustomerBusinessRules.java
│           │       ├── repository/ (5 repo + CustomerSpecifications)
│           │       ├── entity/ (Role, Party, Individual, PartyRole, Customer, Status, Gender)
│           │       ├── dto/request/ + dto/response/
│           │       └── mapper/CustomerMapper.java
│           └── resources/
│               ├── application.yml
│               └── db/migration/ (V1__create_customer_tables.sql, V2__seed_customer_data.sql)
└── infra/
    ├── docker-compose.yml         # config + discovery + gateway + KEYCLOAK + postgres + lookup + mernis + customer
    ├── keycloak/realm/crm-lite-realm.json   # ADR-006: realm import (client crm-bff, rol crm-user, dev
    │                                         #   kullanıcıları; + client crm-saga-worker, service-account,
    │                                         #   ADR-010 eki 13.08.2026 — async SALE saga'nın servis kimliği)
    └── postgres/init/01-create-databases.sql   # CREATE DATABASE customer_db + lookup_db + keycloak_db
```

---

## 4. Servislerin Güncel Durumu (detay)

### 4.0 config-server ✅
- `@EnableConfigServer`, port 8888.
- Profil `native`, kaynak `classpath:/config-repo/` — yani config dosyaları **git-backed bir dış repo değil**,
  bu servisin kendi `src/main/resources/config-repo/` klasöründe duruyor ve jar'a gömülüyor.
- Her istemci servis kendi adına göre bir dosya çeker (`discovery-server.yml`, `api-gateway.yml`,
  `customer-service.yml`, `lookup-service.yml`, `mernis-stub.yml`).
  `application.yml` adlı bir dosya varsa (şu an yok) tüm servislere ortak uygulanır.
- **Trade-off:** classpath kaynaklı olduğu için bir config değişikliği config-server'ın rebuild/restart
  edilmesini gerektirir; git-backed'deki gibi canlı `/actuator/refresh` akışı yok. Dev aşaması için kabul edilen ödün.
- Diğer üç servis `spring.config.import: "optional:configserver:http://localhost:8888"` ile buna bağlanıyor;
  `optional:` öneki sayesinde config-server ayakta değilse servis yine de (config-server'dan önceki) yerel
  varsayılanlarıyla açılmaya çalışır — sert bir bağımlılık değil, ama pratikte config-server'ın **ilk** açılması gerekiyor.

### 4.1 discovery-server ✅
- `@EnableEurekaServer`, port 8761.
- `application.yml`: kendini register etmiyor (`register-with-eureka: false`, `fetch-registry: false`) — bu ayar artık
  yerel dosyada değil, `config-server`'ın `config-repo/discovery-server.yml` dosyasında.
- IDE'den çalışıyor, dashboard `http://localhost:8761` açılıyor.
- **Not:** Eureka "self-preservation" uyarısı (EMERGENCY banner) tek client varken normaldir, zararsız.

### 4.2 api-gateway ✅
- `spring-cloud-starter-gateway-server-webmvc` → **WebMVC (servlet) stack** (WebFlux DEĞİL). Bu ayrım kritik (bkz. §6).
- Eureka client olarak kayıt oluyor.
- **Security (2026-07-17'den beri):** `config/SecurityConfig.java` → BFF chain: `oauth2Login`
  (Auth Code + PKCE) + CSRF (XSRF-TOKEN/SPA handler) + rol bazlı route koruması + JSON 401/403 +
  RP-initiated logout. Eski permitAll/csrf-disable scaffolding'i KALKTI (bkz. §4.3, ADR-007/008).
- **Route:** `spring.cloud.gateway.server.webmvc.routes` altında customers/cities/lookups →
  ilgili servisler; her route'ta `TokenRelay=` + `RemoveRequestHeader=Cookie` filtreleri.
  (Eski `/api/auth/**` → `lb://auth-service` route'u auth-service ile birlikte silindi.)
- **Timeout:** `spring.http.client.connect-timeout: 2s`, `read-timeout: 5s` (WebMVC'nin doğru property'leri).
- **Eureka instance:** `prefer-ip-address: true`, `instance-id: ${spring.application.name}:${random.value}`.
- **Actuator:** `health, info, mappings` açık.
- **Swagger UI (ADR-012, YENİ):** `http://localhost:8080/swagger-ui.html` — customer-service ve
  lookup-service'in `/v3/api-docs`'unu proxy'leyip tek sayfada birleştirir; detay §4.7.
- **Not:** Route/timeout/eureka/actuator ayarlarının tamamı artık yerel `application.yml`'de değil,
  `config-server`'ın `config-repo/api-gateway.yml` dosyasında. Yerel dosyada sadece `spring.application.name`
  ve `spring.config.import` kaldı.
- **Doğrulanmış davranış (auth sonrası):**
  - `GET /actuator/health` → `{"status":"UP"}` (tek anonim actuator endpoint'i).
  - Anonim `GET /api/customers` (Accept: application/json) → **401** `MSG-AUTH-UNAUTHORIZED`.
  - Tarayıcıdan korumalı sayfa → Keycloak login redirect'i (E2E: `GatewayBffIntegrationTest`).
- **`GatewayExceptionHandler` (yeni, `com.crm.gateway.exception`):** downstream servis Eureka'da hiç kayıtlı
  değilse, `LoadBalancerFilterFunctions` bir `HttpServerErrorException` fırlatıyor ve gerçek status'u
  (örn. 503, mesajı "Unable to find instance for X") kendi içinde taşıyor — ama hiçbir şey bunu geri okumadığı
  için düzeltilmeden önce **generic 500**'e düşüyordu (gerçekte gözlemlendi: `mvn spring-boot:run` ile
  auth-service kapalıyken `GET /api/auth/login` **500** dönüyordu, PROJECTBRAIN'deki eski "503" notu yanlıştı).
  Yeni `@RestControllerAdvice`: `HttpStatusCodeException`'ı yakalayıp gömülü status'u (503 için
  `MSG-SERVICE-UNAVAILABLE`) response'a yansıtıyor; `ResourceAccessException` (instance bulundu ama TCP
  bağlantısı başarısız) için de 503 dönüyor; kalan her şey için generic 500 + `MSG-INTERNAL-ERROR`.
  Response şekli: `{timestamp, status, error, messageKey, message, path}` — customer-service'in
  `ErrorResponse`'una benzer ama gateway'e özel ayrı bir `GatewayErrorResponse` record'u.

### 4.3 Kimlik doğrulama / güvenlik ✅ (2026-07-17 milestone — ADR-006..011; auth-service SİLİNDİ)
- **Keycloak tek otorite (ADR-006):** `quay.io/keycloak/keycloak:26.3.4` (compose, port 8180,
  `keycloak_db`), realm **`crm-lite`** repo'daki `infra/keycloak/realm/crm-lite-realm.json`'dan
  otomatik import. Client **`crm-bff`**: PUBLIC + **PKCE S256**, Direct Grant/ROPC **KAPALI**;
  audience mapper access token'a **`crm-api`** yazar; rol: **`crm-user`** (KR-8). Dev kullanıcıları:
  `ayilmaz`/`edemir` (aktif) + `mkaya` (disabled — AC-AUTH-01-04 fikstürü), şifre `crm-dev`
  (yalnız yerel). KR-9: access token 5dk, SSO idle 30dk, SSO max 24s. `KC_HOSTNAME` ile `iss`
  her topolojide `http://localhost:8180/realms/crm-lite`'a sabitlenir; container'lar JWKS'i
  `http://keycloak:8180` üzerinden çeker (env override) — issuer doğrulaması hep kanonik değer.
- **api-gateway = BFF (ADR-007/008):** `oauth2Login` (Authorization Code + PKCE), token'lar
  YALNIZ sunucu tarafında (session-bound OAuth2AuthorizedClientService); tarayıcı sadece
  HttpOnly `JSESSIONID` + okunabilir `XSRF-TOKEN` görür. Route'larda `TokenRelay=` +
  `RemoveRequestHeader=Cookie`; `/api/**` → 401/403 JSON (`MSG-AUTH-UNAUTHORIZED` /
  `MSG-AUTH-FORBIDDEN` / `MSG-AUTH-CSRF-REJECTED`), sayfa gezinmesi → Keycloak'a redirect.
  `GET /api/session/me` (Angular oturum probu), CSRF-korumalı `POST /logout` → RP-initiated
  Keycloak logout. E2E: `GatewayBffIntegrationTest` (10 test, gerçek Keycloak Testcontainers,
  commit'li realm import'la; token expiry/refresh dahil).
- **Login sonrası varış noktası (2026-07-30 düzeltmesi):** `oauth2Login` artık açık bir
  `successHandler` taşıyor — hedef **daima** istekten türetilen uygulama kökü
  (`ServletUriComponentsBuilder.fromContextPath`, `KeycloakLogoutSuccessHandler` deseni):
  nginx üzerinden gelen login `http://localhost:4200/`'e (Angular `'' → customers`),
  doğrudan hit `http://localhost:8080/`'e döner; gateway frontend origin'ini hardcode
  etmez. Ayrıca **request cache tamamen kapatıldı (`NullRequestCache`)**.
  Sebep (canlıda gözlendi): `ExceptionTranslationFilter` entry point'ten ÖNCE
  `saveRequest()` yaptığı için `/api/**` 401 JSON dönse bile URL oturuma kaydoluyor;
  `:4200` ile `:8080` aynı JSESSIONID'yi paylaştığından bayat bir ham API sekmesi sonraki
  login'i kaçırıp kullanıcıyı `…/api/products/2?continue`'ya düşürüyordu. İlk deneme
  (yalnız `/api/**` + `/actuator/**` dışlayan negatif matcher) **eksik kaldı**: tarayıcı
  gösterdiği sekme için alt-kaynak da istediğinden varış `…/favicon.ico?continue` oldu.
  Kara liste bu sınıfı kapatamaz; "yalnız navigasyon" filtresi de yetmez (insan adres
  çubuğuna API URL'i yazabilir). Asıl gerekçe: **bu gateway'in gezilebilir HTML sayfası
  yok** (JSON uçları + `permitAll` Swagger + eşleşmeyen yollar), SPA deep-link'leri nginx
  statiğinden Angular router'a gidiyor → cache'in meşru girdisi hiç yok.
  **Şu iki durumda yeniden değerlendirilecek:** gateway gezilebilir HTML sunarsa, ya da
  Swagger UI auth arkasına alınırsa. Test: `postLoginLandingIsAlwaysTheApplicationRoot`.
- **Eşleşmeyen yol artık 404 (aynı tur, bağımsız kusur):** `GatewayExceptionHandler`'a
  `NoResourceFoundException` + `NoHandlerFoundException` → **404 `MSG-NOT-FOUND`**
  eklendi. Önceden catch-all'a düşüp **500 `MSG-INTERNAL-ERROR` + stack trace** üretiyordu
  — oturumu açık bir kullanıcının `:8080/favicon.ico` isteğinde bile. Test:
  `unmappedPathIsNotFoundNotServerError`. `GatewayBffIntegrationTest` 8 → **10 test**.
  Kayıt: `docs/frontend/scope-and-conflicts.md` §5.11 + §5.12.
- **Zero-trust resource server'lar (ADR-009):** customer-service + lookup-service,
  `crm-security-starter` ile imza(JWKS)/issuer/audience(`crm-api`)/rol(`crm-user`) doğrular —
  gateway'i geçmiş olmak yetmez; doğrudan servis çağrısı da token ister (testli). Çerez asla
  parse edilmez; sadece `/actuator/health` anonim.
- **Servisler-arası (ADR-010):** customer→lookup kullanıcı token'ı taşır (sub korunur,
  `BearerTokenPropagationInterceptor`); customer→mernis **token TAŞIMAZ** (dış KPS simülasyonu).
  Kanıt: `OutboundBearerPropagationTest`.
- **Servis hesabı — async SALE saga (ADR-010 eki, 13.08.2026):** account/product-service'in
  saga handler'ları (Kafka listener thread'inde, kullanıcı isteği YOK) aynı customer-service/
  lookup-service çağrılarını yapıyor ama taşıyacak token bulamıyordu (401, satış `prepare`
  adımında sessizce tıkanıyordu). `crm-saga-worker` client-credentials client'ı (Keycloak) +
  `ServiceAccountTokenProvider` fallback'i eklendi — `BearerTokenPropagationInterceptor`
  SecurityContext'te kullanıcı token'ı bulamazsa buna düşer. Yalnız async-sale profilinde
  account/product-service için aktif; request-bound çağrıların hiçbiri etkilenmedi.
- **Audit (ADR-004 kapanışı):** `created/updated/deleted_by` artık JWT `sub`
  (`CurrentActorProvider`); Flyway seed satırları `system` kalır. Testli
  (`auditColumnsCarryKeycloakSubject`).
- **auth-service:** modül, config dosyası ve `/api/auth/**` route'u **tamamen silindi**
  (ADR-007) — JJWT bağımlılıkları da onunla gitti. Workbook USERS/password tablosu
  UYGULANMADI (ADR-011 — analist onayı bekleyen kayıtlı çelişki).

### 4.4 customer-service ✅ (müşteri AGREGATI — 2026-07-11 refactor'u)
- Port 8082, DB `customer_db`. Kapsam: **FR-CUST-01..05 + FR-ADDR-01..05 + FR-CNTC-01..02** —
  adres ve iletişim ayrı servisler DEĞİL, aynı deployable'ın iç modülleri (ADR-001; atomik create gerekçesiyle).
- **Paket yapısı:** `com.crm.customer.{customer, address, contact, lookup, mernis, common}`.
  Katmanlar: Controller → Service → BusinessRules → Repository; `common/entity` altında
  `AuditableEntity` + `StatusAwareEntity` mapped superclass'ları (created/updated/deleted _date/_by + status_id).
- **Veri modeli (final workbook ile hizalı):** `role` / `city` / `district` / `party` / `ind` /
  `party_role` / `cust` / `addr` / `cntc_medium` (küçük harf fiziksel adlar). Flyway V1+V2 baseline,
  `ddl-auto: validate`. **`gnl_st`/`gnl_tp` tablosu YOK** — bunlar merkezi kataloglar (bkz. §4.5, ADR-002);
  `status_id`/`gender_id`/`party_type_id` kolonları merkezi kontrat ID'lerini taşıyan **dış referanslardır**
  (FK'sız; cross-database FK kullanılmaz). Aktif kayıt filtresi tamamen yerel:
  `status_id = 1 (ACTV) AND deleted_date IS NULL` — sorgu başına uzak çağrı yok.
- **İş kimliği:** `cust.customer_number` (sequence, 1001'den başlar) dışa açılan Customer ID'dir;
  içsel `cust.id` API'ye asla sızmaz. Arama parametresi `customerId` ve `{customerNumber}` path'leri
  hep iş numarasıdır.
- **Endpoint'ler:** liste+filtre TEK kanonik endpoint `GET /api/customers` (ADR-005) —
  **`/api/customers/search` alias'ı KALDIRILDI** (yayınlanmış tüketici yok). + detay/create/update/
  delete, `/addresses` CRUD + `PATCH .../primary`, `/contact-medium` GET/PUT,
  `GET /api/cities(/{id}/districts)`, ve **`GET /api/customers/nationality-id-availability`**
  (29.07.2026, ADR-005 §Addendum — NAT ID müsaitlik sorgusu; liste alias'ı DEĞİL: müşteri
  verisi döndürmez, tek alan `{"available": bool}`, **soft-deleted sahipleri de** raporlar
  çünkü ADR-003 kuralını create yolunun metoduyla yanıtlar; advisory — POST'un 409'u otorite).
  Tam liste: docs/api/customer-service.md.
- **Liste + filtre (ADR-005, 16.07.2026 revizyonu — AC-CUST-01-00):** kritersiz istek artık
  **browse modu**: TÜM aktif müşteriler, sunucu taraflı sayfalı (varsayılan page=0, size=20),
  firstName→lastName→customerNumber A-Z. Zorunlu-kriter kuralı
  (`checkAtLeastOneSearchCriterionExists` + `MSG-SEARCH-CRITERIA-REQUIRED`) KALDIRILDI.
  Her satır **tam `CustomerDetailResponse` kontratı** taşır (tekil detay endpoint'iyle birebir aynı
  alanlar; eski `CustomerSearchResponse` ve `customerId` yanıt alanı SİLİNDİ — sorgu parametresi
  `customerId` adını koruyor). N+1 koruması: satır sorgusu to-one detay grafiğini fetch-join'ler,
  count sorgusu düz join kullanır.
- **Filtre semantiği (KR-01, değişmedi):** `firstName` = First+Middle birleşiminde **kelime-başı**
  eşleşme ("Kemal" → "Ali Kemal"i bulur; "li" → "Ali"yi BULMAZ); `lastName` = Last Name'de
  kelime-başı; ikisi doluysa AND. `gsmNumber` = mobile_phone **prefix**; `nationalityId`/`customerId`
  birebir. Dolu kriter grupları OR'lanır; yalnız aktif müşteriler; sonuçlar müşteri bazlı distinct
  (tüm join'ler to-one).
- **`accountNumber`/`orderNumber` (KR-02 — YENİ, 05.08.2026, ADR-005 §Addendum):** artık
  **birebir** eşleşen gerçek kriterler; 501 kapısı kalktı. Her numara **sahibi olan
  servisin mevcut public ucundan** çözülür (`GET /api/accounts/{n}` → `AccountSummary`,
  `GET /api/orders/{n}` → `OrderSummary` — ikisi de yanıtında `customerNumber`
  taşıdığı için yeni uç/kontrat gerekmedi) ve sonuç `cust.customer_number` eşitliği
  olarak **aynı OR ifadesine** girer. Yalnız **Active** hesap ve yalnız **MIDLWARE**
  sipariş sayılır (pasif hesap = FR-ACCT-04 soft delete'i, CANCELLED = telafi edilmiş
  satış); K-8 223 hesabı account-service'te zaten 404 olduğu için asla arama anahtarı
  olamaz. **Çözülemeyen dolu numara FALSE predicate'i üretir** — kriteri düşürmek
  sorguyu sessizce browse moduna genişletirdi. Numaralar global tekil olduğundan
  **join EKLENMEZ**; sayfa metadata'sı distinct müşteri sayar. Sahip servis
  erişilemezse **503 `MSG-SERVICE-UNAVAILABLE`** (fail-closed). Numaralar uçtan uca
  **String** (baştaki sıfır anlamlı); kriter boşsa **hiçbir dış çağrı yapılmaz**.
  Sınır kodu: `com.crm.customer.{account,order}` + `HttpClientConfig`'teki iki yeni
  `RestClient` (Eureka üzerinden doğrudan, kullanıcı token'ı taşınır — ADR-010).
  KR-04 sayfalama (ADR-005 §Amendment, 29.07.2026): `size` varsayılanı **15**, kabul edilen tek
  değerler **15/30/50** — başka her değer (17, 999999, 0) 400 `MSG-VALIDATION-ERROR`; negatif `page`
  de aynı şekilde 400 (0 ve -1 daha önce `PageRequest.of` üzerinden 500 dönüyordu). `page`'in üst
  sınırı YOK: aralık dışı sayfa normal 200 + boş içerik. Whitelist tek kaynak:
  `common/validation/AllowedPageSize`.
- **Atomik create (AC-CUST-03-21 + KR-10):** tek istek `{demographic, addresses[], contactMedium}` →
  tek `@Transactional` içinde: bean validation (VR-NAME/NATID/EMAIL/PHONE/MOBILE) → iş kuralları
  (yaş/doğum tarihi/NATID tekilliği/primary normalizasyonu/city-district) → **merkezi katalog çözümü**
  (ACTV/INDV/MALE-FEMALE, lookup-service üzerinden; ulaşılamazsa 503 `MSG-SERVICE-UNAVAILABLE`
  fail-closed) → **MERNIS doğrulaması** (reddedilirse 400 `MSG-CUST-NATID-VERIFICATION-FAILED`,
  ulaşılamazsa 503 `MSG-MERNIS-UNAVAILABLE` — 16.07.2026 analist katalog anahtarları; müşteri
  OLUŞMAZ) → PARTY→IND→PARTY_ROLE→CUST→ADDR→CNTC_MEDIUM persist. Herhangi bir hata = hiçbir satır
  kalmaz (entegrasyon testli).
- **nationalityId tekilliği (ADR-003 — §5.14'ü GEÇERSİZ KILAR):** analist kararıyla **kalıcı ve global**:
  `ind.nationality_id` DB UNIQUE (soft-deleted satırlar DAHİL). Pasif müşteri NATID'sini serbest bırakmaz.
  Create tüm satırlara bakar; update yalnız kendi kaydını hariç tutar. Yarış durumunda DB kısıtı
  `DataIntegrityViolationException` → temiz 409 `MSG-CUST-DUP-NATID` (asla 500).
- **Soft delete (FR-CUST-05):** CUST+PARTY_ROLE+PARTY+IND+ADDR+CNTC_MEDIUM tek transaction'da pasife
  çekilir; her satırda `status_id=PASV` + `deleted_date/by` + `updated_date/by` (invariant).
  **Fatura hesabı pasifleştirme UYGULANDI (AC-CUST-05-04, 05.08.2026 — PR #29):** yerel hiçbir
  satıra dokunulmadan ÖNCE account-service'ten müşterinin hesapları listelenir ve **Active**
  olanların her biri pasifleştirilir; erken başarısızlıkta geri alınacak bir şey kalmaz.
  Hesabın hâlâ ürünü varsa 409 `MSG-CUST-HAS-PRODUCTS`, account-service erişilemezse 503 —
  ikisi de silmeyi tamamen reddeder. Erken `checkCustomerHasNoActiveProducts` guard'ı hâlâ
  no-op: aynı ret bir katman derinde, account-service çağrısında yakalanıyor.
- **Role gösterimi:** yanıtlardaki `role` = `ROLE.role_name` ("Customer"); workbook ROLE tablosunda
  code kolonu yok, iç arama `findByRoleName`.
- **Türkçe karakter desteği:** VR-NAME regex'i (1-50, trim-önce) korunuyor; curl'de gövdeyi
  `--data-binary @-` heredoc ile gönder (argv'de native curl bozar — §5.15).
- **Mesaj anahtarları (16.07.2026 kataloğuyla hizalı):** analist kataloğundan
  `MSG-CUST-NATID-VERIFICATION-FAILED` (MERNIS reddi, 400) ve `MSG-MERNIS-UNAVAILABLE` (KPS
  erişilemez, 503) — eski proje-özel `MSG-NATID-VERIFY-FAILED` KALDIRILDI, MERNIS kesintileri artık
  genel `MSG-SERVICE-UNAVAILABLE`'ı kullanmıyor. Dokümante edilmiş proje ekleri:
  `MSG-SERVICE-UNAVAILABLE` (yalnız katalog kesintisi, ADR-002), `MSG-ADDR-LAST-DELETE`,
  `MSG-ADDR-PRIMARY-DELETE`, `MSG-VALIDATION-ERROR`, `MSG-INTERNAL-ERROR`,
  `MSG-FEATURE-NOT-IMPLEMENTED`. `MSG-SEARCH-CRITERIA-REQUIRED` KALDIRILDI (ADR-005).
- **Gateway route'ları:** `/api/customers/**` ve `/api/cities/**` → `lb://customer-service`;
  `/api/lookups/**` → `lb://lookup-service`.
- **Testler (56 test, hepsi geçiyor — 2026-07-16 çalıştırması):** birim (`CustomerBusinessRulesTest`,
  `AddressBusinessRulesTest`, `LookupCatalogServiceTest`, `GlobalExceptionHandlerTest`) + **PostgreSQL
  Testcontainers** entegrasyon (`CustomerServiceIntegrationTest`, 21 test: ADR-005 browse modu —
  kritersiz listede tüm aktifler + soft-deleted hariç + A-Z sıra + tam detay alanları + sayfalama;
  atomik rollback senaryoları, ADR-003 rezervasyonu, kelime-başı arama matrisi, GSM prefix, adres
  invariant'ları, soft-delete metadata, customer_db'de gnl tablosu olmadığının kanıtı, `/search`
  alias'ının yokluğu). Eski zorunlu-kriter testleri kuralla birlikte SİLİNDİ. Lookup/MERNIS istemcileri
  interface seviyesinde mock'lanır — gerçek `LookupCatalogService` doğrulama/cache mantığı testte de
  çalışır. Not: Testcontainers 1.21.3 + Docker/Podman 29 uyumu için surefire `-Dapi.version=1.44`
  pin'li (pom yorumunda gerekçe).
- **Seed fixture genişletmesi (2026-07-31, `V3__expand_customer_test_fixtures.sql`):** 8 yeni aktif
  müşteri (1004-1011) — ortak soyisim, orta-isim araması, GSM prefix gruplaması (0532→3 müşteri,
  0533→2 müşteri), çoklu adres + tek aktif primary invariant'ı, tam dolu iletişim kaydı. Önceden
  uygulanmış V1/V2 DEĞİŞTİRİLMEDİ. Tam fixture kataloğu:
  [`docs/testing/seed-fixture-catalog.md`](docs/testing/seed-fixture-catalog.md). Yeni testler:
  `CustomerServiceIntegrationTest.v3FixtureSearchCoverage` + `.v3FixtureAddressInvariant`.

### 4.5 lookup-service ✅ (YENİ — paylaşılan katalog sahibi, ADR-002)
- Port 8083, DB `lookup_db`. **GNL_ST ve GNL_TP tablolarının tüm sistemdeki TEK sahibi.**
- Kendi Flyway'i workbook'taki TÜM katalog satırlarını **açık (immutable) kontrat ID'leriyle** seed'ler:
  GNL_ST 1=ACTV, 2=PASV (GENERAL) + ORDER/PROD domain'leri; GNL_TP 1=MALE, 2=FEMALE (GENDER),
  3=INDV, 4=ORG (PARTY_TYPE) + diğer domain'ler. **ID'ler asla yeniden numaralanmaz** (ekleme = yeni ID'li
  forward-only migration).
- API: `GET /api/lookups/statuses[?domain=]`, `/statuses/{code}`, `/types[?domain=]`, `/types/{code}`
  (silinmiş katalog satırları dönmez; bilinmeyen kod = 404 `MSG-LOOKUP-NOT-FOUND`).
- Tüketici deseni (customer-service'te `com.crm.customer.lookup`): `LookupCatalogClient` (HTTP) →
  `LookupCatalogService` (domain doğrulama + 15dk/256 kayıt TTL cache + `LookupContract` sabitleriyle
  kontrat-ID assert'i). Controller/repository asla doğrudan katalog çağırmaz. Kod bilinmiyorsa/yanlış
  domain'deyse 400; katalog kapalıysa ve cache'te yoksa **yazma 503 ile fail-closed** — okuma/filtreleme
  etkilenmez (yerel `status_id`). Hardcoded production fallback YOK (sadece testlerde interface mock'u).

### 4.6 mernis-stub ✅ (YENİ — KR-10 fake MERNİS/KPS)
- Port 8084, DB'siz. `POST /api/mernis/verify` `{nationalityId, firstName, lastName, birthDate}` →
  `{verified: bool}`. Deterministik: 11 haneli geçerli her ID doğrulanır, **deny-list hariç**
  (varsayılan `99999999999` — yerel "reddedildi" fikstürü; `config-repo/mernis-stub.yml`).
  Gerçek kişisel veri kullanılmaz. customer-service `MernisClient` ile erişir; doğrulama reddi veya
  erişilemezlik = müşteri oluşturulmaz (fail-closed).
- **⚠️ Proje çapında önemli düzeltme:** Bu servisi yazarken **Lombok'un hiç çalışmadığı** ortaya çıktı —
  JDK 25 + bu Maven Compiler Plugin sürümü, `annotationProcessorPaths` açıkça tanımlanmadıkça artık
  `-classpath`'teki processor'leri (Lombok dahil) otomatik keşfetmiyor. `customer-service/pom.xml`'e bu
  yapılandırma eklenerek düzeltildi (bkz. §5.9). (Buradaki eski "auth-service'e de taşınmalı" uyarısı
  tarihseldir — auth-service 2026-07-17'de silindi; kural yeni Lombok kullanan HER modül için geçerli.)

### 4.7 API Dokümantasyonu (Swagger/OpenAPI) ✅ (YENİ — ADR-012)
- **Tek, birleşik Swagger UI — sadece gateway'de.** `customer-service` ve `lookup-service`
  `springdoc-openapi-starter-webmvc-ui` ile kendi `@RestController`'larından `/v3/api-docs` JSON'unu
  üretir ama `springdoc.swagger-ui.enabled: false` (config-repo) — kendi UI'larını sunmazlar.
  `api-gateway` aynı starter'ı UI açık şekilde taşır; `springdoc.swagger-ui.urls` iki servisi
  dropdown'da listeler; gerçek sayfa **`http://localhost:8080/swagger-ui.html`**.
- **Neden gateway-proxy, neden servislere doğrudan host portu açmadık:** ADR-009 "no published host
  port" kuralı — tüm client trafiği BFF'den girer. Alternatif (her servisin kendi Swagger UI'ını
  8082/8083'te host etmesi + springdoc'un kendi OAuth2 Authorization Code+PKCE akışıyla Keycloak'tan
  doğrudan Bearer token alması) crm-security-starter'ın asıl zero-trust modelini daha sadık test
  ederdi, ama host port açmayı ve iki farklı auth mekanizmasını (BFF session vs. doğrudan Bearer)
  aynı anda idame ettirmeyi gerektirirdi. Gateway-proxy seçildi çünkü: (1) controller path'leri
  zaten `/api/customers`, `/api/lookups` ile mevcut gateway route'larıyla birebir örtüşüyor — "Try
  it out" ekstra hiçbir auth kodu yazmadan mevcut BFF session cookie'sini kullanıyor; (2) yeni host
  portu YOK, ADR-009 hiç esnetilmedi; (3) tek sayfa, tek auth modeli — geliştirici zihinsel yükü az.
- **Gateway route'ları** (`api-gateway.yml`, mevcut `spring.cloud.gateway.server.webmvc.routes`
  kalıbıyla aynı şekilde): `customer-service-docs` ve `lookup-service-docs`, `/v3/api-docs/{servis}`
  path'ini `RewritePath` ile downstream servisin `/v3/api-docs`'una çevirir. `TokenRelay` YOK — docs
  JSON'u iş verisi değil, şema metadata'sı, her iki tarafta da anonim.
- **Güvenlik izinleri:** her iki resource-server'da `crm.security.permit-paths` (starter'ın
  extension point'i, kod değişikliği gerekmez) `/v3/api-docs/**`'i ekliyor; `api-gateway`'in kendi
  `SecurityConfig.java`'sında `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**` `permitAll()`.
  Docs sayfası login'siz görülebilir; "Try it out"'un asıl `/api/**` çağrısı hâlâ `ROLE_crm-user`
  gerektirir (mevcut kural değişmedi) — kullanıcı ayrı bir sekmede normal login akışını tamamlamış
  olmalı, aynı origin olduğu için session cookie'si otomatik gider.
- **Bilinen sınır:** Swagger UI'ın varsayılan `requestInterceptor`'ı CSRF header'ı (`X-XSRF-TOKEN`)
  otomatik eklemiyor — GET'ler sorunsuz, mutating (POST/PUT/DELETE/PATCH) "Try it out" çağrıları için
  şimdilik kullanıcı `XSRF-TOKEN` cookie değerini elle header olarak eklemeli. Otomatikleştirmek
  (custom JS interceptor) ayrı, küçük bir takip işi (bkz. §9).
- **springdoc versiyonu:** root `pom.xml`'de `springdoc-openapi.version=2.8.6` (Maven Central'daki
  en güncel sürüm; Spring Boot 4.1.0/Spring Framework 7 ile springdoc'un 3.x hattı henüz
  yayınlanmadığı için 2.x kullanıldı — derleme/test bunu doğruladı, uyumsuzluk çıkarsa ilk şüpheli yer burası).
- **"Try it out" URL çözümü — bir deneme çöp oldu, kayıt altında:** İlk kurulumda "Try it out"
  isteklerinin gittiği adres, `customer-service`/`lookup-service`'in **kendi container hostname'i**
  oluyordu (`http://<container-id>:8082/...`) — tarayıcıdan erişilemez. İlk deneme
  `server.forward-headers-strategy: framework` (config-repo) + gateway route'larına
  `AddRequestHeader=X-Forwarded-Host, localhost:8080` eklemekti; springdoc bu header'ları
  güvenilir şekilde yansıtmadı, **işe yaramadı**. **Gerçek çözüm:** her iki serviste
  `common/config/OpenApiConfig.java` — bir `OpenApiCustomizer` bean'i, üretilen OpenAPI
  dokümanının `servers` alanını doğrudan **relatif `"/"`** olarak zorluyor. Bu sayede Swagger UI
  API çağrılarını her zaman kendi sayfasının origin'ine (`http://localhost:8080`) göre yapıyor,
  forwarded-header davranışına hiç bağımlı değil. `forward-headers-strategy: framework` config'te
  kaldı (zararsız) ama bu sorunu asıl çözen o DEĞİL — yeni bir servis eklerken ona güvenme, aşağıdaki
  `OpenApiConfig` adımını uygula. Uçtan uca doğrulandı: login → GET liste/detay/adres → CSRF'li
  PATCH (200) → CSRF header'sız aynı istek (403 `MSG-AUTH-CSRF-REJECTED`) → docs anonim erişim (200)
  → `mkaya` (disabled) login reddi — hepsi gerçek gateway/Keycloak'a karşı test edildi (2026-07-20).
- **Yeni bir servis eklendiğinde Swagger'da görünmesi için (OTOMATİK DEĞİL — 4 adım):**
  1. Yeni servisin `pom.xml`'ine `springdoc-openapi-starter-webmvc-ui` bağımlılığı ekle (versiyon
     root `pom.xml`'in `dependencyManagement`'ından geliyor, ayrı versiyon yazma).
  2. Yeni servisin config-repo YAML'ına: `springdoc.swagger-ui.enabled: false` (kendi UI'ını
     sunmasın) + `crm.security.permit-paths` listesine `/v3/api-docs/**` ekle (starter'ın
     `/actuator/health` varsayılanını da tekrar yazmayı unutma — liste extend değil override eder).
  3. `common/config/OpenApiConfig.java`'yı **birebir kopyala** (bkz. `customer-service` veya
     `lookup-service`'teki dosya) — sadece paket adını değiştir. `forward-headers-strategy` EKLEMENE
     gerek yok, gerçek çözüm bu bean.
  4. `api-gateway.yml`'e: yeni bir `{servis}-docs` route'u (`customer-service-docs` ile birebir aynı
     kalıp — `RewritePath=/v3/api-docs/{servis}, /v3/api-docs`) + `springdoc.swagger-ui.urls`
     listesine bir satır. `api-gateway`'in `SecurityConfig.java`'sına DOKUNMANA gerek yok —
     `/v3/api-docs/**` zaten prefix olarak permitAll.
  Tüm bunlardan sonra config-server'ı (yeni route/permit-path onun classpath'inde) VE ilgili yeni
  servisi yeniden build+deploy etmeyi unutma.

---

### 4.8 account-service ✅ (YENİ — 2026-07-23, ADR-013/014)
- Port 8085, DB `account_db`, paket kökü `com.crm.account`. Kapsam: **FR-ACCT-01..04 + KR-11**
  (scope'un kaynağı FR v8-1 Final, 23.07.2026 — FR v8-2, 03.08.2026 revizyonuyla gözden
  geçirildi, davranışsal fark yok). Compose'da host portu YAYINLANMAZ (ADR-009); gateway route'u
  `/api/accounts/**` (TokenRelay + cookie stripping); birleşik Swagger dropdown'ında kayıtlı.
- **Tablolar (Flyway V1/V2):** `acct_tp` (YEREL hesap-tipi kataloğu, kontrat: 1=223 Customer
  Account, 2=224 Billing Account — GNL kataloğu DEĞİL), `cust_acct` (`customer_number` = dış
  iş numarası, `address_id` = dış adres referansı — FK'sız; `account_number VARCHAR(10) UNIQUE`),
  `cust_acct_prod_invl` (yerel ürün-ilişki projeksiyonu), `acct_number_seq` (KR-11 durum
  tablosu; `next_value` = SIRADAKİ değer — seed sonrası (1,2026)=100004). Workbook sapmaları
  kayıtlı (ADR-013/014 + document-delta): legacy numaralar KR-11'e göre YENİDEN üretildi
  (`1261000002`/`1261000010`/`1261000028`/`1261000036`), `customer_id` yerine
  `customer_number`, K-8 223 adı sabit "Customer Account".
- **KR-11 üretimi (ADR-014):** enjekte edilebilir `Clock` + tek atomik
  `INSERT ... ON CONFLICT ... DO UPDATE ... RETURNING next_value - 1` upsert'i (yarış-güvenli,
  gapless, ilk değer TAM 100000) + Luhn check-digit (kanonik örnek `126100000`→`1261000002`).
  Taşma → 409 `MSG-ACCT-NUMBER-CAPACITY-EXCEEDED`; UNIQUE yarışı → 409 `MSG-ACCT-DUP-NUMBER`
  (asla 500).
- **API (tam liste — başka endpoint YOK):** `GET /api/accounts?customerId=` (yalnız 224,
  Active+Passive, Active→Passive + numara ASC, sayfalama yok, boşsa `[]`),
  `POST /api/accounts` (tip zorla 224; adres customer-service'in aktif adres listesinden
  doğrulanır; 201), `GET/PUT/DELETE /api/accounts/{accountNumber}`. PUT yalnız
  accountName+addressId; fazla/immutable alan → 400 `MSG-ACCT-IMMUTABLE-FIELD` (sessizce
  yutulmaz). DELETE = pasifleştirme (204; satır listede Passive olarak KALIR); aktif ürün →
  409 `MSG-ACCT-HAS-PRODUCTS`; Passive'e PUT/tekrar DELETE → 409 `MSG-ACCT-NOT-ACTIVE`.
  İç ID'ler asla dışarı sızmaz; yanıt alanları: accountNumber/accountName/accountTypeCode/
  accountTypeName/billingAddressId/accountStatus.
- **K-8 (analist onaylı):** müşterinin İLK 224'ü yaratılırken 223 yoksa AYNI transaction'da
  yaratılır (aynı KR-11 sequence'ı, sabit ad, birincil adres; partial unique index
  `ux_cust_acct_single_223` müşteri başına tek non-deleted 223 garantiler). 223 hiçbir API
  yanıtında görünmez (liste 224-filtreli; numarası 404).
- **Dış bağımlılıklar:** lookup-service (ACTV/PASV çözümü — yazmalar 503 ile fail-closed) +
  customer-service (müşteri varlık/adres doğrulaması — `GET /api/customers/{n}` +
  `/addresses`, kullanıcı token'ı `BearerTokenPropagationInterceptor` ile taşınır, ADR-010
  eki; Eureka üzerinden DOĞRUDAN, gateway'den DEĞİL). Okumalar tamamen yerel.
- **`cust_acct_prod_invl` tek-yazarlı:** yalnız account-service yazar; şimdilik yalnız
  seed/test verisiyle dolu ama SORGULANAN gerçek guard state. Gelecek product/order
  servisleri account-service komut API'si veya event'i üzerinden besleyecek —
  `account_db`'ye doğrudan yazım YASAK (bilinçli, dokümante TODO — ADR-013 §5).
- **Testler (41 test, hepsi geçiyor):** birim (`LuhnCheckDigitTest`, `AccountNumberFormatTest`,
  `AccountBusinessRulesTest`, `OutboundBearerPropagationTest` — iki outbound istemcinin de
  token taşıdığı kanıtlı) + `AccountServiceIntegrationTest` (21 IT: şema/seed doğrulaması,
  sequence ilk-değer/izolasyon/taşma/8-thread eşzamanlılık, K-8 atomikliği — 224 tahsisi
  patlarsa 223 de geri alınır, numara asla yeniden kullanılmaz, fail-closed 503'ler,
  soft-delete + sıralama, zero-trust 401/403, audit = JWT sub, iç ID sızıntısı yok).
  Sabit `Clock` (2026) ile deterministik numaralar.
- **Yeni modül tuzak notları uygulandı:** `spring-boot-flyway` açık bağımlılık (§5.11),
  Lombok `annotationProcessorPaths` (§5.9), `OpenApiConfig` relative-server bean'i (§4.7
  4-adım listesi), Testcontainers pinleri artık KÖK POM'dan (modül-yerel pin YOK).
- **Seed fixture genişletmesi (2026-07-31, `V4__expand_account_test_fixtures.sql`):**
  14 yeni hesap (K-8 223 + Billing Account'lar) — 6 yeni müşteri için (1004-1008, 1011),
  biri **çoklu Billing Account** (1004: 2 adet), ikisi **ürünsüz Billing Account**
  (1007'nin 3. hesabı, 1011'in hesabı). Hesap numaraları V2 seed'in KR-11
  sequence'ini (`acct_number_seq(1,2026)`) 100004'ten 100017'ye kadar **kesintisiz**
  devam ettiriyor; `next_value` artık **100018** (`GREATEST` ile, asla azaltılmadan).
  Her numara production `AccountNumberGenerator.format`/Luhn algoritmasından
  türetildi ve doğrulandı — bkz. yeni test
  `AccountNumberFormatTest.v4FixtureAccountNumbersAreValidAndDistinct`. Mevcut V1/V2/V3
  DEĞİŞTİRİLMEDİ. Tam fixture kataloğu:
  [`docs/testing/seed-fixture-catalog.md`](docs/testing/seed-fixture-catalog.md).
  `AccountServiceIntegrationTest`'e 2 yeni IT eklendi (çoklu-BA müşteri, ürünsüz/
  çoklu-aile product-ids); mevcut sequence-bağımlı 3 assertion (next_value=100004,
  1261000051/1261000069 beklentileri) yeni değerlere güncellendi (100018,
  1261000184/1261000192/1261000200) — ayrıca @Order(20) atomiklik testi artık
  1005 yerine hesapsız müşteri 1009'u kullanıyor (1005 artık V4 fixture'larına sahip).

### 4.9 product-service ✅ (YENİ — 2026-07-29, salt-okunur FR-PROD-01..02 dilimi)
- Port 8086, DB `product_db`, paket kökü `com.crm.product`. Kapsam: **FR §2.6
  (FR-PROD-01, FR-PROD-02)** + salt-okunur teklif/kampanya kataloğu. Compose'da host
  portu YAYINLANMAZ (ADR-009); gateway route'ları `/api/products/**`, `/api/offers/**`,
  `/api/campaigns/**` (TokenRelay + cookie stripping); birleşik Swagger dropdown'ında
  kayıtlı (ADR-012 4-adım listesi uygulandı).
- **Tablolar (Flyway V1/V2 — 10 workbook tablosu):** `prod_spec`, `prod_ofr`, `cmpg`,
  `cmpg_prod_ofr`, `prod`, `prod_spec_char`, `prod_spec_char_use`, `prod_char_val`,
  `prod_catal`, `prod_catal_prod_ofr`. `status_id` + `prod_spec.service_type_id` +
  `prod_catal.catalog_type_id` merkezi GNL_ST/GNL_TP dış referansları — **lokal
  gnl tablosu/seed'i ve cross-database FK YOK** (ADR-002, testle kanıtlı).
  `prod.service_address_id` → `customer_db.addr` dış referansı (FK'sız);
  `parent_prod_id`/`parent_offer_id` lokal self-FK.
- **⚠️ En kritik sınır (ADR-013 §5):** `PROD`'da **hesap/müşteri kolonu YOKTUR** —
  ürün↔fatura hesabı bağı yalnız `account_db.cust_acct_prod_invl`'dedir ve o tabloyu
  yalnız account-service yazar. Bu yüzden `GET /api/products?accountNumber=` bir
  **kompozisyondur**: product-service account-service'in YENİ
  `GET /api/accounts/{accountNumber}/product-ids` ucunu çağırır (Eureka `lb://` ile
  DOĞRUDAN, gateway'den DEĞİL — ADR-010; kullanıcı token'ı taşınır), dönen id'lerle
  `product_db`'de join yapar. product-service `account_db`'ye ne yazar ne okur
  (`information_schema` testiyle de kanıtlı).
- **API (tam liste — başka endpoint YOK):**
  `GET /api/products?accountNumber=` → `[ProductRowResponse]` (`productId`,
  `productName`, `campaignName`, `campaignId`, `productStatus`; **sayfalama YOK** —
  FR-PROD-01 kural içermiyor; ürün yoksa `200 []` ve `MSG-PROD-NONE` metnini
  frontend gösterir; bilinmeyen/223 hesap → `404 MSG-ACCT-NOT-FOUND`),
  `GET /api/products/{id}` → `ProductDetailResponse` (5 AC-PROD-02-01 alanı; yoksa
  `404 MSG-PROD-NOT-FOUND` — **belgeli proje eklentisi**, analist katalogunda yok),
  `GET /api/offers`, `GET /api/campaigns`. İç anahtarlar sızmaz; API'de `Action`
  alanı yok (UI konusu, AC-PROD-01-04).
- **Türetilen değerler (asla saklanmaz):** servis tipi `PROD_SPEC.service_type_id` →
  GNL_TP `10=INTERNET/11=RESOURCE/12=ACTIVATION` (teklifin kendi servis-tipi kolonu
  yok); ürün statüsü `PROD.status_id` → `"Active"`/`"Passive"` (**pasif ürün listede
  KALIR** — AC-PROD-01-03); kampanya toplam fiyatı üye tekliflerin toplamı (`CMPG`'ye
  fiyat kolonu eklenmedi); kampanyanın public kimliği `cmpg.campaign_code`
  (`CMP-ADSL-01`), iç `cmpg.id` asla dışa çıkmaz; kampanyasız üründe
  `campaignName`/`campaignId` **`null`** (`"-"` gösterimi frontend'in işi).
- **Service Address (FR-PROD-02):** `prod.service_address_id` yalnız ana üründe dolu →
  **çocuk ürün ebeveyninin adresini gösterir** (`ProductBusinessRules`, ebeveyn
  zincirini yukarı yürür). Adres customer-service'in YENİ dahili
  `GET /api/addresses/{addressId}` ucuyla çözülür (gateway'e AÇILMADI; kullanıcı
  token'ı taşınır). Silinmiş adres → blok `null` (detay yine döner);
  customer-service **erişilemez** → 503 (fail closed).
- **Lookup HTTP client YOK (bilinçli):** Faz A tamamen salt-okunur, canlı katalog
  çözümü gerektiren hiçbir yazma yok; `isActive()` yerel `LookupContract` sabitlerini
  kullanır. Bir sonraki yazma dilimi, statü persist etmeden ÖNCE tam
  `LookupCatalogClient` sınırını kurmak zorunda.
- **Belgeli sapmalar (workbook DÜZENLENMEDİ — `document-delta.md` P1..P4):**
  (1) teklif fiyatları uydurulmuş fixture'dır (299/149/49) çünkü workbook kolonu boş
  ama AC-SALE-01-12 tutar istiyor — **analist onayı bekliyor**; (2) `PROD` 1 ve 2'ye
  `campaign_id=1` verildi (kampanyalı dal test edilebilsin); (3) yeni PASV ürün satırı
  (`ADSL 8MB Legacy`) eklendi (pasif dal test edilebilsin); (4) ürün 3 ve 4'ün
  involvement satırları **account-service'in `V3__seed_activation_involvement.sql`**
  migration'ında (o satırlar `account_db`'ye ait). Hesap `1261000028`/`1261000036`
  bilerek ürünsüz — `MSG-PROD-NONE` fixture'ları.
- **Karakteristik modeli** (`prod_spec_char`, `prod_spec_char_use`, `prod_char_val` —
  tek `val` string kolonu, `data_type ∈ {NUMBER,BOOLEAN,TEXT,DATE}`) şema+seed olarak
  var ama **Faz A'da endpoint'i YOK**: §2.7 Product Configuration ekranları tüketecek.
- **Diğer servislerdeki eklemeler (aynı PR):** account-service'e
  `GET /api/accounts/{n}/product-ids` (involvement projeksiyonunun tek public okuma
  noktası; `deleted_date IS NULL`, **involvement statüsüne göre filtrelenmez** —
  ACTV-only filtre yalnız AC-ACCT-04-03 silme guard'ına ait, o mantığa dokunulmadı) +
  `V3` seed; customer-service'e dahili `GET /api/addresses/{addressId}`.
  customer-service'in 501/no-op TODO'ları (accountNumber arama, aktif ürün guard'ı,
  `MSG-ADDR-IN-USE`) **açılmadı** — ayrı takip PR'ı.
- **Testler:** `ProductServiceIntegrationTest` (13 IT — Testcontainers `postgres:16`,
  gerçek HTTP + gerçek starter güvenlik zinciri; `AccountServiceClient` ve
  `CustomerServiceClient` yalnız arayüz seviyesinde mock, gerçek servis/mapper/rules
  mantığı çalışır): şema/kolon kanıtları (gnl yok, `PROD`'da hesap kolonu yok),
  kampanyalı dal + pasif ürün + kampanyasız `null` dalı, çocuk ürünün ebeveyn
  adresi, 404/400 kontratları, iki upstream için 503 fail-closed, 401/403 zero-trust,
  katalog türetimleri (serviceType + türetilmiş 497.00). account-service tarafında
  `product-ids` için 2 yeni IT (43/43 yeşil, AC-ACCT-04-03 guard'ı hâlâ 409),
  customer-service tarafında dahili adres ucu için 1 yeni IT (80/80 yeşil).
- **ADR borcu (açıkça kayıtlı):** **ADR-015 (product boundary)** ve **ADR-013'e
  eklenecek "read-side" fıkrası** yazılmadı; `service-boundaries.md`'nin
  "analyst/architecture sign-off eksik" uyarısı product domain'i için geçerli.
- **Seed fixture genişletmesi (2026-07-31, `V3__expand_product_test_fixtures.sql`):**
  Fiber Internet ailesi (mevcut onaylı SERVICE_TYPE 10/11/12'yi yeniden kullanır —
  yeni paylaşılan lookup tipi YOK) + 2 yeni ADSL teklifi (toplam 10 aktif teklif),
  3 yeni aktif kampanya (`CMP-FIBER-01/02`, `CMP-ADSL-02` — toplam 4, hepsi gelecek
  `activation_end_date`'li; süresi geçmiş/pasif kampanya fixture'ı EKLENMEDİ), 13 yeni
  ürün örneği (id 5-17, toplam 17). Mevcut V1/V2 (pasif "ADSL 8MB Legacy" fixture'ı
  dahil) DEĞİŞTİRİLMEDİ. IPTV ailesi değerlendirildi ve **eklenmedi** — yeni bir
  paylaşılan GNL_TP satırı + `LookupContract.serviceTypeCode()` kod değişikliği
  gerektirir, analist onayı olmadan yapılmadı. Tam fixture kataloğu:
  [`docs/testing/seed-fixture-catalog.md`](docs/testing/seed-fixture-catalog.md).
  Fiyatlar proje-eklentisi geliştirme fixture'ıdır, ticari tarife verisi DEĞİLDİR
  (`document-delta.md` P5-P9). Yeni testler: `ProductServiceIntegrationTest`'e 3 IT
  (çoklu-aile hesap, ürünsüz hesap, çocuk ürünün non-primary ebeveyn adresi) +
  yeni birim test sınıfı `ProductBusinessRulesTest` (çok seviyeli ebeveyn zinciri).
- **2026-08-06 idempotency eki (ADR-015 §9):** `POST /api/products` artık zorunlu
  `saleOperationId` ile replay-safe (yeni `sale_operation` tablosu, V5, UNIQUE
  kısıt); `prod.sale_operation_id` her ürünü yaratan operasyona etiketler. Eski
  belirsiz `/cancel(productIds)` KALDIRILDI, yerine sale-scoped, idempotent
  `/compensate({saleOperationId, productIds})` geldi. Detay: dosyanın başındaki
  "Son güncelleme" girdisi ve `docs/api/product-service.md` §Idempotency addendum.


### 4.10 order-service ✅ + product/account yazma dilimleri (YENİ — 2026-08-02, ADR-015/016)
- Port 8087, DB `order_db`, paket kökü `com.crm.order`. Kapsam: **FR-SALE-01/02
  (§2.7 Ürün Satışı)**. Compose'da host portu YAYINLANMAZ (ADR-009); gateway route'u
  `/api/orders/**`; birleşik Swagger dropdown'ında kayıtlı (ADR-012 4-adım listesi).
- **Tablolar (Flyway V1/V2):** `bsn_inter`, `cust_ord`, `cust_ord_item`,
  `order_number_seq`. Lokal `gnl_*` tablosu ve cross-database FK YOK (ADR-002,
  testle kanıtlı). `customer_number`, `customer_account_number`, `product_offer_id`,
  `product_id` FK'sız dış referanslar.
- **Uçlar (ADR-018, 10.08.2026 sonrası):** CANLI asenkron sözleşme
  `POST /api/orders/drafts` → `POST /api/orders/{n}/submit` (**202**) →
  `GET /api/orders/{n}/status`, artı `DELETE /api/orders/{n}/draft` (yalnız WAIT,
  idempotent). `POST /api/orders` **DEPRECATED** ama build'de: mevcut frontend + geri
  dönüş yolu. `GET /api/orders/{orderNumber}` değişmedi. **Sepet tablosu YOK, sipariş-iptal
  ucu YOK (KR-7 kapsam dışı), sipariş listesi YOK** (hiçbir FR istemiyor) — taslak SEPETİ
  değil SİPARİŞİ erken yaratıyor.
- **KR-12 sipariş numarası (analist gereksinimini karşılayan proje teknik tercihi —
  10.08.2026'da netleşti, artık "onay bekleyen" DEĞİL; taslak yaratılırken tahsis edilir,
  bu yüzden Submit'ten önce gösterilebilir):**
  `[T][YY][SSSSSS][C]` — KR-11'in şeklini birebir koruyor, Luhn, `order_number_seq`
  ADR-014 desenine paralel. Üreteç account-service'ten **kopyalandı** (paylaşılan
  iş-mantığı kütüphanesi yok; iki kopya aynı test vektörleriyle sabitlendi).
  ⚠️ **Sipariş ve hesap numaraları aynı değer uzayında** — seed sipariş numarası
  `1261000002`, hesap `1261000002` ile aynı. Kabul edildi (ayrı DB/servis, ortak
  namespace yok; KR-02 iki ayrı arama alanıyla ayırıyor); analist notu (ADR-016 §8.1).
- **Orkestrasyon (ADR-016 §5) — üç DB, dağıtık transaction YOK.**
  ⚠️ **CANLI YOL OLARAK SUPERSEDE EDİLDİ (ADR-018, 10.08.2026):** aşağıdaki senkron
  sıralama artık deprecated `POST /api/orders`'ın davranışıdır. Canlı satış, order-service'in
  sahibi olduğu kalıcı saga ile yürüyor ve **link → activate** sırasını kullanıyor (aşağıdaki
  (4)/(5) adımlarının TERSİ), çünkü hiçbir hesap talep etmiyorken bir ürün commit edilmemeli.
  §4.10'un geri kalanı (adım 3'ün gerekçesi, product-service yazma dilimi, involvement
  komutu, iki kusur) her iki yol için de aynen geçerli.
  (0) hesap ön koşulu (var mı/224 mü/**Active** mi — AC-SALE-02-01) →
  (1) `order_db`'ye tek yerel transaction, MIDLWARE →
  (2) product-service'te ürünler **PNDG** →
  (3) `product_id` + tutar snapshot'ları yerel olarak doldurulur →
  (4) ürünler ACTV'ye yükseltilir →
  (5) **account-service involvement komutu = COMMIT POINT.**
  (5)'ten önceki her hata: ürünler atılır (hâlâ PNDG — product-service'in PNDG-only
  guard'ı hiç ihlal edilmiyor), sipariş **CANCELLED** olur, kullanıcıya hata döner.
  (5)'ten sonra adım yok → geri alınacak şey yok → **involvement-delete komutu
  YAZILMADI** (ADR-013 §8.6). Numara asla serbest bırakılmaz.
- **Adım 3 neden var:** workbook'un `CUST_ORD_ITEM`'ı `product_id` taşıyor ama başlık
  yazılırken ürün henüz yok. Kolon nullable yapıldı ve ikinci bir yerel
  transaction'da dolduruluyor — ürünleri önce yaratmak, "ilk yazma tek başına
  güvenli bir yerel transaction" özelliğini yok ederdi (ADR-016 §5.2).
- **product-service yazma dilimi (ADR-015):** ilk yazma kodundan ÖNCE tam
  `LookupCatalogClient` sınırı kuruldu (§9.1b'nin uyarısı — ADR-002 fail-closed).
  `POST /api/products` (PNDG, ana ürün INTERNET servis tipinden **türetilir**,
  servis adresi yalnız ana üründe), `/confirm`, `/cancel` (**yalnız PNDG** — aksi
  KR-7'nin kapsam dışı bıraktığı ürün iptali olurdu), `GET /api/offers/{id}/characteristics`.
  Sepet (AC-SALE-01-05/08) ve karakteristik (AC-SALE-01-18/19) doğrulaması burada:
  bunlar `PROD_OFR`/`PROD_SPEC` soruları ve **persist noktasında** doğrulanınca
  atlanamaz hale geliyor. **Servis adresi sahipliği de doğrulanıyor** (AC-SALE-01-11,
  ADR-015 §5.9): `customerNumber` istekte zorunlu ama **saklanmıyor** — yalnız
  adresin o müşteriye ait olduğunu customer-service'in aktif adres listesinden
  kontrol etmek için. Varlık kontrolü yetmezdi: `prod.service_address_id` FR-PROD-02
  modalinde gösteriliyor, doğrulanmamış bir id **başka bir müşterinin adresini**
  ürüne bağlayıp geri gösterirdi. `customerNumber` order-service tarafından
  **hesaptan** okunuyor, istemciden alınmıyor — alınsaydı kontrol hiçbir şey
  doğrulamazdı. **PNDG ürünler FR-PROD-01/02'de görünmez** — mevcut mapper
  ACTV olmayan her şeyi "Passive" render ediyor, sızan satır müşteriye hiç almadığı
  pasif ürün gösterirdi.
- **account-service involvement komutu (ADR-013 §8):**
  `POST /api/accounts/{n}/product-involvements` — toplu, **(hesap, ürün) bazında
  idempotent** (çağıran telafi eden bir orkestratör, retry edebilir), yalnız Active
  224 (Passive → 409 `MSG-ACCT-NOT-ACTIVE`), `short_code = 'ACCT_PROD'` (workbook
  tablo sabiti — kampanya/teklif kodu DEĞİL, V1 default + tüm seed satırlarıyla
  doğrulandı). Ayrıca `AccountResponse`'a `customerNumber` eklendi (ADR-013 §3.6).
- **⚠️ Uygulama sırasında bulunan iki kusur (ADR-016'da kayıtlı):**
  1. **`@Transactional` hiç çalışmıyordu** — yerel yazmalar `OrderServiceImpl`'in
     `protected` metotlarıydı ve `this.` ile çağrılıyordu; Spring'in proxy tabanlı
     AOP'sinde self-invocation proxy'yi atlar. `OrderPersistence` bean'ine taşındı.
  2. **httpclient5 503'te otomatik retry yapıyor** (runtime classpath'te, Eureka
     üzerinden transitif) → tek POST iki sipariş + iki ürün seti yaratıyordu.
     `POST /api/products` idempotent DEĞİL: retry ikinci bir PNDG seti yaratıp
     ilkini öksüz bırakırdı. Üç giden client'ta da `disableAutomaticRetries()`
     (ADR-016 §5.3b). Retry orkestrasyonun kararıdır, transport'un değil.
- **Katalog satırları (10.08.2026 itibarıyla güncel):** GNL_ST `WAIT`(3) **artık
  yazılıyor** — Submit öncesi taslağın statüsü (analist kararı A1, ADR-018 §1.1); bu satır
  eskiden "hiç yazılmaz" diye kayıtlıydı ve o kayıt SUPERSEDE edildi. `MIDLWARE`(4)
  Submit'ten itibaren; `CANCELLED`(5) yalnız sistem tarafından — telafi, terk edilmiş
  taslak veya süresi geçmiş taslak; **kullanıcının submit edilmiş bir siparişi iptal
  etmesi HÂLÂ YOK** (KR-7 kapsam dışı). GNL_TP `TRANSFER`(8)/`CANCEL`(9) **hâlâ hiç
  yazılmaz** — Transfer/Servis Adresi Değişikliği analist kararıyla kapsam dışı (A6) ve bu
  satırlar yeni işlevsellik için gerekçe DEĞİL. `bsn_inter_type_id` kolonu var ve
  NEWSALE(7) yazılıyor — gelecek bir akış migration gerektirmesin diye.
- **Testler (ADR-018 sonrası order-service toplamı 66/66):** yeni
  `AsyncSaleFlowIntegrationTest` (**22 IT** — taslak yaşam döngüsü, Submit'in tek-commit
  atomikliği, 202'nin beklememesi, kopya/sırasız cevap, her hata dalı, iki telafi sırası,
  başarısız telafinin `MANUAL_INTERVENTION`'a yükselmesi, güvensiz hata anahtarının
  değiştirilmesi, takılı saga'nın yeniden yayınlanması, bütçe bitişi, terminal saga'nın
  ASLA yeniden yayınlanmaması, legacy ile async'in aynı satışı işleyememesi) +
  `OrderServiceIntegrationTest` (19 IT — şema/seed, happy path, ön
  koşullar, gövde doğrulama, **her adımın telafi yolu**, CANCELLED siparişin
  numarasını koruması ve görünür kalması, fail-closed, gapless sequence) +
  Outbox IT'leri (10) + `OrderNumberFormatTest` (6) + `LuhnCheckDigitTest` (4) + mimari/
  resilience guard'ları (5) = **66/66**. product-service **54/54** (yeni
  `SaleCommandHandlerTest` 7 — Spring context'siz, Docker'sız), account-service **61/61**
  (yeni `SaleCommandHandlerTest` 8). Üç dış client da yalnız **arayüz** seviyesinde
  mock'lanır — "adım 4 patlarsa" senaryosu canlı bir stack'ten istenemezdi; saga
  cevapları da broker olmadan doğrudan `InboxDispatcher`'a besleniyor, çünkü kopya /
  sırasız / başarısız-telafi senaryolarını gerçek bir broker'dan istemek dakikalar
  sürerdi ve determinizmi olmazdı.
- **Yeni fixture:** product-service `V4__seed_passive_offer_fixture.sql` — tek pasif
  teklif (id 11). `MSG-SALE-OFFER-INACTIVE` dalının verisi yoktu; V2'nin pasif *ürün*
  fixture'ıyla (document-delta P3) aynı gerekçe. `GET /api/offers` ACTV filtreli
  olduğu için mevcut hiçbir kontrat değişmedi. **document-delta P10.**
- **Analist onayı bekleyenler:** KR-12 (ADR-016 §4), teklif fiyatları (P1/P5),
  AC-SALE-01-12'nin "Order ID submit'ten önce" ifadesi (ADR-016 §8.3), transfer/
  service-address-change akışları (§8.2), PNDG'nin anlamı (ADR-015 §8.3).
- **2026-08-06 idempotency eki (ADR-016 §10):** `POST /api/orders` artık zorunlu
  `Idempotency-Key`; `idempotency_key` tablosu (V3) + `IdempotencyKeyFilter`.
  Detay: dosyanın başındaki "Son güncelleme" girdisi ve `docs/api/order-service.md`
  §Idempotency.

### 4.11 crm-messaging-starter — eventing temeli ✅ (YENİ — 2026-08-07, ADR-017)

**Bu bölümün tek cümlelik özeti: altyapı hazır, SALE akışı kesilmedi.**
`POST /api/orders` bit-bit aynı davranıyor; ADR-016 §5'in senkron orkestrasyonu tek canlı
yol. Aşağıdaki her şey `false` ile geliyor.

**Neden var.** ADR-016 §5 dayanıklılık boşluğunu dürüstçe kayda geçirmişti: adım 5
(account involvement, ADR-013 §8) patlarsa satış "best-effort" telafiyle geri alınıyor, ve
telafi de patlarsa ADR-015 §8.4 kalıntıyı operasyonel takip olarak kaydediyor. Bu bir
tasarım özelliği değil, bir boşluk. Observability eki `MdcKeys`'te `sagaId`/`eventId`'yi
tam da bunun için REZERVE etmiş, resilience eki de order→product/account yazma sınırına
bilerek HİÇBİR circuit breaker/retry eklememişti (`NoResilienceOnSaleWriteClientsTest`
yokluğu kanıtlıyor) — çünkü o sınır yamanmak yerine buraya taşınacaktı.

**Modül.** `backend/crm-messaging-starter` (kütüphane; `spring-boot-maven-plugin`
repackage YOK, diğer iki starter gibi):

| Paket | İçerik | Broker görebilir mi? |
|---|---|---|
| `com.crm.messaging.contract` | `EventEnvelope`, `EnvelopeCodec`, `Destinations`, `MessageTypes` | **hayır** |
| `com.crm.messaging.outbox` | entity, repo, `OutboxRecorder`, `OutboxRelay`, retention, metrikler, `OutboxPublisher` **portu** | **hayır** |
| `com.crm.messaging.inbox` | entity, repo, `InboxGuard`, `InboxDispatcher`, `MessageHandler` **portu**, metrikler | **hayır** |
| `com.crm.messaging.adapter.stream` | `StreamBridgeOutboxPublisher`, `MessageHeaders` | **evet — tek yer** |

Servis tarafında aynı kural: `com.crm.product.messaging.adapter.OrderEventStreamAdapter`
tek `Consumer<Message<byte[]>>`; gövdesi tek bir delegasyon. Karar veren her şey altındaki
düz Java sınıflarında (`InboxDispatcher`, `OrderSubmittedHandler`) — `new
OrderSubmittedHandler(codec).handle(envelope)` hiçbir şey çalışmadan geçerli bir çağrı.

**Neden `@EntityScan`/`@EnableJpaRepositories` üç `*Application` sınıfına yazıldı.**
Auto-configuration'dan entity paketi kaydetmek Boot'un auto-configuration paket taramasını
GENİŞLETMEZ, DEĞİŞTİRİR — o zaman `com.crm.order`'ın kendi entity'leri sessizce
bulunamaz olurdu. Açıkça yazmak yarı-doğru olmayı imkânsız kılıyor.

**Neden `OutboxScheduler` kendi thread'ini kuruyor.** Paylaşılan bir starter'dan
`@EnableScheduling` açmak, ona bağlı HER serviste Spring scheduling'i açardı — bu modülün
işi olmayan bir davranış değişikliği. Tek thread, çünkü sıra garantisi partition key
başına ve bir havuz aynı satışın iki mesajını sırasız yayınlayabilirdi.

**Neden `OutboxStatusWriter` ayrı bean.** `OrderPersistence`'ın javadoc'unda zaten kayıtlı
olan tuzak: `@Transactional` proxy tabanlı, `this.markPublished(...)` proxy'yi atlar ve
annotation sessizce hiçbir şey yapmaz. Burada bunun bedeli, yayınlanmış ama "published"
işaretlenmemiş — yani her poll'da yeniden yayınlanan — bir mesaj olurdu.

**Şema.** `outbox_message` + `inbox_message`, her serviste KENDİ veritabanında (order V4,
product V6, account V5). `outbox_message` için iki **partial** index (`WHERE published_at
IS NULL` / `IS NOT NULL`): relay kuyruğu, iki gauge ve retention tam bu iki yüklemi
sorguluyor. `inbox_message` için `uq_inbox_message_id_group`.

**Değişen mevcut testler.** `ProductServiceIntegrationTest#schemaContainsOnlyProductTables`
ve `AccountServiceIntegrationTest#schemaContainsOnlyAccountTables` — iki yeni tablo
eklendi. Bu testler tablo SAHİPLİĞİNİ iddia ediyor, ve iddia hâlâ doğru: bunlar o
veritabanının KENDİ tabloları, paylaşılan değil.

**Bilerek YAPILMAYANLAR:** asenkron SALE cutover'ı; retry destination MERDİVENİ
(isimlendirme kuralı var — `Destinations.retry(...)` — ama otomatik escalation yok,
binder retry'ı `max-attempts: 1` ile KAPALI, tek retry yolu redelivery + Inbox);
account/product tarafında consumer (yalnız Outbox kaydı var, tüketen yok).

Detay: **ADR-017**, `docs/runbooks/eventing.md`, `docs/contracts/events/`,
`infra/eventing/README.md`.

---

## 5. Alınan Kararlar ve Gerekçeleri

Bu bölüm "neden böyle yapıldı" sorusunun cevabıdır. Değiştirmeden önce gerekçeyi oku.

### 5.1 Monorepo + kök parent POM
- Kökte `com.crm:crm-lite-project` (packaging `pom`) oluşturuldu; `java.version`, `spring-cloud.version`
  ve `spring-cloud-dependencies` importu **tek yerden** yönetiliyor.
- Dört servisin `<parent>`'ı bu köke bağlı (`relativePath ../../pom.xml`). Tekrarlanan versiyon/dependencyManagement
  blokları child POM'lardan silindi. Servise özel property'ler (örn. auth-service `jjwt.version`) child'da kaldı.
- **Yeni servis eklerken:** kök `pom.xml` `<modules>`'a ekle + yeni servisin parent'ını köke bağla.

### 5.2 Docker build context = repo KÖKÜ (önemli!)
- Parent POM köke taşınınca, her servisin POM'u `../../pom.xml`'e bakıyor. Docker build context'i
  sadece servis klasörü olursa parent POM context dışında kalır → build patlar.
- **Çözüm (seçilen):** build context repo köküdür. Dockerfile'larda `COPY pom.xml` + `COPY backend/<servis>/...`
  şeklinde gerçek klasör hiyerarşisi korunur; `mvn -f backend/<servis>/pom.xml ...` ile build edilir.
- **docker-compose'da** her servis: `build.context: ..` (infra'nın bir üstü = repo kökü) + `dockerfile: backend/<servis>/Dockerfile`.
- **Build komutu (elle):** repo kökünden `docker build -f backend/<servis>/Dockerfile -t <ad> .`
- Alternatif "parent POM'u .m2'ye install et" yaklaşımı reddedildi (ekstra base-image adımı + güncel tutma riski).

### 5.3 Dockerfile base image = `maven:3.9-eclipse-temurin-25`
- Başlangıçta `eclipse-temurin:25-jdk` + `mvnw` (Maven wrapper) kullanılıyordu.
- **Sorun:** Bu projedeki wrapper "jar'sız" tip — `mvnw` çalışınca wrapper jar'ını internetten indirmeye çalışır,
  ama `eclipse-temurin` imajında `curl`/`wget` yok → `ClassNotFoundException: MavenWrapperMain` hatası.
- **Çözüm:** İçinde Maven hazır gelen `maven:3.9-eclipse-temurin-25` base image; doğrudan `mvn` çağrılıyor,
  `.mvn/`+`mvnw` kopyalamaya gerek yok. Üç servis de artık aynı desende.

### 5.4 ~~Gateway security: dependency kalır, geçici permitAll~~ — GEÇERSİZ (2026-07-17'de kapandı)
> **⚠️ Bu bölüm tarihsel:** permitAll scaffolding'i auth milestone'unda kaldırıldı;
> gateway artık BFF security chain'i çalıştırıyor (bkz. §4.3, ADR-007/008).
- (Tarihsel kayıt) `spring-boot-starter-security` dependency yerindeydi; her isteğe izin veren
  geçici `SecurityFilterChain` ile routing uçtan uca test edilebildi. Beklenen "JWT gelince
  sıkılaştırılacak" adımı gerçekleşti.

### 5.5 Gateway route/timeout/instance ayarları
- Route prefix'i **WebMVC**'ye özel: `spring.cloud.gateway.server.webmvc.routes` (bkz. §6).
- Timeout **WebMVC**'de `spring.http.client.*` ile (WebFlux'un `spring.cloud.gateway.httpclient.*`'ı ÇALIŞMAZ).
- `lb://` şeması için `spring-cloud-starter-loadbalancer` açıkça pom'a eklendi (eureka-client transitive getirse de niyet belgelensin diye).
- Eureka instance ayarları container uyumu için (IP tabanlı kayıt + benzersiz id).

### 5.6 CORS ertelendi
- Henüz frontend yok → CORS eklense de test edilemez. Frontend gelince gerçek origin ile eklenecek.

### 5.7 docker-compose vendor-neutral (Rancher + Podman)
- `version:` satırı yok (modern Compose Spec; ikisi de destekler).
- Container ağında servisler birbirini **servis adıyla** bulur; bu yüzden gateway'in Eureka adresi compose'da
  env ile `http://discovery-server:8761/eureka` olarak ezilir (application.yml'deki `localhost` sadece IDE içindir).
- **Healthcheck artık VAR** (bkz. §5.10) — önceki not ("healthcheck yok, sadece depends_on sıralaması yeterli")
  yanlış çıktı: gerçek bir çalıştırmada config-server'ın diğer üç servisten milisaniyeler sonra hazır olması,
  hepsinin `localhost` varsayılanlarına düşüp kilitlenmesine yol açtı (§5.10'da detaylı anlatılıyor).

### 5.8 config-server: native profile + classpath (git-backed DEĞİL)
- İhtiyaç: her servisin `application.yml`'inde tekrarlanan/dağınık ayarları (eureka adresi, route'lar, gelecekte
  DB bağlantısı) tek yerden yönetmek; auth-service'in datasource'unu doldururken bunu **iki kere** yazmamak
  (önce yerele, sonra config-server'a taşımak yerine baştan config-server'da tanımlamak — bkz. §9.1'de tercih edilen plan).
- **Seçim:** `spring.profiles.active: native` + `spring.cloud.config.server.native.search-locations: classpath:/config-repo/`.
  Yani config dosyaları **ayrı/dış bir git deposu değil**, `config-server` modülünün kendi
  `src/main/resources/config-repo/` klasöründe duruyor ve servisin jar'ına gömülüyor.
- **Alternatif reddedildi:** git-backed config-repo (ayrı repo veya `file:` ile harici bir git klasörü) — bu proje
  tek geliştiricili ve erken/dev aşamada olduğu için ekstra bir git deposu yönetmenin getirisi yok; ileride
  gerçek ortam/sır yönetimi gerektiğinde git-backed'e geçiş yolu açık bırakıldı.
- **Trade-off (bilinçli kabul edildi):** classpath kaynaklı olduğu için config değişikliği config-server'ın
  rebuild + restart edilmesini gerektiriyor; git-backed'in sağladığı canlı `/actuator/refresh` yok.
- **Client tarafı:** üç istemci servise de (`discovery-server`, `api-gateway`, `auth-service`) `spring-cloud-starter-config`
  bağımlılığı eklendi (bu olmadan `spring.config.import: configserver:...` sessizce hiçbir şey yapmaz).
  Her birinin yerel `application.yml`'i artık sadece `spring.application.name` + `spring.config.import` içeriyor;
  gerçek ayarlar `config-server/src/main/resources/config-repo/<servis-adı>.yml` dosyalarında.
- **`optional:` öneki:** `spring.config.import: "optional:configserver:http://localhost:8888"` — config-server
  ayakta değilse servis yine de başlamayı dener (sert bağımlılık değil), ama pratikte doğru ayarları almak için
  config-server'ın **ilk sırada** ayakta olması gerekiyor (bkz. §7 başlatma sırası).
- **Başlatma sırası değişti:** artık `config-server → discovery-server → api-gateway`. Docker Compose'da
  `depends_on` bu sırayı yansıtacak şekilde güncellendi; container ağında istemciler config-server'a
  `http://config-server:8888` ile ulaşıyor (env override, aynı `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` deseninde).

### 5.10 Docker Compose healthcheck — `depends_on` tek başına yetmiyordu (gerçek arıza, teorik değil)
- **Sorun (gerçekte yaşandı):** `depends_on` (healthcheck'siz haliyle) sadece container'ın **başlatılmasını**
  garanti ediyor, içindeki Spring Boot uygulamasının portu **dinlemeye başlamasını** değil. `customer-service`
  entegrasyonunu Docker Compose'da test ederken, `config-server` diğer üç servisten (discovery-server,
  api-gateway, customer-service) birkaç saniye geç ayağa kalktığı için, üçü de `spring.config.import`
  ile config-server'a **tek seferlik** (retry'sız — `spring.cloud.config.retry.*`/`fail-fast` tanımlı değil)
  bağlanmayı denedi, "Connection refused" aldı ve **process'in tüm ömrü boyunca** Spring Cloud Netflix'in
  hard-coded varsayılanına (`eureka.client.service-url.defaultZone=http://localhost:8761/eureka`) düştü —
  bizim `discovery-server:8761` env override'ımızı hiç görmeden. Sonuç: `discovery-server` kendi kendine
  `localhost:8761`'e register olmaya çalışıyor, `api-gateway`/`customer-service` de aynı şekilde — sürekli
  "Cannot execute request on any known server" hatasıyla sonsuz döngü.
- **Neden `docker compose restart <servis>` geçici olarak işe yaradı:** config-server o ana kadar zaten
  tam ayağa kalkmış oluyor; yeniden başlatılan servis bu sefer config-server hazırken deniyor ve doğru
  ayarları alıyor. Ama bu kalıcı bir çözüm değil, her `up` sonrası manuel restart gerektirirdi.
- **Kalıcı çözüm (uygulandı):** `config-server`, `discovery-server`, `postgres`'e Docker Compose `healthcheck`
  eklendi; bunlara bağımlı servislerin `depends_on`'u `condition: service_healthy` ile **gerçekten hazır
  olmayı bekleyecek** şekilde güncellendi.
  - JVM servisleri (config-server, discovery-server, api-gateway, customer-service) için healthcheck:
    `bash -c '</dev/tcp/127.0.0.1/<port>'` — runtime image'lar (`eclipse-temurin:*-jre`, alpine değil) `curl`/
    `wget` içermiyor, ama Debian tabanlı oldukları için `bash` var; `/dev/tcp` bash built-in'i ekstra paket
    kurmadan basit bir TCP-port-açık-mı kontrolü sağlıyor.
  - `postgres` için: image'da zaten hazır gelen `pg_isready` komutu kullanıldı, ekstra bir şey gerekmedi.
- **Alternatif reddedildi:** `spring.cloud.config.fail-fast: true` + retry ayarları (uygulama tarafında
  config-server'a karşı retry) — bu da işe yarardı ama her istemci servise ayrı ayrı config eklemek
  gerekirdi; Compose seviyesinde tek yerden çözmek (healthcheck) daha az tekrar gerektiriyor.

### 5.11 Spring Boot 4: Flyway'in kendi autoconfigürasyon modülü var, `flyway-core` yeterli değil
- **Sorun (gerçekte yaşandı):** `flyway-core` + `flyway-database-postgresql` pom'da olmasına, config'in
  `spring.flyway.enabled: true` demesine ve Postgres bağlantısının sorunsuz çalışmasına rağmen, Flyway
  migration'ları **hiç çalışmadı** — loglarda "Flyway" kelimesi bile geçmiyordu, Hibernate doğrudan boş bir
  şemaya karşı `ddl-auto: validate` yapıp "missing table [customers]" hatasıyla çöktü.
- **Teşhis:** `--debug` ile alınan Spring Boot condition-evaluation raporunda `FlywayAutoConfiguration`
  hiç görünmüyordu (ne eşleşenler ne eşleşmeyenler arasında) — yani sınıf Spring Boot tarafından hiç
  taranmıyordu. Sebep: Spring Boot 4, autoconfigürasyonu (§ örn. `HibernateJpaConfiguration`'ın
  `org.springframework.boot.hibernate.autoconfigure`'a taşınması gibi) küçük, özellik bazlı modüllere böldü;
  Flyway'in autoconfigürasyonu da artık `org.springframework.boot:spring-boot-flyway` adlı **ayrı** bir
  modülde — `flyway-core` sadece Flyway'in çekirdek motorunu getiriyor, Spring entegrasyonunu değil.
- **Çözüm:** `customer-service/pom.xml`'e açıkça `org.springframework.boot:spring-boot-flyway` bağımlılığı
  eklendi (versiyon yok, Spring Boot parent BOM'undan miras alınıyor, 4.1.0).
- **Etkilenen diğer servisler (tarihsel not):** o dönem auth-service'in pom'u için de aynı uyarı
  geçerliydi; auth-service 2026-07-17'de silindi. Kural kalıcı: Flyway kullanan her YENİ modül
  `spring-boot-flyway`'i açıkça eklemeli.

### 5.12 `SELECT DISTINCT` + `ORDER BY` join kolonuna göre → Postgres hatası (arama endpoint'i)
- **Sorun (gerçekte yaşandı):** `GET /api/customers/search?firstName=ali` **500** dönüyordu.
  `GlobalExceptionHandler`'a eklenen loglama sayesinde (bkz. §5.13) gerçek sebep görüldü:
  `ERROR: for SELECT DISTINCT, ORDER BY expressions must appear in select list`.
- **Sebep:** `CustomerSpecifications.search()` içinde `query.distinct(true)` vardı ("mükerrer müşteri
  dönmesin" gereksinimi için, önlem amaçlı eklenmişti); ama `CustomerController`'daki `Sort`,
  `partyRole.party.individual.firstName/lastName` (join'lenmiş `individuals` tablosunun kolonları) üzerinden
  sıralıyor. Postgres, `SELECT DISTINCT` ile birlikte `SELECT` listesinde olmayan bir kolona göre
  `ORDER BY` yapılmasına izin vermiyor.
- **Çözüm:** `distinct(true)` kaldırıldı. Zaten gereksizdi: `Customer → PartyRole → Party → Individual → Role`
  zincirindeki tüm ilişkiler `@OneToOne`/`@ManyToOne` (1-1) — koleksiyon join'i olmadığı için bu sorgu hiçbir
  zaman fiziksel olarak mükerrer satır üretemez, `DISTINCT` başından beri no-op'tu.
- **Ders:** `DISTINCT` + join'lenmiş bir tabloya göre `ORDER BY` kombinasyonu genel bir SQL tuzağı —
  ileride yeni bir arama/listeleme endpoint'i yazılırken tekrar hatırlanmalı.

### 5.13 GlobalExceptionHandler'ın genel `catch-all`'ı hatayı hiçbir yere loglamıyordu
- **Sorun:** `@ExceptionHandler(Exception.class)` metodu sadece generic bir `ErrorResponse` dönüyordu,
  `logger.error(...)` çağrısı yoktu — yani beklenmeyen bir hata olduğunda ne response'ta ne konsol
  loglarında **hiçbir zaman** gerçek stack trace görünmüyordu. §5.12'deki hatayı bulmak bu yüzden
  zorlaştı (ilk denemede loglar tamamen sessizdi).
- **Çözüm:** `handleUnexpected` metoduna `log.error("Unexpected error handling {} {}", method, uri, ex)`
  eklendi — artık her beklenmeyen hata, tam stack trace'iyle konsola basılıyor.

### 5.9 Lombok: `annotationProcessorPaths` açıkça tanımlanmalı (JDK 25 tuzağı)
- **Sorun:** `customer-service` yazılırken derleme, Lombok'un ürettiği **hiçbir** getter/setter/builder'ı
  bulamadı (`cannot find symbol`, `variable X not initialized in the default constructor` gibi hatalar).
  Standalone `javac -processorpath lombok.jar` ile aynı kod sorunsuz derlendi — yani Lombok kütüphanesinin
  kendisi JDK 25 ile uyumlu, ama **Maven Compiler Plugin, `annotationProcessorPaths` açıkça verilmediğinde
  artık `-classpath`'teki processor'leri otomatik keşfetmiyor** (önceki javac sürümlerinin varsayılan
  davranışının aksine).
- **Çözüm (seçilen):** `customer-service/pom.xml`'e `maven-compiler-plugin` için açık
  `annotationProcessorPaths` (Lombok, `${lombok.version}` — Spring Boot parent'tan miras alınıyor) eklendi.
- **Etkilenen diğer servisler (tarihsel not):** o dönem boş auth-service iskeleti için de aynı uyarı
  yazılmıştı; auth-service 2026-07-17'de silindi. `discovery-server`, `api-gateway` ve
  `crm-security-starter` Lombok kullanmıyor, etkilenmiyor.
- **Neden root POM'a değil, sadece customer-service'e eklendi:** Görev kapsamı customer-service ile sınırlı
  tutuldu ("mevcut çalışan servisleri gereksiz yere değiştirme" ilkesi); ama bu aslında **proje çapında bir
  yapılandırma boşluğu** — kök `pom.xml`'in `<pluginManagement>`'ına taşınması, her yeni Lombok kullanan
  servisin bunu tekrar tekrar eklemek zorunda kalmaması için mantıklı bir sonraki adım (§9'a eklendi).

### 5.14 ~~nationalityId: global DB UNIQUE kaldırıldı, ACTIVE-only kural sadece uygulama katmanında~~ — GEÇERSİZ (superseded)

> **⚠️ 2026-07-10 analist kararı ve ADR-003 bu bölümü GEÇERSİZ KILDI:** nationalityId artık
> **kalıcı ve global** tekildir; DB UNIQUE kısıtı (soft-deleted satırlar dahil) geri geldi ve
> pasif müşteri NATID'sini serbest bırakmaz. Aşağıdaki metin yalnız tarihsel kayıt olarak duruyor.
- **Sorun:** `individuals.nationality_id` DB'de global `UNIQUE`'ti ama iş kuralı sadece "ACTIVE müşteriler
  arasında tekil" istiyordu. Bu iki kural örtüşmüyordu: bir müşteri soft-delete (PASSIVE) edildikten sonra
  aynı nationalityId ile yeni bir aktif müşteri oluşturulmaya çalışılırsa, uygulama katmanı bunu engellemiyordu
  (sadece ACTIVE'lere bakıyordu) ama DB'deki eski PASSIVE satır global UNIQUE'e hâlâ takılıyor, ham bir
  `DataIntegrityViolationException` → genel 500'e düşüyordu.
- **Seçenekler:** (A) DB'deki UNIQUE'i kaldır, tekilliği tamamen uygulama katmanında bırak. (B) Cross-table
  partial unique index (customers/party_roles/parties'teki status'e bakan) ekle.
- **Seçilen: (A).** Status alanı `individuals` tablosunda değil `customers`/`party_roles`/`parties`'te
  olduğu için, (B) bu şemada join gerektiren bir partial index anlamına gelir — Postgres'te doğrudan
  desteklenmez (partial index'in WHERE koşulu sadece kendi tablosundaki kolonlara bakabilir), bu yüzden
  (B) ya denormalize edilmiş bir "is_active" kolonu individuals'a eklemeyi ya da trigger tabanlı bir kısıtı
  gerektirirdi — bu aşama için gereksiz karmaşıklık. (A) daha basit ve zaten var olan uygulama kontrolüyle
  (`checkNationalityIdIsUniqueForCreate/ForUpdate`) tam örtüşüyor.
- **Uygulama:** `V1__create_customer_tables.sql`'deki `UNIQUE` kaldırıldı (yeni bir V3 migration yerine V1
  doğrudan değiştirildi — bu servis henüz merge/push edilmemişken, local/dev aşamasında kabul edilebilir bir
  kısayol; bkz. docs/customer-service.md'deki "developers must reset customer_db" notu).
  `Individual` entity'sindeki `@Column(unique = true)` da kaldırıldı.
- **Ek güvence:** `GlobalExceptionHandler`'a bir `DataIntegrityViolationException` handler'ı eklendi (409 +
  `MSG-CUST-DUP-NATID`) — uygulama kontrolünü aşan bir yarış durumu (iki eşzamanlı istek) hâlâ DB'de
  `roles.code`/`individuals.party_id`/`customers.party_role_id` gibi kalan başka UNIQUE kısıtlara takılırsa,
  bu da genel 500 yerine temiz bir yanıt döner.
- **Doğrulama:** Aynı nationalityId ile oluştur → soft-delete → aynı nationalityId ile tekrar oluştur akışı
  gerçek bir Postgres'e karşı elle test edildi, artık 201 dönüyor (öncesinde global UNIQUE'e takılırdı).

### 5.15 Türkçe karakter "Malformed request body" hatası: sunucu değil, shell/argv encoding sorunu
- **İlk şüphe (yanlış çıktı):** `Gender.java`'nın `com.fasterxml.jackson.annotation` importu kullanması ve
  `JacksonConfig`'in Jackson 3 (`tools.jackson.*`) API'sini kullanması karışık görünüyordu. **Doğrulandı ki
  bu bir sorun değil** — Jackson 3, `jackson-annotations` modülünü (dolayısıyla `@JsonValue`/`@JsonCreator`)
  bilinçli olarak `com.fasterxml.jackson.annotation` paketinde bıraktı; `tools.jackson.databind`'in Jackson 3
  ObjectMapper'ı bu anotasyonları sorunsuz tanıyor.
- **Gerçek kök neden (canlı sunucuya karşı doğrulandı):** Turkçe karakterli bir `curl -d '...'` isteği,
  hem gateway üzerinden hem doğrudan customer-service'e karşı **her zaman** "Malformed request body" +
  400 dönüyordu — ama tamamen ASCII bir istek sorunsuz çalışıyordu. Kök neden loglaması eklenip
  (`GlobalExceptionHandler.handleUnreadableBody` artık `log.warn(..., ex)` yapıyor) sunucu yeniden
  başlatıldığında gerçek istisna görüldü: `tools.jackson.core.exc.StreamReadException: Invalid UTF-8 middle
  byte 0x7a`. `curl -v --trace-ascii` ile tel üzerindeki baytlar incelendiğinde, "ö" (UTF-8'de 2 bayt,
  `0xC3 0xB6`) **tek, geçersiz bir bayta** dönüşmüş olarak gönderildiği görüldü — yani JSON, curl'e ulaşmadan
  ÖNCE bozulmuştu.
- **Neden:** Bu ortamdaki `curl` (`/mingw64/bin/curl`, `x86_64-w64-mingw32` derlemesi) native bir Win32
  programı. Git Bash, bu tür bir programı çağırırken komut satırı argümanlarını kendi iç UTF-8 temsilinden
  Win32 CRT'nin beklediği "multi-byte" temsile (aktif ANSI kod sayfası — `chcp` ile görülür, bu ortamda 437)
  çevirmek zorunda; `LANG`/`LC_ALL` da boştu. Bu çeviri UTF-8 çok baytlı bir diziyi doğru taşıyamıyor.
  **`--data-binary @-` ile heredoc/stdin kullanmak** bu sorunu tamamen atlıyor (veri argv üzerinden değil,
  bash'in kendi UTF-8 farkında stdin akışından geçiyor) — canlı sunucuya karşı doğrulandı, "Gözek" sorunsuz
  round-trip etti. Postman gibi GUI araçları da argv'den geçmediği için hiç etkilenmiyor.
- **Yapılan kod değişikliği:** Sadece `GlobalExceptionHandler`'a kök nedeni loglayan bir satır eklendi;
  ayrıca (ilgisiz ama aynı handler'da bulunan) geçersiz enum değerleri (`gender: "Unknown"` gibi) artık genel
  "Malformed request body" yerine temiz bir 400 + `validationErrors` döner. `JacksonConfig`/`Gender`'da
  hiçbir değişiklik gerekmedi — ikisi de zaten doğruydu.
- **Ders:** "Malformed request body" + Türkçe karakter kombinasyonu görülünce önce sunucu kodundan şüphelenmek
  yanlış bir refleksti; gerçek kanıt (raw byte inceleme + kök neden loglaması) olmadan kod "düzeltmeye"
  çalışmak, çalışan kodu bozma riski taşırdı.

---

## 6. WebMVC vs WebFlux Tuzağı (DİKKAT)

Bu gateway **WebMVC** (servlet) stack'idir. İnternetteki Spring Cloud Gateway örneklerinin çoğu **WebFlux** (reactive)
içindir ve **birebir kopyalamak hatalıdır**. Doğrulanmış farklar:

| Konu | WebFlux (YANLIŞ, bizde çalışmaz) | WebMVC (DOĞRU, bizim kullandığımız) |
|---|---|---|
| Route prefix | `spring.cloud.gateway.routes` | `spring.cloud.gateway.server.webmvc.routes` |
| Eski MVC prefix | — | `spring.cloud.gateway.mvc.routes` **deprecated**, kullanma |
| Timeout | `spring.cloud.gateway.httpclient.*` | `spring.http.client.connect-timeout` / `read-timeout` |
| Security bean | `SecurityWebFilterChain` + `ServerHttpSecurity` | `SecurityFilterChain` + `HttpSecurity` (servlet) |
| Route listeleme | `/actuator/gateway/routes` (var) | **YOK** — route'lar `RouterFunction`, `/actuator/mappings`'te görünür |

---

## 7. Nasıl Çalıştırılır

### 7.1 Başlatma sırası (IDE veya Maven — sıra ÖNEMLİ)
customer-service'in **yazma** işlemleri lookup-service (ADR-002) ve mernis-stub'a (KR-10) muhtaçtır;
bu ikisi olmadan servis açılır ve okuma çalışır ama create/update/delete 503 döner (bilinçli fail-closed).

1. **Postgres + Keycloak** (compose/Podman — ilk volume açılışında `customer_db` + `lookup_db`
   + `keycloak_db` oluşur; Keycloak realm'i otomatik import eder → `http://localhost:8180/realms/crm-lite`)
2. **config-server** → doğrula: `http://localhost:8888/customer-service/default`
3. **discovery-server** → `http://localhost:8761`
4. **lookup-service** → `http://localhost:8083/actuator/health` (API artık JWT ister)
5. **mernis-stub** → `http://localhost:8084/actuator/health`
6. **api-gateway** → Eureka'da `API-GATEWAY`; tarayıcıda `http://localhost:8080/api/session/me`
   → Keycloak login (`ayilmaz`/`crm-dev`) → oturum JSON'u
7. **customer-service** → Flyway V1/V2 loglarda; `http://localhost:8082/actuator/health`
8. **account-service** → Flyway V1/V2/V3 loglarda; `http://localhost:8085/actuator/health`
   (yazma işlemleri lookup-service + customer-service ister — ADR-013 fail-closed)
9. **product-service** → Flyway V1/V2 loglarda; `http://localhost:8086/actuator/health`
   (okumalar account-service + customer-service ister — erişilemezse 503 fail-closed)

### 7.1b Terminal / Maven (aynı sıra, ayrı terminallerde)
```bash
mvn clean install -DskipTests   # tek seferlik build (testli: mvn clean install — Docker gerekir)

docker compose -f infra/docker-compose.yml up -d postgres keycloak   # 0) Postgres + Keycloak (ADR-006)
# Podman: podman compose -f infra/docker-compose.yml up -d postgres keycloak

mvn -pl backend/config-server    spring-boot:run   # Terminal 1
mvn -pl backend/discovery-server spring-boot:run   # Terminal 2
mvn -pl backend/lookup-service   spring-boot:run   # Terminal 3
mvn -pl backend/mernis-stub      spring-boot:run   # Terminal 4
mvn -pl backend/api-gateway      spring-boot:run   # Terminal 5
mvn -pl backend/customer-service spring-boot:run   # Terminal 6
mvn -pl backend/account-service  spring-boot:run   # Terminal 7
mvn -pl backend/product-service  spring-boot:run   # Terminal 8
```
Detaylı curl doğrulama sırası: docs/api/customer-service.md; runbook: docs/runbooks/local-development.md.

### 7.2 Docker / Podman (infra/docker-compose.yml)
Repo kökünden:
```bash
# Rancher Desktop (dockerd):
docker compose -f infra/docker-compose.yml up --build

# Podman:
podman compose -f infra/docker-compose.yml up --build
# veya: podman-compose -f infra/docker-compose.yml up --build
```
İlk build birkaç dakika sürer (image + bağımlılık indirir + Postgres init script'i `customer_db`,
`lookup_db` ve `keycloak_db`'yi oluşturur). Durdurma: aynı komut `down` ile. **Postgres verisini de
silmek için:** `down -v`. Not: compose `config-server`, `discovery-server`, `api-gateway`,
**`keycloak`**, `postgres`, `lookup-service`, `mernis-stub`, `customer-service` içerir; healthcheck +
`depends_on: service_healthy` başlatma sırasını otomatik uygular. **Compose profilinde yalnız
gateway (8080), keycloak (8180), config (8888), eureka (8761) ve postgres (5432) host'a açılır** —
8082/8083/8084 yalnız `crm-net` içindedir (ADR-009/010). Eski volume'larda `keycloak_db` yoksa
runbook'taki tek satırlık `CREATE DATABASE` çözümüne bakın.

**⚠️ Flyway/schema değişikliği sonrası (örn. 2026-07-11 workbook baseline'ı — V1/V2 YENİDEN yazıldı,
henüz paylaşılmamış WIP commit'te olduğu için baseline replace serbest — bkz. ADR-002/003):**
V1 migration'ı doğrudan değiştiren bir güncelleme çektiyseniz, daha önce kurulmuş bir `customer_db` Flyway'in
checksum doğrulamasını geçemez. Volume'u sıfırlayın:
```bash
docker compose -f infra/docker-compose.yml down -v
docker compose -f infra/docker-compose.yml up -d postgres
# Podman eşleniği:
podman compose -f infra/docker-compose.yml down -v
podman compose -f infra/docker-compose.yml up -d postgres
```
ardından customer-service'i yeniden başlatın (Flyway V1/V2'yi temiz şemaya uygular).

---

## 8. Doğrulama (Smoke Test — kompakt)

Tüm stack ayaktayken (sıra: §7.1). Her istek durum kodunu basar
(`-w "\nHTTP Status: %{http_code}\n"`):

```bash
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8888/customer-service/default   # config içeriği
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8761/actuator/health            # Eureka (dashboard: 8761)
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8180/realms/crm-lite            # Keycloak realm'i ayakta (ADR-006)
curl -sS -w "\nHTTP Status: %{http_code}\n" http://localhost:8080/actuator/health            # gateway (tek anonim actuator)
# Security ON: anonim iş API'si 401 döner (MSG-AUTH-UNAUTHORIZED)
curl -sS -w "\nHTTP Status: %{http_code}\n" -H "Accept: application/json" "http://localhost:8080/api/customers"
curl -sS -w "\nHTTP Status: %{http_code}\n" -H "Accept: application/json" "http://localhost:8080/api/accounts?customerId=1001"
```

**Oturumlu doğrulama tarayıcıyla yapılır:** `http://localhost:8080/api/session/me` → Keycloak
login (`ayilmaz`/`crm-dev`) → oturum JSON'u; ardından aynı tarayıcıda
`http://localhost:8080/api/customers` ADR-005 browse listesini döner (tam detay kontratı,
`/api/customers/search` alias'ı YOK). **Tam kataloglar:** docs/api/customer-service.md,
docs/api/shared-lookup-service.md, docs/api/mernis-stub.md, **docs/api/authentication.md**
(login/session/CSRF/logout kontratı); runbook: docs/runbooks/local-development.md;
**Postman koleksiyonu:** docs/postman/.

**Swagger UI (ADR-012):** `http://localhost:8080/swagger-ui.html` login'siz açılır (sağ üstte
customer-service/lookup-service dropdown'ı). "Try it out" için önce ayrı bir sekmede
`http://localhost:8080/` üzerinden normal login yapılmış olmalı — aynı origin olduğu için session
cookie'si otomatik gider. Mutating istekler (POST/PUT/DELETE/PATCH) için `XSRF-TOKEN` cookie
değeri şimdilik elle `X-XSRF-TOKEN` header'ı olarak eklenmeli (bkz. §4.7, §9.2).

---

## 8A. Detaylı Fonksiyonel Test Rehberi (Auth + Customer + Swagger)

Manuel/QA testi için §8'deki kompakt smoke test'ten daha ayrıntılı üç yol. Hepsi gerçek
Keycloak + gateway + customer-service/lookup-service'e karşı doğrulandı (A/C: 2026-07-20,
B: 2026-07-22).

### A) Tarayıcıdan (en kolay yol — asıl tasarlandığı akış budur)
**Auth testi:** `http://localhost:8080/` adresini aç → Keycloak login sayfasına yönlenirsin →
`ayilmaz` / `crm-dev` (veya `edemir` / `crm-dev`) ile giriş yap → gateway'e geri döner ve
`{"authenticated":true,"username":"ayilmaz","roles":["crm-user"]}` JSON'ını görürsün
(`SessionController.home`). Bu, tüm Authorization Code + PKCE zincirinin çalıştığını kanıtlar.
Negatif test için `mkaya` / `crm-dev` dene — bu kullanıcı realm'de `enabled: false`, Keycloak
**"Account is disabled"** hatası verir.

**Customer/lookup testi (GET'ler):** Aynı sekmede (session cookie hâlâ tarayıcıda):
- `http://localhost:8080/api/customers` → müşteri listesi (Page)
- `http://localhost:8080/api/customers/1001` → tek müşteri detayı
- `http://localhost:8080/api/customers/1001/addresses` → adresler
- `http://localhost:8080/api/lookups/statuses?domain=GENERAL` → merkezi katalog (ADR-002)

Tarayıcı sadece GET yapabildiği için POST/PUT/DELETE/PATCH testlerini tarayıcıdan yapamazsın —
onun için Postman veya Swagger UI (C, aşağıda) gerekir.

### B) Postman'dan (mutating istekler + tam kontrol için)
Koleksiyon: `docs/postman/CRM-Lite.postman_collection.json` +
`CRM-Lite.local.postman_environment.json` (detay: `docs/postman/README.md`,
`docs/runbooks/auth-testing.md` §6). 2026-07-22'de sıfırdan bir Postman kurulumuyla uçtan
uca doğrulandı — Keycloak login formunu Postman içinden POST'lamaya **çalışma**, kırılgan ve
artık desteklenmiyor; login her zaman gerçek tarayıcıda yapılır.

**B.0 — İçe aktar (bir kere).** Sol üstte bir **Team workspace**'teysen ve import/create
sırasında `"you don't have permission"` hatası alırsan **My Workspace**'e (kişisel) geç, orada
dene. **Import** → iki JSON dosyasını birlikte sürükle-bırak (dosya *yolunu* metin kutusuna
yapıştırma — `Incorrect format` hatası verir). Sağ üstteki environment dropdown'dan
**"CRM Lite — Local"**ı seç — seçmezsen her istek `getaddrinfo ENOTFOUND {{gatewayBaseUrl}}`
ile patlar.

**B.1 — Tarayıcıda giriş yap.** `http://localhost:8080/` (veya `/api/session/me`) aç → Keycloak
login sayfasına yönlenir → `ayilmaz` / `crm-dev` → gateway'e döner,
`{"authenticated":true,"username":"ayilmaz","roles":["crm-user"]}` JSON'ı görürsün.

**B.2 — Cookie'leri Postman'a taşı.** DevTools (F12) → Application → Cookies →
`http://localhost:8080` → **`JSESSIONID`** ve **`XSRF-TOKEN`** değerlerini kopyala (sadece
value, `<`/`>` yok). Postman → herhangi bir isteğin **Send** altındaki **Cookies** linki →
**Add Domain** `localhost` →
```text
JSESSIONID=<değer>; Path=/
XSRF-TOKEN=<değer>; Path=/
```

**B.3 — `xsrfToken` environment değişkenini ayarla.** Sol kenar çubuğu → **Environments** →
**CRM Lite — Local** → `xsrfToken` satırının **CURRENT VALUE** kolonuna `XSRF-TOKEN` cookie
değerini yapıştır → **Save**.

**B.4 — Doğrula.** `09 - Authentication` → *Session probe* → `200` + `"username": "ayilmaz"`
beklenir; `401` dönerse B.1–B.3'ü tekrarla.

**B.5 — GET istekleri.** `00`–`03` ve `04`/`05`/`06`'daki read istekleri ekstra bir şey
gerektirmez, cookie jar yeterli.

**B.6 — Mutating istekler** (`POST`/`PUT`/`PATCH`/`DELETE`, klasör 04/05/06 + 08'deki negatif
senaryolar — ~19 istek). Koleksiyonda **sadece Logout** isteği `X-XSRF-TOKEN` header'ını hazır
taşır; diğer tüm yazma isteklerinde **elle eklenmesi gerekir**:
1. İsteği aç → **Headers** sekmesi
2. Yeni satır: Key `X-XSRF-TOKEN`, Value `{{xsrfToken}}`
3. Send

Header'sız aynı istek `403` + `MSG-AUTH-CSRF-REJECTED` (`Invalid CSRF Token 'null'...`) döner
(bilerek — test edilmiş davranış). `04` sırayla çalıştırılmalı (detail → create → update →
delete → 404 doğrulama); *create*'ten önce `nationalityId`'yi taze, hiç kullanılmamış bir
11 haneli değere çevir (ADR-003 — silinen id'ler bile sonsuza dek rezerve).

**B.7 — Logout.** `09` → *Logout* isteği (2026-07-27'den beri, scope-and-conflicts.md §5.7
seçenek (a)) artık `200` + `{"logoutUrl": "..."}` JSON döner — `302`/`Location` değil, çünkü bir
XHR/Postman isteği Keycloak `end_session`'a giden cross-origin redirect zincirini güvenilir
şekilde süremiyordu (askıda kalabiliyordu), bu da **Keycloak SSO cookie'sine hiç dokunulmamasına**
yol açıyordu. Bu isteğin kendisi **gateway oturumunu** (JSESSIONID) senkron olarak bitirir; SSO'yu
gerçekten bitirmek `logoutUrl`'e (Postman'da `GET` ile) ayrıca gidilmesini gerektirir. Eğer
`logoutUrl` Keycloak `end_session` yerine doğrudan `.../oauth2/authorization/keycloak` çıkarsa,
oturum zaten geçersiz/anonimdi (ör. bu istek daha önce bir kez çalıştırılmış) — B.1'den tazele.

Postman'dan logout attıktan sonra tarayıcıda `/api/session/me` `401` döner (uygulama oturumu
gerçekten bitti — aynı JSESSIONID paylaşılıyor); `logoutUrl`'e ayrıca gidilmezse
`/oauth2/authorization/keycloak`'a giderken **şifre sormadan geri giriş yaptırır** — Keycloak SSO
tarayıcıda hâlâ canlı. Gerçek Angular shell (`frontend/src/app/core/auth/auth.service.ts`)
— kullanıcı sidenav'daki çıkış onay pop-up'ında *Evet* dedikten sonra — `logoutUrl`'i alıp
`window.location.replace` ile tam-sayfa gezinme yaparak bunu otomatik yapar ve kullanıcıyı
doğrudan Keycloak giriş formuna bırakır (araya bir "çıkış yapıldı" sayfası girmez);
DevTools console'dan yalnız `fetch('/logout', ...)` çalıştırıp `logoutUrl`'e gitmemek de aynı
eksik senaryoyu üretir (bkz. `docs/runbooks/auth-testing.md` §4.1 "Trap 2"), tam temiz başlangıç
için incognito pencereyi kapat.

**B.8 — Sık hatalar**

| Belirti | Sebep | Çözüm |
|---|---|---|
| `403 MSG-AUTH-CSRF-REJECTED`, token `'null'` | Header hiç eklenmemiş | B.6 |
| `401` her istekte | Oturum 30 dk idle timeout / cookie yanlış | B.1–B.2 tazele |
| `getaddrinfo ENOTFOUND {{gatewayBaseUrl}}` | Environment seçilmemiş | B.0 |
| Import sırasında "you don't have permission" | Team workspace'te yetki yok | My Workspace'e geç |
| Logout `302` ama Keycloak'a değil `/`'a gidiyor | Oturum zaten sonlanmış | B.1'den tazele |

Not: `customer-service`'e (8082) doğrudan Postman'dan erişilemez — host'a port açılmıyor (ADR-009),
her şey `localhost:8080` üzerinden gateway'e gitmeli.

### C) Swagger UI'dan (ADR-012 — dropdown + "Try it out")
`http://localhost:8080/swagger-ui.html` → sağ üstte customer-service/lookup-service dropdown'ı,
login'siz açılır. "Try it out" çalıştırmadan önce ayrı bir sekmede (A)'daki gibi login ol — aynı
origin olduğu için session cookie'si Swagger sekmesine de geçer. GET'ler direkt çalışır; mutating
çağrılar için tarayıcı DevTools → Application → Cookies'ten `XSRF-TOKEN` değerini alıp isteğin
header alanına `X-XSRF-TOKEN` olarak elle ekle (bkz. §4.7 "Bilinen sınır"). Doğrulanmış örnek:
```bash
curl -b cookies.txt -X PATCH -H "X-XSRF-TOKEN: <xsrf-token>" \
  http://localhost:8080/api/customers/1001/addresses/1/primary
```

---

## 9. Sırada Ne Var (Roadmap / Öncelik)

### 9.1 ~~KİMLİK DOĞRULAMA / GÜVENLİK~~ ✅ UYGULANDI (2026-07-17) — sıradaki adaylar
Müşteri agregatı ✅ (2026-07-11), ADR-005 liste kontratı ✅ (2026-07-16), **auth/security
milestone'u ✅ (2026-07-17, ADR-006..011 — detay §4.3)**. Milestone'un tasarım kararı netleşti:
auth-service iskeleti KALDIRILDI; BFF gateway'de, kimlik Keycloak'ta.
**account-service ✅ (2026-07-23, ADR-013/014 — §4.8)** ve **product-service'in
salt-okunur FR-PROD-01..02 dilimi ✅ (2026-07-29 — §4.9)** de tamamlandı.
**FR-SALE §2.7 ✅ (2026-08-02, ADR-015/016 — §4.10):** order-service + product yazma
dilimi + account involvement komutu; §9.1b'deki ADR borcu kapandı.
**Eventing temeli ✅ (2026-08-07, ADR-017 — §4.11):** transactional Outbox/Inbox,
versiyonlu zarf, broker sınırı; **SALE cutover'ı DEĞİL** (bkz. §9.6).
**Sıradaki adaylar:** §2.7'nin üç frontend ekranı (Offer Selection / Product
Configuration / Submit Order), customer-service takip PR'ı (KR-02 `accountNumber` ve
artık `orderNumber` araması, aktif-ürün guard'ı, `MSG-ADDR-IN-USE`), FR-LANG
lokalizasyon (varsayılan dil İngilizce), Keycloak login sayfası proje teması.
**Analist onayı bekleyen iki kural artık kritik yolda:** KR-12 ve teklif fiyatları.
**Adres/iletişim için ayrı servis YOK ve PLANLANMIYOR** (ADR-001).

### 9.1b ~~product-service diliminden kalan işler~~ — KAPANDI (2026-08-02), kalanlar aşağıda
- [x] ~~**ADR-015 (product boundary)** + **ADR-013 "read-side" fıkrası**~~ — **YAZILDI**
  (ADR-015; ADR-013 §7 okuma, §8 yazma, §3.6 `customerNumber`). ADR-016 (order boundary)
  da eklendi. `service-boundaries.md`'nin "sign-off eksik" uyarısı artık yalnız
  **içerik** için geçerli (KR-12 + fiyatlar), sınırlar için değil.
- [x] ~~**Yazma tarafı (FR-SALE §2.7)**~~ — **UYGULANDI** (§4.10): order-service +
  product-service yazma dilimi + account-service involvement komutu.
- [x] ~~**Karakteristik endpoint'leri**~~ — `GET /api/offers/{id}/characteristics`.
- [x] ~~**`LookupCatalogClient` sınırı yazma diliminden ÖNCE**~~ — ilk yazma kodundan
  önce kuruldu (ADR-015 §4.1).
- [ ] **Teklif fiyatları analist onayı** — `PROD_OFR.product_offer_total_price`
  (299/149/49 + P5 eklemeleri) uydurulmuş fixture (document-delta P1/P5).
  **Artık daha kritik:** bu değerler `cust_ord_item.amount`'a snapshot'lanıyor, yani
  provizyonel fiyatlar kalıcı sipariş geçmişine dönüşüyor (ADR-016 §2.4).
- [ ] **KR-12 (sipariş numarası) analist onayı** — proje-önerisi kural (ADR-016 §4).
  Aynı pakette: sipariş/hesap numarası çakışması (§8.1) ve AC-SALE-01-12'nin
  "Order ID submit'ten önce gösterilir" ifadesi (§8.3).
- [ ] **Takılı kalan PNDG için süpürücü YOK** — confirm telafisi de patlarsa ürünler
  PNDG kalır: müşteriye görünmez ama hesap silme guard'ını bloklar. Tek sorguyla
  bulunabilir (`status_id = PNDG`); spekülatif bir reconciliation job yazılmadı
  (ADR-015 §8.4).
- [ ] **Transfer / service address change akışları** — mock UI'da hesap satırı
  aksiyonu olarak var, **hiçbir FR/AC kapsamıyor** (ADR-016 §8.2). Analist sorusu.
- [ ] **Frontend ürün + satış ekranları** — `MSG-PROD-NONE` metni, `"-"` kampanya
  gösterimi, Action (göz) ikonu, ve §2.7'nin üç ekranı (Offer Selection / Product
  Configuration / Submit Order). Backend kontratı hazır; ayrı takip işi (ADR-016 §9).

### 9.2 Auth milestone'undan kalan işler (implementasyon bitti, bunlar takip)
- [ ] **ADR-011 analist onayı** — workbook USERS tablosunun Keycloak lehine terk edilmesi.
- [ ] **Keycloak proje teması** — AC-AUTH-01 UI detayları (buton disable, maskeleme, 64 karakter,
  LBL-LANGUAGE) + TR/EN görsel bütünlük; şu an standart Keycloak sayfası + yerleşik i18n.
- [ ] **Gerçek ortam sertleştirmesi:** `crm-bff`'i confidential client + vault'lu secret'a çevir
  (ADR-006 §5), `crm.security.cookie-secure=true`, Spring Session/Redis ile session scale-out
  (ADR-007), gateway'e resilience4j (eski §9.5 maddesi).
- [ ] (Opsiyonel) Postman/CI araçları için gateway'e hibrit Bearer kabulü — şu an gateway yalnız
  oturum kabul ediyor; araçlar tarayıcı oturum çerezini kullanıyor (docs/postman/README.md).
- [ ] **Swagger UI CSRF interceptor (ADR-012, §4.7):** `swagger-ui.html`'e custom `requestInterceptor`
  JS'i eklenip `XSRF-TOKEN` cookie'sinin `X-XSRF-TOKEN` header'ına otomatik kopyalanması — şu an
  mutating "Try it out" çağrıları için kullanıcı bunu elle yapıyor.

### 9.3 customer-service — kalan bilinçli TODO'lar
- [x] ~~Adres/iletişim~~ — **TAMAMLANDI, aynı serviste** (ADR-001; ayrı address/contact-service YOK).
- [x] ~~gsmNumber araması 501~~ — **yerel implementasyon** (CNTC_MEDIUM artık customer_db'de).
- [x] ~~nationality_id tekillik çelişkisi~~ — **ADR-003 ile kapandı**: kalıcı global DB UNIQUE.
- [x] ~~Testcontainers yok~~ — **kuruldu**: entegrasyon testleri gerçek PostgreSQL container'ına karşı.
- [x] ~~**`accountNumber`/`orderNumber` araması** 501'den gerçek entegrasyona çevrilecek (KR-02)~~ —
  **TAMAMLANDI 2026-08-05** (bu sayfanın beklediği takip PR'ı): `AccountServiceClient` →
  `GET /api/accounts/{n}` ve `OrderServiceClient` → `GET /api/orders/{n}`; 501 kuralı
  **silindi**. Detay §4.4 + ADR-005 §Addendum.
- [x] ~~Müşteri silmede fatura hesabı pasifleştirme (AC-CUST-05-04) + aktif ürün kontrolü
  (AC-CUST-05-03)~~ — **TAMAMLANDI 05.08.2026 (PR #29).** Silme, yerel agregata dokunmadan
  önce account-service'ten hesapları listeleyip Active olanları pasifleştiriyor; ürünü olan
  hesap 409 `MSG-CUST-HAS-PRODUCTS`, servis erişilemezse 503. Erken
  `checkCustomerHasNoActiveProducts` guard'ı **bilinçli olarak no-op kaldı** — aynı ret zaten
  bir katman derinde yakalanıyor.
- [x] ~~`checkAddressIsNotInUse` (AC-ADDR-04-04, `MSG-ADDR-IN-USE`)~~ — **Billing Account bacağı
  TAMAMLANDI 05.08.2026 (BUG-API-ADDR-04-03):** silme sırasında `AddressServiceImpl`,
  `AccountServiceClient.listAccounts(customerNumber)` ile hesapları çeker; Active bir hesap
  silinen adrese (`billingAddressId`) atıflıysa 409 `MSG-ADDR-IN-USE`, Passive hesaplar veya
  başka adrese atıflı Active hesaplar engellemiyor, account-service erişilemezse 503
  `MSG-SERVICE-UNAVAILABLE` (adres yerelde dokunulmadan kalır). **AC-ADDR-04-04'ün servis-adresi/
  product-service bacağı hâlâ açık** — o kapsam bu değişikliğe dahil değil, ayrı takip gerektirir.
- [ ] Arama performansı: kelime-başı `'% q%'` LIKE deseni index kullanamaz — veri hacmi büyürse `pg_trgm`
  değerlendirilmeli (şu an bootcamp ölçeğinde sorun değil).

### 9.4 config-server sertleştirme (ileride)
- [ ] Şu an classpath/native — sırlar (DB şifresi, JWT secret) düz metin olarak jar'a gömülüyor. Gerçek ortam
  öncesi ya Jasypt/Spring Cloud Config encrypt-decrypt endpoint'i ya da git-backed + harici secret yönetimine geçiş değerlendirilmeli.
- [ ] `/actuator/refresh` / Spring Cloud Bus ile canlı config reload (şu an yok — değişiklik = rebuild+restart).

### 9.5 Gateway sıkılaştırma
- [x] ~~`SecurityConfig`'i permitAll'dan doğrulamaya çevir~~ — **TAMAMLANDI** (BFF chain, §4.3/ADR-007).
- [ ] CORS: önerilen kurulum Angular dev-proxy (same-origin, CORS'suz — ADR-008 §4); doğrudan
  cross-origin istenirse açık allowlist eklenecek (asla wildcard+credentials).
- [ ] (İsteğe bağlı) resilience4j circuit breaker — WebMVC blocking model için downstream koruması.

### 9.6 Container/altyapı borçları
- [x] ~~Compose healthcheck'leri~~ — **eklendi** (bkz. §5.10): config-server/discovery-server/postgres için
  healthcheck + bağımlı servislerde `depends_on: condition: service_healthy`.
- [ ] Eureka self-preservation'ı dev için kapatmak (`eureka.server.enable-self-preservation: false`) — opsiyonel.
- [ ] discovery-server, gateway ve customer-service için graceful shutdown.
- [ ] **Lombok `annotationProcessorPaths` düzeltmesi kök `pom.xml`'in `<pluginManagement>`'ına taşınmalı**
  (bkz. §5.9) — şu an sadece customer-service'te, her yeni Lombok kullanan serviste tekrar eklenmesi gerekiyor.

### 9.7 Eventing temelinden kalan işler (ADR-017 — altyapı bitti, bunlar takip)

Temel UYGULANDI (§4.11). Kalanlar, **sırasıyla**:

- [ ] **SALE cutover'ı — ayrı bir dal, ayrı bir ADR eki.** Üç adım, her biri bir
  sonrakinden önce gözlemlenebilir (`docs/runbooks/eventing.md` §6): (1) yalnız kayıt
  (`outbox.enabled=true`, API yanıtları bit-bit aynı kalmalı), (2) yayınla
  (`broker.enabled=true` + **tam bir** relay), (3) tüket — `POST /api/orders`'ın
  DEĞİŞTİĞİ adım budur. Adımları birleştirme.
- [ ] **Debezium connector'ları canlı doğrulama.** `docker compose --profile eventing`
  yapılandırması `docker compose config` ile geçerli ve connector JSON'ları yerinde,
  ancak **connector'lar çalışan bir Connect'e karşı henüz kaydedilmedi**; in-process
  relay yolu ise testlerle kanıtlı. Cutover adım 2'nin ön koşulu.
  **GÜNCELLEME 13.08.2026:** artık kayıtlılar (`kafka-connect-init`) ve `account`/
  `product-outbox-connector` RUNNING, ama **`order-outbox-connector` FAILED** —
  `occurred_at` alanı Debezium'un outbox SMT'sinin beklediği `INT64` tipinde değil
  (`curl localhost:8083/connectors/order-outbox-connector/status`). Şu an
  ENGELLEYİCİ DEĞİL (order-service'in in-process relay'i aynı satırları zaten
  yayınlıyor), ama account/product-service'te connector RUNNING iken in-process
  relay de aktif — runbook §1'in uyardığı çift-yayın durumu, şimdilik inbox dedup
  ile zararsız. Kök neden araştırılmadı; muhtemelen order_db'nin `outbox_message.occurred_at`
  kolon tanımı account_db/product_db'ninkinden farklı (Flyway V4 vs V5/V6).
- [ ] **Retry destination merdiveni.** İsimlendirme kuralı ve
  `Destinations.retry(dest, attempt)` var; otomatik escalation YOK ve binder retry'ı
  bilerek kapalı (`max-attempts: 1`). Tek retry yolu redelivery + Inbox. Cutover neyin
  fazlasına ihtiyaç duyduğunu söyleyene kadar bu böyle kalmalı.
- [ ] **DLQ için operatör aracı.** Bugün DLQ'yu okumanın yolu
  `kafka-console-consumer.sh`; replay elle. Yeterli, ama bir tüketici canlıya çıkmadan
  önce gözden geçirilmeli.
- [ ] **Prometheus alarm kuralları.** Metrikler yayında (§4.11) ama alarm tanımlanmadı.
  Doğru alarm `crm_outbox_oldest_unpublished_age_seconds` ve
  `crm_inbox_dead_letter_total` üzerine; backlog kapasite göstergesi, duplicate ise
  sağlıklı davranış — ikisine alarm kurma.
- [ ] **Grafana dashboard'una eventing paneli** — "CRM Lite — Overview" henüz outbox/inbox
  metriklerini göstermiyor.

---

## 9A. Git Çalışma Akışı (özet + linkler)

Kurallar kısa: `main` = yalnız stabil demo/release; `dev` = entegre, review'lı geliştirme tabanı;
iş her zaman güncel `origin/dev`'den açılan kısa ömürlü `feature/*`, `fix/*`, `docs/*`,
`refactor/*`, `test/*`, `chore/*` dallarında yapılır. Akış: temiz working tree → `git fetch
--prune` + `git pull --ff-only` ile dev'i tazele → dal aç → tek amaçlı değişiklik →
build/test/diff kontrolü → **yalnız amaçlanan dosyaları** stage'le → Conventional Commit → push →
**base=dev** PR → en az bir review → konuşmaları çöz → **Squash and merge** → dev'i tazele, dalı
sil. dev/main'e doğrudan commit, kör `git add .`, paylaşılan dala force-push YASAK. Release:
dev → PR → main (+ opsiyonel tag); `release/*` dalı şimdilik yok. WIP commit'ler lokal olarak
serbest (PR squash'lanıyor).

- **Kısa kurallar:** [CONTRIBUTING.md](CONTRIBUTING.md)
- **Komut komut tam akış** (conflict/stash/kurtarma senaryoları dahil):
  [docs/runbooks/git-workflow.md](docs/runbooks/git-workflow.md)


## 10. Bilinen Teknik Borç / Notlar

- ~~auth-service iskelet~~ / ~~Gateway permitAll~~ — **2026-07-17'de kapandı:** auth-service
  SİLİNDİ (ADR-007), gateway BFF security chain'i çalıştırıyor (§4.3). Bu iki eski not tarihseldir.
- **Dev kimlik bilgileri yalnız yerel:** Keycloak bootstrap admin `admin/admin` + dev kullanıcı
  şifresi `crm-dev` — deterministik yerel fikstürlerdir, hiçbir gerçek ortamda kullanılamaz.
  `crm-bff` public client olduğu için repoda HİÇBİR client secret yok (ADR-006 §5); gerçek ortam =
  confidential client + vault (bkz. §9.2).
- **Makinede `mvn` artık PATH'te** (önceki not güncel değildi) — `docker` ise hâlâ PATH'te değil, Podman/Rancher kullanılıyor.
- **config-server native/classpath, sır yönetimi yok** — `config-repo` dosyaları düz metin olarak jar'a gömülüyor;
  gerçek bir sır girilecekse önce §9.4 sertleştirmesi gerekir (auth milestone'u bu yüzden config-repo'ya
  secret KOYMADI; Keycloak ayarları env-var tabanlı).
- **Compose 10 servis** (+ tek seferlik keycloak-init) — keycloak, account-service ve
  product-service dahil;
  8082/8083/8084/8085/8086 host'a yayınlanmaz (yalnız `crm-net`). Eski volume'larda
  `account_db`/`product_db`
  yoksa runbook'taki tek satırlık `CREATE DATABASE` çözümüne bakın (keycloak_db ile aynı durum).
- **PAYLAŞILAN KATALOG KURALLARI (ADR-002 — bağlayıcı):**
  - GNL_ST ve GNL_TP **merkezi, cross-service kataloglardır**; tek sahibi `lookup-service` (`lookup_db`).
  - customer-service'te (ve gelecekteki hiçbir serviste) **yerel GNL_ST/GNL_TP tablosu veya seed'i YOKTUR**;
    her servis kendi DB'sine katalog kopyalamaz.
  - Erişim yalnız onaylı API/istemci sınırından: `com.crm.customer.lookup` (`LookupCatalogClient` →
    `LookupCatalogService`); controller/repository doğrudan katalog çağırmaz.
  - customer_db yalnız **kararlı dış referans** saklar: kontrat-immutable merkezi ID'ler
    (`status_id`/`gender_id`/`party_type_id`) — **cross-database FK kullanılmaz**.
  - Katalog erişilemezse davranış açıktır: **yazma işlemleri 503 ile fail-closed** (kısmi kayıt kalmaz,
    bilinmeyen kod sessizce kabul edilmez); okuma/aktif-filtreleme yerel `status_id + deleted_date`
    üzerinden çalışmaya devam eder.
- ~~**customer-service: accountNumber/orderNumber araması kasıtlı 501**~~ — **KAPANDI 2026-08-05**
  (ADR-005 §Addendum): ikisi de sahibi olan servisin public ucundan çözülüyor, `account_db`/
  `order_db` okunmadan. `MSG-FEATURE-NOT-IMPLEMENTED` artık hiçbir serviste üretilmiyor.
- **product-service salt-okunur (2026-07-29)** — ürün yaratma/provizyon/sepet/sipariş YOK;
  `cust_acct_prod_invl`'e **yazma** hâlâ uygulanmadı (yalnız okuma ucu var); karakteristik
  tabloları endpoint'siz; teklif fiyatları analist onayı bekleyen fixture. Hiçbiri
  "yapıldı" diye iddia edilmiyor — bkz. §4.9 + §9.1b.
- **customer-service: `MSG-ADDR-IN-USE` kontrolü — Billing Account bacağı gerçek çağrıya çevrildi
  05.08.2026 (BUG-API-ADDR-04-03, bkz. §9.3)**; AC-ADDR-04-04'ün servis-adresi/product-service
  bacağı **hâlâ açık** — "tamamlandığı" o kapsam için iddia edilmiyor. Fatura hesabı pasifleştirme
  ve aktif ürün kontrolü **05.08.2026'da gerçek çağrıya çevrildi** (PR #29); erken
  `checkCustomerHasNoActiveProducts` guard'ı bilinçli no-op kaldı, ret bir katman derinde yakalanıyor.
- **Testcontainers kurulu** — entegrasyon testleri Docker gerektirir; Docker kapalıysa yalnız o test
  sınıfları düşer (birim testleri etkilenmez). Surefire `-Dapi.version=1.44` pin'i: Testcontainers 1.21.3'ün
  gömülü docker-java'sı Docker 29 motoruna eski API versiyonuyla ping atıyor (bkz. pom yorumları). Docker/
  Testcontainers keşif sorunları için ayrıntılı runbook: `docs/runbooks/testcontainers.md` ve
  `scripts/testcontainers-doctor.sh` (salt-okunur teşhis betiği).
- **Lombok `annotationProcessorPaths` düzeltmesi** artık customer-service + lookup-service + mernis-stub
  pom'larında (JDK 25 tuzağı, bkz. §5.9); kök `<pluginManagement>`'a taşımak hâlâ mantıklı bir iyileştirme.

---

## 11. AI Agent İçin Hızlı Başlangıç Notları

- Bu dosyayı ve `§5` (kararlar) + `§6` (WebMVC tuzağı) bölümlerini **kod önermeden önce** oku.
- **Bağlayıcı mimari kararlar `docs/architecture/adr/`'de** (ADR-001 agregat sınırı, ADR-002 merkezi
  GNL katalogları, ADR-003 kalıcı NATID tekilliği, ADR-004 Keycloak yönü [kısmen ADR-006/007 ile
  süperseded], ADR-005 müşteri liste+filtre kontratı, **ADR-006 Keycloak tek otorite + PKCE,
  ADR-007 gateway BFF + auth-service silme, ADR-008 çerez/CSRF politikası, ADR-009 zero-trust
  resource server + crm-user rolü, ADR-010 servisler-arası token stratejisi, ADR-011 USERS vs
  Keycloak kimliği**) — bunlarla çelişen eski metinler (bu dosyanın tarihsel bölümleri,
  use-case dokümanı, draw.io) geçersizdir. Açık çelişki kaydı: §9B + docs/requirements/document-delta.md.
- Güvenlikle ilgili değişiklikte: ROPC/Direct Grant ASLA; token'lar tarayıcıya ASLA; çerez işleme
  yalnız gateway'de; her yeni korumalı servis `crm-security-starter` + `crm.security.issuer` alır.
- Final gereksinimler `docs/source/requirements`'ta; `Final` adlı dosyalar eskileri ezer (CLAUDE.md).
- Gateway ile ilgili herhangi bir şey yaparken WebFlux örneği kopyalama — §6 tablosuna uy.
- Docker/Compose ile ilgili değişiklikte **root build context** kuralını (§5.2) koru.
- Yeni servis = kök POM `<modules>` + parent bağlama + root-context Dockerfile.
- Emin olmadığın bir Spring Cloud property'sini **resmi WebMVC dokümanından doğrula**, tahmin etme.
- Değişiklik yaptıkça bu dosyanın ilgili bölümünü ve §9 listesini güncelle.
