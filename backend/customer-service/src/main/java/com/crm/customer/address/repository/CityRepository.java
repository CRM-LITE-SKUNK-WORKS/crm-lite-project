package com.crm.customer.address.repository;

import com.crm.customer.address.entity.City;
import com.crm.customer.lookup.LookupContract;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findByStatusIdAndDeletedDateIsNullOrderByName(Long statusId);

    Optional<City> findByIdAndStatusIdAndDeletedDateIsNull(Long id, Long statusId);

    default List<City> findActiveOrderByName() {
        return findByStatusIdAndDeletedDateIsNullOrderByName(LookupContract.STATUS_ACTIVE_ID);
    }

    default Optional<City> findActiveById(Long id) {
        return findByIdAndStatusIdAndDeletedDateIsNull(id, LookupContract.STATUS_ACTIVE_ID);
    }
}
