# Frontend Test Konvansiyonları

FE-ADR-009 (`data-testid`) ve FE-ADR-012 (i18n) kurallarının günlük uygulaması.
Bağlayıcı karar ADR'lerde; bu dosya **nasıl yazılacağını** anlatır.

Son güncelleme: **2026-07-24**

---

## 0. Bu katman tamamen bizim eklediğimiz bir şey

> ⚠️ **Mock UI'da `data-testid` YOK.** Yedi ekran belgesinin tamamı çıkarılıp
> sayıldı: her birinde **0 adet** (FE-ADR-009 §Context). Yani hiçbir test
> seçicisi tasarımdan gelmiyor — **hepsini biz ekliyoruz.**

Bunun iki sonucu var:

1. Mock'a bakıp "seçici zaten var" diye varsayma; yok.
2. Sözlük sıfırdan bizim olduğu için **tek tip olmak zorunda.** Keycloak login
   teması bu konvansiyonu zaten kullanıyor (`login-username-input`,
   `login-password-toggle`, `login-locale-en`, `login-submit`…), dolayısıyla
   login'den başlayan bir uçtan uca senaryo **tek bir seçici dili** konuşur.

---

## 1. Test çerçevesi

| Bileşen | Sürüm | Not |
|---|---|---|
| Runner | **vitest 4.1.10** | `@angular/build:unit-test` builder üzerinden |
| DOM | **jsdom 28.1.0** | |
| Zone | **yok** | zone.js bağımlılık değil → CD **açıkça** tetiklenir |

```bash
npm test                  # izleme modu
npx ng test --watch=false # tek sefer (CI)
npm run check:conventions # data-testid + i18n denetimi (§8)
```

**Zoneless notu:** otomatik change detection yok. Şablon güncellemesi bekleyen
her testte `fixture.detectChanges()` **elle** çağrılır — dil değiştirme gibi
sinyal tetikli güncellemeler dahil.

---

## 2. `data-testid` isimlendirme kuralı

```
{feature}-{section}-{element}[-{variant}]
```

- **kebab-case**, **İngilizce**, **sabit** — asla çevrilmez, asla dile bağlı olmaz.
- Feature öneki zorunlu: `customer-search-…`, `customer-create-…`,
  `customer-detail-…`; uygulama kabuğu için `app-…`.
- Kısaltma yok (`btn`, `inp` değil → `button`, `input`).

| Doğru | Yanlış | Neden |
|---|---|---|
| `customer-search-submit-button` | `searchBtn` | camelCase + kısaltma |
| `customer-search-results-row-1001` | `customer-search-results-row-0` | dizi indeksi (§5) |
| `app-language-tr` | `dil-tr` | Türkçe/çeviriye bağlı |
| `customer-create-step-2` | `step2` | feature öneki yok |

---

## 3. Zorunlu eleman tipleri

Aşağıdakiler **yazıldığı anda** `data-testid` ile gelir. Sonradan test yazarken
eklenmez — FE-ADR-009 §1: *"`data-testid`i olmayan eleman eksik elemandır."*

| Tip | Örnek testid | Not |
|---|---|---|
| **Buton** | `customer-search-submit-button` | ikon buton dahil |
| **Input** | `customer-create-field-first-name-input` | her metin/sayı alanı |
| **Select** | `customer-create-field-city-select` | açılan panel: `-panel` |
| **Datepicker** | `customer-create-field-birth-date-picker` | tetikleyici + panel ayrı |
| **Link / navigasyon** | `app-logo-link`, `app-nav-b2c` | sidenav, sekme, sayfalama |
| **Tablo satırı** | `customer-search-results-row-1001` | **iş anahtarıyla** (§5) |
| **Form** | `customer-create-form` | form kökü |
| **Modal / dialog** | `customer-create-address-dialog` | kök + onay butonları |
| **Dil değiştirici** | `app-language-switcher`, `app-language-en/-tr` | grup + her seçenek |
| Durum yüzeyleri | `customer-search-empty-state`, `app-toast` | boş durum, hata bandı, alan hatası |
| Tablo kökü | `customer-search-results-table` | |

---

## 4. Sınırlar — `data-testid` ASLA

| ❌ Yasak | Neden |
|---|---|
| **CSS seçici olarak kullanılmaz** | Ne stylesheet'te, ne `@apply`'da, ne `[data-testid=…]` kuralında. Stil bağlanırsa testid'i yeniden adlandırmak **görsel regresyona** dönüşür; ikisi bağımsız hareket edebilmeli (FE-ADR-009 §4) |
| **İş mantığı okumaz** | `querySelector('[data-testid=…]')` component/servis içinde yok, dallanma yok, map anahtarı değil. Uygulama açısından **yalnız yazılır**, test açısından yalnız okunur (§5) |
| **Çeviriden türetilmez** | Etiketten üretilen bir id, dil değişince tüm testleri kırar — kuralın var oluş sebebi tam da bu bağlantıyı engellemek (§6) |
| **Prod build'de silinmez** | E2E, container'daki **production** artefaktına karşı koşar; yalnız dev'de var olan seçici hiçbir şeyi test etmez (§7) |
| **Erişilebilirliğin yerine geçmez** | `aria-label`, `role`, `aria-current`, `aria-invalid` ayrıca ve doğru verilir (§8) |

---

## 5. Dinamik elemanlar: iş anahtarı, asla indeks

```
✅ customer-search-results-row-1001     (customerNumber)
✅ customer-detail-address-4            (addressId)
❌ customer-search-results-row-0        (dizi indeksi)
```

Sıralama, sayfalama veya filtre değişince indeks kayar ve test **yanlış satıra**
karşı geçer. Backend zaten kalıcı iş anahtarları veriyor; onlar kullanılır.

---

## 6. i18n kuralı: kullanıcıya görünen hiçbir metin şablona yazılmaz

Her metin katalog anahtarından gelir (FE-ADR-012 §b):

```html
<!-- ✅ -->
<h1>{{ 'UI-SEARCH-TITLE' | t }}</h1>
<button [attr.aria-label]="'UI-COMMON-LOGOUT' | t" data-testid="app-logout-button">…</button>

<!-- ❌ -->
<h1>Search customer</h1>
<button aria-label="Log out">…</button>
```

Kapsam yalnız görünen yazı değil: **`title`, `aria-label`, `placeholder`, `alt`**
da kullanıcıya (ve ekran okuyucuya) ulaşır, onlar da bağlanır.

Kapsam **dışı** (çevrilmez): `data-testid`, `type`, `role`, `viewBox`,
CSS sınıfları — bunlar kullanıcıya görünmez.

**Testte:** metin iddiaları **render edilmiş** yazıya bakar, böylece DOM'a sızan
ham bir anahtar testi kırar. Tek dile çakılı iddia yazma; her iki dili de kontrol et.

---

## 7. Test yazım stili

Seçim **daima** `data-testid` üzerinden. CSS sınıfı, etiket sırası veya çevrilmiş
metinle seçmek — üçü de en sık değişen şeyler.

```ts
function byTestId(fixture: ComponentFixture<T>, id: string): HTMLElement | null {
  return (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
}

it('renders translated copy and never leaks a catalogue key into the DOM', () => {
  const text = byTestId(render(), 'customer-placeholder')?.textContent ?? '';
  expect(text).toContain('You are signed in');
  expect(text).not.toContain('UI-PLACEHOLDER');   // anahtar DOM'a sızarsa kırılır
});
```

Çalışan referans: `frontend/src/app/features/customer/customer-placeholder.spec.ts`.
Kabuğun gerçek testid seti somut örnektir: `app-header`, `app-logo-link`,
`app-language-switcher`, `app-language-en`, `app-language-tr`, `app-user`,
`app-username`, `app-sidenav`, `app-nav-b2c/-b2b/-menu/-leads`,
`app-logout-button`, `app-main`.

---

## 8. Zorlama mekanizması

```bash
npm run check:conventions
```

`frontend/scripts/check-conventions.mjs` iki şeyi denetler:

| Kural | Yakaladığı |
|---|---|
| **testid** | `<button>`, `<input>`, `<select>`, `<textarea>`, gerçekten gezinen `<a>`, ve `(click)` taşıyan **her** eleman — `data-testid` (veya `[attr.data-testid]`) yoksa |
| **i18n** | Şablonda doğrudan yazılmış metin düğümleri + literal `title` / `aria-label` / `placeholder` / `alt` değerleri |

`.html` dosyalarını **ve** component'lerin inline `template:` bloklarını tarar.
Yorumlar, `<svg>` alt ağaçları ve `@if/@for/@let` kontrol-akış başlıkları hariç
tutulur; `{{ … }}` bağlamaları metin sayılmaz.

**Neden ESLint kuralı değil?** FE-ADR-009 §Consequences bunu bilinçle reddetti:
genel bir kural etkileşimli elemanı dekoratif markup'tan ayırt edemez, false
positive üretir ve öngörülebilir sonuç `eslint-disable` yorumlarının yayılmasıdır
— ondan sonra kural hiçbir şey zorlamaz. Bu script o gerekçeyi iki şekilde
karşılıyor:

1. **Dar kapsamlı.** Yalnız etkileşimi tartışmasız olan markup'a bakar; dekoratif
   markup hiç incelenmez, dolayısıyla false-positive üretecek yüzey yoktur.
2. **Susturulamaz.** Satır içi bir istisna mekanizması **yoktur**, çünkü
   FE-ADR-009 §1 gereği çözüm her zaman attribute'u eklemektir — elemanı muaf
   tutmak değil.

Bu bir **konvansiyon denetçisi**, derleyici değil: şablonları metinsel okur. İki
kural için yeterli ve bağımlılık gerektirmiyor.

**Nihai zorlama yine de insan + E2E:** script bir güvenlik ağı; asıl mekanizma
PR review ve E2E süitinin kendisi — seçici yoksa test **yazılamaz**, bu da
yorumla susturulamayan doğal bir geri bildirimdir.

---

## 9. Bakım

Yeni bir eleman tipi veya isimlendirme kalıbı çıktığında **önce §2/§3'e satır
eklenir**, sonra kod yazılır. Kural değişirse FE-ADR-009 ile bu dosya aynı
commit'te güncellenir — ikisinin çelişmesi kuralın kendisini değersizleştirir.
