package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.logistica.Shipment;
import com.cernecommerce.core.ports.in.LogisticaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller stub do domínio <b>logistica</b>.
 *
 * <p>Esqueleto: demonstra o fluxo Controller → {@link LogisticaUseCase} → service.
 * Endpoints previstos (TODO): expedição/status, clique e retire, rotas de motoboy,
 * transportadora. Endpoint atual requer {@code LOGISTICA_READ}.</p>
 */
@RestController
@RequestMapping("/logistica")
@Tag(name = "Logística", description = "Expedição, clique e retire e rotas — esqueleto, implementação pendente")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class LogisticaController {

    private final LogisticaUseCase logisticaUseCase;

    public LogisticaController(LogisticaUseCase logisticaUseCase) {
        this.logisticaUseCase = logisticaUseCase;
    }

    /**
     * @deprecated Sem persistência (não há tabela {@code shipment}) e sem consumidor: a entrega do
     * PDV/marketplace vive em {@code OrderDelivery} e é acompanhada pela Central de Pedidos. Mantido
     * só para não quebrar o client gerado; será removido junto com {@link LogisticaUseCase}.
     */
    @Deprecated(since = "2026-11")
    @Operation(summary = "Lista expedições (DEPRECADO — sempre página vazia)", deprecated = true,
            description = "Não implementado e sem plano de implementação. Use a Central de Pedidos (/pdv/app-orders) e a entrega do pedido.")
    @GetMapping("/shipments")
    @PreAuthorize("hasAuthority('LOGISTICA_READ')")
    public ResponseEntity<PageResult<Shipment>> listShipments(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(logisticaUseCase.listShipments(page, size));
    }
}
