package com.crm.account.account;

/**
 * The immutable part of the local acct_tp contract (ADR-013 §2.5) plus the K-8
 * constants. The acct_tp IDs are contract-fixed by the Flyway seed and must never
 * be renumbered — the K-8 partial unique index in V1 depends on ID 1 = code 223.
 */
public final class AccountContract {

    private AccountContract() {
    }

    /** acct_tp contract row: code 223, "Customer Account" (K-8, never listed). */
    public static final long TYPE_CUSTOMER_ACCOUNT_ID = 1L;
    /** acct_tp contract row: code 224, "Billing Account" (the only listed type). */
    public static final long TYPE_BILLING_ACCOUNT_ID = 2L;

    /** KR-11 [T] digit: customer segment, fixed 1 this phase (individual customers). */
    public static final int SEGMENT_INDIVIDUAL = 1;

    /** K-8: fixed, non-editable account_name of the automatic 223 Customer Account. */
    public static final String CUSTOMER_ACCOUNT_NAME = "Customer Account";

    /** UI-facing status labels (AC-ACCT-01-02): derived, never stored. */
    public static final String STATUS_LABEL_ACTIVE = "Active";
    public static final String STATUS_LABEL_PASSIVE = "Passive";

    /**
     * cust_acct_prod_invl.short_code (ADR-013 §8.3). Verified against the V1 DDL
     * default and every V2/V3/V4 seed row: this is the workbook's per-table
     * short-code constant — the same convention as acct_tp.short_code — and is
     * emphatically NOT a campaign or offer code. Nothing may be inferred into it.
     */
    public static final String INVOLVEMENT_SHORT_CODE = "ACCT_PROD";
}
