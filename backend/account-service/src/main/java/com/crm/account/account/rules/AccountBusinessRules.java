package com.crm.account.account.rules;

import com.crm.account.account.entity.CustomerAccount;
import com.crm.account.account.repository.ProductInvolvementRepository;
import com.crm.account.common.exception.BusinessException;
import com.crm.account.common.exception.MessageKeys;
import com.crm.account.customer.CustomerAddress;
import com.crm.account.lookup.LookupContract;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountBusinessRules {

    private final ProductInvolvementRepository involvementRepository;

    /**
     * ADR-013 §3.4: a request body carrying any property outside the declared DTO
     * fields (immutable accountNumber/accountType, unknown junk, ...) is rejected
     * whole — never silently ignored.
     */
    public void checkNoUnknownFields(Map<String, Object> unknownFields) {
        if (!unknownFields.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.ACCT_IMMUTABLE_FIELD,
                    "These fields cannot be set or changed: " + String.join(", ", unknownFields.keySet()));
        }
    }

    /** Passive accounts are read-only: update/delete are refused (sprint decision, ADR-013 §3.4/3.5). */
    public void checkAccountIsActive(CustomerAccount account) {
        if (!account.isActive()) {
            throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.ACCT_NOT_ACTIVE,
                    "Account " + account.getAccountNumber() + " is passive and cannot be modified");
        }
    }

    /**
     * ADR-013 §8.2: an account may only acquire new products while it is Active.
     * A Passive 224 stays readable (AC-ACCT-04-02) but selling into it would
     * resurrect a passivated account through the side door — this is the
     * server-side enforcement of AC-SALE-02-01, which the FR states only as a
     * disabled UI action.
     *
     * <p>Separate from {@link #checkAccountIsActive} on purpose: that one answers
     * "may the user edit this account?", this one "may a sale attach products to
     * it?". They share a message key today because the cause is the same, but they
     * are different questions and a future divergence must not have to untangle one
     * method serving two callers.
     */
    public void checkAccountCanReceiveProducts(CustomerAccount account) {
        if (!account.isActive()) {
            throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.ACCT_NOT_ACTIVE,
                    "Account " + account.getAccountNumber() + " is passive and cannot receive products");
        }
    }

    /**
     * AC-ACCT-04-03 delete guard against the LOCAL involvement projection (ADR-013 §5)
     * — the sole source of truth until product-service exists.
     */
    public void checkAccountHasNoActiveProducts(CustomerAccount account) {
        boolean hasActiveProducts = involvementRepository.existsByCustomerAccountIdAndStatusIdAndDeletedDateIsNull(
                account.getId(), LookupContract.STATUS_ACTIVE_ID);
        if (hasActiveProducts) {
            throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.ACCT_HAS_PRODUCTS,
                    "There are active products linked to account " + account.getAccountNumber());
        }
    }

    /**
     * AC-ACCT-02-02 / FR-ACCT-03: the billing address must be one of the customer's
     * ACTIVE addresses as customer-service lists them (ADR-013 §2.4).
     */
    public void checkAddressBelongsToCustomer(Long addressId, long customerNumber, List<CustomerAddress> addresses) {
        boolean known = addresses.stream().anyMatch(address -> addressId.equals(address.addressId()));
        if (!known) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR,
                    "addressId " + addressId + " is not an active address of customer " + customerNumber);
        }
    }

    /**
     * K-8 (ADR-013 §4.3): the automatic 223 Customer Account is linked to the
     * customer's primary active address. customer-service guarantees exactly one for
     * an active customer; its absence is a data-integrity problem we refuse to
     * paper over with a nullable address.
     */
    public CustomerAddress requirePrimaryAddress(long customerNumber, List<CustomerAddress> addresses) {
        return addresses.stream()
                .filter(CustomerAddress::primary)
                .findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR,
                        "Customer " + customerNumber + " has no active primary address"));
    }
}
