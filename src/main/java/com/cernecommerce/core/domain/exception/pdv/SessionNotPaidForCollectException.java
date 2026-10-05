package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F040 — sessão paga no final (PDV-F034) recolhida sem estar paga. A sessão vai ao salão a
 * receber, mas o recolhimento é o momento de cobrar: registra-se o pagamento (fechamento parcial com
 * a linha, inclusive MARCADO) e só então se recolhe. 409.
 */
public class SessionNotPaidForCollectException extends RuntimeException {
    public SessionNotPaidForCollectException(Long itemId) {
        super("A sessão " + itemId + " é paga no final: registre o pagamento antes de recolher");
    }
}
