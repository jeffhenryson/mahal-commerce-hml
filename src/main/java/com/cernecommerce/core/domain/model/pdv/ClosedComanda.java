package com.cernecommerce.core.domain.model.pdv;

/**
 * Uma comanda com o registro do encerramento (PDV-F029): quem fechou, finalizou ou cancelou, e o
 * motivo do cancelamento. Fica fora de {@link Comanda} porque só o histórico os lê.
 *
 * @param storePurchase PDV-F036 — "comprou na loja?"; nulo se ninguém respondeu
 */
public record ClosedComanda(Comanda comanda, String closedBy, String cancelReason, StorePurchase storePurchase) {

    public ClosedComanda(Comanda comanda, String closedBy, String cancelReason) {
        this(comanda, closedBy, cancelReason, null);
    }
}
