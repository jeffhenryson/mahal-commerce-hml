package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerCreditLimitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerCreditLimitJpaRepository extends JpaRepository<CustomerCreditLimitEntity, Long> {
}
