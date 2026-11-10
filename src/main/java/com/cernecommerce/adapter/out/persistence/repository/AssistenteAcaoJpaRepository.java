package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.AssistenteAcaoEntity;
import com.cernecommerce.core.domain.model.assistente.EstadoAcao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AssistenteAcaoJpaRepository extends JpaRepository<AssistenteAcaoEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AssistenteAcaoEntity a WHERE a.id = :id")
    Optional<AssistenteAcaoEntity> findByIdForUpdate(@Param("id") String id);

    List<AssistenteAcaoEntity> findByConversaIdOrderByCriadaEmAsc(String conversaId);

    @Modifying
    @Query("DELETE FROM AssistenteAcaoEntity a WHERE a.conversaId IN :ids")
    int deleteByConversaIds(@Param("ids") List<String> ids);

    @Modifying
    @Query("UPDATE AssistenteAcaoEntity a SET a.estado = :novo WHERE a.estado = :atual AND a.expiraEm <= :agora")
    int expirar(@Param("atual") EstadoAcao atual, @Param("novo") EstadoAcao novo, @Param("agora") Instant agora);

    @Modifying
    @Query("UPDATE AssistenteAcaoEntity a SET a.estado = :novo, a.resultado = :resultado, a.executadaEm = :agora "
            + "WHERE a.estado = :atual AND a.executadaEm < :corte")
    int falharTravadas(@Param("atual") EstadoAcao atual, @Param("novo") EstadoAcao novo,
            @Param("resultado") String resultado, @Param("corte") Instant corte, @Param("agora") Instant agora);
}
