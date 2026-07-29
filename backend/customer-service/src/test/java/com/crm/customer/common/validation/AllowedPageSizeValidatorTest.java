package com.crm.customer.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * KR-04 whitelist, unit level — the Docker-backed integration test covers the same
 * rule end to end through HTTP.
 */
class AllowedPageSizeValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private record Query(@AllowedPageSize Integer size) { }

    @ParameterizedTest
    @ValueSource(ints = {15, 30, 50})
    void acceptsWhitelistedSizes(int size) {
        assertThat(validator.validate(new Query(size))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 14, 17, 20, 100, 999999, -1})
    void rejectsEverythingElse(int size) {
        Set<ConstraintViolation<Query>> violations = validator.validate(new Query(size));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("must be one of 15, 30, 50");
    }

    @Test
    @DisplayName("null stays valid: an absent size falls back to the @RequestParam default")
    void acceptsNull() {
        assertThat(validator.validate(new Query(null))).isEmpty();
    }

    @Test
    @DisplayName("the @RequestParam default is itself whitelisted")
    void defaultIsWhitelisted() {
        assertThat(Integer.parseInt(AllowedPageSize.DEFAULT_AS_STRING)).isEqualTo(AllowedPageSize.DEFAULT);
        assertThat(validator.validate(new Query(AllowedPageSize.DEFAULT))).isEmpty();
    }
}
