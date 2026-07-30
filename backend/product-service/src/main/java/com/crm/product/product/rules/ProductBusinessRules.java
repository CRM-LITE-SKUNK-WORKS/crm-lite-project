package com.crm.product.product.rules;

import com.crm.product.product.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductBusinessRules {

    /**
     * FR-PROD-02 presentation rule: service_address_id is only filled on the MAIN
     * product of an installation — a child product displays its parent's service
     * address. Walks up the parent chain (must run inside the read transaction:
     * the parent association is lazy beyond the first fetch-joined level).
     */
    public Long resolveEffectiveServiceAddressId(Product product) {
        for (Product current = product; current != null; current = current.getParent()) {
            if (current.getServiceAddressId() != null) {
                return current.getServiceAddressId();
            }
        }
        return null;
    }
}
