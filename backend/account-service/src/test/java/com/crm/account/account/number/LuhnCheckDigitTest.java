package com.crm.account.account.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LuhnCheckDigitTest {

    @Test
    @DisplayName("KR-11 canonical sample: payload 126100000 -> check digit 2 -> 1261000002 (ADR-014 §2)")
    void canonicalSample() {
        assertThat(LuhnCheckDigit.compute("126100000")).isEqualTo(2);
        assertThat(LuhnCheckDigit.isValid("1261000002")).isTrue();
    }

    @Test
    @DisplayName("computed check digit always yields a Luhn-valid full number")
    void computeAndValidateRoundTrip() {
        for (String payload : new String[]{"126100001", "126100004", "127100000", "126999999", "100000000", "999999999"}) {
            int check = LuhnCheckDigit.compute(payload);
            assertThat(check).isBetween(0, 9);
            assertThat(LuhnCheckDigit.isValid(payload + check))
                    .as("payload %s + check %s", payload, check)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("a single-digit corruption is detected")
    void singleDigitErrorDetected() {
        // 1261000002 is valid; flip one digit.
        assertThat(LuhnCheckDigit.isValid("1261000003")).isFalse();
        assertThat(LuhnCheckDigit.isValid("1261000012")).isFalse();
        assertThat(LuhnCheckDigit.isValid("2261000002")).isFalse();
    }

    @Test
    @DisplayName("non-numeric input is rejected loudly")
    void nonNumericRejected() {
        assertThatThrownBy(() -> LuhnCheckDigit.compute("12610000x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
