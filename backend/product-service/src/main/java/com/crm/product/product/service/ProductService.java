package com.crm.product.product.service;

import com.crm.product.product.dto.response.ProductDetailResponse;
import com.crm.product.product.dto.response.ProductRowResponse;
import java.util.List;

public interface ProductService {

    /** FR-PROD-01: the billing account's product rows (empty list = MSG-PROD-NONE, rendered by the frontend). */
    List<ProductRowResponse> list(String accountNumber);

    /** FR-PROD-02: the detail modal for one product. */
    ProductDetailResponse getById(long productId);
}
