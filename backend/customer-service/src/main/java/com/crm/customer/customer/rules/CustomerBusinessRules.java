package com.crm.customer.customer.rules;

import com.crm.customer.common.exception.BusinessException;
import com.crm.customer.common.exception.MessageKeys;
import com.crm.customer.customer.entity.Customer;
import com.crm.customer.customer.repository.CustomerRepository;
import com.crm.customer.customer.repository.IndividualRepository;
import java.time.LocalDate;
import java.time.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerBusinessRules {

    private static final int MIN_AGE = 18;

    private final CustomerRepository customerRepository;
    private final IndividualRepository individualRepository;

    // NOTE: the former checkAtLeastOneSearchCriterionExists rule was REMOVED by the
    // FR/AC v8 analyst decision of 2026-07-16 (AC-CUST-01-00 + ADR-005): a request
    // with no criteria is now the valid "browse all active customers" mode.

    // NOTE: checkNoUnsupportedCrossServiceSearchCriterion (the 501 gate on
    // accountNumber/orderNumber) was REMOVED once both owning domains shipped —
    // account-service 2026-07-23 (ADR-013) and order-service 2026-08-02 (ADR-016). KR-02
    // is now resolved for real in CustomerServiceImpl.search through those services'
    // own APIs; MSG-FEATURE-NOT-IMPLEMENTED is no longer produced by this service.

    /** Resolves the public business identifier to an ACTIVE customer or 404s. */
    public Customer checkCustomerExistsAndActive(Long customerNumber) {
        Customer customer = customerRepository.findByCustomerNumber(customerNumber)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.CUST_NOT_FOUND,
                        "Customer not found: " + customerNumber));
        if (!customer.isActive()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.CUST_NOT_FOUND,
                    "Customer not found: " + customerNumber);
        }
        return customer;
    }

    /**
     * ADR-003 uniqueness as a QUERY: is this Nationality ID still free for a create?
     * Same rows and same semantics as {@link #checkNationalityIdIsUniqueForCreate} —
     * ALL ind rows, soft-deleted and passive included — it just answers instead of
     * throwing. Deliberately the single source of the rule so the pre-check exposed
     * by the API can never drift away from the create-time authority.
     *
     * Advisory by nature: a create between the probe and the POST still loses to the
     * 409 (and, in a race, to the DB UNIQUE constraint). Callers must not treat a
     * `true` here as a reservation.
     */
    public boolean isNationalityIdAvailableForCreate(String nationalityId) {
        return !individualRepository.existsByNationalityId(nationalityId);
    }

    // ADR-003: global, permanent uniqueness — checks ALL ind rows including
    // soft-deleted/passive ones. The DB UNIQUE constraint is the racing-insert backstop.
    public void checkNationalityIdIsUniqueForCreate(String nationalityId) {
        if (!isNationalityIdAvailableForCreate(nationalityId)) {
            throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.CUST_DUP_NATID,
                    "A customer already exists with this Nationality ID");
        }
    }

    public void checkNationalityIdIsUniqueForUpdate(String nationalityId, Long currentIndividualId) {
        if (individualRepository.existsByNationalityIdAndIdNot(nationalityId, currentIndividualId)) {
            throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.CUST_DUP_NATID,
                    "A customer already exists with this Nationality ID");
        }
    }

    public void checkBirthDateIsNotFuture(LocalDate birthDate) {
        if (birthDate.isAfter(LocalDate.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VAL_BIRTHDATE,
                    "Birth date cannot be in the future");
        }
    }

    public void checkCustomerIsAtLeast18(LocalDate birthDate) {
        if (Period.between(birthDate, LocalDate.now()).getYears() < MIN_AGE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VAL_AGE_MIN,
                    "Customer must be at least " + MIN_AGE + " years old");
        }
    }

    // TODO (intentional): AC-CUST-05-03 requires blocking deletion while the customer
    // has active products. Products live in the future product/account domains; this
    // check is a documented no-op until they exist. It must NOT be removed silently.
    public void checkCustomerHasNoActiveProducts(Long customerNumber) {
        // no-op
    }
}
