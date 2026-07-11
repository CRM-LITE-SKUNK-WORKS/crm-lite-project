package com.crm.customer.mernis;

/** KR-10: MERNIS verification failed — the customer must not be created. */
public class MernisRejectedException extends RuntimeException {

    public MernisRejectedException(String message) {
        super(message);
    }
}
