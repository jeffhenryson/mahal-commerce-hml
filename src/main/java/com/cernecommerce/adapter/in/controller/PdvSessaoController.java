package com.cernecommerce.adapter.in.controller;

import org.springframework.security.core.GrantedAuthority;

import com.cernecommerce.core.domain.exception.pdv.SessionEssenceRequiredException;

import com.cernecommerce.core.domain.exception.pdv.CourtesyNotAllowedException;
import com.cernecommerce.core.domain.exception.pdv.SessionPayLaterNotAllowedException;

import com.cernecommerce.adapter.in.converter.ComandaDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.AddRoshExtraRequest;
import com.cernecommerce.adapter.in.dtos.request.RepeatSessionRequest;
import com.cernecommerce.adapter.in.dtos.request.SessionAddonRequest;
import com.cernecommerce.adapter.in.dtos.response.SessionAddonResponseDTO;
import com.cernecommerce.adapter.in.dtos.request.UpdateSessionStatusRequest;
import com.cernecommerce.adapter.in.dtos.request.AddSessionRequest;
import com.cernecommerce.adapter.in.dtos.request.SessionAssetTypeRequest;
import com.cernecommerce.adapter.in.dtos.request.SessionSettingsRequest;
import com.cernecommerce.adapter.in.dtos.request.SessionTierRequest;
import com.cernecommerce.adapter.in.dtos.response.ComandaResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SessionAssetTypeResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SessionMenuResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SessionSettingsResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SessionTierResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.SessionMenuUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cardápio de sessão da mesa (PDV-F021): o cadastro (faixas, utensílios, configuração — admin) e o
 * lançamento na comanda (sessão e 2º rosh — atendente). Controller próprio, como o do kit montável,
 * porque metade dele é cadastro e não mesa.
 */
@RestController
@Tag(name = "PDV (Sessão)", description = "Cardápio de sessão de narguilé: faixas, utensílios e duplo rosh")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class PdvSessaoController {

    private final SessionMenuUseCase sessionMenuUseCase;
    private final ComandaUseCase comandaUseCase;
    private final CrmUseCase crmUseCase;
    private final ComandaDTOConverter comandaConverter;
    private final ApplicationEventPublisher publisher;

    public PdvSessaoController(SessionMenuUseCase sessionMenuUseCase, ComandaUseCase comandaUseCase,
            CrmUseCase crmUseCase, ComandaDTOConverter comandaConverter, ApplicationEventPublisher publisher) {
        this.sessionMenuUseCase = sessionMenuUseCase;
        this.comandaUseCase = comandaUseCase;
        this.crmUseCase = crmUseCase;
        this.comandaConverter = comandaConverter;
        this.publisher = publisher;
    }

    // ── Mesa (atendente) ──────────────────────────────────────────────────────────────────────

    @Operation(summary = "Cardápio de sessão para a mesa",
            description = "Faixas ativas, utensílios com quantos estão livres agora, configuração "
                    + "(upgrade de vaso, dias de duplo rosh) e se hoje é dia de duplo rosh.")
    @GetMapping("/pdv/sessao/cardapio")
    @PreAuthorize("hasAnyAuthority('PDV_COMANDA_MANAGE', 'PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionMenuResponseDTO> menu() {
        return ResponseEntity.ok(SessionMenuResponseDTO.of(sessionMenuUseCase.getMenu()));
    }

    @Operation(summary = "Lança uma sessão do cardápio na mesa",
            description = "Preço = faixa (+ upgrade se vasoGrande) + Σ adicionais, resolvido pelo servidor. "
                    + "A essência vai na nota da linha; o carvão é só registro. Aloca vaso e utensílios "
                    + "inclusos; não move estoque. PDV-F027: a sessão nasce AGUARDANDO_PAGAMENTO e vai a "
                    + "PREPARANDO quando é paga (close com itemIds); a mesa aceita sessões em paralelo, "
                    + "limitadas ao utensílio livre. modo=DUPLO cria também o 2º rosh a R$ 0 ligado à "
                    + "sessão, em NA_FILA, na mesma transação — para pagar a sessão, mande os dois ids em "
                    + "itemIds do close. PDV-F034: pagarNoFinal=true leva a sessão direto a PREPARANDO, "
                    + "a receber na conta (o 2º rosh herda), e exige PDV_SESSION_PAY_LATER.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lançada, com a comanda atualizada"),
            @ApiResponse(responseCode = "400", description = "Essência vazia, ou DUPLO sem essenciaRosh (SESSION_ESSENCE_REQUIRED)", content = @Content),
            @ApiResponse(responseCode = "403", description = "DUPLO sem PDV_COMANDA_COURTESY (COURTESY_NOT_ALLOWED, PDV-C025): o 2º rosh do duplo é cortesia; pagarNoFinal sem PDV_SESSION_PAY_LATER (SESSION_PAY_LATER_NOT_ALLOWED, PDV-F034)", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comanda, faixa (SESSION_TIER_NOT_FOUND) ou adicional (SESSION_ADDON_NOT_FOUND) não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não aberta, sem utensílio livre (SESSION_ASSET_UNAVAILABLE) ou vaso não configurado (SESSION_MENU_CONFLICT)", content = @Content)
    })
    @PostMapping("/pdv/comandas/{id}/sessoes")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> addSession(@PathVariable("id") Long comandaId,
            @Valid @RequestBody AddSessionRequest request, Authentication authentication) {
        requirePayLaterAllowed(request.isPagarNoFinal(), authentication);
        requireDuploAllowed(request.isDuplo(), request.getEssenciaRosh(), authentication);
        Comanda comanda = comandaUseCase.addSession(comandaId, new ComandaUseCase.AddSessionCommand(
                request.getTierId(), request.getEssencia(), request.isVasoGrande(), request.getCarvao(),
                request.getAdicionalIds(), request.isDuplo(), request.getEssenciaRosh(), request.getTierIdRosh(),
                request.isPagarNoFinal()),
                authentication.getName());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("comandaId", comandaId);
        payload.put("tierId", request.getTierId());
        payload.put("vasoGrande", request.isVasoGrande());
        payload.put("duplo", request.isDuplo());
        payload.put("pagarNoFinal", request.isPagarNoFinal());
        if (request.getAdicionalIds() != null && !request.getAdicionalIds().isEmpty()) {
            payload.put("adicionalIds", request.getAdicionalIds());
        }
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_SESSION_ADDED, authentication.getName(), payload));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(comanda));
    }

    @Operation(summary = "Repete uma sessão da mesa (mesma configuração)",
            description = "PDV-F027 — nova sessão com a faixa, o vaso, o carvão e os adicionais da sessão "
                    + "{itemId}, pelo preço atual do cardápio. essencia nula repete o sabor. A origem pode "
                    + "estar recolhida: os utensílios são reservados de novo. Nasce AGUARDANDO_PAGAMENTO, "
                    + "como no lançamento normal.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lançada, com a comanda atualizada"),
            @ApiResponse(responseCode = "400", description = "DUPLO sem essenciaRosh (SESSION_ESSENCE_REQUIRED)", content = @Content),
            @ApiResponse(responseCode = "403", description = "DUPLO sem PDV_COMANDA_COURTESY (COURTESY_NOT_ALLOWED, PDV-C025); pagarNoFinal sem PDV_SESSION_PAY_LATER (SESSION_PAY_LATER_NOT_ALLOWED, PDV-F034)", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comanda, faixa (SESSION_TIER_NOT_FOUND) ou adicional (SESSION_ADDON_NOT_FOUND) não encontrado ou inativo", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não aberta, a linha não é uma sessão desta comanda (NOT_A_SESSION_LINE), sem utensílio livre (SESSION_ASSET_UNAVAILABLE) ou vaso não configurado (SESSION_MENU_CONFLICT)", content = @Content)
    })
    @PostMapping("/pdv/comandas/{id}/sessoes/{itemId}/repetir")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> repeatSession(@PathVariable("id") Long comandaId,
            @PathVariable("itemId") Long sourceItemId, @Valid @RequestBody RepeatSessionRequest request,
            Authentication authentication) {
        requirePayLaterAllowed(request.isPagarNoFinal(), authentication);
        requireDuploAllowed(request.isDuplo(), request.getEssenciaRosh(), authentication);
        Comanda comanda = comandaUseCase.repeatSession(comandaId, sourceItemId,
                new ComandaUseCase.RepeatSessionCommand(request.getEssencia(), request.isDuplo(),
                        request.getEssenciaRosh(), request.getTierIdRosh(), request.isPagarNoFinal()),
                authentication.getName());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("comandaId", comandaId);
        payload.put("repeatedFromItemId", sourceItemId);
        payload.put("duplo", request.isDuplo());
        payload.put("pagarNoFinal", request.isPagarNoFinal());
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_SESSION_ADDED, authentication.getName(), payload));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(comanda));
    }

    @Operation(summary = "Lança o 2º rosh de uma sessão (duplo rosh)",
            description = "Nova essência, mesmos utensílios, ligado à sessão (fecha junto). De graça no "
                    + "primeiro rosh extra da sessão quando a mesa foi aberta em dia de duplo rosh; "
                    + "pelo preço da faixa nos demais casos.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lançado, com a comanda atualizada"),
            @ApiResponse(responseCode = "404", description = "Comanda ou faixa não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "A linha não é uma sessão aberta desta comanda (NOT_A_SESSION_LINE)", content = @Content)
    })
    @PostMapping("/pdv/comandas/{id}/sessoes/{itemId}/rosh")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> addRoshExtra(@PathVariable("id") Long comandaId,
            @PathVariable("itemId") Long sessionItemId, @Valid @RequestBody AddRoshExtraRequest request,
            Authentication authentication) {
        Comanda comanda = comandaUseCase.addRoshExtra(comandaId, sessionItemId, request.getTierId(),
                request.getEssencia(), authentication.getName());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("comandaId", comandaId);
        payload.put("sessionItemId", sessionItemId);
        if (request.getTierId() != null) {
            payload.put("tierId", request.getTierId());
        }
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_ROSH_EXTRA_ADDED, authentication.getName(), payload));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(comanda));
    }

    @Operation(summary = "Avança o status de uma sessão da mesa",
            description = "PDV-F023 — NA_FILA → PREPARANDO → ENTREGUE → RECOLHIDO (e PREPARANDO → "
                    + "RECOLHIDO). Recolher libera os utensílios quando a sessão e os roshs ligados a "
                    + "ela estão todos recolhidos, e promove o próximo rosh NA_FILA da mesma sessão para "
                    + "PREPARANDO. AGUARDANDO_PAGAMENTO não sai por aqui: quem a promove é o pagamento "
                    + "(PDV-F027).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atualizada, com a comanda"),
            @ApiResponse(responseCode = "404", description = "Comanda ou linha não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não aberta, linha sem status de sessão (NOT_A_SESSION_LINE) transição inválida (INVALID_SESSION_TRANSITION), ou rosh da fila mandado ao preparo antes de pago — a própria linha ainda a cobrar, ou a sessão raiz aguardando pagamento (SESSION_NOT_PAID, PDV-C026); ou sessão paga no final recolhida antes de paga (SESSION_NOT_PAID_FOR_COLLECT, PDV-F040)", content = @Content)
    })
    @PatchMapping("/pdv/comandas/{id}/sessoes/{itemId}/status")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> updateSessionStatus(@PathVariable("id") Long comandaId,
            @PathVariable("itemId") Long itemId, @Valid @RequestBody UpdateSessionStatusRequest request,
            Authentication authentication) {
        Comanda comanda = comandaUseCase.updateSessionStatus(comandaId, itemId, request.getStatus(),
                authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_SESSION_STATUS_CHANGED, authentication.getName(),
                Map.of("comandaId", comandaId, "itemId", itemId, "status", request.getStatus().name())));
        return ResponseEntity.ok(toResponse(comanda));
    }

    // ── Cadastro (admin) ──────────────────────────────────────────────────────────────────────

    @Operation(summary = "Lista as faixas de sessão (ativas e inativas)")
    @GetMapping("/pdv/sessao/faixas")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<List<SessionTierResponseDTO>> listTiers() {
        return ResponseEntity.ok(sessionMenuUseCase.listTiers().stream().map(SessionTierResponseDTO::of).toList());
    }

    @Operation(summary = "Cria uma faixa de sessão")
    @PostMapping("/pdv/sessao/faixas")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionTierResponseDTO> createTier(@Valid @RequestBody SessionTierRequest request,
            Authentication authentication) {
        var tier = sessionMenuUseCase.createTier(request.getNome(), request.getPreco(), request.getMarcas(),
                request.getOrdem() == null ? 0 : request.getOrdem());
        audit(authentication, "faixa", tier.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionTierResponseDTO.of(tier));
    }

    @Operation(summary = "Atualiza uma faixa de sessão",
            description = "Mudar o preço vale para lançamentos novos; linha já lançada mantém o preço congelado.")
    @PutMapping("/pdv/sessao/faixas/{id}")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionTierResponseDTO> updateTier(@PathVariable Long id,
            @Valid @RequestBody SessionTierRequest request, Authentication authentication) {
        var tier = sessionMenuUseCase.updateTier(id, request.getNome(), request.getPreco(), request.getMarcas(),
                request.getOrdem(), request.getAtivo() == null || request.getAtivo());
        audit(authentication, "faixa", id);
        return ResponseEntity.ok(SessionTierResponseDTO.of(tier));
    }

    @Operation(summary = "Lista os adicionais pagos da sessão (ativos e inativos)")
    @GetMapping("/pdv/sessao/adicionais")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<List<SessionAddonResponseDTO>> listAddons() {
        return ResponseEntity.ok(sessionMenuUseCase.listAddons().stream().map(SessionAddonResponseDTO::of).toList());
    }

    @Operation(summary = "Cria um adicional pago da sessão", description = "PDV-F024 — ex.: filtro de gelo.")
    @PostMapping("/pdv/sessao/adicionais")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionAddonResponseDTO> createAddon(@Valid @RequestBody SessionAddonRequest request,
            Authentication authentication) {
        var addon = sessionMenuUseCase.createAddon(request.getNome(), request.getPreco(),
                request.getOrdem() == null ? 0 : request.getOrdem());
        audit(authentication, "adicional", addon.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionAddonResponseDTO.of(addon));
    }

    @Operation(summary = "Atualiza um adicional pago da sessão",
            description = "Mudar o preço vale para lançamentos novos; a linha já lançada guarda o preço da época.")
    @PutMapping("/pdv/sessao/adicionais/{id}")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionAddonResponseDTO> updateAddon(@PathVariable Long id,
            @Valid @RequestBody SessionAddonRequest request, Authentication authentication) {
        var addon = sessionMenuUseCase.updateAddon(id, request.getNome(), request.getPreco(), request.getOrdem(),
                request.getAtivo() == null || request.getAtivo());
        audit(authentication, "adicional", id);
        return ResponseEntity.ok(SessionAddonResponseDTO.of(addon));
    }

    @Operation(summary = "Lista os utensílios da sessão")
    @GetMapping("/pdv/sessao/utensilios")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<List<SessionAssetTypeResponseDTO>> listAssetTypes() {
        return ResponseEntity.ok(sessionMenuUseCase.listAssetTypes().stream()
                .map(SessionAssetTypeResponseDTO::of).toList());
    }

    @Operation(summary = "Cria um tipo de utensílio")
    @PostMapping("/pdv/sessao/utensilios")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionAssetTypeResponseDTO> createAssetType(
            @Valid @RequestBody SessionAssetTypeRequest request, Authentication authentication) {
        if (request.getCodigo() == null || request.getCodigo().isBlank()) {
            throw new IllegalArgumentException("código do utensílio é obrigatório");
        }
        var type = sessionMenuUseCase.createAssetType(request.getCodigo(), request.getNome(),
                request.getQuantidadeTotal() == null ? 0 : request.getQuantidadeTotal(),
                Boolean.TRUE.equals(request.getIncluso()));
        audit(authentication, "utensilio", type.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(SessionAssetTypeResponseDTO.of(type));
    }

    @Operation(summary = "Atualiza um tipo de utensílio (nome, quantidade, incluso, ativo)")
    @PutMapping("/pdv/sessao/utensilios/{id}")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionAssetTypeResponseDTO> updateAssetType(@PathVariable Long id,
            @Valid @RequestBody SessionAssetTypeRequest request, Authentication authentication) {
        var type = sessionMenuUseCase.updateAssetType(id, request.getNome(), request.getQuantidadeTotal(),
                request.getIncluso(), request.getAtivo() == null || request.getAtivo());
        audit(authentication, "utensilio", id);
        return ResponseEntity.ok(SessionAssetTypeResponseDTO.of(type));
    }

    @Operation(summary = "Configuração do cardápio de sessão")
    @GetMapping("/pdv/sessao/config")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionSettingsResponseDTO> getSettings() {
        return ResponseEntity.ok(SessionSettingsResponseDTO.of(sessionMenuUseCase.getSettings()));
    }

    @Operation(summary = "Atualiza a configuração: vasos, preço do upgrade e dias de duplo rosh")
    @PutMapping("/pdv/sessao/config")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SessionSettingsResponseDTO> updateSettings(@Valid @RequestBody SessionSettingsRequest request,
            Authentication authentication) {
        SessionSettings saved = sessionMenuUseCase.updateSettings(new SessionSettings(request.getVasoPadraoCodigo(),
                request.getVasoGrandeCodigo(), request.getUpgradeVasoGrandePreco(), request.getDiasDuploRosh()));
        audit(authentication, "config", 1L);
        return ResponseEntity.ok(SessionSettingsResponseDTO.of(saved));
    }

    private void audit(Authentication authentication, String target, Long id) {
        publisher.publishEvent(AuditEvent.of(EventType.SESSION_MENU_CHANGED, authentication.getName(),
                Map.of("target", target, "id", String.valueOf(id))));
    }

    /** Mesmo enriquecimento de nome de cliente de {@code PdvComandaController}. */
    private ComandaResponseDTO toResponse(Comanda comanda) {
        ComandaResponseDTO dto = comandaConverter.toResponse(comanda);
        if (dto.getCustomerId() != null) {
            dto.setCustomerName(crmUseCase.findCustomerNames(List.of(dto.getCustomerId())).get(dto.getCustomerId()));
        }
        return dto;
    }

    /**
     * PDV-C025 — o 2º rosh do duplo é uma linha de cortesia a R$ 0, em qualquer dia. Cortesia na mesa
     * exige {@code PDV_COMANDA_COURTESY} desde PDV-F010; sem a mesma exigência aqui, qualquer
     * atendente lançava toda sessão como duplo. Decisão do dono (01/10/2026). O sabor do 2º rosh
     * vem depois da permissão: quem não pode lançar não precisa saber o que faltou no corpo.
     */
    private void requireDuploAllowed(boolean duplo, String essenciaRosh, Authentication authentication) {
        if (!duplo) {
            return;
        }
        boolean allowed = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(COURTESY_AUTHORITY::equals);
        if (!allowed) {
            throw new CourtesyNotAllowedException(authentication.getName());
        }
        if (essenciaRosh == null || essenciaRosh.isBlank()) {
            throw new SessionEssenceRequiredException("essenciaRosh");
        }
    }

    /** PDV-F034 — sessão paga no final é risco de calote, e o risco tem dono (decisão de 02/10/2026). */
    private void requirePayLaterAllowed(boolean pagarNoFinal, Authentication authentication) {
        if (pagarNoFinal && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch(PAY_LATER_AUTHORITY::equals)) {
            throw new SessionPayLaterNotAllowedException(authentication.getName());
        }
    }

    private static final String COURTESY_AUTHORITY = "PDV_COMANDA_COURTESY";
    private static final String PAY_LATER_AUTHORITY = "PDV_SESSION_PAY_LATER";
}
