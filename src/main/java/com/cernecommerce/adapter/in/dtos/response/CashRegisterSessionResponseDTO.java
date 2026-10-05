package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Sessão de caixa na borda HTTP (PDV-C002).
 *
 * <p>Existe para o record de domínio {@code CashRegisterSession} não vazar direto na API, que era
 * o que {@code GET /pdv/sessions} fazia antes.</p>
 */
@Data
public class CashRegisterSessionResponseDTO {

    private Long id;
    private String operator;
    private Instant openedAt;

    @Schema(description = "Fundo de troco com que o caixa foi aberto.")
    private BigDecimal openingAmount;

    @Schema(description = "Depósito de onde sai a mercadoria vendida neste caixa.")
    private String warehouseCode;

    private Instant closedAt;

    @Schema(description = "Quem fez a conferência — nem sempre é o operador do caixa.")
    private String closedBy;

    @Schema(description = "Motivo/observação registrado no fechamento, quando informado.")
    private String closingNotes;

    @Schema(description = "O que deveria haver na gaveta: abertura + vendas − sangrias + suprimentos. "
            + "Só existe depois do fechamento.")
    private BigDecimal expectedAmount;

    @Schema(description = "O que foi contado de fato no fechamento.")
    private BigDecimal countedAmount;

    @Schema(description = "contado − esperado. Negativo é falta na gaveta, e é um número legítimo: "
            + "divergência não impede o fechamento, apenas fica registrada.")
    private BigDecimal differenceAmount;

    @Schema(description = "Indica se o fechamento encontrou diferença.")
    private boolean diverges;

    private String status;

    @Schema(description = "PDV-F037 — horas inteiras desde a abertura (até o fechamento, se fechada).")
    private long horasAberto;

    @Schema(description = "PDV-F037 — caixa aberto há 12h ou mais: sugerir fechar e abrir outro. Só aviso, "
            + "nada é bloqueado.")
    private boolean fechamentoSugerido;
}
