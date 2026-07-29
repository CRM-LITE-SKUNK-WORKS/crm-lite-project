package com.crm.customer.customer.service;

import com.crm.customer.customer.dto.request.CustomerCreateRequest;
import com.crm.customer.customer.dto.request.CustomerUpdateRequest;
import com.crm.customer.customer.dto.response.CustomerDetailResponse;
import com.crm.customer.customer.dto.response.NationalityIdAvailabilityResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerService {

    /**
     * ADR-005 list/filter contract: with no criteria this is the paginated browse of
     * ALL active customers; with criteria it filters (KR-01 semantics). Every row
     * carries the full detail contract.
     */
    Page<CustomerDetailResponse> search(String firstName, String lastName, String nationalityId,
                                        Long customerNumber, String gsmNumber,
                                        String accountNumber, String orderNumber, Pageable pageable);

    CustomerDetailResponse getByCustomerNumber(Long customerNumber);

    /**
     * ADR-005 §Addendum: read-only availability probe for the create screen. Reports
     * the ADR-003 rule as it really is — soft-deleted holders included — which no
     * other read endpoint can, since the list filters to active customers. Advisory
     * only: {@link #create} remains the authority.
     */
    NationalityIdAvailabilityResponse checkNationalityIdAvailability(String nationalityId);

    CustomerDetailResponse create(CustomerCreateRequest request);

    CustomerDetailResponse update(Long customerNumber, CustomerUpdateRequest request);

    void delete(Long customerNumber);
}
