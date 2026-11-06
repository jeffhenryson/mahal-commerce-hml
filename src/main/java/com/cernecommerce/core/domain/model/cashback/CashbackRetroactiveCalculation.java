package com.cernecommerce.core.domain.model.cashback;

import com.cernecommerce.core.domain.model.pedido.Order;

import java.math.BigDecimal;
import java.util.List;

/**
 * O que entra no crédito retroativo de um cliente.
 *
 * @param skippedAlreadyEarned pedidos pagos que ficaram de fora por já terem ganho, mesmo que revertido
 */
public record CashbackRetroactiveCalculation(List<Order> orders, List<ItemCredit> items, int skippedAlreadyEarned) {

    /** Uma linha que gera crédito. Prévia e gravação saem daqui, para o arredondamento ser o mesmo. */
    public record ItemCredit(Long orderId, Long orderItemId, BigDecimal base, BigDecimal credit) {
    }
}
