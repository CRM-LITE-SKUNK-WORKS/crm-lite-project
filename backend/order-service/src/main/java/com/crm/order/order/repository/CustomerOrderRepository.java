package com.crm.order.order.repository;

import com.crm.order.order.entity.CustomerOrder;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    /**
     * GET /api/orders/{orderNumber} (ADR-016 §3.2). Items and the business
     * interaction are fetch-joined because the representation reads both — one query
     * per lookup, not one per line.
     *
     * <p>Deliberately NOT filtered by status: a CANCELLED order must still be
     * retrievable. It is a real record of something that was attempted, and hiding it
     * would make a compensated sale indistinguishable from one that never happened.
     */
    @Query("""
            SELECT DISTINCT o FROM CustomerOrder o
            JOIN FETCH o.businessInteraction
            LEFT JOIN FETCH o.items
            WHERE o.orderNumber = :orderNumber
            """)
    Optional<CustomerOrder> findByOrderNumberWithItems(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    /**
     * The stale-draft cleanup's queue (ADR-018 §6): WAIT orders nobody submitted or
     * abandoned, oldest first.
     *
     * <p>The business interaction is fetch-joined because the cleanup passivates both
     * rows together — without it this would be one extra query per draft, which is
     * exactly the shape of job that quietly becomes the slowest thing in the service.
     * Bounded by {@link Limit}: whatever one tick does not reach is still stale on the
     * next one.
     */
    @Query("""
            SELECT o FROM CustomerOrder o
            JOIN FETCH o.businessInteraction
            WHERE o.statusId = :waitingStatusId
              AND o.createdDate < :staleBefore
            ORDER BY o.createdDate ASC
            """)
    List<CustomerOrder> findStaleDrafts(@Param("waitingStatusId") long waitingStatusId,
                                        @Param("staleBefore") Instant staleBefore,
                                        Limit limit);
}
