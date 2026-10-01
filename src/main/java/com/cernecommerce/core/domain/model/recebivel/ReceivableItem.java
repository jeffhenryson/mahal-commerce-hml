package com.cernecommerce.core.domain.model.recebivel;

import java.math.BigDecimal;

/**
 * Snapshot de uma linha do pedido no momento do marcar. Guarda todos os itens do pedido, mesmo
 * quando só parte dele foi marcada.
 *
 * @param mode {@code null} em produto; {@code SESSAO} ou {@code ROSH_EXTRA} na sessão de narguilé
 */
public record ReceivableItem(Long id, Long orderItemId, String sku, String productName, BigDecimal quantity,
        BigDecimal subtotal, String mode) {
}
