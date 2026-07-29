package com.crm.customer.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Enforces {@link AllowedPageSize}. The violation message is derived from
 * {@link AllowedPageSize#ALLOWED} so the whitelist and the text a client sees can
 * never drift apart.
 */
public class AllowedPageSizeValidator implements ConstraintValidator<AllowedPageSize, Integer> {

    private static final String ALLOWED_TEXT = Arrays.stream(AllowedPageSize.ALLOWED)
            .mapToObj(Integer::toString)
            .collect(Collectors.joining(", "));

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        // An absent parameter never reaches here as null (the @RequestParam default
        // applies first), but null stays valid so the constraint is reusable.
        if (value == null || IntStream.of(AllowedPageSize.ALLOWED).anyMatch(allowed -> allowed == value)) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("must be one of " + ALLOWED_TEXT)
                .addConstraintViolation();
        return false;
    }
}
