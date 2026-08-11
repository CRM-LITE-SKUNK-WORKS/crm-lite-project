package com.crm.order.messaging;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.inbox.MessageHandler;
import com.crm.order.saga.SaleSagaOrchestrator;

/**
 * What order-service does with a saga reply: decode it into this service's own DTO and
 * hand it to the process manager (ADR-018 §5).
 *
 * <p><b>A plain class.</b> No annotation, no broker type, no Spring requirement:
 * {@code new SaleReplyHandler(codec, orchestrator).handle(envelope)} is a valid call from
 * a JUnit test with nothing running — which is what makes every saga behaviour worth
 * testing (duplicate, out-of-order, failure, restart) reachable without a broker, and
 * what the architecture guard test enforces.
 *
 * <p>It runs inside {@code InboxGuard}'s transaction, alongside the duplicate claim.
 * Throwing rolls both back, so a failure leaves the message genuinely unprocessed rather
 * than half-applied — and decoding is deliberately inside that transaction too, because
 * an unsupported schema version must be a terminal, dead-lettered outcome rather than a
 * half-populated DTO the state machine acts on.
 */
public class SaleReplyHandler implements MessageHandler {

    private final EnvelopeCodec codec;
    private final SaleSagaOrchestrator orchestrator;

    public SaleReplyHandler(EnvelopeCodec codec, SaleSagaOrchestrator orchestrator) {
        this.codec = codec;
        this.orchestrator = orchestrator;
    }

    @Override
    public void handle(EventEnvelope envelope) {
        SaleReplyContracts.SaleStepOutcome outcome =
                codec.decodePayload(envelope, SaleReplyContracts.SaleStepOutcome.class);
        orchestrator.applyReply(envelope, outcome);
    }
}
