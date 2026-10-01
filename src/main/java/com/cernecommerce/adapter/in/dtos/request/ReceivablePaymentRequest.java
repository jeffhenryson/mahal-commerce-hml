package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/** Corpo do {@code POST /receivables/payments} — receber marcado (CRM-F010). */
@Data
public class ReceivablePaymentRequest {

    @NotNull
    @Schema(description = "Cliente que está pagando.", example = "123")
    private Long customerId;

    @NotEmpty
    @Valid
    @Schema(description = "DINHEIRO, DEBITO, CREDITO ou PIX. Só DINHEIRO pode passar do saldo (vira troco).")
    private List<SalePaymentRequest> payments;

    @Schema(description = "Abate só nestes, na ordem dada. Ausente: abate do mais antigo (FIFO por vencimento).",
            example = "[9, 12]")
    private List<Long> receivableIds;
}
