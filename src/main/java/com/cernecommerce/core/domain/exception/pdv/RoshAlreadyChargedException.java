package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F055 — desfazer o duplo cujo 2º rosh é cobrado (não cortesia) ou já entrou num pedido. Tirar a
 * linha apagaria uma cobrança: quem quer desfazer uma venda paga reembolsa o pedido.
 */
public class RoshAlreadyChargedException extends RuntimeException {
    public RoshAlreadyChargedException(Long roshItemId) {
        super("O 2º rosh já foi cobrado e não pode ser desfeito (linha " + roshItemId + ").");
    }
}
