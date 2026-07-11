package com.crm.customer.customer.repository;

import com.crm.customer.customer.entity.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleNameAndDeletedDateIsNull(String roleName);
}
