package com.crm.account.messaging;

import com.crm.account.account.AccountContract;
import com.crm.account.account.dto.response.AccountResponse;
import com.crm.account.common.exception.BusinessException;
import com.crm.account.common.exception.MessageKeys;
import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.inbox.MessageHandler;
import com.crm.messaging.outbox.OutboxRecorder;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What account-service does with the three SALE saga commands (ADR-018 §5).
 *
 * <p><b>A plain class</b> — no annotation, no broker type, constructible and callable
 * from a JUnit test with nothing running (ADR-017 §4), which is what the architecture
 * guard test enforces.
 *
 * <p><b>It creates no new business logic.</b> The account check is the same read the
 * detail endpoint serves, the link is the same {@code addProductInvolvements} the
 * synchronous route calls, and the compensation is the one internal method ADR-018 §7
 * added. This class translates messages into those calls and their results back into
 * messages.
 *
 * <p>Business failures (4xx) are ANSWERS and are published as FAILED outcomes;
 * infrastructure failures (5xx, an unreachable catalog) are rethrown so the claim rolls
 * back and the transport redelivers. Telling the orchestrator "this sale failed" when the
 * truth is "I could not tell" would compensate a sale that was fine.
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
            case SaleCommandContracts.CHECK -> check(envelope);
            case SaleCommandContracts.LINK -> link(envelope);
            case SaleCommandContracts.COMPENSATE -> compensate(envelope);
            default -> log.error("Unroutable message type {} on a SALE command binding ({}); acknowledged "
                            + "without action — redelivery would repeat identically",
                    envelope.messageType(), envelope.messageId());
        }
    }

    private void check(EventEnvelope envelope) {
        SaleCommandContracts.CheckSaleAccount command =
                codec.decodePayload(envelope, SaleCommandContracts.CheckSaleAccount.class);
        try {
            AccountResponse account = executor.check(command.accountNumber());
            if (!AccountContract.STATUS_LABEL_ACTIVE.equals(account.accountStatus())) {
                // AC-SALE-02-01. A passive account stays READABLE (AC-ACCT-04-02) — which
                // is why this is a status check on a successful read rather than an
                // exception, and why the sale must ask again here even though Submit
                // already checked: the account can be passivated in between.
                reply(envelope, SaleReplyEventContracts.CHECKED_DESTINATION, SaleReplyEventContracts.CHECKED,
                        command.accountNumber(),
                        SaleReplyEventContracts.SaleStepOutcome.failed(command.orderNumber(), "account-passive",
                                MessageKeys.ACCT_NOT_ACTIVE));
                return;
            }
            reply(envelope, SaleReplyEventContracts.CHECKED_DESTINATION, SaleReplyEventContracts.CHECKED,
                    command.accountNumber(),
                    new SaleReplyEventContracts.SaleStepOutcome(command.orderNumber(),
                            SaleReplyEventContracts.Result.SUCCEEDED, null, null,
                            account.customerNumber(), null));
        } catch (BusinessException e) {
            rethrowIfInfrastructure(e);
            reply(envelope, SaleReplyEventContracts.CHECKED_DESTINATION, SaleReplyEventContracts.CHECKED,
                    command.accountNumber(),
                    SaleReplyEventContracts.SaleStepOutcome.failed(command.orderNumber(), "account-check",
                            e.getMessageKey()));
        }
    }

    private void link(EventEnvelope envelope) {
        SaleCommandContracts.LinkSaleProducts command =
                codec.decodePayload(envelope, SaleCommandContracts.LinkSaleProducts.class);
        try {
            // The saga id — the order number — is stored on every row this creates. It is
            // the ownership key the compensation below matches on, and the reason a saga
            // can never passivate an involvement it did not write (ADR-018 §7).
            executor.link(command.accountNumber(), command.productIds(), command.orderNumber());
            reply(envelope, SaleReplyEventContracts.LINKED_DESTINATION, SaleReplyEventContracts.LINKED,
                    command.accountNumber(),
                    new SaleReplyEventContracts.SaleStepOutcome(command.orderNumber(),
                            SaleReplyEventContracts.Result.SUCCEEDED, null, null, null, command.productIds()));
        } catch (BusinessException e) {
            rethrowIfInfrastructure(e);
            log.warn("Involvement write rejected for order {}: {}", command.orderNumber(), e.getMessageKey());
            reply(envelope, SaleReplyEventContracts.LINKED_DESTINATION, SaleReplyEventContracts.LINKED,
                    command.accountNumber(),
                    SaleReplyEventContracts.SaleStepOutcome.failed(command.orderNumber(), "involvement",
                            e.getMessageKey()));
        }
    }

    private void compensate(EventEnvelope envelope) {
        SaleCommandContracts.CompensateSaleInvolvements command =
                codec.decodePayload(envelope, SaleCommandContracts.CompensateSaleInvolvements.class);
        try {
            int passivated = executor.compensate(command.accountNumber(), command.orderNumber());
            log.info("Compensated {} involvement row(s) for order {}", passivated, command.orderNumber());
            reply(envelope, SaleReplyEventContracts.COMPENSATED_DESTINATION,
                    SaleReplyEventContracts.COMPENSATED, command.accountNumber(),
                    new SaleReplyEventContracts.SaleStepOutcome(command.orderNumber(),
                            SaleReplyEventContracts.Result.SUCCEEDED, null, null, null, List.of()));
        } catch (BusinessException e) {
            rethrowIfInfrastructure(e);
            // A failed compensation must never be quietly dropped: it puts the saga into
            // MANUAL_INTERVENTION, where it is a state, a gauge and a log line rather than
            // residue nobody counted.
            log.error("Involvement compensation failed for order {}: {}", command.orderNumber(),
                    e.getMessageKey());
            reply(envelope, SaleReplyEventContracts.COMPENSATED_DESTINATION,
                    SaleReplyEventContracts.COMPENSATED, command.accountNumber(),
                    SaleReplyEventContracts.SaleStepOutcome.failed(command.orderNumber(),
                            "involvement-compensation", e.getMessageKey()));
        }
    }

    /** A 5xx is not a business answer — it is this service saying it could not tell. */
    private static void rethrowIfInfrastructure(BusinessException e) {
        if (e.getStatus().is5xxServerError() || MessageKeys.SERVICE_UNAVAILABLE.equals(e.getMessageKey())) {
            throw e;
        }
    }

    private void reply(EventEnvelope cause, String destination, String messageType, String aggregateId,
                       SaleReplyEventContracts.SaleStepOutcome outcome) {
        // In the caller's (Inbox) transaction — OutboxRecorder is Propagation.MANDATORY,
        // so it cannot be anywhere else. causationId is the command that produced this
        // fact, which is what makes the sale one traceable chain under the sagaId.
        outboxRecorder.record(destination, messageType, SaleReplyEventContracts.AGGREGATE_TYPE,
                aggregateId, cause.sagaId(), cause.messageId(), outcome);
    }
}
