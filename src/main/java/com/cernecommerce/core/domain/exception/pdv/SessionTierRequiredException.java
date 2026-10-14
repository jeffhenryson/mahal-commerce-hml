package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F050 — não há faixa para a sessão: a essência do catálogo não tem faixa na ficha (ou a dela
 * está inativa) e o pedido também não trouxe {@code tierId}. Sem faixa não há preço. PDV-F052 usa a
 * mesma exceção na ficha do produto, quando a casa exige a faixa da essência de sessão.
 */
public class SessionTierRequiredException extends RuntimeException {

    private final String sku;

    public SessionTierRequiredException(String sku) {
        super("Esta essência está sem faixa — defina a faixa na ficha do produto.");
        this.sku = sku;
    }

    /** O sabor sem faixa; nulo quando a sessão é só em texto. */
    public String sku() {
        return sku;
    }
}
