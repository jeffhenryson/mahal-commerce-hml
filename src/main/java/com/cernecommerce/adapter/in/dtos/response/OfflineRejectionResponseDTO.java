package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Venda offline recusada, pendente ou resolvida (PDV-F043). */
@Data
public class OfflineRejectionResponseDTO {

    private Long id;
    private Long sessionId;
    private String clientSaleId;
    private Instant clientSoldAt;
    private Long customerId;
    private List<Item> items;
    private List<Payment> payments;
    private String errorCode;
    private String message;
    private Instant createdAt;
    private String createdBy;

    @Schema(description = "Nulo enquanto pendente. RETRIED (virou o pedido orderId) ou DISCARDED.")
    private String resolution;

    private Instant resolvedAt;
    private String resolvedBy;
    private String resolutionNote;
    private Long orderId;

    @Data
    public static class Item {
        private String sku;
        private BigDecimal quantity;
        private BigDecimal discountAmount;
        private String note;
    }

    @Data
    public static class Payment {
        private String method;
        private BigDecimal amount;
        private Integer installments;
    }
}
