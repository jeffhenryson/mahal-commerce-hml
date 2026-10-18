package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ReorderPointEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReorderPointJpaRepository extends JpaRepository<ReorderPointEntity, Long> {

    Optional<ReorderPointEntity> findBySkuAndWarehouseId(String sku, Long warehouseId);

    Page<ReorderPointEntity> findByWarehouseIdOrderBySkuAsc(Long warehouseId, Pageable pageable);

    void deleteBySkuAndWarehouseId(String sku, Long warehouseId);

    // EST-F022 — GET /estoque/summary. Replica em SQL a mesma regra de
    // ReorderPoint.severityFor: CRÍTICO é saldo <= 0 OU <= minQuantity * ReorderPoint
    // .CRITICAL_THRESHOLD_FACTOR (0.5); ATENÇÃO é abaixo do mínimo mas acima do limiar crítico.
    // List<Object[]> (mesmo idioma de findCategoryWithMostProductsRaw) e não uma constructor
    // expression: SUM sem linha nenhuma devolve NULL, e o adapter converte para 0 — uma
    // constructor expression para (long, long) quebraria nesse caso.
    //
    // EST-C019 — o LEFT JOIN com COALESCE(sb.quantity, 0) não é defensivo, é a regra: quem tem ponto
    // de reposição e NENHUMA linha em stock_balance nunca recebeu entrada naquele depósito, então o
    // saldo dele é zero — o caso mais crítico que existe. Com o INNER JOIN anterior esses SKUs eram
    // descartados das duas contagens, e o resumo dizia "nenhum produto crítico" enquanto a tela de
    // Alertas (que resolve saldo ausente como zero, em EstoqueService) os listava corretamente.
    //
    // EST-F053 — produto base com variações (e sem parentSellable) fica fora: não vende nem recebe
    // entrada, então o saldo dele é sempre zero e o alerta seria sempre um falso "Crítico".
    //
    // O 0.5 literal duplica ReorderPoint.CRITICAL_THRESHOLD_FACTOR de propósito — JPQL não lê
    // constante de domínio. Mudar o fator lá exige mudar aqui; o teste de EST-C019 cobre os dois.
    @Query("""
            SELECT
              SUM(CASE WHEN COALESCE(sb.quantity, 0) <= 0
                         OR COALESCE(sb.quantity, 0) <= (rp.minQuantity * 0.5) THEN 1L ELSE 0L END),
              SUM(CASE WHEN COALESCE(sb.quantity, 0) > 0 AND COALESCE(sb.quantity, 0) < rp.minQuantity
                            AND COALESCE(sb.quantity, 0) > (rp.minQuantity * 0.5) THEN 1L ELSE 0L END)
            FROM ReorderPointEntity rp
            LEFT JOIN StockBalanceEntity sb ON sb.sku = rp.sku AND sb.warehouseId = rp.warehouseId
            WHERE NOT EXISTS (
              SELECT 1 FROM ProductVariantEntity v
              WHERE v.product.sku = rp.sku AND v.product.parentSellable = false)
            """)
    List<Object[]> countAlertsRaw();
}
