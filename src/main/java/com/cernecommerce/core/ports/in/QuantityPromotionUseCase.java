package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.estoque.PromotionChannel;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotion;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotionEngine;
import com.cernecommerce.core.domain.model.pedido.OrderItem;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Promoção por quantidade ("leve 4 por R$ 10"): cadastro (Estoque › Promoções) e o cálculo que o
 * balcão, a comanda e o marketplace chamam.
 */
public interface QuantityPromotionUseCase {

    /** Todas as regras, por id. */
    List<QuantityPromotion> list();

    /** @throws com.cernecommerce.core.domain.exception.estoque.QuantityPromotionNotFoundException */
    QuantityPromotion get(Long id);

    /**
     * @throws com.cernecommerce.core.domain.exception.estoque.DuplicatePromotionNameException
     * @throws com.cernecommerce.core.domain.exception.estoque.PromotionSkuNotFoundException
     * @throws com.cernecommerce.core.domain.exception.estoque.PromotionSkuInactiveException
     */
    QuantityPromotion create(QuantityPromotion promotion, String username);

    /** Substitui a regra inteira. Vendas já feitas não mudam, porque guardam o desconto na linha. */
    QuantityPromotion update(Long id, QuantityPromotion promotion, String username);

    QuantityPromotion setActive(Long id, boolean active, String username);

    void delete(Long id);

    /** Só as vigentes no instante e no canal. */
    List<QuantityPromotion> listVigentes(PromotionChannel channel, Instant at);

    /**
     * Aplica as regras vigentes em {@code at} no {@code channel} ao carrinho. O cálculo único que os
     * três canais usam.
     *
     * @param unitsInKit SKU → unidades presas a kit montável (não contam)
     */
    QuantityPromotionEngine.Result calculate(PromotionChannel channel, Instant at,
            List<QuantityPromotionEngine.CartLine> lines, Map<String, Integer> unitsInKit);

    /**
     * Venda de balcão (canal PDV): a venda do caixa e a compra na loja lançada na mesa. Calcula a promoção
     * sobre as linhas já montadas do catálogo, confere com o que a tela mostrou e grava o desconto nas
     * linhas. Unidades em kit ({@code kitQuantity}) não contam, e o desconto de cada SKU é repartido entre
     * as linhas dele sem passar do que cada uma ainda cobra.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.PromotionChangedException check estrito
     *         divergente. No leniente (venda offline já cobrada) vale o valor do cliente, com aviso no log.
     */
    PromotedSale applyToSale(Instant at, List<OrderItem> items, List<PdvUseCase.SaleItemCommand> commands,
            PdvUseCase.PromotionCheck check, String clientSaleId);

    /** Linhas com a promoção aplicada e o desconto total dela (fora do teto de desconto). */
    record PromotedSale(List<OrderItem> items, java.math.BigDecimal total) {
    }

    /**
     * Sem motor ligado (testes antigos): vende sem promoção, conferindo o esperado contra zero.
     */
    static PromotedSale withoutPromotions(List<OrderItem> items, PdvUseCase.PromotionCheck check) {
        if (!check.lenient() && check.expectedDiscount().signum() != 0) {
            throw new com.cernecommerce.core.domain.exception.estoque.PromotionChangedException(
                    check.expectedDiscount(), java.math.BigDecimal.ZERO.setScale(2));
        }
        return new PromotedSale(items, java.math.BigDecimal.ZERO);
    }

    /** Números de uma regra no período: para o dono ver se a promoção compensa. */
    PromotionStats stats(Long id, Instant from, Instant to);

    record PromotionStats(long vendas, long grupos, long unidades, java.math.BigDecimal descontoTotal) {
    }
}
