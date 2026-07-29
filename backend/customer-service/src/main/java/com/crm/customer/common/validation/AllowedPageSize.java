package com.crm.customer.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * KR-04 page-size whitelist: {@code GET /api/customers} accepts only 15, 30 and 50.
 * Anything else (17, 999999, 0, ...) is a 400 with MSG-VALIDATION-ERROR, never a
 * silently applied size and never a 500 — an unvalidated {@code size} reaches
 * {@code PageRequest.of}, which throws IllegalArgumentException for size &lt; 1 and
 * used to surface as an Internal Server Error.
 *
 * <p>This annotation is the single source of truth for the policy: {@link #ALLOWED}
 * drives both the check and the violation message, and {@link #DEFAULT_AS_STRING}
 * feeds the controller's {@code @RequestParam(defaultValue = ...)}, which needs a
 * compile-time String constant. The default is a member of ALLOWED by construction,
 * so the default can never fall outside the whitelist.
 *
 * <p>Supersedes the API default of 20 recorded in ADR-005 §Recorded discrepancy;
 * see ADR-005 §Amendment (2026-07-29).
 */
@Documented
@Constraint(validatedBy = AllowedPageSizeValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedPageSize {

    /** KR-04 default Per Page. */
    int DEFAULT = 15;

    /** {@link #DEFAULT} as a compile-time String constant for {@code @RequestParam}. */
    String DEFAULT_AS_STRING = "15";

    /** KR-04 selectable Per Page values; the validator rejects everything else. */
    int[] ALLOWED = {DEFAULT, 30, 50};

    /** Unused in practice: the validator builds the message from {@link #ALLOWED}. */
    String message() default "invalid page size";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
