package com.crm.customer.customer.service;

import com.crm.customer.customer.dto.request.CustomerCreateRequest;
import com.crm.customer.customer.dto.request.CustomerUpdateRequest;
import com.crm.customer.customer.dto.response.CustomerDetailResponse;
import com.crm.customer.customer.dto.response.CustomerSearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerService {

    Page<CustomerSearchResponse> search(String firstName, String lastName, String nationalityId,
                                        Long customerNumber, String gsmNumber,
                                        String accountNumber, String orderNumber, Pageable pageable);

    CustomerDetailResponse getByCustomerNumber(Long customerNumber);

    CustomerDetailResponse create(CustomerCreateRequest request);

    CustomerDetailResponse update(Long customerNumber, CustomerUpdateRequest request);

    void delete(Long customerNumber);
}
