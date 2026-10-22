package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockBalanceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockBalanceJpaRepository extends JpaRepository<StockBalanceEntity, Long> {

    Optional<StockBalanceEntity> findBySkuAndWarehouseId(String sku, Long warehouseId);

    Page<StockBalanceEntity> findByWarehouseIdOrderBySkuAsc(Long warehouseId, Pageable pageable);

    /** PDV-C058 — dos SKUs pedidos, os com saldo livre (físico menos reserva) no depósito. */
    @Query("SELECT b.sku FROM StockBalanceEntity b WHERE b.warehouseId = :warehouseId AND b.sku IN :skus "
            + "AND b.quantity - b.reservedQuantity > 0")
    List<String> findSkusWithFreeStock(@Param("warehouseId") Long warehouseId,
            @Param("skus") Collection<String> skus);

    boolean existsBySku(String sku);

    // EST-F022 — GET /estoque/summary. `sb.sku` é texto livre sem FK: pode casar com o SKU pai
    // (join com `p`) ou com o de uma variação (join com `v`), nunca os dois — a checagem de
    // duplicidade em EstoqueService.createProduct garante que os dois espaços de nomes não
    // colidem. Precedência de custo efetivo (EST-F007): primeiro `sb.averageCost` (custo médio
    // ponderado do histórico real de entradas), senão o custo manual efetivo idêntico a
    // Product.effectivePricingFor — próprio da variação, senão o do pai dela, senão o do produto
    // direto —, senão zero (saldo sem nenhum custo conhecido contribui zero, não invalida a soma).
    //
    // `LEFT JOIN v.product vp` explícito, e NÃO navegação de caminho `v.product.costPrice` no
    // SELECT: esta última gera um INNER JOIN implícito na associação, que elimina do resultado
    // toda linha em que `v` é nulo (saldo cujo SKU é do produto pai, não de uma variação) — bug
    // silencioso que faz a soma inteira ignorar todo saldo que não é de variação.
    //
    // EST-C022 — o QA de 06/09/2026 achou R$ 1.562,49 de diferença entre este total e a soma que a
    // tela calculava por conta própria, e a causa era não haver regra escrita: cada lado escolheu
    // um critério. A regra passa a ser explícita, e está documentada na @Operation de
    // GET /estoque/summary e no README do módulo:
    //
    //   • RASCUNHO fica FORA. Produto que ainda não foi publicado não é mercadoria da loja — o
    //     saldo dele é de um cadastro em construção, e somá-lo infla o valor do estoque com o que
    //     o operador ainda está digitando.
    //   • Produto INATIVO fica DENTRO. Desativar tira de circulação, não da prateleira: a caixa
    //     continua lá, custou dinheiro e aparece no balanço. Excluí-la faria o total divergir da
    //     contagem física, que é o número com que ele precisa bater.
    //   • Depósito inativo fica DENTRO, pela mesma razão — o galpão fechado ainda tem mercadoria.
    //   • Saldo sem custo conhecido contribui zero, e não invalida a soma (já era assim).
    //
    // O filtro de status é aplicado ao produto que dá o custo, e vale tanto para o SKU pai quanto
    // para o de variação: rascunho é atributo do produto, a grade inteira acompanha.
    @Query("""
            SELECT COALESCE(SUM(sb.quantity * COALESCE(sb.averageCost, v.costPrice, vp.costPrice, p.costPrice, 0)), 0)
            FROM StockBalanceEntity sb
            LEFT JOIN ProductVariantEntity v ON v.sku = sb.sku
            LEFT JOIN v.product vp
            LEFT JOIN ProductEntity p ON p.sku = sb.sku
            WHERE (vp IS NULL OR vp.status <> 'RASCUNHO')
              AND (p  IS NULL OR p.status  <> 'RASCUNHO')
            """)
    BigDecimal sumInventoryValueAtCost();
}
