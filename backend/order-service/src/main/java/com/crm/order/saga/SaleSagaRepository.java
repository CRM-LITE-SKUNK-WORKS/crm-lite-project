package com.crm.order.saga;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleSagaRepository extends JpaRepository<SaleSaga, String> {

    Optional<SaleSaga> findByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    long countByCurrentState(SaleSagaState state);

    /**
     * The recovery job's queue (ADR-018 §8): sagas whose outstanding command has gone
     * unanswered past its due time. Bounded by {@link Limit} so one tick can never drag
     * an unbounded backlog into memory — anything it does not reach is still due on the
     * next tick.
     */
    @Query("""
            SELECT s FROM SaleSaga s
            WHERE s.currentState IN :states
              AND s.nextRetryAt IS NOT NULL
              AND s.nextRetryAt <= :now
            ORDER BY s.nextRetryAt ASC
            """)
    List<SaleSaga> findDue(@Param("states") Collection<SaleSagaState> states,
                           @Param("now") Instant now,
                           Limit limit);

    /**
     * The stuck gauge (ADR-018 §9): non-terminal sagas that have not moved for a while.
     * Distinct from "due for retry" — a saga can be retried repeatedly and still be
     * stuck, which is exactly the situation the gauge has to make visible.
     */
    @Query("""
            SELECT COUNT(s) FROM SaleSaga s
            WHERE s.currentState NOT IN :terminalStates
              AND s.updatedAt < :threshold
            """)
    long countStuck(@Param("terminalStates") Collection<SaleSagaState> terminalStates,
                    @Param("threshold") Instant threshold);
}
