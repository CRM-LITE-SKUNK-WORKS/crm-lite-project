package com.crm.product.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.contract.MessageTypes;
import com.crm.messaging.outbox.OutboxRecorder;
import com.crm.product.common.exception.BusinessException;
import com.crm.product.common.exception.MessageKeys;
import com.crm.product.product.dto.request.ProductCompensationRequest;
import com.crm.product.product.dto.request.ProductCreateRequest;
import com.crm.product.product.dto.response.CreatedProductResponse;
import com.crm.product.product.dto.response.ProductCreateResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

/**
 * The SALE saga command handler, exercised with <b>no Spring context and no broker</b> —
 * which is the property ADR-017 §4 asks of business handlers and the reason this suite
 * runs in milliseconds without Docker.
 *
 * <p>What it is really about is the split ADR-018 §8 draws: a <b>business</b> failure is an
 * ANSWER and must be published so the saga can compensate and the user can be told what is
 * wrong; an <b>infrastructure</b> failure is not an answer and must be rethrown so the
 * message is redelivered. Getting that backwards produces the two worst outcomes available
 * — a sale compensated because a database blipped, or a rejected basket redelivered
 * forever while the customer waits.
 */
class SaleCommandHandlerTest {

    private static final String ORDER_NUMBER = "1261000027";

    EnvelopeCodec codec;
    SaleCommandExecutor executor;
    OutboxRecorder recorder;
    SaleCommandHandler handler;

    @BeforeEach
    void setUp() {
        codec = new EnvelopeCodec();
        executor = Mockito.mock(SaleCommandExecutor.class);
        recorder = Mockito.mock(OutboxRecorder.class);
        handler = new SaleCommandHandler(codec, executor, recorder);
    }

    @Test
    @DisplayName("prepare: publishes the created products, their amounts, and the order number as the operation id")
    void prepareSucceeds() {
        Mockito.when(executor.prepare(Mockito.any())).thenReturn(new ProductCreateResponse(
                "CMP-ADSL-01", "ADSL Hosgeldin Kampanyasi", 101L, new BigDecimal("497.00"),
                List.of(new CreatedProductResponse(1L, "ADSL 8MB Offer", 101L, true, new BigDecimal("299.00")),
                        new CreatedProductResponse(2L, "Modem", 102L, false, new BigDecimal("198.00")))));

        handler.handle(prepareCommand());

        ArgumentCaptor<ProductCreateRequest> request = ArgumentCaptor.forClass(ProductCreateRequest.class);
        Mockito.verify(executor).prepare(request.capture());
        assertThat(request.getValue().getSaleOperationId())
                .as("orderNumber IS the saleOperationId (ADR-018 §3) — this is what makes a reissued "
                        + "command replay the first result instead of creating a second installation")
                .isEqualTo(ORDER_NUMBER);
        assertThat(request.getValue().getCustomerNumber()).isEqualTo(1001L);
        assertThat(request.getValue().getItems()).hasSize(1);

        SaleReplyEventContracts.SaleStepOutcome outcome = capturedOutcome();
        assertThat(outcome.result()).isEqualTo(SaleReplyEventContracts.Result.SUCCEEDED);
        assertThat(outcome.totalAmount()).isEqualByComparingTo("497.00");
        assertThat(outcome.products()).hasSize(2);
        assertThat(outcome.productIds()).containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("prepare: a rejected basket is published as a FAILED outcome with the analyst's own key")
    void prepareBusinessFailureIsAnAnswer() {
        Mockito.when(executor.prepare(Mockito.any())).thenThrow(new BusinessException(
                HttpStatus.BAD_REQUEST, MessageKeys.SALE_OFFER_INACTIVE, "offer 2 is passive"));

        handler.handle(prepareCommand());

        SaleReplyEventContracts.SaleStepOutcome outcome = capturedOutcome();
        assertThat(outcome.result()).isEqualTo(SaleReplyEventContracts.Result.FAILED);
        assertThat(outcome.failureMessageKey())
                .as("the user must be told what is actually wrong, not a generic failure")
                .isEqualTo(MessageKeys.SALE_OFFER_INACTIVE);
    }

    @Test
    @DisplayName("prepare: an infrastructure failure is rethrown and publishes nothing")
    void prepareInfrastructureFailureIsRetried() {
        Mockito.when(executor.prepare(Mockito.any())).thenThrow(new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE, MessageKeys.SERVICE_UNAVAILABLE, "catalog is down"));

        assertThatThrownBy(() -> handler.handle(prepareCommand()))
                .isInstanceOf(BusinessException.class);

        Mockito.verifyNoInteractions(recorder);
        // Rethrowing is the only way to tell the transport not to acknowledge. Publishing
        // FAILED here would compensate a sale that was never actually rejected.
    }

    @Test
    @DisplayName("compensate: nothing to undo is a SUCCESSFUL compensation, not a failure")
    void compensateWithNoProductsSucceeds() {
        handler.handle(command(MessageTypes.COMPENSATE_SALE_PRODUCTS,
                new SaleCommandContracts.CompensateSaleProducts(ORDER_NUMBER, List.of())));

        Mockito.verifyNoInteractions(executor);
        SaleReplyEventContracts.SaleStepOutcome outcome = capturedOutcome();
        assertThat(outcome.result())
                .as("the orchestrator is waiting for compensation to CONCLUDE; 'there was nothing "
                        + "to undo' concludes it")
                .isEqualTo(SaleReplyEventContracts.Result.SUCCEEDED);
    }

    @Test
    @DisplayName("compensate: passes this sale's own id, so it can never touch another sale's products")
    void compensateIsSaleScoped() {
        handler.handle(command(MessageTypes.COMPENSATE_SALE_PRODUCTS,
                new SaleCommandContracts.CompensateSaleProducts(ORDER_NUMBER, List.of(101L, 102L))));

        ArgumentCaptor<ProductCompensationRequest> request =
                ArgumentCaptor.forClass(ProductCompensationRequest.class);
        Mockito.verify(executor).compensate(request.capture());
        assertThat(request.getValue().getSaleOperationId()).isEqualTo(ORDER_NUMBER);
        assertThat(request.getValue().getProductIds()).containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("compensate: a refused compensation is published as FAILED so the saga can escalate")
    void compensateFailureIsPublished() {
        Mockito.doThrow(new BusinessException(HttpStatus.CONFLICT, MessageKeys.SALE_OPERATION_MISMATCH,
                        "those products belong to another sale"))
                .when(executor).compensate(Mockito.any());

        handler.handle(command(MessageTypes.COMPENSATE_SALE_PRODUCTS,
                new SaleCommandContracts.CompensateSaleProducts(ORDER_NUMBER, List.of(101L))));

        SaleReplyEventContracts.SaleStepOutcome outcome = capturedOutcome();
        assertThat(outcome.result()).isEqualTo(SaleReplyEventContracts.Result.FAILED);
        assertThat(outcome.failureMessageKey()).isEqualTo(MessageKeys.SALE_OPERATION_MISMATCH);
        // A swallowed compensation failure is residue nobody counted; published, it becomes
        // MANUAL_INTERVENTION — a state, a gauge and an alert (ADR-018 §7.2).
    }

    @Test
    @DisplayName("activate: publishes the ids it acted on")
    void activateSucceeds() {
        handler.handle(command(MessageTypes.ACTIVATE_SALE_PRODUCTS,
                new SaleCommandContracts.ActivateSaleProducts(ORDER_NUMBER, List.of(101L, 102L))));

        Mockito.verify(executor).activate(Mockito.any());
        SaleReplyEventContracts.SaleStepOutcome outcome = capturedOutcome();
        assertThat(outcome.result()).isEqualTo(SaleReplyEventContracts.Result.SUCCEEDED);
        assertThat(outcome.productIds()).containsExactly(101L, 102L);
    }

    // ------------------------------------------------------------------ helpers

    private EventEnvelope prepareCommand() {
        return command(MessageTypes.PREPARE_SALE_PRODUCTS, new SaleCommandContracts.PrepareSaleProducts(
                ORDER_NUMBER, 1001L, 1L, "CMP-ADSL-01",
                List.of(new SaleCommandContracts.PrepareSaleProducts.Item(1L, Map.of(1L, "16")))));
    }

    private EventEnvelope command(String messageType, Object payload) {
        return new EventEnvelope(UUID.randomUUID().toString(), messageType,
                MessageTypes.currentVersion(messageType), MessageTypes.AGGREGATE_ORDER, ORDER_NUMBER,
                ORDER_NUMBER, "corr-test", null, Instant.parse("2026-08-10T09:00:00Z"),
                codec.encodePayload(payload));
    }

    private SaleReplyEventContracts.SaleStepOutcome capturedOutcome() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(recorder).record(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.any(), Mockito.any(), payload.capture());
        return (SaleReplyEventContracts.SaleStepOutcome) payload.getValue();
    }
}
