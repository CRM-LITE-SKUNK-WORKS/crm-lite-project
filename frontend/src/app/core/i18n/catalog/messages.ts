import type { Catalog } from './catalog.types';

/**
 * Message catalogue (MSG-*). Two provenances, both flat and verbatim-named
 * (FE-ADR-012 §c — these keys are the backend/analyst contract and are resolved
 * against `error.messageKey`, so they are NEVER renamed).
 *
 * 1) ANALYST keys — from `CRM_Lite_FR_AC_v8_Final.docx` §3, EN + TR both approved
 *    (mock-ui-analysis §7A.6).
 * 2) PROJECT keys — framework/integration outcomes the analyst catalogue does not
 *    name (FE-ADR-008 §7, mock-ui-analysis §7A.7). EN + TR are PROJECT-AUTHORED
 *    and marked below, so analyst-approved text stays distinguishable.
 *
 * `MSG-AUTH-INVALID-CRED` is in the analyst catalogue but is a Keycloak
 * login-page concern (theme messages_en/tr.properties), never an API key — it is
 * kept for completeness but not used by Angular (mock-ui-analysis §7A.7).
 */
export const MESSAGES = {
  // ---- Analyst-approved (mock-ui-analysis §7A.6) ----
  'MSG-AUTH-INVALID-CRED': {
    en: 'Wrong user name or password. Please try again',
    tr: 'Kullanıcı adı veya şifre hatalı. Lütfen tekrar deneyin.',
  },
  'MSG-CUST-NOT-FOUND': {
    en: 'No customer found! Would you like to create the customer?',
    tr: 'Müşteri bulunamadı! Müşteriyi oluşturmak ister misiniz?',
  },
  'MSG-CUST-DUP-NATID': {
    en: 'A customer already exists with this Nationality ID.',
    tr: 'Bu Nationality ID ile kayıtlı bir müşteri zaten mevcut.',
  },
  'MSG-CUST-DELETE-CONFIRM': {
    en: 'Are you sure to delete this customer?',
    tr: 'Bu müşteriyi silmek istediğinize emin misiniz?',
  },
  'MSG-CUST-HAS-PRODUCTS': {
    en: 'Since the customer has active products, the customer cannot be deleted.',
    tr: 'Müşterinin aktif ürünleri olduğundan müşteri silinemez.',
  },
  'MSG-ADDR-IN-USE': {
    en: 'This address is linked to an active billing account or service and cannot be deleted.',
    tr: 'Bu adres aktif bir fatura hesabına veya servise bağlıdır ve silinemez.',
  },
  'MSG-ADDR-DELETE-CONFIRM': {
    en: 'Are you sure to delete this address?',
    tr: 'Bu adresi silmek istediğinize emin misiniz?',
  },
  'MSG-ACCT-DELETE-CONFIRM': {
    en: 'Are you sure to delete this billing account?',
    tr: 'Bu fatura hesabını silmek istediğinize emin misiniz?',
  },
  'MSG-ACCT-HAS-PRODUCTS': {
    en: 'Deletion could not be performed. There are active product(s) linked to this billing account.',
    tr: 'Silme işlemi gerçekleştirilemedi. Bu fatura hesabına bağlı aktif ürün(ler) var.',
  },
  'MSG-ACCT-DELETED': {
    en: 'Billing account deleted successfully.',
    tr: 'Fatura hesabı başarıyla silindi.',
  },
  'MSG-PROD-NONE': {
    en: 'No products are linked to this billing account.',
    tr: 'Bu fatura hesabına bağlı ürün bulunmamaktadır.',
  },
  'MSG-VAL-EMAIL': {
    en: 'Please enter a valid email address.',
    tr: 'Lütfen geçerli bir e-posta adresi girin.',
  },
  'MSG-VAL-PHONE': {
    en: 'Please enter a valid phone number (e.g. 05XXXXXXXXX).',
    tr: 'Lütfen geçerli bir telefon numarası girin (ör. 05XXXXXXXXX).',
  },
  'MSG-VAL-NATID': {
    en: 'Please enter a valid Nationality ID (11 digits).',
    tr: 'Lütfen geçerli bir Nationality ID girin (11 hane).',
  },
  'MSG-MERNIS-UNAVAILABLE': {
    en: 'The identity verification service is currently unavailable.',
    tr: 'Kimlik doğrulama servisine şu anda ulaşılamıyor.',
  },
  'MSG-CUST-NATID-VERIFICATION-FAILED': {
    en: 'The provided National Identity Number could not be verified.',
    tr: 'Girilen T.C. Kimlik Numarası doğrulanamadı.',
  },
  'MSG-VAL-BIRTHDATE': {
    en: 'Birth date cannot be in the future.',
    tr: 'Doğum tarihi gelecekte olamaz.',
  },
  'MSG-VAL-AGE-MIN': {
    en: 'Customer must be at least 18 years old.',
    tr: 'Müşteri en az 18 yaşında olmalıdır.',
  },
  'MSG-VAL-NAME': {
    en: 'Please enter a valid name (letters only).',
    tr: 'Lütfen geçerli bir ad girin (yalnız harf).',
  },
  'MSG-VAL-CHAR-REQUIRED': {
    en: 'This field is required.',
    tr: 'Bu alan zorunludur.',
  },
  'MSG-VAL-CHAR-FORMAT': {
    en: 'Please enter a value matching the field type.',
    tr: 'Lütfen alan tipine uygun bir değer girin.',
  },

  // ---- PROJECT-AUTHORED: no analyst wording exists (FE-ADR-008 §7) ----
  'MSG-VALIDATION-ERROR': {
    en: 'Some fields are invalid. Please review and try again.',
    tr: 'Bazı alanlar geçersiz. Lütfen gözden geçirip tekrar deneyin.',
  },
  'MSG-INTERNAL-ERROR': {
    en: 'Something went wrong. Please try again.',
    tr: 'Bir şeyler ters gitti. Lütfen tekrar deneyin.',
  },
  'MSG-SERVICE-UNAVAILABLE': {
    en: 'The service is temporarily unavailable. Please try again shortly.',
    tr: 'Servise şu anda geçici olarak ulaşılamıyor. Lütfen biraz sonra tekrar deneyin.',
  },
  'MSG-FEATURE-NOT-IMPLEMENTED': {
    en: 'This feature is not available yet.',
    tr: 'Bu özellik henüz kullanılamıyor.',
  },
  'MSG-ADDR-LAST-DELETE': {
    en: 'The last address cannot be deleted. A customer must keep at least one address.',
    tr: 'Son adres silinemez. Bir müşterinin en az bir adresi olmalıdır.',
  },
  'MSG-ADDR-PRIMARY-DELETE': {
    en: 'The primary address cannot be deleted. Set another address as primary first.',
    tr: 'Birincil adres silinemez. Önce başka bir adresi birincil yapın.',
  },
  'MSG-LOOKUP-NOT-FOUND': {
    en: 'The requested reference value was not found.',
    tr: 'İstenen referans değeri bulunamadı.',
  },
  'MSG-AUTH-UNAUTHORIZED': {
    en: 'Your session has ended. Please sign in again.',
    tr: 'Oturumunuz sona erdi. Lütfen yeniden giriş yapın.',
  },
  'MSG-AUTH-FORBIDDEN': {
    en: 'You do not have permission to perform this action.',
    tr: 'Bu işlemi gerçekleştirme yetkiniz yok.',
  },
  'MSG-AUTH-CSRF-REJECTED': {
    en: 'Your session needs to be refreshed. Please try again.',
    tr: 'Oturumunuzun yenilenmesi gerekiyor. Lütfen tekrar deneyin.',
  },
  // Phrased like the analyst's other confirmations (MSG-CUST-DELETE-CONFIRM,
  // MSG-ADDR-DELETE-CONFIRM) so every Yes/No dialog in the app reads the same.
  'MSG-AUTH-LOGOUT-CONFIRM': {
    en: 'Are you sure you want to log out?',
    tr: 'Çıkış yapmak istediğinize emin misiniz?',
  },

  // ---- account-service PROJECT-AUTHORED keys (ADR-013 §6; EN/TR verbatim from
  //      docs/architecture/account-service-decisions.md §message catalog).
  //      Backend-returned; names are the contract and are never changed.
  //      (MSG-ACCT-HAS-PRODUCTS / -DELETE-CONFIRM / -DELETED are analyst /
  //      frontend-only and already defined above.) ----
  'MSG-ACCT-NOT-FOUND': {
    en: 'Billing account not found.',
    tr: 'Fatura hesabı bulunamadı.',
  },
  'MSG-ACCT-NOT-ACTIVE': {
    en: 'This account is passive and cannot be modified.',
    tr: 'Bu hesap pasif durumda olduğundan üzerinde değişiklik yapılamaz.',
  },
  'MSG-ACCT-IMMUTABLE-FIELD': {
    en: 'The request contains fields that cannot be set or changed.',
    tr: 'İstek, atanamayan veya değiştirilemeyen alanlar içeriyor.',
  },
  'MSG-ACCT-DUP-NUMBER': {
    en: 'An account with this account number already exists.',
    tr: 'Bu hesap numarasına sahip bir hesap zaten mevcut.',
  },
  'MSG-ACCT-NUMBER-CAPACITY-EXCEEDED': {
    en: 'Account number capacity for this segment and year is exhausted.',
    tr: 'Bu segment ve yıl için hesap numarası kapasitesi tükendi.',
  },

  // ---- product-service PROJECT-AUTHORED key (docs/api/product-service.md
  //      §Message-key notes). NOT in the analyst catalog: FR-PROD-02 is always
  //      reached from a row the user just saw, so the analysts never named an
  //      unknown-product outcome. EN/TR are the backend document's own
  //      suggestion, taken verbatim. Backend-returned (404) — the name is the
  //      contract and is never changed.
  //      (MSG-PROD-NONE is the analyst key and is FRONTEND-ONLY: an account
  //      with no products is a 200 [], so the backend never sends it — it is
  //      defined above with the other analyst messages.) ----
  'MSG-PROD-NOT-FOUND': {
    en: 'Product not found.',
    tr: 'Ürün bulunamadı.',
  },

  // ---- §2.7 Product Sale (FR-SALE-01/02) ANALYST keys, EN/TR taken verbatim
  //      from the FR/AC v8-1 Final message catalogue. Every one of these is
  //      produced by product-service and RELAYED UNCHANGED by order-service
  //      (docs/api/order-service.md §Semantics): collapsing them into one
  //      generic 400 would defeat §2.7's whole point — the user must be told
  //      WHICH rule they broke. Each therefore gets its own text.
  //
  //      A valid basket is exactly three offers: one INTERNET + one RESOURCE +
  //      one ACTIVATION (AC-SALE-01-08). The NO-*/MULTI-* pairs are the two
  //      ways that can fail per service type. ----
  'MSG-SALE-DUP-OFFER': {
    en: 'This offer is already in the basket.',
    tr: 'Bu teklif sepette zaten mevcut.',
  },
  'MSG-SALE-OFFER-INACTIVE': {
    en: 'The basket contains an inactive offer. Please remove it to continue.',
    tr: 'Sepette aktif olmayan bir teklif var. Devam etmek için lütfen çıkarın.',
  },
  'MSG-SALE-NO-INTERNET': {
    en: 'An internet service offer is required to continue.',
    tr: 'Devam etmek için bir internet servisi teklifi gereklidir.',
  },
  'MSG-SALE-NO-RESOURCE': {
    en: 'A resource offer (e.g. modem) is required to continue.',
    tr: 'Devam etmek için bir kaynak (ör. modem) teklifi gereklidir.',
  },
  'MSG-SALE-NO-ACTIVATION': {
    en: 'An activation service offer is required to continue.',
    tr: 'Devam etmek için bir aktivasyon servisi teklifi gereklidir.',
  },
  'MSG-SALE-MULTI-INTERNET': {
    en: 'Only one internet service offer is allowed.',
    tr: 'Yalnızca bir internet servisi teklifi eklenebilir.',
  },
  'MSG-SALE-MULTI-RESOURCE': {
    en: 'Only one resource (modem) offer is allowed.',
    tr: 'Yalnızca bir kaynak (modem) teklifi eklenebilir.',
  },
  'MSG-SALE-MULTI-ACTIVATION': {
    en: 'Only one activation service offer is allowed.',
    tr: 'Yalnızca bir aktivasyon servisi teklifi eklenebilir.',
  },

  // FRONTEND-ONLY analyst key: the AC-SALE-01-15 confirm modal. The backend
  // never produces it (docs/api/order-service.md §Message-key notes) — it is
  // the question the client asks before it POSTs.
  'MSG-SALE-ORDER-CONFIRM': {
    en: 'Are you sure to submit this order?',
    tr: 'Bu siparişi göndermek istediğinize emin misiniz?',
  },

  // ---- order-service PROJECT-AUTHORED keys (docs/api/order-service.md
  //      §Message-key notes). The analyst catalogue names no order outcomes,
  //      because §2.7 never describes an order being looked up or refused.
  //      EN/TR for MSG-ORDER-NOT-FOUND are the backend document's own
  //      suggestion, taken verbatim; the other two are authored to the same
  //      shape. Backend-returned — the names are the contract. ----
  'MSG-ORDER-NOT-FOUND': {
    en: 'Order not found.',
    tr: 'Sipariş bulunamadı.',
  },
  'MSG-ORDER-DUP-NUMBER': {
    en: 'This order number is already in use. Please try again.',
    tr: 'Bu sipariş numarası zaten kullanımda. Lütfen tekrar deneyin.',
  },
  'MSG-ORDER-NUMBER-CAPACITY-EXCEEDED': {
    en: 'Order number capacity for this segment and year is exhausted.',
    tr: 'Bu segment ve yıl için sipariş numarası kapasitesi tükendi.',
  },

  // FRONTEND-ONLY fallback — never returned by any backend. `POST /api/orders`
  // orchestrates six services (ADR-016); when a hop is unreachable the run is
  // compensated, so the order does NOT exist and retrying is safe. The generic
  // MSG-SERVICE-UNAVAILABLE cannot say that, and saying it matters here: the
  // POST is not idempotent, so a user who wrongly believes the order might have
  // gone through would avoid the retry they should make (ADR-016 §5.3b).
  'MSG-SALE-SUBMIT-UNAVAILABLE': {
    en: 'The order could not be created because a service is temporarily unavailable. Nothing was saved — please try again shortly.',
    tr: 'Bir servise geçici olarak ulaşılamadığı için sipariş oluşturulamadı. Hiçbir kayıt yapılmadı — lütfen biraz sonra tekrar deneyin.',
  },
} satisfies Catalog;
