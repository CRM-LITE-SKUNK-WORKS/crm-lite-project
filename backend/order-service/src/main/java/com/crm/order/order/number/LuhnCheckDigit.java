package com.crm.order.order.number;

/**
 * Standard Luhn (mod 10) check digit over a numeric payload (ADR-014 §2).
 * Canonical KR-12 vector (identical algorithm, ADR-016 §4.2): payload "126100000" → check digit 2 → "1261000002".
 */
public final class LuhnCheckDigit {

    private LuhnCheckDigit() {
    }

    /** Check digit to append to {@code payload} (digits only). */
    public static int compute(String payload) {
        int sum = 0;
        boolean doubleIt = true; // rightmost payload digit is doubled when the check digit is appended
        for (int i = payload.length() - 1; i >= 0; i--) {
            int digit = digitAt(payload, i);
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return (10 - sum % 10) % 10;
    }

    /** Standard Luhn validation of a complete number (payload + check digit). */
    public static boolean isValid(String fullNumber) {
        int sum = 0;
        boolean doubleIt = false;
        for (int i = fullNumber.length() - 1; i >= 0; i--) {
            int digit = digitAt(fullNumber, i);
            if (doubleIt) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }

    private static int digitAt(String value, int index) {
        char c = value.charAt(index);
        if (c < '0' || c > '9') {
            throw new IllegalArgumentException("Not a numeric string: " + value);
        }
        return c - '0';
    }
}
