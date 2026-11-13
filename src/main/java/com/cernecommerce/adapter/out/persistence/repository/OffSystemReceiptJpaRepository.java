package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OffSystemReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OffSystemReceiptJpaRepository extends JpaRepository<OffSystemReceiptEntity, Long> {

    /** Mais recentes primeiro; {@code id} desempata o mesmo instante. */
    List<OffSystemReceiptEntity> findBySessionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(Long sessionId);

    Optional<OffSystemReceiptEntity> findByIdAndSessionIdAndDeletedAtIsNull(Long id, Long sessionId);
}
