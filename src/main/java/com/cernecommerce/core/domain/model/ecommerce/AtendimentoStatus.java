package com.cernecommerce.core.domain.model.ecommerce;

/**
 * Etapa operacional de um pedido do app no caixa (ECM-F010). Anda ao lado de
 * {@link com.cernecommerce.core.domain.model.pedido.OrderStatus}, sem substituí-lo: o pedido diz se
 * foi pago ({@code AGUARDANDO_PAGAMENTO → CONCLUIDO}); o atendimento diz o que a loja já fez com ele.
 */
public enum AtendimentoStatus {
    /** Chegou na fila; nenhum operador pegou ainda. */
    PENDENTE,
    /** Um operador aceitou no seu caixa aberto; na entrega, a taxa já está definida. */
    ACEITO,
    /** Retirada separada no balcão, esperando o cliente. */
    PRONTO,
    /** Entrega paga e despachada. */
    SAIU_PARA_ENTREGA,
    /** Retirada paga e entregue no balcão. Final. */
    RETIRADO,
    /** Entrega concluída. Final. */
    ENTREGUE,
    /** O caixa recusou (falta de produto, área sem entrega). Final; o pedido foi cancelado. */
    RECUSADO,
    /** O cliente cancelou pelo app. Final. */
    CANCELADO,
    /** Passou do prazo do dia sem pagamento. Final; o pedido foi cancelado pelo varredor. */
    EXPIRADO;

    public boolean isFinal() {
        return this == RETIRADO || this == ENTREGUE || this == RECUSADO || this == CANCELADO || this == EXPIRADO;
    }
}
