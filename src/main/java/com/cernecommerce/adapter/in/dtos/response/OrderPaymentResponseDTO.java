package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pagamento.PaymentChannel;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class OrderPaymentResponseDTO {
    private Long id;

    @Schema(description = "DINHEIRO, DEBITO, CREDITO, PIX ou MARCADO (CRM-F010).")
    private String method;

    private BigDecimal amount;

    @Schema(description = "CAPTURED soma; CORRECTED (PDV-F027) é a linha lançada na forma errada e "
            + "aposentada por uma correção — devolvida como lastro, mas fora de qualquer total.")
    private String status;

    @Schema(description = "Só em CREDITO.")
    private Integer installments;

    private Instant capturedAt;
    private Instant createdAt;

    @Schema(description = "MAQUININHA ou LINK (PDV-F025). Nulo em DINHEIRO e em pagamento antigo.")
    private PaymentChannel channel;

    @Schema(description = "CIELO ou INFINITYPAY (PDV-F025). Só junto com channel.")
    private PaymentProvider provider;

    @Schema(description = "PDV-F027 — em CORRECTED, a correção que aposentou a linha; nas demais, a "
            + "correção que a lançou (null se veio da venda).")
    private Long correctionId;

    @Schema(description = "PDV-F027 — a correção que lançou esta linha; null se veio da venda.")
    private Long originCorrectionId;

    @Schema(description = "PDV-F027 — só em CORRECTED.")
    private Instant correctedAt;

    @Schema(description = "PDV-F027 — só em CORRECTED.")
    private String correctedBy;

    @Schema(description = "CRM-F010 — vencimento da linha MARCADO (status ON_ACCOUNT). O recibo mostra "
            + "\"Marcado — vence em dd/mm\".")
    private java.time.LocalDate dueDate;
}
