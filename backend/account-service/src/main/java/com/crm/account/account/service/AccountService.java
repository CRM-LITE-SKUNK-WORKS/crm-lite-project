package com.crm.account.account.service;

import com.crm.account.account.dto.request.AccountCreateRequest;
import com.crm.account.account.dto.request.AccountUpdateRequest;
import com.crm.account.account.dto.request.ProductInvolvementRequest;
import com.crm.account.account.dto.response.AccountResponse;
import com.crm.account.account.dto.response.ProductInvolvementResponse;
import java.util.List;

public interface AccountService {

    List<AccountResponse> list(long customerNumber);

    AccountResponse getByAccountNumber(String accountNumber);

    List<Long> listProductIds(String accountNumber);

    ProductInvolvementResponse addProductInvolvements(String accountNumber, ProductInvolvementRequest request);

    /**
     * Saga-scoped involvement compensation (ADR-018 §7). INTERNAL: invoked only by the
     * SALE saga's compensation command and exposed by no endpoint — ADR-013 §8.6's
     * refusal to create a user-facing involvement-delete stands unchanged.
     *
     * @return how many rows this sale operation owned and passivated (0 is a success)
     */
    int compensateProductInvolvements(String accountNumber, String saleOperationId);

    AccountResponse create(AccountCreateRequest request);

    AccountResponse update(String accountNumber, AccountUpdateRequest request);

    void delete(String accountNumber);
}
