package com.crm.customer.address.rules;

import com.crm.customer.account.AccountSummary;
import com.crm.customer.address.entity.Address;
import com.crm.customer.address.entity.City;
import com.crm.customer.address.entity.District;
import com.crm.customer.address.repository.AddressRepository;
import com.crm.customer.address.repository.CityRepository;
import com.crm.customer.address.repository.DistrictRepository;
import com.crm.customer.common.exception.BusinessException;
import com.crm.customer.common.exception.MessageKeys;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AddressBusinessRules {

    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;
    private final AddressRepository addressRepository;

    public City checkCityExistsAndActive(Long cityId) {
        return cityRepository.findActiveById(cityId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR,
                        "Unknown or inactive city: " + cityId));
    }

    /** FR-ADDR-02: District is cascading — it must belong to the selected City. */
    public District checkDistrictBelongsToCity(Long districtId, City city) {
        District district = districtRepository.findActiveById(districtId)
                .orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR,
                        "Unknown or inactive district: " + districtId));
        if (!district.getCity().getId().equals(city.getId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, MessageKeys.VALIDATION_ERROR,
                    "District " + districtId + " does not belong to city " + city.getId());
        }
        return district;
    }

    public Address checkAddressExistsAndActive(Long addressId, Long partyId) {
        return addressRepository.findByIdAndPartyIdAndDeletedDateIsNull(addressId, partyId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MessageKeys.CUST_NOT_FOUND,
                        "Address not found: " + addressId));
    }

    /** AC-ADDR-04-01: the single remaining address cannot be deleted. */
    public void checkAddressIsNotTheLastOne(Long partyId) {
        if (addressRepository.countByPartyIdAndDeletedDateIsNull(partyId) <= 1) {
            throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.ADDR_LAST_DELETE,
                    "The customer's only address cannot be deleted");
        }
    }

    /** AC-ADDR-04-02: the primary address cannot be deleted while others exist. */
    public void checkAddressIsNotPrimary(Address address) {
        if (address.isPrimary()) {
            throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.ADDR_PRIMARY_DELETE,
                    "The primary address cannot be deleted; set another address as primary first");
        }
    }

    /**
     * AC-ADDR-04-04 / BUG-API-ADDR-04-03: the Billing Account half of the in-use guard —
     * an Active account still billing to this address blocks the delete. The caller
     * (AddressServiceImpl) fetches {@code accounts} via AccountServiceClient; this class
     * stays free of the HTTP/account-service dependency, matching the existing
     * business-rule vs. service-orchestration split.
     *
     * <p>The service-address / product-service half of AC-ADDR-04-04 is not covered here —
     * see PROJECTBRAIN.md §9.3.
     */
    public void checkAddressIsNotInUse(Long addressId, List<AccountSummary> accounts) {
        boolean inUse = accounts.stream()
                .anyMatch(account -> account.isActive() && addressId.equals(account.billingAddressId()));
        if (inUse) {
            throw new BusinessException(HttpStatus.CONFLICT, MessageKeys.ADDR_IN_USE,
                    "Address is linked to an active billing account: " + addressId);
        }
    }
}
