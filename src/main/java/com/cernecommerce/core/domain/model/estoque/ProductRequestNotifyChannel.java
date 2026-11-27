package com.cernecommerce.core.domain.model.estoque;

/**
 * Como o cliente foi avisado de que o produto chegou: pela automação do CRM ({@code PEDIDO_PRODUTO_CHEGOU})
 * ou à mão, pelo WhatsApp da própria loja ({@code MANUAL}, só registra).
 */
public enum ProductRequestNotifyChannel {
    AUTOMATION, MANUAL
}
