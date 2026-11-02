package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.NfeImportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NfeImportJpaRepository extends JpaRepository<NfeImportEntity, Long> {

    /** V182 — import já CONFIRMED com esta chave de acesso (no máximo um, pelo índice único parcial). */
    java.util.Optional<NfeImportEntity> findFirstByAccessKeyAndStatus(String accessKey, String status);
}
