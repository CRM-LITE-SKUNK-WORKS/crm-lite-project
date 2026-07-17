package com.crm.customer.customer.service;

import com.crm.customer.customer.dto.request.CustomerCreateRequest;
import com.crm.customer.customer.dto.request.CustomerUpdateRequest;
import com.crm.customer.customer.dto.response.CustomerDetailResponse;
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

    CustomerDetailResponse create(CustomerCreateRequest request);

    CustomerDetailResponse update(Long customerNumber, CustomerUpdateRequest request);

    void delete(Long customerNumber);
}
