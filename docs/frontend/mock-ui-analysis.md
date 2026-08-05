# Mock UI Analiz Raporu — Etiya CRM Lite

Kaynak: `docs/source/mock-ui/Guncel_Etiya_CRM_Lite_Full_App.html`
(self-extracting bundle, React 18 tabanlı prototip)

> **Bu dosya, Angular geliştirmesi için tasarımın TEK REFERANS KAYNAĞIDIR.**
> Renk, ölçü, boşluk, bileşen davranışı veya ekran düzeni ile ilgili her karar
> buradan alınır. Mock HTML'i tekrar açıp yorumlamak yerine bu dosya kullanılır.
> Buradaki bir değer yanlışsa önce bu dosya düzeltilir, sonra kod yazılır.

---

## 0. Doğrulama Kaydı (23.07.2026)

Bu rapor mock'un **açılıp çıkarılmasıyla** doğrulandı. Bundle yapısı:

| Katman | İçerik |
|---|---|
| `__bundler/manifest` | 21 asset (base64 + gzip): 13 woff2 font, React/ReactDOM UMD, DC runtime, ikon shim, EDS bundle, 7 ekran HTML'i |
| `__bundler/template` | Uygulama kabuğu (`Full App`) — token CSS'leri satır içi gömülü |
| `__bundler/ext_resources` | Her ekran ayrı bir `*.dc.html` dokümanı olarak paketlenmiş |

Çıkarılan ekran dosyaları ve boyutları:

| Ekran dosyası | Satır | Boyut |
|---|---|---|
| `Login v2.dc.html` | 159 | 10.6 KB |
| `Customer Search.dc.html` | 570 | 39.9 KB |
| `Create Customer.dc.html` | 628 | 46.5 KB |
| `Customer Info v2.dc.html` | 1424 | 115.9 KB |
| `Offer Selection.dc.html` | 661 | 52.4 KB |
| `Product Configuration.dc.html` | 492 | 35.3 KB |
| `Submit Order.dc.html` | 338 | 24.7 KB |

### ⚠️ Önceki analizdeki hatalar (düzeltildi)

Önceki sürüm token değerlerini **CSS satır içi fallback'lerinden** okumuştu
(`var(--eds-space-10,48px)` gibi). Mock'un satır içi fallback'lerinin bir kısmı
gerçek token değeriyle **uyuşmuyor**. Aşağıdaki tablo hem düzeltmeyi hem de
mock'taki hatalı fallback'i gösterir — Angular'a çevirirken **token dosyasındaki
değer** esastır, fallback değil.

| Token | Önceki analiz (hatalı) | **Gerçek değer** | Not |
|---|---|---|---|
| `--eds-space-10` | 48px | **40px** | `--eds-space-12` = 48px (analizde hiç yoktu) |
| `--eds-ease-standard` | `cubic-bezier(.4,0,.2,1)` | **`cubic-bezier(0.2,0,0,1)`** | mock fallback'i yanlış |
| `--eds-ease-enter` | `cubic-bezier(0,0,0.2,1)` | **`cubic-bezier(0,0,0.15,1)`** | mock fallback'i yanlış |
| `--eds-font-mono` | `monospace` | **`'JetBrains Mono', ui-monospace, …`** | 6 woff2 varyantı gömülü |
| `--eds-color-bg-selected` | `#FEF2E6` / `#FEF6EE` | **`#FEF6EE`** (orange-50) | `#FEF2E6` diye bir değer yok |
| `--eds-color-text-disabled` | `#9C9CBC` / `#A6A6C2` | **`#9C9CBC`** (ink-400) | `#A6A6C2` diye bir değer yok |
| `--eds-color-feedback-warning-icon` | `#B45309` | **`#CA8A04`** (yellow-600) | |
| `--eds-color-feedback-info-icon` | `#2B6CB0` | **`#2563EB`** (blue-600) | |
| `--eds-color-feedback-warning-text` | `#92400E` | **`#854D0E`** (yellow-800) | |
| `--eds-color-text-danger` | `#B91C1C` / `#C0362C` | **`#B91C1C`** (red-700) | `#C0362C` diye bir değer yok |
| `--eds-type-title-size` | "24px" | **BÖYLE BİR TOKEN YOK** | Doğrusu `--eds-type-h1-size: 24px`. Login v2 mock'u bu tanımsız token'ı kullanıyor (fallback'e düşüyor) — Angular'da `h1` kullanılacak |
| `--eds-color-border-strong` | `#C9C9DE` | **BÖYLE BİR TOKEN YOK** | Uydurma |
| `--eds-color-text-link` / `-link-hover` | `#B85C0D` / `#93470A` | **BÖYLE BİR TOKEN YOK** | Link rengi için `--eds-color-text-brand` kullanılıyor |

Ayrıca eksik olan ve aşağıda eklenen bölümler: focus ring token'ları, elevation
0–3, z-index katmanları, breakpoint'ler, `control-height-sm/lg`, tablo satır
yükseklikleri, `--eds-radius-full`, tip ölçeğinin tamamı (display/h1/h3/label/code),
duration token'ları, reduced-motion sözleşmesi, `.eds-tabular-nums`,
`color-scheme: light only`.

### Bileşen/ekran seviyesinde düzeltmeler

| Konu | Önceki analiz | **Gerçek durum** |
|---|---|---|
| Customer Search filtre alanları | "ID number, Customer ID, First name, Second name, Last name, Role" | **ID number, Customer ID, Account number, GSM number, Name, Last name, Order number** — "Second name" ve "Role" **filtre değil, tablo kolonu** |
| Create Customer | Düz tek sayfalık form | **3 adımlı wizard** (Demographic → Address → Contact) + adres modal'ı + silme onay modal'ı |
| Per page seçenekleri | "15/30/50 mi?" | **15 / 25 / 50, varsayılan 15** (Customer Search); hesap tablosunda 4 / 8 / 12 |
| Tasarım sistemi bileşen sayısı | "8 bileşen" | DS **26 bileşen** tanımlıyor; mock ekranları bunlardan **8'ini** kullanıyor |
| `PasswordInput` | "Sadece login mock'unda" | Login **ve** Product Configuration'da (şifre tipli ürün karakteristiği) |
| Bileşen kullanım sayıları | 65/64/56/46/21/17/2/2 | ✅ **Doğru** — teyit edildi |
| `data-testid` sayısı | 0 | ✅ **Doğru** — 7 ekranda da 0 |

---

## 1. Tasarım Sistemi

Mock, **"Etiya EDS Lite Design System"** (namespace `EtiyaEDSLiteDesignSystem_d00eff`,
bundle formatı 4) üzerine kurulu. Token dosyaları:

`tokens/colors.css`, `tokens/typography.css`, `tokens/spacing.css`, `tokens/radius.css`,
`tokens/elevation.css`, `tokens/motion.css`, `tokens/layout.css`, `tokens/base.css`,
`styles.css` (import listesi)

Renk sistemi **iki katmanlı**:
- **Primitives** — ham palet (`--eds-orange-*`, `--eds-ink-*`, `--eds-green/red/yellow/blue-*`).
  Token dosyasındaki kural: *"bileşenler bunları asla doğrudan kullanmaz."*
- **Semantics** — bileşenlerin tükettiği katman (`--eds-color-*`).

**Angular kuralı:** SCSS/Tailwind temasında iki katman da birebir kurulur, ama
bileşen kodunda **sadece semantic token** kullanılır.

---

## 2. Tasarım Token'ları (doğrulanmış gerçek değerler)

### 2.1 Primitives — Etiya Orange

| Token | Değer |
|---|---|
| `--eds-orange-50` | `#FEF6EE` |
| `--eds-orange-100` | `#FDEBDA` |
| `--eds-orange-200` | `#FCDABC` |
| `--eds-orange-300` | `#F9B479` |
| `--eds-orange-400` | `#F79B4D` |
| `--eds-orange-500` | `#F58220` ← logo turuncusu |
| `--eds-orange-600` | `#DB7013` |
| `--eds-orange-700` | `#B85C0D` |
| `--eds-orange-800` | `#8F4709` |
| `--eds-orange-900` | `#6B3507` |

### 2.2 Primitives — Etiya Ink (lacivert tonlu nötr)

| Token | Değer |
|---|---|
| `--eds-ink-50` | `#F7F7FB` |
| `--eds-ink-100` | `#EFEFF6` |
| `--eds-ink-200` | `#DEDEEB` |
| `--eds-ink-300` | `#C6C6DB` |
| `--eds-ink-400` | `#9C9CBC` |
| `--eds-ink-500` | `#6E6E96` |
| `--eds-ink-600` | `#57577E` |
| `--eds-ink-700` | `#414166` |
| `--eds-ink-800` | `#313152` |
| `--eds-ink-900` | `#242441` ← Etiya Navy |
| `--eds-white` | `#FFFFFF` |

### 2.3 Primitives — Geri bildirim aileleri

| Aile | 50 | 200 | 600 | 700 / 800 |
|---|---|---|---|---|
| green | `#F0FDF4` | `#BBF7D0` | `#16A34A` | `#15803D` (700) |
| red | `#FEF2F2` | `#FECACA` | `#DC2626` | `#B91C1C` (700) |
| yellow | `#FEFCE8` | `#FDE68A` | `#CA8A04` | `#854D0E` (800) |
| blue | `#EFF6FF` | `#BFDBFE` | `#2563EB` | `#1D4ED8` (700) |

### 2.4 Semantic — Zemin

| Token | Eşleşme | Değer |
|---|---|---|
| `--eds-color-bg-page` | ink-50 | `#F7F7FB` |
| `--eds-color-bg-surface` | white | `#FFFFFF` |
| `--eds-color-bg-surface-sunken` | ink-100 | `#EFEFF6` |
| `--eds-color-bg-surface-hover` | ink-50 | `#F7F7FB` |
| `--eds-color-bg-selected` | orange-50 | `#FEF6EE` |
| `--eds-color-bg-inverse` | ink-900 | `#242441` |
| `--eds-color-bg-overlay` | — | `rgba(36,36,65,.50)` |
| `--eds-color-bg-disabled` | ink-100 | `#EFEFF6` |

### 2.5 Semantic — Metin

| Token | Eşleşme | Değer |
|---|---|---|
| `--eds-color-text-primary` | ink-900 | `#242441` |
| `--eds-color-text-secondary` | ink-600 | `#57577E` |
| `--eds-color-text-tertiary` | ink-500 | `#6E6E96` |
| `--eds-color-text-placeholder` | ink-400 | `#9C9CBC` |
| `--eds-color-text-disabled` | ink-400 | `#9C9CBC` |
| `--eds-color-text-inverse` | white | `#FFFFFF` |
| `--eds-color-text-brand` | orange-700 | `#B85C0D` |
| `--eds-color-text-on-brand` | ink-900 | `#242441` |
| `--eds-color-text-success` | green-700 | `#15803D` |
| `--eds-color-text-danger` | red-700 | `#B91C1C` |
| `--eds-color-text-warning` | yellow-800 | `#854D0E` |
| `--eds-color-text-info` | blue-700 | `#1D4ED8` |

> 🔒 **Bağlayıcı kural (token dosyasında yazılı):** turuncu zemin üzerine **asla
> beyaz metin** yazılmaz — `--eds-color-text-on-brand` daima lacivert (`#242441`).

### 2.6 Semantic — Kenarlık ve focus

| Token | Eşleşme | Değer |
|---|---|---|
| `--eds-color-border-default` | ink-200 | `#DEDEEB` |
| `--eds-color-border-input` | ink-300 | `#C6C6DB` |
| `--eds-color-border-hover` | ink-400 | `#9C9CBC` |
| `--eds-color-border-focus` | orange-500 | `#F58220` |
| `--eds-color-border-error` | red-600 | `#DC2626` |
| `--eds-color-border-selected` | orange-500 | `#F58220` |
| `--eds-focus-ring-shadow` | — | `0 0 0 3px var(--eds-orange-200)` |
| `--eds-focus-ring-offset-shadow` | — | `0 0 0 2px var(--eds-color-bg-surface), 0 0 0 4px var(--eds-orange-500)` |

**Focus kuralı:** form kontrolleri `box-shadow` halkası kullanır
(`--eds-focus-ring-shadow`); buton/link/menü gibi form dışı etkileşimler
`base.css`'teki `:focus-visible { outline: 2px solid var(--eds-color-border-focus);
outline-offset: 2px; }` kuralına düşer. Button/IconButton ise `:focus-visible`'da
`--eds-focus-ring-offset-shadow` kullanır.

### 2.7 Semantic — Aksiyon (dolgular)

| Token | Değer |
|---|---|
| `--eds-color-action-primary-bg` | `#F58220` |
| `--eds-color-action-primary-bg-hover` | `#DB7013` |
| `--eds-color-action-primary-bg-active` | `#B85C0D` |
| `--eds-color-action-primary-text` | `#242441` |
| `--eds-color-action-secondary-bg` | `#FFFFFF` |
| `--eds-color-action-secondary-border` | `#C6C6DB` |
| `--eds-color-action-secondary-text` | `#313152` |
| `--eds-color-action-ghost-hover-bg` | `#EFEFF6` |
| `--eds-color-action-danger-bg` | `#DC2626` |
| `--eds-color-action-danger-bg-hover` | `#B91C1C` |
| `--eds-color-action-danger-text` | `#FFFFFF` |
| `--eds-color-action-disabled-bg` | `#EFEFF6` |
| `--eds-color-action-disabled-text` | `#9C9CBC` |

### 2.8 Semantic — Feedback yüzeyleri (bg / border / icon / text)

| Aile | bg | border | icon | text |
|---|---|---|---|---|
| success | `#F0FDF4` | `#BBF7D0` | `#16A34A` | `#15803D` |
| danger | `#FEF2F2` | `#FECACA` | `#DC2626` | `#B91C1C` |
| warning | `#FEFCE8` | `#FDE68A` | `#CA8A04` | `#854D0E` |
| info | `#EFF6FF` | `#BFDBFE` | `#2563EB` | `#1D4ED8` |

### 2.9 Semantic — Durum rozetleri (bg / dot / text)

| Aile | bg | dot | text |
|---|---|---|---|
| success | `#F0FDF4` | `#16A34A` | `#15803D` |
| warning | `#FEFCE8` | `#CA8A04` | `#854D0E` |
| info | `#EFF6FF` | `#2563EB` | `#1D4ED8` |
| neutral | `#EFEFF6` | `#6E6E96` | `#57577E` |
| danger | `#FEF2F2` | `#DC2626` | `#B91C1C` |

Token dosyasındaki not: *"SRS statülerini bunlara eşleyin."*

### 2.10 Boşluk (4px taban; ara değer yok)

| Token | Değer |
|---|---|
| `--eds-space-1` | 4px |
| `--eds-space-2` | 8px |
| `--eds-space-3` | 12px |
| `--eds-space-4` | 16px |
| `--eds-space-5` | 20px |
| `--eds-space-6` | 24px |
| `--eds-space-8` | 32px |
| `--eds-space-10` | **40px** |
| `--eds-space-12` | 48px |

### 2.11 Yarıçap

| Token | Değer | Kullanım |
|---|---|---|
| `--eds-radius-sm` | 4px | checkbox, tag, badge |
| `--eds-radius-md` | 6px | button, input, select (varsayılan) |
| `--eds-radius-lg` | 8px | card, modal, popover, toast |
| `--eds-radius-full` | 999px | avatar, status dot, switch |

Tavan `radius-lg`: hiçbir kart/modal/popover 8px'i aşmaz.

### 2.12 Tipografi

Font: **Inter** (400/500/600 — 700+ **asla kullanılmaz**, hiyerarşi ağırlıkla
değil boyut/boşlukla kurulur). Mono: **JetBrains Mono** (teknik/kod değerleri).

| Token | Değer |
|---|---|
| `--eds-font-sans` | `'Inter', -apple-system, "Segoe UI", Roboto, sans-serif` |
| `--eds-font-mono` | `'JetBrains Mono', ui-monospace, "SFMono-Regular", Menlo, monospace` |
| `--eds-weight-regular` | 400 |
| `--eds-weight-medium` | 500 |
| `--eds-weight-semibold` | 600 |

Tip ölçeği (`--eds-type-{stil}-size / -line / -weight`):

| Stil | size | line | weight |
|---|---|---|---|
| display | 32px | 40px | 600 |
| h1 | 24px | 32px | 600 |
| h2 | 20px | 28px | 600 |
| h3 | 16px | 24px | 600 |
| body-lg | 16px | 24px | 400 |
| body | 14px | 20px | 400 |
| body-sm | 13px | 18px | 400 |
| caption | 12px | 16px | 400 |
| label | 12px | 16px | 500, `tracking: 0.4px` |
| code | 13px | 20px | 400 |

**Font dosyaları:** 13 woff2 gömülü — Inter 400/500/600 (7 subset: latin,
latin-ext, greek, greek-ext, cyrillic, cyrillic-ext, vietnamese) + JetBrains Mono
400/500 (6 subset). Türkçe karakterler `latin-ext` subsetinden gelir — Angular'da
**latin-ext subseti mutlaka yüklenmelidir**.

### 2.13 Elevation

| Token | Değer | Kullanım |
|---|---|---|
| `--eds-elevation-0` | `none` | card, table — varsayılan düz görünüm (gölge değil, kenarlık) |
| `--eds-elevation-1` | `0 1px 2px rgba(36,36,65,.06)` | sticky header alt çizgisi |
| `--eds-elevation-2` | `0 2px 8px rgba(36,36,65,.10)` | dropdown, popover, tooltip, datepicker |
| `--eds-elevation-3` | `0 8px 24px rgba(36,36,65,.14)` | modal, drawer |
| `--eds-elevation-4` | `0 12px 32px rgba(36,36,65,.18)` | toast |

Gölge taban rengi **daima Ink 900**. **Kartlar hover'da yükselmez** — etkileşim
kenarlık rengiyle anlatılır, gölge hareketiyle değil.

### 2.14 Motion

| Token | Değer | Kullanım |
|---|---|---|
| `--eds-duration-100` | 100ms | hover / focus / renk / press |
| `--eds-duration-150` | 150ms | input durumları, tooltip, checkbox/radio/switch, çıkışlar |
| `--eds-duration-200` | 200ms | dropdown/menu/popover/tabs, sayfa içeriği girişi |
| `--eds-duration-300` | 300ms | modal, accordion, açılan satır, toast, drawer |
| `--eds-duration-400` | 400ms | büyük sayfa geçişleri (nadir) |
| `--eds-duration-spin` | 900ms | spinner döngüsü |
| `--eds-duration-shimmer` | 1800ms | skeleton |
| `--eds-duration-indeterminate` | 1200ms | belirsiz progress |
| `--eds-ease-standard` | `cubic-bezier(0.2, 0, 0, 1)` | genel amaçlı |
| `--eds-ease-enter` | `cubic-bezier(0, 0, 0.15, 1)` | girişler (yavaşlayarak) |
| `--eds-ease-exit` | `cubic-bezier(0.3, 0, 1, 1)` | çıkışlar (hızlanarak; eşleşen girişten daima kısa) |
| `--eds-ease-linear` | `linear` | sadece spinner/skeleton/progress döngüleri |

🚫 **Sistem genelinde yasak:** bounce/spring/overshoot, ripple, shake, confetti,
stagger, parallax.

**Reduced motion sözleşmesi** (`base`/`motion.css`'te yazılı):
```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { transition-duration: 1ms !important; scroll-behavior: auto !important; }
  *:not([data-eds-motion-exempt]) { animation-duration: 1ms !important; animation-iteration-count: 1 !important; }
}
```
İki istisna: Spinner dönmeye devam eder (`[data-eds-motion-exempt]` ile muaf),
Skeleton shimmer'ı statik tona düşer (muafiyet yok).

### 2.15 Layout

| Token | Değer |
|---|---|
| `--eds-z-sticky-header` | 100 |
| `--eds-z-dropdown` | 1000 |
| `--eds-z-overlay` | 1300 |
| `--eds-z-modal` | 1400 |
| `--eds-z-toast` | 1500 |
| `--eds-z-tooltip` | 1600 |
| `--eds-breakpoint-md` | 768px |
| `--eds-breakpoint-lg` | 1024px |
| `--eds-breakpoint-xl` | 1280px |
| `--eds-breakpoint-2xl` | 1440px |
| `--eds-content-max-width` | 1440px |
| `--eds-page-gutter` | `var(--eds-space-6)` = 24px |
| `--eds-form-grid-gap` | `var(--eds-space-6)` = 24px |
| `--eds-control-height-sm` | 32px |
| `--eds-control-height-md` | **40px** (varsayılan — tüm formlar) |
| `--eds-control-height-lg` | 48px (login tarzı tekil vurgulu formlar) |
| `--eds-row-height-default` | 48px |
| `--eds-row-height-compact` | 40px |

**Desktop-first**, optimize aralık **1024–1440px**.

> 🔴 **KARAR (23.07.2026) — z-index çelişkisi kapatıldı.** Mock ekranları z-index
> token'larını kullanmıyor: modal'lara sabit `1000`, toast'a `1200` yazıyor. Bu
> yanlış: toast (1200) modal'ın (1400 olması gereken) altında kalır ve modal
> içinden tetiklenen bir bildirim görünmez olur.
>
> **Bağlayıcı kural: hiçbir bileşende sayısal z-index literal'i yazılmaz.**
> Katman değeri **yalnız** token'dan gelir ve tam olarak bu altı katman vardır:
>
> | Katman | Token | Değer |
> |---|---|---|
> | Sticky header | `--eds-z-sticky-header` | 100 |
> | Dropdown / Select paneli / DatePicker | `--eds-z-dropdown` | 1000 |
> | Modal overlay (karartma) | `--eds-z-overlay` | 1300 |
> | Modal / drawer gövdesi | `--eds-z-modal` | 1400 |
> | Toast | `--eds-z-toast` | 1500 |
> | Tooltip | `--eds-z-tooltip` | 1600 |
>
> Sıralama zaten doğru kurulmuş: overlay < modal < toast < tooltip. Yeni bir
> katman gerekirse **önce token eklenir**, sonra kullanılır. Kod review'da
> `z-index: <sayı>` görülmesi ret sebebidir.

### 2.16 Base reset

```css
:root { color-scheme: light only; }          /* dark mode kapsam dışı — bilinçli karar */
*, *::before, *::after { box-sizing: border-box; }
html, body { margin: 0; padding: 0; }
body {
  background: var(--eds-color-bg-page);
  color: var(--eds-color-text-primary);
  font-family: var(--eds-font-sans);
  font-size: var(--eds-type-body-size);
  line-height: var(--eds-type-body-line);
  -webkit-font-smoothing: antialiased;
}
.eds-tabular-nums { font-variant-numeric: tabular-nums; }
:focus-visible { outline: 2px solid var(--eds-color-border-focus); outline-offset: 2px; }
```

> **Zorunlu:** ID / tutar / tarih kolonlarında `font-variant-numeric: tabular-nums`.
> Mock bunu tablo hücrelerinde satır içi olarak uyguluyor.

---

## 3. Bileşen Envanteri

### 3.1 Tasarım sistemi ne sunuyor (26 bileşen)

| Kategori | Bileşenler |
|---|---|
| Foundation | `Icon` |
| Forms — buttons | `Button`, `ButtonGroup`, `IconButton` |
| Forms — text inputs | `TextInput`, `Textarea`, `MaskedInput`, `PasswordInput`, `SearchInput` |
| Forms — selection | `Select`, `Checkbox`, `RadioGroup`, `Switch`, `DatePicker` |
| Forms — structure | `FormField`, `FormSection` |
| Feedback — loading | `Spinner`, `ProgressBar`, `Skeleton`, `SkeletonTableRow`, `SkeletonDescriptionList`, `SkeletonCard` |
| Data display — utility | `Accordion`, `Divider`, `EmptyState`, `Tooltip` |

**DS'de OLMAYAN:** Table, Modal/Dialog, Toast, Badge/Tag, Pagination, Tabs, Stepper,
Card, Combobox/Autocomplete. Mock ekranları bunları **satır içi HTML + style ile
elle** kuruyor.

### 3.2 Mock ekranlarının fiilen kullandığı 8 bileşen

| Bileşen | Toplam | Login | Search | Create | Info | Offer | Config | Submit |
|---|---|---|---|---|---|---|---|---|
| `Icon` | 65 | 1 | 8 | 7 | 16 | 15 | 10 | 8 |
| `FormField` | 64 | 2 | 7 | 17 | 26 | 6 | 6 | — |
| `Button` | 56 | 1 | 3 | 10 | 26 | 6 | 5 | 5 |
| `TextInput` | 46 | 1 | 7 | 13 | 17 | 4 | 4 | — |
| `IconButton` | 21 | — | — | 3 | 17 | — | 1 | — |
| `Select` | 17 | — | 1 | 3 | 9 | 2 | 2 | — |
| `PasswordInput` | 2 | 1 | — | — | — | — | 1 | — |
| `DatePicker` | 2 | — | — | 1 | 1 | — | — | — |

### 3.3 Bileşen API'leri (bundle'dan çıkarılmış imzalar)

```ts
Icon({ name, size = 20, strokeWidth = 1.75, color = 'currentColor', className, style, 'aria-label' })
Button({ variant = 'primary', size = 'md', iconLeading, iconTrailing, fullWidth = false,
         loading = false, disabled = false, type = 'button', children, className, style })
IconButton({ icon, 'aria-label', tooltip, variant = 'ghost', size = 40,
             disabled = false, loading = false, className, style })
TextInput({ size = 'md', iconLeading, iconTrailing, suffix, clearable = false, maxLength,
            showCount = false, error = false, disabled = false, readOnly = false,
            value, defaultValue, onChange, onClear, onFocus, onBlur, placeholder,
            endAdornment, className, style })
Select({ options, value, defaultValue, onChange, placeholder = 'Select…', searchable = false,
         clearable = false, disabled = false, error = false, loading = false,
         size = 'md', className, style })
DatePicker({ value, defaultValue, onChange, min, max, placeholder = 'DD.MM.YYYY',
             disabled = false, error = false, className, style })
FormField({ label, htmlFor, required = false, optional = false, hint, helperText,
            errorText, children, className, style })
```

**Ekranlarda fiilen kullanılan varyantlar:**
- `Button.variant`: `primary`, `secondary`, `danger` (DS ayrıca `ghost` tanımlıyor)
- `Button.size`: `md` (varsayılan), `sm` (yalnız "Create new customer")
- `IconButton.variant`: `ghost`, `danger-ghost` (DS ayrıca `subtle` tanımlıyor)
- `IconButton.size`: `32` (DS: `32` | `40`)
- `Select.size`: `md`, `sm` (per-page seçici)

### 3.4 Button — ölçü ve durum tablosu

| size | height | padding-x | font-size | icon size |
|---|---|---|---|---|
| sm | `--eds-control-height-sm` (32px) | `--eds-space-3` (12px) | body-sm (13px) | 16 |
| md | `--eds-control-height-md` (40px) | `--eds-space-4` (16px) | body (14px) | 20 |
| lg | `--eds-control-height-lg` (48px) | `--eds-space-5` (20px) | body-lg (16px) | 20 |

Taban: `inline-flex`, `gap: --eds-space-2`, `font-weight: medium`,
`border-radius: --eds-radius-md`, `border: 1px solid transparent`,
`white-space: nowrap`, geçiş `--eds-duration-100 --eds-ease-standard`.

| variant | bg | text | border | hover | active |
|---|---|---|---|---|---|
| primary | action-primary-bg | action-primary-text | — | `-bg-hover` | `-bg-active` |
| secondary | action-secondary-bg | action-secondary-text | action-secondary-border | border-hover + bg-surface-hover | bg-selected + border-selected |
| ghost | bg-surface-sunken | action-secondary-text | — | border-default | border-hover |
| danger | action-danger-bg | action-danger-text | — | action-danger-bg-hover | — |

### 3.5 IconButton

`32×32` veya `40×40` kutu, `radius-md`, taban rengi `text-secondary`.

| variant | bg | hover |
|---|---|---|
| ghost | bg-surface-sunken | border-default + text-primary |
| subtle | bg-surface-sunken | border-default |
| danger-ghost | feedback-danger-bg | feedback-danger-border + action-danger-bg-hover |

Disabled: `cursor: not-allowed`, renk `action-disabled-text`, zemin
`action-disabled-bg` (her ikisi de `!important`).

### 3.6 TextInput

```
.eds-input-wrap  → flex, gap space-2, bg-surface, 1px border-input,
                   radius-md, padding 0 space-3, yükseklik = control-height-{size}
  :hover          → border-hover
  [data-focused]  → border-focus + box-shadow: --eds-focus-ring-shadow
  [data-error]    → border-error
  [data-disabled] → bg-disabled, border transparent, not-allowed
  [data-readonly] → bg-surface-sunken, border transparent
.eds-input-native → şeffaf, outline yok, font body, renk text-primary
  ::placeholder   → text-placeholder
```

### 3.7 Select

Native `<select>` değil, custom panel. Panel animasyonu:
`eds-panel-in` — `opacity 0→1` + `translateY(-4px→0)`, `--eds-duration-200 --eds-ease-enter`.
Option hover → `bg-surface-hover`; seçili option → `bg-selected`.

### 3.8 FormField (düzen sözleşmesi)

```
<div style="display:grid; gap:var(--eds-space-2)">
  <label>  ← flex, gap space-1, font-weight medium, size body, renk text-primary
     {label}
     {required && <span aria-hidden style="color:var(--eds-color-border-error)">*</span>}
     {optional && <span style="font-weight:regular;color:text-tertiary">(optional)</span>}
     {hint && <Tooltip><Icon name="info" size=14 /></Tooltip>}
  </label>
  {children}
  <div>   ← minHeight: --eds-type-caption-line (16px), size caption
     {error ? <Icon name="alert-circle" size=14 color=feedback-danger-icon /> : null}
     <span>{errorText || helperText || ''}</span>
  </div>
</div>
```

> 🔑 **Kritik:** hata mesajı alanı **her zaman render edilir** (`minHeight` 16px) —
> hata belirdiğinde alan yüksekliği **zıplamaz**. Angular karşılığında bu davranış
> korunmalı. Hata rengi `--eds-color-text-danger`, geçiş
> `color --eds-duration-150 --eds-ease-standard`.
> Ekranlarda `FormField` için verilen `hint-size` daima `100%,56px`
> (label 16 + kontrol 40 + hata satırı 16 + gap'ler).

### 3.9 İkon seti

İkonlar Lucide tabanlı, 24×24 viewBox, `stroke-width: 2`,
`stroke-linecap/linejoin: round`, `fill: none`, `stroke: currentColor`.
Shim'de **38 ikon** gömülü. Ekranlarda fiilen kullanılan **28 ikon**:

```
alert-circle, alert-triangle, arrow-left, arrow-right, arrow-right-left, ban,
briefcase, check, check-circle-2, chevron-left, chevron-right, eye, info,
log-out, mail, map-pin, menu, pencil, plus, search, search-x, shopping-cart,
thumbs-up, trash-2, user, user-plus, users, x
```

Ayrıca shim'de olup ekranlarda kullanılmayanlar: `chevron-down`, `chevron-up`,
`chevrons-left`, `chevrons-right`, `copy`, `eye-off`, `help-circle`, `inbox`,
`more-vertical`, `calendar` (DatePicker içinde kullanılıyor).

**Boyut konvansiyonu:** header/sidenav 22, kart ikonları 20–24,
tablo/aksiyon 16–18, FormField hata/hint 14.

---

## 4. Uygulama Kabuğu (tüm ekranlarda ortak)

Kabuk `Full App` template'inde tanımlı; her ekran kendi kopyasını içeriyor.
Angular'da **tek bir layout bileşeni** olarak yazılmalı.

```
┌──────────────────────────────────────────────────────────────────────┐
│ HEADER — height 64px, bg #242441 (bg-inverse), padding 0 24px        │
│ [Etiya logo 28px, tıklanınca Customer Search'e]        [EN] │ ...    │
└──────────────────────────────────────────────────────────────────────┘
┌────────┬─────────────────────────────────────────────────────────────┐
│SIDENAV │ MAIN                                                        │
│ 80px   │ flex:1, overflow:auto, padding 24px,                        │
│ bg #fff│ flex-column, gap 20px (space-5)                             │
│ border-│                                                             │
│ right  │                                                             │
│ 1px    │                                                             │
│ DEDEEB │                                                             │
└────────┴─────────────────────────────────────────────────────────────┘
```

### 4.1 Header sağ blok (soldan sağa, gap 20px, yükseklik 32px)

1. `EN` — body (14px), weight medium, `#fff`, `letter-spacing: 0.4px`
2. Ayraç — `1px × 24px`, `rgba(255,255,255,0.18)`
3. `Mobility · Resp. Sales Rep.` — body-sm (13px), `rgba(255,255,255,0.72)`, `max-width: 120px`
4. Ayraç
5. Avatar (28px daire, `rgba(255,255,255,0.12)`, `Icon user` 16px beyaz) + `John` (14px, medium, beyaz)

Logo: gömülü SVG (data URI), `height: 28px`. `<a>` içinde,
`aria-label="Go to customer search"`.

### 4.2 Sidenav

`padding: 12px 8px`, `gap: 4px`. Her öğe: 56px yükseklik, tam genişlik,
dikey flex (ikon üstte, etiket altta, gap 4px), `radius-md`,
`border: 1px solid transparent`.

| Öğe | İkon | Etiket |
|---|---|---|
| B2C | `users` | `B2C` |
| B2B | `briefcase` | `B2B` |
| (menü) | `menu` | — |
| (leads) | `thumbs-up` | — |
| **(en altta, `flex:1` boşluktan sonra)** | `log-out` | — (title: "Log out") |

- **Aktif:** zemin `--eds-color-action-primary-bg` (#F58220), ikon+etiket rengi
  `--eds-color-text-on-brand` (#242441)
- **Pasif:** zemin şeffaf, renk `--eds-color-text-secondary` (#57577E)
- Etiket: `font-size: 11px` (token dışı!), `weight: semibold`
- Geçiş: `background-color 100ms, border-color 100ms`
- Varsayılan aktif: `b2c`

> 🟢 **KARAR (23.07.2026):** Sidenav etiketi mock'ta `11px` — tip ölçeğinde
> karşılığı **yok**. **`--eds-type-caption-size` (12px)'e yuvarlanır.** Yeni
> token tanımlanmaz; token dışı piksel değeri yazmak yasaktır (FE-ADR-011 §f).
>
> 🟢 **KARAR (23.07.2026) — sidenav öğelerinin davranışı:** Yalnız **B2C**
> aktiftir. `B2B`, `menu` ve `thumbs-up` ikonları **görünür kalır** ama
> **tıklama hiçbir şey tetiklemez** — navigasyon yok, aktif durum değişmez.
> Erişilebilirlik için `aria-disabled="true"` ve `cursor: default`; görsel
> olarak pasif sidenav öğesi stilini korur. (Mock'ta bu butonlar aktif durumu
> değiştiriyor — o davranış taşınmaz.)

### 4.3 Toast (Customer Search ve Customer Info)

```
position: fixed; top: 76px; right: var(--eds-space-6);
z-index: 1200 (⚠️ token: --eds-z-toast = 1500 olmalı)
display: flex; gap: space-3; max-width: 400px;
padding: space-3 space-4; bg-surface; 1px border-default;
radius-lg; box-shadow: --eds-elevation-4;
animation: eds-toast-in 200ms var(--eds-ease-enter)
  @keyframes eds-toast-in { from { opacity:0; transform:translateY(-8px) } to { … } }
role="status"
```
İçerik: `Icon check-circle-2` (20px, `feedback-success-icon`) + mesaj (14px) +
kapatma butonu (24×24, `Icon x` 16px, `aria-label="Dismiss notification"`).
**Otomatik kapanma: 6 saniye.**

### 4.4 Ekranlar arası durum (mock'un localStorage anahtarları)

| Anahtar | İçerik |
|---|---|
| `eds_view_customer` | Görüntülenen müşteri payload'ı |
| `eds_edit_customer` | Düzenleme için Create ekranına aktarılan müşteri |
| `eds_flash` | Tek seferlik bildirim mesajı (okununca silinir) |
| `eds_customer_accounts` | Hesap listesi (`{v: SEED_VERSION, list}`) |
| `eds_sale_account`, `eds_sale_basket`, `eds_sale_address`, `eds_sale_config`, `eds_pending_sale`, `eds_open_summary` | Satış akışı durumu |

> Bu, mock'un sunucusuz çalışması için kurulmuş bir hile. Angular'da bunun
> karşılığı **router state + servis (signal/store)** olacak; `localStorage`
> taşınmayacak. Ancak **hangi verinin ekranlar arasında taşındığını** göstermesi
> bakımından değerli.

---

## 5. Ekranlar ve Backend Kapsam Eşleşmesi

Kabuk 7 ekranı tek React ağacında tutuyor (`startView` prop'u:
`login | search | create | info | offer | config | submit`); geçiş `setState`,
sayfa yenilenmesi yok.

| # | Ekran | Backend durumu | Şimdi geliştirilebilir mi? |
|---|---|---|---|
| 1 | **Login v2** | Keycloak teması TAMAMLANDI | ❌ Angular'da yazılmayacak |
| 2 | **Customer Search** | ✅ `GET /api/customers` hazır (ADR-005) | ✅ **EVET — golden path** |
| 3 | **Create Customer** | ✅ `POST /api/customers` hazır | ✅ EVET |
| 4 | **Customer Info v2** | ~~⚠️ KISMEN~~ → ✅ **4 sekmenin dördü de hazır** — müşteri/adres/iletişim + hesap (`/api/accounts/**`, 2026-07-24) + ürün görüntüleme (`/api/products`, 2026-07-31) | ✅ **EVET — tamamı** (yalnız `Deactivate product` yazma ucu yok) |
| 5 | **Offer Selection** | ❌ FR-PROD + FR-SALE — **katalog uçları var** (`/api/offers`, `/api/campaigns`) ama sepet/sipariş için **order-service YOK** | ❌ HAYIR — order-service ile birlikte |
| 6 | **Product Configuration** | ❌ FR-PROD — karakteristik şeması var, **endpoint yok** (product-service Faz A) | ❌ HAYIR — order-service ile birlikte |
| 7 | **Submit Order** | ❌ FR-SALE — servis yok | ❌ HAYIR — order-service ile birlikte |

### ⚠️ Kapsam Uyarısı: Mock UI backend'den GENİŞ

> ⚠️ **Güncellendi (2026-07-31).** Aşağıdaki 2026-07-23 tarihli karar iki kez
> aşıldı: hesap tarafı 2026-07-24'te (FE-ADR-013 §Amendment A1), ürün
> **görüntüleme** tarafı 2026-07-31'de (§Amendment B1) kapsama girdi. Özgün
> metin ADR disipliniyle korunuyor.

`Customer Info v2` ekranındaki **"Customer account" sekmesinin tamamı** var olmayan
domain'lere ait: hesap CRUD (FR-ACCT), ürün listesi/deaktivasyon/görüntüleme
(FR-PROD), "Start new sale" / "Transfer" / "Service address change" (FR-SALE).
Backend dokümanı bunu `traceability-matrix.md` "Deferred" bölümünde kaydetmiş.

**Karar:** Customer Info sadece **Customer info / Address / Contact medium**
sekmeleriyle yapılır; "Customer account" sekmesi ilgili servisler gelene kadar
render edilmez (bkz. §9 açık soru 4).

**Bugünkü durum (2026-07-31):** sekme **dört sekmeyle** canlı. Hâlâ kapsam dışı
kalanlar: `Deactivate product` (yazma ucu yok), FR-SALE satır aksiyonları
(`Start new sale` / `Transfer` / `Service address change` — API bilinçle
`Action` alanı döndürmüyor) ve üç satış ekranı (order-service bekliyor).

---

## 5A. Backend Kontratına Hizalama — Bağlayıcı Kararlar (23.07.2026)

Mock ile backend kontratı arasında tespit edilen 20 uyuşmazlık karara bağlandı.
**Genel kural: çakışmada BACKEND KONTRATI kazanır.** Mock görsel referanstır, veri
sözleşmesi değildir.

Kaynaklar: `docs/api/customer-service.md`, ADR-005,
`backend/customer-service/.../dto/**` (DTO'lar 23.07.2026'da doğrudan okundu).

### 5A.1 Veri sözleşmesi — mock ne diyor, ne kullanılacak

| # | Alan / konu | Mock | **Kullanılacak (backend)** |
|---|---|---|---|
| 7 | Doğum tarihi | `DD.MM.YYYY` (`02.11.1996`) | **ISO `YYYY-MM-DD`** — API'ye/API'den daima ISO. `DD.MM.YYYY` yalnız **görüntüleme** formatıdır, taşıma formatı değil |
| 8 | Cinsiyet | Select değeri `male` / `female` | **`"Male"` / `"Female"`** (request+response). Backend `Gender` enum'una map ediyor; lookup kısa kodu `MALE`/`FEMALE` ayrı bir katman, UI onu kullanmaz |
| 9 | Adres alanları | `city`, `district` (**string ad**), `street`, `houseNo`, `description` | **`cityId`, `districtId` (Long), `street`, `houseFlatNumber`, `addressDescription`, `primary` (boolean)**. Select'ler **id** taşır, ad değil |
| 11 | İletişim alanları | `email`, `mobile`, `home`, `fax` | **`email`, `mobilePhone`, `homePhone`, `fax`** |
| 12 | Müşteri kimliği | `customerId` (7 hane, `3068231`) | **Response alanı `customerNumber`** (Long, 1001'den başlar). **Query parametresi `customerId` adını korur** — ikisi karıştırılmayacak (ADR-005 Compatibility) |

### 5A.2 Arama semantiği

| # | Konu | Karar |
|---|---|---|
| 2 | "Second name" | **Ayrı filtre alanı YOK.** Mock'ta da yok. Tek `firstName` parametresi **First + Middle birleşiminde** kelime-başı arar (KR-01). Tabloda `middleName` ayrı **kolon** olarak gösterilir. UI'da tek kutu: **"Name"** |
| 3 | "Role" | **Filtre YOK, filtreleme yapılmayacak.** `role` yalnız **tablo kolonu** (response'ta `role` alanı, `"Customer"`) |
| 4 | Kriter birleşimi | Backend doğru: **isim grubu içi AND, dolu kriter grupları arası OR**. Mock zaten aynı. Değişiklik yok |
| 5 | `accountNumber` / `orderNumber` | Backend'de henüz geliştirme yok → **501 `MSG-FEATURE-NOT-IMPLEMENTED`**. İki filtre alanı UI'da render edilir ama **disabled** ve "yakında" ipucu taşır; hiçbir zaman istek gönderilmez (bkz. §9 madde 10) |
| 6 | Sayfa boyutu | **Varsayılan 15, seçenekler 15/30/50** (§6.2 ve §9 madde 3 — 29.07.2026 revizyonu; eski "varsayılan 20 / 20-50-100" kararı geçersiz) |
| 17 | Türkçe karakterler | **Backend'e göre: diakritik-DUYARLI.** Backend `lower()` + `LIKE` kullanıyor, diakritik katlaması **yok** (`CustomerSpecifications.wordStart`). `yilmaz` → `Yılmaz`'ı **bulmaz**; `Yılmaz` → bulur. **Frontend katlama YAPMAYACAK** — mock'un `ş→s, ç→c, ğ→g, ı→i, ü→u, ö→o` fold'u Angular'a taşınmayacak, kullanıcı girdisi ham gönderilecek. Kullanıcının Türkçe klavyeyle doğru yazması beklenir; arama kutusuna Türkçe karakter girişi engellenmeyecek ve `input`'lar UTF-8 olarak gönderilecek |

> 🐞 **Backend hatası — ÖLÇÜLDÜ, teorik değil (§9 madde 11).**
> Bu bir frontend↔backend uyuşmazlığı **değildir**; hata tamamen backend'in
> içindedir. `CustomerSpecifications.wordStart` aramanın iki tarafını **iki
> farklı motorda** küçük harfe çeviriyor:
>
> - **Kolon tarafı:** `cb.lower(...)` → SQL `lower()` → **Postgres** yapıyor
> - **Arama terimi tarafı:** `value.toLowerCase()` → **Java** yapıyor
>
> İki motor Türkçe büyük harflerde aynı sonucu üretmiyor. 23.07.2026'da çalışan
> stack üzerinde ölçülen değerler (kod noktaları):
>
> | Girdi | Postgres `lower()` | Java `toLowerCase()` — `ROOT`/`en_US` | Java — `tr` |
> |---|---|---|---|
> | `I` | `i` (U+0069) | `i` (U+0069) ✅ | `ı` (U+0131) ❌ |
> | `İ` | `i` (U+0069) | `i` + U+0307 — **iki kod noktası** ❌ | `i` (U+0069) ✅ |
>
> Kanıt (canlı DB'de):
> `SELECT lower('İbrahim') LIKE 'i̇brahim%'` → **`false`**
> (soldaki `ibrahim`, sağdaki Java'nın ürettiği `i` + birleşen nokta).
>
> **Sonuç:** `İ` ile başlayan bir isim (İbrahim, İsmail, İrem, İpek…) doğru
> yazılsa bile **bulunamaz**. Bu, mevcut ortamda (JVM `en_US`) **şu an aktif**
> bir hatadır — Türkçe locale'e özel bir uç durum değil.
>
> ⚠️ **Önceki notumdaki "tek satır `toLowerCase(Locale.ROOT)`" önerisi yanlıştı.**
> Yukarıdaki tablo gösteriyor ki **hiçbir Java `Locale` değeri iki durumu birden
> düzeltmiyor**: `ROOT` `I`'yı düzeltir ama `İ`'yi bozuk bırakır, `tr` tam tersi.
>
> **Doğru düzeltme: küçük harfe çevirmeyi Java'da hiç yapmamak** — arama terimini
> de SQL `lower()`'dan geçirmek, böylece iki taraf da aynı motoru kullanır ve
> tanım gereği tutarlı olur:
>
> ```java
> // wordStart(...) içinde, needle'ı Java'da lowercase ETME:
> String needle = escapeLike(value);                     // sadece LIKE kaçışı
> Expression<String> p1 = cb.lower(cb.literal(needle + "%"));
> Expression<String> p2 = cb.lower(cb.literal("% " + needle + "%"));
> return cb.or(cb.like(haystack, p1, '\\'), cb.like(haystack, p2, '\\'));
> ```
>
> Backend işi — frontend'i bağlamaz, ama düzeltilmezse arama ekranı Türkçe
> isimlerde sessizce eksik sonuç verir.

### 5A.3 Diğer

| # | Konu | Karar |
|---|---|---|
| 1 | Filtre etiketi "Name" | Semantik zaten uyumlu. Etiket **"Name"** kalır (LBL kataloğunda ayrı bir anahtar yok) |
| 10 | Şehir / ilçe listesi | Mock hardcoded; **`GET /api/cities` ve `GET /api/cities/{cityId}/districts`** kullanılacak. Response: `{cityId, name}` / `{districtId, name}` |
| 13 | Birincil adres | Mock `primaryIndex` (dizi indeksi); backend **`PATCH /api/customers/{n}/addresses/{addressId}/primary`** (kalıcı id). UI id ile çalışacak |
| 14 | Create'te ≥1 adres | Uyumlu, değişiklik yok |
| 15 | NATID kontrolü | Mock client-side iki sabit ID; gerçek akış: **409 `MSG-CUST-DUP-NATID`** (soft-deleted dahil, ADR-003) + mock'ta hiç olmayan **MERNIS doğrulaması** (400 `MSG-CUST-NATID-VERIFICATION-FAILED` / 503 `MSG-MERNIS-UNAVAILABLE`). Create ekranı bu üç hatayı da karşılamalı |
| 16 | Yaş / doğum tarihi | Uyumlu (`MSG-VAL-AGE-MIN`, `MSG-VAL-BIRTHDATE`) |
| 18 | Pasif müşteriler | Uyumlu — backend zaten yalnız aktifleri döndürüyor |
| 19 | z-index | §2.15'teki karar bloğuna taşındı |
| 20 | `data-testid` | §8'e taşındı — **her etkileşimli elemanda zorunlu** |

### 5A.4 Backend response şekilleri (DTO'lardan doğrulandı, 23.07.2026)

```jsonc
// GET /api/customers/{n}  ve  GET /api/customers satırları
{ "customerNumber": 1001, "firstName": "Ali", "middleName": null, "lastName": "Yildiz",
  "fatherName": "Hasan", "motherName": "Ayse", "birthDate": "1990-05-14",
  "gender": "Male", "nationalityId": "12345678901", "role": "Customer", "status": "ACTV" }

// GET /api/customers/{n}/addresses  (dizi)
{ "addressId": 1, "cityId": 1, "cityName": "Istanbul", "districtId": 1,
  "districtName": "Kadıköy", "street": "Bağdat Cad.", "houseFlatNumber": "12",
  "addressDescription": "Home", "primary": true }

// GET /api/customers/{n}/contact-medium
{ "email": "…", "homePhone": "…", "mobilePhone": "…", "fax": null }

// GET /api/cities → [{ "cityId": 1, "name": "Istanbul" }]
// GET /api/cities/{id}/districts → [{ "districtId": 1, "name": "Kadıköy" }]

// Hata gövdesi (her endpoint)
{ "timestamp": "…", "status": 409, "error": "Conflict", "messageKey": "MSG-…",
  "message": "…", "path": "…", "validationErrors": { "alan": "mesaj" } }
```

**Liste parametreleri:** `firstName, lastName, nationalityId, customerId,
accountNumber, gsmNumber, orderNumber, page (0), size (20)`.
**`sort` parametresi YOK** — sıralama sunucuda sabit
(`firstName ASC → lastName ASC → customerNumber ASC`), UI'dan değiştirilemez.

**`PUT /api/customers/{n}`** gövdesi = create'in `demographic` bloğunun aynısı
(`CustomerUpdateRequest extends DemographicRequest`): `firstName, middleName,
lastName, fatherName, motherName, birthDate, gender, nationalityId`.

> ⚠️ `Page` zarfının JSON alan adları (`content`, `totalElements`, `totalPages`,
> `number`, `size`, …) Spring'in `PageImpl` serileştirmesinden gelir; projede
> `spring.data.web.pageable.serialization-mode` ayarlanmamış. **Çalışan bir
> instance'ta veya Swagger'da teyit edilmeli** — kod okumasıyla kesinleştirilemedi.

---

## 6. Ekran Detayları

### 6.1 Login v2 (Angular'da YAZILMAYACAK — referans için)

```
grid-template-columns: 1.05fr 1fr; height: 100vh
┌────────────────────────┬──────────────────────────┐
│ MARKA PANELİ           │ FORM PANELİ              │
│ bg #242441             │ bg #fff, padding 40px    │
│ padding 40px           │ ┌──────────────────────┐ │
│ Etiya logo 200px       │ │ max-width: 360px     │ │
│ (ortalanmış)           │ │ gap: 32px (space-8)  │ │
│                        │ │                      │ │
│                        │ │ h1 "Sign in" 24px    │ │
│                        │ │ p açıklama 14px      │ │
│                        │ │ [hata banner]        │ │
│                        │ │ Username (icon user) │ │
│                        │ │ Password             │ │
│                        │ │ [Login] fullWidth    │ │
│                        │ │ ─────────────────    │ │
│                        │ │ Demo bilgileri 12px  │ │
│                        │ └──────────────────────┘ │
└────────────────────────┴──────────────────────────┘
```
- Hata banner'ı: `role="alert"`, `Icon alert-circle` 20px + 13px metin,
  `feedback-danger-bg/border`, `radius-md`, padding `12px 16px`
- Buton `loading` durumunda etiket `Signing in…`
- Enter tuşu submit eder
- Demo: `salesperson` / `etiya2026`; 850ms sahte gecikme
- ⚠️ `--eds-type-title-size` kullanıyor — **tanımsız token**, doğrusu `h1` (24px)

### 6.2 Customer Search (golden path) ⭐

```
main (padding 24px, gap 20px)
├── h1 "Search customer"  ← h2 token (20px/600), letter-spacing -0.2px
└── grid-template-columns: 320px 1fr; gap: 24px; flex:1
    ┌──────────────────┬────────────────────────────────────────────┐
    │ SEARCH FILTER    │ SEARCH RESULTS                             │
    │ card             │ card                                       │
    │                  │                                            │
    │ header 64px      │ header 64px                                │
    │  h2 "Search      │  h2 "Search results"    [Create new        │
    │      filter" 16px│                          customer] sm      │
    │ ────────────────  │ ──────────────────────────────────────────  │
    │ body (scroll)    │ TABLO (overflow:auto, flex:1)              │
    │  padding 20px    │                                            │
    │  gap 6px         │ ──────────────────────────────────────────  │
    │  7 × FormField   │ PAGINATION FOOTER                          │
    │                  │  grid: 1fr auto 1fr                        │
    │ ────────────────  │  [aralık]  [‹ 1 2 3 ›]  [Per page ▾]      │
    │ footer           │                                            │
    │  [Clear][Search] │                                            │
    └──────────────────┴────────────────────────────────────────────┘
```

**Filtre alanları (yukarıdan aşağı, tümü `FormField` + `TextInput`, 40px):**

| # | Label | API parametresi | Placeholder | inputMode | Kural |
|---|---|---|---|---|---|
| 1 | ID number | `nationalityId` | `11-digit ID number` | numeric | sadece rakam, max 11; tam eşleşme |
| 2 | Customer ID | `customerId` | `e.g. 3068231` | numeric | sadece rakam; tam eşleşme (**`customerNumber` değeri**) |
| 3 | Account number | `accountNumber` | `Account number` | numeric | 🚫 **disabled** — backend 501 (§5A.2/5) |
| 4 | GSM number | `gsmNumber` | `05XX XXX XX XX` | numeric | sadece rakam, max 11; **prefix** eşleşme |
| 5 | Name | `firstName` | `Name` | — | serbest metin; **First+Middle** birleşiminde kelime-başı |
| 6 | Last name | `lastName` | `Last name` | — | serbest metin; soyadda kelime-başı |
| 7 | Order number | `orderNumber` | `Order number` | numeric | 🚫 **disabled** — backend 501 (§5A.2/5) |

> Mock'ta ilk alanın id'si `idNumber`; **API parametresi `nationalityId`**.
> Mock'ta 3 ve 7 çalışıyor gibi görünüyor — Angular'da **disabled** render edilir
> ve hiçbir zaman isteğe eklenmez.

**Aksiyonlar (footer, padding `16px 20px`, üst kenarlık, gap 12px):**
- `Clear` — secondary, otomatik genişlik
- `Search` — primary, `iconLeading="search"`, **`fullWidth`**, `disabled` = hiçbir
  alan doldurulmamışsa

> 🟢 **KARAR (23.07.2026) — açılışta browse modu.** AC-CUST-01-02 ("Search tüm
> alanlar boşken pasif") ile AC-CUST-01-00 ("login sonrası tüm müşteriler
> listelenir") arasındaki gerilim şöyle çözüldü:
>
> - Ekran açıldığında **otomatik, kritersiz** `GET /api/customers?page=0&size=20`
>   çağrısı yapılır → tablo tüm aktif müşterilerle dolu gelir (AC-CUST-01-00).
> - `Search` butonu yalnız **yeniden** aramayı tetikler ve tüm filtreler boşken
>   **pasif kalır** (AC-CUST-01-02).
> - `Clear` filtreleri temizler **ve** browse moduna döner (kritersiz çağrı).
>
> Böylece iki AC de ihlal edilmeden karşılanır. Backend zaten kritersiz isteği
> browse modu olarak yorumluyor (ADR-005 §1) — ek endpoint gerekmiyor.

**Doğrulama (yalnız Search'e basınca gösterilir):**
| Alan | Kural | Mesaj |
|---|---|---|
| ID number | uzunluk ≠ 11 | `ID number must be 11 digits.` |
| GSM number | `05` ile başlamıyor | `GSM number must start with 05.` |

Kullanıcı yazmaya başlayınca hatalar gizlenir. Bir alan **tamamen
temizlenirse** o filtre anında kaldırılır (yeniden Search gerekmez).

**Sonuç tablosu — 6 kolon** (`sc-raw-table`, `border-collapse: collapse`):

| # | Başlık | Hücre biçimi |
|---|---|---|
| 1 | Customer ID | **link** — `text-brand` (#B85C0D), `weight: medium`, `underline`, `text-underline-offset: 2px`, `tabular-nums` |
| 2 | First name | body (14px), `text-primary` |
| 3 | Second name | body, `text-primary` |
| 4 | Last name | body, `text-primary` |
| 5 | Role | body, **`text-secondary`** (#57577E) |
| 6 | ID number | body, `text-primary`, **`tabular-nums`** |

- `thead tr`: zemin `bg-surface-sunken` (#EFEFF6); `th`: `padding: 12px 20px`,
  body-sm (13px), `weight: semibold`, `text-secondary`, `letter-spacing: 0.4px`,
  `white-space: nowrap`, sola hizalı
- `td`: `padding: 16px 20px`, `white-space: nowrap`
- Satırlar **zebra**: tek indeksliler `bg-page` (#F7F7FB), çiftler `bg-surface` (#fff);
  her satırda `border-top: 1px solid border-default`
- Customer ID linkine tıklama → Customer Info'ya gider

**Pagination footer** (`padding: 12px 20px`, üst kenarlık,
`grid-template-columns: 1fr auto 1fr`, gap 16px):

- **Sol:** aralık etiketi `"{from}–{to} of {total}"` — body-sm, `text-tertiary`,
  `tabular-nums` (en-dash `–`, U+2013)
- **Orta:** `‹` (Icon `chevron-left` 18px) + sayfa numaraları + `›`
  (`chevron-right` 18px), gap 4px
  - Sayfa butonu: `min-width: 32px`, `height: 32px`, `padding: 0 6px`,
    `radius-md`, body-sm, `tabular-nums`
  - **aktif:** 1px `border-focus`, zemin `bg-selected`, renk `text-brand`,
    `weight: semibold`, `cursor: default`, `aria-current="page"`
  - **boşta:** 1px `border-default`, zemin `bg-surface`, renk `text-secondary`,
    `weight: medium`
  - **disabled:** aynı ama renk `text-disabled`, `cursor: not-allowed`
  - **Kesme mantığı:** toplam sayfa ≤ 7 → hepsi; aksi halde
    `1 … (page-1) page (page+1) … son`, `…` `text-tertiary` ve tıklanamaz
- **Sağ:** `Per page` etiketi (body-sm, `text-secondary`) + `Select size="sm"`
  (`width: 84px`), mock'ta seçenekler **15 / 25 / 50**, varsayılan **15**
  - 🟢 **KARAR (29.07.2026, 23.07.2026 kararının yerine): seçenekler
    `15 / 30 / 50`, varsayılan `15` — KR-04 ile birebir.** Analistler eski
    kararın dayanağını beş API bug'ıyla kaldırdı
    (BUG-API-CUST-01-14/-16/-17/-18/-19): `GET /api/customers` artık **yalnız
    15/30/50** kabul ediyor, başka her `size` 400 dönüyor (ADR-005 §Amendment).
    ~~`20 / 50 / 100`~~ ve "`size` pozitif her değeri kabul ediyor" ifadesi
    geçersiz; **100 seçeneği kalktı**. Mock'un 15/25/50'sinden tek fark 25→30.
    Değerler iki haneli kaldığı için Select genişliği 84px'te kalabilir.
  - Panel **yukarı açılır** (`.eds-pagesize-up .eds-select-panel { top:auto;
    bottom: calc(100% + 4px) }`) — kart içinde kalması için

**Boş durum** (`noResults`, dikey+yatay ortalı, `padding: 40px 24px`, gap 12px):
- 48px daire, zemin `bg-surface-sunken`, içinde `Icon search-x` 24px `text-tertiary`
- Başlık: `No customer found` — body-lg (16px), semibold, `text-primary`
- Açıklama (`max-width: 360px`, body 14px, `line-height: 1.5`, `text-secondary`):
  `Use "Create new customer" above to add this customer.`

**Mock'un arama semantiği** (backend karşılaştırması için önemli):
- 52 kayıtlık sabit veri; `status === 'Inactive'` olanlar **listelenmez**
- Name kutusu `"First Middle"` **birleşimi** üzerinde arar → **KR-01 ile birebir uyumlu**
- Last name kutusu yalnız soyadda arar; ikisi birlikte doluysa **AND**
- Eşleşme: kelime başına çapalı (`indexOf` sonucu 0 veya öncesi boşluk),
  büyük/küçük harf duyarsız — bu kısım backend ile uyumlu
- 🔴 **Mock Türkçe diakritiği KATLIYOR** (`İ/I/ı→i`, `ş→s`, `ç→c`, `ğ→g`, `ü→u`,
  `ö→o`), yani `"yilmaz"` → `"Yılmaz"` eşleşiyor. **Backend katlamıyor**
  (`CustomerSpecifications.wordStart` yalnız `lower()` + `LIKE`).
  **KARAR: backend esas — bu fold Angular'a TAŞINMAYACAK**, kullanıcı girdisi
  ham gönderilecek (§5A.2 madde 17)
- GSM: **başlangıç eşleşmesi** (kısmi numara geçerli) — backend ile uyumlu
- ID number / Customer ID: **tam eşleşme** — backend ile uyumlu
- **Dolu kriterler OR'lanır** (`groups.some(Boolean)`) → ✅ backend de OR'luyor,
  uyumlu (§5A.2 madde 4)

### 6.3 Create Customer — 3 ADIMLI WIZARD

> ⚠️ Bu ekran düz bir form **değil**. Önceki analiz bunu kaçırmıştı.

```
main (padding 24px, gap 20px)
├── h1 "Create customer" / "Edit customer"   ← h2 token (20px/600)
├── STEPPER (gap 12px)
│   ● 01 Demographic ──── ○ 02 Address ──── ○ 03 Contact
└── WIZARD CARD (bg-surface, 1px border-default, radius-lg)
    ├── [aktif adımın gövdesi]  padding 24px, alt kenarlık
    └── [footer]                padding 16px 24px, space-between
```

**Stepper görsel sözleşmesi:**
| Durum | Daire | Etiket | Bağlayıcı |
|---|---|---|---|
| done (`n < step`) | 28px daire, `action-primary-bg`, içinde `Icon check` 16px `action-primary-text` | 13px/500, `text-primary` | `action-primary-bg` |
| active (`n === step`) | 28px daire, `action-primary-bg`, içinde `01`/`02`/`03` (13px/600) | 13px/**600**, `text-primary` | `border-default` |
| upcoming | 28px daire, `bg-surface-sunken`, numara `text-tertiary` | 13px/500, `text-tertiary` | `border-default` |

Bağlayıcı: `height: 2px`, `radius: 2px`, `flex: 1 1 auto`, `min-width: 24px`;
son adımda `display: none`. Adım numaraları **`padStart(2,'0')`** → `01`, `02`, `03`.

#### ADIM 1 — Demographic info
`grid-template-columns: 1fr 1fr; gap: 20px 24px` (satır 20px, kolon 24px)

| # | Label | Kontrol | Zorunlu | Notlar |
|---|---|---|---|---|
| 1 | First name | TextInput | ✅ | |
| 2 | Second name | TextInput | — | id=`middleName` |
| 3 | Last name | TextInput | ✅ | |
| 4 | Birth date | **DatePicker** | ✅ | `max = today` |
| 5 | Gender | **Select** | ✅ | `male` / `female`, placeholder `Select gender` |
| 6 | Father name | TextInput | — | |
| 7 | Mother name | TextInput | — | |
| 8 | Nationality ID | TextInput `inputMode=numeric` | ✅ | sadece rakam, max 11 |

Footer: `[Cancel]` (secondary, sol) ··· `[Next ›]` (primary,
`iconTrailing="chevron-right"`, sağ). `Cancel` → Customer Search.

**Doğrulama (yalnız Next'e basınca):**
| Alan | Kural | Mesaj |
|---|---|---|
| First/Last name | `/^[A-Za-zÀ-ſĞğİıŞşÖöÜüÇç\s'.-]+$/` | `Please enter a valid name (letters only).` |
| Birth date | gelecek tarih | `Birth date cannot be in the future.` |
| Birth date | yaş < 18 | `Customer must be at least 18 years old.` |
| Nationality ID | uzunluk ≠ 11 | `Please enter a valid ID number (11 digits).` |
| Nationality ID | kayıtlı ID (`23144230011`, `19805540022`) | `A customer already exists with this ID number.` |

`Next` butonu ayrıca **zorunlu alanlar boşsa disabled**
(firstName ∧ lastName ∧ birthDate ∧ gender ∧ nationalityId).

#### ADIM 2 — Address info
`grid-template-columns: 1fr 1fr; gap: 16px; align-items: start`

**Adres kartı** (min-height `146px`, padding 20px, `radius-lg`):
```
┌──────────────────────────────────────────┐
│ ⦿(40px daire, bg-surface-sunken)         │
│   Icon map-pin 20px text-tertiary        │
│                                          │
│   Kadıköy, Istanbul       ← 14px/600     │
│   Bağdat Cad. No: 12 · Home ← 13px       │
│                            text-secondary│
│ ──────────────────────────────────────── │  ← 1px, margin 16px 0 12px
│ (◉) Primary          [✎ 32] [🗑 32]      │
└──────────────────────────────────────────┘
```
- **Birincil adres:** kenarlık `border-selected` (#F58220), zemin `bg-selected`
  (#FEF6EE); radio 18px daire, 2px `border-selected`, içinde 8px turuncu nokta;
  etiket `Primary`, renk `text-brand`
- **Diğerleri:** kenarlık `border-default`, zemin `bg-surface`; radio 2px
  `border-input`; etiket `Set as primary` (13px/500, `text-secondary`)
- Sağ aksiyonlar: `IconButton pencil` (ghost 32, "Edit address"),
  `IconButton trash-2` (danger-ghost 32, "Delete address")
- **Birincil adres silinemez** → çöp butonu `disabled`,
  tooltip `Primary address can't be deleted`
- Başlık formatı: `"{district}, {city}"`;
  özet: `"{street} No: {houseNo} · {description}"`

**"Add new address" kutusu:** aynı `min-height: 146px`, `1px dashed border-hover`
(#9C9CBC), zemin `bg-surface-sunken`, `radius-lg`, ortalı dikey flex,
`Icon plus` 24px `text-tertiary` + `Add new address` (14px/500).

**Adres modal'ı** (overlay `bg-overlay` + `place-items:center`, `padding: 24px`;
kart `max-width: 560px`, `max-height: calc(100vh - 48px)`, `radius-lg`,
`elevation-3`):
```
┌── header (padding 20px 24px, alt kenarlık) ────────────┐
│ Add address / Edit address        [IconButton x ghost] │
├── body (padding 24px, scroll, gap 16px) ───────────────┤
│ [City ▾ *]          [District ▾ *]     ← 1fr 1fr, 16px │
│ [Street *]          [House / flat number *]            │
│ [Description *]                        ← tam genişlik  │
├── footer (padding 16px 24px, üst kenarlık, sağa) ──────┤
│                        [Cancel]  [✓ Add address]       │
└────────────────────────────────────────────────────────┘
```
- Tüm 5 alan **zorunlu**; hepsi dolmadan kaydet butonu `disabled`
- `City` seçilince `District` aktifleşir; **şehir değişince alt alanların
  tümü sıfırlanır**
- Şehir/ilçe verisi (mock): Istanbul → Kadıköy, Beşiktaş, Şişli, Üsküdar;
  Ankara → Çankaya, Keçiören, Yenimahalle; Izmir → Konak, Bornova, Karşıyaka
  (Angular'da bu `lookup-service`'ten gelecek — ADR-002)
- Kaydet butonu etiketi: yeni ise `Add address`, düzenleme ise `Save changes`;
  başlık da buna göre değişir

**Adres silme onayı** (`max-width: 420px`, padding 24px, gap 16px):
- 40px daire `feedback-danger-bg`, içinde `Icon trash-2` 20px `feedback-danger-icon`
- Başlık `Delete address` (16px/600) + metin
  `Are you sure you want to delete {district}, {city}?` (14px, `text-secondary`)
- Butonlar sağa hizalı: `[No]` (secondary) `[Yes]` (**danger**)
- Silinen adres birincilden önceyse `primaryIndex` bir azaltılır

Footer: `[‹ Previous]` (secondary, `iconLeading="chevron-left"`) ···
`[Next ›]` (primary). **En az 1 adres yoksa Next disabled.**

#### ADIM 3 — Contact info
`grid-template-columns: 1fr 1fr; gap: 20px 24px`

| # | Label | Kontrol | Durum | Placeholder |
|---|---|---|---|---|
| 1 | Email | TextInput `iconLeading="mail"` | ✅ zorunlu | `name@example.com` |
| 2 | Mobile phone | TextInput numeric | ✅ zorunlu | `05XXXXXXXXX` |
| 3 | Home phone | TextInput numeric | `optional` rozeti | `0XXXXXXXXXX` |
| 4 | Fax | TextInput numeric | `optional` rozeti | `0XXXXXXXXXX` |

Telefon alanları: sadece rakam, **max 11**.

**Doğrulama (yalnız Create'e basınca):**
| Alan | Kural | Mesaj |
|---|---|---|
| Email | `/^[^\s@]+@[^\s@]+\.[^\s@]+$/` | `Please enter a valid email address.` |
| Mobile | uzunluk 11 **ve** `05` ile başlar | `Please enter a valid phone number (e.g. 05XXXXXXXXX).` |
| Home / Fax | uzunluk 11 **ve** `0` ile başlar | (aynı mesaj) |

Footer: `[‹ Previous]` ··· `[✓ Create]` (primary, `iconLeading="check"`;
düzenleme modunda `Save changes`). Email+Mobile boşsa disabled.

**Başarı akışı:** payload `eds_view_customer`'a yazılır,
`eds_flash = "Customer created successfully."` (düzenlemede
`"Customer updated successfully."`) → Customer Info v2'ye gider, toast orada
gösterilir.

**Edit modu:** `eds_edit_customer` doluysa wizard aynı bileşenle edit olarak
açılır; başlık `Edit customer`, "kayıtlı ID" kontrolü **atlanır**
(kendi ID'sine takılmaması için).

### 6.4 Customer Info v2 — 4 SEKMELİ

```
main (padding 24px, gap 20px, overflow:hidden)
├── TABS (alt kenarlık 1px border-default, position:relative)
│   [Customer info][Customer account][Address][Contact medium]
│   ────────────                                   ← 2px turuncu underline
└── [aktif sekmenin içeriği]  (kart, flex:0 1 auto, overflow-y:auto)
```

**Sekme sözleşmesi:** her buton `flex: 1 1 0` (eşit genişlik), `text-align: center`,
`padding: 12px 4px`, body (14px). Aktif: `weight 600` + `text-primary`;
pasif: `weight 500` + `text-secondary`. Geçiş `color 120ms --eds-ease-standard`.

**Underline:** `position: absolute; bottom: -1px; left: 0; height: 2px;
radius: 2px; width: 25%` (=100/4), `transform: translateX(index * 100%)`,
zemin `action-primary-bg`, geçiş `transform 220ms --eds-ease-standard`.

Sekme değişince tüm alt durumlar sıfırlanır (açık modal'lar, düzenleme, hata bayrakları).

#### Sekme 1 — Customer info ✅ backend'de var

**Kart başlığı** (`padding: 20px 24px`, alt kenarlık, gap 12px):
- 32px daire avatar: zemin `bg-inverse` (#242441), metin `text-inverse`,
  caption (12px), `weight 600`, `letter-spacing: 0.4px` — **baş harfler**
  (`firstName[0] + lastName[0]`, büyük harf)
- Tam ad: h2 (20px), `weight 600`, `letter-spacing: -0.2px`, taşarsa `ellipsis`
  (format: `firstName middleName lastName`, boşlar atlanır)
- Sağda: `IconButton pencil` (ghost 32, "Edit customer info") +
  `IconButton trash-2` (danger-ghost 32, "Delete customer")

**Gövde:** `padding: 24px`, `grid-template-columns: 1fr 1fr`,
`gap: 16px 48px` (satır `space-4`, kolon `space-10`).
Her alan: dikey flex, gap 2px — label caption (12px) `text-tertiary`,
değer body (14px) `weight medium` `text-primary`.

| # | Label | Değer notu |
|---|---|---|
| 1 | First name | |
| 2 | Second name | boşsa `—` (em dash) |
| 3 | Last name | |
| 4 | Birth date | `tabular-nums`, format `DD.MM.YYYY` |
| 5 | Gender | |
| 6 | Father name | boşsa `—` |
| 7 | Mother name | boşsa `—` |
| 8 | Nationality ID | `tabular-nums` |

**Düzenleme modal'ı** (`max-width: 680px`, header `min-height: 72px`):
başlık `Customer info update`, gövde Create adım 1'in birebir aynısı
(8 alan, `1fr 1fr`, `gap 20px 24px`), footer `[Cancel] [✓ Save]`.

**Silme onay modal'ı** (`max-width: 420px`) — iki durumlu:
1. **Soru:** 40px danger daire + `Icon trash-2`, başlık `Delete customer`,
   metin `Are you sure you want to delete this customer?`, `[No] [Yes(danger)]`
2. **Hata** (aktif ürün varsa): `feedback-danger-bg/border` kutusu içinde
   `Icon alert-circle` 20px + metin
   `Since the customer has active products, the customer cannot be deleted.`
   (renk `text-danger`), tek buton `[Close]` (secondary)

#### Sekme 2 — Customer account ✅ backend'de var (2026-07-24 hesap, 2026-07-31 ürün)

> ⚠️ **Başlıktaki "backend'de YOK (yazılmayacak)" ibaresi geçersiz.**
> `account-service` 2026-07-24'te, `product-service` 2026-07-31'de geldi; sekme
> hem hesap hem ürün tarafıyla **yazıldı** (FE-ADR-013 §Amendment A1 ve §B1).
> Aşağıdaki düzen tanımı **bağlayıcı tasarım referansı olarak aynen geçerli.**

Düzen: başlık `Customer accounts` + `[+ Create new account]`
(primary). Tablo kolonları: *(genişleme oku 44px)*, `Account status`
(rozet+nokta), `Account number` (tabular), `Account name`, `Account type`,
`Actions` (sağa hizalı: `pencil` ghost + `trash-2` danger-ghost).
Satır genişleyince ürün alt tablosu açılır: `Product ID`, `Product name`,
`Campaign name`, `Campaign ID`, `Status`, `Action` (`eye` + `ban`).
Sayfalama: 4 / 8 / 12.

> 🔴 **Sayfalama UYGULANMADI** (bilinçli, iki bağımsız gerekçe): hesap listesi
> ve ürün listesi **ikisi de zarfsız düz dizi** döndürüyor, ve **FR-PROD-01
> hiçbir sayfalama kuralı tanımlamıyor**. 4/8/12 seçicisi bu yüzden
> reprodüksiyona alınmadı (`scope-and-conflicts.md` §4.24/5, §1B.2). Backend
> kontratı kazanır (§5A, FE-ADR-013 §e).

**Ürün alt tablosu — kolon → alan eşlemesi** (`GET /api/products?accountNumber=`,
`docs/api/product-service.md`):

| Mock kolonu | Yanıt alanı | Not |
|---|---|---|
| `Product ID` | `productId` | `tabular-nums`; public ürün tanımlayıcısı (`prod.id`). **KR-11 benzeri ürün numarası YOK** — iş numarası yalnız hesaplarda var |
| `Product name` | `productName` | |
| `Campaign name` | `campaignName` | Kampanyasız üründe `null` → **`-`** (render kuralı açıkça frontend'in işi) |
| `Campaign ID` | `campaignId` | **Public kampanya kodu** (`cmpg.campaign_code`, ör. `CMP-ADSL-01`) — iç `cmpg.id` asla dönmez. `null` → `-` |
| `Status` | `productStatus` | `Active` / `Passive`, backend'de türetilir. **Pasif ürün listede KALIR** (AC-PROD-01-03) — StatusBadge deseni (yukarıdaki rozet spesifikasyonu) |
| `Action` | *(alan yok)* | Saf UI kolonu (AC-PROD-01-04). `eye` = ürünü görüntüle (aktif); `ban` = pasifleştir → **yazma ucu yok, disabled** (FE-ADR-013 §Amendment B3) |

#### Sekme 2 devamı — "View product" modal'ı (FR-PROD-02) ⚠️ mock markup'ı YOK

> 🔴 **Bu bölüm mock'tan çıkarılamadı.** `docs/source/mock-ui/`ndaki bundle
> (`Guncel_Etiya_CRM_Lite_Full_App.html`) ürün ekranlarının markup'ını
> **taşımıyor**: "Campaign", "View product", "Product ID", "Prod offer" gibi
> hiçbir metin dosyada geçmiyor (2026-07-31 doğrulandı; §1'de listelenen ayrı
> `Customer Info v2.dc.html` dosyası repoda mevcut değil). Modal'a dair mock'ta
> **yalnız** §8'deki `View product` aria-label çapası kaldı.
>
> **Uygulanan karar (uydurma değil, kayıtlı türetme):** düzen Customer Info'nun
> **dokümante okuma grid'ini** izliyor (yukarıdaki Sekme 1 gövdesi: `1fr 1fr`,
> etiket caption/`text-tertiary` + değer body/medium, boş değer `—`); modal
> ölçüsü `560px` (§7.2 `md`), footer tek `[Close]` (secondary). Alanlar
> AC-PROD-02-01'in kendi listesi. Gerekçe ve alternatiflerin elenmesi:
> `scope-and-conflicts.md` §4.28/4, FE-ADR-013 §Amendment B1.

Alanlar (`GET /api/products/{id}`):

| Sıra | Etiket | Yanıt alanı | Not |
|---|---|---|---|
| 1 | `Product offer name` | `productOfferName` | |
| 2 | `Product offer ID` | `productOfferId` | `tabular-nums` |
| 3 | `Product spec ID` | `productSpecId` | `tabular-nums` |
| 4 | `Campaign` | `campaign` | Kampanya **ADI** (bu uç kampanya kodu döndürmez); `null` → `—` |
| 5 | `Service address` | `serviceAddress` | `{addressId, street, houseFlatNumber, districtName, cityName}` → iki satır: `street houseFlatNumber` / `districtName, cityName`. **Alt ürün ebeveyninin adresini gösterir** — zincir backend'de yürünür, istemci ek çağrı YAPMAZ. `null` (adres yok/silinmiş) → "Servis adresi yok", modal yine açılır |

Başlık, kullanıcının az önce tıkladığı **satırın ürün adıdır**: detay yanıtı
bilinçli olarak ürün kimliği (`productId`/`productName`) taşımıyor.

**Durum rozeti (StatusBadge deseni — diğer sekmelerde de kullanılabilir):**
```
inline-flex, gap 6px, padding 2px 8px, radius-sm, caption (12px), weight medium
aktif   → bg: status-success-bg (#F0FDF4), renk: green-700 (#15803D),
          nokta 6px daire status-success-dot (#16A34A)
pasif   → bg: bg-surface-sunken (#EFEFF6), renk: text-secondary (#57577E),
          nokta 6px daire text-disabled (#9C9CBC)
```

#### Sekme 3 — Address ✅ backend'de var

Başlık `Address` (h2), sağda **görünmez 32px placeholder** (diğer sekmelerle
hizalama için). Gövde `padding: 24px`, kart grid'i ve modal'ları **Create adım
2 ile birebir aynı** (§6.3). Fark yok — Angular'da **tek ortak bileşen**
(`AddressCardsComponent` + `AddressFormDialog`) yazılmalı.

#### Sekme 4 — Contact medium ✅ backend'de var

Başlık `Contact medium` (h2) + sağda `IconButton pencil` (ghost 32,
"Edit contact medium"). Gövde: `padding 24px`, `1fr 1fr`, `gap 16px 48px`
— Customer info'nun okuma düzeniyle aynı.

| # | Label | Not |
|---|---|---|
| 1 | Email | boşsa `—` |
| 2 | Home phone | `tabular-nums`, boşsa `—` |
| 3 | Mobile phone | `tabular-nums`, boşsa `—` |
| 4 | Fax | `tabular-nums`, boşsa `—` |

> ⚠️ Okuma sırası **Email → Home → Mobile → Fax**, ama düzenleme modal'ında da
> aynı sıra kullanılıyor (grid soldan sağa doldurduğu için görsel dizilim:
> sol sütun Email/Mobile, sağ sütun Home/Fax). Angular'da **okuma ve düzenleme
> sırası aynı tutulmalı.**

**Düzenleme modal'ı** (`max-width: 560px`): başlık `Contact medium update`,
4 alan (`1fr 1fr`, `gap 20px 24px`), doğrulama Create adım 3 ile aynı,
footer `[Cancel] [✓ Save]`.

### 6.5–6.7 Offer Selection / Product Configuration / Submit Order
❌ Backend yok — geliştirilmeyecek. Referans için düzen özeti:

Üçü de ortak bir **satış stepper'ını** paylaşır:
`1 Offer selection → 2 Product configuration → 3 Submit order`
ve sayfanın altında sabit bir **page footer** (ileri/geri aksiyonları) taşır.

- **Offer Selection:** `Offer selection` başlığı; solda sekmeli arama kartı
  (Product offers / Campaigns), sağda **Basket** paneli.
  - Ürün tablosu: `Prod offer ID`, `Prod offer name`, `Category`, `Price`
  - Kampanya tablosu: `Campaign ID`, `Campaign`, `Includes`, `Price`
  - Basket boş durumu: `Icon shopping-cart` 24px + `Basket is empty` +
    `Search offers and add them to the basket.`
  - Sepet kalemleri kaldırma butonu (`Icon x` 16px), bundle'larda alt kalemler
- **Product Configuration:** `Product configuration` başlığı; ürün karakteristik
  kartları (alanlar dinamik: metin / select / **password**) + `Address Info` bölümü
- **Submit Order:** `Submit order` başlığı; tek "Order card" özeti + gönderim
  onay dialog'u

---

## 7. Angular'a Çevirme Kararları

**Tavsiye:** Tailwind + EDS token'ları Tailwind teması olarak + bileşenleri
kendimiz yaz. Dış bileşen kütüphanesi bağımlılığı **sıfır**.

Gerekçe:
1. Token'lar zaten tanımlı ve isimlendirilmiş → tema olarak birebir aktarılır.
2. Fiilen gereken bileşen seti küçük: `Icon`, `FormField`, `TextInput`,
   `Button`, `IconButton`, `Select`, `DatePicker` (7 bileşen — `PasswordInput`
   Keycloak'ta kaldı).
3. Karmaşık overlay/combobox/autocomplete **yok**; tek gerçek overlay `Select`
   paneli ve `DatePicker` takvimi.
4. Tablo düz HTML → hazır tablo bileşeni gereksiz.

### 7.1 `shared/ui/` altında yazılacak EDS bileşenleri

| Angular bileşeni | Kaynak | Öncelik |
|---|---|---|
| `EdsIcon` | DS `Icon` (28 ikonluk alt küme yeter) | P0 |
| `EdsFormField` | DS `FormField` (hata alanı yükseklik sabiti dahil) | P0 |
| `EdsTextInput` | DS `TextInput` | P0 |
| `EdsButton` | DS `Button` (primary/secondary/danger, sm/md) | P0 |
| `EdsIconButton` | DS `IconButton` (ghost/danger-ghost, 32) | P0 |
| `EdsSelect` | DS `Select` (custom panel) | P0 |
| `EdsDatePicker` | DS `DatePicker` | P1 |

### 7.2 DS'de olmayan, ekranlar için elle yazılacak desenler

| Desen | Nerede kullanılıyor | Not |
|---|---|---|
| `EdsCard` (header/body/footer) | tüm ekranlar | 1px `border-default`, `radius-lg`, `elevation-0` |
| `EdsModal` | Create, Info | overlay + `max-width` varyantları (**420 / 480 / 520 / 560 / 680**), `elevation-3`. ⚠ **Düzeltildi 03.08.2026** (BUG-2 teşhisi sırasında bundle'daki gerçek markup okundu): tablo daha önce yalnız 420/560/680 diyordu, **480 ve 520 eksikti**. Gerçek kullanım: 420 = onay dialogları; **480 = Create/Edit billing account + Start new sale**; 520 = ürün detayı; 560 = iletişim/adres; 680 = müşteri bilgisi düzenleme.<br>🔴 **İki farklı taşma şekli var ve bu bilinçlidir:** 480'lik iki modal `overflow:visible` + **`max-height` YOK** (gövdedeki `Select` panelinin modal sınırının dışına taşabilmesi için); diğerleri `max-height:calc(100vh - 48px)` + `overflow:hidden` panel + `overflow:auto` gövde. Uygulama: `Modal` deseninin `overflowVisible` girdisi (scope §4.30) |
| `EdsConfirmDialog` | adres/müşteri/hesap silme | 420px, danger daire + `[No][Yes]` |
| `EdsToast` | Search, Info | 6sn otomatik kapanma, `elevation-4`, `role="status"` |
| `EdsStatusBadge` | Info hesap tablosu | nokta + metin, radius-sm |
| `EdsPagination` | Search, Info hesap | aralık + numaralar + per-page |
| `EdsTabs` | Info | eşit genişlik + kayan underline |
| `EdsStepper` | Create, satış akışı | 01/02/03 daire + bağlayıcı |
| `EdsEmptyState` | Search | DS'de `EmptyState` var ama mock kullanmıyor — DS sürümü tercih edilir |
| `EdsAddressCard` + `EdsAddressFormDialog` | Create adım 2 **ve** Info Address sekmesi | **ortak** — iki yerde birebir aynı |

---

## 7A. Çok Dillilik (i18n) — Bağlayıcı Kararlar (23.07.2026)

### 7A.1 Analist gereksinimi (FR-LANG-01, v8-2)

`docs/source/requirements/CRM_Lite_FR_AC_v8-2.docx` §2.8'den **birebir**
(v8 Final'den bu yana değişmedi, 2026-08-05'te v8-2'ye karşı yeniden doğrulandı):

| AC | Metin |
|---|---|
| **AC-LANG-01-01** | "Her ekranın üst (header) bölümünde, **Login ekranı dahil**, LBL-LANGUAGE etiketli bir dil değiştirici bulunur; liste yalnız Türkçe (TR) ve İngilizce (EN) seçeneklerini içerir ve varsayılan seçili dil **İngilizce**'dir." |
| **AC-LANG-01-02** | "Dil değiştirildiğinde tüm arayüz etiketleri (Etiket Kataloğu) ve mesajları (Mesaj Kataloğu) seçilen dile göre **anında** gösterilir." |
| **AC-LANG-01-03** | "Seçilen dil **oturum boyunca korunur** ve ekranlar arası geçişte sıfırlanmaz." |

**Sonuçlar:**
- Dil değiştirici **her ekranın header'ında** — opsiyonel değil. Login'de zaten
  var (Keycloak `crm-lite` teması, `login-locale-switcher`).
- "Anında" ⇒ **sayfa yenilemesi kabul edilemez** ⇒ build-time i18n
  (`@angular/localize`) bu gereksinimi **karşılayamaz**, elendi.

### 7A.2 Backend'in rolü — doğrulandı (23.07.2026)

Backend'de `Accept-Language`, `LocaleResolver`, `LocaleContextHolder`,
`MessageSource` ve `messages*.properties` **hiç yok** (`backend/` altında sıfır
eşleşme). Yani:

- Backend **dil-bağımsızdır**; `Accept-Language` göndermenin bir etkisi yoktur.
- Hata gövdesindeki **`message` alanı sabit İngilizce** teknik metindir —
  **kullanıcıya asla gösterilmez**, yalnız log/debug içindir.
- **Kullanıcıya gösterilecek metin daima `messageKey`'den türetilir.**
  Katalogda karşılığı olmayan bir `messageKey` gelirse generic bir hata metni
  gösterilir ve anahtar konsola loglanır (sessizce yutulmaz).

### 7A.3 Kararlar

| # | Konu | **Karar** |
|---|---|---|
| 1 | Yaklaşım | **Signal tabanlı kendi i18n servisimiz.** Dış kütüphane yok — §7'deki "sıfır bağımlılık" doktrini korunur. `lang` bir `signal`, sözlük bir `computed`; dil değişince Angular tüm şablonları otomatik yeniden değerlendirir ⇒ AC-LANG-01-02 ek iş olmadan sağlanır |
| 2 | Katalog yapısı | **Tek dosya, `key → {en, tr}`** (`core/i18n/catalog.ts`). Analist dokümanının tablo yapısıyla birebir ⇒ docx mutabakatı bir diff işlemi; "en'de var, tr'de yok" kayması yapısal olarak imkânsız; anahtar tipi `keyof typeof CATALOG` ile compile-time güvenli |
| 3 | Kalıcılık | **`localStorage` (`crm.lang`) + Keycloak `ui_locales`.** Login redirect'i `/oauth2/authorization/keycloak?ui_locales={lang}` olarak kurulur ⇒ Keycloak login sayfası da aynı dilde açılır (AC-LANG-01-01 "Login ekranı dahil"). Realm zaten `supportedLocales: [en, tr]`, `defaultLocale: en` |
| 4 | Tarih | **Taşıma daima ISO `YYYY-MM-DD`; gösterim iki dilde de sabit `dd.MM.yyyy`.** Mock İngilizce arayüzde bile `02.11.1996` gösteriyor — analist dilden bağımsız TR formatı istemiş. DatePicker placeholder'ı da iki dilde `DD.MM.YYYY`. `MM/dd` belirsizliği riski sıfırlanır |
| 5 | Sayı | Bu fazda **locale'e bağlı sayı formatlaması yok** — ekranlarda para/ondalık alan bulunmuyor. ID/telefon/numara alanları `font-variant-numeric: tabular-nums` ile ham gösterilir. Para birimi FR-SALE geldiğinde ayrıca karara bağlanacak |
| 6 | Varsayılan dil | **`en`** — `localStorage` boşsa. Tarayıcı diline **bakılmaz** (AC-LANG-01-01 "varsayılan İngilizce" kesin) |

### 7A.4 Anahtar isimlendirme

- **Analist anahtarları aynen korunur:** `LBL-*` (21 adet) ve `MSG-*` (30 adet).
  Yeniden adlandırılmaz, gruplanmaz, camelCase'e çevrilmez — backend'in
  döndürdüğü `messageKey` ile birebir eşleşmeleri şart.
- **Proje anahtarları** analist kataloğunda olmayan metinler içindir
  (ekran başlıkları, alan etiketleri, placeholder'lar, boş durum metinleri).
  Konvansiyon: `UI-{EKRAN}-{ELEMAN}`, ör. `UI-SEARCH-TITLE`,
  `UI-SEARCH-FILTER-ID-NUMBER`, `UI-SEARCH-EMPTY-BODY`.
- **`data-testid` asla çeviriye bağlanmaz** (§8) — dil değişince test kırılmaz.

### 7A.5 Etiket Kataloğu — analist kaynağından çıkarıldı (21 anahtar)

| Key | EN | TR |
|---|---|---|
| `LBL-LOGIN` | Login | Giriş Yap |
| `LBL-LOGOUT` | Logout | Çıkış |
| `LBL-SEARCH` | Search | Ara |
| `LBL-CREATE-CUSTOMER` | Create Customer | Yeni Müşteri Oluştur |
| `LBL-NEXT` | Next | İleri |
| `LBL-PREVIOUS` | Previous | Geri |
| `LBL-CANCEL` | Cancel | İptal |
| `LBL-SAVE` | Save | Kaydet |
| `LBL-CREATE` | Create | Oluştur |
| `LBL-ADD-NEW-ADDRESS` | Add New Address | Yeni Adres Ekle |
| `LBL-EDIT` | Edit | Düzenle |
| `LBL-DELETE` | Delete | Sil |
| `LBL-SET-PRIMARY-ADDR` | Select as a primary address | Birincil adres olarak seç |
| `LBL-CREATE-NEW-ACCOUNT` | Create New Account | Yeni Hesap Oluştur |
| `LBL-START-NEW-SALE` | Start New Sale | Yeni Satış Başlat |
| `LBL-ADD-TO-BASKET` | Add to Basket | Sepete Ekle |
| `LBL-CLEAR` | Clear | Temizle |
| `LBL-SUBMIT` | Submit | Gönder |
| `LBL-LANGUAGE` | Language | Dil |
| `LBL-YES` | Yes | Evet |
| `LBL-NO` | No | Hayır |

> `LBL-CREATE-NEW-ACCOUNT`, `LBL-START-NEW-SALE`, `LBL-ADD-TO-BASKET`,
> `LBL-SUBMIT` → FR-ACCT/FR-PROD/FR-SALE ekranlarına ait; katalogda tutulur ama
> bu fazda kullanılmaz.

### 7A.6 Mesaj Kataloğu — analist kaynağından çıkarıldı (30 anahtar)

| Key | EN | TR |
|---|---|---|
| `MSG-AUTH-INVALID-CRED` | Wrong user name or password. Please try again | Kullanıcı adı veya şifre hatalı. Lütfen tekrar deneyin. |
| `MSG-CUST-NOT-FOUND` | No customer found! Would you like to create the customer? | Müşteri bulunamadı! Müşteriyi oluşturmak ister misiniz? |
| `MSG-CUST-DUP-NATID` | A customer already exists with this Nationality ID. | Bu Nationality ID ile kayıtlı bir müşteri zaten mevcut. |
| `MSG-CUST-DELETE-CONFIRM` | Are you sure to delete this customer? | Bu müşteriyi silmek istediğinize emin misiniz? |
| `MSG-CUST-HAS-PRODUCTS` | Since the customer has active products, the customer cannot be deleted. | Müşterinin aktif ürünleri olduğundan müşteri silinemez. |
| `MSG-ADDR-IN-USE` | This address is linked to an active billing account or service and cannot be deleted. | Bu adres aktif bir fatura hesabına veya servise bağlıdır ve silinemez. |
| `MSG-ADDR-DELETE-CONFIRM` | Are you sure to delete this address? | Bu adresi silmek istediğinize emin misiniz? |
| `MSG-ACCT-DELETE-CONFIRM` | Are you sure to delete this billing account? | Bu fatura hesabını silmek istediğinize emin misiniz? |
| `MSG-ACCT-HAS-PRODUCTS` | Deletion could not be performed. There are active product(s) linked to this billing account. | Silme işlemi gerçekleştirilemedi. Bu fatura hesabına bağlı aktif ürün(ler) var. |
| `MSG-ACCT-DELETED` | Billing account deleted successfully. | Fatura hesabı başarıyla silindi. |
| `MSG-PROD-NONE` | No products are linked to this billing account. | Bu fatura hesabına bağlı ürün bulunmamaktadır. |
| `MSG-VAL-EMAIL` | Please enter a valid email address. | Lütfen geçerli bir e-posta adresi girin. |
| `MSG-VAL-PHONE` | Please enter a valid phone number (e.g. 05XXXXXXXXX). | Lütfen geçerli bir telefon numarası girin (ör. 05XXXXXXXXX). |
| `MSG-VAL-NATID` | Please enter a valid Nationality ID (11 digits). | Lütfen geçerli bir Nationality ID girin (11 hane). |
| `MSG-MERNIS-UNAVAILABLE` | The identity verification service is currently unavailable. | Kimlik doğrulama servisine şu anda ulaşılamıyor. |
| `MSG-CUST-NATID-VERIFICATION-FAILED` | The provided National Identity Number could not be verified. | Girilen T.C. Kimlik Numarası doğrulanamadı. |
| `MSG-VAL-BIRTHDATE` | Birth date cannot be in the future. | Doğum tarihi gelecekte olamaz. |
| `MSG-VAL-AGE-MIN` | Customer must be at least 18 years old. | Müşteri en az 18 yaşında olmalıdır. |
| `MSG-VAL-NAME` | Please enter a valid name (letters only). | Lütfen geçerli bir ad girin (yalnız harf). |
| `MSG-VAL-CHAR-REQUIRED` | This field is required. | Bu alan zorunludur. |
| `MSG-VAL-CHAR-FORMAT` | Please enter a value matching the field type. | Lütfen alan tipine uygun bir değer girin. |
| `MSG-SALE-DUP-OFFER` | This offer is already in the basket. | Bu teklif sepette zaten mevcut. |
| `MSG-SALE-ORDER-CONFIRM` | Are you sure to submit this order? | Bu siparişi göndermek istediğinize emin misiniz? |
| `MSG-SALE-OFFER-INACTIVE` | The basket contains an inactive offer. Please remove it to continue. | Sepette aktif olmayan bir teklif var. Devam etmek için lütfen çıkarın. |
| `MSG-SALE-NO-INTERNET` | An internet service offer is required to continue. | Devam etmek için bir internet servisi teklifi gereklidir. |
| `MSG-SALE-NO-RESOURCE` | A resource offer (e.g. modem) is required to continue. | Devam etmek için bir kaynak (ör. modem) teklifi gereklidir. |
| `MSG-SALE-NO-ACTIVATION` | An activation service offer is required to continue. | Devam etmek için bir aktivasyon servisi teklifi gereklidir. |
| `MSG-SALE-MULTI-INTERNET` | Only one internet service offer is allowed. | Yalnızca bir internet servisi teklifi eklenebilir. |
| `MSG-SALE-MULTI-RESOURCE` | Only one resource (modem) offer is allowed. | Yalnızca bir kaynak (modem) teklifi eklenebilir. |
| `MSG-SALE-MULTI-ACTIVATION` | Only one activation service offer is allowed. | Yalnızca bir aktivasyon servisi teklifi eklenebilir. |

### 7A.7 Katalogda karşılığı OLMAYAN backend anahtarları

Backend, analist kataloğunda bulunmayan anahtarlar da döndürüyor
(`functional-requirements.md` "Documented project additions"). Bunların TR/EN
metinleri **proje tarafından yazılacak** — analist kaynağında yok:

`MSG-VALIDATION-ERROR`, `MSG-INTERNAL-ERROR`, `MSG-SERVICE-UNAVAILABLE`,
`MSG-FEATURE-NOT-IMPLEMENTED`, `MSG-ADDR-LAST-DELETE`,
`MSG-ADDR-PRIMARY-DELETE`, `MSG-LOOKUP-NOT-FOUND`, `MSG-AUTH-UNAUTHORIZED`,
`MSG-AUTH-FORBIDDEN`, `MSG-AUTH-CSRF-REJECTED`.

> `MSG-AUTH-INVALID-CRED` katalogda var ama **API anahtarı değil** — Keycloak
> login sayfasının konusudur ve tema `messages_en/tr.properties` içinde zaten
> karşılanmıştır (`invalidUserMessage`). Angular tarafında kullanılmaz.

### 7A.8 Dikkat edilecekler

- **Türkçe karakterler:** katalog `.ts` dosyasında UTF-8 olarak literal yazılır
  (`\uXXXX` kaçışı **kullanılmaz**) — Keycloak tema bundle'ında da aynı kural
  geçerli, `messages_tr.properties` başındaki not bunu söylüyor.
- **Arama girdisinde diakritik katlaması yok** (§5A.2 madde 17) — i18n ile
  karıştırılmamalı; bu bir arama semantiği kararı, çeviri konusu değil.
- **`MSG-CUST-NOT-FOUND` metni bir soru içeriyor** ("Would you like to create
  the customer?") — mock'un boş durum ekranı da aynı yönlendirmeyi yapıyor
  ("Use *Create new customer* above to add this customer"). İki metin
  **çelişmiyor ama aynı da değil**; hangisinin kullanılacağı §9'a eklendi.

---

## 8. data-testid — Bağlayıcı Kural (KARAR 23.07.2026)

Mock'ta **hiç `data-testid` yok** (7 ekranda da 0 adet — doğrulandı). **Mock bu
konuda referans DEĞİLDİR.**

> 🔴 **Kural: her etkileşimli ve her doğrulanabilir eleman `data-testid` taşır.**
> Mock'ta olmaması bir gerekçe değildir. Zaten uygulanan örnek: Keycloak
> `crm-lite` login teması (`login-page`, `login-username-input`,
> `login-password-toggle`, `login-submit`, `login-alert`,
> `login-locale-switcher`, `login-locale-en`, `login-locale-tr`) — Angular aynı
> konvansiyonu sürdürür.

### Kapsam — `data-testid` ZORUNLU olan elemanlar

| Kategori | Örnekler |
|---|---|
| Tüm form kontrolleri | input, select, datepicker, checkbox, radio |
| Tüm butonlar | primary/secondary/danger, IconButton, link-buton |
| Navigasyon | sidenav öğeleri, sekmeler, sayfalama butonları, logo linki |
| Tablo yapısı | tablo kökü, her satır (kimlikli), tıklanabilir hücre/link |
| Overlay'ler | modal kökü, onay dialog'u, toast, dropdown paneli |
| Durum yüzeyleri | boş durum bloğu, hata banner'ı, alan hata metni, yükleme/skeleton |
| Wizard | stepper kökü, her adım, adım geçiş butonları |

### İsimlendirme konvansiyonu

`{ekran}-{bölüm}-{eleman}[-{varyant}]`, **kebab-case**, İngilizce, tekil.

```
customer-search-filter-id-number-input
customer-search-filter-clear-button
customer-search-submit-button
customer-search-results-table
customer-search-results-row-1001          ← dinamik: customerNumber ile sonlanır
customer-search-results-row-1001-open-link
customer-search-empty-state
customer-search-pagination-next
customer-search-pagination-page-size
customer-create-step-2
customer-create-address-add-button
customer-create-address-dialog
customer-info-tab-contact
customer-info-delete-confirm-yes
app-toast
```

**Dinamik satırlar** kimlikle biter (`…-row-{customerNumber}`,
`…-address-{addressId}`) — **dizi indeksi kullanılmaz**, sayfalama/sıralama
değişince indeks kayar.

### Kurallar

- `data-testid` **asla** çeviriye, görünen metne veya CSS sınıfına bağlanmaz —
  dil değişince test kırılmamalı (bkz. i18n bölümü).
- Üretim build'inde **silinmez** (E2E ortamda da aynı işaretçiler gerekir).
- `data-testid` erişilebilirliğin yerine geçmez: `aria-label`, `role`,
  `aria-current` ayrıca ve doğru şekilde verilir.

### Mock'tan devralınacak erişilebilirlik çapaları

`aria-label`: `Go to customer search`, `Log out`, `Previous page`, `Next page`,
`Dismiss notification`, `Edit customer info`, `Delete customer`, `Edit address`,
`Delete address`, `Close`, `View product`, `Deactivate product`, `Edit account`,
`Delete account`, `Remove from basket`.
Ayrıca `aria-current="page"` (aktif sayfa), `role="status"` (toast),
`role="alert"` (hata banner'ı), `title` (tooltip metinleri).

---

## 9. Analiste Sorulacak Açık Sorular

1. ~~**Arama kriterlerinin birleşimi**~~ → ✅ **KAPANDI (23.07.2026):** yanlış
   premise'ti. Backend de OR'luyor (isim grubu içi AND, gruplar arası OR) —
   mock ile birebir aynı. Bkz. §5A.2 madde 4.
2. ~~**"Account number" / "Order number" filtreleri**~~ → ✅ **KAPANDI
   (23.07.2026):** backend parametreleri **tanıyor** ama henüz geliştirilmedi →
   501 `MSG-FEATURE-NOT-IMPLEMENTED`. Alanlar UI'da **disabled** render edilir.
   Bkz. §5A.2 madde 5 ve aşağıdaki madde 10.
3. ~~**Per page değerleri**~~ → ✅ **YENİDEN KARARA BAĞLANDI (29.07.2026):**
   liste **pageable, varsayılan sayfa boyutu 15, seçenekler 15/30/50** — KR-04
   birebir uygulanıyor. 23.07.2026'daki "varsayılan 20, seçenekler 20/50/100"
   kararı **geçersiz**: dayanağı olan "API pozitif her `size` değerini kabul
   ediyor" davranışı analist bug'larıyla kaldırıldı, API artık whitelist dışı
   her değere 400 dönüyor (ADR-005 §Amendment). `PAGE_SIZE_OPTIONS` ile backend
   whitelist'i birebir aynı tutulmalı — aksi halde liste ekranı 400 alır.
4. **Customer Info "Customer account" sekmesi:** ilgili servisler gelene kadar
   sekme **hiç gösterilmeyecek** mi, yoksa disabled/"yakında" durumunda mı
   duracak?
5. **Sidenav etiket boyutu 11px:** tip ölçeğinde karşılığı yok. Caption'a (12px)
   yuvarlansın mı, yoksa yeni bir token mı tanımlansın?
6. **Sidenav öğelerinin işlevi:** B2C/B2B/menu/thumbs-up mock'ta sadece görsel
   durum değiştiriyor (hedef sayfa yok). CRM Lite kapsamında bunlar ne yapacak?
7. **Header'daki "Mobility · Resp. Sales Rep." rolü:** statik mi kalacak,
   Keycloak token'ından mı gelecek? (`/api/session/me` yalnız `username`,
   `subject`, `roles` döndürüyor — organizasyon bilgisi hiçbir yerde yok.)
   → Header'daki **"EN" dil seçici kısmı KAPANDI**: AC-LANG-01-01 gereği
   gerçek bir TR/EN değiştirici olacak (§7A).
8. ~~**Inactive müşteriler**~~ → ✅ **KAPANDI:** backend zaten yalnız aktifleri
   döndürüyor (`status_id = ACTV AND deleted_date IS NULL`) — mock ile uyumlu.
9. ~~**Login ekranı**~~ → ✅ **KAPANDI (23.07.2026):** Keycloak `crm-lite` teması
   mock'un Login v2'sini birebir uyguluyor (`infra/keycloak/themes/crm-lite/`).
   Angular'da login ekranı **yazılmayacak**.
10. **501 dönen filtrelerin görünürlüğü:** §5A.2'de "disabled render edilir"
    kararı verildi, ama **disabled ipucu metni** (`tooltip` / `helperText`)
    ne diyecek? Öneri: `"Available when the account/order module is released."`
    — analist onayı gerekiyor.
11. **Backend arama hatası — `İ` ile başlayan isimler bulunamıyor.** §5A.2'de
    ölçümle birlikte tarif edildi (`lower('İbrahim') LIKE 'i̇brahim%'` → `false`).
    Java ile Postgres aynı harfi farklı küçültüyor. **Backend düzeltmesi**;
    frontend'i bloke etmiyor ama düzeltilmezse Türkçe isim aramaları sessizce
    eksik sonuç verir. Not: bu, §5A.2 madde 17'deki **diakritik kararından
    bağımsızdır** — düzeltme `yilmaz` → `Yılmaz` eşleşmesini sağlamaz, sağlamamalı.
12. **Boş sonuç metni çelişkisi:** analist kataloğunda `MSG-CUST-NOT-FOUND` =
    *"No customer found! Would you like to create the customer?"*; mock'un boş
    durum ekranı ise *"No customer found"* başlığı + *"Use 'Create new customer'
    above to add this customer"* açıklaması kullanıyor. Hangisi geçerli —
    katalog metni mi, mock metni mi? (§7A.8)
13. **Proje anahtarlarının TR/EN metinleri:** §7A.7'deki 10 backend anahtarı
    analist kataloğunda yok. Metinleri biz mi yazacağız, analistten mi
    isteyeceğiz?

---

## 10. FRONTEND_BRAIN.md Bağlantısı ✅ (23.07.2026'da eklendi)

> `FRONTEND_BRAIN.md` repo kökünde **oluşturuldu** (`PROJECTBRAIN.md` kardeşi).
> Aşağıdaki blok oraya **§6 Tasarım Referansı** bölümü olarak işlendi; ayrıca
> kurallar `FRONTEND_BRAIN.md` §8 (AI Agent) ve `docs/frontend/adr/FE-ADR-011`
> içinde tekrarlanmıştır. Bu bölüm kaydın kendisi olarak korunuyor:

```markdown
## Tasarım Referansı

`docs/frontend/mock-ui-analysis.md` — Etiya CRM Lite tasarımının **tek referans
kaynağıdır**. Analistlerden gelen mock UI bundle'ından
(`docs/source/mock-ui/Guncel_Etiya_CRM_Lite_Full_App.html`) çıkarılıp
23.07.2026'da doğrulanmıştır.

Bağlayıcı kurallar:
- Renk, boşluk, tipografi, yarıçap, elevation, motion ve layout değerleri
  **oradaki tablolardan** alınır. Değer uydurulmaz, mock HTML'inden yeniden
  yorumlanmaz.
- Mock HTML'inin satır içi CSS fallback'lerinin bir kısmı gerçek token
  değerleriyle **uyuşmuyor** (§0'daki düzeltme tablosu). Daima **token dosyası
  değeri** esastır.
- Ekran düzeni, tablo kolonları, buton yerleşimi, doğrulama mesajları ve boş
  durum metinleri §6'daki ekran detaylarından alınır.
- Bir değer yanlışsa **önce `mock-ui-analysis.md` düzeltilir**, sonra kod yazılır.
- Turuncu zemin üzerine asla beyaz metin yazılmaz
  (`--eds-color-text-on-brand` = `#242441`).
- Bileşenler yalnız **semantic** token (`--eds-color-*`) tüketir; primitive
  paletler (`--eds-orange-*`, `--eds-ink-*`) doğrudan kullanılmaz.
- **Mock görsel referanstır, veri sözleşmesi değildir.** Alan adı, tip, format
  veya arama semantiği çakışmasında **backend kontratı kazanır** (§5A).
- **z-index literal'i yasak** — yalnız `--eds-z-*` token'ları (§2.15).
- **`data-testid` her etkileşimli elemanda zorunludur**; mock'ta olmaması
  gerekçe değildir. İsimlendirme ve kapsam kuralları §8'de.
- **Kullanıcıya gösterilen hiçbir metin şablona gömülmez** — tamamı i18n
  kataloğundan gelir (§7A). Backend'in `message` alanı kullanıcıya gösterilmez;
  metin daima `messageKey`'den türetilir.
```
