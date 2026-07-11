package com.crm.customer.address.repository;

import com.crm.customer.address.entity.District;
import com.crm.customer.lookup.LookupContract;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistrictRepository extends JpaRepository<District, Long> {

    List<District> findByCityIdAndStatusIdAndDeletedDateIsNullOrderByName(Long cityId, Long statusId);

    Optional<District> findByIdAndStatusIdAndDeletedDateIsNull(Long id, Long statusId);

    default List<District> findActiveByCityOrderByName(Long cityId) {
        return findByCityIdAndStatusIdAndDeletedDateIsNullOrderByName(cityId, LookupContract.STATUS_ACTIVE_ID);
    }

    default Optional<District> findActiveById(Long id) {
        return findByIdAndStatusIdAndDeletedDateIsNull(id, LookupContract.STATUS_ACTIVE_ID);
    }
}
