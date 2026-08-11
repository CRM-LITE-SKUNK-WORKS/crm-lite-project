package com.crm.account.messaging.adapter;

import com.crm.account.messaging.SaleCommandContracts;
import com.crm.account.messaging.SaleCommandExecutor;
import com.crm.account.messaging.SaleCommandHandler;
import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.inbox.InboxDispatcher;
import com.crm.messaging.outbox.OutboxRecorder;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * The Spring Cloud Stream functional bindings for the SALE saga commands account-service
 * handles (ADR-017 §4/§7, ADR-018 §5) — the entire broker surface of this service.
 *
 * <p>Functional {@code Consumer<Message<byte[]>>} beans whose whole body is one
 * delegation: no {@code @KafkaListener}, no {@code KStream}, no topology, and no binder
 * vocabulary in this file. Destination, group, partitioning and DLQ are configuration, so
 * a binder swap needs no Java change.
 *
 * <p>Off unless {@code crm.messaging.broker.enabled=true}: with it false no binding is
 * created, the Kafka binder is never instantiated, and the service starts with no broker
 * present.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "crm.messaging.broker", name = "enabled", havingValue = "true")
public class SaleCommandStreamAdapter {

    /** Binding {@code checkSaleAccount-in-0}. */
    @Bean
    public Consumer<Message<byte[]>> checkSaleAccount(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                      SaleCommandExecutor executor, OutboxRecorder recorder) {
        return binding(dispatcher, codec, executor, recorder, SaleCommandContracts.CHECK_DESTINATION);
    }

    /** Binding {@code linkSaleProducts-in-0}. */
    @Bean
    public Consumer<Message<byte[]>> linkSaleProducts(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                      SaleCommandExecutor executor, OutboxRecorder recorder) {
        return binding(dispatcher, codec, executor, recorder, SaleCommandContracts.LINK_DESTINATION);
    }

    /** Binding {@code compensateSaleInvolvements-in-0}. */
    @Bean
    public Consumer<Message<byte[]>> compensateSaleInvolvements(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                                SaleCommandExecutor executor,
                                                                OutboxRecorder recorder) {
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
