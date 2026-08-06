package com.crm.product.product.idempotency;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleOperationRepository extends JpaRepository<SaleOperation, Long> {

    Optional<SaleOperation> findBySaleOperationId(String saleOperationId);
}
