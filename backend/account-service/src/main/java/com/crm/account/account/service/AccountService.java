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

    AccountResponse create(AccountCreateRequest request);

    AccountResponse update(String accountNumber, AccountUpdateRequest request);

    void delete(String accountNumber);
}
