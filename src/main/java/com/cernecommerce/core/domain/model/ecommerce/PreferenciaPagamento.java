package com.cernecommerce.core.domain.model.ecommerce;

/**
 * Como o cliente do app diz que vai pagar no caixa (ECM-F009). É só uma intenção, mostrada na fila
 * do PDV: quem registra o pagamento de verdade é a liquidação, com as formas que o operador lançar.
 * Na ENTREGA só vale {@link #PIX} — o operador cobra pelo WhatsApp antes de despachar.
 */
public enum PreferenciaPagamento {
    PIX,
    CREDITO,
    DEBITO,
    DINHEIRO
}
