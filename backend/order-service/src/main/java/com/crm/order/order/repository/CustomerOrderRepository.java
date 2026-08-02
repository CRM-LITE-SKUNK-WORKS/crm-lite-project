package com.crm.order.order.repository;

import com.crm.order.order.entity.CustomerOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
