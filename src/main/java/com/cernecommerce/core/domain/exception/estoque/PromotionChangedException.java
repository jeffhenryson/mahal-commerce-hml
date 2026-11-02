package com.cernecommerce.core.domain.exception.estoque;

import java.math.BigDecimal;

/**
 * 409 PROMOTION_CHANGED: o desconto de promoção por quantidade que o servidor calculou difere do que
 * o cliente mostrou ({@code expectedPromotionDiscount}). A venda não fecha com um total diferente do
 * que o cliente viu; a tela recarrega as regras e mostra o valor atual.
 */
public class PromotionChangedException extends RuntimeException {

    private final BigDecimal currentPromotionDiscount;

    public PromotionChangedException(BigDecimal expected, BigDecimal current) {
        super("A promoção mudou: o desconto agora é R$ " + current + " (a tela mostrava R$ " + expected + ")");
        this.currentPromotionDiscount = current;
    }

    public BigDecimal getCurrentPromotionDiscount() {
        return currentPromotionDiscount;
    }
}
