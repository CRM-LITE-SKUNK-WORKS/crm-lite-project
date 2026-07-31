package com.crm.account.account.number;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pure-format tests for the KR-11 [T][YY][SSSSSS][C] shape (ADR-014). */
class AccountNumberFormatTest {

    @Test
    @DisplayName("first 2026 number: segment 1, sequence 100000 -> 1261000002 (canonical sample)")
    void firstNumberOf2026() {
        assertThat(AccountNumberGenerator.format(1, 2026, 100000)).isEqualTo("1261000002");
    }

    @Test
    @DisplayName("seed continuation: sequence 100001..100003 -> the regenerated workbook numbers")
    void seedNumbers() {
        assertThat(AccountNumberGenerator.format(1, 2026, 100001)).isEqualTo("1261000010");
        assertThat(AccountNumberGenerator.format(1, 2026, 100002)).isEqualTo("1261000028");
        assertThat(AccountNumberGenerator.format(1, 2026, 100003)).isEqualTo("1261000036");
    }

    @Test
    @DisplayName("year change flows into YY: 2027 -> 127xxxxxxC, 2030 -> 130xxxxxxC")
    void yearChange() {
        String n2027 = AccountNumberGenerator.format(1, 2027, 100000);
        assertThat(n2027).startsWith("127100000").hasSize(10);
        assertThat(LuhnCheckDigit.isValid(n2027)).isTrue();

        String n2030 = AccountNumberGenerator.format(1, 2030, 100000);
        assertThat(n2030).startsWith("130100000").hasSize(10);
        assertThat(LuhnCheckDigit.isValid(n2030)).isTrue();
    }

    @Test
    @DisplayName("every generated number is 10 digits and Luhn-valid across the sequence range")
    void alwaysTenDigitsAndLuhnValid() {
        for (int seq : new int[]{100000, 123456, 555555, 999999}) {
            String number = AccountNumberGenerator.format(1, 2026, seq);
            assertThat(number).hasSize(10).containsOnlyDigits();
            assertThat(LuhnCheckDigit.isValid(number)).isTrue();
            assertThat(number.substring(3, 9)).isEqualTo(String.valueOf(seq));
        }
    }

    @Test
    @DisplayName("century rollover keeps two YY digits (2100 -> 00)")
    void centuryRollover() {
        String number = AccountNumberGenerator.format(1, 2100, 100000);
        assertThat(number).startsWith("100100000").hasSize(10);
        assertThat(LuhnCheckDigit.isValid(number)).isTrue();
    }

    /**
     * V4 fixture expansion (docs/testing/seed-fixture-catalog.md): sequence
     * 100004..100017 contiguously continuing the V2 seed's next_value=100004,
     * across customers 1004-1008/1011. Every literal committed to the V4
     * migration is re-derived here from the SAME production algorithm and
     * Luhn-validated — the migration numbers are not hand-typed.
     */
    @Test
    @DisplayName("V4 fixture expansion: 14 contiguous numbers (100004..100017), all Luhn-valid and pairwise distinct")
    void v4FixtureAccountNumbersAreValidAndDistinct() {
        String[] expected = {
                "1261000044", "1261000051", "1261000069", // customer 1004 (223, 224, 224)
                "1261000077", "1261000085",                // customer 1005 (223, 224)
                "1261000093", "1261000101",                // customer 1006 (223, 224)
                "1261000119", "1261000127", "1261000135",   // customer 1007 (223, 224, 224)
                "1261000143", "1261000150",                // customer 1008 (223, 224)
                "1261000168", "1261000176",                // customer 1011 (223, 224)
        };
        java.util.Set<String> seen = new java.util.HashSet<>();
        int seq = 100004;
        for (String number : expected) {
            String derived = AccountNumberGenerator.format(1, 2026, seq);
            assertThat(derived).isEqualTo(number);
            assertThat(LuhnCheckDigit.isValid(derived)).isTrue();
            assertThat(seen.add(derived)).as("no duplicate account number: " + derived).isTrue();
            seq++;
        }
        // Next value after the fixture range (matches the V4 migration's GREATEST target).
        assertThat(seq).isEqualTo(100018);
    }
}
