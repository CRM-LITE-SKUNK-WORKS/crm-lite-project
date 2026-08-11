package com.crm.account.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crm.account.account.dto.response.AccountResponse;
import com.crm.account.common.exception.BusinessException;
import com.crm.account.common.exception.MessageKeys;
import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.contract.MessageTypes;
import com.crm.messaging.outbox.OutboxRecorder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

/**
 * The SALE saga command handler, exercised with <b>no Spring context and no broker</b>
 * (ADR-017 §4) — so every outcome the saga depends on is reproducible in milliseconds,
 * including the two that a live stack cannot be asked to produce on demand: a passive
 * account discovered mid-sale, and an involvement compensation that itself fails.
 */
class SaleCommandHandlerTest {

    private static final String ORDER_NUMBER = "1261000027";
    private static final String ACCOUNT_NUMBER = "1261000010";

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
    @DisplayName("check: an Active account answers SUCCEEDED with the AUTHORITATIVE customer number")
    void activeAccountPassesTheCheck() {
        Mockito.when(executor.check(ACCOUNT_NUMBER)).thenReturn(account("Active"));

        handler.handle(command(MessageTypes.CHECK_SALE_ACCOUNT,
                new SaleCommandContracts.CheckSaleAccount(ORDER_NUMBER, ACCOUNT_NUMBER)));

        SaleReplyEventContracts.SaleStepOutcome outcome = capturedOutcome();
        assertThat(outcome.result()).isEqualTo(SaleReplyEventContracts.Result.SUCCEEDED);
        assertThat(outcome.customerNumber())
                .as("the sale learns who the customer is from the ACCOUNT, never from the browser "
                        + "— it is what the service-address ownership check depends on (ADR-015 §5.9)")
                .isEqualTo(1001L);
    }

    @Test
    @DisplayName("check: a Passive account is a FAILED outcome, not an exception — it stays readable")
    void passiveAccountFailsTheCheck() {
        Mockito.when(executor.check(ACCOUNT_NUMBER)).thenReturn(account("Passive"));

        handler.handle(command(MessageTypes.CHECK_SALE_ACCOUNT,
                new SaleCommandContracts.CheckSaleAccount(ORDER_NUMBER, ACCOUNT_NUMBER)));

        SaleReplyEventContracts.SaleStepOutcome outcome = capturedOutcome();
        assertThat(outcome.result()).isEqualTo(SaleReplyEventContracts.Result.FAILED);
        assertThat(outcome.failureMessageKey()).isEqualTo(MessageKeys.ACCT_NOT_ACTIVE);
        // AC-SALE-02-01. A passive account is READABLE (AC-ACCT-04-02), so the read
        // succeeds and the status is what rejects the sale — which is also why the saga
        // asks again here even though Submit already checked.
    }

    @Test
    @DisplayName("check: an unknown account relays MSG-ACCT-NOT-FOUND")
    void unknownAccountFailsTheCheck() {
        Mockito.when(executor.check(ACCOUNT_NUMBER)).thenThrow(new BusinessException(
                HttpStatus.NOT_FOUND, MessageKeys.ACCT_NOT_FOUND, "no such account"));

        handler.handle(command(MessageTypes.CHECK_SALE_ACCOUNT,
                new SaleCommandContracts.CheckSaleAccount(ORDER_NUMBER, ACCOUNT_NUMBER)));

        assertThat(capturedOutcome().failureMessageKey()).isEqualTo(MessageKeys.ACCT_NOT_FOUND);
    }

    @Test
    @DisplayName("check: an unreachable dependency is rethrown, never published as a sale failure")
    void infrastructureFailureIsRetried() {
        Mockito.when(executor.check(ACCOUNT_NUMBER)).thenThrow(new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE, MessageKeys.SERVICE_UNAVAILABLE, "catalog is down"));

        assertThatThrownBy(() -> handler.handle(command(MessageTypes.CHECK_SALE_ACCOUNT,
                new SaleCommandContracts.CheckSaleAccount(ORDER_NUMBER, ACCOUNT_NUMBER))))
                .isInstanceOf(BusinessException.class);

        Mockito.verifyNoInteractions(recorder);
    }

    @Test
    @DisplayName("link: stamps the saga's own id onto the rows, which is what makes compensation safe")
    void linkCarriesTheSaleOperationId() {
        handler.handle(command(MessageTypes.LINK_SALE_PRODUCTS,
                new SaleCommandContracts.LinkSaleProducts(ORDER_NUMBER, ACCOUNT_NUMBER, List.of(101L, 102L))));

        Mockito.verify(executor).link(ACCOUNT_NUMBER, List.of(101L, 102L), ORDER_NUMBER);
        SaleReplyEventContracts.SaleStepOutcome outcome = capturedOutcome();
        assertThat(outcome.result()).isEqualTo(SaleReplyEventContracts.Result.SUCCEEDED);
        assertThat(outcome.productIds()).containsExactly(101L, 102L);
    }

    @Test
    @DisplayName("link: a passivated account is published as FAILED so the products get compensated")
    void linkFailureIsPublished() {
        Mockito.doThrow(new BusinessException(HttpStatus.CONFLICT, MessageKeys.ACCT_NOT_ACTIVE,
                        "account is passive"))
                .when(executor).link(Mockito.anyString(), Mockito.anyList(), Mockito.anyString());

        handler.handle(command(MessageTypes.LINK_SALE_PRODUCTS,
                new SaleCommandContracts.LinkSaleProducts(ORDER_NUMBER, ACCOUNT_NUMBER, List.of(101L))));

        assertThat(capturedOutcome().result()).isEqualTo(SaleReplyEventContracts.Result.FAILED);
    }

    @Test
    @DisplayName("compensate: scoped to this saga, and removing zero rows is still a success")
    void compensateIsScopedAndIdempotent() {
        Mockito.when(executor.compensate(ACCOUNT_NUMBER, ORDER_NUMBER)).thenReturn(0);

        handler.handle(command(MessageTypes.COMPENSATE_SALE_INVOLVEMENTS,
                new SaleCommandContracts.CompensateSaleInvolvements(ORDER_NUMBER, ACCOUNT_NUMBER)));

        Mockito.verify(executor).compensate(ACCOUNT_NUMBER, ORDER_NUMBER);
        assertThat(capturedOutcome().result())
                .as("a repeated compensation finds nothing left to undo, which is a completed "
                        + "compensation and not an error")
                .isEqualTo(SaleReplyEventContracts.Result.SUCCEEDED);
    }

    @Test
    @DisplayName("compensate: a failure is published so the saga escalates instead of claiming an undo")
    void compensateFailureIsPublished() {
        Mockito.when(executor.compensate(Mockito.anyString(), Mockito.anyString()))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.ACCT_NOT_FOUND,
                        "account vanished"));

        handler.handle(command(MessageTypes.COMPENSATE_SALE_INVOLVEMENTS,
                new SaleCommandContracts.CompensateSaleInvolvements(ORDER_NUMBER, ACCOUNT_NUMBER)));

        assertThat(capturedOutcome().result()).isEqualTo(SaleReplyEventContracts.Result.FAILED);
    }

    // ------------------------------------------------------------------ helpers

    private static AccountResponse account(String status) {
        return new AccountResponse(ACCOUNT_NUMBER, 1001L, "Home", "224", "Billing Account", 5L, status);
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
