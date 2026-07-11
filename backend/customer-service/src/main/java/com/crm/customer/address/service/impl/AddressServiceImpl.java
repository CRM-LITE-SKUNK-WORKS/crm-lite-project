package com.crm.customer.address.service.impl;

import com.crm.customer.address.dto.AddressRequest;
import com.crm.customer.address.dto.AddressResponse;
import com.crm.customer.address.entity.Address;
import com.crm.customer.address.entity.City;
import com.crm.customer.address.entity.District;
import com.crm.customer.address.repository.AddressRepository;
import com.crm.customer.address.rules.AddressBusinessRules;
import com.crm.customer.address.service.AddressService;
import com.crm.customer.common.AuditActor;
import com.crm.customer.customer.entity.Customer;
import com.crm.customer.customer.entity.Party;
import com.crm.customer.customer.rules.CustomerBusinessRules;
import com.crm.customer.lookup.LookupCatalogService;
import com.crm.customer.lookup.LookupContract;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final AddressBusinessRules addressBusinessRules;
    private final CustomerBusinessRules customerBusinessRules;
    private final LookupCatalogService lookupCatalogService;

    @Override
    public List<AddressResponse> list(Long customerNumber) {
        Party party = resolveParty(customerNumber);
        return addressRepository.findByPartyIdAndDeletedDateIsNullOrderById(party.getId())
                .stream().map(AddressResponse::from).toList();
    }

    /** FR-ADDR-02: save active; the customer's first address becomes primary automatically. */
    @Override
    @Transactional
    public AddressResponse add(Long customerNumber, AddressRequest request) {
        Party party = resolveParty(customerNumber);
        City city = addressBusinessRules.checkCityExistsAndActive(request.getCityId());
        District district = addressBusinessRules.checkDistrictBelongsToCity(request.getDistrictId(), city);

        long activeStatusId = lookupCatalogService.resolveStatusId("status",
                LookupContract.STATUS_ACTIVE, LookupContract.STATUS_DOMAIN_GENERAL);

        boolean firstAddress = addressRepository.countByPartyIdAndDeletedDateIsNull(party.getId()) == 0;
        boolean primary = firstAddress || request.isPrimaryRequested();
        if (primary && !firstAddress) {
            demoteCurrentPrimary(party.getId());
        }

        Address address = new Address();
        address.setParty(party);
        address.setCity(city);
        address.setDistrict(district);
        address.setStreet(request.getStreet());
        address.setHouseFlatNo(request.getHouseFlatNumber());
        address.setAddressDescription(request.getAddressDescription());
        address.setPrimary(primary);
        address.setStatusId(activeStatusId);
        address.markCreated(AuditActor.SYSTEM);
        return AddressResponse.from(addressRepository.save(address));
    }

    /** FR-ADDR-03: field update only; primary designation changes go through setPrimary. */
    @Override
    @Transactional
    public AddressResponse update(Long customerNumber, Long addressId, AddressRequest request) {
        Party party = resolveParty(customerNumber);
        Address address = addressBusinessRules.checkAddressExistsAndActive(addressId, party.getId());
        City city = addressBusinessRules.checkCityExistsAndActive(request.getCityId());
        District district = addressBusinessRules.checkDistrictBelongsToCity(request.getDistrictId(), city);

        address.setCity(city);
        address.setDistrict(district);
        address.setStreet(request.getStreet());
        address.setHouseFlatNo(request.getHouseFlatNumber());
        address.setAddressDescription(request.getAddressDescription());
        address.markUpdated(AuditActor.SYSTEM);
        return AddressResponse.from(address);
    }

    /** FR-ADDR-04: soft delete with last-address / primary / in-use guards. */
    @Override
    @Transactional
    public void delete(Long customerNumber, Long addressId) {
        Party party = resolveParty(customerNumber);
        Address address = addressBusinessRules.checkAddressExistsAndActive(addressId, party.getId());
        addressBusinessRules.checkAddressIsNotTheLastOne(party.getId());
        addressBusinessRules.checkAddressIsNotPrimary(address);
        addressBusinessRules.checkAddressIsNotInUse(addressId);

        long passiveStatusId = lookupCatalogService.resolveStatusId("status",
                LookupContract.STATUS_PASSIVE, LookupContract.STATUS_DOMAIN_GENERAL);
        address.passivate(passiveStatusId, AuditActor.SYSTEM);
    }

    /** FR-ADDR-05: switching primary demotes the previous primary in the same transaction. */
    @Override
    @Transactional
    public AddressResponse setPrimary(Long customerNumber, Long addressId) {
        Party party = resolveParty(customerNumber);
        Address address = addressBusinessRules.checkAddressExistsAndActive(addressId, party.getId());
        if (!address.isPrimary()) {
            demoteCurrentPrimary(party.getId());
            address.setPrimary(true);
            address.markUpdated(AuditActor.SYSTEM);
        }
        return AddressResponse.from(address);
    }

    private void demoteCurrentPrimary(Long partyId) {
        addressRepository.findByPartyIdAndPrimaryTrueAndDeletedDateIsNull(partyId).ifPresent(current -> {
            current.setPrimary(false);
            current.markUpdated(AuditActor.SYSTEM);
            // Flush the demotion before the new primary is written so the
            // ux_addr_active_primary partial unique index never sees two primaries.
            addressRepository.saveAndFlush(current);
        });
    }

    private Party resolveParty(Long customerNumber) {
        Customer customer = customerBusinessRules.checkCustomerExistsAndActive(customerNumber);
        return customer.getPartyRole().getParty();
    }
}
