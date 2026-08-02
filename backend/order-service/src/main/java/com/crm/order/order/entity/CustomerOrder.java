package com.crm.order.order.entity;

import com.crm.order.common.entity.StatusAwareEntity;
import com.crm.order.lookup.LookupContract;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * cust_ord: the order header. {@code orderNumber} is the KR-12 public identifier —
 * immutable and never reused, including after a compensated (CANCELLED) sale
 * (ADR-016 §4.6). {@code customerNumber} is the public business customer number, not
 * the workbook's internal {@code customer_id} (ADR-016 §2.3).
 *
 * <p>{@code totalAmount} is a price SNAPSHOT (project addition, ADR-016 §2.4): an
 * order's amount is a fact about the moment it was placed, so it is stored rather
 * than recomputed from the catalog. NULL only inside the sale request, between the
 * header write and the post-creation update (ADR-016 §5.2).
 */
@Entity
@Table(name = "cust_ord")
@Getter
@Setter
@NoArgsConstructor
public class CustomerOrder extends StatusAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, updatable = false, length = 10)
    private String orderNumber;

    @Column(name = "customer_number", nullable = false)
    private Long customerNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bsn_inter_id", nullable = false)
    private BusinessInteraction businessInteraction;

    @Column(name = "total_amount", precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("id ASC")
    private List<CustomerOrderItem> items = new ArrayList<>();

    /**
     * The order was accepted and is being processed — the workbook's
     * "Siparis Alindi Isleniyor...", which is the only status a user ever sees
     * (AC-SALE-01-15) and the one no AC moves an order out of (ADR-016 §6).
     *
     * <p>Deliberately NOT {@code isActive()}: an order's healthy state is MIDLWARE,
     * not ACTV, so the inherited generic check would answer "no" for every perfectly
     * valid order.
     */
    public boolean isInProgress() {
        return getDeletedDate() == null
                && Long.valueOf(LookupContract.STATUS_MIDDLEWARE_ID).equals(getStatusId());
    }

    public void addItem(CustomerOrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}
