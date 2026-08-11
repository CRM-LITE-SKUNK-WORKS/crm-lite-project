package com.crm.order.messaging.adapter;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.inbox.InboxDispatcher;
import com.crm.order.messaging.SaleReplyContracts;
import com.crm.order.messaging.SaleReplyHandler;
import com.crm.order.saga.SaleSagaOrchestrator;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * The Spring Cloud Stream functional bindings for order-service (ADR-017 §4/§7,
 * ADR-018 §5) — the entire consumer-side broker surface of this service.
 *
 * <p>Every bean is a {@code Consumer<Message<byte[]>>} whose whole body is one
 * delegation: the functional model, no {@code @KafkaListener}, no {@code KStream}, no
 * topology. Everything that decides anything lives below it in plain classes —
 * {@link InboxDispatcher} for deduplication and failure policy,
 * {@link SaleReplyHandler} and {@link SaleSagaOrchestrator} for the state machine.
 *
 * <p><b>Six bindings and one handler.</b> The handler is shared because the decision the
 * six replies drive is one state machine, not six; the bindings are separate because a
 * destination is a contract (ADR-017 §7.8) and merging them into one reply channel would
 * mean an incompatible change to any one reply forced a new destination version on all
 * six. The source destination is passed per binding so a dead-lettered message lands next
 * to the destination it came from.
 *
 * <p>Payload type {@code byte[]}, not a POJO, so that no message converter registered for
 * the HTTP API can reinterpret what arrived: the envelope is decoded by the codec that
 * produced it and only by that codec.
 *
 * <p>Off unless {@code crm.messaging.broker.enabled=true}. With it false no binding is
 * created, the Kafka binder is never instantiated, and the service starts with no broker
 * present — which is exactly how the default (non-{@code async-sale}) profile still runs.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "crm.messaging.broker", name = "enabled", havingValue = "true")
public class SaleSagaStreamAdapter {

    /** Binding {@code saleAccountChecked-in-0}. */
    @Bean
    public Consumer<Message<byte[]>> saleAccountChecked(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                        SaleSagaOrchestrator orchestrator) {
        return binding(dispatcher, codec, orchestrator, SaleReplyContracts.ACCOUNT_CHECKED_DESTINATION);
    }

    /** Binding {@code saleProductsPrepared-in-0}. */
    @Bean
    public Consumer<Message<byte[]>> saleProductsPrepared(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                          SaleSagaOrchestrator orchestrator) {
        return binding(dispatcher, codec, orchestrator, SaleReplyContracts.PRODUCTS_PREPARED_DESTINATION);
    }

    /** Binding {@code saleProductsLinked-in-0}. */
    @Bean
    public Consumer<Message<byte[]>> saleProductsLinked(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                        SaleSagaOrchestrator orchestrator) {
        return binding(dispatcher, codec, orchestrator, SaleReplyContracts.PRODUCTS_LINKED_DESTINATION);
    }

    /** Binding {@code saleProductsActivated-in-0}. */
    @Bean
    public Consumer<Message<byte[]>> saleProductsActivated(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                           SaleSagaOrchestrator orchestrator) {
        return binding(dispatcher, codec, orchestrator, SaleReplyContracts.PRODUCTS_ACTIVATED_DESTINATION);
    }

    /** Binding {@code saleProductsCompensated-in-0}. */
    @Bean
    public Consumer<Message<byte[]>> saleProductsCompensated(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                             SaleSagaOrchestrator orchestrator) {
        return binding(dispatcher, codec, orchestrator, SaleReplyContracts.PRODUCTS_COMPENSATED_DESTINATION);
    }

    /** Binding {@code saleInvolvementsCompensated-in-0}. */
    @Bean
    public Consumer<Message<byte[]>> saleInvolvementsCompensated(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                                 SaleSagaOrchestrator orchestrator) {
        return binding(dispatcher, codec, orchestrator, SaleReplyContracts.INVOLVEMENTS_COMPENSATED_DESTINATION);
    }

    private static Consumer<Message<byte[]>> binding(InboxDispatcher dispatcher, EnvelopeCodec codec,
                                                     SaleSagaOrchestrator orchestrator, String destination) {
        SaleReplyHandler handler = new SaleReplyHandler(codec, orchestrator);
        String consumerGroup = SaleReplyContracts.consumerGroup(destination);
        return message -> dispatcher.dispatch(message.getPayload(), destination, consumerGroup, handler);
    }
}
