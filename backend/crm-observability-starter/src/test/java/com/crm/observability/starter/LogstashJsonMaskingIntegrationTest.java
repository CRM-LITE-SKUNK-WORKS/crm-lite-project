package com.crm.observability.starter;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.nio.charset.StandardCharsets;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * End-to-end proof that {@link SensitiveDataMaskingDecorator} actually wires into
 * a real {@link LogstashEncoder} the way {@code logback-json-base.xml} configures
 * it in production — the unit tests on {@link SensitiveDataMaskingRules} alone
 * would not catch a mismatch against logstash-logback-encoder's actual
 * {@code JsonGeneratorDecorator} SPI.
 */
class LogstashJsonMaskingIntegrationTest {

    private LogstashEncoder encoder;

    @BeforeEach
    void setUp() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        encoder = new LogstashEncoder();
        encoder.setContext(context);
        encoder.setJsonGeneratorDecorator(new SensitiveDataMaskingDecorator());
        encoder.start();
    }

    @AfterEach
    void tearDown() {
        encoder.stop();
        MDC.clear();
    }

    @Test
    void masksASecretInTheRenderedMessageAndInAnMdcValue() {
        MDC.put("someHeader", "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln");
        try {
            String json = encode("customer lookup failed for token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc123xyz");
            assertThat(json).doesNotContain("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc123xyz");
            assertThat(json).doesNotContain("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln");
            assertThat(json).contains("***MASKED***");
            assertThat(json).contains("customer lookup failed for token");
        } finally {
            MDC.remove("someHeader");
        }
    }

    @Test
    void leavesOrdinaryLogContentIntact() {
        String json = encode("Order 1261000010 created for customer 1001");
        assertThat(json).contains("1261000010").contains("1001").doesNotContain("***MASKED***");
    }

    private String encode(String message) {
        Logger logger = (Logger) LoggerFactory.getLogger("test-logger");
        ILoggingEvent event = new LoggingEvent(Logger.class.getName(), logger, Level.INFO, message, null, null);
        byte[] bytes = encoder.encode(event);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
