package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashbackRateEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CashbackRateJpaRepository extends JpaRepository<CashbackRateEntity, Long> {

    Page<CashbackRateEntity> findAll(Pageable pageable);

    /**
     * Candidatas à resolução SKU → CATEGORY → GLOBAL, da mais para a menos específica. O
     * chamador pega a primeira — {@code category} nulo simplesmente nunca casa com a cláusula de
     * CATEGORY (semântica de {@code NULL} do SQL), o que já basta para produto sem categoria só
     * poder resolver por SKU ou GLOBAL.
     */
    @Query("""
            SELECT r FROM CashbackRateEntity r
            WHERE r.active = true
              AND r.validFrom <= :at
              AND (r.validTo IS NULL OR r.validTo > :at)
              AND ((r.scope = 'SKU' AND r.scopeRef = :sku)
                OR (r.scope = 'CATEGORY' AND r.scopeRef = :category)
                OR (r.scope = 'GLOBAL'))
            ORDER BY CASE r.scope WHEN 'SKU' THEN 0 WHEN 'CATEGORY' THEN 1 ELSE 2 END ASC
            """)
    List<CashbackRateEntity> findApplicableCandidates(@Param("sku") String sku,
            @Param("category") String category, @Param("at") Instant at, Pageable pageable);

    /**
     * Existe taxa ativa e sem {@code validTo} (em aberto) para a mesma abrangência? Mesma
     * abrangência do índice único parcial do schema — usado para recusar uma segunda taxa
     * concorrente antes de o banco rejeitar por constraint.
     */
    @Query("""
            SELECT COUNT(r) > 0 FROM CashbackRateEntity r
            WHERE r.scope = :scope AND r.active = true AND r.validTo IS NULL
              AND ((:scopeRef IS NULL AND r.scopeRef IS NULL) OR r.scopeRef = :scopeRef)
            """)
    boolean existsActiveOpenEnded(@Param("scope") String scope, @Param("scopeRef") String scopeRef);

    @Query("""
            SELECT r FROM CashbackRateEntity r
            WHERE r.scope = 'GLOBAL'
              AND r.active = true
              AND r.validFrom <= :at
              AND (r.validTo IS NULL OR r.validTo > :at)
            ORDER BY r.validFrom DESC
            """)
    List<CashbackRateEntity> findGlobalCandidates(@Param("at") Instant at,
            org.springframework.data.domain.Pageable pageable);
}
