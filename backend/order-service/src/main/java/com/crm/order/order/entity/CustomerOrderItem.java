package com.crm.order.order.entity;

import com.crm.order.common.entity.StatusAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * cust_ord_item: one line of the order — the offer bought and the product instance
 * it became. Both {@code productOfferId} and {@code productId} are FK-less external
 * references into product_db (ADR-016 §2.1/§2.5).
 *
 * <p>{@code productId} and {@code amount} are NULL between the header write and the
 * post-creation update (ADR-016 §5.2): the workbook's column layout demands a
 * product id that does not exist until product-service has been called, and the
 * alternative — creating products before the order — would destroy the property that
 * the sale's first write is a single, self-contained local transaction. The
 * intermediate state is never observable: it exists only inside the request, and a
 * request that dies there leaves the order CANCELLED.
 */
@Entity
@Table(name = "cust_ord_item")
@Getter
@Setter
@NoArgsConstructor
public class CustomerOrderItem extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cust_ord_id", nullable = false)
    private CustomerOrder order;

    @Column(name = "product_offer_id", nullable = false)
    private Long productOfferId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;
}
