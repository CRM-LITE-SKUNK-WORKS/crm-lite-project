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
} satisfies Catalog;
