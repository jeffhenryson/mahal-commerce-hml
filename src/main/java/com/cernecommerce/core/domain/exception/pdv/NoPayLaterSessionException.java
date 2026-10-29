package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F060 — compra na loja lançada na conta de uma mesa sem sessão paga no final em aberto: sem ela
 * não há cobrança no fim da mesa a que juntar a compra, e ela deve ser cobrada no caixa. 409.
 */
public class NoPayLaterSessionException extends RuntimeException {
    public NoPayLaterSessionException(Long comandaId) {
        super("A comanda " + comandaId + " não tem sessão para pagar no final: cobre a compra no caixa");
    }
}
