package com.crm.customer.contact.service;

import com.crm.customer.contact.dto.ContactMediumRequest;
import com.crm.customer.contact.dto.ContactMediumResponse;

public interface ContactMediumService {

    ContactMediumResponse get(Long customerNumber);

    ContactMediumResponse update(Long customerNumber, ContactMediumRequest request);
}
