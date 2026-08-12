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
  // A DATE characteristic on a new order is forward-looking; the rule is
  // frontend-only (see charNotPastValidator), hence a UI-* and not an MSG-* key.
  'UI-VAL-CHAR-DATE-PAST': {
    en: 'Please choose today or a later date.',
    tr: 'Lütfen bugünü veya ileri bir tarihi seçin.',
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

  // ---- Job titles (header, scope §2.20) ----
  // The session carries a CODE (`titleCode: "SALES_REP"`), never display text, so
  // the title localizes with TR/EN like everything else on screen. Exactly the
  // §2.7 `gender` arrangement: the identity provider owns a stable value, this
  // catalogue owns the words. Key shape: UI-TITLE-{CODE with _ as -}. A code with
  // no entry here is rendered RAW rather than swallowed (see Shell.title) — this
  // list is expected to grow, not to be exhaustive.
  'UI-TITLE-SALES-REP': { en: 'Sales Representative', tr: 'Satış Temsilcisi' },

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
  // UI-SEARCH-DEFERRED-HINT was REMOVED on 2026-08-05: the Account/Order Number
  // filters are live (customer-service resolves KR-02 for real), so a hint saying
  // the module is unreleased would be a lie. scope §2.24 is closed with it.

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
  // BUG-1 (2026-08-03): the account dialog's inline "New address" panel. The
  // field labels/placeholders are the address dialog's existing keys — the
  // form is the same five fields, so it uses the same copy (scope §4.30).
  'UI-ACCOUNT-ADD-ADDRESS': { en: 'Add new address', tr: 'Yeni adres ekle' },
  'UI-ACCOUNT-NEW-ADDRESS-TITLE': { en: 'New address', tr: 'Yeni adres' },
  'UI-ACCOUNT-NEW-ADDRESS-CANCEL': {
    en: 'Cancel new address',
    tr: 'Yeni adresten vazgeç',
  },
  'UI-ACCOUNT-NEW-ADDRESS-SAVE': { en: 'Save address', tr: 'Adresi kaydet' },
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

  // ---- §2.7 Product Sale wizard (FR-SALE-01/02). EN comes VERBATIM from the
  //      decoded mock screens (Offer Selection / Product Configuration /
  //      Submit Order .dc.html), which are the binding visual contract
  //      (FE-ADR-011, scope §2B.7). Button labels are NOT here — LBL-NEXT,
  //      LBL-PREVIOUS, LBL-CANCEL, LBL-SUBMIT, LBL-CLEAR, LBL-ADD-TO-BASKET,
  //      LBL-SEARCH, LBL-YES and LBL-NO are analyst keys already in labels.ts.
  //      Address-form text is not here either: AC-SALE-01-11 reuses the
  //      FR-ADDR-02 dialog, so it reuses that dialog's keys. ----

  // Wizard chrome (shared by all three steps)
  'UI-SALE-STEP-OFFER': { en: 'Offer selection', tr: 'Teklif seçimi' },
  'UI-SALE-STEP-CONFIG': { en: 'Product configuration', tr: 'Ürün yapılandırma' },
  // Deliberately NOT the same as UI-SALE-SUBMIT-TITLE: the mock's step 3 chip
  // reads "Order summary" while its page heading reads "Submit order".
  'UI-SALE-STEP-SUMMARY': { en: 'Order summary', tr: 'Sipariş özeti' },
  'UI-SALE-WIZARD-PROGRESS': { en: 'Wizard progress', tr: 'Sihirbaz ilerlemesi' },
  // Parameterized (FE-ADR-012 §h): the sub-heading under every step title.
  'UI-SALE-ACCOUNT-CONTEXT': {
    en: 'New sale · Account {accountNumber}',
    tr: 'Yeni satış · Hesap {accountNumber}',
  },

  // Step 1 — Offer selection
  'UI-SALE-OFFER-TITLE': { en: 'Offer selection', tr: 'Teklif seçimi' },
  // The mock names these two tabs "Catalog" and "Campaign" — singular for the
  // second (Offer selection.dc.html, `tabItems`). Corrected 2026-08-05 from the
  // earlier "Product offers" / "Campaigns" (scope §4.32).
  'UI-SALE-TAB-OFFERS': { en: 'Catalog', tr: 'Katalog' },
  'UI-SALE-TAB-CAMPAIGNS': { en: 'Campaign', tr: 'Kampanya' },
  'UI-SALE-FILTER-CATEGORY': { en: 'Category', tr: 'Kategori' },
  'UI-SALE-FILTER-CATEGORY-ALL': { en: 'All categories', tr: 'Tüm kategoriler' },
  'UI-SALE-FILTER-OFFER-ID': { en: 'Offer ID', tr: 'Teklif ID' },
  'UI-SALE-FILTER-OFFER-NAME': { en: 'Offer name', tr: 'Teklif adı' },
  'UI-SALE-FILTER-CAMPAIGN': { en: 'Campaign', tr: 'Kampanya' },
  'UI-SALE-FILTER-CAMPAIGN-ALL': { en: 'All campaigns', tr: 'Tüm kampanyalar' },
  'UI-SALE-FILTER-CAMPAIGN-ID': { en: 'Campaign ID', tr: 'Kampanya ID' },
  'UI-SALE-FILTER-CAMPAIGN-NAME': { en: 'Campaign name', tr: 'Kampanya adı' },
  'UI-SALE-COL-OFFER-ID': { en: 'Prod offer ID', tr: 'Ürün teklifi ID' },
  'UI-SALE-COL-OFFER-NAME': { en: 'Prod offer name', tr: 'Ürün teklifi adı' },
  'UI-SALE-COL-CATEGORY': { en: 'Category', tr: 'Kategori' },
  'UI-SALE-COL-PRICE': { en: 'Price', tr: 'Fiyat' },
  'UI-SALE-COL-CAMPAIGN-ID': { en: 'Campaign ID', tr: 'Kampanya ID' },
  'UI-SALE-COL-CAMPAIGN': { en: 'Campaign', tr: 'Kampanya' },
  'UI-SALE-COL-INCLUDES': { en: 'Includes', tr: 'İçerik' },
  'UI-SALE-CATALOG-EMPTY': {
    en: 'No offers match your search.',
    tr: 'Aramanızla eşleşen teklif yok.',
  },
  'UI-SALE-CATALOG-LOADING': { en: 'Loading offers…', tr: 'Teklifler yükleniyor…' },
  // Readable names for the DERIVED `serviceType` enum of GET /api/offers and
  // GET /api/campaigns. The raw wire value (INTERNET / RESOURCE / ACTIVATION)
  // is never printed; the EN wording seeds from the mock's own type labels
  // (FE-ADR-012 §f), the enum keys come from the contract (scope §4.32).
  'UI-SALE-SERVICE-TYPE-INTERNET': { en: 'Internet package', tr: 'İnternet paketi' },
  'UI-SALE-SERVICE-TYPE-RESOURCE': { en: 'Modem', tr: 'Modem' },
  'UI-SALE-SERVICE-TYPE-ACTIVATION': { en: 'Activation package', tr: 'Aktivasyon paketi' },
  // Catalog card footer (mock: `selCountLabel`). With nothing ticked it counts
  // the ROWS of the active tab; the moment something is ticked it counts the
  // SELECTION instead. Plain interpolation, no plural engine (FE-ADR-012 §h.3).
  'UI-SALE-SELECTED-COUNT': { en: '{count} selected', tr: '{count} seçili' },
  'UI-SALE-ROW-COUNT-OFFERS': { en: '{count} offers', tr: '{count} teklif' },
  'UI-SALE-ROW-COUNT-CAMPAIGNS': { en: '{count} campaigns', tr: '{count} kampanya' },
  'UI-SALE-BASKET-TITLE': { en: 'Basket', tr: 'Sepet' },
  // The basket header counts ENTRIES: an added campaign is ONE item, not three
  // (the mock's `basket.length`). Singular and plural are two SEPARATE KEYS
  // selected at the call site — that is not a plural engine, which FE-ADR-012
  // §h.3 rules out; it is the same "one key, one sentence" shape as every other
  // entry here (scope §4.33).
  'UI-SALE-BASKET-COUNT-ONE': { en: '1 item', tr: '1 kalem' },
  'UI-SALE-BASKET-COUNT': { en: '{count} items', tr: '{count} kalem' },
  // Second line of a CAMPAIGN row in the basket. Parameterized so TR can put
  // the word where it belongs rather than always after the id (§h.2).
  'UI-SALE-BASKET-CAMPAIGN-SUBTITLE': {
    en: '{campaignId} · Campaign',
    tr: '{campaignId} · Kampanya',
  },
  'UI-SALE-BASKET-EMPTY': { en: 'Basket is empty', tr: 'Sepet boş' },
  'UI-SALE-BASKET-EMPTY-HINT': {
    en: 'Search offers and add them to the basket.',
    tr: 'Teklifleri arayıp sepete ekleyin.',
  },
  'UI-SALE-BASKET-REMOVE': { en: 'Remove from basket', tr: 'Sepetten çıkar' },
  // Accessible name of the filled check on an already-added row. The row is a
  // <tr>, not a widget, so "already added" is conveyed as CONTENT here rather
  // than as `aria-disabled` on the row (which would be an invalid state on a
  // non-widget and makes tooling treat the row as unclickable).
  'UI-SALE-ROW-ADDED': { en: 'Already in basket', tr: 'Sepete eklendi' },
  'UI-SALE-TOTAL-AMOUNT': { en: 'Total amount', tr: 'Toplam tutar' },

  // Step 2 — Product configuration
  'UI-SALE-CONFIG-TITLE': { en: 'Product configuration', tr: 'Ürün yapılandırma' },
  'UI-SALE-CONFIG-OFFER-NAME': { en: 'Prod Offer Name', tr: 'Ürün Teklifi Adı' },
  'UI-SALE-CONFIG-OFFER-ID': { en: 'Prod Offer ID', tr: 'Ürün Teklifi ID' },
  // UI-SALE-CONFIG-NO-FIELDS was REMOVED on 2026-08-05: the mock renders NOTHING
  // under the header of an offer with no characteristics (its `hasFields` branch
  // has no else arm), so the sentence was ours, not the design's. AC-SALE-01-21
  // is still satisfied — the offer keeps its own card (scope §4.33).
  'UI-SALE-CONFIG-LOADING': {
    en: 'Loading product configuration…',
    tr: 'Ürün yapılandırması yükleniyor…',
  },
  // AC-SALE-01-12: LBL-NEXT validates HERE. The per-field MSG-VAL-CHAR-* text
  // is the answer; this banner only says where to look when the offending
  // field is scrolled out of sight.
  'UI-SALE-CONFIG-INVALID': {
    en: 'Please correct the highlighted fields before continuing.',
    tr: 'Devam etmeden önce lütfen işaretli alanları düzeltin.',
  },
  // AC-SALE-01-17: a BOOLEAN characteristic is a choice, not free text — the
  // options carry the backend's own wire values (`true` / `false`).
  'UI-SALE-CHAR-BOOL-TRUE': { en: 'Yes', tr: 'Evet' },
  'UI-SALE-CHAR-BOOL-FALSE': { en: 'No', tr: 'Hayır' },
  'UI-SALE-CHAR-BOOL-PLACEHOLDER': { en: 'Select', tr: 'Seçiniz' },
  'UI-SALE-ADDRESS-TITLE': { en: 'Address info', tr: 'Adres bilgisi' },
  'UI-SALE-ADDRESS-HINT': {
    en: 'Select a service address from existing addresses.',
    tr: 'Mevcut adreslerden bir servis adresi seçin.',
  },
  'UI-SALE-ADDRESS-REQUIRED': {
    en: 'Please select or add a service address to continue.',
    tr: 'Devam etmek için lütfen bir servis adresi seçin veya ekleyin.',
  },

  // Step 3 — Submit order
  'UI-SALE-SUBMIT-TITLE': { en: 'Submit order', tr: 'Siparişi gönder' },
  'UI-SALE-ORDER-DETAILS': { en: 'Order details', tr: 'Sipariş detayları' },
  'UI-SALE-ORDER-NUMBER': { en: 'Order Number', tr: 'Sipariş Numarası' },
  'UI-SALE-ORDER-ITEMS': { en: 'Order items', tr: 'Sipariş kalemleri' },
  'UI-SALE-SERVICE-ADDRESS': { en: 'Service address', tr: 'Servis adresi' },
  'UI-SALE-CONFIRM-TITLE': { en: 'Submit this order?', tr: 'Bu sipariş gönderilsin mi?' },
  // The confirm BODY states what is about to happen, with the two facts the
  // mock puts there: which billing account, and for how much (FE-ADR-012 §h).
  // The account is always known in practice — the wizard cannot start without
  // one — but the mock guards the clause, so the no-account wording exists too.
  // Values come from the sale's own state (context + basket), never the mock's
  // fixture figures. Replaces the generic `MSG-SALE-ORDER-CONFIRM`, whose
  // question the TITLE now asks in the mock's own words (scope §4.33).
  'UI-SALE-CONFIRM-BODY': {
    en: 'The order will be created for billing account {accountNumber}. Total amount is {totalAmount}.',
    tr: 'Sipariş, {accountNumber} numaralı fatura hesabı için oluşturulacak. Toplam tutar: {totalAmount}.',
  },
  'UI-SALE-CONFIRM-BODY-NO-ACCOUNT': {
    en: 'The order will be created. Total amount is {totalAmount}.',
    tr: 'Sipariş oluşturulacak. Toplam tutar: {totalAmount}.',
  },
  // AC-SALE-01-15's success, announced on Customer Info AFTER the automatic
  // navigation (scope §4.33). Singular and plural are two keys chosen by the
  // sender — not a plural engine (FE-ADR-012 §h.3). `orderNumber` is the KR-12
  // number minted by order-service; `accountNumber` and `count` come from the
  // same 201 body, never from client bookkeeping.
  'UI-SALE-TOAST-ORDER-SUBMITTED-ONE': {
    en: 'Order {orderNumber} submitted successfully. 1 product added to account {accountNumber}.',
    tr: '{orderNumber} numaralı sipariş başarıyla gönderildi. {accountNumber} numaralı hesaba 1 ürün eklendi.',
  },
  'UI-SALE-TOAST-ORDER-SUBMITTED': {
    en: 'Order {orderNumber} submitted successfully. {count} products added to account {accountNumber}.',
    tr: '{orderNumber} numaralı sipariş başarıyla gönderildi. {accountNumber} numaralı hesaba {count} ürün eklendi.',
  },
  // UI-SALE-SUCCESS-TITLE / -STATUS / UI-SALE-BACK-TO-CUSTOMER were REMOVED on
  // 2026-08-05: the wizard no longer has an in-place success state to label or
  // a manual way back — it navigates on 201 (scope §4.33).

  // ---- Asynchronous SALE (ADR-018): the draft's Order Number, the
  //      in-place processing state, and the terminal failure screen.
  //      UI-SALE-PROCESSING-TITLE is the analyst's OWN MIDLWARE wording,
  //      taken verbatim (OrderContract, ADR-018 §6); the rest are
  //      PROJECT-AUTHORED — no analyst UX for this screen exists yet
  //      (ADR-018 §6's "PROJECT INTERPRETATION PENDING the analyst's final
  //      UX clarification"). ----
  'UI-SALE-ORDER-NUMBER-PENDING': { en: 'Creating order…', tr: 'Sipariş oluşturuluyor…' },
  'UI-SALE-PROCESSING-TITLE': { en: 'Order received, processing…', tr: 'Sipariş Alındı, İşleniyor…' },
  'UI-SALE-PROCESSING-HINT': {
    en: 'This can take a few moments. You may leave this page — the order will keep processing.',
    tr: 'Bu işlem biraz zaman alabilir. Bu sayfadan çıkabilirsiniz — sipariş işlenmeye devam eder.',
  },
  'UI-SALE-FAILURE-TITLE': { en: 'Order could not be completed', tr: 'Sipariş tamamlanamadı' },
  'UI-SALE-FAILURE-RETURN': { en: 'Return to Customer Info', tr: 'Müşteri Bilgisine Dön' },

  // Customer Info account-row entry point (AC-SALE-01-02: no billing account
  // ⇒ the action is not offered and the user is told to create one first).
  'UI-SALE-NO-ACCOUNT-HINT': {
    en: 'Create a billing account before starting a sale.',
    tr: 'Satış başlatmadan önce bir fatura hesabı oluşturun.',
  },

  // ---- DatePicker aria labels (first consumer: demographic edit dialog) ----
  'UI-DATE-OPEN-CALENDAR': { en: 'Open calendar', tr: 'Takvimi aç' },
  'UI-DATE-PREV-MONTH': { en: 'Previous month', tr: 'Önceki ay' },
  'UI-DATE-NEXT-MONTH': { en: 'Next month', tr: 'Sonraki ay' },
  'UI-DATE-PREV-YEAR': { en: 'Previous year', tr: 'Önceki yıl' },
  'UI-DATE-NEXT-YEAR': { en: 'Next year', tr: 'Sonraki yıl' },
} satisfies Catalog;
