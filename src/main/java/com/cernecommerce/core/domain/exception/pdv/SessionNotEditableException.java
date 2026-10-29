package com.cernecommerce.core.domain.exception.pdv;

import com.cernecommerce.core.domain.model.pdv.SessionStatus;

/**
 * PDV-F058 — edição de sessão que já foi entregue ou recolhida: o narguilé está (ou esteve) com o
 * cliente, e sabor, vaso e carvão não mudam mais. 409.
 */
public class SessionNotEditableException extends RuntimeException {
    public SessionNotEditableException(Long itemId, SessionStatus status) {
        super("A sessão " + itemId + " já foi entregue (" + status + "): não dá mais para editar");
    }
}
