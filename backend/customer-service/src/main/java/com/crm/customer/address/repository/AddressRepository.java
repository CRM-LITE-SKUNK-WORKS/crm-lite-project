package com.crm.customer.address.repository;

import com.crm.customer.address.entity.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<Address, Long> {

    // "Active address" = the soft-delete invariant's local filter. deleted_date IS NULL
    // is sufficient here because status and deleted_date are always flipped together.
    List<Address> findByPartyIdAndDeletedDateIsNullOrderById(Long partyId);

    Optional<Address> findByIdAndPartyIdAndDeletedDateIsNull(Long id, Long partyId);

    long countByPartyIdAndDeletedDateIsNull(Long partyId);

    Optional<Address> findByPartyIdAndPrimaryTrueAndDeletedDateIsNull(Long partyId);
}
