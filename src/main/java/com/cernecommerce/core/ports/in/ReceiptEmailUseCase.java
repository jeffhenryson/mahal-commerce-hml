package com.cernecommerce.core.ports.in;

/** Comprovante de compra por e-mail, enviado quando o operador pede (balcão ou mesa). */
public interface ReceiptEmailUseCase {

    /**
     * Envia o comprovante do pedido para o e-mail do cliente vinculado.
     *
     * @return o destinatário mascarado (ex.: {@code jo***@gmail.com}), para o operador confirmar
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException se o pedido não existir
     * @throws com.cernecommerce.core.domain.exception.pdv.ReceiptEmailUnavailableException se o pedido
     *         estiver cancelado, não tiver cliente vinculado ou o cliente não tiver e-mail
     */
    String sendPurchaseReceipt(Long orderId);
}
