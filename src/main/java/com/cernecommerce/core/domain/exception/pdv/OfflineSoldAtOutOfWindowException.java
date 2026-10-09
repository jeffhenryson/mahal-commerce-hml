package com.cernecommerce.core.domain.exception.pdv;

import java.time.Instant;

/**
 * Horário da venda offline fora do caixa (PDV-F043): antes da abertura, ou no futuro além da folga de
 * relógio. 400 — relógio do terminal errado ou fila de outro caixa; o lote inteiro é recusado.
 */
public class OfflineSoldAtOutOfWindowException extends RuntimeException {
    public OfflineSoldAtOutOfWindowException(String clientSaleId, Instant soldAt, Instant openedAt) {
        super("Venda offline " + clientSaleId + " com horário " + soldAt + " fora do caixa (aberto em " + openedAt + ")");
    }
}
