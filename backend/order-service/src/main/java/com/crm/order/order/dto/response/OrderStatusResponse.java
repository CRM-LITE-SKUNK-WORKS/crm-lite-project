package com.crm.order.order.dto.response;

import com.crm.order.order.ProcessingStatus;
import java.time.Instant;

/**
 * The asynchronous SALE status resource (ADR-018 §6) — and the body of all three
 * asynchronous responses: the 201 that creates a draft, the 202 that accepts a submit,
 * and the 200 the client polls.
 *
 * <p>One shape for all three on purpose. A client that has just submitted and a client
 * that is polling are asking the same question — where is this sale? — so giving them
 * two shapes would mean the poll loop has to parse the accept response differently from
 * every response after it.
 *
 * <p><b>The two status fields are not redundant.</b> {@code orderStatus} is the
 * analyst-owned business status from GNL_ST (WAIT / MIDLWARE / CANCELLED);
 * {@code processingStatus} is this service's technical answer about the asynchronous
 * work. A completed sale is {@code MIDLWARE} + {@code COMPLETED}, because MIDLWARE is
 * what the analyst defined at approval and no accepted requirement defines another
 * terminal ORDER status — collapsing the two fields into one would force this project to
 * invent that missing status.
 *
 * @param failureMessageKey a message key from the catalog, present only on a terminal
 *                          failure. Never an exception message, a stack trace or any
 *                          other infrastructure detail (ADR-018 §8)
 * @param updatedAt         when the state last changed — what makes a poll loop able to
 *                          tell "still working" from "stuck"
 */
public record OrderStatusResponse(
        String orderNumber,
        String orderStatus,
        ProcessingStatus processingStatus,
        String failureMessageKey,
        Instant updatedAt) {
}
