package com.crm.product.product.repository;

import com.crm.product.product.entity.ProductCharValue;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCharValueRepository extends JpaRepository<ProductCharValue, Long> {

    /**
     * The characteristic values of a set of products, used by the confirm and cancel
     * commands (ADR-015 §5.6/§5.7): a value follows its product's lifecycle, so the
     * status transition must reach these rows too — leaving them PNDG behind a
     * committed product would make the "identify incomplete sales" query lie.
     */
    List<ProductCharValue> findByProductIdIn(Collection<Long> productIds);
}
