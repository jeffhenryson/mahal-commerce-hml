package com.cernecommerce.core.domain.exception.pedido;

import java.math.BigDecimal;

/**
 * Desconto concedido acima do teto permitido (PDV-F004).
 *
 * <p>O teto é um número de configuração, não um segundo nível de permissão. Dois níveis só criariam
 * a tentação de distribuir o nível maior para evitar o incômodo — e aí não haveria teto nenhum.</p>
 */
public class DiscountLimitExceededException extends RuntimeException {

    public DiscountLimitExceededException(BigDecimal discountPercent, BigDecimal maxPercent) {
        super("Desconto de " + discountPercent + "% excede o teto de " + maxPercent
                + "% por pedido. Ajuste o desconto ou peça autorização para alterar o limite.");
    }

    /**
     * Desconto do chefe fora da regra (percentual exato sobre a base elegível, linha fora dos
     * benefícios, fechamento parcial…). Mesmo {@code 409 DISCOUNT_LIMIT_EXCEEDED} que a tela já trata.
     */
    public DiscountLimitExceededException(String message) {
        super(message);
    }
}
