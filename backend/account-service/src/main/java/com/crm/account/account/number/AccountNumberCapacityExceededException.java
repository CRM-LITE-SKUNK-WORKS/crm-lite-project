package com.crm.account.account.number;

/**
 * The KR-11 per-segment/per-year sequence is exhausted (issued value would exceed
 * 999999). A documented domain error — mapped to 409
 * MSG-ACCT-NUMBER-CAPACITY-EXCEEDED, never a raw 500 (ADR-014 §6). The surrounding
 * transaction rolls back, taking the sequence increment with it.
 */
public class AccountNumberCapacityExceededException extends RuntimeException {

    public AccountNumberCapacityExceededException(int segment, int year) {
        super("Account number capacity for segment " + segment + " and year " + year + " is exhausted");
    }
}
