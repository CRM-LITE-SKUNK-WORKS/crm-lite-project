package com.crm.account.account.service.impl;

import com.crm.account.account.AccountContract;
import com.crm.account.account.dto.request.AccountCreateRequest;
import com.crm.account.account.dto.request.AccountUpdateRequest;
import com.crm.account.account.dto.request.ProductInvolvementRequest;
import com.crm.account.account.dto.response.AccountResponse;
import com.crm.account.account.dto.response.ProductInvolvementResponse;
import com.crm.account.account.entity.AccountType;
import com.crm.account.account.entity.CustomerAccount;
import com.crm.account.account.mapper.AccountMapper;
import com.crm.account.account.number.AccountNumberGenerator;
import com.crm.account.account.repository.AccountTypeRepository;
import com.crm.account.account.entity.ProductInvolvement;
import com.crm.account.account.repository.CustomerAccountRepository;
import com.crm.account.account.repository.ProductInvolvementRepository;
import com.crm.account.account.rules.AccountBusinessRules;
import com.crm.account.account.service.AccountService;
import com.crm.account.common.CurrentActorProvider;
import com.crm.account.common.exception.BusinessException;
import com.crm.account.common.exception.MessageKeys;
import com.crm.account.customer.CustomerAddress;
import com.crm.account.customer.CustomerServiceClient;
import com.crm.account.customer.CustomerSummary;
import com.crm.account.lookup.LookupCatalogService;
import com.crm.account.lookup.LookupContract;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final CustomerAccountRepository accountRepository;
    private final AccountTypeRepository accountTypeRepository;
    private final ProductInvolvementRepository involvementRepository;
    private final AccountBusinessRules rules;
    private final AccountNumberGenerator numberGenerator;
    private final AccountMapper mapper;
    private final LookupCatalogService lookupCatalog;
    private final CustomerServiceClient customerClient;
    private final CurrentActorProvider actorProvider;

    /**
     * FR-ACCT-01: 224 Billing Accounts only (the K-8 223 never appears), Active and
     * Passive, Active group first, accountNumber ascending inside each group.
     * Fully local read — no cross-service calls; an unknown customer is an empty list.
     */
    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> list(long customerNumber) {
        return accountRepository
                .findByCustomerNumberAndAccountTypeIdOrderByStatusIdAscAccountNumberAsc(
                        customerNumber, AccountContract.TYPE_BILLING_ACCOUNT_ID)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getByAccountNumber(String accountNumber) {
        return mapper.toResponse(findVisibleAccount(accountNumber));
    }

    /**
     * ADR-013 §5 read side (FR-PROD-01 composition for product-service): the
     * account's involved product ids from the local involvement projection —
     * non-deleted rows only, deliberately NOT filtered by involvement status
     * (AC-PROD-01-03 lists passive products too; the AC-ACCT-04-03 delete guard
     * keeps its own ACTV-only check). Unknown numbers — and the K-8 223, which is
     * never API-visible (ADR-013 §4.5) — are 404 via the same visibility rule as
     * the detail endpoint; a Passive 224 stays readable (AC-ACCT-04-02).
     */
    @Override
    @Transactional(readOnly = true)
    public List<Long> listProductIds(String accountNumber) {
        CustomerAccount account = findVisibleAccount(accountNumber);
        return involvementRepository.findByCustomerAccountIdAndDeletedDateIsNullOrderByProductIdAsc(account.getId())
                .stream()
                .map(ProductInvolvement::getProductId)
                .toList();
    }

    /**
     * ADR-013 §8 write side — the involvement command, the commit point of a sale
     * (ADR-016 §5.1 step 4). Bulk and single-shot: every requested product is linked
     * in THIS one local transaction, so a partial link set can never be observed.
     *
     * <p>Beyond the usual visibility rule the target must also be <b>Active</b>: a
     * Passive account stays readable (AC-ACCT-04-02) but must never acquire new
     * products. This is the server-side enforcement of AC-SALE-02-01, which the UI
     * states only as a disabled action — and a disabled button is not a check.
     *
     * <p>Idempotent per (account, product): a product already linked by a non-deleted
     * row is left untouched rather than duplicated or rejected. The caller is a
     * compensating orchestrator that may legitimately retry, and duplicate rows would
     * corrupt the meaning of the AC-ACCT-04-03 delete guard.
     *
     * <p>{@code productId} is accepted as an opaque external reference — account-service
     * deliberately does NOT call product-service to verify it (that would invert the
     * dependency direction and create a call cycle; ADR-013 §8.5).
     */
    @Override
    @Transactional
    public ProductInvolvementResponse addProductInvolvements(String accountNumber, ProductInvolvementRequest request) {
        CustomerAccount account = findVisibleAccount(accountNumber);
        rules.checkAccountCanReceiveProducts(account);

        // Distinct: a caller repeating an id within one body must not create two rows.
        List<Long> requestedIds = request.getProductIds().stream().distinct().toList();
        Set<Long> alreadyLinked = involvementRepository
                .findByCustomerAccountIdAndProductIdInAndDeletedDateIsNull(account.getId(), requestedIds)
                .stream()
                .map(ProductInvolvement::getProductId)
                .collect(Collectors.toSet());

        // Resolved through the shared catalog even when every id is already linked:
        // fail closed is a property of the write path, not of whether it had work to do
        // (ADR-002 §7).
        long activeStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_ACTIVE, LookupContract.STATUS_DOMAIN_GENERAL);
        String actor = actorProvider.get();

        for (Long productId : requestedIds) {
            if (alreadyLinked.contains(productId)) {
                continue;
            }
            ProductInvolvement involvement = new ProductInvolvement();
            involvement.setCustomerAccountId(account.getId());
            involvement.setProductId(productId);
            involvement.setShortCode(AccountContract.INVOLVEMENT_SHORT_CODE);
            involvement.setStatusId(activeStatusId);
            involvement.markCreated(actor);
            involvementRepository.save(involvement);
        }

        // Return the resulting state (same shape/ordering as the §7 read side) rather
        // than an insert count, so a retry answers identically — observable idempotency.
        return new ProductInvolvementResponse(account.getAccountNumber(),
                involvementRepository.findByCustomerAccountIdAndDeletedDateIsNullOrderByProductIdAsc(account.getId())
                        .stream()
                        .map(ProductInvolvement::getProductId)
                        .toList());
    }

    /**
     * FR-ACCT-02 / AC-ACCT-02-03 + K-8 (ADR-013 §4): validates the customer and
     * address through customer-service (user token propagated, ADR-010 addendum),
     * resolves ACTV through the shared catalog (fail closed, ADR-002), then — in this
     * ONE local ACID transaction — lazily creates the customer's 223 Customer Account
     * if missing and creates the requested 224 Billing Account. Both numbers come
     * from the same KR-11 generator/sequence; a failure anywhere rolls back
     * everything including the sequence increments.
     */
    @Override
    @Transactional
    public AccountResponse create(AccountCreateRequest request) {
        rules.checkNoUnknownFields(request.getUnknownFields());

        long customerNumber = request.getCustomerId();
        CustomerSummary customer = customerClient.fetchCustomer(customerNumber)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.CUST_NOT_FOUND,
                        "Customer " + customerNumber + " not found"));
        if (!LookupContract.STATUS_ACTIVE.equals(customer.status())) {
            // The detail endpoint only serves visible customers, but stay defensive:
            // accounts are never created for a passive customer.
            throw new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.CUST_NOT_FOUND,
                    "Customer " + customerNumber + " not found");
        }

        List<CustomerAddress> addresses = customerClient.fetchAddresses(customerNumber);
        rules.checkAddressBelongsToCustomer(request.getAddressId(), customerNumber, addresses);

        long activeStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_ACTIVE, LookupContract.STATUS_DOMAIN_GENERAL);
        String actor = actorProvider.get();

        // K-8: lazy 223 creation on the FIRST 224 — any existing 223 row (active or
        // passive) counts, a passivated 223 is never recreated. The V1 partial unique
        // index backs this check against concurrent first-creates.
        if (!accountRepository.existsByCustomerNumberAndAccountTypeId(
                customerNumber, AccountContract.TYPE_CUSTOMER_ACCOUNT_ID)) {
            CustomerAddress primaryAddress = rules.requirePrimaryAddress(customerNumber, addresses);
            persistAccount(customerNumber, AccountContract.TYPE_CUSTOMER_ACCOUNT_ID,
                    AccountContract.CUSTOMER_ACCOUNT_NAME, "Default customer account",
                    primaryAddress.addressId(), activeStatusId, actor);
        }

        CustomerAccount billingAccount = persistAccount(customerNumber, AccountContract.TYPE_BILLING_ACCOUNT_ID,
                request.getAccountName(), "Billing account", request.getAddressId(), activeStatusId, actor);
        return mapper.toResponse(billingAccount);
    }

    /**
     * FR-ACCT-03: mutable fields are exactly accountName and addressId; the address
     * is re-validated against the customer's active address list. Passive accounts
     * are read-only (409 MSG-ACCT-NOT-ACTIVE).
     */
    @Override
    @Transactional
    public AccountResponse update(String accountNumber, AccountUpdateRequest request) {
        rules.checkNoUnknownFields(request.getUnknownFields());

        CustomerAccount account = findVisibleAccount(accountNumber);
        rules.checkAccountIsActive(account);

        List<CustomerAddress> addresses = customerClient.fetchAddresses(account.getCustomerNumber());
        rules.checkAddressBelongsToCustomer(request.getAddressId(), account.getCustomerNumber(), addresses);

        account.setAccountName(request.getAccountName());
        account.setAddressId(request.getAddressId());
        account.markUpdated(actorProvider.get());
        return mapper.toResponse(accountRepository.save(account));
    }

    /**
     * FR-ACCT-04: soft passivation only — never a physical delete. Blocked by an
     * active product involvement (409 MSG-ACCT-HAS-PRODUCTS, local projection) and
     * by an already-Passive state (409 MSG-ACCT-NOT-ACTIVE). The PASV id is resolved
     * through the shared catalog (fail closed 503 when it is unreachable, ADR-002).
     */
    @Override
    @Transactional
    public void delete(String accountNumber) {
        CustomerAccount account = findVisibleAccount(accountNumber);
        rules.checkAccountIsActive(account);
        rules.checkAccountHasNoActiveProducts(account);

        long passiveStatusId = lookupCatalog.resolveStatusId("status",
                LookupContract.STATUS_PASSIVE, LookupContract.STATUS_DOMAIN_GENERAL);
        account.passivate(passiveStatusId, actorProvider.get());
        accountRepository.save(account);
    }

    /**
     * The public single-account view: 224 Billing Accounts only. A 223's number is
     * deliberately indistinguishable from a nonexistent one (K-8 — ADR-013 §4.5).
     */
    private CustomerAccount findVisibleAccount(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .filter(account -> account.getAccountType().getId() == AccountContract.TYPE_BILLING_ACCOUNT_ID)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.ACCT_NOT_FOUND,
                        "Account " + accountNumber + " not found"));
    }

    private CustomerAccount persistAccount(long customerNumber, long accountTypeId, String accountName,
                                           String description, Long addressId, long statusId, String actor) {
        AccountType accountType = accountTypeRepository.getReferenceById(accountTypeId);
        CustomerAccount account = new CustomerAccount();
        account.setCustomerNumber(customerNumber);
        account.setAccountType(accountType);
        account.setAccountNumber(numberGenerator.next(AccountContract.SEGMENT_INDIVIDUAL));
        account.setAccountName(accountName);
        account.setDescription(description);
        account.setAddressId(addressId);
        account.setStatusId(statusId);
        account.markCreated(actor);
        return accountRepository.save(account);
    }
}
