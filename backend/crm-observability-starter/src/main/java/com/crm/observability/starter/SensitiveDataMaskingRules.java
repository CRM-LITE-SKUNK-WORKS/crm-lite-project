package com.crm.observability.starter;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Regex-based redaction applied to EVERY string value a log event writes to JSON
 * ({@link SensitiveDataMaskingJsonGenerator}) — a defence-in-depth backstop, not a
 * substitute for not logging secrets in the first place. Deliberately pure and
 * dependency-free so it can be unit-tested without standing up Logback.
 *
 * <p>Never logged (requirement list this exists for): JWTs, passwords, cookies,
 * full National IDs, credentials, personal-data request bodies. The last one is
 * a code-review discipline (no application code in this project logs a raw
 * request body) that this class cannot enforce by regex; the other four are.
 */
final class SensitiveDataMaskingRules {

    private SensitiveDataMaskingRules() {
    }

    private static final String MASK = "***MASKED***";

    // Three base64url segments separated by dots, starting with the base64 of
    // '{"' (every JWT header begins with `{"alg"` or `{"typ"`) — matches an
    // Authorization: Bearer value, a raw token pasted into a message, or one
    // embedded anywhere else in a formatted string.
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}");

    private static final Pattern BEARER_PREFIX = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._-]+");

    // Cookie header values and Set-Cookie lines. Matches "Cookie: a=b; c=d" and
    // "Set-Cookie: JSESSIONID=...; ...", keeping the header name visible so the
    // log line still says WHAT was redacted.
    private static final Pattern COOKIE_HEADER = Pattern.compile(
            "(?i)\\b(Cookie|Set-Cookie)\\s*:\\s*[^\\r\\n]+");

    // KR-10 Turkish National ID: exactly 11 digits, word-bounded so a 10-digit
    // KR-11/KR-12 business number (account/order) is never touched.
    private static final Pattern NATIONAL_ID = Pattern.compile("\\b\\d{11}\\b");

    // key: value / key=value / "key":"value" pairs whose key name signals a secret.
    private static final Pattern CREDENTIAL_KV = Pattern.compile(
            "(?i)(\"?(?:password|passwd|secret|credential|clientSecret|client_secret|apiKey|api_key)\"?\\s*[:=]\\s*\")"
                    + "[^\"]*(\")");
    private static final Pattern CREDENTIAL_KV_UNQUOTED = Pattern.compile(
            "(?i)(\\b(?:password|passwd|secret|credential|clientSecret|client_secret|apiKey|api_key)\\s*[:=]\\s*)"
                    + "\\S+");

    private static final List<Pattern> WHOLE_VALUE_REPLACERS = List.of(JWT, BEARER_PREFIX, NATIONAL_ID);

    static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String result = value;
        for (Pattern pattern : WHOLE_VALUE_REPLACERS) {
            result = pattern.matcher(result).replaceAll(MASK);
        }
        // Keeps the header name ($1) so the log line still says WHAT was redacted.
        result = COOKIE_HEADER.matcher(result).replaceAll("$1: " + MASK);
        result = CREDENTIAL_KV.matcher(result).replaceAll("$1" + MASK + "$2");
        result = CREDENTIAL_KV_UNQUOTED.matcher(result).replaceAll("$1" + MASK);
        return result;
    }
}
