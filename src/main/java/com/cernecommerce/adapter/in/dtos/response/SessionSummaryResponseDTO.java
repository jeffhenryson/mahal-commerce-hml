package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** PDV-F038 — resumo do fechamento de caixa: total vendido e o detalhamento por forma de pagamento. */
@Data
public class SessionSummaryResponseDTO {

    @Schema(description = "Mesmo detalhamento de /payment-totals.")
    private List<PaymentTotalResponseDTO> totals;

    @Schema(description = "Total vendido e recebido: soma dos netAmount de todas as formas (dinheiro já sem "
            + "troco, estornos descontados, quitações de marcado incluídas).")
    private BigDecimal totalReceived;

    @Schema(description = "Marcado (fiado, CRM-F010) vendido na sessão. Fora de totalReceived: o dinheiro "
            + "ainda não entrou.")
    private BigDecimal totalOnAccount;

    @Schema(description = "Quitações de marcado recebidas em DINHEIRO nesta sessão. Já estão no esperado da "
            + "gaveta (e em totals[DINHEIRO].receivableReceived).")
    private BigDecimal receivableCashReceived;

    @Schema(description = "Quitações de marcado lançadas nesta sessão e estornadas depois (todos os métodos). "
            + "Fora do esperado e de totalReceived.")
    private BigDecimal receivableReversed;
}
