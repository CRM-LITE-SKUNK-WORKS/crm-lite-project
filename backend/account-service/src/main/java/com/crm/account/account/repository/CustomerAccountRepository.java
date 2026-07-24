package com.crm.account.account.repository;

import com.crm.account.account.entity.CustomerAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, Long> {

    /**
     * FR-ACCT-01 list: one account type (224), BOTH Active and Passive rows
     * (AC-ACCT-01-03 — passivated rows keep deleted_date set and stay visible),
     * sorted Active first then Passive (GNL_ST contract: ACTV=1 < PASV=2), then
     * accountNumber ascending inside each group (AC-ACCT-01-04; VARCHAR(10) KR-11
     * numbers are fixed-width, so lexicographic order is numeric order).
     */
    List<CustomerAccount> findByCustomerNumberAndAccountTypeIdOrderByStatusIdAscAccountNumberAsc(
            Long customerNumber, Long accountTypeId);

    Optional<CustomerAccount> findByAccountNumber(String accountNumber);

    /** K-8 existence check: ANY 223 row (active or passive) counts — never recreate. */
    boolean existsByCustomerNumberAndAccountTypeId(Long customerNumber, Long accountTypeId);
}
