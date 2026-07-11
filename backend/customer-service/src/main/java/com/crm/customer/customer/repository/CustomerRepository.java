package com.crm.customer.customer.repository;

import com.crm.customer.customer.entity.Customer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByCustomerNumber(Long customerNumber);

    /** Concurrency-safe business customer number (starts at 1001, never reused). */
    @Query(value = "SELECT nextval('cust_customer_number_seq')", nativeQuery = true)
    long nextCustomerNumber();
}
