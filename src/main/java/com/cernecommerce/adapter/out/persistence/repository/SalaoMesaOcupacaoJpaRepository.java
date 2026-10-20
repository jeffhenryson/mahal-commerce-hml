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

    // O aparelho da mesa é só um token opaco: sem entidade própria, a tabela de acessos é um conjunto.
    @Modifying
    @Query(value = "INSERT INTO salao_mesa_acesso (ocupacao_id, device_token) VALUES (:ocupacaoId, :deviceToken) "
            + "ON CONFLICT DO NOTHING", nativeQuery = true)
    void addAcesso(@Param("ocupacaoId") Long ocupacaoId, @Param("deviceToken") String deviceToken);

    @Query(value = "SELECT EXISTS (SELECT 1 FROM salao_mesa_acesso WHERE ocupacao_id = :ocupacaoId "
            + "AND device_token = :deviceToken)", nativeQuery = true)
    boolean hasAcesso(@Param("ocupacaoId") Long ocupacaoId, @Param("deviceToken") String deviceToken);
}
