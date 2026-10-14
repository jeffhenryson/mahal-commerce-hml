package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.CashSessionActivityRequest;
import com.cernecommerce.adapter.in.dtos.response.CashSessionActivityResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.pdv.CashSessionActivity;
import com.cernecommerce.core.ports.in.CashSessionActivityUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDV-F047 — atividades do operador na sessão de caixa (varrer, passar pano, trocar dinheiro...).
 * Controller à parte do {@code PdvController}: o diário do turno não mexe em venda nem em gaveta.
 */
@RestController
@RequestMapping("/pdv/sessions/{id}/activities")
@Tag(name = "PDV (Vendas Balcão)", description = "Frente de caixa")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class PdvSessionActivityController {

    private static final String CLOSE_ANY_AUTHORITY = "PDV_SESSION_CLOSE_ANY";

    private final CashSessionActivityUseCase activityUseCase;
    private final ApplicationEventPublisher publisher;

    public PdvSessionActivityController(CashSessionActivityUseCase activityUseCase,
            ApplicationEventPublisher publisher) {
        this.activityUseCase = activityUseCase;
        this.publisher = publisher;
    }

    @Operation(summary = "Anota uma atividade na sessão de caixa (PDV-F047)",
            description = "Só com a sessão aberta e do próprio operador; com PDV_SESSION_CLOSE_ANY, em qualquer "
                    + "sessão. OUTRO exige note; note tem até 280 caracteres. O username vem do token.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Anotada",
                    content = @Content(schema = @Schema(implementation = CashSessionActivityResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "OUTRO sem note, ou note longa demais", content = @Content),
            @ApiResponse(responseCode = "403", description = "SESSION_NOT_OWNED — sessão de outro operador",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "CASH_REGISTER_SESSION_CLOSED — sessão fechada",
                    content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<CashSessionActivityResponseDTO> register(@PathVariable("id") Long sessionId,
            @Valid @RequestBody CashSessionActivityRequest request, Authentication authentication) {
        CashSessionActivity activity = activityUseCase.register(sessionId, request.getType(), request.getNote(),
                authentication.getName(), AuthUtils.hasAuthority(authentication, CLOSE_ANY_AUTHORITY));
        Map<String, Object> details = new HashMap<>();
        details.put("sessionId", sessionId);
        details.put("activityId", activity.id());
        details.put("type", activity.type().name());
        // AuditEvent.of copia com Map.copyOf, que recusa valor nulo.
        if (activity.note() != null) {
            details.put("note", activity.note());
        }
        publisher.publishEvent(AuditEvent.of(EventType.CASH_SESSION_ACTIVITY_REGISTERED, authentication.getName(),
                details));
        return ResponseEntity.status(201).body(CashSessionActivityResponseDTO.from(activity));
    }

    @Operation(summary = "Lista as atividades da sessão de caixa, mais recentes primeiro (PDV-F047)")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('PDV_READ', 'ORDER_READ')")
    public ResponseEntity<List<CashSessionActivityResponseDTO>> list(@PathVariable("id") Long sessionId) {
        return ResponseEntity.ok(activityUseCase.list(sessionId).stream()
                .map(CashSessionActivityResponseDTO::from).toList());
    }
}
