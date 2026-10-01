package com.cernecommerce.core.domain.model.recebivel;

/** Situação de um {@link CustomerReceivable} ("marcado") — CRM-F010. */
public enum ReceivableStatus {
    /** Nada pago ainda, dentro do prazo. */
    ABERTO,
    /** Parte paga, dentro do prazo. */
    PARCIAL,
    /** Zerado. Terminal. */
    QUITADO,
    /** Venceu com saldo — gravado pelo job diário; bloqueia novo marcar do cliente. */
    VENCIDO,
    /** Perdoado ou lançado por engano, ou o pedido de origem foi reembolsado. Terminal. */
    CANCELADO;

    /** Ainda tem saldo a receber. */
    public boolean isOpen() {
        return this == ABERTO || this == PARCIAL || this == VENCIDO;
    }
}
