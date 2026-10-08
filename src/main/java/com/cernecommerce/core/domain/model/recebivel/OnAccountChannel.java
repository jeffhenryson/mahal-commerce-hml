package com.cernecommerce.core.domain.model.recebivel;

/**
 * Onde o "Marcar" aconteceu (CRM-F010): no balcão ou na mesa. Não é coluna de
 * {@code customer_receivable} — o recebível de mesa é o que tem {@code comanda_id}.
 */
public enum OnAccountChannel {
    BALCAO,
    MESA;

    public static OnAccountChannel of(Long comandaId) {
        return comandaId == null ? BALCAO : MESA;
    }
}
