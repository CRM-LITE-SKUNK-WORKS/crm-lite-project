package com.crm.order.lookup;

/**
 * The immutable part of the shared catalog contract (ADR-002) needed by the order
 * domain: the GNL_ST ORDER-domain statuses plus GENERAL ACTV, and the GNL_TP
 * BSN_INTER_TYPE rows. The IDs are what local queries filter on, so reads never
 * need a remote call (ADR-002 §8); writes resolve through {@link LookupCatalogService}
 * and fail closed.
 *
 * <p>These IDs are contract data from the Final workbook and are never renumbered.
 */
public final class LookupContract {

    private LookupContract() {
    }

    public static final String STATUS_DOMAIN_GENERAL = "GENERAL";
    public static final String STATUS_ACTIVE = "ACTV";
    public static final long STATUS_ACTIVE_ID = 1L;
    public static final String STATUS_PASSIVE = "PASV";
    public static final long STATUS_PASSIVE_ID = 2L;

    /**
     * GNL_ST ORDER domain. Only two of the three are ever written (ADR-016 §6):
     * <ul>
     *   <li><b>MIDLWARE</b> — the status every order is created with and the only one
     *       a user ever sees. Its workbook name is literally
     *       <i>"Siparis Alindi Isleniyor..."</i>, which is the state AC-SALE-01-15
     *       tells the user about, and no AC moves an order out of it.
     *   <li><b>CANCELLED</b> — system-triggered compensation ONLY (ADR-016 §5). No
     *       endpoint lets a user cancel an order; KR-7 keeps that out of phase.
     *   <li><b>WAIT</b> — never written. No AC references it; the constant exists so
     *       a future reader sees the omission is deliberate, not an oversight.
     * </ul>
     */
    public static final String STATUS_DOMAIN_ORDER = "ORDER";
    public static final String STATUS_WAITING = "WAIT";
    public static final long STATUS_WAITING_ID = 3L;
    public static final String STATUS_MIDDLEWARE = "MIDLWARE";
    public static final long STATUS_MIDDLEWARE_ID = 4L;
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final long STATUS_CANCELLED_ID = 5L;

    /**
     * GNL_TP BSN_INTER_TYPE. Only NEWSALE is ever written this phase (ADR-016 §6/§8.2):
     * no FR/AC covers transfer or cancellation, so building them would be inventing
     * requirements. The column exists and is populated, so a future flow needs no
     * migration.
     */
    public static final String TYPE_DOMAIN_BSN_INTER = "BSN_INTER_TYPE";
    public static final String TYPE_NEW_SALE = "NEWSALE";
    public static final long TYPE_NEW_SALE_ID = 7L;
    public static final long TYPE_TRANSFER_ID = 8L;
    public static final long TYPE_CANCEL_ID = 9L;
}
