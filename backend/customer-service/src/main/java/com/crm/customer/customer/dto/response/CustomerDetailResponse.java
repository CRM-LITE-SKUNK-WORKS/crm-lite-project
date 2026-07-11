package com.crm.customer.customer.dto.response;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Customer Info screen payload (FR-CUST-02). {@code customerNumber} is the business
 * identifier; {@code status} is the GNL_ST short code (display names/localization are
 * a frontend catalog concern); {@code role} is ROLE.role_name ("Customer").
 */
@Getter
@AllArgsConstructor
public class CustomerDetailResponse {

    private final Long customerNumber;
    private final String firstName;
    private final String middleName;
    private final String lastName;
    private final String fatherName;
    private final String motherName;
    private final LocalDate birthDate;
    private final String gender;
    private final String nationalityId;
    private final String role;
    private final String status;
}
