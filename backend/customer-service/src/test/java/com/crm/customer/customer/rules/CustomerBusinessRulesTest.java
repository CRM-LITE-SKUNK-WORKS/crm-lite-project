package com.crm.customer.customer.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crm.customer.common.exception.BusinessException;
import com.crm.customer.common.exception.MessageKeys;
import com.crm.customer.customer.entity.Customer;
import com.crm.customer.customer.repository.CustomerRepository;
import com.crm.customer.customer.repository.IndividualRepository;
import com.crm.customer.lookup.LookupContract;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CustomerBusinessRulesTest {

    private CustomerRepository customerRepository;
    private IndividualRepository individualRepository;
    private CustomerBusinessRules rules;

    @BeforeEach
    void setUp() {
        customerRepository = mock(CustomerRepository.class);
        individualRepository = mock(IndividualRepository.class);
        rules = new CustomerBusinessRules(customerRepository, individualRepository);
    }

    // The former atLeastOneSearchCriterion tests were removed with the rule itself:
    // FR/AC v8 (2026-07-16) makes the no-criteria request the valid browse-all mode
    // (AC-CUST-01-00, ADR-005) — covered end-to-end in CustomerServiceIntegrationTest.

    @Test
    void crossServiceCriteria_accountNumberStill501() {
        assertThatThrownBy(() -> rules.checkNoUnsupportedCrossServiceSearchCriterion("0101112900", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
                    assertThat(be.getMessageKey()).isEqualTo(MessageKeys.FEATURE_NOT_IMPLEMENTED);
                });
    }

    @Test
    void crossServiceCriteria_orderNumberStill501() {
        assertThatThrownBy(() -> rules.checkNoUnsupportedCrossServiceSearchCriterion(null, "5001"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void crossServiceCriteria_passesWhenNoneProvided() {
        rules.checkNoUnsupportedCrossServiceSearchCriterion(null, null);
    }

    @Test
    void customerExistsAndActive_throwsNotFoundWhenMissing() {
        when(customerRepository.findByCustomerNumber(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rules.checkCustomerExistsAndActive(9999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessageKey())
                        .isEqualTo(MessageKeys.CUST_NOT_FOUND));
    }

    @Test
    void customerExistsAndActive_throwsNotFoundWhenSoftDeleted() {
        Customer passive = new Customer();
        passive.setStatusId(LookupContract.STATUS_PASSIVE_ID);
        passive.markDeleted("system");
        when(customerRepository.findByCustomerNumber(1003L)).thenReturn(Optional.of(passive));

        assertThatThrownBy(() -> rules.checkCustomerExistsAndActive(1003L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessageKey())
                        .isEqualTo(MessageKeys.CUST_NOT_FOUND));
    }

    @Test
    void customerExistsAndActive_returnsActiveCustomer() {
        Customer active = new Customer();
        active.setStatusId(LookupContract.STATUS_ACTIVE_ID);
        when(customerRepository.findByCustomerNumber(1001L)).thenReturn(Optional.of(active));

        assertThat(rules.checkCustomerExistsAndActive(1001L)).isSameAs(active);
    }

    @Test
    void nationalityIdUniqueness_createChecksAllRowsIncludingDeleted() {
        // ADR-003: the repository query has no active/deleted filter — one mock covers
        // the "held by a soft-deleted customer" case exercised end-to-end in the
        // integration test.
        when(individualRepository.existsByNationalityId("34567890123")).thenReturn(true);

        assertThatThrownBy(() -> rules.checkNationalityIdIsUniqueForCreate("34567890123"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getMessageKey()).isEqualTo(MessageKeys.CUST_DUP_NATID);
                });
    }

    @Test
    void nationalityIdUniqueness_createPassesWhenFree() {
        when(individualRepository.existsByNationalityId("99988877766")).thenReturn(false);
        rules.checkNationalityIdIsUniqueForCreate("99988877766");
    }

    @Test
    void nationalityIdUniqueness_updateExcludesOnlyOwnRecord() {
        when(individualRepository.existsByNationalityIdAndIdNot("12345678901", 5L)).thenReturn(true);

        assertThatThrownBy(() -> rules.checkNationalityIdIsUniqueForUpdate("12345678901", 5L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessageKey())
                        .isEqualTo(MessageKeys.CUST_DUP_NATID));
    }

    @Test
    void birthDate_futureRejected() {
        assertThatThrownBy(() -> rules.checkBirthDateIsNotFuture(LocalDate.now().plusDays(1)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessageKey())
                        .isEqualTo(MessageKeys.VAL_BIRTHDATE));
    }

    @Test
    void age_under18Rejected() {
        assertThatThrownBy(() -> rules.checkCustomerIsAtLeast18(LocalDate.now().minusYears(17)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessageKey())
                        .isEqualTo(MessageKeys.VAL_AGE_MIN));
    }

    @Test
    void age_exactly18Passes() {
        rules.checkCustomerIsAtLeast18(LocalDate.now().minusYears(18));
    }
}
