package com.crm.customer.customer.repository;

import com.crm.customer.customer.entity.Party;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartyRepository extends JpaRepository<Party, Long> {
}
