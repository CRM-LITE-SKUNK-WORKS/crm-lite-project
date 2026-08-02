package com.crm.product.catalog.repository;

import com.crm.product.catalog.entity.ProductSpecCharUse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductSpecCharUseRepository extends JpaRepository<ProductSpecCharUse, Long> {

    /**
     * The characteristic schema of one offer (AC-SALE-01-10/20): reached through the
     * offer's spec, because an offer declares no characteristics of its own.
     *
     * <p>Active uses only — a passivated characteristic-use must stop being asked for
     * on new sales, while historical prod_char_val rows keep referencing it. Ordered
     * by id so the Product Configuration screen renders fields in a stable sequence
     * across page loads; no FR defines an ordering, and an unstable one would be a
     * UI defect.
     */
    @Query("""
            SELECT u FROM ProductSpecCharUse u
            JOIN FETCH u.characteristic c
            WHERE u.spec.id = (SELECT o.spec.id FROM ProductOffer o WHERE o.id = :offerId)
              AND u.statusId = :activeStatusId AND u.deletedDate IS NULL
              AND c.statusId = :activeStatusId AND c.deletedDate IS NULL
            ORDER BY u.id
            """)
    List<ProductSpecCharUse> findActiveByOfferId(Long offerId, long activeStatusId);
}
