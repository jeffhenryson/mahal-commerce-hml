package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.FinanceiroDTOConverter;
import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.CashFlowEntryPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.CashFlowEntryRequest;
import com.cernecommerce.adapter.in.dtos.response.CashFlowEntryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashFlowSummaryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.MarginReportResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.FinanceiroUseCase;
import com.cernecommerce.core.ports.in.OrderReportUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Controller do domínio <b>financeiro</b> — contrato de escrita fechado em FIN-F004 (fluxo de
 * caixa manual, com vínculo opcional a pedido/compra de origem) e relatório de margem em FIN-F003
 * (agregação de {@code OrderItem.marginAmount()}, delegada a {@link OrderReportUseCase} — o dado
 * nasce em {@code order_item}, não em {@code cash_flow_entry}). DRE simplificado e conciliação de
 * taxas de gateway seguem como TODO. Leitura requer {@code FINANCEIRO_READ}; escrita requer
 * {@code FINANCEIRO_CASH_FLOW_MANAGE}.
 */
@RestController
@RequestMapping("/financeiro")
@Tag(name = "Financeiro", description = "Fluxo de caixa, DRE e conciliação")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class FinanceiroController {

    private final FinanceiroUseCase financeiroUseCase;
    private final OrderReportUseCase orderReportUseCase;
    private final FinanceiroDTOConverter converter;
    private final OrderDTOConverter orderConverter;
    private final ApplicationEventPublisher publisher;

    public FinanceiroController(FinanceiroUseCase financeiroUseCase, OrderReportUseCase orderReportUseCase,
            FinanceiroDTOConverter converter, OrderDTOConverter orderConverter,
            ApplicationEventPublisher publisher) {
        this.financeiroUseCase = financeiroUseCase;
        this.orderReportUseCase = orderReportUseCase;
        this.converter = converter;
        this.orderConverter = orderConverter;
        this.publisher = publisher;
    }

    @Operation(summary = "Lista lançamentos de fluxo de caixa")
    @GetMapping("/cash-flow")
    @PreAuthorize("hasAuthority('FINANCEIRO_READ')")
    public ResponseEntity<PageResult<CashFlowEntryResponseDTO>> listCashFlow(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<CashFlowEntry> result = financeiroUseCase.listCashFlow(page, size);
        return ResponseEntity.ok(new PageResult<>(
                result.content().stream().map(converter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @Operation(summary = "Saldo agregado de fluxo de caixa num período",
            description = "Agregado no backend (soma de entradas/saídas por due_date no "
                    + "intervalo) — não soma os itens de uma página, que ficaria incorreto assim "
                    + "que houvesse mais de uma página.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Período inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/cash-flow/summary")
    @PreAuthorize("hasAuthority('FINANCEIRO_READ')")
    public ResponseEntity<CashFlowSummaryResponseDTO> getCashFlowSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(converter.toResponse(financeiroUseCase.getCashFlowSummary(from, to)));
    }

    @Operation(summary = "Relatório de margem agregado do período (FIN-F003)",
            description = "Agrega OrderItem.marginAmount() (netAmount - costPrice*quantity) dos "
                    + "pedidos com pagamento confirmado (PAGO em diante, exceto REEMBOLSADO). "
                    + "from/to são obrigatórios (máx. 366 dias). Itens sem costPrice congelado "
                    + "(pedidos legados) são excluídos do agregado, nunca contados como margem "
                    + "zero — ver itemsConsidered.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "from/to ausentes, from depois de to, ou intervalo maior que o teto permitido", content = @Content)
    })
    @GetMapping("/margem")
    @PreAuthorize("hasAuthority('FINANCEIRO_READ')")
    public ResponseEntity<MarginReportResponseDTO> getMarginReport(
            @RequestParam(required = false) SalesChannel channel,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @io.swagger.v3.oas.annotations.Parameter(description = "Pendência 9 — itens em topProductsByMargin (1 a 50).")
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(orderConverter.toMarginResponse(
                orderReportUseCase.getMarginReport(channel, warehouseCode, from, to, limit)));
    }

    @Operation(summary = "Cria um lançamento de fluxo de caixa",
            description = "Nasce com status PREVISTO. date (lançamento) é atribuída pelo servidor.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criado"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/cash-flow")
    @PreAuthorize("hasAuthority('FINANCEIRO_CASH_FLOW_MANAGE')")
    public ResponseEntity<CashFlowEntryResponseDTO> createCashFlowEntry(
            @Valid @RequestBody CashFlowEntryRequest request, Authentication authentication) {
        CashFlowEntry created = financeiroUseCase.createCashFlowEntry(request.getDescription(),
                request.getEntityName(), request.getCategory(), request.getDirection(), request.getAmount(),
                request.getDueDate(), request.getLinkedEntityType(), request.getLinkedEntityId());
        publisher.publishEvent(AuditEvent.of(EventType.CASH_FLOW_ENTRY_CREATED,
                authentication.getName(), Map.of("cashFlowEntryId", String.valueOf(created.id()))));
        return ResponseEntity.created(URI.create("/financeiro/cash-flow/" + created.id()))
                .body(converter.toResponse(created));
    }

    @Operation(summary = "Altera parcialmente um lançamento (edita campos e/ou marca como pago)",
            description = "Campo ausente ou nulo é mantido. Marcar status: PAGO sem paymentDate "
                    + "grava a data corrente no servidor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Lançamento não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/cash-flow/{id}")
    @PreAuthorize("hasAuthority('FINANCEIRO_CASH_FLOW_MANAGE')")
    public ResponseEntity<CashFlowEntryResponseDTO> updateCashFlowEntry(@PathVariable Long id,
            @Valid @RequestBody CashFlowEntryPatchRequest request, Authentication authentication) {
        CashFlowEntry updated = financeiroUseCase.updateCashFlowEntry(id, request.getDescription(),
                request.getEntityName(), request.getCategory(), request.getDirection(), request.getAmount(),
                request.getDueDate(), request.getStatus(), request.getPaymentDate(),
                request.getLinkedEntityType(), request.getLinkedEntityId());
        publisher.publishEvent(AuditEvent.of(EventType.CASH_FLOW_ENTRY_UPDATED,
                authentication.getName(), Map.of("cashFlowEntryId", String.valueOf(id))));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Remove um lançamento",
            description = "Soft-delete quando há linkedEntityId (preserva o vínculo com o "
                    + "pedido/compra de origem); remoção definitiva caso contrário.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removido"),
            @ApiResponse(responseCode = "404", description = "Lançamento não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/cash-flow/{id}")
    @PreAuthorize("hasAuthority('FINANCEIRO_CASH_FLOW_MANAGE')")
    public ResponseEntity<Void> deleteCashFlowEntry(@PathVariable Long id, Authentication authentication) {
        financeiroUseCase.deleteCashFlowEntry(id);
        publisher.publishEvent(AuditEvent.of(EventType.CASH_FLOW_ENTRY_DELETED,
                authentication.getName(), Map.of("cashFlowEntryId", String.valueOf(id))));
        return ResponseEntity.noContent().build();
    }
}
