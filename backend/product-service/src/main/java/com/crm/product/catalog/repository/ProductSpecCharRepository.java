package com.crm.product.catalog.repository;

import com.crm.product.catalog.entity.ProductSpecChar;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Only used to obtain a reference when persisting a prod_char_val row: the
 * characteristic itself was already validated against the offer's schema
 * (ADR-015 §6), so no lookup query is needed — just the FK.
 */
public interface ProductSpecCharRepository extends JpaRepository<ProductSpecChar, Long> {
}
