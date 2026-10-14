package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pdv.CashSessionActivity;
import com.cernecommerce.core.domain.model.pdv.CashSessionActivityType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** PDV-F047 — atividade anotada na sessão de caixa. */
@Schema(description = "PDV-F047 — atividade do operador na sessão de caixa")
public record CashSessionActivityResponseDTO(Long id, CashSessionActivityType type, String note, String username,
        Instant createdAt) {

    public static CashSessionActivityResponseDTO from(CashSessionActivity activity) {
        return new CashSessionActivityResponseDTO(activity.id(), activity.type(), activity.note(),
                activity.username(), activity.createdAt());
    }
}
