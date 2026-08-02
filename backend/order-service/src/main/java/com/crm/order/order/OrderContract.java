package com.crm.order.order;

/**
 * Order-domain constants (ADR-016). There is no display-label pair here like the
 * account/product services have: FR §2.7 never renders an order status as
 * "Active"/"Passive" — AC-SALE-01-15 shows the workbook's own MIDLWARE wording,
 * "Sipariş Alındı, İşleniyor…", which is a localization catalog entry, not a
 * backend string.
 */
public final class OrderContract {

    private OrderContract() {
    }

    /** KR-12 [T] digit: NEWSALE this phase (ADR-016 §4.3). */
    public static final int SEGMENT_NEW_SALE = 1;

    /**
     * bsn_inter.description. The workbook's sample text is a human sentence
     * ("Ali Yildiz yeni urun satisi islemi"); no FR defines a format, so new rows get
     * a neutral, language-independent marker rather than a fabricated Turkish
     * sentence built from customer data (which would also be the wrong place for
     * localization — the backend returns message keys, ADR-016 §7).
     */
    public static final String NEW_SALE_DESCRIPTION = "New product sale";
}
