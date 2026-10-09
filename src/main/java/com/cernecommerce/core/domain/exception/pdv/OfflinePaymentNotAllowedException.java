package com.cernecommerce.core.domain.exception.pdv;

/**
 * Forma de pagamento que não existe sem rede numa venda offline (PDV-F043): só dinheiro, débito e
 * crédito na maquininha. 400 — o lote inteiro é recusado, porque é defeito de quem montou a fila.
 */
public class OfflinePaymentNotAllowedException extends RuntimeException {
    public OfflinePaymentNotAllowedException(String clientSaleId, String method) {
        super("Venda offline " + clientSaleId + ": " + method + " não é aceito offline — só dinheiro, débito e crédito");
    }
}
