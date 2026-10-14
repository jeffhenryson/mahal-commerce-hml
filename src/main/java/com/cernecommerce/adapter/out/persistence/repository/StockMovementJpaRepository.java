package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockMovementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface StockMovementJpaRepository
        extends JpaRepository<StockMovementEntity, Long>, JpaSpecificationExecutor<StockMovementEntity> {

    // EST-C018 — a listagem filtrada do ledger não mora mais aqui: era um @Query com o padrão
    // ":param IS NULL OR ...", e com :from/:to (Instant) nulos o Postgres real recusava inferir o
    // tipo do bind ("could not determine data type of parameter $7"), derrubando GET
    // /estoque/movements com 500 em toda chamada — inclusive sem filtro nenhum. A montagem passou a
    // ser Specification em StockMovementRepositoryImpl.findBySkuAndWarehouseId, mesmo caminho que
    // OrderRepositoryImpl.findAll já tinha tomado pelo mesmo motivo. O javadoc de
    // ProductJpaRepository.search continua valendo para os filtros de String/Boolean que ficaram lá.

    /**
     * Últimas ENTRADAs de um SKU num depósito (item 2 — histórico de compras). Mesmo desempate
     * por {@code id} de {@link #search} — várias entradas do mesmo recebimento podem gravar
     * {@code createdAt} idêntico.
     */
    @Query("SELECT m FROM StockMovementEntity m "
            + "WHERE m.sku = :sku AND m.warehouseId = :warehouseId AND m.type = 'ENTRADA' "
            + "ORDER BY m.createdAt DESC, m.id DESC")
    Page<StockMovementEntity> searchEntradas(@Param("sku") String sku, @Param("warehouseId") Long warehouseId,
            Pageable pageable);

    /**
     * Consumo agregado por SKU no período (EST-F011) — a base da curva ABC.
     *
     * <p>Fonte é {@code SAIDA} do ledger, e não {@code order_item}: o que precisa ser reposto é
     * tudo que saiu da prateleira, incluindo cortesia, perda e o lado de saída de uma conversão
     * (EST-F025), que uma consulta a vendas não enxerga.</p>
     *
     * <p>Valoriza pelo custo médio vigente do par SKU/depósito ({@code stock_balance.average_cost},
     * EST-F007), com {@code COALESCE} para zero: SKU sem custo conhecido entra valendo nada e cai em
     * C, em vez de desaparecer do relatório. O {@code LEFT JOIN} em {@code StockBalanceEntity} também
     * traz o saldo atual, que é o denominador do giro.</p>
     *
     * <p>O índice {@code idx_stock_movement_sku_warehouse_created} da V55 cobre o filtro.</p>
     */
    @Query("""
            SELECT m.sku AS sku, COALESCE(p.name, m.sku) AS productName,
                   SUM(m.quantity) AS consumedQuantity,
                   COALESCE(SUM(m.quantity * COALESCE(b.averageCost, 0)), 0) AS consumedValue,
                   COALESCE(MAX(b.quantity), 0) AS currentBalance
            FROM StockMovementEntity m
            LEFT JOIN ProductEntity p ON p.sku = m.sku
            LEFT JOIN StockBalanceEntity b ON b.sku = m.sku AND b.warehouseId = m.warehouseId
            WHERE m.type = 'SAIDA'
              AND (:warehouseId IS NULL OR m.warehouseId = :warehouseId)
              AND m.createdAt >= :from
              AND m.createdAt <= :to
            GROUP BY m.sku, p.name
            ORDER BY consumedValue DESC
            """)
    List<ConsumptionProjection> findConsumptionByPeriod(@Param("warehouseId") Long warehouseId,
            @Param("from") Instant from, @Param("to") Instant to);

    /** Projeção de {@link #findConsumptionByPeriod}. */
    interface ConsumptionProjection {
        String getSku();

        String getProductName();

        BigDecimal getConsumedQuantity();

        BigDecimal getConsumedValue();

        BigDecimal getCurrentBalance();
    }

    boolean existsBySku(String sku);

    /** EST-F039 — [sku, quantidade, custo] das entradas por transferência num depósito. */
    @Query("""
            SELECT m.sku AS sku, SUM(m.quantity) AS quantity,
                   COALESCE(SUM(m.quantity * COALESCE(m.unitCost, 0)), 0) AS totalCost
            FROM StockMovementEntity m
            WHERE m.type = 'ENTRADA'
              AND m.counterpartWarehouseId IS NOT NULL
              AND m.warehouseId = :warehouseId
              AND m.createdAt >= :from
              AND m.createdAt <= :to
            GROUP BY m.sku
            ORDER BY totalCost DESC, m.sku
            """)
    List<Object[]> transferredInto(@Param("warehouseId") Long warehouseId, @Param("from") Instant from,
            @Param("to") Instant to);
}
