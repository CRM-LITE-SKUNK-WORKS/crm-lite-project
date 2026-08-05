package com.crm.customer.customer.repository;

/**
 * The resolved state of a KR-02 <b>child-record</b> search criterion — Account Number
 * or Order Number — whose record lives in another service's database.
 *
 * <p>customer-service cannot express those criteria as SQL: {@code cust_acct} and
 * {@code cust_ord} are owned by account-service and order-service, and the
 * database-per-service rule forbids reading, joining or copying them. The owning
 * service resolves the number to its owner (an exact, unique match, so at most one
 * customer) and the outcome arrives here as a plain business customer number, which
 * the local query CAN express against {@code cust.customer_number}.
 *
 * <p>The three states must stay distinct, and in particular {@link #unmatched()} must
 * never collapse into {@link #absent()}: dropping a filled-but-unresolved number would
 * silently turn {@code ?accountNumber=<unknown>} into the criterion-less browse mode
 * and answer "all active customers" where the user asked for exactly one.
 */
public record ChildRecordCriterion(boolean present, Long ownerCustomerNumber) {

    private static final ChildRecordCriterion ABSENT = new ChildRecordCriterion(false, null);

    /** The field was not filled — it contributes no predicate at all. */
    public static ChildRecordCriterion absent() {
        return ABSENT;
    }

    /** Filled, but the owning service knows no live record with that number. */
    public static ChildRecordCriterion unmatched() {
        return new ChildRecordCriterion(true, null);
    }

    /** Filled and resolved to the customer that owns the record. */
    public static ChildRecordCriterion ownedBy(long ownerCustomerNumber) {
        return new ChildRecordCriterion(true, ownerCustomerNumber);
    }
}
