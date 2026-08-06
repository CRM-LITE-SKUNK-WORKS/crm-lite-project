package com.crm.order.order.idempotency;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKeyRecord, Long> {

    Optional<IdempotencyKeyRecord> findByIdempotencyKey(String idempotencyKey);
}
