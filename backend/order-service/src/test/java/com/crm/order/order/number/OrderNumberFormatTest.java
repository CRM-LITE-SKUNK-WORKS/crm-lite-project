package com.crm.order.order.number;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure-format tests for the KR-12 {@code [T][YY][SSSSSS][C]} shape (ADR-016 §4).
 *
 * <p>ADR-016 §4.5 duplicates the generator from account-service rather than
 * extracting a shared library. This class is the other half of that decision: the
 * two copies are pinned by <b>identical vectors</b>, so a change to either
 * algorithm shows up as a failing test rather than as two services quietly issuing
 * differently-shaped numbers. The vectors below are deliberately the same values
 * {@code AccountNumberFormatTest} asserts.
 */
class OrderNumberFormatTest {

    @Test
    @DisplayName("first 2026 number: segment 1, sequence 100000 -> 1261000002 (canonical KR-11/KR-12 vector)")
    void firstNumberOf2026() {
        assertThat(OrderNumberGenerator.format(1, 2026, 100000)).isEqualTo("1261000002");
    }

    @Test
    @DisplayName("identical to the KR-11 vectors — the pin that keeps the duplicated generator honest")
    void matchesTheAccountServiceVectors() {
        assertThat(OrderNumberGenerator.format(1, 2026, 100001)).isEqualTo("1261000010");
        assertThat(OrderNumberGenerator.format(1, 2026, 100002)).isEqualTo("1261000028");
        assertThat(OrderNumberGenerator.format(1, 2026, 100003)).isEqualTo("1261000036");
    }

    @Test
    @DisplayName("the seeded order number is what the production algorithm actually produces")
    void seedOrderNumber() {
        // V2 regenerates the workbook's legacy order_number 5001 (ADR-016 §8.5).
        // Asserting the literal here means the migration can never drift from the code.
        assertThat(OrderNumberGenerator.format(1, 2026, 100000)).isEqualTo("1261000002");
        // ...and the next order issued in 2026 (seq 100001, per the seeded next_value).
        assertThat(OrderNumberGenerator.format(1, 2026, 100001)).isEqualTo("1261000010");
    }

    @Test
    @DisplayName("year change flows into YY: 2027 -> 127xxxxxxC, 2030 -> 130xxxxxxC")
    void yearChange() {
        String n2027 = OrderNumberGenerator.format(1, 2027, 100000);
        assertThat(n2027).startsWith("127100000").hasSize(10);
        assertThat(LuhnCheckDigit.isValid(n2027)).isTrue();

        String n2030 = OrderNumberGenerator.format(1, 2030, 100000);
        assertThat(n2030).startsWith("130100000").hasSize(10);
        assertThat(LuhnCheckDigit.isValid(n2030)).isTrue();
    }

    @Test
    @DisplayName("every generated number is 10 digits and Luhn-valid across the sequence range")
    void alwaysTenDigitsAndLuhnValid() {
        for (int seq : new int[]{100000, 123456, 555555, 999999}) {
            String number = OrderNumberGenerator.format(1, 2026, seq);
            assertThat(number).hasSize(10).containsOnlyDigits();
            assertThat(LuhnCheckDigit.isValid(number)).isTrue();
            assertThat(number.substring(3, 9)).isEqualTo(String.valueOf(seq));
        }
    }

    @Test
    @DisplayName("a future non-1 [T] (reserved for TRANSFER/CANCEL) stays 10 digits and Luhn-valid")
    void reservedSegmentDigits() {
        // ADR-016 §4.3 reserves other T values for BSN_INTER types that have no FR
        // yet. No code issues them, but the format must already survive them —
        // otherwise "reserved" would be an empty promise.
        for (int segment : new int[]{2, 8, 9}) {
            String number = OrderNumberGenerator.format(segment, 2026, 100000);
            assertThat(number).hasSize(10).containsOnlyDigits().startsWith(String.valueOf(segment));
            assertThat(LuhnCheckDigit.isValid(number)).isTrue();
        }
    }
}
