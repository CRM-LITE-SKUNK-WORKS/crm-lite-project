package com.crm.customer.contact.repository;

import com.crm.customer.contact.entity.ContactMedium;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMediumRepository extends JpaRepository<ContactMedium, Long> {

    Optional<ContactMedium> findByPartyIdAndDeletedDateIsNull(Long partyId);
}
