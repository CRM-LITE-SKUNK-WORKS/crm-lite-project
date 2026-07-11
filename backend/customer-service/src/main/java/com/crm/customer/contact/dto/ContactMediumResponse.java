package com.crm.customer.contact.dto;

import com.crm.customer.contact.entity.ContactMedium;

public record ContactMediumResponse(String email, String homePhone, String mobilePhone, String fax) {

    public static ContactMediumResponse from(ContactMedium contact) {
        return new ContactMediumResponse(contact.getEmail(), contact.getHomePhone(),
                contact.getMobilePhone(), contact.getFax());
    }
}
