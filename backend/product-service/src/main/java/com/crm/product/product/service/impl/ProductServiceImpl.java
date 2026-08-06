package com.crm.product.product.service.impl;

import com.crm.product.account.AccountServiceClient;
import com.crm.product.catalog.entity.Campaign;
import com.crm.product.catalog.entity.ProductOffer;
import com.crm.product.catalog.entity.ProductSpecCharUse;
import com.crm.product.catalog.repository.CampaignOfferRepository;
import com.crm.product.catalog.repository.CampaignRepository;
import com.crm.product.catalog.repository.ProductOfferRepository;
import com.crm.product.catalog.repository.ProductSpecCharRepository;
import com.crm.product.catalog.repository.ProductSpecCharUseRepository;
import com.crm.product.common.CurrentActorProvider;
import com.crm.product.common.exception.BusinessException;
import com.crm.product.common.exception.MessageKeys;
import com.crm.product.customer.CustomerServiceClient;
import com.crm.product.lookup.LookupCatalogService;
import com.crm.product.lookup.LookupContract;
import com.crm.product.product.dto.request.ProductCharacteristicValueRequest;
import com.crm.product.product.dto.request.ProductCompensationRequest;
import com.crm.product.product.dto.request.ProductCreateItemRequest;
import com.crm.product.product.dto.request.ProductCreateRequest;
import com.crm.product.product.dto.request.ProductLifecycleRequest;
import com.crm.product.product.dto.response.CreatedProductResponse;
import com.crm.product.product.dto.response.ProductCreateResponse;
import com.crm.product.product.dto.response.ProductDetailResponse;
import com.crm.product.product.dto.response.ProductRowResponse;
import com.crm.product.product.dto.response.ServiceAddressResponse;
import com.crm.product.product.entity.Product;
import com.crm.product.product.entity.ProductCharValue;
import com.crm.product.product.idempotency.SaleOperationCoordinator;
import com.crm.product.product.idempotency.SaleOperationDecision;
import com.crm.product.product.mapper.ProductMapper;
import com.crm.product.product.repository.ProductCharValueRepository;
import com.crm.product.product.repository.ProductRepository;
import com.crm.product.product.rules.BasketValidationRules;
import com.crm.product.product.rules.CharacteristicValidationRules;
import com.crm.product.product.rules.ProductBusinessRules;
import com.crm.product.product.service.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductCharValueRepository charValueRepository;
    private final ProductOfferRepository offerRepository;
    private final ProductSpecCharUseRepository specCharUseRepository;
    private final ProductSpecCharRepository specCharRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignOfferRepository campaignOfferRepository;
    private final ProductBusinessRules rules;
    private final BasketValidationRules basketRules;
    private final CharacteristicValidationRules characteristicRules;
    private final ProductMapper mapper;
    private final AccountServiceClient accountClient;
    private final CustomerServiceClient customerClient;
    private final LookupCatalogService lookupCatalog;
    private final CurrentActorProvider actorProvider;
    private final SaleOperationCoordinator saleOperationCoordinator;
    private final ObjectMapper objectMapper;

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
                // ADR-015 §5.5: a PNDG row belongs to an in-flight (or crashed) sale and
                // is not a product the customer owns. The Status column has exactly two
                // values and the mapper renders anything non-ACTV as "Passive", so
                // leaking one would show a passive product that was never bought.
                // Defence in depth — a PNDG product normally has no involvement row yet.
                .filter(product -> !product.isPending())
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
                .filter(candidate -> !candidate.isPending())   // ADR-015 §5.5: not yet a real product
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.PROD_NOT_FOUND,
                        "Product " + productId + " not found"));

        Long serviceAddressId = rules.resolveEffectiveServiceAddressId(product);
        ServiceAddressResponse serviceAddress = serviceAddressId == null ? null
                : customerClient.fetchAddress(serviceAddressId)
                        .map(mapper::toServiceAddressResponse)
                        .orElse(null);
        return mapper.toDetailResponse(product, serviceAddress);
    }

    /**
     * FR-SALE-01 (ADR-015 §5.1): creates one whole installation — main product plus
     * children — in ONE local transaction. Bulk on purpose: a per-product endpoint
     * would make a half-created installation observable.
     *
     * <p>Order of work, each step a precondition of the next:
     * <ol>
     *   <li>basket composition (AC-SALE-01-05/08) — cheapest checks, and the ones a
     *       user can actually fix, so they report first;
     *   <li>campaign coherence, if one is claimed;
     *   <li>service-address ownership (AC-SALE-01-11);
     *   <li>per-item characteristic validation (AC-SALE-01-18/19);
     *   <li>PNDG resolved through the catalog — fail closed BEFORE anything is
     *       written (ADR-002 §7);
     *   <li>persist main product first (children need its id as parent).
     * </ol>
     * Any failure rolls the whole transaction back: nothing is persisted, which is
     * what lets ADR-016 §5.1 treat a failed step 2 as "no external state to unwind".
     *
     * <p>ADR-015 idempotency addendum (2026-08-06): {@link SaleOperationCoordinator#resolve}
     * runs in its OWN independent transaction ({@code REQUIRES_NEW} — it is a
     * different bean, so the {@code @Transactional} proxy on THIS method has no
     * bearing on it) and is called first. A REPLAY short-circuits everything below —
     * no validation, no writes, because they already happened for this exact
     * operation id. A PROCEED wraps the rest of this method: on ANY failure the
     * reservation is released (this method's own transaction rolls back with it, so
     * nothing was persisted either way) so a genuine retry with the same id gets a
     * clean second attempt rather than a permanent 409. {@code complete()} — also
     * {@code REQUIRES_NEW} — commits independently just before this method returns,
     * so the recorded snapshot is available to a replay the instant this response
     * reaches the caller.
     */
    @Override
    @Transactional
    public ProductCreateResponse create(ProductCreateRequest request) {
        SaleOperationDecision decision = saleOperationCoordinator.resolve(request.getSaleOperationId());
        if (decision instanceof SaleOperationDecision.Replay replay) {
            return readSnapshot(replay.responseSnapshot());
        }
        long reservationId = ((SaleOperationDecision.Proceed) decision).reservationId();
        try {
            ProductCreateResponse response = doCreate(request);
            saleOperationCoordinator.complete(reservationId, writeSnapshot(response), response.mainProductId());
            return response;
        } catch (RuntimeException e) {
            saleOperationCoordinator.release(reservationId);
            throw e;
        }
    }

    private ProductCreateResponse readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, ProductCreateResponse.class);
        } catch (JsonProcessingException e) {
            // Written by writeSnapshot() below and never hand-edited — this cannot
            // happen outside data corruption, which is not this method's job to fix.
            throw new IllegalStateException("Corrupt sale-operation response snapshot", e);
        }
    }

    private String writeSnapshot(ProductCreateResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not snapshot product-creation response", e);
        }
    }

    private ProductCreateResponse doCreate(ProductCreateRequest request) {
        List<Long> offerIds = request.getItems().stream().map(ProductCreateItemRequest::getOfferId).toList();
        basketRules.checkNoDuplicateOffers(offerIds);

        Map<Long, ProductOffer> offers = loadOffers(offerIds);
        List<ProductOffer> basket = offerIds.stream().map(offers::get).toList();
        basketRules.checkOffersAreActive(basket);
        basketRules.checkServiceTypeComposition(basket);

        Campaign campaign = resolveCampaign(request.getCampaignId(), offerIds);

        // AC-SALE-01-11 (ADR-015 §5.9): the service address must belong to THIS
        // customer. An unvalidated id would let a sale attach another customer's
        // address to a product, which FR-PROD-02 then displays back.
        rules.checkServiceAddressBelongsToCustomer(request.getServiceAddressId(), request.getCustomerNumber(),
                customerClient.fetchAddresses(request.getCustomerNumber()));

        // Validate EVERY item before persisting any of them: a rejection must not
        // leave the first two products written and the third refused.
        Map<Long, Map<Long, String>> validatedValues = new LinkedHashMap<>();
        for (ProductCreateItemRequest item : request.getItems()) {
            validatedValues.put(item.getOfferId(), validateCharacteristics(item));
        }

        long pendingStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_PENDING, LookupContract.STATUS_DOMAIN_PROD);
        String actor = actorProvider.get();

        // AC-SALE-01-09: the INTERNET offer is the main product and the only one
        // carrying the service address; the others hang off it. Derived from the
        // service type, never declared by the caller (ADR-015 §5.3).
        ProductOffer mainOffer = basket.stream()
                .filter(offer -> LookupContract.SERVICE_TYPE_INTERNET_ID == offer.getSpec().getServiceTypeId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Composition rules passed but no internet offer was found"));

        Product mainProduct = persistProduct(mainOffer, null, campaign, request.getServiceAddressId(),
                pendingStatusId, actor, validatedValues.get(mainOffer.getId()), request.getSaleOperationId());

        List<CreatedProductResponse> created = new ArrayList<>();
        created.add(toCreated(mainOffer, mainProduct, true));
        for (ProductOffer offer : basket) {
            if (offer.getId().equals(mainOffer.getId())) {
                continue;
            }
            // Children carry NO service address of their own: FR-PROD-02 resolves it
            // by walking up to the parent, and storing a copy would create a second
            // truth that could drift.
            Product child = persistProduct(offer, mainProduct, campaign, null,
                    pendingStatusId, actor, validatedValues.get(offer.getId()), request.getSaleOperationId());
            created.add(toCreated(offer, child, false));
        }

        // AC-SALE-01-12 Total Amount, derived here so one definition of the sum
        // exists (the same rule the campaign read side already follows).
        BigDecimal totalAmount = created.stream()
                .map(CreatedProductResponse::amount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ProductCreateResponse(
                campaign == null ? null : campaign.getCampaignCode(),
                campaign == null ? null : campaign.getName(),
                mainProduct.getId(),
                totalAmount,
                created);
    }

    /**
     * ADR-015 §5.6: promotes the given PNDG products — and their characteristic
     * values — to ACTV. Idempotent: ids already ACTV are left untouched, because the
     * caller may retry (ADR-016 §5.3) and a second call must not be an error.
     */
    @Override
    @Transactional
    public void confirm(ProductLifecycleRequest request) {
        List<Product> products = loadProductsForLifecycle(request.getProductIds());
        long activeStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_ACTIVE, LookupContract.STATUS_DOMAIN_GENERAL);
        String actor = actorProvider.get();

        List<Long> promoted = new ArrayList<>();
        for (Product product : products) {
            if (!product.isPending()) {
                continue;   // already confirmed by an earlier attempt
            }
            product.setStatusId(activeStatusId);
            product.markUpdated(actor);
            promoted.add(product.getId());
        }
        if (!promoted.isEmpty()) {
            // Values follow their product: leaving them PNDG behind a committed
            // product would make the "find incomplete sales" query lie.
            for (ProductCharValue value : charValueRepository.findByProductIdIn(promoted)) {
                value.setStatusId(activeStatusId);
                value.markUpdated(actor);
            }
        }
    }

    /**
     * ADR-015 §5.7, revised by the idempotency addendum (2026-08-06): the
     * compensation counterpart — soft-passivates PNDG products that belong to
     * {@code request.saleOperationId}, with the full invariant (PASV +
     * deleted_date/by), cascading to their characteristic values. Physical deletion
     * is never used anywhere in this project.
     *
     * <p>Replaces the old generic {@code cancel(productIds)}, which rejected a
     * repeat call outright (a second call always found the products already PASV,
     * hence never PNDG) and had no notion of WHICH sale a product belonged to. This
     * version is genuinely idempotent — see the three cases below — and sale-scoped:
     * <ul>
     *   <li>unknown id → ignored (nothing to compensate, not an error: a caller
     *       cannot be made to distinguish "already gone" from "never existed");
     *   <li>found but owned by a DIFFERENT {@code saleOperationId} → 409
     *       {@code MSG-SALE-OPERATION-MISMATCH}, refused outright — this is the
     *       "cannot cancel products belonging to another sale operation" guarantee;
     *   <li>found, owned by THIS operation, already PASV (or, after ADR-016 §5.3's
     *       confirm step, ACTV) → left untouched, no-op success;
     *   <li>found, owned by THIS operation, still PNDG → passivated.
     * </ul>
     */
    @Override
    @Transactional
    public void compensate(ProductCompensationRequest request) {
        List<Product> products = productRepository.findAllById(request.getProductIds());

        List<Long> mismatched = products.stream()
                .filter(product -> !request.getSaleOperationId().equals(product.getSaleOperationId()))
                .map(Product::getId)
                .toList();
        if (!mismatched.isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.SALE_OPERATION_MISMATCH,
                    "These products do not belong to sale operation " + request.getSaleOperationId()
                            + ": " + mismatched);
        }

        List<Product> stillPending = products.stream().filter(Product::isPending).toList();
        if (stillPending.isEmpty()) {
            return;   // everything requested is unknown or already resolved — no-op
        }

        long passiveStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_PASSIVE, LookupContract.STATUS_DOMAIN_GENERAL);
        String actor = actorProvider.get();

        List<Long> pendingIds = stillPending.stream().map(Product::getId).toList();
        for (ProductCharValue value : charValueRepository.findByProductIdIn(pendingIds)) {
            value.passivate(passiveStatusId, actor);
        }
        for (Product product : stillPending) {
            product.passivate(passiveStatusId, actor);
        }
    }

    // ------------------------------------------------------------------ helpers

    private Map<Long, ProductOffer> loadOffers(List<Long> offerIds) {
        Map<Long, ProductOffer> offers = new LinkedHashMap<>();
        offerRepository.findWithSpecByIdIn(offerIds).forEach(offer -> offers.put(offer.getId(), offer));
        List<Long> unknown = offerIds.stream().filter(id -> !offers.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            // A nonexistent offer is a client defect, not an inactive-offer outcome:
            // MSG-SALE-OFFER-INACTIVE would tell the user something false.
            throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR,
                    "Unknown offer ids: " + unknown);
        }
        return offers;
    }

    /**
     * ADR-015 §6: a claimed campaign must be Active and must actually contain every
     * submitted offer. AC-SALE-01-04 adds campaign offers as a set, so a basket
     * naming a campaign it does not match is a client defect — not a discount to be
     * honoured.
     */
    private Campaign resolveCampaign(String campaignCode, List<Long> offerIds) {
        if (campaignCode == null || campaignCode.isBlank()) {
            return null;
        }
        Campaign campaign = campaignRepository.findByCampaignCode(campaignCode)
                .filter(Campaign::isActive)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR,
                        "Unknown or inactive campaign: " + campaignCode));

        List<Long> memberOfferIds = campaignOfferRepository
                .findActiveMembers(campaign.getId(), LookupContract.STATUS_ACTIVE_ID)
                .stream()
                .map(member -> member.getOffer().getId())
                .toList();
        List<Long> outsiders = offerIds.stream().filter(id -> !memberOfferIds.contains(id)).toList();
        if (!outsiders.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR,
                    "These offers are not part of campaign " + campaignCode + ": " + outsiders);
        }
        return campaign;
    }

    private Map<Long, String> validateCharacteristics(ProductCreateItemRequest item) {
        List<ProductSpecCharUse> schema = specCharUseRepository
                .findActiveByOfferId(item.getOfferId(), LookupContract.STATUS_ACTIVE_ID);
        Map<Long, String> submitted = new LinkedHashMap<>();
        for (ProductCharacteristicValueRequest value : item.getCharacteristics()) {
            submitted.put(value.getCharacteristicId(), value.getValue());
        }
        return characteristicRules.validate(item.getOfferId(), schema, submitted);
    }

    private Product persistProduct(ProductOffer offer, Product parent, Campaign campaign, Long serviceAddressId,
                                   long statusId, String actor, Map<Long, String> values, String saleOperationId) {
        Product product = new Product();
        product.setOffer(offer);
        product.setSpec(offer.getSpec());
        product.setParent(parent);
        product.setCampaign(campaign);
        product.setSaleOperationId(saleOperationId);
        // The workbook's PROD rows name the product after what was sold; there is no
        // FR-defined naming rule, so the offer's own name is the honest default.
        product.setName(offer.getName());
        product.setDescription(offer.getDescription());
        product.setServiceAddressId(serviceAddressId);
        product.setServiceStartDate(java.time.LocalDate.now());
        // transaction_id stays NULL — ADR-015 §8.2 (no product_db → order_db link).
        product.setStatusId(statusId);
        product.markCreated(actor);
        Product saved = productRepository.save(product);

        values.forEach((characteristicId, value) -> {
            ProductCharValue charValue = new ProductCharValue();
            charValue.setProduct(saved);
            charValue.setCharacteristic(specCharRepository.getReferenceById(characteristicId));
            charValue.setVal(value);
            charValue.setStatusId(statusId);
            charValue.markCreated(actor);
            charValueRepository.save(charValue);
        });
        return saved;
    }

    private CreatedProductResponse toCreated(ProductOffer offer, Product product, boolean main) {
        return new CreatedProductResponse(offer.getId(), offer.getName(), product.getId(), main,
                offer.getTotalPrice());
    }

    private List<Product> loadProductsForLifecycle(List<Long> productIds) {
        List<Product> products = productRepository.findAllById(productIds);
        if (products.size() != productIds.stream().distinct().count()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.PROD_NOT_FOUND,
                    "One or more products were not found: " + productIds);
        }
        return products;
    }
}
