# config-server — Sonra Bakılacaklar

## 1. config-server hiçbir kimlik doğrulaması olmadan açık
`http://localhost:8888/customer-service/default` adresine ulaşan herkes o servisin tüm config'ini
düz metin JSON olarak görebiliyor. Şu an içerik yerel dev değerleri (crmlite/crmlite) olduğu için
düşük riskli, ama gerçek bir sır yazıldığı an bu endpoint bir sızıntı kapısına dönüşür. Auth
milestone'u bu yüzden config-repo'ya HİÇBİR sır koymadı (crm-bff public client, Keycloak ayarları
env-var — ADR-006); yine de config-server'a en azından basic auth eklenmesi gerekecek.

## 2. `optional:` öneki + merkezi config = sessiz yanlış yapılandırma riski
Üç serviste de `spring.config.import: "optional:configserver:..."` var. Config-server ayakta
değilse (ya da henüz tam açılmamışsa) servisler hata vermeden, config-server'dan gelecek ayarlar
olmadan başlıyor. Artık port, Eureka adresi gibi her şey config-server'a taşındığı için yerelde
fallback değer yok — config-server'a ulaşılamazsa örn. `discovery-server` ve `api-gateway` ikisi de
Spring Boot varsayılan portuna (8080) düşüp çakışabilir. `docker-compose.yml`'de healthcheck de
olmadığı için, Compose ile otomatik başlatmada bu risk daha yüksek (depends_on sadece container
başlatma sırasını garanti ediyor, config-server'ın gerçekten hazır olmasını değil).

## 3. ~~Gateway loglarındaki "Using generated security password" gürültüsü~~ (tarihsel)
Eski permitAll dönemine ait bir notdu; gateway artık gerçek bir BFF security chain'i çalıştırıyor
(ADR-007) ve kendi `SecurityFilterChain`'i olduğu için Boot'un ürettiği rastgele şifre devrede değil.
