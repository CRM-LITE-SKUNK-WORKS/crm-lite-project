package com.crm.observability.starter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.JsonGeneratorDelegate;
import java.io.IOException;

/**
 * Runs every STRING value written to the log JSON (the rendered message, MDC
 * values, structured arguments, exception messages — anything, regardless of
 * field name) through {@link SensitiveDataMaskingRules} before it reaches the
 * underlying generator. Delegating at the {@link JsonGenerator} level, rather
 * than masking only the final rendered string, is what catches a secret no
 * matter which field it ended up in.
 */
final class SensitiveDataMaskingJsonGenerator extends JsonGeneratorDelegate {

    SensitiveDataMaskingJsonGenerator(JsonGenerator delegate) {
        super(delegate, true);
    }

    @Override
    public void writeString(String text) throws IOException {
        super.writeString(SensitiveDataMaskingRules.mask(text));
    }

    @Override
    public void writeString(char[] text, int offset, int len) throws IOException {
        super.writeString(SensitiveDataMaskingRules.mask(new String(text, offset, len)));
    }

    @Override
    public void writeStringField(String fieldName, String value) throws IOException {
        super.writeStringField(fieldName, SensitiveDataMaskingRules.mask(value));
    }

    /**
     * MDC entries and other generic key/value providers in logstash-logback-encoder
     * write through {@code writeObjectField}, not {@code writeStringField} — and the
     * inherited {@link JsonGeneratorDelegate} implementation forwards {@code Object}
     * writes straight to the wrapped generator's own codec, bypassing this class's
     * {@code writeString} overrides entirely. Intercepting the String case here (and
     * in {@link #writeObject}) is what makes masking apply to MDC values, not just
     * the rendered message — proven by {@code LogstashJsonMaskingIntegrationTest}.
     */
    @Override
    public void writeObjectField(String fieldName, Object value) throws IOException {
        if (value instanceof String text) {
            writeFieldName(fieldName);
            writeString(text);
        } else {
            super.writeObjectField(fieldName, value);
        }
    }

    @Override
    public void writeObject(Object value) throws IOException {
        if (value instanceof String text) {
            writeString(text);
        } else {
            super.writeObject(value);
        }
    }
}
