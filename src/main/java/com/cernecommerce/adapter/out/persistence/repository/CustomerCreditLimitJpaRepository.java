package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerCreditLimitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CustomerCreditLimitJpaRepository
        extends JpaRepository<CustomerCreditLimitEntity, CustomerCreditLimitEntity.Key> {

    List<CustomerCreditLimitEntity> findByCustomerId(Long customerId);

    List<CustomerCreditLimitEntity> findByCustomerIdIn(Collection<Long> customerIds);
}
