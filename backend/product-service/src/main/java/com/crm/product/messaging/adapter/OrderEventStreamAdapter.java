package com.crm.product.messaging.adapter;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.inbox.InboxDispatcher;
import com.crm.product.messaging.OrderSubmittedContract;
import com.crm.product.messaging.OrderSubmittedHandler;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * The Spring Cloud Stream functional binding for product-service (ADR-017 §4/§7).
 *
 * <p>This class is the entire consumer-side broker surface of the service. It is a
 * {@code Consumer<Message<byte[]>>} — the functional model, no {@code @KafkaListener}, no
 * {@code KStream}, no topology — and its whole body is one delegation. Everything that
 * decides anything lives below it in plain classes: {@link InboxDispatcher} for
 * deduplication and failure policy, {@link OrderSubmittedHandler} for the business
 * reaction.
 *
 * <p>Bytes in, nothing out. The payload type is {@code byte[]} rather than a POJO so that
 * no message converter registered for the HTTP API can reinterpret what arrived: the
 * envelope is decoded by the codec that produced it, and only by that codec.
 *
 * <p>Binding configuration — destination, group, partitioning, DLQ — is
 * {@code spring.cloud.stream.bindings.orderSubmitted-in-0.*} in configuration, not here.
 * A binder swap is then a configuration change with no Java change at all.
 *
 * <p>Off unless {@code crm.messaging.broker.enabled=true}. Before the SALE cutover it is
 * false everywhere, so no binding is created, the Kafka binder is never instantiated, and
 * the service starts with no broker present exactly as it does today.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "crm.messaging.broker", name = "enabled", havingValue = "true")
public class OrderEventStreamAdapter {

    /**
     * Binding name {@code orderSubmitted-in-0}. The function name is the binding prefix by
     * Spring Cloud Stream convention, which is why the bean name matters here and nowhere
     * else in the service.
     */
    @Bean
    public Consumer<Message<byte[]>> orderSubmitted(InboxDispatcher dispatcher, EnvelopeCodec codec) {
        OrderSubmittedHandler handler = new OrderSubmittedHandler(codec);
        return message -> dispatcher.dispatch(
                message.getPayload(),
                OrderSubmittedContract.SOURCE_DESTINATION,
                OrderSubmittedContract.CONSUMER_GROUP,
                handler);
    }
}
