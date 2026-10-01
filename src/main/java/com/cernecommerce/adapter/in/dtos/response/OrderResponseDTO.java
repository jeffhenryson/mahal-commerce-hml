package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class OrderResponseDTO {

    private Long id;

    @Schema(description = "Numeração de sequência própria, emitida na conclusão. Pedidos "
            + "anteriores à V65 têm prefixo LEG-.", example = "000001000")
    private String orderNumber;

    @Schema(description = "BALCAO, MESA ou MARKETPLACE.")
    private String channel;

    @Schema(description = "Comanda que originou o pedido. Preenchido só em channel = MESA.")
    private Long comandaId;

    @Schema(description = "Rótulo da mesa, congelado no fechamento. Preenchido só em channel = MESA.")
    private String tableLabel;

    private String status;

    @Schema(description = "Nulo em venda anônima de balcão.")
    private Long customerId;

    @Schema(description = "Nulo em pedido de marketplace.")
    private Long sessionId;

    private String warehouseCode;

    @Schema(description = "Bruto: soma de quantidade x preço unitário dos itens.")
    private BigDecimal grossAmount;

    private BigDecimal discountAmount;

    @Schema(description = "Cashback usado como abatimento. Sempre zero enquanto CRM-F003 não existir.")
    private BigDecimal cashbackRedeemed;

    @Schema(description = "Líquido a pagar: bruto - desconto - cashback resgatado.")
    private BigDecimal netAmount;

    @Schema(description = "Troco. Não é linha de pagamento.")
    private BigDecimal changeAmount;

    @Schema(description = "Taxa de serviço da mesa (PDV-F015). FORA do netAmount de propósito: o "
            + "líquido é a receita da mercadoria, a taxa é repasse ao garçom — somá-la ali inflaria "
            + "receita e margem. Zero em toda venda que não veio de mesa.")
    private BigDecimal serviceFeeAmount;

    @Schema(description = "O que o cliente efetivamente paga: netAmount + serviceFeeAmount + "
            + "delivery.fee (PDV-F015, PDV-F022). É contra este valor que o pagamento é validado e o "
            + "troco calculado. No balcão sem entrega coincide com netAmount.")
    private BigDecimal totalPayable;


    private String cancelReason;
    private Instant createdAt;
    private Instant paidAt;
    private Instant concludedAt;
    private Instant cancelledAt;

    @Schema(description = "Instante da reserva para retirada depois (PDV-F008), quando status é ou "
            + "já foi RESERVADO. Permanece preenchido após a retirada (RESERVADO -> CONCLUIDO).")
    private Instant reservedAt;

    @Schema(description = "PDV-F022 — entrega ou retirada; null quando a venda não tem. delivery.fee já está em totalPayable.")
    private DeliveryResponseDTO delivery;

    private List<OrderItemResponseDTO> items;

    @Schema(description = "Pagamentos do pedido (PDV-F006). Ausente/nulo em listagens — só vem "
            + "preenchido em GET /pdv/sales/{id} e nas respostas de registro/liquidação, para não "
            + "gerar uma consulta extra por linha em toda paginação.")
    private List<OrderPaymentResponseDTO> payments;

    @Schema(description = "URL de checkout hospedada pelo gateway (ECM-F004) — para onde o "
            + "cliente é redirecionado para pagar. Ausente/nulo em toda resposta exceto a de "
            + "POST /shop/checkout, que é o único momento em que ela existe.")
    private String checkoutUrl;

    @Schema(description = "CRM-F010 — PAGO, PENDENTE (marcado sem nenhuma quitação) ou PARCIAL. Só nas "
            + "respostas que trazem payments.")
    private String paymentStatus;

    @Schema(description = "CRM-F010 — o marcado do pedido, se houver. Só em GET /orders/{id}.")
    private Long receivableId;
}
