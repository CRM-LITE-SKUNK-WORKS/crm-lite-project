package com.crm.product.product.rules;

import com.crm.product.common.exception.BusinessException;
import com.crm.product.common.exception.MessageKeys;
import com.crm.product.customer.CustomerAddress;
import com.crm.product.product.entity.Product;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProductBusinessRules {

    /**
     * AC-SALE-01-11 (ADR-015 §5.9): the service address must be one of the
     * customer's ACTIVE addresses, as customer-service lists them — the same rule
     * and the same endpoint account-service applies to billing addresses
     * (ADR-013 §2.4).
     *
     * <p>Checking mere existence would not be enough. {@code prod.service_address_id}
     * is an FK-less external reference that FR-PROD-02 renders in the detail modal,
     * so an unvalidated id would let a sale attach <b>another customer's address</b>
     * to a product and display it back — a cross-customer data leak, not a cosmetic
     * gap. Only the customer-scoped list can answer "does this address belong to
     * this customer?".
     */
    public void checkServiceAddressBelongsToCustomer(Long serviceAddressId, long customerNumber,
                                                     List<CustomerAddress> addresses) {
        boolean known = addresses.stream().anyMatch(address -> serviceAddressId.equals(address.addressId()));
        if (!known) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR,
                    "serviceAddressId " + serviceAddressId + " is not an active address of customer "
                            + customerNumber);
        }
    }

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
