package com.crm.product.product.service;

import com.crm.product.product.dto.request.ProductCreateRequest;
import com.crm.product.product.dto.request.ProductLifecycleRequest;
import com.crm.product.product.dto.response.ProductCreateResponse;
import com.crm.product.product.dto.response.ProductDetailResponse;
import com.crm.product.product.dto.response.ProductRowResponse;
import java.util.List;

public interface ProductService {

    /** FR-PROD-01: the billing account's product rows (empty list = MSG-PROD-NONE, rendered by the frontend). */
    List<ProductRowResponse> list(String accountNumber);

    /** FR-PROD-02: the detail modal for one product. */
    ProductDetailResponse getById(long productId);

    /** FR-SALE-01 (ADR-015 §5.1): create one installation, PNDG, in one local transaction. */
    ProductCreateResponse create(ProductCreateRequest request);

    /** ADR-015 §5.6: promote PNDG products (and their characteristic values) to ACTV. Idempotent. */
    void confirm(ProductLifecycleRequest request);

    /** ADR-015 §5.7: compensation — soft-passivate never-committed (PNDG) products. */
    void cancel(ProductLifecycleRequest request);
}
