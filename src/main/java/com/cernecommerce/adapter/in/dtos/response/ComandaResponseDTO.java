package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class ComandaResponseDTO {

    private Long id;
    private Long sessionId;
    private String warehouseCode;
    private String tableOrCustomerLabel;

    @Schema(description = "Cliente do CRM vinculado à mesa (PDV-F010). Nulo em mesa sem vínculo.")
    private Long customerId;

    @Schema(description = "Nome do cliente vinculado, resolvido no CRM. Nulo em mesa sem vínculo.")
    private String customerName;
    private String status;
    private List<ComandaItemResponseDTO> items;

    @Schema(description = "Soma dos subtotais dos itens já lançados.")
    private BigDecimal runningTotal;

    @Schema(description = "Preenchido só depois do fechamento — o pedido gerado a partir dos itens.")
    private Long orderId;

    private String openedBy;
    private Instant openedAt;
    private Instant closedAt;

    // ── PDV-F029: histórico — preenchidos em GET /pdv/comandas/history e /{id} ──────────────

    @Schema(description = "Quem fechou, finalizou ou cancelou a mesa. \"system\" na varredura automática.")
    private String closedBy;

    @Schema(description = "Minutos entre abertura e encerramento. Nulo enquanto ABERTA.")
    private Long durationMinutes;

    @Schema(description = "Motivo do cancelamento, se CANCELADA e informado.")
    private String cancelReason;

    @Schema(description = "Pedidos MESA gerados por /close, inclusive os parciais.")
    private List<Long> orderIds;

    @Schema(description = "Soma do totalPayable dos pedidos não reembolsados.")
    private BigDecimal totalPaid;

    private BigDecimal serviceFeeTotal;
    private BigDecimal discountTotal;

    @Schema(description = "Custo das linhas de cortesia (quantidade × custo congelado). A cortesia é gravada a preço zero, então o valor de venda não existe.")
    private BigDecimal courtesyTotal;

    @Schema(description = "Sessões de narguilé (linhas SESSAO) da mesa.")
    private Integer sessionsCount;

    @Schema(description = "Só em GET /pdv/comandas/{id}: os pedidos gerados, com os pagamentos.")
    private List<ComandaOrder> orders;

    @Data
    public static class ComandaOrder {
        private Long id;
        private String orderNumber;
        private Instant closedAt;
        private BigDecimal totalPayable;
        private String status;
        private List<OrderPaymentResponseDTO> payments;
    }
}
