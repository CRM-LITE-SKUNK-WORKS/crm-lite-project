package com.crm.product.product;

/**
 * Product-domain display constants (AC-PROD-01-03). There is deliberately NO
 * product-number generation rule here: the public product identifier is the
 * internal prod.id — KR-11-style business numbers exist only for accounts.
 */
public final class ProductContract {

    private ProductContract() {
    }

    /** UI-facing status labels (AC-PROD-01-03): derived, never stored. */
    public static final String STATUS_LABEL_ACTIVE = "Active";
    public static final String STATUS_LABEL_PASSIVE = "Passive";
}
