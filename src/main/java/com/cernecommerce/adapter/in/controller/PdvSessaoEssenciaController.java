package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.SessionEssenceFlagsRequest;
import com.cernecommerce.adapter.in.dtos.response.SessionEssenceResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDV-F061 — a aba "Essências" do cardápio de sessão: em quais modalidades novas (compra de essência,
 * itens pessoais) cada sabor pode ser vendido.
 */
@RestController
@Tag(name = "PDV (Sessão)")
@SecurityRequirement(name = "bearerAuth")
public class PdvSessaoEssenciaController {

    private final EstoqueUseCase estoqueUseCase;
    private final ApplicationEventPublisher publisher;

    public PdvSessaoEssenciaController(EstoqueUseCase estoqueUseCase, ApplicationEventPublisher publisher) {
        this.estoqueUseCase = estoqueUseCase;
        this.publisher = publisher;
    }

    @Operation(summary = "Essências de sessão e as modalidades em que valem",
            description = "PDV-F061 — as essências de sessão ativas que se podem vender, uma linha por SKU "
                    + "vendável (o mesmo critério do catálogo do PDV), com faixaId e as marcações "
                    + "compraEssencia/itensPessoais (na variação, o que ela não marcou vem do produto).")
    @GetMapping("/pdv/sessao/essencias")
    @PreAuthorize("hasAnyAuthority('PDV_READ','PDV_SESSAO_MANAGE')")
    public ResponseEntity<List<SessionEssenceResponseDTO>> list() {
        return ResponseEntity.ok(estoqueUseCase.listSessionEssences().stream()
                .map(SessionEssenceResponseDTO::of).toList());
    }

    @Operation(summary = "Marca as modalidades de uma essência",
            description = "PDV-F061 — SKU de variação grava na variação; o SKU do próprio produto grava no "
                    + "produto. Campo omitido mantém o atual.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Gravado; devolve a linha atualizada"),
            @ApiResponse(responseCode = "404", description = "SKU desconhecido", content = @Content),
            @ApiResponse(responseCode = "400", description = "O produto não é vendido por sessão (NOT_A_PACKAGED_SESSION_PRODUCT)", content = @Content)
    })
    @PatchMapping("/pdv/sessao/essencias/{sku}")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionEssenceResponseDTO> setFlags(@PathVariable String sku,
            @RequestBody SessionEssenceFlagsRequest request, Authentication authentication) {
        EstoqueUseCase.SessionEssence saved = estoqueUseCase.setSessionFlags(sku, request.getCompraEssencia(),
                request.getItensPessoais());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("target", "essencia");
        payload.put("id", saved.sku());
        payload.put("compraEssencia", saved.compraEssencia());
        payload.put("itensPessoais", saved.itensPessoais());
        publisher.publishEvent(AuditEvent.of(EventType.SESSION_MENU_CHANGED, authentication.getName(), payload));
        return ResponseEntity.ok(SessionEssenceResponseDTO.of(saved));
    }
}
