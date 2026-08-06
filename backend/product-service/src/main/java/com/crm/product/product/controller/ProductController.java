package com.crm.product.product.controller;

import com.crm.product.product.dto.request.ProductCompensationRequest;
import com.crm.product.product.dto.request.ProductCreateRequest;
import com.crm.product.product.dto.request.ProductLifecycleRequest;
import com.crm.product.product.dto.response.ProductCreateResponse;
import com.crm.product.product.dto.response.ProductDetailResponse;
import com.crm.product.product.dto.response.ProductRowResponse;
import com.crm.product.product.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR-PROD-01..02 reads plus the FR-SALE-01 write slice (ADR-015 §5). No pagination
 * (FR-PROD-01 defines none). {@code accountNumber} is the public KR-11
 * billing-account number; {@code id} the public product identifier (prod.id).
 *
 * <p>Note what is still absent: there is no user-facing product <b>cancellation</b>
 * endpoint. KR-7 leaves that out of phase (AC-PROD-01-04's Action column offers only
 * a view icon), and {@code /cancel} below is a compensation command restricted to
 * never-committed PNDG rows — not the same thing (ADR-015 §5.7).
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductRowResponse>> list(@RequestParam("accountNumber") String accountNumber) {
        return ResponseEntity.ok(productService.list(accountNumber));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponse> detail(@PathVariable long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    // ---------------------------------------------- FR-SALE-01 write slice (ADR-015 §5)
    // Service-to-service commands invoked by order-service over Eureka with the
    // user's token (ADR-010). They are matched by the existing /api/products/**
    // gateway route and demand the same crm-user JWT as every other endpoint —
    // accepted for the same reason ADR-013 §7.4 accepts it for product-ids.

    /** Creates one whole installation (main + children), all PNDG, in one transaction. */
    @PostMapping
    public ResponseEntity<ProductCreateResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    /** Promotes PNDG products to ACTV. Idempotent — the caller may retry. */
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody ProductLifecycleRequest request) {
        productService.confirm(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sale-scoped compensation (ADR-015 §5.7, revised by the idempotency addendum,
     * 2026-08-06): soft-passivates PNDG products created by THIS
     * {@code saleOperationId}. Fully idempotent — an already-passivated product is a
     * no-op success, and repeating the call creates no additional writes — and refuses
     * (409) a product that belongs to a different operation rather than touching it.
     * Replaces the old generic {@code /cancel(productIds)}, which had neither
     * property.
     */
    @PostMapping("/compensate")
    public ResponseEntity<Void> compensate(@Valid @RequestBody ProductCompensationRequest request) {
        productService.compensate(request);
        return ResponseEntity.noContent().build();
    }
}
