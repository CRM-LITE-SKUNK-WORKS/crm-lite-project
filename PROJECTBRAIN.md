# PROJECTBRAIN — CRM Lite

> **Amaç:** Bu dosya projenin **güncel durumunun tek doğ­ru kaynağıdır** (single source of truth).
> Hem projeye sonradan dönen geliştirici, hem de sıfırdan bağlam kuran bir AI agent bu dosyayı
> okuyarak "nerede kaldık, neden böyle yapıldı, sırada ne var" sorularını cevaplayabilmelidir.
>
> **Son güncelleme:** 2026-07-08
> **Bu dosyayı güncel tut:** Her anlamlı değişiklikten sonra ilgili bölümü ve "Sırada ne var" listesini güncelle.

---

## 1. Proje Özeti

CRM Lite — Spring Boot tabanlı bir **mikroservis monorepo**'su. Şu an altyapı çekirdeği
(config server + service discovery + API gateway) kurulu ve çalışır durumda; iş servisleri (auth) henüz iskelet halinde.

- **Dil / Runtime:** Java 25
- **Framework:** Spring Boot `4.1.0`, Spring Cloud `2025.1.2`
- **Build:** Maven (monorepo, kök parent POM ile)
- **Container:** Dockerfile'lar + `infra/docker-compose.yml` (Rancher Desktop **ve** Podman uyumlu)
- **Geliştirme ortamı:** Windows 11, IntelliJ IDEA. Makinede terminalde `mvn`/`docker` PATH'te
  kurulu DEĞİL — servisler IDE'den (Run) veya Podman/Rancher üzerinden çalıştırılıyor.

---

## 2. Mimari

```
                    ┌─────────────────────┐
   İstemci ───────► │   api-gateway :8080 │  (Spring Cloud Gateway - WebMVC)
                    └──────────┬──────────┘
                               │ lb:// (Eureka'dan servis bulur)
                               ▼
                    ┌─────────────────────┐        ┌──────────────────────┐
                    │  auth-service :8081 │        │ discovery-server:8761│
                    │  (JPA + JWT, İSKELET│◄──────►│  (Eureka Server)     │
                    │   Postgres gerekli) │  kayıt └──────────────────────┘
                    └─────────────────────┘

   Üç servis de açılışta ─────► ┌───────────────────────┐
   spring.config.import ile     │ config-server :8888   │
   kendi config'ini çeker       │ (native/classpath repo)│
                                 └───────────────────────┘
```

| Servis | Port | Rol | Durum |
|---|---|---|---|
| `config-server` | 8888 | Merkezi config (Spring Cloud Config Server, native/classpath) | ✅ Çalışıyor |
| `discovery-server` | 8761 | Eureka service registry | ✅ Çalışıyor |
| `api-gateway` | 8080 | API gateway (WebMVC), routing + security | ✅ Çalışıyor |
| `auth-service` | 8081 | Kimlik doğrulama / JWT | ⛔ İskelet — Postgres + kod eksik, ayağa kalkmıyor |

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
│   │               ├── api-gateway.yml
│   │               └── auth-service.yml
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
│   └── auth-service/
│       ├── pom.xml
│       ├── Dockerfile
│       └── src/main/
│           ├── java/com/crm/auth/
│           │   ├── AuthServiceApplication.java
│           │   ├── common/    (controller/dto/entity/repository/service — HEPSİ BOŞ İSKELET)
│           │   ├── login/     (controller/dto/entity/repository/service — HEPSİ BOŞ İSKELET)
│           │   ├── security/  (controller/dto/entity/repository/service — HEPSİ BOŞ İSKELET)
│           │   └── session/   (controller/dto/entity/repository/service — HEPSİ BOŞ İSKELET)
│           └── resources/
│               ├── application.yml                 # datasource BOŞ (doldurulmalı)
│               └── db/migration/.gitkeep           # Flyway migration YOK
└── infra/
    └── docker-compose.yml         # config-server + discovery-server + api-gateway (auth-service henüz yok)
```

---

## 4. Servislerin Güncel Durumu (detay)

### 4.0 config-server ✅
- `@EnableConfigServer`, port 8888.
- Profil `native`, kaynak `classpath:/config-repo/` — yani config dosyaları **git-backed bir dış repo değil**,
  bu servisin kendi `src/main/resources/config-repo/` klasöründe duruyor ve jar'a gömülüyor.
- Her istemci servis kendi adına göre bir dosya çeker (`discovery-server.yml`, `api-gateway.yml`, `auth-service.yml`).
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
- **Security:** `config/SecurityConfig.java` → servlet `SecurityFilterChain`, şu an `anyRequest().permitAll()` + `csrf().disable()`. Geçici; JWT gelince sıkılaştırılacak.
- **Route:** `spring.cloud.gateway.server.webmvc.routes` altında `/api/auth/**` → `lb://auth-service`.
- **Timeout:** `spring.http.client.connect-timeout: 2s`, `read-timeout: 5s` (WebMVC'nin doğru property'leri).
- **Eureka instance:** `prefer-ip-address: true`, `instance-id: ${spring.application.name}:${random.value}`.
- **Actuator:** `health, info, mappings` açık.
- **Not:** Route/timeout/eureka/actuator ayarlarının tamamı artık yerel `application.yml`'de değil,
  `config-server`'ın `config-repo/api-gateway.yml` dosyasında. Yerel dosyada sadece `spring.application.name`
  ve `spring.config.import` kaldı.
- **Doğrulanmış davranış:**
  - `GET /actuator/health` → `{"status":"UP"}` (401 yok, security düzgün).
  - `GET /actuator/mappings` → route `predicate: /api/auth/**` + `ProxyExchangeHandlerFunction` görünüyor (route yüklü).
  - `GET /api/auth/login` → **503** (route eşleşiyor, load-balancer auth-service'i arıyor ama ayakta değil — BEKLENEN doğru sonuç).

### 4.3 auth-service ⛔
- **Ayağa KALKMIYOR.** Başlatınca hata:
  `Failed to configure a DataSource: 'url' attribute is not specified...` / `Failed to determine a suitable driver class`.
- Sebep: `spring.datasource.url/username/password` **boş** — artık yerel `application.yml`'de değil,
  `config-server`'ın `config-repo/auth-service.yml` dosyasında boş duruyor; ve `db/migration` altında **hiç Flyway migration yok**.
- Java dosyalarının **tamamı boş iskelet** (paket + boş class). Hiç iş mantığı yazılmamış.
- `pom.xml`: web, data-jpa, security, validation, actuator, flyway (+ postgresql), lombok, jjwt (api/impl/jackson), config-client bağımlılıkları hazır.
- **Not:** Postgres kurulduğunda datasource/flyway değerleri doğrudan `config-repo/auth-service.yml`'e yazılmalı
  (önce yerel dosyaya geçici değer koyup sonra config-server'a taşımak yerine — bkz. §5.8 gerekçesi).

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

### 5.4 Gateway security: dependency kalır, geçici permitAll
- `spring-boot-starter-security` dependency yerinde; şimdilik her isteğe izin veren `SecurityFilterChain`.
- Gerekçe: JWT doğrulama mekanizması (auth-service) henüz yok. permitAll ile 401 duvarı kalkıp routing test edilebiliyor.
- İleride bu sınıfın içi JWT doğrulama filtresine dönüştürülecek (dependency zaten yerinde, geçiş kolay).

### 5.5 Gateway route/timeout/instance ayarları
- Route prefix'i **WebMVC**'ye özel: `spring.cloud.gateway.server.webmvc.routes` (bkz. §6).
- Timeout **WebMVC**'de `spring.http.client.*` ile (WebFlux'un `spring.cloud.gateway.httpclient.*`'ı ÇALIŞMAZ).
- `lb://` şeması için `spring-cloud-starter-loadbalancer` açıkça pom'a eklendi (eureka-client transitive getirse de niyet belgelensin diye).
- Eureka instance ayarları container uyumu için (IP tabanlı kayıt + benzersiz id).

### 5.6 CORS ertelendi
- Henüz frontend yok → CORS eklense de test edilemez. Frontend gelince gerçek origin ile eklenecek.

### 5.7 docker-compose vendor-neutral (Rancher + Podman)
- `version:` satırı yok (modern Compose Spec; ikisi de destekler). Healthcheck yok (JRE imajında curl yok
  + gateway health'i ileride security'ye takılabilir) → sadece `depends_on` sıralaması. Eureka client zaten retry ediyor.
- Container ağında servisler birbirini **servis adıyla** bulur; bu yüzden gateway'in Eureka adresi compose'da
  env ile `http://discovery-server:8761/eureka` olarak ezilir (application.yml'deki `localhost` sadece IDE içindir).

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

### 7.1 IDE (mevcut ana yöntem)
Sıra önemli (config-server artık ilk sırada):
1. **config-server** → `ConfigServerApplication` Run. Doğrula: `http://localhost:8888/discovery-server/default`
   (Config Server'ın REST API'si, `discovery-server.yml` içeriğini JSON olarak döner).
2. **discovery-server** → `DiscoveryServerApplication` Run. Doğrula: `http://localhost:8761`.
3. **api-gateway** → `ApiGatewayApplication` Run. Doğrula: Eureka'da `API-GATEWAY` görünür.
4. auth-service → **şu an çalıştırılamaz** (Postgres/kod eksik).

> IDE'de Maven projeleri görünmüyorsa: kök `pom.xml`'i "Add as Maven Project" / "Load Maven Project" ile ekle;
> dört modülü otomatik tanır.

### 7.2 Docker / Podman (infra/docker-compose.yml)
Repo kökünden:
```bash
# Rancher Desktop (dockerd):
docker compose -f infra/docker-compose.yml up --build

# Podman:
podman compose -f infra/docker-compose.yml up --build
# veya: podman-compose -f infra/docker-compose.yml up --build
```
İlk build birkaç dakika sürer (image + bağımlılık indirir). Durdurma: aynı komut `down` ile.
Not: compose şu an config-server + discovery-server + api-gateway içerir (auth-service henüz yok).

---

## 8. Doğrulama (Smoke Test)

config-server + discovery-server + api-gateway ayaktayken:
- `GET http://localhost:8888/discovery-server/default` → `discovery-server.yml` içeriği JSON olarak döner (config-server çalışıyor).
- `GET http://localhost:8888/api-gateway/default` → `api-gateway.yml` içeriği döner.
- `GET http://localhost:8761` → Eureka dashboard, `API-GATEWAY` kayıtlı.
- `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`.
- `GET http://localhost:8080/actuator/mappings` → `predicate: /api/auth/**` + `ProxyExchangeHandlerFunction`.
- `GET http://localhost:8080/api/auth/login` → **503** (auth-service ayakta değilken beklenen; route çalışıyor demek).

---

## 9. Sırada Ne Var (Roadmap / Öncelik)

### 9.1 Bir sonraki büyük adım — config-server KURULDU ✅
`config-server` artık ayakta (§4.0, §5.8): native profil + `classpath:/config-repo/`. Seçilen plan (A) uygulandı —
kalan tek büyük engel artık sadece **auth-service'in DB kurulumu**; datasource/flyway değerleri doğrudan
`config-repo/auth-service.yml`'e yazılacak (iki kez iş yapmaya gerek yok).

### 9.2 auth-service'i tamamlama (blokör işler)
- [ ] PostgreSQL sağla (Podman ile compose'a `postgres` servisi eklemek muhtemel yol).
- [ ] `config-repo/auth-service.yml`'deki datasource'u doldur (url/username/password).
- [ ] Flyway migration(lar)ı yaz (`src/main/resources/db/migration/V1__init.sql` ...). Şu an klasör boş.
- [ ] Boş iskelet sınıfları implement et (login/security/session/common: entity, repository, service, controller, dto).
- [ ] JWT üretimi/doğrulaması (jjwt bağımlılıkları hazır).
- [ ] auth-service'i `infra/docker-compose.yml`'e ekle (root-context Dockerfile'ı zaten hazır).

### 9.3 config-server sertleştirme (ileride)
- [ ] Şu an classpath/native — sırlar (DB şifresi, JWT secret) düz metin olarak jar'a gömülüyor. Gerçek ortam
  öncesi ya Jasypt/Spring Cloud Config encrypt-decrypt endpoint'i ya da git-backed + harici secret yönetimine geçiş değerlendirilmeli.
- [ ] `/actuator/refresh` / Spring Cloud Bus ile canlı config reload (şu an yok — değişiklik = rebuild+restart).

### 9.4 Gateway sıkılaştırma (auth-service hazır olunca)
- [ ] `SecurityConfig`'i permitAll'dan JWT doğrulamaya çevir.
- [ ] CORS ekle (frontend geldiğinde, gerçek origin ile).
- [ ] (İsteğe bağlı) resilience4j circuit breaker — WebMVC blocking model için downstream koruması.

### 9.5 Container/altyapı borçları
- [ ] Compose healthcheck'leri (imaja curl/health aracı eklemek gerekir; gateway health'i security'ye takılmasın).
- [ ] Eureka self-preservation'ı dev için kapatmak (`eureka.server.enable-self-preservation: false`) — opsiyonel.
- [ ] discovery-server ve gateway için graceful shutdown, gerekiyorsa auth için de.

---

## 10. Bilinen Teknik Borç / Notlar

- **auth-service tamamen iskelet** — çalıştırmayı deneme, Postgres+kod olmadan çökecektir (beklenen).
- **Gateway security şu an açık (permitAll)** — hiçbir kimlik doğrulama yapmıyor; production'a gitmeden JWT'ye çevrilmeli.
- **Makinede `mvn`/`docker` PATH'te yok** — komut satırından `mvn` çalışmaz; IDE veya Podman/Rancher kullanılıyor.
- **config-server native/classpath, sır yönetimi yok** — `config-repo` dosyaları düz metin olarak jar'a gömülüyor;
  gerçek bir DB şifresi/JWT secret girildiğinde bu düz metin git'e de gidecek demektir (bkz. §9.3).
- **Compose sadece 3 servis** — auth-service henüz eklenmedi (Postgres bağımlılığı olmadan eklemenin anlamı yok).

---

## 11. AI Agent İçin Hızlı Başlangıç Notları

- Bu dosyayı ve `§5` (kararlar) + `§6` (WebMVC tuzağı) bölümlerini **kod önermeden önce** oku.
- Gateway ile ilgili herhangi bir şey yaparken WebFlux örneği kopyalama — §6 tablosuna uy.
- Docker/Compose ile ilgili değişiklikte **root build context** kuralını (§5.2) koru.
- Yeni servis = kök POM `<modules>` + parent bağlama + root-context Dockerfile.
- Emin olmadığın bir Spring Cloud property'sini **resmi WebMVC dokümanından doğrula**, tahmin etme.
- Değişiklik yaptıkça bu dosyanın ilgili bölümünü ve §9 listesini güncelle.
