package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashbackRedemptionAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CashbackRedemptionAllocationJpaRepository
        extends JpaRepository<CashbackRedemptionAllocationEntity, Long> {

    /** {@code [earnedEntryId, SUM(amount)]} — quanto de cada ganho já foi consumido por resgate. */
    @Query("""
            SELECT a.earnedEntryId, SUM(a.amount) FROM CashbackRedemptionAllocationEntity a
            WHERE a.earnedEntryId IN :earnedIds
            GROUP BY a.earnedEntryId
            """)
    List<Object[]> sumConsumedByEarnedIds(@Param("earnedIds") Collection<Long> earnedIds);

    /**
     * {@code [earnedEntryId, SUM(amount)]} — alocação líquida de um resgate: o que o REDEEMED
     * consumiu menos o que as devoluções dele (REDEMPTION_REVERSED) já devolveram, por ganho.
     */
    @Query("""
            SELECT a.earnedEntryId, SUM(a.amount) FROM CashbackRedemptionAllocationEntity a
            WHERE a.entryId = :redeemedId
               OR a.entryId IN (SELECT x.id FROM CashbackEntryEntity x
                                WHERE x.reversesEntryId = :redeemedId AND x.type = 'REDEMPTION_REVERSED')
            GROUP BY a.earnedEntryId
            """)
    List<Object[]> sumNetByRedemption(@Param("redeemedId") Long redeemedId);
}
