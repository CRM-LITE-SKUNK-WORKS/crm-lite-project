import type { Catalog } from './catalog.types';

/**
 * UI catalogue (UI-*) — text this project owns (screen titles, field labels,
 * placeholders, empty-state copy) that the analyst catalogues do not cover
 * (FE-ADR-012 §c). All entries are PROJECT-AUTHORED.
 *
 * Keys are feature-scoped by convention: UI-{FEATURE}-{ELEMENT}
 * (FE-ADR-012 §g). EN seeds come from the mock's English interface
 * (mock-ui-analysis §5–§6); TR is authored here. When a feature is built, its
 * keys may move into features/customer/<feature>/i18n.ts — the shape stays
 * `key → {en, tr}`, so it is a move, not a rewrite.
 *
 * This is a SEED, not the full screen text — only enough to prove the pipeline
 * and anchor the naming convention (no screens are built yet).
 */
export const UI = {
  // ---- Generic / cross-cutting ----
  'UI-ERROR-GENERIC': {
    en: 'Something went wrong. Please try again.',
    tr: 'Bir şeyler ters gitti. Lütfen tekrar deneyin.',
  },
  // Field-level fallback (FE-ADR-008 §2): shown on a control when the backend's
  // per-field validationErrors value is raw English (not a catalogue key), so the
  // raw text is never rendered.
  'UI-FIELD-INVALID': {
    en: 'Please check this field.',
    tr: 'Lütfen bu alanı kontrol edin.',
  },
  // Per-field replacements for backend fields annotated with raw English. Worded
  // constraint-agnostically — the envelope never says WHICH constraint failed
  // (see FIELD_FALLBACK_KEYS in core/http/api-error.ts).
  'UI-VAL-SELECT-REQUIRED': {
    en: 'Please make a valid selection.',
    tr: 'Lütfen geçerli bir seçim yapın.',
  },
  'UI-VAL-ADDRESS-REQUIRED': {
    en: 'Please add at least one address.',
    tr: 'Lütfen en az bir adres ekleyin.',
  },
  'UI-VAL-SECTION-INCOMPLETE': {
    en: 'This section is incomplete. Please review it.',
    tr: 'Bu bölüm eksik. Lütfen gözden geçirin.',
  },

  // ---- Common shell (header, language switcher) ----
  'UI-COMMON-APP-NAME': { en: 'CRM Lite', tr: 'CRM Lite' },
  'UI-COMMON-LANGUAGE': { en: 'Language', tr: 'Dil' },
  'UI-COMMON-LANGUAGE-EN': { en: 'English', tr: 'İngilizce' },
  'UI-COMMON-LANGUAGE-TR': { en: 'Turkish', tr: 'Türkçe' },
  'UI-COMMON-LOADING': { en: 'Loading…', tr: 'Yükleniyor…' },
  'UI-COMMON-LOGOUT': { en: 'Log out', tr: 'Çıkış yap' },
  'UI-COMMON-SIGN-IN': { en: 'Sign in', tr: 'Giriş yap' },
  'UI-COMMON-USER-AVATAR': { en: 'Signed in user', tr: 'Oturum açan kullanıcı' },

  // ---- Sidenav (mock-ui-analysis §4.2; only B2C is active — decision 2.21) ----
  'UI-NAV-GO-TO-SEARCH': { en: 'Go to customer search', tr: 'Müşteri aramaya git' },
  'UI-NAV-B2C': { en: 'B2C', tr: 'B2C' },
  'UI-NAV-B2B': { en: 'B2B', tr: 'B2B' },
  'UI-NAV-MENU': { en: 'Menu', tr: 'Menü' },
  'UI-NAV-LEADS': { en: 'Leads', tr: 'Potansiyel müşteriler' },

  // ---- Access denied (403 role denial; body text reuses MSG-AUTH-FORBIDDEN) ----
  'UI-ACCESS-DENIED-TITLE': { en: 'Access denied', tr: 'Erişim reddedildi' },

  // ---- Signed out (guard-free landing after logout) ----
  'UI-SIGNED-OUT-TITLE': { en: 'You have been signed out', tr: 'Oturumunuz kapatıldı' },
  'UI-SIGNED-OUT-BODY': {
    en: 'Your session in this application has ended.',
    tr: 'Bu uygulamadaki oturumunuz sonlandırıldı.',
  },

  // ---- Routing skeleton placeholder (replaced by Customer Search) ----
  'UI-PLACEHOLDER-TITLE': { en: 'You are signed in', tr: 'Oturumunuz açık' },
  'UI-PLACEHOLDER-BODY': {
    en: 'This protected page is a placeholder. The Customer Search screen will replace it.',
    tr: 'Bu korumalı sayfa bir yer tutucudur. Yerini Müşteri Arama ekranı alacak.',
  },

  // ---- Customer Search (mock-ui-analysis §6.2) ----
  'UI-SEARCH-TITLE': { en: 'Search customer', tr: 'Müşteri ara' },
  'UI-SEARCH-FILTER-HEADING': { en: 'Search filter', tr: 'Arama filtresi' },
  'UI-SEARCH-RESULTS-HEADING': { en: 'Search results', tr: 'Arama sonuçları' },
  'UI-SEARCH-FILTER-ID-NUMBER': { en: 'ID number', tr: 'Kimlik numarası' },
  'UI-SEARCH-FILTER-CUSTOMER-ID': { en: 'Customer ID', tr: 'Müşteri no' },
  'UI-SEARCH-FILTER-ACCOUNT-NUMBER': { en: 'Account number', tr: 'Hesap numarası' },
  'UI-SEARCH-FILTER-GSM-NUMBER': { en: 'GSM number', tr: 'GSM numarası' },
  'UI-SEARCH-FILTER-NAME': { en: 'Name', tr: 'Ad' },
  'UI-SEARCH-FILTER-LAST-NAME': { en: 'Last name', tr: 'Soyad' },
  'UI-SEARCH-FILTER-ORDER-NUMBER': { en: 'Order number', tr: 'Sipariş numarası' },
  'UI-SEARCH-COL-CUSTOMER-ID': { en: 'Customer ID', tr: 'Müşteri no' },
  'UI-SEARCH-COL-FIRST-NAME': { en: 'First name', tr: 'Ad' },
  'UI-SEARCH-COL-SECOND-NAME': { en: 'Second name', tr: 'İkinci ad' },
  'UI-SEARCH-COL-LAST-NAME': { en: 'Last name', tr: 'Soyad' },
  'UI-SEARCH-COL-ROLE': { en: 'Role', tr: 'Rol' },
  'UI-SEARCH-COL-ID-NUMBER': { en: 'ID number', tr: 'Kimlik numarası' },
  'UI-SEARCH-EMPTY-TITLE': { en: 'No customer found', tr: 'Müşteri bulunamadı' },
  'UI-SEARCH-EMPTY-BODY': {
    en: 'Use “Create new customer” above to add this customer.',
    tr: 'Bu müşteriyi eklemek için yukarıdaki “Yeni müşteri oluştur”u kullanın.',
  },
  'UI-SEARCH-CREATE-NEW': { en: 'Create new customer', tr: 'Yeni müşteri oluştur' },
  'UI-SEARCH-PER-PAGE': { en: 'Per page', tr: 'Sayfa başına' },
  // Disabled account/order filters (out of scope, FE-ADR-013 §b; scope-and-conflicts §2.24 wording pending analyst).
  'UI-SEARCH-DEFERRED-HINT': {
    en: 'Available when the account/order module is released.',
    tr: 'Hesap/sipariş modülü yayınlandığında kullanılabilir olacak.',
  },

  // ---- Create Customer (mock-ui-analysis §6.3) ----
  'UI-CREATE-TITLE': { en: 'Create customer', tr: 'Müşteri oluştur' },
  'UI-CREATE-EDIT-TITLE': { en: 'Edit customer', tr: 'Müşteriyi düzenle' },
  'UI-CREATE-STEP-DEMOGRAPHIC': { en: 'Demographic', tr: 'Demografik' },
  'UI-CREATE-STEP-ADDRESS': { en: 'Address', tr: 'Adres' },
  'UI-CREATE-STEP-CONTACT': { en: 'Contact', tr: 'İletişim' },
  'UI-CREATE-FIELD-FIRST-NAME': { en: 'First name', tr: 'Ad' },
  'UI-CREATE-FIELD-SECOND-NAME': { en: 'Second name', tr: 'İkinci ad' },
  'UI-CREATE-FIELD-LAST-NAME': { en: 'Last name', tr: 'Soyad' },
  'UI-CREATE-FIELD-BIRTH-DATE': { en: 'Birth date', tr: 'Doğum tarihi' },
  'UI-CREATE-FIELD-GENDER': { en: 'Gender', tr: 'Cinsiyet' },
  'UI-CREATE-FIELD-FATHER-NAME': { en: 'Father name', tr: 'Baba adı' },
  'UI-CREATE-FIELD-MOTHER-NAME': { en: 'Mother name', tr: 'Anne adı' },
  'UI-CREATE-FIELD-NATIONALITY-ID': { en: 'Nationality ID', tr: 'Kimlik numarası' },
  'UI-CREATE-FIELD-EMAIL': { en: 'Email', tr: 'E-posta' },
  'UI-CREATE-FIELD-MOBILE': { en: 'Mobile phone', tr: 'Cep telefonu' },
  'UI-CREATE-FIELD-HOME': { en: 'Home phone', tr: 'Ev telefonu' },
  'UI-CREATE-FIELD-FAX': { en: 'Fax', tr: 'Faks' },
  'UI-CREATE-FIELD-CITY': { en: 'City', tr: 'İl' },
  'UI-CREATE-FIELD-DISTRICT': { en: 'District', tr: 'İlçe' },
  'UI-CREATE-FIELD-STREET': { en: 'Street', tr: 'Cadde/Sokak' },
  'UI-CREATE-FIELD-HOUSE-NO': { en: 'House / flat number', tr: 'Bina / daire no' },
  'UI-CREATE-FIELD-DESCRIPTION': { en: 'Description', tr: 'Açıklama' },

  // ---- Customer Info (mock-ui-analysis §6.4; account tab is out of scope) ----
  'UI-DETAIL-TAB-INFO': { en: 'Customer info', tr: 'Müşteri bilgisi' },
  'UI-DETAIL-TAB-ADDRESS': { en: 'Address', tr: 'Adres' },
  'UI-DETAIL-TAB-CONTACT': { en: 'Contact medium', tr: 'İletişim' },
} satisfies Catalog;
