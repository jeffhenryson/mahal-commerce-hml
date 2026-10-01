package com.cernecommerce.adapter.in.dtos.request;

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
    @Schema(description = "Limite do \"Marcar\". null volta ao limite padrão da loja "
            + "(pdv.on-account.default-credit-limit).", example = "300.00", nullable = true)
    private BigDecimal creditLimit;
}
