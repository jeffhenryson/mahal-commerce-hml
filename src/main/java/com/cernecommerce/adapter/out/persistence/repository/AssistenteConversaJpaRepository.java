package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.AssistenteConversaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AssistenteConversaJpaRepository extends JpaRepository<AssistenteConversaEntity, String> {

    List<AssistenteConversaEntity> findByUsernameOrderByAtualizadaEmDesc(String username, Pageable pageable);

    @Query("SELECT c.id FROM AssistenteConversaEntity c WHERE c.atualizadaEm < :corte")
    List<String> findIdsAtualizadasAntesDe(@Param("corte") Instant corte);

    @Modifying
    @Query("DELETE FROM AssistenteConversaEntity c WHERE c.id IN :ids")
    int deleteByIds(@Param("ids") List<String> ids);
}
