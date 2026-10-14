package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.AbcAnalysis;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.StockMovement;

import java.time.Instant;
import java.util.List;

/**
 * Port de saída para persistência de movimentações de estoque (trilha de auditoria de
 * alterações de {@link com.cernecommerce.core.domain.model.estoque.StockBalance}).
 */
public interface StockMovementRepository {

    StockMovement save(StockMovement movement);

    /**
     * Consumo agregado por SKU no período (EST-F011), já valorizado pelo custo médio e com o saldo
     * atual — a entrada de {@code AbcAnalysis.classify}. {@code warehouseId} nulo agrega a loja
     * inteira.
     *
     * <p>Devolve as linhas <b>cruas</b>, sem classificação: a curva é aritmética de domínio, e
     * deixá-la em SQL a tornaria impossível de testar sem banco.</p>
     */
    List<AbcAnalysis.ConsumptionLine> findConsumptionByPeriod(Long warehouseId, Instant from, Instant to);

    /**
     * Histórico paginado de movimentações, mais recentes primeiro. {@code sku} e/ou
     * {@code warehouseId} nulos não filtram por esse critério.
     */
    default PageResult<StockMovement> findBySkuAndWarehouseId(String sku, Long warehouseId, int page, int size) {
        return findBySkuAndWarehouseId(sku, warehouseId, null, null, null, page, size);
    }

    /**
     * Mesmo que {@link #findBySkuAndWarehouseId(String, Long, int, int)}, com filtro adicional de
     * {@code type} e intervalo {@code [from, to]} de {@code createdAt} (item 6 do pedido do
     * frontend) — todos opcionais, {@code null} não filtra por esse critério.
     */
    PageResult<StockMovement> findBySkuAndWarehouseId(String sku, Long warehouseId, MovementType type,
            Instant from, Instant to, int page, int size);

    /**
     * Últimas {@code ENTRADA}s de um SKU num depósito, mais recentes primeiro (item 2 do pedido do
     * frontend — histórico de compras). {@code unitCost}/{@code lotCode} já vêm gravados na
     * própria movimentação (EST-F007/EST-F008); {@code goodsReceiptId} liga de volta ao
     * recebimento que a originou, quando veio de um (nulo em movimentação manual e em toda
     * entrada anterior à migration que introduziu o vínculo).
     */
    PageResult<StockMovement> findEntradasBySkuAndWarehouseId(String sku, Long warehouseId, int page, int size);

    /**
     * Indica se existe alguma movimentação para o SKU, em qualquer depósito — usado para bloquear
     * DELETE de variante com histórico (item 8 do pedido do frontend).
     */
    boolean existsBySku(String sku);

    /**
     * EST-F039 — {@code ENTRADA}s por transferência em {@code warehouseId} no período
     * {@code [from, to]}, agrupadas por SKU, com a quantidade e o custo (quantidade × custo
     * unitário; sem custo conta zero). Mais caro primeiro.
     */
    List<TransferredLine> transferredInto(Long warehouseId, Instant from, Instant to);

    record TransferredLine(String sku, java.math.BigDecimal quantity, java.math.BigDecimal totalCost) {
    }
}
