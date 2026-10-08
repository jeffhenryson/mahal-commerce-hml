package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/** Um marcado (CRM-F010): a conta a receber de um pedido BALCAO ou MESA. */
@Data
public class ReceivableResponseDTO {

    private Long id;
    private Long customerId;
    private String customerName;
    private Long orderId;
    private String orderNumber;
    private Long comandaId;

    @Schema(description = "BALCAO (sem comanda) ou MESA (com comanda): o limite em que o marcado conta.")
    private String channel;
    private String tableLabel;

    @Schema(description = "Todos os itens do pedido no momento do marcar — mesmo quando só parte foi marcada.")
    private List<Item> items;

    @Schema(description = "Valor marcado (a parte MARCADO do pedido).")
    private BigDecimal amount;
    private BigDecimal amountPaid;
    private BigDecimal amountOpen;
    private LocalDate dueDate;

    @Schema(description = "ABERTO, PARCIAL, QUITADO, VENCIDO ou CANCELADO.")
    private String status;

    @Schema(description = "Dias desde o vencimento, se em aberto e vencido; 0 nos demais.")
    private long daysOverdue;
    private Instant createdAt;
    private String createdBy;
    private Instant settledAt;
    private String cancelReason;
    private String cancelledBy;
    private Instant cancelledAt;
    private List<Payment> payments;

    @Data
    public static class Item {
        private Long orderItemId;
        private String sku;
        private String productName;
        private BigDecimal quantity;
        private BigDecimal subtotal;

        @Schema(description = "SESSAO ou ROSH_EXTRA na sessão de narguilé; null em produto.")
        private String mode;
    }

    @Data
    public static class Payment {
        private Long id;
        private Long batchId;
        private BigDecimal amount;
        private String method;
        private Integer installments;
        private String channel;
        private String provider;
        private Long cashSessionId;
        private String receivedBy;
        private Instant receivedAt;
    }
}
