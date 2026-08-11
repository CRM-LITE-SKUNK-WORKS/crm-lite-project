package com.crm.product.messaging.adapter;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.inbox.InboxDispatcher;
import com.crm.messaging.outbox.OutboxRecorder;
import com.crm.product.messaging.SaleCommandContracts;
import com.crm.product.messaging.SaleCommandExecutor;
import com.crm.product.messaging.SaleCommandHandler;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * The Spring Cloud Stream functional bindings for the SALE saga commands product-service
 * handles (ADR-017 §4/§7, ADR-018 §5).
 *
 * <p>Same shape as {@link OrderEventStreamAdapter} and for the same reasons: functional
 * {@code Consumer<Message<byte[]>>} beans whose whole body is one delegation, no
 * {@code @KafkaListener}, no topology, and no binder vocabulary anywhere in this file.
 * Destination, group, partitioning and DLQ are configuration
 * ({@code spring.cloud.stream.bindings.*}), so a binder swap needs no Java change.
 *
 * <p>Off unless {@code crm.messaging.broker.enabled=true}: with it false no binding is
 * created, the Kafka binder is never instantiated, and the service starts with no broker
 * present — which is how the default profile still runs.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "crm.messaging.broker", name = "enabled", havingValue = "true")
public class SaleCommandStreamAdapter {

    /** Binding {@code prepareSaleProducts-in-0}. */
    @Bean
    public Consumer<Message<byte[]>> prepareSaleProducts(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                         SaleCommandExecutor executor, OutboxRecorder recorder) {
        return binding(dispatcher, codec, executor, recorder, SaleCommandContracts.PREPARE_DESTINATION);
    }

    /** Binding {@code activateSaleProducts-in-0}. */
    @Bean
    public Consumer<Message<byte[]>> activateSaleProducts(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                          SaleCommandExecutor executor, OutboxRecorder recorder) {
        return binding(dispatcher, codec, executor, recorder, SaleCommandContracts.ACTIVATE_DESTINATION);
    }

    /** Binding {@code compensateSaleProducts-in-0}. */
    @Bean
    public Consumer<Message<byte[]>> compensateSaleProducts(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                            SaleCommandExecutor executor, OutboxRecorder recorder) {
        return binding(dispatcher, codec, executor, recorder, SaleCommandContracts.COMPENSATE_DESTINATION);
    }

    private static Consumer<Message<byte[]>> binding(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                     SaleCommandExecutor executor, OutboxRecorder recorder,
                                                     String destination) {
        SaleCommandHandler handler = new SaleCommandHandler(codec, executor, recorder);
        String consumerGroup = SaleCommandContracts.consumerGroup(destination);
        return message -> dispatcher.dispatch(message.getPayload(), destination, consumerGroup, handler);
    }
}
