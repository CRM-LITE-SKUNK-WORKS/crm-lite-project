package com.crm.product.product.rules;

import com.crm.product.catalog.entity.ProductOffer;
import com.crm.product.common.exception.BusinessException;
import com.crm.product.common.exception.MessageKeys;
import com.crm.product.lookup.LookupContract;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * AC-SALE-01-05/08 basket composition, enforced server-side (ADR-015 §6).
 *
 * <p>The FR states these as Offer-Selection screen behaviour on LBL-NEXT. A
 * screen-only check is not a check: the same request can be posted directly, and
 * order-service forwards the basket verbatim without validating it. So the rules run
 * here, at the point of persistence, where the PROD_OFR/PROD_SPEC facts they depend
 * on actually live.
 *
 * <p>Each rule maps to its own analyst message key, never a generic 400 — the
 * frontend renders the specific text the catalog defines for each failure.
 */
@Component
public class BasketValidationRules {

    /** AC-SALE-01-05: the same offer may not appear twice in one basket. */
    public void checkNoDuplicateOffers(List<Long> offerIds) {
        if (offerIds.stream().distinct().count() != offerIds.size()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.SALE_DUP_OFFER,
                    "The same offer was submitted more than once");
        }
    }

    /** AC-SALE-01-08: every offer in the basket must be Active. */
    public void checkOffersAreActive(List<ProductOffer> offers) {
        List<Long> inactive = offers.stream().filter(offer -> !offer.isActive()).map(ProductOffer::getId).toList();
        if (!inactive.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.SALE_OFFER_INACTIVE,
                    "These offers are not active: " + inactive);
        }
    }

    /**
     * AC-SALE-01-08: exactly one INTERNET, one RESOURCE and one ACTIVATION offer.
     * The service type is derived through the offer's spec — PROD_OFR has no
     * service-type column of its own (the same derivation the read side uses).
     *
     * <p>Checked in catalog order (INTERNET → RESOURCE → ACTIVATION) with "missing"
     * before "too many" for each, so a basket failing several rules reports the one
     * a user would fix first rather than an arbitrary one.
     */
    public void checkServiceTypeComposition(List<ProductOffer> offers) {
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (ProductOffer offer : offers) {
            Long serviceTypeId = offer.getSpec().getServiceTypeId();
            counts.merge(serviceTypeId, 1, Integer::sum);
        }
        check(counts, LookupContract.SERVICE_TYPE_INTERNET_ID,
                MessageKeys.SALE_NO_INTERNET, MessageKeys.SALE_MULTI_INTERNET, "internet");
        check(counts, LookupContract.SERVICE_TYPE_RESOURCE_ID,
                MessageKeys.SALE_NO_RESOURCE, MessageKeys.SALE_MULTI_RESOURCE, "resource");
        check(counts, LookupContract.SERVICE_TYPE_ACTIVATION_ID,
                MessageKeys.SALE_NO_ACTIVATION, MessageKeys.SALE_MULTI_ACTIVATION, "activation");
    }

    private void check(Map<Long, Integer> counts, long serviceTypeId, String missingKey, String multipleKey,
                       String label) {
        int count = counts.getOrDefault(serviceTypeId, 0);
        if (count == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, missingKey,
                    "The basket must contain exactly one " + label + " offer, but contains none");
        }
        if (count > 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, multipleKey,
                    "The basket must contain exactly one " + label + " offer, but contains " + count);
        }
    }
}
