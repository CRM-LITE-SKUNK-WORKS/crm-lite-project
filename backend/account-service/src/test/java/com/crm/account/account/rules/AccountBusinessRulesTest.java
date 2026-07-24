package com.crm.account.account.rules;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.crm.account.account.entity.CustomerAccount;
import com.crm.account.account.repository.ProductInvolvementRepository;
import com.crm.account.common.exception.BusinessException;
import com.crm.account.common.exception.MessageKeys;
import com.crm.account.customer.CustomerAddress;
import com.crm.account.lookup.LookupContract;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AccountBusinessRulesTest {

    @Mock
    ProductInvolvementRepository involvementRepository;

    AccountBusinessRules rules;

    @BeforeEach
    void setUp() {
        rules = new AccountBusinessRules(involvementRepository);
    }

    private static void assertBusinessException(Throwable thrown, HttpStatus status, String messageKey) {
        org.assertj.core.api.Assertions.assertThat(thrown)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .satisfies(ex -> {
                    org.assertj.core.api.Assertions.assertThat(ex.getStatus()).isEqualTo(status);
                    org.assertj.core.api.Assertions.assertThat(ex.getMessageKey()).isEqualTo(messageKey);
                });
    }

    @Test
    @DisplayName("unknown/immutable request fields -> 400 MSG-ACCT-IMMUTABLE-FIELD, naming the fields")
    void unknownFieldsRejected() {
        assertBusinessException(
                org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                        () -> rules.checkNoUnknownFields(Map.of("accountNumber", "999", "accountType", "223"))),
                HttpStatus.BAD_REQUEST, MessageKeys.ACCT_IMMUTABLE_FIELD);
    }

    @Test
    @DisplayName("empty unknown-field map passes")
    void noUnknownFieldsPasses() {
        assertThatCode(() -> rules.checkNoUnknownFields(Map.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("passive account -> 409 MSG-ACCT-NOT-ACTIVE")
    void passiveAccountNotEditable() {
        CustomerAccount account = new CustomerAccount();
        account.setAccountNumber("1261000036");
        account.setStatusId(LookupContract.STATUS_PASSIVE_ID);

        assertBusinessException(
                org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                        () -> rules.checkAccountIsActive(account)),
                HttpStatus.CONFLICT, MessageKeys.ACCT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("active account passes the editable check")
    void activeAccountEditable() {
        CustomerAccount account = new CustomerAccount();
        account.setStatusId(LookupContract.STATUS_ACTIVE_ID);
        assertThatCode(() -> rules.checkAccountIsActive(account)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("active product involvement -> 409 MSG-ACCT-HAS-PRODUCTS (AC-ACCT-04-03)")
    void activeInvolvementBlocksDelete() {
        CustomerAccount account = new CustomerAccount();
        account.setId(2L);
        account.setAccountNumber("1261000010");
        when(involvementRepository.existsByCustomerAccountIdAndStatusIdAndDeletedDateIsNull(
                2L, LookupContract.STATUS_ACTIVE_ID)).thenReturn(true);

        assertBusinessException(
                org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                        () -> rules.checkAccountHasNoActiveProducts(account)),
                HttpStatus.CONFLICT, MessageKeys.ACCT_HAS_PRODUCTS);
    }

    @Test
    @DisplayName("no active involvement -> delete guard passes")
    void noInvolvementPasses() {
        CustomerAccount account = new CustomerAccount();
        account.setId(3L);
        when(involvementRepository.existsByCustomerAccountIdAndStatusIdAndDeletedDateIsNull(
                3L, LookupContract.STATUS_ACTIVE_ID)).thenReturn(false);
        assertThatCode(() -> rules.checkAccountHasNoActiveProducts(account)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("addressId outside the customer's active list -> 400 MSG-VALIDATION-ERROR")
    void foreignAddressRejected() {
        List<CustomerAddress> addresses = List.of(new CustomerAddress(1L, true), new CustomerAddress(2L, false));
        assertBusinessException(
                org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                        () -> rules.checkAddressBelongsToCustomer(99L, 1001L, addresses)),
                HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR);
        assertThatCode(() -> rules.checkAddressBelongsToCustomer(2L, 1001L, addresses)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("K-8 primary address: returned when present, 400 when the customer has none")
    void primaryAddressResolution() {
        assertThatCode(() -> rules.requirePrimaryAddress(1001L,
                List.of(new CustomerAddress(1L, true), new CustomerAddress(2L, false)))).doesNotThrowAnyException();

        assertBusinessException(
                org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                        () -> rules.requirePrimaryAddress(1001L, List.of(new CustomerAddress(2L, false)))),
                HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("thrown BusinessExceptions carry no internal ids in their messages")
    void messagesExposeNoInternalIds() {
        CustomerAccount account = new CustomerAccount();
        account.setId(42L);
        account.setAccountNumber("1261000010");
        when(involvementRepository.existsByCustomerAccountIdAndStatusIdAndDeletedDateIsNull(
                42L, LookupContract.STATUS_ACTIVE_ID)).thenReturn(true);

        assertThatThrownBy(() -> rules.checkAccountHasNoActiveProducts(account))
                .hasMessageContaining("1261000010")
                .hasMessageNotContaining("42");
    }
}
