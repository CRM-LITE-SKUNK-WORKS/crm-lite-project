package com.crm.customer.address.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crm.customer.address.entity.Address;
import com.crm.customer.address.entity.City;
import com.crm.customer.address.entity.District;
import com.crm.customer.address.repository.AddressRepository;
import com.crm.customer.address.repository.CityRepository;
import com.crm.customer.address.repository.DistrictRepository;
import com.crm.customer.common.exception.BusinessException;
import com.crm.customer.common.exception.MessageKeys;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AddressBusinessRulesTest {

    private CityRepository cityRepository;
    private DistrictRepository districtRepository;
    private AddressRepository addressRepository;
    private AddressBusinessRules rules;

    @BeforeEach
    void setUp() {
        cityRepository = mock(CityRepository.class);
        districtRepository = mock(DistrictRepository.class);
        addressRepository = mock(AddressRepository.class);
        rules = new AddressBusinessRules(cityRepository, districtRepository, addressRepository);
    }

    private City city(long id) {
        City city = new City();
        city.setId(id);
        return city;
    }

    private District district(long id, City city) {
        District district = new District();
        district.setId(id);
        district.setCity(city);
        return district;
    }

    @Test
    void unknownCity_rejected() {
        when(cityRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rules.checkCityExistsAndActive(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void districtOfAnotherCity_rejected() {
        // FR-ADDR-02 cascading rule: Kadikoy (city 1) is not selectable under Ankara (city 2).
        City istanbul = city(1L);
        City ankara = city(2L);
        when(districtRepository.findActiveById(1L)).thenReturn(Optional.of(district(1L, istanbul)));

        assertThatThrownBy(() -> rules.checkDistrictBelongsToCity(1L, ankara))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void districtOfSelectedCity_returned() {
        City istanbul = city(1L);
        District kadikoy = district(1L, istanbul);
        when(districtRepository.findActiveById(1L)).thenReturn(Optional.of(kadikoy));

        assertThat(rules.checkDistrictBelongsToCity(1L, istanbul)).isSameAs(kadikoy);
    }

    @Test
    void lastRemainingAddress_cannotBeDeleted() {
        // AC-ADDR-04-01
        when(addressRepository.countByPartyIdAndDeletedDateIsNull(1L)).thenReturn(1L);

        assertThatThrownBy(() -> rules.checkAddressIsNotTheLastOne(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(be.getMessageKey()).isEqualTo(MessageKeys.ADDR_LAST_DELETE);
                });
    }

    @Test
    void secondAddressExists_deleteAllowedByCountGuard() {
        when(addressRepository.countByPartyIdAndDeletedDateIsNull(1L)).thenReturn(2L);
        rules.checkAddressIsNotTheLastOne(1L);
    }

    @Test
    void primaryAddress_cannotBeDeleted() {
        // AC-ADDR-04-02
        Address primary = new Address();
        primary.setPrimary(true);

        assertThatThrownBy(() -> rules.checkAddressIsNotPrimary(primary))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessageKey())
                        .isEqualTo(MessageKeys.ADDR_PRIMARY_DELETE));
    }

    @Test
    void nonPrimaryAddress_passesPrimaryGuard() {
        Address secondary = new Address();
        secondary.setPrimary(false);
        rules.checkAddressIsNotPrimary(secondary);
    }
}
