package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-C036 — a sessão já foi servida e não foi paga: tirá-la da mesa é uma desistência, e a
 * desistência exige motivo. Sem ele, apagar a linha era dar o narguilé de graça sem a alçada de
 * cortesia e sem deixar rastro no histórico. 400.
 */
public class SessionWithdrawalReasonRequiredException extends RuntimeException {
    public SessionWithdrawalReasonRequiredException(Long itemId) {
        super("A sessão " + itemId + " já foi servida e não foi paga: informe o motivo da desistência.");
    }
}
