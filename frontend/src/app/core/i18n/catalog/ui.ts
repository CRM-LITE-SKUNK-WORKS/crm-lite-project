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
  'UI-COMMON-RETRY': { en: 'Try again', tr: 'Tekrar dene' },
  'UI-COMMON-CLOSE': { en: 'Close', tr: 'Kapat' },
  'UI-COMMON-OPTIONAL': { en: '(optional)', tr: '(isteğe bağlı)' },
  'UI-COMMON-COMING-SOON': { en: 'Coming soon', tr: 'Çok yakında' },
  'UI-COMMON-DISMISS': { en: 'Dismiss notification', tr: 'Bildirimi kapat' },
  'UI-COMMON-LOGOUT': { en: 'Log out', tr: 'Çıkış yap' },
  'UI-COMMON-USER-AVATAR': { en: 'Signed in user', tr: 'Oturum açan kullanıcı' },

  // ---- Sidenav (mock-ui-analysis §4.2; only B2C is active — decision 2.21) ----
  'UI-NAV-GO-TO-SEARCH': { en: 'Go to customer search', tr: 'Müşteri aramaya git' },
  'UI-NAV-B2C': { en: 'B2C', tr: 'B2C' },
  'UI-NAV-B2B': { en: 'B2B', tr: 'B2B' },
  'UI-NAV-MENU': { en: 'Menu', tr: 'Menü' },
  'UI-NAV-LEADS': { en: 'Leads', tr: 'Potansiyel müşteriler' },

  // ---- Access denied (403 role denial; body text reuses MSG-AUTH-FORBIDDEN) ----
  'UI-ACCESS-DENIED-TITLE': { en: 'Access denied', tr: 'Erişim reddedildi' },

  // ---- Customer Search (mock-ui-analysis §6.2) ----
  'UI-SEARCH-TITLE': { en: 'Search customer', tr: 'Müşteri ara' },
  'UI-SEARCH-FILTER-HEADING': { en: 'Search filter', tr: 'Arama filtresi' },
  'UI-SEARCH-RESULTS-HEADING': { en: 'Search results', tr: 'Arama sonuçları' },
  // Browse mode (ADR-005 criterion-less list, AC-CUST-01-00): the card holds
  // EVERY active customer, not the result of a search — so it must not claim
  // to be one. PROJECT-AUTHORED heading; analyst wording may replace the text
  // without touching code (scope §4.26).
  'UI-SEARCH-BROWSE-HEADING': { en: 'All customers', tr: 'Tüm müşteriler' },
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
  'UI-SEARCH-SUBMIT': { en: 'Search', tr: 'Ara' },
  'UI-SEARCH-CLEAR': { en: 'Clear', tr: 'Temizle' },
  'UI-SEARCH-LOADING': { en: 'Loading customers…', tr: 'Müşteriler yükleniyor…' },
  // State 3 (customer-search-analysis §6): browse mode with zero customers —
  // deliberately DIFFERENT from MSG-CUST-NOT-FOUND, which implies a search
  // happened. PROJECT-AUTHORED; analyst wording pending (scope §2A.3).
  'UI-SEARCH-BROWSE-EMPTY-TITLE': { en: 'No customers yet', tr: 'Henüz müşteri yok' },
  'UI-SEARCH-BROWSE-EMPTY-BODY': {
    en: 'Customers will appear here once they are created.',
    tr: 'Müşteriler oluşturuldukça burada listelenecek.',
  },
  // Client-side UX validation on Search (mock-ui-analysis §6.2 wording; the
  // backend remains the authority, FE-ADR-007).
  'UI-SEARCH-VAL-ID-LENGTH': {
    en: 'ID number must be 11 digits.',
    tr: 'Kimlik numarası 11 haneli olmalıdır.',
  },
  'UI-SEARCH-VAL-GSM-PREFIX': {
    en: 'GSM number must start with 05.',
    tr: 'GSM numarası 05 ile başlamalıdır.',
  },
  // Placeholders (mock-ui-analysis §6.2 table; name/last-name/account/order
  // placeholders equal their labels and reuse those keys).
  'UI-SEARCH-PLACEHOLDER-ID-NUMBER': {
    en: '11-digit ID number',
    tr: '11 haneli kimlik numarası',
  },
  'UI-SEARCH-PLACEHOLDER-CUSTOMER-ID': { en: 'e.g. 3068231', tr: 'ör. 3068231' },
  'UI-SEARCH-PLACEHOLDER-GSM': { en: '05XX XXX XX XX', tr: '05XX XXX XX XX' },
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
  // Success flash after the atomic create (mock §6.3 eds_flash wording) —
  // PROJECT-AUTHORED; shown as a Toast on Customer Info.
  'UI-CREATE-TOAST-SUCCESS': {
    en: 'Customer created successfully.',
    tr: 'Müşteri başarıyla oluşturuldu.',
  },
  'UI-CREATE-FIELD-CITY': { en: 'City', tr: 'İl' },
  'UI-CREATE-FIELD-DISTRICT': { en: 'District', tr: 'İlçe' },
  'UI-CREATE-FIELD-STREET': { en: 'Street', tr: 'Cadde/Sokak' },
  'UI-CREATE-FIELD-HOUSE-NO': { en: 'House / flat number', tr: 'Bina / daire no' },
  'UI-CREATE-FIELD-DESCRIPTION': { en: 'Description', tr: 'Açıklama' },

  // ---- Pagination (mock-ui-analysis §6.2 footer; parameterized per FE-ADR-012 §h:
  //      named placeholders so TR can reorder freely; en-dash U+2013) ----
  'UI-PAGINATION-RANGE': { en: '{from}–{to} of {total}', tr: '{total} kayıttan {from}–{to}' },
  'UI-PAGINATION-PREV': { en: 'Previous page', tr: 'Önceki sayfa' },
  'UI-PAGINATION-NEXT': { en: 'Next page', tr: 'Sonraki sayfa' },
  'UI-PAGINATION-PAGE': { en: 'Page {page}', tr: 'Sayfa {page}' },

  // ---- Customer Info (mock-ui-analysis §6.4) ----
  'UI-DETAIL-TAB-INFO': { en: 'Customer info', tr: 'Müşteri bilgisi' },
  'UI-DETAIL-TAB-ACCOUNT': { en: 'Customer account', tr: 'Müşteri hesabı' },
  'UI-DETAIL-TAB-ADDRESS': { en: 'Address', tr: 'Adres' },
  'UI-DETAIL-TAB-CONTACT': { en: 'Contact medium', tr: 'İletişim' },
  'UI-DETAIL-LOADING': { en: 'Loading customer…', tr: 'Müşteri yükleniyor…' },
  'UI-DETAIL-NOT-FOUND-TITLE': { en: 'Customer not found', tr: 'Müşteri bulunamadı' },
  'UI-DETAIL-BACK-TO-SEARCH': { en: 'Back to search', tr: 'Aramaya dön' },
  'UI-DETAIL-EDIT-INFO': { en: 'Edit customer info', tr: 'Müşteri bilgisini düzenle' },
  'UI-DETAIL-DELETE-CUSTOMER': { en: 'Delete customer', tr: 'Müşteriyi sil' },
  'UI-DETAIL-INFO-UPDATE-TITLE': {
    en: 'Customer info update',
    tr: 'Müşteri bilgisi güncelleme',
  },
  'UI-DETAIL-EDIT-CONTACT': { en: 'Edit contact medium', tr: 'İletişim bilgisini düzenle' },
  'UI-DETAIL-CONTACT-UPDATE-TITLE': {
    en: 'Contact medium update',
    tr: 'İletişim bilgisi güncelleme',
  },
  'UI-DETAIL-CONTACT-LOADING': {
    en: 'Loading contact info…',
    tr: 'İletişim bilgisi yükleniyor…',
  },
  'UI-DETAIL-ACCOUNTS-HEADING': { en: 'Customer accounts', tr: 'Müşteri hesapları' },
  // (`UI-DETAIL-PRODUCTS-HEADING` was removed 2026-07-31: the "Coming soon"
  //  block it titled is gone, and the live section owns `UI-PRODUCT-HEADING`
  //  under its own feature prefix — same call as §4.24/8's placeholder removal.)
  // Success toasts (mock §6.3 flash pattern; §4.3 toast contract) — PROJECT-AUTHORED.
  'UI-DETAIL-TOAST-INFO-SAVED': {
    en: 'Customer updated successfully.',
    tr: 'Müşteri başarıyla güncellendi.',
  },
  'UI-DETAIL-TOAST-CONTACT-SAVED': {
    en: 'Contact medium updated successfully.',
    tr: 'İletişim bilgisi başarıyla güncellendi.',
  },
  'UI-DETAIL-TOAST-CUSTOMER-DELETED': {
    en: 'Customer deleted successfully.',
    tr: 'Müşteri başarıyla silindi.',
  },
  // Gender DISPLAY labels — the wire values stay "Male"/"Female" (scope §2.7);
  // these only localize what the user sees, they never change the data.
  'UI-GENDER-MALE': { en: 'Male', tr: 'Erkek' },
  'UI-GENDER-FEMALE': { en: 'Female', tr: 'Kadın' },
  'UI-CREATE-GENDER-PLACEHOLDER': { en: 'Select gender', tr: 'Cinsiyet seçin' },

  // ---- Address sub-module (mock §6.3 step 2 = §6.4 Address tab, byte-identical) ----
  'UI-ADDRESS-ADD-TITLE': { en: 'Add address', tr: 'Adres ekle' },
  'UI-ADDRESS-EDIT-TITLE': { en: 'Edit address', tr: 'Adresi düzenle' },
  'UI-ADDRESS-SUBMIT-EDIT': { en: 'Save changes', tr: 'Değişiklikleri kaydet' },
  'UI-ADDRESS-PRIMARY': { en: 'Primary', tr: 'Birincil' },
  'UI-ADDRESS-DELETE': { en: 'Delete address', tr: 'Adresi sil' },
  'UI-ADDRESS-PRIMARY-DELETE-HINT': {
    en: "Primary address can't be deleted",
    tr: 'Birincil adres silinemez',
  },
  'UI-ADDRESS-LOADING': { en: 'Loading addresses…', tr: 'Adresler yükleniyor…' },
  // Card summary line (mock §6.3: "{street} No: {houseNo} · {description}") —
  // parameterized so the format string never sits hardcoded in a template.
  'UI-ADDRESS-SUMMARY': {
    en: '{street} No: {houseNo} · {description}',
    tr: '{street} No: {houseNo} · {description}',
  },
  'UI-ADDRESS-DISTRICT-PLACEHOLDER': { en: 'Select district', tr: 'İlçe seçin' },
  'UI-ADDRESS-CITY-PLACEHOLDER': { en: 'Select city', tr: 'İl seçin' },

  // ---- Contact placeholders (mock §6.3 step 3) ----
  'UI-CONTACT-PLACEHOLDER-EMAIL': { en: 'name@example.com', tr: 'ad@ornek.com' },
  'UI-CONTACT-PLACEHOLDER-MOBILE': { en: '05XXXXXXXXX', tr: '05XXXXXXXXX' },
  'UI-CONTACT-PLACEHOLDER-PHONE': { en: '0XXXXXXXXXX', tr: '0XXXXXXXXXX' },

  // ---- Account section (mock §6.4 tab 2; FR-ACCT-01..04 — PROJECT-AUTHORED;
  //      backend messageKeys live in messages.ts under their contract names) ----
  'UI-ACCOUNT-COL-STATUS': { en: 'Account status', tr: 'Hesap durumu' },
  'UI-ACCOUNT-COL-NUMBER': { en: 'Account number', tr: 'Hesap numarası' },
  'UI-ACCOUNT-COL-NAME': { en: 'Account name', tr: 'Hesap adı' },
  'UI-ACCOUNT-COL-TYPE': { en: 'Account type', tr: 'Hesap tipi' },
  'UI-ACCOUNT-COL-ACTIONS': { en: 'Actions', tr: 'İşlemler' },
  // Status DISPLAY labels — wire values stay "Active"/"Passive" (contract).
  'UI-ACCOUNT-STATUS-ACTIVE': { en: 'Active', tr: 'Aktif' },
  'UI-ACCOUNT-STATUS-PASSIVE': { en: 'Passive', tr: 'Pasif' },
  'UI-ACCOUNT-LOADING': { en: 'Loading billing accounts…', tr: 'Fatura hesapları yükleniyor…' },
  'UI-ACCOUNT-EMPTY-TITLE': { en: 'No billing accounts', tr: 'Fatura hesabı yok' },
  'UI-ACCOUNT-EMPTY-BODY': {
    en: 'Use “Create New Account” above to add the first billing account.',
    tr: 'İlk fatura hesabını eklemek için yukarıdaki “Yeni Hesap Oluştur”u kullanın.',
  },
  'UI-ACCOUNT-EDIT-TITLE': { en: 'Edit account', tr: 'Hesabı düzenle' },
  'UI-ACCOUNT-DELETE': { en: 'Delete account', tr: 'Hesabı sil' },
  'UI-ACCOUNT-FIELD-NAME': { en: 'Account name', tr: 'Hesap adı' },
  'UI-ACCOUNT-FIELD-ADDRESS': { en: 'Billing address', tr: 'Fatura adresi' },
  'UI-ACCOUNT-FIELD-NUMBER': { en: 'Account number', tr: 'Hesap numarası' },
  'UI-ACCOUNT-FIELD-TYPE': { en: 'Account type', tr: 'Hesap tipi' },
  'UI-ACCOUNT-ADDRESS-PLACEHOLDER': { en: 'Select address', tr: 'Adres seçin' },
  'UI-ACCOUNT-TOAST-CREATED': {
    en: 'Billing account created successfully.',
    tr: 'Fatura hesabı başarıyla oluşturuldu.',
  },
  'UI-ACCOUNT-TOAST-UPDATED': {
    en: 'Billing account updated successfully.',
    tr: 'Fatura hesabı başarıyla güncellendi.',
  },
  // Row expander (mock §6.4 tab 2: products open inside the account row).
  'UI-ACCOUNT-EXPAND': { en: 'Show products', tr: 'Ürünleri göster' },
  'UI-ACCOUNT-COLLAPSE': { en: 'Hide products', tr: 'Ürünleri gizle' },

  // ---- Product section (mock §6.4 tab 2 sub-table; FR-PROD-01..02 —
  //      PROJECT-AUTHORED. The backend messageKeys MSG-PROD-NONE (analyst,
  //      frontend-only) and MSG-PROD-NOT-FOUND (project addition, 404) live in
  //      messages.ts under their contract names and are NOT duplicated here. ----
  'UI-PRODUCT-HEADING': { en: 'Products', tr: 'Ürünler' },
  // AC-PROD-01-03 columns, in contract order.
  'UI-PRODUCT-COL-ID': { en: 'Product ID', tr: 'Ürün ID' },
  'UI-PRODUCT-COL-NAME': { en: 'Product name', tr: 'Ürün adı' },
  'UI-PRODUCT-COL-CAMPAIGN-NAME': { en: 'Campaign name', tr: 'Kampanya adı' },
  // The PUBLIC campaign code (cmpg.campaign_code) — the only campaign identity
  // the UI ever shows; internal campaign ids never leave the service.
  'UI-PRODUCT-COL-CAMPAIGN-ID': { en: 'Campaign ID', tr: 'Kampanya ID' },
  'UI-PRODUCT-COL-STATUS': { en: 'Status', tr: 'Durum' },
  'UI-PRODUCT-COL-ACTION': { en: 'Action', tr: 'İşlem' },
  // Status DISPLAY labels — wire values stay "Active"/"Passive" (contract).
  'UI-PRODUCT-STATUS-ACTIVE': { en: 'Active', tr: 'Aktif' },
  'UI-PRODUCT-STATUS-PASSIVE': { en: 'Passive', tr: 'Pasif' },
  'UI-PRODUCT-LOADING': { en: 'Loading products…', tr: 'Ürünler yükleniyor…' },
  // AC-PROD-01-02 empty state; the body is the ANALYST key MSG-PROD-NONE.
  'UI-PRODUCT-EMPTY-TITLE': { en: 'No products', tr: 'Ürün yok' },
  // AC-PROD-01-04: the Action column is view-only.
  'UI-PRODUCT-VIEW': { en: 'View product', tr: 'Ürünü görüntüle' },
  // "Deactivate product" is a WRITE and product-service Phase A has none, so the
  // control is inert; the label says so (FE-ADR-013 §Amendment A3 rule 4).
  'UI-PRODUCT-DEACTIVATE-COMING-SOON': {
    en: 'Deactivate product (coming soon)',
    tr: 'Ürünü pasifleştir (çok yakında)',
  },
  // ---- FR-PROD-02 detail modal (AC-PROD-02-01 fields) ----
  'UI-PRODUCT-DETAIL-TITLE': { en: 'Product detail', tr: 'Ürün detayı' },
  'UI-PRODUCT-DETAIL-LOADING': { en: 'Loading product…', tr: 'Ürün yükleniyor…' },
  'UI-PRODUCT-FIELD-OFFER-NAME': { en: 'Product offer name', tr: 'Ürün teklifi adı' },
  'UI-PRODUCT-FIELD-OFFER-ID': { en: 'Product offer ID', tr: 'Ürün teklifi ID' },
  'UI-PRODUCT-FIELD-SPEC-ID': { en: 'Product spec ID', tr: 'Ürün spesifikasyon ID' },
  'UI-PRODUCT-FIELD-CAMPAIGN': { en: 'Campaign', tr: 'Kampanya' },
  'UI-PRODUCT-FIELD-SERVICE-ADDRESS': { en: 'Service address', tr: 'Servis adresi' },
  'UI-PRODUCT-SERVICE-ADDRESS-NONE': {
    en: 'No service address',
    tr: 'Servis adresi yok',
  },

  // ---- DatePicker aria labels (first consumer: demographic edit dialog) ----
  'UI-DATE-OPEN-CALENDAR': { en: 'Open calendar', tr: 'Takvimi aç' },
  'UI-DATE-PREV-MONTH': { en: 'Previous month', tr: 'Önceki ay' },
  'UI-DATE-NEXT-MONTH': { en: 'Next month', tr: 'Sonraki ay' },
  'UI-DATE-PREV-YEAR': { en: 'Previous year', tr: 'Önceki yıl' },
  'UI-DATE-NEXT-YEAR': { en: 'Next year', tr: 'Sonraki yıl' },
} satisfies Catalog;
