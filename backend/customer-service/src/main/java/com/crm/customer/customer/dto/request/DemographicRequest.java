package com.crm.customer.customer.dto.request;

import com.crm.customer.common.exception.MessageKeys;
import com.crm.customer.customer.dto.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Demographic block shared by create (nested) and update (flat) flows — FR-CUST-03/04.
 * Leading/trailing whitespace is trimmed pre-validation by JacksonConfig's global
 * string-trimming deserializer (VR-NAME requires trim-then-validate).
 */
@Getter
@Setter
public class DemographicRequest {

    // VR-NAME: letters only (incl. Turkish), single spaces between words, length 1-50.
    public static final String NAME_REGEX = "^[A-Za-zÇĞİÖŞÜçğıöşü]+( [A-Za-zÇĞİÖŞÜçğıöşü]+)*$";
    // VR-NATID: exactly 11 digits.
    public static final String NATIONALITY_ID_REGEX = "^[0-9]{11}$";

    @NotBlank(message = MessageKeys.VAL_NAME)
    @Pattern(regexp = NAME_REGEX, message = MessageKeys.VAL_NAME)
    @Size(max = 50, message = MessageKeys.VAL_NAME)
    private String firstName;

    @Pattern(regexp = NAME_REGEX, message = MessageKeys.VAL_NAME)
    @Size(max = 50, message = MessageKeys.VAL_NAME)
    private String middleName;

    @NotBlank(message = MessageKeys.VAL_NAME)
    @Pattern(regexp = NAME_REGEX, message = MessageKeys.VAL_NAME)
    @Size(max = 50, message = MessageKeys.VAL_NAME)
    private String lastName;

    @Pattern(regexp = NAME_REGEX, message = MessageKeys.VAL_NAME)
    @Size(max = 50, message = MessageKeys.VAL_NAME)
    private String fatherName;

    @Pattern(regexp = NAME_REGEX, message = MessageKeys.VAL_NAME)
    @Size(max = 50, message = MessageKeys.VAL_NAME)
    private String motherName;

    @NotNull(message = MessageKeys.VAL_BIRTHDATE)
    private LocalDate birthDate;

    // KR-05: only Male/Female; no message key exists for a missing gender in the catalog.
    @NotNull(message = "gender is required")
    private Gender gender;

    @NotBlank(message = MessageKeys.VAL_NATID)
    @Pattern(regexp = NATIONALITY_ID_REGEX, message = MessageKeys.VAL_NATID)
    private String nationalityId;
}
