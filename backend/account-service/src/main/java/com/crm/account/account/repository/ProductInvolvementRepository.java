package com.crm.account.account.repository;

import com.crm.account.account.entity.ProductInvolvement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductInvolvementRepository extends JpaRepository<ProductInvolvement, Long> {

    /**
     * AC-ACCT-04-03 delete guard (ADR-013 §5): active involvement ⇔
     * status_id = ACTV AND deleted_date IS NULL — evaluated fully locally
     * (the ACTV id is the stored external contract reference, ADR-002).
     */
    boolean existsByCustomerAccountIdAndStatusIdAndDeletedDateIsNull(Long customerAccountId, Long statusId);
}
