package com.crm.product.lookup;

/**
 * The immutable part of the shared catalog contract (ADR-002) needed by the product
 * domain: GNL_ST GENERAL statuses plus the GNL_TP SERVICE_TYPE and CATALOG_TYPE
 * rows the product schema references. The IDs are what active-record queries filter
 * on locally ({@code status_id = STATUS_ACTIVE_ID AND deleted_date IS NULL}), so
 * reads never need a remote call.
 *
 * <p>Phase A is entirely read-only, so — unlike customer/account-service — this
 * service deliberately carries NO lookup HTTP client: there is no write that would
 * need a live catalog resolve, and these contract constants are the only coupling.
 * A future write slice must introduce the full client/service boundary
 * (LookupCatalogClient pattern) before persisting any status.
 */
public final class LookupContract {

    private LookupContract() {
    }

    public static final String STATUS_DOMAIN_GENERAL = "GENERAL";
    public static final String STATUS_ACTIVE = "ACTV";
    public static final String STATUS_PASSIVE = "PASV";
    public static final long STATUS_ACTIVE_ID = 1L;
    public static final long STATUS_PASSIVE_ID = 2L;

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
