package com.cernecommerce.core.domain.exception.pedido;

/**
 * PED-C009 — estorno que devolve dinheiro em espécie exige caixa aberto de quem estorna. Sem ele, o
 * estorno caía no caixa do pedido, que pode estar fechado há dias: a cédula saía de alguma gaveta e
 * nenhum esperado a contava. Decisão do dono (2026-10-17). Cartão e PIX não passam pela gaveta e
 * seguem sem exigir. Vira 409 {@code CASH_REFUND_REQUIRES_OPEN_SESSION}.
 */
public class CashRefundRequiresOpenSessionException extends RuntimeException {

    public CashRefundRequiresOpenSessionException(Long orderId, String username) {
        super("O estorno do pedido " + orderId + " devolve dinheiro: " + username
                + " precisa estar com o caixa aberto, para a cédula sair de uma gaveta conferida.");
    }
}
