package com.crm.customer.customer.dto;

import com.crm.customer.lookup.LookupContract;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * API-level gender value (KR-05: exactly "Male"/"Female", accepted case-insensitively,
 * always rendered as "Male"/"Female"). Not a JPA type: the database stores the central
 * GNL_TP catalog ID (ADR-002), resolved through the lookup client at write time using
 * {@link #lookupCode()}.
 */
public enum Gender {
    MALE("Male", LookupContract.TYPE_MALE),
    FEMALE("Female", LookupContract.TYPE_FEMALE);

    private final String apiValue;
    private final String lookupCode;

    Gender(String apiValue, String lookupCode) {
        this.apiValue = apiValue;
        this.lookupCode = lookupCode;
    }

    @JsonValue
    public String getApiValue() {
        return apiValue;
    }

    /** GNL_TP short code (domain GENDER) used for catalog resolution. */
    public String lookupCode() {
        return lookupCode;
    }

    @JsonCreator
    public static Gender fromApiValue(String value) {
        for (Gender gender : values()) {
            if (gender.apiValue.equalsIgnoreCase(value)) {
                return gender;
            }
        }
        throw new IllegalArgumentException("Invalid gender value: " + value);
    }

    /** Reverse mapping for responses; the IDs are contract-immutable (ADR-002). */
    public static Gender fromTypeId(long genderTypeId) {
        if (genderTypeId == LookupContract.TYPE_MALE_ID) {
            return MALE;
        }
        if (genderTypeId == LookupContract.TYPE_FEMALE_ID) {
            return FEMALE;
        }
        throw new IllegalStateException("gender_id " + genderTypeId + " is not a contract GENDER id");
    }
}
