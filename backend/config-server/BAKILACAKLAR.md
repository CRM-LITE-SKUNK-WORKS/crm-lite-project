# config-server — Sonra Bakılacaklar

## 1. config-server hiçbir kimlik doğrulaması olmadan açık
`http://localhost:8888/auth-service/default` adresine ulaşan herkes o servisin tüm config'ini
düz metin JSON olarak görebiliyor. Şu an datasource alanları boş olduğu için zararsız, ama
`config-repo/auth-service.yml`'e gerçek bir Postgres şifresi ve ileride JWT secret'ı yazıldığı an,
bu endpoint bir sır sızıntı kapısına dönüşür. Config-server'a en azından basic auth eklenmesi gerekecek.

## 2. `optional:` öneki + merkezi config = sessiz yanlış yapılandırma riski
Üç serviste de `spring.config.import: "optional:configserver:..."` var. Config-server ayakta
değilse (ya da henüz tam açılmamışsa) servisler hata vermeden, config-server'dan gelecek ayarlar
olmadan başlıyor. Artık port, Eureka adresi gibi her şey config-server'a taşındığı için yerelde
fallback değer yok — config-server'a ulaşılamazsa örn. `discovery-server` ve `api-gateway` ikisi de
Spring Boot varsayılan portuna (8080) düşüp çakışabilir. `docker-compose.yml`'de healthcheck de
olmadığı için, Compose ile otomatik başlatmada bu risk daha yüksek (depends_on sadece container
başlatma sırasını garanti ediyor, config-server'ın gerçekten hazır olmasını değil).

## 3. Gateway loglarındaki "Using generated security password" gürültüsü
Zararsız ama kafa karıştırıcı: `spring-boot-starter-security` classpath'te olduğu için Spring Boot
otomatik bir `UserDetailsService` üretiyor, ama `SecurityConfig`'teki `permitAll()` zaten bu
authentication mekanizmasını devre dışı bıraktığı için üretilen şifre hiç kullanılmıyor.
