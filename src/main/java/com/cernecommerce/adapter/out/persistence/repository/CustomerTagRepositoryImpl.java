package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerTagEntity;
import com.cernecommerce.adapter.out.persistence.entity.TagEntity;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
public class CustomerTagRepositoryImpl implements CustomerTagRepository {

    private final CustomerTagJpaRepository customerTagJpaRepository;

    public CustomerTagRepositoryImpl(CustomerTagJpaRepository customerTagJpaRepository) {
        this.customerTagJpaRepository = customerTagJpaRepository;
    }

    @Override
    public void associate(Long customerId, Long tagId) {
        if (!customerTagJpaRepository.existsByCustomerIdAndTagId(customerId, tagId)) {
            customerTagJpaRepository.save(new CustomerTagEntity(customerId, tagId));
        }
    }

    @Override
    public void disassociate(Long customerId, Long tagId) {
        customerTagJpaRepository.deleteByCustomerIdAndTagId(customerId, tagId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tag> findTagsByCustomerId(Long customerId) {
        return customerTagJpaRepository.findTagsByCustomerId(customerId).stream()
                .map(this::toDomain)
                .toList();
    }

    private Tag toDomain(TagEntity e) {
        return Tag.of(e.getId(), e.getNome());
    }

    @Override
    public void removeAllByCustomerId(Long customerId) {
        customerTagJpaRepository.deleteAllByCustomerId(customerId);
    }
}
