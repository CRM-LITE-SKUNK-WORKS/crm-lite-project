package com.crm.product.product.repository;

import com.crm.product.product.entity.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * FR-PROD-01 rows for the ids account-service's involvement projection
     * returned. Deliberately NO active filter: passive products stay listed with
     * Status "Passive" (AC-PROD-01-03). Campaign fetch-joined for the campaign
     * columns; id order keeps the list deterministic.
     */
    @Query("""
            SELECT p FROM Product p
            LEFT JOIN FETCH p.campaign
            WHERE p.id IN :ids
            ORDER BY p.id
            """)
    List<Product> findRowsByIdIn(List<Long> ids);

    /**
     * FR-PROD-02 detail graph: offer (+ spec through it), campaign and parent are
     * all read while mapping — fetch-joined so the modal is a single query (the
     * parent's own to-one associations stay lazy; only its service_address_id is
     * touched, inside the read transaction).
     */
    @Query("""
            SELECT p FROM Product p
            JOIN FETCH p.offer
            JOIN FETCH p.spec
            LEFT JOIN FETCH p.campaign
            LEFT JOIN FETCH p.parent
            WHERE p.id = :id
            """)
    Optional<Product> findDetailById(Long id);
}
