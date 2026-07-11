package com.crm.customer.contact.dto;

import com.crm.customer.common.exception.MessageKeys;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** FR-CUST-03 / FR-CNTC-02: Email and Mobile Phone mandatory; Home Phone and Fax optional. */
@Getter
@Setter
public class ContactMediumRequest {

    // VR-EMAIL (FR format catalog).
    public static final String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    // VR-PHONE: starts with 0, digits only, exactly 11 digits.
    public static final String PHONE_REGEX = "^0[0-9]{10}$";
    // VR-MOBILE: starts with 05, digits only, exactly 11 digits.
    public static final String MOBILE_REGEX = "^05[0-9]{9}$";

    @NotBlank(message = MessageKeys.VAL_EMAIL)
    @Pattern(regexp = EMAIL_REGEX, message = MessageKeys.VAL_EMAIL)
    @Size(max = 254, message = MessageKeys.VAL_EMAIL)
    private String email;

    @Pattern(regexp = PHONE_REGEX, message = MessageKeys.VAL_PHONE)
    private String homePhone;

    @NotBlank(message = MessageKeys.VAL_PHONE)
    @Pattern(regexp = MOBILE_REGEX, message = MessageKeys.VAL_PHONE)
    private String mobilePhone;

    @Pattern(regexp = PHONE_REGEX, message = MessageKeys.VAL_PHONE)
    private String fax;
}
