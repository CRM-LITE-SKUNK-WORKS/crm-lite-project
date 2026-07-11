package com.crm.customer.common.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * VR-NAME requires leading/trailing whitespace to be trimmed before validation
 * runs, not rejected by it. Bean Validation has no built-in "trim first" step,
 * so this is done once here, at JSON deserialization time, for every incoming
 * String field. A blank result after trimming is treated as absent (null) so
 * optional fields keep working with {@code @Pattern}/{@code @NotBlank}.
 *
 * Spring Boot 4 defaults to Jackson 3 (spring-boot-starter-web pulls in
 * spring-boot-starter-jackson, not the legacy spring-boot-jackson2 module), so
 * this uses the {@code tools.jackson.*} API and {@code JsonMapperBuilderCustomizer}
 * rather than the Jackson 2 {@code Jackson2ObjectMapperBuilderCustomizer}.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer trimmingStringsCustomizer() {
        return builder -> builder.addModule(new SimpleModule().addDeserializer(String.class, new StringDeserializer() {
            @Override
            public String deserialize(JsonParser p, DeserializationContext ctxt) {
                String value = super.deserialize(p, ctxt);
                if (value == null) {
                    return null;
                }
                String trimmed = value.trim();
                return trimmed.isEmpty() ? null : trimmed;
            }
        }));
    }
}
