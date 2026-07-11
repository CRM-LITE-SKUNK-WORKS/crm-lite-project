package com.crm.customer.customer.entity;

import com.crm.customer.common.entity.StatusAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Local ROLE lookup (workbook: id, role_name). Seeded: 1 = "Customer". */
@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
public class Role extends StatusAwareEntity {

    @Id
    private Long id;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;
}
