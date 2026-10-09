package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.exception.pdv.OfflineRejectionAlreadyResolvedException;

import java.time.Instant;
import java.util.List;

/**
 * Venda feita offline que a sincronização recusou e que espera revisão (PDV-F043).
 *
 * <p>Decisão do dono (2026-10-08): faltou estoque, sumiu o SKU ou o preço — a venda <b>não</b> entra
 * sozinha. Ela fica guardada como chegou, com o motivo, e o caixa não fecha até alguém resolvê-la:
 * reenviar depois de acertar o estoque ({@link #retried}) ou descartar com motivo ({@link #discarded}),
 * quando o dinheiro é acertado fora do sistema. Nenhuma venda offline some em silêncio.</p>
 */
public record OfflineSaleRejection(
        Long id,
        Long sessionId,
        String clientSaleId,
        Instant clientSoldAt,
        Long customerId,
        List<OfflineSaleItem> items,
        List<OfflineSalePayment> payments,
        String errorCode,
        String message,
        Instant createdAt,
        String createdBy,
        Instant resolvedAt,
        String resolvedBy,
        OfflineRejectionResolution resolution,
        String resolutionNote,
        Long orderId) {

    public OfflineSaleRejection {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId é obrigatório");
        }
        if (clientSaleId == null || clientSaleId.isBlank()) {
            throw new IllegalArgumentException("clientSaleId é obrigatório");
        }
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode é obrigatório");
        }
        items = items == null ? List.of() : List.copyOf(items);
        payments = payments == null ? List.of() : List.copyOf(payments);
        // Resolvida pela metade é meio estado — mesma régua de OpenPackage com closedAt/closeReason.
        if ((resolvedAt == null) != (resolution == null)) {
            throw new IllegalArgumentException("resolvedAt e resolution vêm juntos");
        }
    }

    public static OfflineSaleRejection create(Long sessionId, String clientSaleId, Instant clientSoldAt,
            Long customerId, List<OfflineSaleItem> items, List<OfflineSalePayment> payments, String errorCode,
            String message, String createdBy, Instant now) {
        return new OfflineSaleRejection(null, sessionId, clientSaleId, clientSoldAt, customerId, items, payments,
                errorCode, message, now, createdBy, null, null, null, null, null);
    }

    public boolean isPending() {
        return resolution == null;
    }

    /** Reenviada com sucesso: virou o pedido {@code newOrderId}. */
    public OfflineSaleRejection retried(Long newOrderId, String by, Instant at) {
        requirePending();
        return new OfflineSaleRejection(id, sessionId, clientSaleId, clientSoldAt, customerId, items, payments,
                errorCode, message, createdAt, createdBy, at, by, OfflineRejectionResolution.RETRIED, null, newOrderId);
    }

    /** Reenviada e recusada de novo: continua pendente, com o motivo novo. */
    public OfflineSaleRejection stillFailing(String newErrorCode, String newMessage) {
        requirePending();
        return new OfflineSaleRejection(id, sessionId, clientSaleId, clientSoldAt, customerId, items, payments,
                newErrorCode, newMessage, createdAt, createdBy, null, null, null, null, null);
    }

    /** Descartada: o dinheiro foi acertado fora do sistema. O motivo é obrigatório. */
    public OfflineSaleRejection discarded(String reason, String by, Instant at) {
        requirePending();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("o motivo do descarte é obrigatório");
        }
        return new OfflineSaleRejection(id, sessionId, clientSaleId, clientSoldAt, customerId, items, payments,
                errorCode, message, createdAt, createdBy, at, by, OfflineRejectionResolution.DISCARDED, reason.trim(),
                null);
    }

    public OfflineSaleRejection withId(Long newId) {
        return new OfflineSaleRejection(newId, sessionId, clientSaleId, clientSoldAt, customerId, items, payments,
                errorCode, message, createdAt, createdBy, resolvedAt, resolvedBy, resolution, resolutionNote, orderId);
    }

    private void requirePending() {
        if (!isPending()) {
            throw new OfflineRejectionAlreadyResolvedException(id, resolution.name());
        }
    }
}
