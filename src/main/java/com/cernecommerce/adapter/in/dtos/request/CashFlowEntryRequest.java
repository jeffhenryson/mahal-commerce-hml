package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.financeiro.CashFlowCategory;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry.Direction;
import com.cernecommerce.core.domain.model.financeiro.LinkedEntityType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "Novo lançamento de fluxo de caixa. Nasce com status PREVISTO.")
public class CashFlowEntryRequest {

    @NotBlank
    @Size(max = 255)
    private String description;

    @Size(max = 150)
    private String entityName;

    @NotNull
    private CashFlowCategory category;

    @NotNull
    private Direction direction;

    @NotNull
    @DecimalMin(value = "0.01")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    @NotNull
    private LocalDate dueDate;

    private LinkedEntityType linkedEntityType;

    private Long linkedEntityId;
}
