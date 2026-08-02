package com.crm.account.account.mapper;

import com.crm.account.account.AccountContract;
import com.crm.account.account.dto.response.AccountResponse;
import com.crm.account.account.entity.CustomerAccount;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    /**
     * Must run inside a transaction (the lazy accountType association is read here).
     * accountStatus is derived from the local soft-delete invariant, never stored.
     */
    public AccountResponse toResponse(CustomerAccount account) {
        return new AccountResponse(
                account.getAccountNumber(),
                account.getCustomerNumber(),
                account.getAccountName(),
                account.getAccountType().getAccountTypeCode(),
                account.getAccountType().getName(),
                account.getAddressId(),
                account.isActive() ? AccountContract.STATUS_LABEL_ACTIVE : AccountContract.STATUS_LABEL_PASSIVE);
    }
}
