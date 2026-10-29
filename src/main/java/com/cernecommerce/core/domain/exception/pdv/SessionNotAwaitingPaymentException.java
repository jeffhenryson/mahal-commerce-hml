package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F059 — só a sessão aguardando pagamento, e ainda sem pedido, passa para "pagar no final": a que
 * já foi paga ou já está no salão não tem o que converter. 409.
 */
public class SessionNotAwaitingPaymentException extends RuntimeException {
    public SessionNotAwaitingPaymentException(Long itemId) {
        super("A sessão " + itemId + " não está aguardando pagamento: não dá para passar para pagar no final");
    }
}
