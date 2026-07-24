# `shared/ui` — EDS Bileşenleri Tasarım Notu

Sonraki fazın (a) parçası. **Kod değil, tasarım kararı.** Mock'un React
bileşenlerinin (`docs/frontend/mock-ui-analysis.md` §3.3'te bundle'dan çıkarılmış
imzalar) Angular karşılıkları.

Son güncelleme: **2026-07-24** · Bağlayıcı: FE-ADR-011 (bileşen kütüphanesi yok,
EDS token'ları), FE-ADR-007 (Reactive Forms), FE-ADR-009 (`data-testid`),
FE-ADR-012 (i18n)

---

## 0. Çeviride geçerli genel kurallar

| React (mock) | Angular karşılığı | Gerekçe |
|---|---|---|
| `props` | **signal `input()` / `output()`** | Zoneless CD (FE-ADR-006 §7); `@Input` dekoratörü kullanılmaz |
| `children` | `<ng-content>` | |
| `value` + `onChange` | **`ControlValueAccessor`** | Ekranlar Reactive Forms kullanıyor (FE-ADR-007); ayrı bir "controlled component" mekanizması kurulmaz |
| `className` / `style` | **taşınmaz** | Dışarıdan stil sızması FE-ADR-011'in token disiplinini deler; varyasyon `variant`/`size` ile yapılır |
| `aria-label` string'i | **`ariaLabelKey`** (katalog anahtarı) | Metin doğrudan yazılmaz (FE-ADR-012 §b); bileşen anahtarı alır, kendi çözer |
| — (mock'ta yok) | **`testId` input'u (zorunlu)** | Mock'ta `data-testid` **0 adet**; katman tamamen bizim (FE-ADR-009) |

**`testId` sözleşmesi:** her bileşen `testId` alır ve onu **etkileşimli host
elemanına** basar (buton `<button>`'a, input `<input>`'a). Bileşik bileşenler alt
parçalara türev ekler: `{testId}-input`, `{testId}-error`, `{testId}-panel`,
`{testId}-option-{value}`. Böylece ekran tek bir kök id verir, alt seçiciler
otomatik ve öngörülebilir olur.

**Ortak durum sözleşmesi:** `default · hover · focus-visible · error · disabled ·
readonly`. Focus **daima** `--eds-focus-ring-shadow` + `border-focus`; `outline:
none` tek başına asla kullanılmaz. `prefers-reduced-motion` altında geçişler
kapatılır (`styles.css` içinde global olarak kurulu).

---

## 1. Icon

| | |
|---|---|
| **Girdi** | `name` (zorunlu, birlik: 28 kullanılan ikon), `size = 20`, `ariaLabelKey?` |
| **Varyant** | yok |
| **Durum** | yok (saf sunum) |
| **Renk** | `currentColor` — rengi **daima ebeveyn** belirler, bileşen renk almaz |

**Erişilebilirlik:** `ariaLabelKey` verilmezse `aria-hidden="true"` + `focusable="false"`.
Verilirse `role="img"` + çözülmüş `aria-label`. **Anlam taşıyan ikon asla tek
başına kullanılmaz** — ya görünür etiketle ya da `ariaLabelKey` ile gelir.

**`data-testid`:** yok. Dekoratif; testler ikona değil, kapsayan butona bakar.

**Boyut:** yalnız `16 | 20 | 24` (4px ölçeği). Mock'un `22`si **20'ye yuvarlanır**
(FE-ADR-011 §f — token dışı piksel yazılmaz; §2.18'deki 11px→12px kararının aynısı).

**Uygulama notu:** SVG path'leri inline bir `Record<IconName, string>` haritasında;
`name` union tipi olduğu için **olmayan ikon derleme hatası** verir. Lucide paketi
bağımlılık olarak eklenmez — 28 ikon için tüm set gereksiz (FE-ADR-011).

---

## 2. Button

| | |
|---|---|
| **Girdi** | `variant = 'primary' \| 'secondary' \| 'danger'`, `size = 'md' \| 'sm'`, `iconLeading?`, `iconTrailing?`, `fullWidth = false`, `loading = false`, `disabled = false`, `type = 'button'`, `testId` |
| **Çıktı** | yok — native `click` host'tan doğal olarak yükselir |

`ghost` ve `lg` **yazılmaz**: mock'un hiçbir ekranında kullanılmıyor (§3.3).
Kullanan bir ekran çıkarsa o zaman eklenir.

| Durum | Davranış |
|---|---|
| default / hover / active | §3.4 varyant tablosu; geçiş `duration-100 ease-standard` |
| focus-visible | focus ring |
| `disabled` | `action-disabled-bg` + `action-disabled-text`, `cursor: not-allowed` |
| `loading` | spinner + `aria-busy="true"`; **`disabled` gibi davranır** ama `aria-disabled` ile — buton odakta kalır, ekran okuyucu durumu duyurur |

**Erişilebilirlik:** `type` **daima açık** (form içinde varsayılan `submit` sürprizi
olmasın). `fullWidth` yalnız düzen; anlam değiştirmez.

**Metin:** `<ng-content>` ile gelir; çağıran `{{ 'KEY' | t }}` geçirir. Bileşen
metni **kendi yazmaz**.

---

## 3. IconButton

| | |
|---|---|
| **Girdi** | `icon` (zorunlu), `ariaLabelKey` (**zorunlu**), `variant = 'ghost' \| 'danger-ghost'`, `size = 32`, `disabled`, `loading`, `testId` |

`subtle` ve `size=40` yazılmaz (ekranlarda kullanılmıyor).

**Erişilebilirlik — en kritik bileşen:** görünür metni olmadığı için
`ariaLabelKey` **derleme düzeyinde zorunlu** (opsiyonel değil). Mock'un mevcut
etiketleri korunur: `Edit customer info`, `Delete customer`, `Edit address`,
`Delete address`, `Previous page`, `Next page`, `Dismiss notification`, `Close`.
Tooltip varsa `aria-describedby` ile bağlanır — `aria-label`in yerine geçmez.

---

## 4. FormField

Düzen sözleşmesi (§3.8). **Kontrolü sarar, kontrolü bilmez.**

| | |
|---|---|
| **Girdi** | `labelKey` (zorunlu), `required = false`, `optional = false`, `hintKey?`, `helperTextKey?`, `errorKey?`, `controlId` (zorunlu), `testId` |
| **İçerik** | `<ng-content>` — TextInput / Select / DatePicker |

> 🔑 **Zıplamayan hata satırı.** Hata alanı **her zaman** render edilir,
> `min-height: 16px` (caption satır yüksekliği). Hata belirince/kaybolunca form
> yüksekliği **değişmez**. Mock'un davranışı budur ve korunur (§3.8).

**Erişilebilirlik:**
- `<label for={controlId}>` — `controlId` zorunlu, böylece bağ kopamaz.
- Hata varken kontrol `aria-invalid="true"` + `aria-describedby={controlId}-error`.
- Hata satırı `role="alert"` **değil**, `aria-live="polite"` — form gönderiminde
  aynı anda beliren 5 hata ekran okuyucuyu boğmaz.
- `required` yıldızı `aria-hidden`; zorunluluk kontrole `required` ile bildirilir.

**Alan hatası kaynağı:** `errorKey` bir **katalog anahtarıdır**. Backend'den gelen
alan hataları `core/http`'nin ürettiği `ApiFieldError.messageKey`'i buraya geçer —
ham backend metni **asla** (FE-ADR-008 §2).

**`data-testid`:** kök `{testId}`, hata satırı `{testId}-error`.

---

## 5. TextInput

| | |
|---|---|
| **Girdi** | `size = 'md'`, `iconLeading?`, `iconTrailing?`, `clearable = false`, `maxLength?`, `showCount = false`, `error = false`, `disabled`, `readonly`, `placeholderKey?`, `inputMode?`, `testId` |
| **Çıktı** | `cleared` (temizle butonu) |
| **Form** | **`ControlValueAccessor`** — `formControlName` ile kullanılır |

| Durum | Görsel (§3.6) |
|---|---|
| default | `bg-surface`, `border-input` |
| hover | `border-hover` |
| focus | `border-focus` + focus ring |
| error | `border-error` |
| disabled | `bg-disabled`, kenarlık şeffaf, `not-allowed` |
| readonly | `bg-sunken`, kenarlık şeffaf |

**Erişilebilirlik:** `id` dışarıdan (FormField'ın `controlId`'si). `clearable`
butonu **gerçek `<button type="button">`** ve kendi `aria-label`ını alır
(`{testId}-clear`), süsleme `<span>` değil. `showCount` sayacı `aria-live` değil
(her tuşta duyurmak gürültü).

**`data-testid`:** `{testId}` → `<input>`, `{testId}-clear` → temizle butonu.

---

## 6. Select

Native `<select>` **değil**, custom panel (§3.7) — mock'un görünümü ve arama
yeteneği native ile karşılanamıyor.

| | |
|---|---|
| **Girdi** | `options: ReadonlyArray<{ value: T; labelKey?: string; label?: string }>`, `placeholderKey?`, `searchable = false`, `clearable = false`, `disabled`, `error`, `loading = false`, `size = 'md' \| 'sm'`, `testId` |
| **Form** | `ControlValueAccessor` |

> **`labelKey` vs `label` ikiliği bilinçli.** Sabit seçenekler (cinsiyet, sayfa
> boyutu) katalog anahtarı taşır; **backend'den gelen** referans veriler
> (il/ilçe — `GET /api/cities`) zaten sunucudan gelen metni taşır ve çevrilmez.
> İkisini tek alana sıkıştırmak, backend verisini katalogda arayan bir hataya
> davetiye çıkarırdı.

**Erişilebilirlik (en zahmetli kısım):** `role="combobox"` + `aria-expanded` +
`aria-controls`; panel `role="listbox"`, seçenekler `role="option"` +
`aria-selected`. Klavye: `↑ ↓` gezinme, `Enter` seç, `Esc` kapat, `Home/End`,
harfle atlama. Açıkken odak **panelde tuzaklanır**, kapanınca tetikleyiciye döner.
`aria-activedescendant` ile aktif seçenek bildirilir.

**`data-testid`:** `{testId}` tetikleyici, `{testId}-panel`, `{testId}-option-{value}`
(indeks değil **değer** — FE-ADR-009 §3).

---

## 7. DatePicker — ayrı ele alınıyor (en karmaşık)

Diğer altısından **kasten sonraya** bırakılıyor: tek başına en fazla
erişilebilirlik ve yerelleştirme yüzeyine sahip bileşen, ve Customer Search'te
**kullanılmıyor** (yalnız Create/Detail'de doğum tarihi). Golden path'i bloke
etmez.

**Çözülmesi gereken dört mesele:**

1. **Biçim ikiliği.** Gösterim `dd.MM.yyyy` (sabit — FE-ADR-012 §e istisnası,
   `scope-and-conflicts §4.10`), taşıma **ISO `yyyy-MM-dd`** (§2.6). Bileşen bu
   dönüşümü **içeride** yapar; forma daima ISO verir. Ekranların tarih
   biçimlendirmesiyle uğraşması yasak.
2. **Takvim paneli yerelleştirmesi.** Ay ve gün adları TR/EN değişmeli, haftanın
   ilk günü **Pazartesi** (TR takvim alışkanlığı). `Intl.DateTimeFormat` ile —
   katalogda 12 ay + 7 gün anahtarı tutmak gereksiz tekrar olur.
3. **Erişilebilirlik.** Panel `role="dialog"`, ızgara `role="grid"`,
   günler `role="gridcell"` + `aria-selected`; `↑↓←→` gün/hafta, `PageUp/Down`
   ay, `Home/End` hafta başı/sonu, `Esc` kapat. Seçili gün `aria-current="date"`.
   Odak tuzağı + kapanışta tetikleyiciye dönüş.
4. **Elle yazma.** Mock `placeholder="DD.MM.YYYY"` gösteriyor → alan yazılabilir.
   Geçersiz/eksik giriş **yazarken değil, blur'da** hataya dönüşür.

**Girdi taslağı:** `min?`, `max?` (ISO), `disabled`, `error`, `placeholderKey`,
`testId`. `max` Customer Search dışında **"bugün − 18 yıl"** olarak kullanılır
(KR: yaş ≥ 18) — ama bu **UX kolaylığı**, otorite backend (FE-ADR-007).

**`data-testid`:** `{testId}` (input), `{testId}-trigger`, `{testId}-panel`,
`{testId}-day-{yyyy-MM-dd}`.

**Açık soru:** native `<input type="date">` yeterli mi? Tarayıcı takvimi
"bedava" erişilebilirlik ve yerelleştirme verir, ama görünümü EDS token'larıyla
**stillenemez** ve mock'un panel tasarımından farklıdır. Öneri: mock görünümü
şart değilse native, şartsa custom. **Analist/tasarım kararı bekliyor.**

---

## 8. Yazım sırası (öneri)

`Icon` → `Button` → `IconButton` → `FormField` → `TextInput` → `Select` → *(sonra)* `DatePicker`

Icon ve FormField diğerlerinin içinde geçtiği için önce; DatePicker en sonda ve
golden path'ten sonra. Her bileşen **yazıldığı commit'te** kendi spec'iyle gelir
(render + klavye + `data-testid` iddiaları — `docs/frontend/testing-conventions.md` §7).
