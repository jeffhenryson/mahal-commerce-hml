package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SalaoMesaOcupacaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SalaoMesaOcupacaoJpaRepository extends JpaRepository<SalaoMesaOcupacaoEntity, Long> {

    Optional<SalaoMesaOcupacaoEntity> findFirstByMesaIdOrderByAbertaEmDescIdDesc(Long mesaId);

    List<SalaoMesaOcupacaoEntity> findByComandaIdIn(Collection<Long> comandaIds);

    /** PDV-C057 — a ocupação mais recente de cada mesa, numa consulta (usa idx_salao_mesa_ocupacao_mesa). */
    @Query(value = "SELECT DISTINCT ON (mesa_id) * FROM salao_mesa_ocupacao "
            + "ORDER BY mesa_id, aberta_em DESC, id DESC", nativeQuery = true)
    List<SalaoMesaOcupacaoEntity> findUltimaDeCadaMesa();

    // O aparelho da mesa é só um token opaco: sem entidade própria, a tabela de acessos é um conjunto.
    // Acesso do titular prevalece (V170): o aparelho que já era dele não vira acompanhante.
    @Modifying
    @Query(value = "INSERT INTO salao_mesa_acesso (ocupacao_id, device_token, acompanhante) "
            + "VALUES (:ocupacaoId, :deviceToken, :acompanhante) ON CONFLICT (ocupacao_id, device_token) "
            + "DO UPDATE SET acompanhante = salao_mesa_acesso.acompanhante AND EXCLUDED.acompanhante",
            nativeQuery = true)
    void addAcesso(@Param("ocupacaoId") Long ocupacaoId, @Param("deviceToken") String deviceToken,
            @Param("acompanhante") boolean acompanhante);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM salao_mesa_acesso WHERE ocupacao_id = :ocupacaoId "
            + "AND device_token = :deviceToken)", nativeQuery = true)
    boolean hasAcesso(@Param("ocupacaoId") Long ocupacaoId, @Param("deviceToken") String deviceToken);

    /** O acesso do aparelho: vazio sem acesso; senão se entrou como acompanhante (PDV-C062). */
    @Query(value = "SELECT acompanhante FROM salao_mesa_acesso WHERE ocupacao_id = :ocupacaoId "
            + "AND device_token = :deviceToken", nativeQuery = true)
    Optional<Boolean> findAcompanhante(@Param("ocupacaoId") Long ocupacaoId, @Param("deviceToken") String deviceToken);
}
