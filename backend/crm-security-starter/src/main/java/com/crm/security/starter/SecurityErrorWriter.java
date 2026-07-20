package com.crm.security.starter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Writes the project's established error-body shape
 * ({@code timestamp, status, error, messageKey, message, path}) without pulling a
 * JSON library into the starter. Values are JSON-escaped manually; the shape mirrors
 * customer-service's ErrorResponse / the gateway's GatewayErrorResponse.
 */
public final class SecurityErrorWriter {

    private SecurityErrorWriter() {
    }

    public static void write(HttpServletResponse response, int status, String error,
                             String messageKey, String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{"
                + "\"timestamp\":\"" + Instant.now() + "\","
                + "\"status\":" + status + ","
                + "\"error\":\"" + escape(error) + "\","
                + "\"messageKey\":\"" + escape(messageKey) + "\","
                + "\"message\":\"" + escape(message) + "\","
                + "\"path\":\"" + escape(path) + "\""
                + "}";
        response.getWriter().write(body);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
