package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerCreditLimitEntity;
import com.cernecommerce.core.domain.model.recebivel.CreditLimits;
import com.cernecommerce.core.domain.model.recebivel.OnAccountChannel;
import com.cernecommerce.core.ports.out.recebivel.CustomerCreditLimitRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    public CreditLimits findByCustomerId(Long customerId) {
        return toLimits(jpaRepository.findByCustomerId(customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, CreditLimits> findByCustomerIds(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return Map.of();
        }
        return jpaRepository.findByCustomerIdIn(customerIds).stream()
                .collect(Collectors.groupingBy(CustomerCreditLimitEntity::getCustomerId,
                        Collectors.collectingAndThen(Collectors.toList(),
                                CustomerCreditLimitRepositoryImpl::toLimits)));
    }

    @Override
    public void save(Long customerId, OnAccountChannel channel, BigDecimal creditLimit, String updatedBy,
            Instant updatedAt) {
        jpaRepository.save(new CustomerCreditLimitEntity(customerId, column(channel), creditLimit, updatedBy,
                updatedAt));
    }

    @Override
    public void delete(Long customerId, OnAccountChannel channel) {
        CustomerCreditLimitEntity.Key key = new CustomerCreditLimitEntity.Key(customerId, column(channel));
        if (jpaRepository.existsById(key)) {
            jpaRepository.deleteById(key);
        }
    }

    /** Na porta, canal nulo é o teto total; na tabela, {@code 'TOTAL'}. */
    private static String column(OnAccountChannel channel) {
        return channel == null ? CustomerCreditLimitEntity.TOTAL : channel.name();
    }

    private static CreditLimits toLimits(List<CustomerCreditLimitEntity> rows) {
        BigDecimal total = null;
        BigDecimal balcao = null;
        BigDecimal mesa = null;
        for (CustomerCreditLimitEntity row : rows) {
            switch (row.getChannel()) {
                case "BALCAO" -> balcao = row.getCreditLimit();
                case "MESA" -> mesa = row.getCreditLimit();
                default -> total = row.getCreditLimit();
            }
        }
        return new CreditLimits(total, balcao, mesa);
    }
}
