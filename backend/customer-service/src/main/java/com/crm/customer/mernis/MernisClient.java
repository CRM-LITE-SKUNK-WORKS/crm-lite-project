package com.crm.customer.mernis;

import java.time.LocalDate;

/**
 * Boundary to the fake MERNIS/KPS verification service (KR-10). Mockable in tests.
 * Implementations must fail closed: transport problems raise
 * {@link MernisUnavailableException}, never a silent "verified".
 */
public interface MernisClient {

    /** @return true when MERNIS verified the identity, false when it rejected it. */
    boolean verify(String nationalityId, String firstName, String lastName, LocalDate birthDate);
}
