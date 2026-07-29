package com.crm.customer.customer.service.impl;

import com.crm.customer.address.dto.AddressRequest;
import com.crm.customer.address.entity.Address;
import com.crm.customer.address.entity.City;
import com.crm.customer.address.entity.District;
import com.crm.customer.address.repository.AddressRepository;
import com.crm.customer.address.rules.AddressBusinessRules;
import com.crm.customer.common.CurrentActorProvider;
import com.crm.customer.common.exception.BusinessException;
import com.crm.customer.common.exception.MessageKeys;
import com.crm.customer.contact.entity.ContactMedium;
import com.crm.customer.contact.repository.ContactMediumRepository;
import com.crm.customer.customer.dto.request.CustomerCreateRequest;
import com.crm.customer.customer.dto.request.CustomerUpdateRequest;
import com.crm.customer.customer.dto.request.DemographicRequest;
import com.crm.customer.customer.dto.response.CustomerDetailResponse;
import com.crm.customer.customer.dto.response.NationalityIdAvailabilityResponse;
import com.crm.customer.customer.entity.Customer;
import com.crm.customer.customer.entity.Individual;
import com.crm.customer.customer.entity.Party;
import com.crm.customer.customer.entity.PartyRole;
import com.crm.customer.customer.entity.Role;
import com.crm.customer.customer.mapper.CustomerMapper;
import com.crm.customer.customer.repository.CustomerRepository;
import com.crm.customer.customer.repository.CustomerSpecifications;
import com.crm.customer.customer.repository.IndividualRepository;
import com.crm.customer.customer.repository.PartyRepository;
import com.crm.customer.customer.repository.PartyRoleRepository;
import com.crm.customer.customer.repository.RoleRepository;
import com.crm.customer.customer.rules.CustomerBusinessRules;
import com.crm.customer.customer.service.CustomerService;
import com.crm.customer.lookup.LookupCatalogService;
import com.crm.customer.lookup.LookupContract;
import com.crm.customer.mernis.MernisClient;
import com.crm.customer.mernis.MernisRejectedException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    private static final String CUSTOMER_ROLE_NAME = "Customer";

    private final CustomerRepository customerRepository;
    private final PartyRepository partyRepository;
    private final IndividualRepository individualRepository;
    private final PartyRoleRepository partyRoleRepository;
    private final RoleRepository roleRepository;
    private final AddressRepository addressRepository;
    private final ContactMediumRepository contactMediumRepository;
    private final CustomerBusinessRules businessRules;
    private final AddressBusinessRules addressBusinessRules;
    private final CustomerMapper customerMapper;
    private final LookupCatalogService lookupCatalogService;
    private final MernisClient mernisClient;
    // Audit attribution (ADR-004): Keycloak sub of the authenticated request.
    private final CurrentActorProvider currentActor;

    /**
     * ADR-005: no criteria = paginated browse of all active customers
     * (AC-CUST-01-00); the former at-least-one-criterion rule was removed by the
     * analyst decision in FR/AC v8 (2026-07-16).
     */
    @Override
    public Page<CustomerDetailResponse> search(String firstName, String lastName, String nationalityId,
                                               Long customerNumber, String gsmNumber,
                                               String accountNumber, String orderNumber, Pageable pageable) {
        businessRules.checkNoUnsupportedCrossServiceSearchCriterion(accountNumber, orderNumber);

        Specification<Customer> spec =
                CustomerSpecifications.search(firstName, lastName, nationalityId, customerNumber, gsmNumber);
        return customerRepository.findAll(spec, pageable).map(customerMapper::toDetailResponse);
    }

    @Override
    public CustomerDetailResponse getByCustomerNumber(Long customerNumber) {
        Customer customer = businessRules.checkCustomerExistsAndActive(customerNumber);
        return customerMapper.toDetailResponse(customer);
    }

    /**
     * ADR-005 §Addendum. Not a search: it asks the ADR-003 rule itself (through the
     * SAME business-rules method the create path uses), so it sees the soft-deleted
     * holders that {@link #search} filters out by design. Advisory — the create is
     * still the authority and can answer 409 after a `true` here.
     */
    @Override
    public NationalityIdAvailabilityResponse checkNationalityIdAvailability(String nationalityId) {
        return new NationalityIdAvailabilityResponse(
                businessRules.isNationalityIdAvailableForCreate(nationalityId));
    }

    /**
     * FR-CUST-03 / AC-CUST-03-21, atomic: validations, shared-catalog resolution
     * (ADR-002) and MERNIS verification (KR-10) all happen BEFORE the aggregate is
     * persisted inside this single local transaction. Any failure leaves nothing behind.
     */
    @Override
    @Transactional
    public CustomerDetailResponse create(CustomerCreateRequest request) {
        DemographicRequest demographic = request.getDemographic();

        businessRules.checkBirthDateIsNotFuture(demographic.getBirthDate());
        businessRules.checkCustomerIsAtLeast18(demographic.getBirthDate());
        businessRules.checkNationalityIdIsUniqueForCreate(demographic.getNationalityId());

        // Address block: exactly one primary after normalization + city/district checks.
        List<AddressRequest> addresses = normalizePrimary(request.getAddresses());

        // Shared catalog resolution — fail closed before touching the database.
        long activeStatusId = lookupCatalogService.resolveStatusId("status",
                LookupContract.STATUS_ACTIVE, LookupContract.STATUS_DOMAIN_GENERAL);
        long partyTypeId = lookupCatalogService.resolveTypeId("partyType",
                LookupContract.TYPE_INDIVIDUAL, LookupContract.TYPE_DOMAIN_PARTY_TYPE);
        long genderId = lookupCatalogService.resolveTypeId("gender",
                demographic.getGender().lookupCode(), LookupContract.TYPE_DOMAIN_GENDER);

        // KR-10: verify through fake MERNIS before persistence; rejected or unavailable
        // both mean no customer is created.
        if (!mernisClient.verify(demographic.getNationalityId(), demographic.getFirstName(),
                demographic.getLastName(), demographic.getBirthDate())) {
            throw new MernisRejectedException("Nationality ID could not be verified by MERNIS");
        }

        Party party = new Party();
        party.setPartyTypeId(partyTypeId);
        party.setStatusId(activeStatusId);
        party.markCreated(currentActor.get());
        party = partyRepository.save(party);

        Individual individual = new Individual();
        individual.setParty(party);
        applyDemographics(individual, demographic, resolveGenderIdForPersist(genderId));
        individual.setStatusId(activeStatusId);
        individual.markCreated(currentActor.get());
        individual = individualRepository.save(individual);

        Role customerRole = roleRepository.findByRoleNameAndDeletedDateIsNull(CUSTOMER_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Seed data missing: role '" + CUSTOMER_ROLE_NAME + "' not found"));

        PartyRole partyRole = new PartyRole();
        partyRole.setParty(party);
        partyRole.setRole(customerRole);
        partyRole.setStatusId(activeStatusId);
        partyRole.markCreated(currentActor.get());
        partyRole = partyRoleRepository.save(partyRole);

        Customer customer = new Customer();
        customer.setCustomerNumber(customerRepository.nextCustomerNumber());
        customer.setPartyRole(partyRole);
        customer.setStatusId(activeStatusId);
        customer.markCreated(currentActor.get());
        customer = customerRepository.save(customer);

        for (AddressRequest addressRequest : addresses) {
            Address address = buildAddress(party, addressRequest, activeStatusId);
            addressRepository.save(address);
        }

        ContactMedium contact = new ContactMedium();
        contact.setParty(party);
        applyContact(contact, request.getContactMedium());
        contact.setStatusId(activeStatusId);
        contact.markCreated(currentActor.get());
        contactMediumRepository.save(contact);

        return customerMapper.toDetailResponse(customer, individual, customerRole);
    }

    @Override
    @Transactional
    public CustomerDetailResponse update(Long customerNumber, CustomerUpdateRequest request) {
        Customer customer = businessRules.checkCustomerExistsAndActive(customerNumber);

        businessRules.checkBirthDateIsNotFuture(request.getBirthDate());
        businessRules.checkCustomerIsAtLeast18(request.getBirthDate());

        PartyRole partyRole = customer.getPartyRole();
        Individual individual = partyRole.getParty().getIndividual();
        businessRules.checkNationalityIdIsUniqueForUpdate(request.getNationalityId(), individual.getId());

        long genderId = lookupCatalogService.resolveTypeId("gender",
                request.getGender().lookupCode(), LookupContract.TYPE_DOMAIN_GENDER);

        applyDemographics(individual, request, genderId);
        individual.markUpdated(currentActor.get());
        customer.markUpdated(currentActor.get());

        return customerMapper.toDetailResponse(customer, individual, partyRole.getRole());
    }

    /**
     * FR-CUST-05 / AC-CUST-05-04: soft-deletes the locally owned aggregate
     * (CUST, PARTY_ROLE, PARTY, IND, ADDR, CNTC_MEDIUM) in one transaction.
     * Billing-account passivation is cross-service future work (documented TODO in
     * the FR traceability matrix) and is NOT claimed to happen here.
     */
    @Override
    @Transactional
    public void delete(Long customerNumber) {
        Customer customer = businessRules.checkCustomerExistsAndActive(customerNumber);
        businessRules.checkCustomerHasNoActiveProducts(customerNumber);

        long passiveStatusId = lookupCatalogService.resolveStatusId("status",
                LookupContract.STATUS_PASSIVE, LookupContract.STATUS_DOMAIN_GENERAL);

        PartyRole partyRole = customer.getPartyRole();
        Party party = partyRole.getParty();
        Individual individual = party.getIndividual();

        customer.passivate(passiveStatusId, currentActor.get());
        partyRole.passivate(passiveStatusId, currentActor.get());
        party.passivate(passiveStatusId, currentActor.get());
        individual.passivate(passiveStatusId, currentActor.get());

        for (Address address : addressRepository.findByPartyIdAndDeletedDateIsNullOrderById(party.getId())) {
            address.passivate(passiveStatusId, currentActor.get());
        }
        contactMediumRepository.findByPartyIdAndDeletedDateIsNull(party.getId())
                .ifPresent(contact -> contact.passivate(passiveStatusId, currentActor.get()));
    }

    /** AC-ADDR-02-04 + create-wizard rule: exactly one primary after normalization. */
    private List<AddressRequest> normalizePrimary(List<AddressRequest> addresses) {
        List<AddressRequest> normalized = new ArrayList<>(addresses);
        long primaryCount = normalized.stream().filter(AddressRequest::isPrimaryRequested).count();
        if (primaryCount > 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR,
                    "Exactly one address may be marked as primary");
        }
        if (primaryCount == 0) {
            normalized.get(0).setPrimary(true);
        }
        return normalized;
    }

    private Address buildAddress(Party party, AddressRequest request, long activeStatusId) {
        City city = addressBusinessRules.checkCityExistsAndActive(request.getCityId());
        District district = addressBusinessRules.checkDistrictBelongsToCity(request.getDistrictId(), city);

        Address address = new Address();
        address.setParty(party);
        address.setCity(city);
        address.setDistrict(district);
        address.setStreet(request.getStreet());
        address.setHouseFlatNo(request.getHouseFlatNumber());
        address.setAddressDescription(request.getAddressDescription());
        address.setPrimary(request.isPrimaryRequested());
        address.setStatusId(activeStatusId);
        address.markCreated(currentActor.get());
        return address;
    }

    private void applyDemographics(Individual individual, DemographicRequest source, long genderId) {
        individual.setFirstName(source.getFirstName());
        individual.setMiddleName(source.getMiddleName());
        individual.setLastName(source.getLastName());
        individual.setFatherName(source.getFatherName());
        individual.setMotherName(source.getMotherName());
        individual.setBirthDate(source.getBirthDate());
        individual.setGenderId(genderId);
        individual.setNationalityId(source.getNationalityId());
    }

    private void applyContact(ContactMedium contact, com.crm.customer.contact.dto.ContactMediumRequest source) {
        contact.setEmail(source.getEmail());
        contact.setHomePhone(source.getHomePhone());
        contact.setMobilePhone(source.getMobilePhone());
        contact.setFax(source.getFax());
    }

    private long resolveGenderIdForPersist(long genderId) {
        return genderId;
    }
}
