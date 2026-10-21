package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.EssenciasDoDiaRequest;
import com.cernecommerce.adapter.in.dtos.request.EssenciasPerfisRequest;
import com.cernecommerce.adapter.in.dtos.request.RecusarSolicitacaoRequest;
import com.cernecommerce.adapter.in.dtos.request.SalaoMesaRequest;
import com.cernecommerce.adapter.in.dtos.response.ChamadoMesaResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.EssenciaDoDiaResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.MesaOcupacaoResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SalaoMesaResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SolicitacaoSessaoResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.salao.SolicitacaoResumo;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;
import com.cernecommerce.core.domain.model.salao.StatusChamado;
import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Autoatendimento do Salão, lado da casa: as mesas com QR e as essências do dia (admin) e a fila de
 * sessões pedidas pelo QR (PDV). O lado do lead é o {@link SalaoPublicoController}.
 */
@RestController
@Tag(name = "Salão (Autoatendimento)", description = "Mesas com QR, essências do dia e sessões pedidas pelo QR")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class SalaoAutoatendimentoController {

    private static final String PAY_LATER_AUTHORITY = "PDV_SESSION_PAY_LATER";

    private final SalaoAutoatendimentoUseCase useCase;
    private final ApplicationEventPublisher publisher;

    public SalaoAutoatendimentoController(SalaoAutoatendimentoUseCase useCase, ApplicationEventPublisher publisher) {
        this.useCase = useCase;
        this.publisher = publisher;
    }

    // ── Mesas ─────────────────────────────────────────────────────────────────────────────────

    @GetMapping("/salao/mesas")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<List<SalaoMesaResponseDTO>> listMesas() {
        return ResponseEntity.ok(useCase.listMesas().stream().map(SalaoMesaResponseDTO::of).toList());
    }

    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada, com o token do QR"),
            @ApiResponse(responseCode = "409", description = "Nome já usado (SALAO_MESA_NOME_DUPLICADO)", content = @Content)
    })
    @PostMapping("/salao/mesas")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SalaoMesaResponseDTO> createMesa(@Valid @RequestBody SalaoMesaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(SalaoMesaResponseDTO.of(useCase.createMesa(request.nome())));
    }

    @PutMapping("/salao/mesas/{id}")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SalaoMesaResponseDTO> updateMesa(@PathVariable Long id,
            @Valid @RequestBody SalaoMesaRequest request) {
        return ResponseEntity.ok(SalaoMesaResponseDTO.of(
                useCase.updateMesa(id, request.nome(), request.ativo() == null || request.ativo())));
    }

    @Operation(summary = "Gera um QR novo para a mesa", description = "O QR impresso antes deixa de funcionar.")
    @PostMapping("/salao/mesas/{id}/regenerar-token")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<SalaoMesaResponseDTO> regenerarToken(@PathVariable Long id) {
        return ResponseEntity.ok(SalaoMesaResponseDTO.of(useCase.regenerarToken(id)));
    }

    @Operation(summary = "Mesas do QR ocupadas", description = "Uma por mesa com comanda aberta, com o código de 4 "
            + "dígitos que o cliente pode pedir ao atendente.")
    @GetMapping("/salao/ocupacoes")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<List<MesaOcupacaoResponseDTO>> ocupacoes() {
        return ResponseEntity.ok(useCase.ocupacoesAbertas().stream().map(MesaOcupacaoResponseDTO::of).toList());
    }

    // ── Essências do dia ──────────────────────────────────────────────────────────────────────

    @Operation(summary = "Essências disponíveis, marcadas as do dia",
            description = "Disponível = lata aberta ou fechada no depósito das sessões, com faixa ativa. "
                    + "data omitida = hoje no fuso do salão. perfisConfigurados = a casa já marcou os perfis "
                    + "do SKU; falso, os perfis são a sugestão pelo nome.")
    @GetMapping("/salao/essencias-do-dia")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<EssenciaDoDiaResponseDTO> listEssenciasDoDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        LocalDate dia = data == null ? useCase.hoje() : data;
        return ResponseEntity.ok(EssenciaDoDiaResponseDTO.of(dia, useCase.listEssenciasDoDia(dia)));
    }

    @Operation(summary = "Define as essências do dia", description = "Substitui a seleção da data. Vazia = o QR "
            + "oferece todas as disponíveis.")
    @PutMapping("/salao/essencias-do-dia")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<EssenciaDoDiaResponseDTO> setEssenciasDoDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @Valid @RequestBody EssenciasDoDiaRequest request) {
        LocalDate dia = data == null ? useCase.hoje() : data;
        useCase.setEssenciasDoDia(dia, request.skus());
        return ResponseEntity.ok(EssenciaDoDiaResponseDTO.of(dia, useCase.listEssenciasDoDia(dia)));
    }

    @Operation(summary = "Copia a seleção de um dia para outro", description = "O \"copiar de ontem\".")
    @PostMapping("/salao/essencias-do-dia/copiar")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<EssenciaDoDiaResponseDTO> copiarEssenciasDoDia(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate para) {
        LocalDate destino = para == null ? useCase.hoje() : para;
        useCase.copiarEssenciasDoDia(de, destino);
        return ResponseEntity.ok(EssenciaDoDiaResponseDTO.of(destino, useCase.listEssenciasDoDia(destino)));
    }

    @Operation(summary = "Define os perfis de sabor por SKU", description = "Substitui os perfis de cada SKU "
            + "listado; lista vazia limpa. SKU nunca configurado tem os perfis sugeridos pelo nome.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Gravados"),
            @ApiResponse(responseCode = "400", description = "SKU vazio ou perfil inválido", content = @Content)
    })
    @PutMapping("/salao/essencias-perfis")
    @PreAuthorize("hasAuthority('PDV_SESSAO_MANAGE')")
    public ResponseEntity<Void> salvarPerfis(@Valid @RequestBody EssenciasPerfisRequest request) {
        useCase.salvarPerfis(request.itens().stream()
                .map(i -> new SalaoAutoatendimentoUseCase.PerfisDoSku(i.sku(), i.perfis())).toList());
        return ResponseEntity.noContent().build();
    }

    // ── PDV: solicitações ─────────────────────────────────────────────────────────────────────

    @Operation(summary = "Sessões pedidas pelo QR",
            description = "PENDENTE = as que o atendente ainda pode aceitar (há menos de 30 min). EXPIRADA é derivada.")
    @GetMapping("/pdv/solicitacoes-sessao")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<PageResult<SolicitacaoSessaoResponseDTO>> listSolicitacoes(
            @RequestParam(defaultValue = "PENDENTE") SolicitacaoStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<SolicitacaoResumo> r = useCase.listSolicitacoes(status, page, size);
        return ResponseEntity.ok(new PageResult<>(r.content().stream().map(SolicitacaoSessaoResponseDTO::of).toList(),
                r.page(), r.size(), r.totalElements(), r.totalPages()));
    }

    @Operation(summary = "Aceita a sessão pedida pelo QR",
            description = "Abre a mesa no caixa sessionId com o cliente (ou usa a comanda ainda aberta da mesa) e "
                    + "lança a sessão com o que o lead escolheu. Mesa livre fica ocupada pelo aparelho que pediu. "
                    + "Pedido pago no final exige PDV_SESSION_PAY_LATER, como o lançamento com pagarNoFinal.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aceita, com a comanda"),
            @ApiResponse(responseCode = "403", description = "Sem PDV_SESSION_PAY_LATER (SESSION_PAY_LATER_NOT_ALLOWED) ou caixa de outro operador", content = @Content),
            @ApiResponse(responseCode = "409", description = "Já aceita, recusada ou expirada (SOLICITACAO_NAO_PENDENTE); sem lata ou utensílio (erros do lançamento da sessão)", content = @Content)
    })
    @PostMapping("/pdv/solicitacoes-sessao/{id}/aceitar")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<SolicitacaoSessaoResponseDTO> aceitar(@PathVariable Long id, @RequestParam Long sessionId,
            Authentication authentication) {
        boolean podePagarNoFinal = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(PAY_LATER_AUTHORITY::equals);
        SolicitacaoResumo aceita = useCase.aceitar(id, sessionId, authentication.getName(), podePagarNoFinal);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("comandaId", aceita.solicitacao().comandaId());
        payload.put("solicitacaoId", id);
        payload.put("essenciaSku", aceita.solicitacao().essenciaSku());
        if (aceita.solicitacao().essenciaSku2() != null) {
            payload.put("essenciaSku2", aceita.solicitacao().essenciaSku2());
        }
        payload.put("pagarNoFinal", aceita.solicitacao().escolhas().pagarNoFinal());
        payload.put("origem", "QR");
        publisher.publishEvent(AuditEvent.of(EventType.COMANDA_SESSION_ADDED, authentication.getName(), payload));
        return ResponseEntity.ok(SolicitacaoSessaoResponseDTO.of(aceita));
    }

    @PostMapping("/pdv/solicitacoes-sessao/{id}/recusar")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<SolicitacaoSessaoResponseDTO> recusar(@PathVariable Long id,
            @Valid @RequestBody(required = false) RecusarSolicitacaoRequest request, Authentication authentication) {
        return ResponseEntity.ok(SolicitacaoSessaoResponseDTO.of(
                useCase.recusar(id, request == null ? null : request.motivo(), authentication.getName())));
    }

    // ── PDV: chamados da mesa ─────────────────────────────────────────────────────────────────

    @Operation(summary = "Chamados feitos pelo QR",
            description = "ABERTO = os de menos de 30 min das comandas ainda abertas, mais antigos primeiro. "
                    + "ATENDIDO = os das últimas 12 h.")
    @GetMapping("/pdv/chamados-mesa")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<List<ChamadoMesaResponseDTO>> listChamados(
            @RequestParam(defaultValue = "ABERTO") StatusChamado status) {
        return ResponseEntity.ok(useCase.listChamados(status).stream().map(ChamadoMesaResponseDTO::of).toList());
    }

    @Operation(summary = "Marca o chamado como atendido", description = "Idempotente: o já atendido volta como está.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atendido"),
            @ApiResponse(responseCode = "404", description = "Chamado não encontrado (CHAMADO_NOT_FOUND)", content = @Content)
    })
    @PostMapping("/pdv/chamados-mesa/{id}/atender")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ChamadoMesaResponseDTO> atenderChamado(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ChamadoMesaResponseDTO.of(useCase.atenderChamado(id, authentication.getName())));
    }
}
