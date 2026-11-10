package com.cernecommerce.core.domain.exception.pedido;

/**
 * Desconto do chefe e resgate de cashback pedidos na mesma venda ou no mesmo fechamento de mesa.
 *
 * <p>Os dois benefícios são exclusivos: na mesma venda é <b>ou</b> o desconto do chefe <b>ou</b> o
 * resgate. Vira {@code 409 BENEFIT_CONFLICT}.</p>
 */
public class BenefitConflictException extends RuntimeException {

    public BenefitConflictException() {
        super("Desconto do chefe e resgate de cashback não podem ser usados na mesma venda: escolha um dos dois.");
    }
}
