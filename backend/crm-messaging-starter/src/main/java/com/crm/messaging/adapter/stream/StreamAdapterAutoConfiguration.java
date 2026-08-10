package com.crm.messaging.adapter.stream;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.outbox.OutboxPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;

/**
 * Wiring for the broker adapter, kept in the adapter package.
 *
 * <p>It is separate from {@code CrmMessagingAutoConfiguration} for one reason: that class
 * would otherwise have to import {@link StreamBridge}, and "Spring Cloud Stream appears
 * only in adapter packages" (ADR-017 §4) should be literally true of the import graph, not
 * true-except-for-the-wiring. Configuration is code.
 *
 * <p>Ordered <b>before</b> the core auto-configuration so its {@code OutboxPublisher} bean
 * is registered first: the core class falls back to the fail-loud
 * {@code NoBrokerOutboxPublisher} via {@code @ConditionalOnMissingBean}, and a fallback
 * that is evaluated before the real thing exists always wins.
 *
 * <p>Both conditions must hold — the class on the classpath AND
 * {@code crm.messaging.broker.enabled=true}. Before the SALE cutover the property is
 * false everywhere, so no publisher is created here, no binder is instantiated, and the
 * service starts with no broker present.
 */
@AutoConfiguration(before = com.crm.messaging.CrmMessagingAutoConfiguration.class)
@ConditionalOnClass(StreamBridge.class)
@ConditionalOnProperty(prefix = "crm.messaging.broker", name = "enabled", havingValue = "true")
public class StreamAdapterAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OutboxPublisher.class)
    public OutboxPublisher streamBridgeOutboxPublisher(StreamBridge streamBridge, EnvelopeCodec codec) {
        return new StreamBridgeOutboxPublisher(streamBridge, codec);
    }
}
