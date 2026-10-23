package com.cernecommerce.core.domain.model.recebivel;

import java.time.Instant;

/**
 * Estorno de um recebimento de marcado lançado por engano (V173). Append-only: o lote e suas linhas
 * continuam gravados; a existência desta linha os tira do caixa e devolve o saldo aos marcados.
 *
 * @param sessionOpenAtReversal se o caixa do lote ainda estava aberto — fechado, o esperado gravado
 *        no fechamento não muda e o estorno fica só anotado.
 */
public record ReceivablePaymentBatchReversal(Long id, Long batchId, String reason, String reversedBy,
        Instant reversedAt, boolean sessionOpenAtReversal) {
}
