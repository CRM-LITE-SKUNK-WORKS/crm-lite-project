package com.crm.messaging.inbox;

import com.crm.messaging.contract.EventEnvelope;

/**
 * What a service actually does with a message (ADR-017 §4).
 *
 * <p>A plain functional interface over a plain envelope. An implementation is an ordinary
 * Java class with no annotation, no broker type in its signature and no container
 * requirement — {@code new SomeHandler(repo).handle(envelope)} in a unit test is the
 * intended usage, and the architecture guard test asserts that nothing in these packages
 * can even see {@code org.apache.kafka}.
 *
 * <p>Implementations must be safe to call inside the caller's transaction: {@link InboxGuard}
 * invokes {@code handle} with the duplicate claim already inserted and the transaction
 * still open, so throwing rolls back the claim along with the business change and the
 * message is retried.
 */
@FunctionalInterface
public interface MessageHandler {

    void handle(EventEnvelope envelope);
}
