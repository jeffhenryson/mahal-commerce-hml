package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Pedido na visão do administrador — inclui custo e margem por item. */
@Data
public class OrderAdminResponseDTO {

    private Long id;
    private String orderNumber;

    @Schema(description = "Origem do pedido: BALCAO, MESA ou MARKETPLACE. Imutável — não muda "
            + "quando um pedido do app é pago no balcão, e o pedido de mesa NASCE MESA no "
            + "fechamento da comanda.")
    private String channel;

    @Schema(description = "Comanda que originou o pedido. Preenchido só em channel = MESA.")
    private Long comandaId;

    @Schema(description = "Rótulo da mesa, congelado no fechamento (ex.: \"Mesa 4\"). Preenchido "
            + "só em channel = MESA.")
    private String tableLabel;

    private String status;
    private Long customerId;

    @Schema(description = "Nome do cliente, resolvido a partir de customerId. Nulo se o pedido "
            + "não tiver cliente vinculado (venda anônima de balcão).")
    private String customerName;

    @Schema(description = "Caixa que liquidou o pedido. Presente em toda venda de balcão e também "
            + "em pedido do app pago na loja.")
    private Long sessionId;

    private String warehouseCode;
    private BigDecimal grossAmount;
    private BigDecimal discountAmount;
    private BigDecimal cashbackRedeemed;
    private BigDecimal netAmount;
    private BigDecimal changeAmount;

    @Schema(description = "Taxa de serviço da mesa (PDV-F015). FORA do netAmount de propósito: o "
            + "líquido é a receita da mercadoria, a taxa é repasse ao garçom — somá-la ali inflaria "
            + "receita e margem. Zero em toda venda que não veio de mesa.")
    private BigDecimal serviceFeeAmount;

    @Schema(description = "O que o cliente efetivamente paga: netAmount + serviceFeeAmount + "
            + "delivery.fee (PDV-F015, PDV-F022). É contra este valor que o pagamento é validado e o "
            + "troco calculado. No balcão sem entrega coincide com netAmount.")
    private BigDecimal totalPayable;


    @Schema(description = "Soma da margem dos itens. Nula se algum item não tiver custo congelado.")
    private BigDecimal marginAmount;

    private String cancelReason;
    private Instant createdAt;
    private Instant paidAt;
    private Instant concludedAt;
    private Instant cancelledAt;

    @Schema(description = "Instante da reserva para retirada depois (PDV-F008), quando status é ou "
            + "já foi RESERVADO. Permanece preenchido após a retirada (RESERVADO -> CONCLUIDO).")
    private Instant reservedAt;

    @Schema(description = "Instante em que o pedido foi separado (status SEPARADO), quando aplicável.")
    private Instant separatedAt;

    @Schema(description = "Instante em que o pedido saiu para entrega (status ENVIADO), quando aplicável.")
    private Instant shippedAt;

    @Schema(description = "Instante em que o pedido foi entregue (status ENTREGUE), quando aplicável.")
    private Instant deliveredAt;

    @Schema(description = "Estados para os quais este pedido pode transitar agora.")
    private List<String> allowedTransitions;


    @Schema(description = "PDV-F022 — entrega ou retirada; null quando a venda não tem. delivery.fee já está em totalPayable.")
    private DeliveryResponseDTO delivery;
    private List<OrderItemAdminResponseDTO> items;

    @Schema(description = "PDV-F026 — pagamentos do pedido (todas as linhas, inclusive REFUNDED). Só em "
            + "GET /orders/{id}; nulo na listagem.")
    private List<OrderPaymentResponseDTO> payments;

    @Schema(description = "PDV-F026 — métodos com pagamento CAPTURED, sem repetição. Só na listagem "
            + "GET /orders; com changeAmount, diz como o pedido foi pago sem abrir o recibo.")
    private List<PaymentMethod> paymentMethods;

    @Schema(description = "CRM-F010 — PAGO, PENDENTE (marcado sem nenhuma quitação) ou PARCIAL. Só nas "
            + "respostas que trazem payments.")
    private String paymentStatus;

    @Schema(description = "CRM-F010 — o marcado do pedido, se houver. Só em GET /orders/{id}.")
    private Long receivableId;
}
