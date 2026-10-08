package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.recebivel.OnAccountChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import lombok.Data;

/** Corpo do {@code PUT /crm/customers/{id}/credit-limit} (CRM-F010). */
@Data
public class CreditLimitRequest {

    @DecimalMin("0.00")
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Limite do \"Marcar\". null apaga a linha: no canal, volta ao padrão "
            + "(pdv.on-account.default-credit-limit.<canal>, depois pdv.on-account.default-credit-limit); "
            + "no total, o cliente fica sem teto.", example = "300.00", nullable = true)
    private BigDecimal creditLimit;

    @Schema(description = "BALCAO ou MESA: o limite daquele canal. Ausente: o teto total (a soma dos dois "
            + "canais), como antes do limite por canal. Não mexe nas outras linhas.", nullable = true)
    private OnAccountChannel channel;
}
