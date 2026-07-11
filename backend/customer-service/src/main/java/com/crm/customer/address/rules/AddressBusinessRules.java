package com.crm.customer.address.rules;

import com.crm.customer.address.entity.Address;
import com.crm.customer.address.entity.City;
import com.crm.customer.address.entity.District;
import com.crm.customer.address.repository.AddressRepository;
import com.crm.customer.address.repository.CityRepository;
import com.crm.customer.address.repository.DistrictRepository;
import com.crm.customer.common.exception.BusinessException;
import com.crm.customer.common.exception.MessageKeys;
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

    // TODO (intentional): AC-ADDR-04-04 (MSG-ADDR-IN-USE) blocks deleting an address
    // linked to an active billing account or service address. Those records live in the
    // future account/product domains; until they exist this check is a documented no-op.
    public void checkAddressIsNotInUse(Long addressId) {
        // no-op
    }
}
