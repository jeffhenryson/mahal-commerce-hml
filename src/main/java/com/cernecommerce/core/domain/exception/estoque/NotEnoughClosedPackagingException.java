package com.cernecommerce.core.domain.exception.estoque;

import java.math.BigDecimal;

/**
 * PDV-F045 — abrir à mão mais embalagens fechadas do que há disponíveis. Diferente da venda, que abre
 * o nível de cima quando falta, aqui a falta é recusada: abrir 1 maço nunca abre uma carteira. 409
 * com o mesmo {@code INSUFFICIENT_STOCK} da venda, que é o que o contrato do PDV pede — o 400 da
 * venda ({@link InsufficientStockException}) não muda.
 */
public class NotEnoughClosedPackagingException extends RuntimeException {
    public NotEnoughClosedPackagingException(String sku, BigDecimal available, BigDecimal requested) {
        super("Não há " + requested.stripTrailingZeros().toPlainString() + " de " + sku
                + " fechado para abrir: disponível " + available.stripTrailingZeros().toPlainString());
    }
}
