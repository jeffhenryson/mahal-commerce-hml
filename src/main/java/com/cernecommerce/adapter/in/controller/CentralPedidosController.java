package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.CentralPedidosDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.CentralPedidosConfigDTO;
import com.cernecommerce.adapter.in.dtos.response.CentralPedidosIndicadoresResponseDTO;
import com.cernecommerce.core.ports.in.CentralPedidosUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Central de Pedidos (ECM-F013): configuração do recebimento e indicadores do dia. */
@RestController
@RequestMapping("/central-pedidos")
@Tag(name = "Central de Pedidos", description = "Recebimento de pedidos do app e do QR da mesa")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class CentralPedidosController {

    private final CentralPedidosUseCase useCase;

    public CentralPedidosController(CentralPedidosUseCase useCase) {
        this.useCase = useCase;
    }

    @Operation(summary = "Configuração do recebimento",
            description = "Sempre 200: enquanto a loja nunca salvou, devolve o padrão.")
    @GetMapping("/config")
    @PreAuthorize("hasAnyAuthority('ONLINE_ORDER_READ','ONLINE_CONFIG_MANAGE')")
    public ResponseEntity<CentralPedidosConfigDTO> config() {
        return ResponseEntity.ok(CentralPedidosDTOConverter.toDto(useCase.config()));
    }

    @Operation(summary = "Substitui a configuração do recebimento",
            description = "Substitui tudo. Regra entre campos violada: 400 INVALID_CENTRAL_CONFIG, com todos os "
                    + "problemas na mensagem.")
    @PutMapping("/config")
    @PreAuthorize("hasAuthority('ONLINE_CONFIG_MANAGE')")
    public ResponseEntity<CentralPedidosConfigDTO> salvarConfig(@Valid @RequestBody CentralPedidosConfigDTO body,
            Authentication authentication) {
        return ResponseEntity.ok(CentralPedidosDTOConverter.toDto(
                useCase.salvarConfig(CentralPedidosDTOConverter.toDomain(body), authentication.getName())));
    }

    @Operation(summary = "Indicadores do dia",
            description = "Dia local da loja (America/Sao_Paulo); sem data, hoje. Receita pela regra do "
                    + "totalRevenueNet de /orders/summary.")
    @GetMapping("/indicators")
    @PreAuthorize("hasAuthority('ONLINE_ORDER_READ')")
    public ResponseEntity<CentralPedidosIndicadoresResponseDTO> indicadores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate dia = date != null ? date : LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        return ResponseEntity.ok(CentralPedidosDTOConverter.toDto(useCase.indicadores(dia)));
    }
}
