import type { Catalog } from './catalog.types';

/**
 * Label catalogue (LBL-*) — verbatim from the analyst source
 * `CRM_Lite_FR_AC_v8_Final.docx` §4, transcribed in mock-ui-analysis §7A.5.
 *
 * These 21 keys are the analyst contract: names are NEVER changed, grouped or
 * camel-cased (FE-ADR-012 §c). Turkish text is written as UTF-8 literals — no
 * \uXXXX escaping (FE-ADR-012 §g / mock-ui-analysis §7A.8).
 *
 * LBL-CREATE-NEW-ACCOUNT, LBL-START-NEW-SALE, LBL-ADD-TO-BASKET and LBL-SUBMIT
 * belong to FR-ACCT/FR-PROD/FR-SALE screens (out of scope, FE-ADR-013); kept in
 * the catalogue but unused in this phase.
 */
export const LABELS = {
  'LBL-LOGIN': { en: 'Login', tr: 'Giriş Yap' },
  'LBL-LOGOUT': { en: 'Logout', tr: 'Çıkış' },
  'LBL-SEARCH': { en: 'Search', tr: 'Ara' },
  'LBL-CREATE-CUSTOMER': { en: 'Create Customer', tr: 'Yeni Müşteri Oluştur' },
  'LBL-NEXT': { en: 'Next', tr: 'İleri' },
  'LBL-PREVIOUS': { en: 'Previous', tr: 'Geri' },
  'LBL-CANCEL': { en: 'Cancel', tr: 'İptal' },
  'LBL-SAVE': { en: 'Save', tr: 'Kaydet' },
  'LBL-CREATE': { en: 'Create', tr: 'Oluştur' },
  'LBL-ADD-NEW-ADDRESS': { en: 'Add New Address', tr: 'Yeni Adres Ekle' },
  'LBL-EDIT': { en: 'Edit', tr: 'Düzenle' },
  'LBL-DELETE': { en: 'Delete', tr: 'Sil' },
  'LBL-SET-PRIMARY-ADDR': { en: 'Select as a primary address', tr: 'Birincil adres olarak seç' },
  'LBL-CREATE-NEW-ACCOUNT': { en: 'Create New Account', tr: 'Yeni Hesap Oluştur' },
  'LBL-START-NEW-SALE': { en: 'Start New Sale', tr: 'Yeni Satış Başlat' },
  'LBL-ADD-TO-BASKET': { en: 'Add to Basket', tr: 'Sepete Ekle' },
  'LBL-CLEAR': { en: 'Clear', tr: 'Temizle' },
  'LBL-SUBMIT': { en: 'Submit', tr: 'Gönder' },
  'LBL-LANGUAGE': { en: 'Language', tr: 'Dil' },
  'LBL-YES': { en: 'Yes', tr: 'Evet' },
  'LBL-NO': { en: 'No', tr: 'Hayır' },
} satisfies Catalog;
