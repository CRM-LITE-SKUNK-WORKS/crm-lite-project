package com.crm.product.lookup;

/**
 * The immutable part of the shared catalog contract (ADR-002) needed by the product
 * domain: GNL_ST GENERAL statuses plus the GNL_TP SERVICE_TYPE and CATALOG_TYPE
 * rows the product schema references. The IDs are what active-record queries filter
 * on locally ({@code status_id = STATUS_ACTIVE_ID AND deleted_date IS NULL}), so
 * reads never need a remote call.
 *
 * <p>These constants stay the only coupling used by <b>reads</b> (ADR-002 §8), so no
 * query makes a remote call per row. <b>Writes</b> go through the full
 * {@link LookupCatalogService} boundary introduced with the FR-SALE slice
 * (ADR-015 §4.1) — the Phase A note that "this service carries no lookup HTTP
 * client" no longer holds, and no status may be persisted without resolving it.
 */
public final class LookupContract {

    private LookupContract() {
    }

    public static final String STATUS_DOMAIN_GENERAL = "GENERAL";
    public static final String STATUS_ACTIVE = "ACTV";
    public static final String STATUS_PASSIVE = "PASV";
    public static final long STATUS_ACTIVE_ID = 1L;
    public static final long STATUS_PASSIVE_ID = 2L;

    /**
     * GNL_ST PNDG (id 6, domain PROD) — a workbook row reserved since day one and
     * never written by any code until the FR-SALE write slice. Meaning given by
     * ADR-015 §5.2: <i>reserved by an in-flight sale, not yet committed.</i> No new
     * catalog row was invented; the semantics are the project decision.
     */
    public static final String STATUS_DOMAIN_PROD = "PROD";
    public static final String STATUS_PENDING = "PNDG";
    public static final long STATUS_PENDING_ID = 6L;

    /** GNL_TP SERVICE_TYPE contract rows (workbook: 10/11/12, never renumbered). */
    public static final long SERVICE_TYPE_INTERNET_ID = 10L;
    public static final long SERVICE_TYPE_RESOURCE_ID = 11L;
    public static final long SERVICE_TYPE_ACTIVATION_ID = 12L;
    public static final String SERVICE_TYPE_INTERNET = "INTERNET";
    public static final String SERVICE_TYPE_RESOURCE = "RESOURCE";
    public static final String SERVICE_TYPE_ACTIVATION = "ACTIVATION";

    /** GNL_TP CATALOG_TYPE contract rows (referenced by prod_catal seed data). */
    public static final long CATALOG_TYPE_INDIVIDUAL_ID = 5L;
    public static final long CATALOG_TYPE_CORPORATE_ID = 6L;

    /**
     * Maps a stored external SERVICE_TYPE reference to its contract short code
     * ("derive the service type through the spec" rule — the offer has no
     * service-type column of its own). Unknown ids map to null rather than failing
     * a read: the catalog is the authority for additions.
     */
    public static String serviceTypeCode(Long serviceTypeId) {
        if (serviceTypeId == null) {
            return null;
        }
        if (serviceTypeId == SERVICE_TYPE_INTERNET_ID) {
            return SERVICE_TYPE_INTERNET;
        }
        if (serviceTypeId == SERVICE_TYPE_RESOURCE_ID) {
            return SERVICE_TYPE_RESOURCE;
        }
        if (serviceTypeId == SERVICE_TYPE_ACTIVATION_ID) {
            return SERVICE_TYPE_ACTIVATION;
        }
        return null;
    }
}
