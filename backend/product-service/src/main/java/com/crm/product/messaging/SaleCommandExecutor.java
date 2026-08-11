package com.crm.product.messaging;

import com.crm.product.product.dto.request.ProductCompensationRequest;
import com.crm.product.product.dto.request.ProductCreateRequest;
import com.crm.product.product.dto.request.ProductLifecycleRequest;
import com.crm.product.product.dto.response.ProductCreateResponse;
import com.crm.product.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a SALE saga command's business work in its OWN transaction
 * ({@code REQUIRES_NEW}), separate from the transaction holding the Inbox claim.
 *
 * <p><b>This is the one place the ideal "claim + mutation + reply in one commit" is
 * deliberately relaxed, and the reason is not convenience.</b> A saga step must be able
 * to fail as a BUSINESS outcome — a rejected basket is a fact the orchestrator has to
 * learn about, not an error to retry forever. But a {@code REQUIRED} transactional method
 * that throws marks the CALLER's transaction rollback-only, so catching the exception and
 * recording a failure reply in the same transaction would fail at commit with
 * {@code UnexpectedRollbackException} — the reply could never be written at all, and the
 * message would be redelivered until the DLQ took it. That is a worse outcome than the
 * window this class accepts.
 *
 * <p><b>What the relaxation actually costs, and why it is safe.</b>
 * <ul>
 *   <li><b>Failure path — nothing is relaxed.</b> The inner transaction rolled back, so
 *       there is no mutation; the Inbox claim and the failure reply still commit together
 *       in the caller's transaction. Atomicity holds because there is nothing else to be
 *       atomic with.</li>
 *   <li><b>Success path.</b> The mutation commits an instant before the claim and the
 *       reply. If the outer commit then fails, the work exists with no claim — so the
 *       message is redelivered, and every operation reachable from here is idempotent by
 *       construction: creation replays the FIRST response through the sale-operation
 *       coordinator (ADR-015 idempotency addendum), activation skips products that are no
 *       longer PNDG, and compensation is a documented no-op on anything already
 *       resolved. The redelivery therefore produces the same reply, not a second sale.</li>
 * </ul>
 *
 * <p>Note what does NOT need this: order-service's own saga transitions. Those are plain
 * entity mutations that never throw a business exception, so the orchestrator keeps full
 * "saga transition + outgoing command in one commit" atomicity.
 */
@Service
@RequiredArgsConstructor
public class SaleCommandExecutor {

    private final ProductService productService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProductCreateResponse prepare(ProductCreateRequest request) {
        return productService.create(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void activate(ProductLifecycleRequest request) {
        productService.confirm(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(ProductCompensationRequest request) {
        productService.compensate(request);
    }
}
