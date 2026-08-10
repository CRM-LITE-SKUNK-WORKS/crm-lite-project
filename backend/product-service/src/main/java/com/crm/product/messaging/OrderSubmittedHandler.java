package com.crm.product.messaging;

import com.crm.messaging.contract.EnvelopeCodec;
import com.crm.messaging.contract.EventEnvelope;
import com.crm.messaging.inbox.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What product-service does with an {@code crm.order.order-submitted} event.
 *
 * <p><b>A plain class.</b> No annotation, no broker type, no Spring requirement:
 * {@code new OrderSubmittedHandler(codec).handle(envelope)} is a valid call from a JUnit
 * test with nothing running, which is the property ADR-017 §4 asks for and the
 * architecture guard test enforces.
 *
 * <p><b>What it does today, honestly stated:</b> it decodes the payload into this
 * service's own DTO and records that it observed the sale. It does NOT create products.
 * Creating them here would BE the asynchronous SALE cutover, which is explicitly not part
 * of this branch — {@code POST /api/products} remains the live route and ADR-016 §5's
 * synchronous orchestration is untouched. At cutover this method body is where the
 * existing {@code ProductService#create} call moves; everything around it — the inbox
 * claim, the transaction, the duplicate guard, the retry and dead-letter policy — is
 * already in place and already tested.
 *
 * <p>It runs inside {@code InboxGuard}'s transaction, alongside the duplicate claim.
 * Throwing rolls both back, so a failure leaves the message genuinely unprocessed rather
 * than half-applied.
 */
public class OrderSubmittedHandler implements MessageHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderSubmittedHandler.class);

    private final EnvelopeCodec codec;

    public OrderSubmittedHandler(EnvelopeCodec codec) {
        this.codec = codec;
    }

    @Override
    public void handle(EventEnvelope envelope) {
        // Decoding through the codec is what applies the schema-version check: an
        // unsupported version throws here rather than producing a half-populated DTO.
        OrderSubmittedContract.OrderSubmitted payload =
                codec.decodePayload(envelope, OrderSubmittedContract.OrderSubmitted.class);
        log.info("Observed order-submitted for order {} ({} item(s), saga {}) — no product is created "
                        + "from this path before the SALE cutover (ADR-017 §11)",
                payload.orderNumber(),
                payload.items() == null ? 0 : payload.items().size(),
                envelope.sagaId());
    }
}
