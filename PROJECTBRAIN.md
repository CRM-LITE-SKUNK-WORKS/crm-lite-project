# PROJECTBRAIN — CRM Lite

> **Amaç:** Bu dosya projenin **güncel durumunun tek doğ­ru kaynağıdır** (single source of truth).
> Hem projeye sonradan dönen geliştirici, hem de sıfırdan bağlam kuran bir AI agent bu dosyayı
> okuyarak "nerede kaldık, neden böyle yapıldı, sırada ne var" sorularını cevaplayabilmelidir.
>
> **Son güncelleme:** 2026-07-18 (**AUTH/SECURITY milestone'u uygulandı — ADR-006..011:**
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
   customer-service :8082 ── lookup-service :8083   (ikisi de zero-trust JWT
              │                                      resource server — ADR-009,
              └──► mernis-stub :8084 (token YOK,     crm-security-starter)
                   dış sistem simülasyonu, ADR-010)

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
| ~~`auth-service`~~ | ~~8081~~ | ~~Kimlik doğrulama~~ | 🗑️ **SİLİNDİ (2026-07-17, ADR-007)** — BFF gateway'de, kimlik Keycloak'ta; iskelet geri getirilmeyecek |

**Planlanan servisler (henüz YOK — analist/mimari onayı bekliyor; ayrıntı:
`docs/architecture/service-boundaries.md` yol haritası):**

| Planlanan | Muhtemel sahiplik | Durum |
|---|---|---|
| ~~auth/security milestone~~ | Keycloak tek otorite + gateway BFF + zero-trust resource server + JWT-sub audit (ADR-006..011). auth-service iskeleti SİLİNDİ | ✅ **UYGULANDI (2026-07-17)** |
| localization-service | FR-LANG merkezi etiket/mesaj kataloğu (varsayılan dil artık **İngilizce**, 16.07.2026). Backend zaten dil-bağımsız `messageKey` dönüyor | 🗓️ Planlı, başlanmadı |
| account-service | ACCT_TP + CUST_ACCT (fatura hesapları) | 🗓️ Planlı, sınır analist-final değil |
| product-service | PROD_SPEC/PROD_OFR/PROD/CMPG*/PROD_CATAL* — product+catalog kapsamı birleşebilir | 🗓️ Planlı, sınır analist-final değil |
| order-service | BSN_INTER, CUST_ORD, CUST_ORD_ITEM + satış orkestrasyonu (FR-SALE) | 🗓️ Planlı, sınır analist-final değil |

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
│   │       └── BearerTokenPropagationInterceptor.java   (opt-in, ADR-010)
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
    ├── keycloak/realm/crm-lite-realm.json   # ADR-006: realm import (client crm-bff, rol crm-user, dev kullanıcıları)
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
  Keycloak logout. E2E: `GatewayBffIntegrationTest` (7 test, gerçek Keycloak Testcontainers,
  commit'li realm import'la; token expiry/refresh dahil).
- **Zero-trust resource server'lar (ADR-009):** customer-service + lookup-service,
  `crm-security-starter` ile imza(JWKS)/issuer/audience(`crm-api`)/rol(`crm-user`) doğrular —
  gateway'i geçmiş olmak yetmez; doğrudan servis çağrısı da token ister (testli). Çerez asla
  parse edilmez; sadece `/actuator/health` anonim.
- **Servisler-arası (ADR-010):** customer→lookup kullanıcı token'ı taşır (sub korunur,
  `BearerTokenPropagationInterceptor`); customer→mernis **token TAŞIMAZ** (dış KPS simülasyonu).
  Kanıt: `OutboundBearerPropagationTest`.
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
  `GET /api/cities(/{id}/districts)`. Tam liste: docs/api/customer-service.md.
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
  (tüm join'ler to-one). `accountNumber`/`orderNumber` → hâlâ 501 (account/order domain'leri yok).
  KR-04 notu: UI varsayılanı 15 (15/30/50 Per Page) — API varsayılanı 20; açık kayıtlı fark, ADR-005.
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
  çekilir; her satırda `status_id=PASV` + `deleted_date/by` + `updated_date/by` (invariant). Fatura
  hesabı pasifleştirme + aktif ürün kontrolü hâlâ cross-service TODO (bilinçli, dokümante).
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
```

**Oturumlu doğrulama tarayıcıyla yapılır:** `http://localhost:8080/api/session/me` → Keycloak
login (`ayilmaz`/`crm-dev`) → oturum JSON'u; ardından aynı tarayıcıda
`http://localhost:8080/api/customers` ADR-005 browse listesini döner (tam detay kontratı,
`/api/customers/search` alias'ı YOK). **Tam kataloglar:** docs/api/customer-service.md,
docs/api/shared-lookup-service.md, docs/api/mernis-stub.md, **docs/api/authentication.md**
(login/session/CSRF/logout kontratı); runbook: docs/runbooks/local-development.md;
**Postman koleksiyonu:** docs/postman/.

---

## 9. Sırada Ne Var (Roadmap / Öncelik)

### 9.1 ~~KİMLİK DOĞRULAMA / GÜVENLİK~~ ✅ UYGULANDI (2026-07-17) — sıradaki adaylar
Müşteri agregatı ✅ (2026-07-11), ADR-005 liste kontratı ✅ (2026-07-16), **auth/security
milestone'u ✅ (2026-07-17, ADR-006..011 — detay §4.3)**. Milestone'un tasarım kararı netleşti:
auth-service iskeleti KALDIRILDI; BFF gateway'de, kimlik Keycloak'ta. **Sıradaki adaylar:**
account/product/order domain'leri, FR-LANG lokalizasyon (varsayılan dil İngilizce), frontend
(Angular — docs/api/authentication.md kontratına karşı) ve Keycloak login sayfası proje teması.
**Adres/iletişim için ayrı servis YOK ve PLANLANMIYOR** (ADR-001).

### 9.2 Auth milestone'undan kalan işler (implementasyon bitti, bunlar takip)
- [ ] **ADR-011 analist onayı** — workbook USERS tablosunun Keycloak lehine terk edilmesi.
- [ ] **Keycloak proje teması** — AC-AUTH-01 UI detayları (buton disable, maskeleme, 64 karakter,
  LBL-LANGUAGE) + TR/EN görsel bütünlük; şu an standart Keycloak sayfası + yerleşik i18n.
- [ ] **Gerçek ortam sertleştirmesi:** `crm-bff`'i confidential client + vault'lu secret'a çevir
  (ADR-006 §5), `crm.security.cookie-secure=true`, Spring Session/Redis ile session scale-out
  (ADR-007), gateway'e resilience4j (eski §9.5 maddesi).
- [ ] (Opsiyonel) Postman/CI araçları için gateway'e hibrit Bearer kabulü — şu an gateway yalnız
  oturum kabul ediyor; araçlar tarayıcı oturum çerezini kullanıyor (docs/postman/README.md).

### 9.3 customer-service — kalan bilinçli TODO'lar
- [x] ~~Adres/iletişim~~ — **TAMAMLANDI, aynı serviste** (ADR-001; ayrı address/contact-service YOK).
- [x] ~~gsmNumber araması 501~~ — **yerel implementasyon** (CNTC_MEDIUM artık customer_db'de).
- [x] ~~nationality_id tekillik çelişkisi~~ — **ADR-003 ile kapandı**: kalıcı global DB UNIQUE.
- [x] ~~Testcontainers yok~~ — **kuruldu**: entegrasyon testleri gerçek PostgreSQL container'ına karşı.
- [ ] **account/order domain'leri** kurulunca `accountNumber`/`orderNumber` aramasını 501'den gerçek
  entegrasyona çevir (KR-02).
- [ ] **product/account domain'leri** kurulunca `checkCustomerHasNoActiveProducts`'ı (AC-CUST-05-03) ve
  müşteri silmede fatura hesabı pasifleştirmeyi (AC-CUST-05-04'ün kalan kısmı) gerçek çağrıya çevir.
- [ ] `checkAddressIsNotInUse` (AC-ADDR-04-04, `MSG-ADDR-IN-USE`) — hesap/servis adresi kayıtları gelince.
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

## 9B. Açık Analist/Doküman Çelişkileri (sessizce çözülmedi — kayıt altında)

Tam liste + işlem kaydı: `docs/requirements/document-delta.md`. Özet:

1. **NATID "aktif" ifadesi:** use-case dokümanı (FR-CUST-03 alternatif adım 4.5) hâlâ "eşleşen
   **aktif** bir müşteri" diyor; FR AC-CUST-03-12 (nitelik yok) + ADR-003 (kalıcı global tekillik)
   geçerli. 16.07.2026 revizyonu bu çelişkiyi temizlemedi — analist tarafında açık.
2. **draw.io FR-CUST-01 "içinde-geçen" notu:** KR-01 kelime-başı eşleşmeyle çelişiyor; KR-01 geçerli.
3. **KR-04 varsayılan sayfa boyutu:** analist UI varsayılanı 15 (15/30/50); API varsayılanı 20
   (ADR-005 ekip kararı). Frontend `size` parametresini açıkça gönderecek; analistler API
   varsayılanının da 15 olmasını isterse tek satırlık değişiklik.
4. **Use-case FR-CUST-03'te iki adet "Adım 4.5"** — kaynak dokümanda editoryal hata; analiste
   bildirilecek.
5. **Workbook USERS tablosu (username/password_hash) vs Keycloak (ADR-011):** uygulama tarafında
   USERS/parola tablosu YOK; kimlik bilgisi sahibi Keycloak. Seed kullanıcı adları Keycloak dev
   kullanıcısı olarak yaşıyor (`mkaya` disabled). Analist onayı bekleniyor.
6. **FR-AUTH-01 "uygulama içi login formu" varsayımı:** kimlik bilgisi girişi Keycloak login
   sayfasında (ADR-006; ROPC yasak). AC-AUTH-01 UI detayları gelecekteki Keycloak proje temasına
   bağlanır (bkz. §9.2).

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
- **Compose 8 servis** — keycloak dahil; 8082/8083/8084 host'a yayınlanmaz (yalnız `crm-net`).
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
- **customer-service: accountNumber/orderNumber araması kasıtlı 501** — account/order domain'leri kurulana
  kadar (gsmNumber artık YEREL ve çalışıyor).
- **customer-service: `checkCustomerHasNoActiveProducts` + fatura hesabı pasifleştirme + `MSG-ADDR-IN-USE`
  kontrolü TODO/no-op** — ilgili domain'ler kurulunca gerçek çağrıya çevrilecek (bkz. §9.3). Bu kontrollerin
  "yapıldığı" HİÇBİR yerde iddia edilmiyor.
- **Testcontainers kurulu** — entegrasyon testleri Docker gerektirir; Docker kapalıysa yalnız o test
  sınıfları düşer (birim testleri etkilenmez). Surefire `-Dapi.version=1.44` pin'i: Testcontainers 1.21.3'ün
  gömülü docker-java'sı Docker 29 motoruna eski API versiyonuyla ping atıyor (bkz. pom yorumları).
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
