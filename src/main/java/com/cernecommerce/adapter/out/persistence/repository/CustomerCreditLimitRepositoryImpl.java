package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerCreditLimitEntity;
import com.cernecommerce.core.ports.out.recebivel.CustomerCreditLimitRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Transactional
public class CustomerCreditLimitRepositoryImpl implements CustomerCreditLimitRepository {

    private final CustomerCreditLimitJpaRepository jpaRepository;

    public CustomerCreditLimitRepositoryImpl(CustomerCreditLimitJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> findByCustomerId(Long customerId) {
        return jpaRepository.findById(customerId).map(CustomerCreditLimitEntity::getCreditLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> findByCustomerIds(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(CustomerCreditLimitEntity::getCustomerId,
                        CustomerCreditLimitEntity::getCreditLimit));
    }

    @Override
    public void save(Long customerId, BigDecimal creditLimit, String updatedBy, Instant updatedAt) {
        jpaRepository.save(new CustomerCreditLimitEntity(customerId, creditLimit, updatedBy, updatedAt));
    }

    @Override
    public void delete(Long customerId) {
        if (jpaRepository.existsById(customerId)) {
            jpaRepository.deleteById(customerId);
        }
    }
}
