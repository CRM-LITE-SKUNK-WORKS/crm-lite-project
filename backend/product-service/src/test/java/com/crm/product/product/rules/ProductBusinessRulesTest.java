package com.crm.product.product.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.crm.product.product.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-PROD-02 service-address resolution: a child product displays its parent's
 * service address, walking up an arbitrary-depth parent chain.
 */
class ProductBusinessRulesTest {

    private final ProductBusinessRules rules = new ProductBusinessRules();

    @Test
    @DisplayName("main product with its own address resolves to itself")
    void mainProductResolvesOwnAddress() {
        Product main = product(null, 1L);
        assertThat(rules.resolveEffectiveServiceAddressId(main)).isEqualTo(1L);
    }

    @Test
    @DisplayName("direct child resolves the parent's address")
    void directChildResolvesParentAddress() {
        Product main = product(null, 4L);
        Product child = product(main, null);
        assertThat(rules.resolveEffectiveServiceAddressId(child)).isEqualTo(4L);
    }

    @Test
    @DisplayName("grandchild (multi-level chain) walks up past an intermediate null to the main product's address")
    void multiLevelChainResolvesToMainAddress() {
        Product main = product(null, 8L);
        Product child = product(main, null);
        Product grandchild = product(child, null);
        assertThat(rules.resolveEffectiveServiceAddressId(grandchild)).isEqualTo(8L);
    }

    @Test
    @DisplayName("no address anywhere in the chain resolves to null")
    void nullEverywhereResolvesToNull() {
        Product main = product(null, null);
        Product child = product(main, null);
        assertThat(rules.resolveEffectiveServiceAddressId(child)).isNull();
    }

    private static Product product(Product parent, Long serviceAddressId) {
        Product product = new Product();
        product.setParent(parent);
        product.setServiceAddressId(serviceAddressId);
        return product;
    }
}
