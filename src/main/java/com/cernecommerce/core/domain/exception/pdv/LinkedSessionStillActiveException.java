package com.cernecommerce.core.domain.exception.pdv;

import java.util.List;

/**
 * PDV-C036 — a sessão tem rosh ligado ainda no salão (na fila, em preparo ou na mesa), e o rosh usa
 * o mesmo narguilé. A desistência da sessão espera o rosh ser resolvido — recolhido, removido ou
 * também desistido —, senão o vaso voltaria à casa com um rosh ainda em uso. 409.
 */
public class LinkedSessionStillActiveException extends RuntimeException {
    public LinkedSessionStillActiveException(Long itemId, List<Long> activeIds) {
        super("A sessão " + itemId + " tem rosh ainda no salão: " + activeIds
                + ". Resolva o rosh antes de registrar a desistência.");
    }
}
