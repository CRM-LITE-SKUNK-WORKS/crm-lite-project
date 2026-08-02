package com.crm.account.account.repository;

import com.crm.account.account.entity.ProductInvolvement;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductInvolvementRepository extends JpaRepository<ProductInvolvement, Long> {

    /**
     * AC-ACCT-04-03 delete guard (ADR-013 §5): active involvement ⇔
     * status_id = ACTV AND deleted_date IS NULL — evaluated fully locally
     * (the ACTV id is the stored external contract reference, ADR-002).
     */
    boolean existsByCustomerAccountIdAndStatusIdAndDeletedDateIsNull(Long customerAccountId, Long statusId);

    /**
     * ADR-013 §5 read side (FR-PROD-01 composition): the account's involvement
     * rows for product-service. Non-deleted rows only, deliberately NOT filtered
     * by involvement status — AC-PROD-01-03 lists passive products too (the
     * displayed status comes from the product itself); the status filter above
     * belongs exclusively to the AC-ACCT-04-03 delete guard.
     */
    List<ProductInvolvement> findByCustomerAccountIdAndDeletedDateIsNullOrderByProductIdAsc(Long customerAccountId);

    /**
     * ADR-013 §8.4 idempotency support: which of the requested products are already
     * linked to this account by a non-deleted row. Re-posting an existing pair must
     * leave it untouched — duplicate involvement rows would corrupt the meaning of
     * the AC-ACCT-04-03 delete guard, and the caller is a compensating orchestrator
     * (ADR-016 §5) that may legitimately retry.
     */
    List<ProductInvolvement> findByCustomerAccountIdAndProductIdInAndDeletedDateIsNull(
            Long customerAccountId, Collection<Long> productIds);
}
