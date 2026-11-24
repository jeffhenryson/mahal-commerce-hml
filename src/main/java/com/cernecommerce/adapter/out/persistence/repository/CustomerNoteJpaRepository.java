package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerNoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerNoteJpaRepository extends JpaRepository<CustomerNoteEntity, Long> {

    List<CustomerNoteEntity> findByCustomerIdOrderByCriadoEmDesc(Long customerId);

    org.springframework.data.domain.Page<CustomerNoteEntity> findByCustomerIdOrderByCriadoEmDescIdDesc(Long customerId,
            org.springframework.data.domain.Pageable pageable);

    /** V206 — apaga todas as notas do cliente (os anexos saem antes, pelo repositório de anexos). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM CustomerNoteEntity n WHERE n.customerId = :customerId")
    int deleteAllByCustomerId(@org.springframework.data.repository.query.Param("customerId") Long customerId);
}
