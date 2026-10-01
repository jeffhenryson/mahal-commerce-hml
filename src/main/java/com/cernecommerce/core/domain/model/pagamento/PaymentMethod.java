package com.cernecommerce.core.domain.model.pagamento;

/**
 * Forma de pagamento de um {@link OrderPayment} (PDV-F006).
 *
 * <p>Os quatro primeiros métodos são do balcão físico, sempre asserção do operador. {@link
 * #GATEWAY_PIX} (ECM-F004, Fatia 10, gateway InfinitePay) é distinto de {@link #PIX} de propósito
 * — trust/auditoria diferentes: {@code PIX} é o operador dizendo "recebi", {@code GATEWAY_PIX} é
 * confirmado pelo webhook do gateway, sempre reconferido contra a API dele antes de capturar (nunca
 * confiando no corpo do webhook). {@code GATEWAY_CARTAO} fica para quando/se cartão via gateway
 * existir — não adicionado agora por não ter uso.</p>
 */
public enum PaymentMethod {

    /** Único método em que pode haver troco. */
    DINHEIRO,
    DEBITO,
    /** Único método que aceita {@link OrderPayment#installments()}. */
    CREDITO,
    PIX,
    /** PIX confirmado pelo webhook do gateway (ECM-F004) — nunca lançado pelo operador. */
    GATEWAY_PIX,
    /**
     * "Marcar" (CRM-F010): o cliente VIP paga depois. A linha nasce ON_ACCOUNT, com vencimento, e
     * nunca entra em caixa — quem entra é a quitação, no caixa de quem recebe.
     */
    MARCADO
}
