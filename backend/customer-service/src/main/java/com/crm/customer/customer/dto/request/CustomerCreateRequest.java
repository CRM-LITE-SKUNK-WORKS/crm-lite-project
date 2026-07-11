package com.crm.customer.customer.dto.request;

import com.crm.customer.address.dto.AddressRequest;
import com.crm.customer.contact.dto.ContactMediumRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Full atomic create payload (FR-CUST-03 / AC-CUST-03-21): the wizard keeps
 * Demographic → Address → Contact state client-side and submits everything in one
 * request; PARTY, IND, PARTY_ROLE, CUST, ADDR and CNTC_MEDIUM are persisted in a
 * single local transaction or not at all.
 */
@Getter
@Setter
public class CustomerCreateRequest {

    @NotNull(message = "demographic is required")
    @Valid
    private DemographicRequest demographic;

    @NotEmpty(message = "at least one address is required")
    @Valid
    private List<AddressRequest> addresses;

    @NotNull(message = "contactMedium is required")
    @Valid
    private ContactMediumRequest contactMedium;
}
