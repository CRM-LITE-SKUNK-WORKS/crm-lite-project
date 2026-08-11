package com.crm.product.messaging;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.inbox.MessageHandler;
import com.crm.messaging.outbox.OutboxRecorder;
import com.crm.product.common.exception.BusinessException;
import com.crm.product.common.exception.MessageKeys;
import com.crm.product.product.dto.request.ProductCharacteristicValueRequest;
import com.crm.product.product.dto.request.ProductCompensationRequest;
import com.crm.product.product.dto.request.ProductCreateItemRequest;
import com.crm.product.product.dto.request.ProductCreateRequest;
import com.crm.product.product.dto.request.ProductLifecycleRequest;
import com.crm.product.product.dto.response.ProductCreateResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What product-service does with the three SALE saga commands (ADR-018 §5).
 *
 * <p><b>A plain class.</b> No annotation, no broker type, no Spring requirement:
 * {@code new SaleCommandHandler(codec, executor, recorder).handle(envelope)} is a valid
 * call from a JUnit test with nothing running, which is what ADR-017 §4 asks of business
 * code and what the architecture guard test enforces.
 *
 * <p><b>It creates no new business logic.</b> Every command routes into the SAME
 * {@code ProductService} method the synchronous HTTP endpoint calls — composition rules,
 * characteristic validation, the PNDG lifecycle and the sale-scoped compensation guard
 * are unchanged and unduplicated. This class translates a message into that call and the
 * result back into a message; nothing else.
 *
 * <p><b>The failure split is the important part.</b>
 * <ul>
 *   <li>A <b>business</b> failure (4xx {@code BusinessException}: an inactive offer, a
 *       basket that breaks composition, a characteristic that fails validation) is an
 *       ANSWER. It is published as a FAILED outcome carrying the analyst's own message
 *       key, and the message is acknowledged — redelivering it would produce the same
 *       rejection forever while the customer waited.</li>
 *   <li>An <b>infrastructure</b> failure (the catalog is unreachable, the database is
 *       down, anything 5xx) is NOT an answer. It is rethrown, the transaction rolls back
 *       with the Inbox claim, and the transport redelivers — because the next attempt may
 *       genuinely succeed and telling the orchestrator "this sale failed" would be a
 *       lie.</li>
 * </ul>
 */
public class SaleCommandHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(SaleCommandHandler.class);

    private final EnvelopeCodec codec;
    private final SaleCommandExecutor executor;
    private final OutboxRecorder outboxRecorder;

    public SaleCommandHandler(EnvelopeCodec codec, SaleCommandExecutor executor, OutboxRecorder outboxRecorder) {
        this.codec = codec;
        this.executor = executor;
        this.outboxRecorder = outboxRecorder;
    }

    @Override
    public void handle(EventEnvelope envelope) {
        switch (envelope.messageType()) {
            case SaleCommandContracts.PREPARE -> prepare(envelope);
            case SaleCommandContracts.ACTIVATE -> activate(envelope);
            case SaleCommandContracts.COMPENSATE -> compensate(envelope);
            default -> log.error("Unroutable message type {} on a SALE command binding ({}); acknowledged "
                            + "without action — redelivery would repeat identically",
                    envelope.messageType(), envelope.messageId());
        }
    }

    private void prepare(EventEnvelope envelope) {
        SaleCommandContracts.PrepareSaleProducts command =
                codec.decodePayload(envelope, SaleCommandContracts.PrepareSaleProducts.class);
        try {
            ProductCreateResponse response = executor.prepare(toCreateRequest(command));
            List<SaleReplyEventContracts.SaleStepOutcome.PreparedProduct> products = response.products().stream()
                    .map(product -> new SaleReplyEventContracts.SaleStepOutcome.PreparedProduct(
                            product.productId(), product.offerId(), product.amount(), product.main()))
                    .toList();
            reply(envelope, SaleReplyEventContracts.PREPARED_DESTINATION, SaleReplyEventContracts.PREPARED,
                    String.valueOf(response.mainProductId()),
                    new SaleReplyEventContracts.SaleStepOutcome(command.orderNumber(),
                            SaleReplyEventContracts.Result.SUCCEEDED, null, null,
                            response.totalAmount(), products,
                            products.stream().map(
                                    SaleReplyEventContracts.SaleStepOutcome.PreparedProduct::productId).toList()));
        } catch (BusinessException e) {
            rethrowIfInfrastructure(e);
            log.warn("Product preparation rejected for order {}: {}", command.orderNumber(), e.getMessageKey());
            reply(envelope, SaleReplyEventContracts.PREPARED_DESTINATION, SaleReplyEventContracts.PREPARED,
                    command.orderNumber(),
                    SaleReplyEventContracts.SaleStepOutcome.failed(command.orderNumber(), "preparation",
                            e.getMessageKey()));
        }
    }

    private void activate(EventEnvelope envelope) {
        SaleCommandContracts.ActivateSaleProducts command =
                codec.decodePayload(envelope, SaleCommandContracts.ActivateSaleProducts.class);
        ProductLifecycleRequest request = new ProductLifecycleRequest();
        request.setProductIds(command.productIds());
        try {
            executor.activate(request);
            reply(envelope, SaleReplyEventContracts.ACTIVATED_DESTINATION, SaleReplyEventContracts.ACTIVATED,
                    command.orderNumber(),
                    new SaleReplyEventContracts.SaleStepOutcome(command.orderNumber(),
                            SaleReplyEventContracts.Result.SUCCEEDED, null, null, null, null,
                            command.productIds()));
        } catch (BusinessException e) {
            rethrowIfInfrastructure(e);
            log.warn("Product activation rejected for order {}: {}", command.orderNumber(), e.getMessageKey());
            reply(envelope, SaleReplyEventContracts.ACTIVATED_DESTINATION, SaleReplyEventContracts.ACTIVATED,
                    command.orderNumber(),
                    SaleReplyEventContracts.SaleStepOutcome.failed(command.orderNumber(), "activation",
                            e.getMessageKey()));
        }
    }

    private void compensate(EventEnvelope envelope) {
        SaleCommandContracts.CompensateSaleProducts command =
                codec.decodePayload(envelope, SaleCommandContracts.CompensateSaleProducts.class);
        if (command.productIds() == null || command.productIds().isEmpty()) {
            // Nothing was ever created for this sale. Answering SUCCEEDED is correct and
            // necessary: the orchestrator is waiting for compensation to conclude, and
            // "there was nothing to undo" is a completed compensation.
            reply(envelope, SaleReplyEventContracts.COMPENSATED_DESTINATION, SaleReplyEventContracts.COMPENSATED,
                    command.orderNumber(),
                    new SaleReplyEventContracts.SaleStepOutcome(command.orderNumber(),
                            SaleReplyEventContracts.Result.SUCCEEDED, null, null, null, null, List.of()));
            return;
        }
        ProductCompensationRequest request = new ProductCompensationRequest();
        request.setSaleOperationId(command.orderNumber());
        request.setProductIds(command.productIds());
        try {
            executor.compensate(request);
            reply(envelope, SaleReplyEventContracts.COMPENSATED_DESTINATION, SaleReplyEventContracts.COMPENSATED,
                    command.orderNumber(),
                    new SaleReplyEventContracts.SaleStepOutcome(command.orderNumber(),
                            SaleReplyEventContracts.Result.SUCCEEDED, null, null, null, null,
                            command.productIds()));
        } catch (BusinessException e) {
            rethrowIfInfrastructure(e);
            // A FAILED compensation is the one outcome that must never be quietly
            // dropped: it puts the saga into MANUAL_INTERVENTION, where it is visible as
            // a state, a gauge and a log line rather than as residue nobody counted.
            log.error("Product compensation failed for order {}: {}", command.orderNumber(), e.getMessageKey());
            reply(envelope, SaleReplyEventContracts.COMPENSATED_DESTINATION, SaleReplyEventContracts.COMPENSATED,
                    command.orderNumber(),
                    SaleReplyEventContracts.SaleStepOutcome.failed(command.orderNumber(), "compensation",
                            e.getMessageKey()));
        }
    }

    /**
     * A 5xx {@code BusinessException} is not a business answer — it is this service
     * saying it could not tell. Rethrowing rolls back the claim so the transport
     * redelivers, which is the only correct response to "ask me again".
     */
    private static void rethrowIfInfrastructure(BusinessException e) {
        if (e.getStatus().is5xxServerError() || MessageKeys.SERVICE_UNAVAILABLE.equals(e.getMessageKey())) {
            throw e;
        }
    }

    private void reply(EventEnvelope cause, String destination, String messageType, String aggregateId,
                       SaleReplyEventContracts.SaleStepOutcome outcome) {
        // Recorded in the caller's (Inbox) transaction — OutboxRecorder is
        // Propagation.MANDATORY, so it cannot be anywhere else. The causationId is the
        // command that produced this fact, which is what makes the whole sale one
        // traceable chain under the sagaId.
        outboxRecorder.record(destination, messageType, SaleReplyEventContracts.AGGREGATE_TYPE,
                aggregateId, cause.sagaId(), cause.messageId(), outcome);
    }

    private static ProductCreateRequest toCreateRequest(SaleCommandContracts.PrepareSaleProducts command) {
        ProductCreateRequest request = new ProductCreateRequest();
        request.setCustomerNumber(command.customerNumber());
        request.setServiceAddressId(command.serviceAddressId());
        request.setCampaignId(command.campaignId());
        // The order number IS the sale operation id (ADR-018 §3): a reissued command
        // replays the first result instead of creating a second installation.
        request.setSaleOperationId(command.orderNumber());
        List<ProductCreateItemRequest> items = new ArrayList<>();
        for (SaleCommandContracts.PrepareSaleProducts.Item item : command.items()) {
            ProductCreateItemRequest created = new ProductCreateItemRequest();
            created.setOfferId(item.offerId());
            List<ProductCharacteristicValueRequest> characteristics = new ArrayList<>();
            if (item.characteristics() != null) {
                for (Map.Entry<Long, String> entry : item.characteristics().entrySet()) {
                    ProductCharacteristicValueRequest value = new ProductCharacteristicValueRequest();
                    value.setCharacteristicId(entry.getKey());
                    value.setValue(entry.getValue());
                    characteristics.add(value);
                }
            }
            created.setCharacteristics(characteristics);
            items.add(created);
        }
        request.setItems(items);
        return request;
    }
}
