package com.crm.customer.contact.service.impl;

import com.crm.customer.common.AuditActor;
import com.crm.customer.common.exception.BusinessException;
import com.crm.customer.common.exception.MessageKeys;
import com.crm.customer.contact.dto.ContactMediumRequest;
import com.crm.customer.contact.dto.ContactMediumResponse;
import com.crm.customer.contact.entity.ContactMedium;
import com.crm.customer.contact.repository.ContactMediumRepository;
import com.crm.customer.contact.service.ContactMediumService;
import com.crm.customer.customer.entity.Customer;
import com.crm.customer.customer.rules.CustomerBusinessRules;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactMediumServiceImpl implements ContactMediumService {

    private final ContactMediumRepository contactMediumRepository;
    private final CustomerBusinessRules customerBusinessRules;

    @Override
    public ContactMediumResponse get(Long customerNumber) {
        return ContactMediumResponse.from(resolveContact(customerNumber));
    }

    /** FR-CNTC-02: update-in-place; the single contact row per party never multiplies. */
    @Override
    @Transactional
    public ContactMediumResponse update(Long customerNumber, ContactMediumRequest request) {
        ContactMedium contact = resolveContact(customerNumber);
        contact.setEmail(request.getEmail());
        contact.setHomePhone(request.getHomePhone());
        contact.setMobilePhone(request.getMobilePhone());
        contact.setFax(request.getFax());
        contact.markUpdated(AuditActor.SYSTEM);
        return ContactMediumResponse.from(contact);
    }

    private ContactMedium resolveContact(Long customerNumber) {
        Customer customer = customerBusinessRules.checkCustomerExistsAndActive(customerNumber);
        Long partyId = customer.getPartyRole().getParty().getId();
        return contactMediumRepository.findByPartyIdAndDeletedDateIsNull(partyId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.CUST_NOT_FOUND,
                        "Contact medium not found for customer " + customerNumber));
    }
}
