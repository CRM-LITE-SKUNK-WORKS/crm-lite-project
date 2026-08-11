package com.crm.account.messaging;

import com.crm.account.account.dto.request.ProductInvolvementRequest;
import com.crm.account.account.dto.response.AccountResponse;
import com.crm.account.account.service.AccountService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a SALE saga command's business work in its OWN transaction
 * ({@code REQUIRES_NEW}), separate from the transaction holding the Inbox claim.
 *
 * <p>See product-service's counterpart for the full reasoning. The short version: a saga
 * step must be able to fail as a BUSINESS outcome, but a {@code REQUIRED} transactional
 * method that throws marks the caller's transaction rollback-only — so the failure reply
 * could never be committed. On the failure path nothing was written, so the claim and the
 * reply are still atomic; on the success path the mutation commits an instant earlier,
 * and every operation here is idempotent (involvement per (account, product),
 * compensation scoped and no-op once passivated), so a redelivery converges rather than
 * duplicating.
 */
@Service
@RequiredArgsConstructor
public class SaleCommandExecutor {

    private final AccountService accountService;

    /**
     * The account check is a READ, so it needs no transaction of its own for isolation —
     * it is here purely for symmetry of failure handling: a passive or unknown account
     * throws, and that exception must not poison the Inbox transaction that is about to
     * record the rejection.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public AccountResponse check(String accountNumber) {
        return accountService.getByAccountNumber(accountNumber);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void link(String accountNumber, List<Long> productIds, String saleOperationId) {
        ProductInvolvementRequest request = new ProductInvolvementRequest();
        request.setProductIds(productIds);
        request.setSaleOperationId(saleOperationId);
        accountService.addProductInvolvements(accountNumber, request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int compensate(String accountNumber, String saleOperationId) {
        return accountService.compensateProductInvolvements(accountNumber, saleOperationId);
    }
}
