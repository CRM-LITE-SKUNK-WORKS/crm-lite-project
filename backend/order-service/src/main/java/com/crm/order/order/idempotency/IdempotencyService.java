package com.crm.order.order.idempotency;

import com.crm.order.common.exception.MessageKeys;
import com.crm.order.order.dto.request.OrderCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The {@code POST /api/orders} idempotency contract (ADR-016 idempotency addendum,
 * 2026-08-06). Deliberately NOT {@code @Transactional} itself — like
 * {@link com.crm.order.order.service.impl.OrderServiceImpl}, it orchestrates two
 * independent short transactions on {@link IdempotencyPersistence} rather than
 * holding one open across the reserve-then-maybe-requery sequence.
 *
 * <p>{@link #reserveOrReplay} is the ONLY place that decides what a repeated key
 * means; {@link IdempotencyKeyFilter} only asks the question and renders the answer.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyPersistence persistence;
    private final ObjectMapper objectMapper;

    /**
     * Normalizes the request by round-tripping it through the actual wire DTO: parsing
     * whatever bytes the client sent into {@link OrderCreateRequest} and re-serializing
     * it strips incidental differences (whitespace, key order) that Jackson does not
     * treat as meaningful, so two byte-for-byte-different-but-equivalent request bodies
     * hash identically. If the body does not even parse, the raw bytes are hashed
     * instead — normalization is moot for a request that is about to fail body
     * validation anyway (the usual 400 `MSG-VALIDATION-ERROR` path still runs).
     */
    public String normalizedRequestHash(byte[] rawBody) {
        byte[] toHash;
        try {
            OrderCreateRequest parsed = objectMapper.readValue(rawBody, OrderCreateRequest.class);
            toHash = objectMapper.writeValueAsBytes(parsed);
        } catch (Exception e) {
            toHash = rawBody;
        }
        return sha256Hex(toHash);
    }

    /**
     * The asynchronous endpoints' hash (ADR-018 §3): the request PATH plus the raw body.
     *
     * <p>Two differences from {@link #normalizedRequestHash} and both are deliberate.
     * The path is included because one {@code Idempotency-Key} identifies one HTTP
     * command, and draft creation and submission are two — without the path, a client
     * that reused a key would be served the wrong command's recorded response instead of
     * the 409 that tells it about the mistake. The body is not round-tripped through a
     * DTO because there are two different DTOs here; the normalization that buys was only
     * ever worth it for a single, known wire shape, and a client that sends
     * byte-identical retries (which is what a retry is) needs none of it.
     */
    public String scopedRequestHash(String requestPath, byte[] rawBody) {
        byte[] path = (requestPath + "\n").getBytes(CHARSET);
        byte[] combined = new byte[path.length + rawBody.length];
        System.arraycopy(path, 0, combined, 0, path.length);
        System.arraycopy(rawBody, 0, combined, path.length, rawBody.length);
        return sha256Hex(combined);
    }

    /**
     * The concurrency guard is {@link IdempotencyPersistence#reserve}'s UNIQUE-constraint
     * INSERT, not this method's control flow: two threads racing here both attempt the
     * insert, and the database — not a prior SELECT — decides which one gets
     * {@link IdempotencyDecision.Proceed}.
     */
    public IdempotencyDecision reserveOrReplay(String idempotencyKey, String requestHash) {
        try {
            long reservationId = persistence.reserve(idempotencyKey, requestHash);
            return new IdempotencyDecision.Proceed(reservationId);
        } catch (DataIntegrityViolationException raceLost) {
            return persistence.findByKey(idempotencyKey)
                    .map(existing -> resolve(existing, requestHash))
                    .orElseThrow(() -> raceLost);
        }
    }

    public void complete(long reservationId, int responseStatus, String responseBody, String orderNumber) {
        persistence.complete(reservationId, responseStatus, responseBody, orderNumber);
    }

    private IdempotencyDecision resolve(IdempotencyKeyRecord existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            return new IdempotencyDecision.Conflict(MessageKeys.IDEMPOTENCY_KEY_CONFLICT,
                    "Idempotency-Key " + existing.getIdempotencyKey()
                            + " was already used with a different request body");
        }
        if (IdempotencyStatus.IN_PROGRESS.equals(existing.getStatus())) {
            // A genuinely concurrent request for the same key is still running. Bounded,
            // logged residue if the owning request never reaches complete() (crash) —
            // the same class of trade-off ADR-015 §8.4 accepts for a stuck PNDG row,
            // rather than a speculative reclaim-after-timeout policy no requirement asks
            // for (IdempotencyPersistence's javadoc records the reasoning).
            return new IdempotencyDecision.Conflict(MessageKeys.IDEMPOTENCY_KEY_IN_PROGRESS,
                    "A request for Idempotency-Key " + existing.getIdempotencyKey() + " is already being processed");
        }
        return new IdempotencyDecision.Replay(existing.getResponseStatus(), existing.getResponseBody());
    }

    private String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandatory JDK algorithm (JLS platform guarantee) — this
            // branch cannot execute on any conforming JVM.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    // Kept package-visible purely so IdempotencyKeyFilter can hash without duplicating
    // the charset constant.
    static final java.nio.charset.Charset CHARSET = StandardCharsets.UTF_8;
}
