package com.crm.observability.starter;

import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.decorate.JsonGeneratorDecorator;

/**
 * Wired into every service's JSON encoder via {@code logback-json-base.xml}
 * (never logback-spring.xml overriding it away): {@code
 * <jsonGeneratorDecorator class="com.crm.observability.starter.SensitiveDataMaskingDecorator"/>}.
 */
public class SensitiveDataMaskingDecorator implements JsonGeneratorDecorator {

    @Override
    public JsonGenerator decorate(JsonGenerator generator) {
        return new SensitiveDataMaskingJsonGenerator(generator);
    }
}
