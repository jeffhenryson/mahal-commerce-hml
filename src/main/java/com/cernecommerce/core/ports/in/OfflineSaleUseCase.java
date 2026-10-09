package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.pdv.OfflineSaleItem;
import com.cernecommerce.core.domain.model.pdv.OfflineSalePayment;
import com.cernecommerce.core.domain.model.pdv.OfflineSaleRejection;

import java.time.Instant;
import java.util.List;

/**
 * Venda offline no balcão (PDV-F043): sincronização da fila do caixa e revisão das recusadas.
 */
public interface OfflineSaleUseCase {

    /**
     * Sincroniza as vendas feitas offline no caixa {@code sessionId}, cada uma na sua transação:
     * uma recusada não derruba as outras.
     *
     * <p>O caixa precisa estar aberto e ser de {@code username}. Venda já sincronizada (mesmo
     * {@code clientSaleId}) volta como {@code DUPLICATE}; a que não entra — falta de estoque, SKU
     * que sumiu, sem preço — volta como {@code REJECTED} e fica guardada para revisão, e o caixa não
     * fecha até ela ser resolvida.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException caixa fechado
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException caixa de outro
     * @throws com.cernecommerce.core.domain.exception.pdv.OfflinePaymentNotAllowedException PIX/marcado no lote
     * @throws com.cernecommerce.core.domain.exception.pdv.OfflineSoldAtOutOfWindowException horário fora do caixa
     */
    List<SyncResult> sync(Long sessionId, List<OfflineSaleCommand> sales, String username);

    /** As recusadas do caixa, pendentes e resolvidas. */
    List<OfflineSaleRejection> listRejections(Long sessionId);

    /**
     * Reenvia a venda recusada depois do acerto (em nome do operador do caixa). Deu certo: fica
     * {@code RETRIED} com o pedido. Falhou de novo: continua pendente, com o motivo novo.
     */
    OfflineSaleRejection retry(Long rejectionId, String reviewer);

    /** Descarta com motivo — o dinheiro dessa venda é acertado fora do sistema. */
    OfflineSaleRejection discard(Long rejectionId, String reason, String reviewer);

    /** Uma venda da fila offline, como o caixa a registrou. */
    record OfflineSaleCommand(String clientSaleId, Instant soldAt, Long customerId, List<OfflineSaleItem> items,
            List<OfflineSalePayment> payments) {
    }

    enum SyncStatus { SYNCED, DUPLICATE, REJECTED }

    /** O que aconteceu com uma venda do lote. */
    record SyncResult(String clientSaleId, SyncStatus status, Long orderId, Long rejectionId, String errorCode,
            String message) {

        public static SyncResult synced(String clientSaleId, Long orderId, boolean duplicate) {
            return new SyncResult(clientSaleId, duplicate ? SyncStatus.DUPLICATE : SyncStatus.SYNCED, orderId,
                    null, null, null);
        }

        public static SyncResult rejected(OfflineSaleRejection rejection) {
            return new SyncResult(rejection.clientSaleId(), SyncStatus.REJECTED, null, rejection.id(),
                    rejection.errorCode(), rejection.message());
        }
    }
}
