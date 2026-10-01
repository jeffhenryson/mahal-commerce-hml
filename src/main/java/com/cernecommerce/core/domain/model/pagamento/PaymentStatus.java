package com.cernecommerce.core.domain.model.pagamento;

/**
 * Estado de um {@link OrderPayment} (PDV-F006).
 *
 * <p>O balcão só produz {@link #CAPTURED} — o dinheiro já está na gaveta no instante da venda, sem
 * passar por autorização. {@link #PENDING}/{@link #AUTHORIZED}/{@link #FAILED} existem para o
 * fluxo de gateway (Fatia 10), que confirma de forma assíncrona por webhook. {@link #REFUNDED} é o
 * estorno (PDV-F007, ainda não implementado).</p>
 */
public enum PaymentStatus {

    PENDING,
    AUTHORIZED,
    /** Dinheiro efetivamente recebido/confirmado. */
    CAPTURED,
    REFUNDED,
    FAILED,
    /**
     * Cobrança encerrada sem nunca ter recebido dinheiro (PDV-C015) — o caso do pedido montado no
     * app e pago no balcão: a cobrança de gateway criada no checkout não vai ser confirmada por
     * webhook nenhum, e deixá-la {@link #PENDING} para sempre descreveria uma cobrança em aberto
     * que não existe.
     *
     * <p>Não é {@link #FAILED}: ali o gateway recusou, e é assim que a linha aparece numa
     * investigação de pagamento. Não é {@link #REFUNDED}: estorno é dinheiro que entrou e voltou.
     * Aqui o dinheiro nunca passou por este caminho.</p>
     */
    CANCELLED,
    /**
     * Linha capturada na forma errada e aposentada por uma correção (PDV-F030). Fica de pé como
     * lastro — as leituras do pedido a devolvem, riscada — mas não soma em caixa nem em total.
     */
    CORRECTED,
    /**
     * A parte "marcada" da venda (CRM-F010): devida pelo cliente até {@code dueDate}, sem dinheiro
     * nenhum na gaveta. Não soma em caixa; o recebível em {@code customer_receivable} acompanha a
     * quitação.
     */
    ON_ACCOUNT
}
