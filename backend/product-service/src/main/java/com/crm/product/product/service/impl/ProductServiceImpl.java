package com.crm.product.product.service.impl;

import com.crm.product.account.AccountServiceClient;
import com.crm.product.common.exception.BusinessException;
import com.crm.product.common.exception.MessageKeys;
import com.crm.product.customer.CustomerServiceClient;
import com.crm.product.product.dto.response.ProductDetailResponse;
import com.crm.product.product.dto.response.ProductRowResponse;
import com.crm.product.product.dto.response.ServiceAddressResponse;
import com.crm.product.product.entity.Product;
import com.crm.product.product.mapper.ProductMapper;
import com.crm.product.product.repository.ProductRepository;
import com.crm.product.product.rules.ProductBusinessRules;
import com.crm.product.product.service.ProductService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductBusinessRules rules;
    private final ProductMapper mapper;
    private final AccountServiceClient accountClient;
    private final CustomerServiceClient customerClient;

    /**
     * FR-PROD-01 composition (ADR-013 §5 read side): the product ↔ account link is
     * owned by account-service, so the ids come from its involvement projection —
     * this service never reads account_db. An unknown (or K-8-hidden 223) account
     * is 404 MSG-ACCT-NOT-FOUND, mirroring account-service's own contract; an
     * account without products is a 200 empty array (the frontend renders
     * MSG-PROD-NONE). No pagination — FR-PROD-01 defines none.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ProductRowResponse> list(String accountNumber) {
        List<Long> productIds = accountClient.fetchProductIds(accountNumber)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.ACCT_NOT_FOUND,
                        "Account " + accountNumber + " not found"));
        if (productIds.isEmpty()) {
            return List.of();
        }
        return productRepository.findRowsByIdIn(productIds)
                .stream()
                .map(mapper::toRowResponse)
                .toList();
    }

    /**
     * FR-PROD-02: the Service Address is resolved through customer-service (the
     * stored service_address_id is an FK-less external reference; a child product
     * shows its parent's address). customer-service being unreachable fails the
     * read closed (503) — a vanished address only blanks the block.
     */
    @Override
    @Transactional(readOnly = true)
    public ProductDetailResponse getById(long productId) {
        Product product = productRepository.findDetailById(productId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.PROD_NOT_FOUND,
                        "Product " + productId + " not found"));

        Long serviceAddressId = rules.resolveEffectiveServiceAddressId(product);
        ServiceAddressResponse serviceAddress = serviceAddressId == null ? null
                : customerClient.fetchAddress(serviceAddressId)
                        .map(mapper::toServiceAddressResponse)
                        .orElse(null);
        return mapper.toDetailResponse(product, serviceAddress);
    }
}
