package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.financeiro.CashFlowCategory;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry.Direction;
import com.cernecommerce.core.domain.model.financeiro.CashFlowStatus;
import com.cernecommerce.core.domain.model.financeiro.LinkedEntityType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Alteração parcial de um lançamento. Campo ausente ou nulo significa <b>não mexer</b> — mesma
 * semântica de PATCH usada em {@code CategoryPatchRequest}. Marcar {@code status: PAGO} sem
 * {@code paymentDate} grava a data corrente no servidor.
 */
@Data
public class CashFlowEntryPatchRequest {

    @Size(min = 1, max = 255)
    private String description;

    @Size(max = 150)
    private String entityName;

    private CashFlowCategory category;

    private Direction direction;

    @DecimalMin(value = "0.01")
    @Digits(integer = 12, fraction = 2)
    private BigDecimal amount;

    private LocalDate dueDate;

    private CashFlowStatus status;

    private LocalDate paymentDate;

    private LinkedEntityType linkedEntityType;

    private Long linkedEntityId;
}
