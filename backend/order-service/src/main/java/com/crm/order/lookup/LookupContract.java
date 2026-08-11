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
     * GNL_ST ORDER domain. All three are written since ADR-018 — the analyst's
     * clarification that "records should already be created in the relevant tables
     * [but] because the order has not yet been completed/approved their status is not
     * MIDLWARE" made the workbook's existing WAIT row the pre-submit status:
     * <ul>
     *   <li><b>WAIT</b> — a started sale whose Submit has not been confirmed. The order
     *       and its KR-12 number already exist, which is what lets the Submit screen show
     *       the Order Number (AC-SALE-01-12). A WAIT order can never start fulfilment on
     *       its own: nothing consumes it and no command exists for it (ADR-018 §6).
     *   <li><b>MIDLWARE</b> — from Submit onwards; the workbook's
     *       <i>"Siparis Alindi Isleniyor..."</i>, the state AC-SALE-01-15 tells the user
     *       about, and the one no accepted AC moves an order out of. A COMPLETED sale
     *       therefore stays MIDLWARE.
     *   <li><b>CANCELLED</b> — system-triggered only: an abandoned/expired draft, or a
     *       terminally compensated sale. No endpoint lets a user cancel a SUBMITTED
     *       order; KR-7 keeps that out of phase (ADR-018 §6).
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
