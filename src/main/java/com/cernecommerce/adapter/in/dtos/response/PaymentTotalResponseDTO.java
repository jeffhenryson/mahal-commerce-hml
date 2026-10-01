package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Total recebido por forma de pagamento numa sessão de caixa. Só {@code DINHEIRO} entra na
 * conferência da gaveta; débito, crédito e PIX se conferem contra a adquirente.
 */
@Data
public class PaymentTotalResponseDTO {

    @Schema(description = "DINHEIRO, DEBITO, CREDITO ou PIX.")
    private String method;

    @Schema(description = "Soma dos pagamentos CAPTURED deste método na sessão. Zero se o método "
            + "não foi usado.")
    private BigDecimal amount;

    @Schema(description = "PDV-F026 — soma dos estornos (REFUNDED) deste método na sessão.")
    private BigDecimal refundedAmount;

    @Schema(description = "PDV-F026 — troco devolvido nas vendas da sessão. Só em DINHEIRO; zero nos demais.")
    private BigDecimal changeAmount;

    @Schema(description = "PDV-F026 — o que ficou: amount + receivableReceived - refundedAmount - changeAmount.")
    private BigDecimal netAmount;

    @Schema(description = "CRM-F010 — quitação de marcado recebida nesta sessão, neste método. Separada "
            + "de amount (venda) para o relatório do caixa; já líquida do troco e somada em netAmount. MARCADO em si nunca "
            + "aparece aqui: é dinheiro que não entrou.")
    private BigDecimal receivableReceived;
}
