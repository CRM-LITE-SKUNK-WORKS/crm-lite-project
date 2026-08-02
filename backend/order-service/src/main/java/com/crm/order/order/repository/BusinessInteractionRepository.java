package com.crm.order.order.repository;

import com.crm.order.order.entity.BusinessInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessInteractionRepository extends JpaRepository<BusinessInteraction, Long> {
}
