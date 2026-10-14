package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F044 — o 2º rosh já tem sabor (ou já saiu do salão): o sabor se escolhe uma vez, e trocar
 * depois mexeria numa lata já consumida. 409.
 */
public class RoshFlavorNotPendingException extends RuntimeException {
    public RoshFlavorNotPendingException(Long itemId) {
        super("O 2º rosh " + itemId + " não está aguardando sabor");
    }
}
