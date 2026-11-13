package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** "Valor recebido por fora": Pix ou cartão que caiu sem passar pelo sistema. */
@Data
public class OffSystemReceiptRequest {

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Valor recebido, maior que zero.", example = "35.00")
    private BigDecimal amount;

    @NotNull
    @Schema(description = "PIX, DEBITO ou CREDITO. Outras formas dão 400.", example = "PIX")
    private PaymentMethod method;

    @Schema(description = "Operadora da maquininha, se houver. Nulo: caiu direto na conta (ex.: Pix na chave do dono).",
            example = "INFINITYPAY", nullable = true)
    private PaymentProvider provider;

    @Size(max = 120)
    @Schema(description = "Observação livre.", example = "Pix na chave de telefone do dono")
    private String note;
}
