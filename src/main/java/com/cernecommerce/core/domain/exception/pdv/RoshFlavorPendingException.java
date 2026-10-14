package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F044 — o 2º rosh sem sabor não vai ao preparo: não há o que preparar. Escolha o sabor por
 * {@code PUT …/itens/{itemId}/rosh-sabor}, que já o promove quando o narguilé está livre. 409.
 */
public class RoshFlavorPendingException extends RuntimeException {
    public RoshFlavorPendingException(Long itemId) {
        super("O 2º rosh " + itemId + " ainda não tem sabor: escolha o sabor antes de preparar");
    }
}
