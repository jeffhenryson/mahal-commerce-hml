package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerNoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerNoteJpaRepository extends JpaRepository<CustomerNoteEntity, Long> {

    List<CustomerNoteEntity> findByCustomerIdOrderByCriadoEmDesc(Long customerId);

    org.springframework.data.domain.Page<CustomerNoteEntity> findByCustomerIdOrderByCriadoEmDescIdDesc(Long customerId,
            org.springframework.data.domain.Pageable pageable);
}
