package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.CashbackRetroactiveRequest;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.cashback.CashbackRetroactivePreview;
import com.cernecommerce.core.ports.in.CashbackRetroactiveUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/crm/customers/{id}/cashback/retroactive")
@Tag(name = "CRM: cashback retroativo", description = "Crédito da taxa global sobre pedidos pagos sem cashback")
@SecurityRequirement(name = "bearerAuth")
public class CashbackRetroactiveController {

    private final CashbackRetroactiveUseCase useCase;
    private final ApplicationEventPublisher publisher;

    public CashbackRetroactiveController(CashbackRetroactiveUseCase useCase, ApplicationEventPublisher publisher) {
        this.useCase = useCase;
        this.publisher = publisher;
    }

    @Operation(summary = "Prévia do crédito retroativo",
            description = "Não grava nada. Sem CPF ou sem taxa global, devolve eligible = false e o motivo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "CUSTOMER_NOT_FOUND", content = @Content)
    })
    @GetMapping("/preview")
    @PreAuthorize("hasAuthority('CASHBACK_ADJUST')")
    public CashbackRetroactivePreview preview(@PathVariable Long id) {
        return useCase.preview(id);
    }

    @Operation(summary = "Credita o cashback retroativo",
            description = "Recalcula no servidor. Crédito novo sem carência, com a validade do programa. Auditado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Creditado"),
            @ApiResponse(responseCode = "404", description = "CUSTOMER_NOT_FOUND", content = @Content),
            @ApiResponse(responseCode = "409", description = "CASHBACK_RETROACTIVE_STALE", content = @Content),
            @ApiResponse(responseCode = "422", description = "CASHBACK_CUSTOMER_NOT_ELIGIBLE, "
                    + "CASHBACK_NOTHING_TO_CREDIT ou CASHBACK_GLOBAL_RATE_MISSING", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAuthority('CASHBACK_ADJUST')")
    public CashbackRetroactiveUseCase.Result grant(@PathVariable Long id,
            @Valid @RequestBody CashbackRetroactiveRequest request, Authentication authentication) {
        CashbackRetroactiveUseCase.Result result = useCase.grant(id, request.expectedCreditAmount());
        publisher.publishEvent(AuditEvent.of(EventType.CUSTOMER_UPDATED, authentication.getName(),
                Map.of("customerId", String.valueOf(id), "action", "CASHBACK_RETROACTIVE_GRANTED",
                        "amount", result.creditedAmount().toPlainString(), "ordersCredited", result.ordersCredited())));
        return result;
    }
}
