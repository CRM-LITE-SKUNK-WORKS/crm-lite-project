package com.crm.account.lookup;

/**
 * The immutable part of the shared catalog contract (ADR-002) needed by the account
 * domain: GNL_ST GENERAL statuses only. The IDs are what active-record queries
 * filter on locally ({@code status_id = STATUS_ACTIVE_ID AND deleted_date IS NULL}),
 * so reads never need a remote call. {@link LookupCatalogService} asserts on every
 * resolve that the central catalog still agrees with these values and fails the
 * write loudly if it does not.
 */
public final class LookupContract {

    private LookupContract() {
    }

    public static final String STATUS_DOMAIN_GENERAL = "GENERAL";
    public static final String STATUS_ACTIVE = "ACTV";
    public static final String STATUS_PASSIVE = "PASV";
    public static final long STATUS_ACTIVE_ID = 1L;
    public static final long STATUS_PASSIVE_ID = 2L;
}
