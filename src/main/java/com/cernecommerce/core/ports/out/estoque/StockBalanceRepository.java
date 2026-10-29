package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.StockBalance;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Port de saída para persistência de saldo de estoque por SKU/depósito.
 */
public interface StockBalanceRepository {

    Optional<StockBalance> findBySkuAndWarehouseId(String sku, Long warehouseId);

    /** Saldo paginado dos produtos de um depósito, ordenado por SKU — sem os arquivados (EST-F054). */
    PageResult<StockBalance> findByWarehouseId(Long warehouseId, int page, int size);

    /** PDV-C058 — dos {@code skus}, os com saldo livre (físico menos reserva) no depósito. */
    Set<String> findSkusWithFreeStock(Long warehouseId, Collection<String> skus);

    StockBalance save(StockBalance stockBalance);

    /**
     * Indica se existe alguma linha de saldo para o SKU, em qualquer depósito — usado para
     * bloquear DELETE de variante com histórico (item 8 do pedido do frontend).
     */
    boolean existsBySku(String sku);

    /**
     * Soma {@code quantity * costPrice efetivo} de todo saldo em todos os depósitos — o
     * {@code costPrice efetivo} segue a mesma precedência variação→pai de
     * {@code Product.effectivePricingFor}. Saldo cujo SKU não resolve a nenhum custo conhecido
     * contribui zero, não é excluído da soma nem a invalida.
     */
    BigDecimal sumInventoryValueAtCost();
}
