package com.crm.customer.lookup;

/**
 * The immutable part of the shared catalog contract (ADR-002): short codes, domains
 * and the explicit IDs enumerated in the Final entity/seed workbook and seeded by
 * lookup-service's migrations.
 *
 * The IDs listed here are what active-record queries filter on locally
 * (e.g. {@code status_id = STATUS_ACTIVE_ID AND deleted_date IS NULL}), so reads
 * never need a remote call. {@link LookupCatalogService} asserts on every resolve
 * that the central catalog still agrees with these values and fails the write loudly
 * if it does not (a renumbered central catalog is a broken contract, not something
 * to paper over).
 */
public final class LookupContract {

    private LookupContract() {
    }

    // GNL_ST domains and contract entries
    public static final String STATUS_DOMAIN_GENERAL = "GENERAL";
    public static final String STATUS_ACTIVE = "ACTV";
    public static final String STATUS_PASSIVE = "PASV";
    public static final long STATUS_ACTIVE_ID = 1L;
    public static final long STATUS_PASSIVE_ID = 2L;

    // GNL_TP domains and contract entries
    public static final String TYPE_DOMAIN_GENDER = "GENDER";
    public static final String TYPE_DOMAIN_PARTY_TYPE = "PARTY_TYPE";
    public static final String TYPE_MALE = "MALE";
    public static final String TYPE_FEMALE = "FEMALE";
    public static final String TYPE_INDIVIDUAL = "INDV";
    public static final long TYPE_MALE_ID = 1L;
    public static final long TYPE_FEMALE_ID = 2L;
    public static final long TYPE_INDIVIDUAL_ID = 3L;
}
