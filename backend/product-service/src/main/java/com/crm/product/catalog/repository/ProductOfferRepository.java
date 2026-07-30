package com.crm.product.catalog.repository;

import com.crm.product.catalog.entity.ProductOffer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductOfferRepository extends JpaRepository<ProductOffer, Long> {

    /**
     * Catalog read: ACTIVE offers only (local soft-delete invariant, ADR-002).
     * The spec is fetch-joined because the service type is derived through it.
     */
    @Query("""
            SELECT o FROM ProductOffer o
            JOIN FETCH o.spec
            WHERE o.statusId = :statusId AND o.deletedDate IS NULL
            ORDER BY o.id
            """)
    List<ProductOffer> findActiveWithSpec(Long statusId);
}
