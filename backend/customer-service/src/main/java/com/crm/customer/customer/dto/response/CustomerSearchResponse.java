package com.crm.customer.customer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AC-CUST-01-05 result row. {@code customerId} carries the BUSINESS customer number
 * (CUST.customer_number, e.g. 1001) — the internal database id is never exposed.
 */
@Getter
@AllArgsConstructor
public class CustomerSearchResponse {

    private final Long customerId;
    private final String firstName;
    private final String middleName;
    private final String lastName;
    private final String role;
    private final String nationalityId;
}
