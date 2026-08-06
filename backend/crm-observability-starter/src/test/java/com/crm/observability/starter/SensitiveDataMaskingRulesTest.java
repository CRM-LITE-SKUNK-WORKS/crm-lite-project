package com.crm.observability.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Requirement 14 ("sensitive-data log masking"): proves the masking regexes
 * actually redact JWTs, Bearer headers, cookies, full National IDs and
 * credential-shaped key/value pairs wherever they appear in a log message,
 * while leaving ordinary business content (order numbers, account numbers,
 * plain words) untouched.
 */
class SensitiveDataMaskingRulesTest {

    @Test
    void masksAJwtWhereverItAppearsInAMessage() {
        String jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dGhpcyBpcyBhIGZha2Ugc2ln";
        String masked = SensitiveDataMaskingRules.mask("token received: " + jwt + " for request X");
        assertThat(masked).doesNotContain(jwt).contains("***MASKED***").contains("for request X");
    }

    @Test
    void masksABearerAuthorizationHeaderValue() {
        String masked = SensitiveDataMaskingRules.mask("Authorization: Bearer abc123.def456-ghi_789");
        assertThat(masked).doesNotContain("abc123.def456-ghi_789").contains("***MASKED***");
    }

    @Test
    void masksCookieAndSetCookieHeaderLinesButKeepsTheHeaderName() {
        String masked = SensitiveDataMaskingRules.mask("Cookie: JSESSIONID=ABCDEF123; XSRF-TOKEN=xyz");
        assertThat(masked).doesNotContain("ABCDEF123").doesNotContain("xyz").contains("Cookie").contains("***MASKED***");

        String setCookieMasked = SensitiveDataMaskingRules.mask("Set-Cookie: JSESSIONID=SECRET; Path=/; HttpOnly");
        assertThat(setCookieMasked).doesNotContain("SECRET").contains("Set-Cookie");
    }

    @Test
    void masksAnElevenDigitNationalIdButNotATenDigitBusinessNumber() {
        String masked = SensitiveDataMaskingRules.mask("nationalityId=12345678901 accepted");
        assertThat(masked).doesNotContain("12345678901").contains("***MASKED***");

        // KR-11/KR-12 account and order numbers are 10 digits — must NOT be masked.
        String businessNumber = SensitiveDataMaskingRules.mask("order 1261000010 confirmed for account 1261000036");
        assertThat(businessNumber).contains("1261000010").contains("1261000036").doesNotContain("***MASKED***");
    }

    @Test
    void masksCredentialShapedKeyValuePairsQuotedAndUnquoted() {
        String quoted = SensitiveDataMaskingRules.mask("payload: {\"password\":\"hunter2\",\"username\":\"ayilmaz\"}");
        assertThat(quoted).doesNotContain("hunter2").contains("***MASKED***").contains("ayilmaz");

        String unquoted = SensitiveDataMaskingRules.mask("client_secret=s3cr3t-value sent");
        assertThat(unquoted).doesNotContain("s3cr3t-value").contains("***MASKED***");
    }

    @Test
    void leavesOrdinaryBusinessContentUntouched() {
        String message = "Order 1261000010 created for customer 1001, status MIDLWARE";
        assertThat(SensitiveDataMaskingRules.mask(message)).isEqualTo(message);
    }

    @Test
    void toleratesNullAndEmptyInput() {
        assertThat(SensitiveDataMaskingRules.mask(null)).isNull();
        assertThat(SensitiveDataMaskingRules.mask("")).isEmpty();
    }
}
