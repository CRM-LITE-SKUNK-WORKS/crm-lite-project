package com.crm.customer.address.service;

import com.crm.customer.address.dto.AddressRequest;
import com.crm.customer.address.dto.AddressResponse;
import java.util.List;

public interface AddressService {

    List<AddressResponse> list(Long customerNumber);

    /** Service-to-service resolution of one ACTIVE address by its public id (see InternalAddressController). */
    AddressResponse getActiveAddress(Long addressId);

    AddressResponse add(Long customerNumber, AddressRequest request);

    AddressResponse update(Long customerNumber, Long addressId, AddressRequest request);

    void delete(Long customerNumber, Long addressId);

    AddressResponse setPrimary(Long customerNumber, Long addressId);
}
