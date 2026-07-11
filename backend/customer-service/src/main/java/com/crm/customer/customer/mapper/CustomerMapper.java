package com.crm.customer.customer.mapper;

import com.crm.customer.customer.dto.Gender;
import com.crm.customer.customer.dto.response.CustomerDetailResponse;
import com.crm.customer.customer.dto.response.CustomerSearchResponse;
import com.crm.customer.customer.entity.Customer;
import com.crm.customer.customer.entity.Individual;
import com.crm.customer.customer.entity.Role;
import com.crm.customer.lookup.LookupContract;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    /**
     * Used for rows freshly loaded from the DB (search/get), where the full
     * customer -> partyRole -> party -> individual/role graph is populated by the query.
     */
    public CustomerSearchResponse toSearchResponse(Customer customer) {
        Individual individual = customer.getPartyRole().getParty().getIndividual();
        Role role = customer.getPartyRole().getRole();
        return new CustomerSearchResponse(
                customer.getCustomerNumber(),
                individual.getFirstName(),
                individual.getMiddleName(),
                individual.getLastName(),
                role.getRoleName(),
                individual.getNationalityId()
        );
    }

    public CustomerDetailResponse toDetailResponse(Customer customer) {
        Individual individual = customer.getPartyRole().getParty().getIndividual();
        Role role = customer.getPartyRole().getRole();
        return toDetailResponse(customer, individual, role);
    }

    /**
     * Used right after create/update, where the Party -> Individual inverse association
     * hasn't been refreshed from the DB (JPA does not sync the mappedBy side of a
     * OneToOne in memory) — callers pass the objects they already hold.
     */
    public CustomerDetailResponse toDetailResponse(Customer customer, Individual individual, Role role) {
        return new CustomerDetailResponse(
                customer.getCustomerNumber(),
                individual.getFirstName(),
                individual.getMiddleName(),
                individual.getLastName(),
                individual.getFatherName(),
                individual.getMotherName(),
                individual.getBirthDate(),
                Gender.fromTypeId(individual.getGenderId()).getApiValue(),
                individual.getNationalityId(),
                role.getRoleName(),
                statusCode(customer.getStatusId())
        );
    }

    /**
     * Status short code from the stored external reference. Local constant mapping is
     * safe because the IDs are contract-immutable and verified against the central
     * catalog on every write (ADR-002).
     */
    private String statusCode(Long statusId) {
        if (statusId == null) {
            return null;
        }
        if (statusId == LookupContract.STATUS_ACTIVE_ID) {
            return LookupContract.STATUS_ACTIVE;
        }
        if (statusId == LookupContract.STATUS_PASSIVE_ID) {
            return LookupContract.STATUS_PASSIVE;
        }
        return String.valueOf(statusId);
    }
}
