package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.CreditLimitRequest;
import com.cernecommerce.adapter.in.dtos.request.ReceivableCancelRequest;
import com.cernecommerce.adapter.in.dtos.request.ReceivableDueDateRequest;
import com.cernecommerce.adapter.in.dtos.request.ReceivablePaymentRequest;
import com.cernecommerce.adapter.in.dtos.response.ReceivableResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.domain.model.recebivel.OnAccountEligibility;
import com.cernecommerce.core.domain.model.recebivel.ReceivableCustomerSummary;
import com.cernecommerce.core.domain.model.recebivel.ReceivableFilter;
import com.cernecommerce.core.domain.model.recebivel.ReceivablePayment;
import com.cernecommerce.core.domain.model.recebivel.ReceivableStatus;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase.ReceivableView;
import com.cernecommerce.core.ports.in.ReceivableUseCase.SettlementResult;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Marcar" — venda a prazo para cliente VIP (CRM-F010): a tabela única de marcados, a quitação no
 * caixa de quem recebe, a manutenção (prazo, cancelamento) e o limite de crédito do cliente.
 */
@RestController
@Validated
@Tag(name = "Marcados", description = "Venda a prazo para cliente VIP: recebíveis, quitação e limite")
@SecurityRequirement(name = "bearerAuth")
public class ReceivableController {

    private final ReceivableUseCase receivableUseCase;
    private final OrderDTOConverter orderConverter;
    private final ApplicationEventPublisher publisher;

    public ReceivableController(ReceivableUseCase receivableUseCase, OrderDTOConverter orderConverter,
            ApplicationEventPublisher publisher) {
        this.receivableUseCase = receivableUseCase;
        this.orderConverter = orderConverter;
        this.publisher = publisher;
    }

    // ── Consulta ─────────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Tabela única de marcados",
            description = "Um item por pedido marcado, com os itens do pedido (o front achata em uma linha por "
                    + "produto ou sessão). overdue=true: só em aberto com vencimento antes de hoje. Ordem: "
                    + "vencimento mais próximo primeiro.")
    @GetMapping("/receivables")
    @PreAuthorize("hasAuthority('RECEIVABLE_READ')")
    public ResponseEntity<PageResult<ReceivableResponseDTO>> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) ReceivableStatus status,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        PageResult<ReceivableView> result = receivableUseCase.list(
                new ReceivableFilter(customerId, status, overdue, dueFrom, dueTo, createdFrom, createdTo), page, size);
        return ResponseEntity.ok(new PageResult<>(result.content().stream().map(ReceivableController::toResponse)
                .toList(), result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @Operation(summary = "Marcados agrupados por cliente",
            description = "Sem status: só os em aberto (ABERTO, PARCIAL, VENCIDO). overdue=true: só clientes com "
                    + "saldo vencido. Ordem: mais vencido primeiro.")
    @GetMapping("/receivables/summary")
    @PreAuthorize("hasAuthority('RECEIVABLE_READ')")
    public ResponseEntity<List<ReceivableCustomerSummary>> summary(
            @RequestParam(required = false) ReceivableStatus status,
            @RequestParam(required = false) Boolean overdue) {
        return ResponseEntity.ok(receivableUseCase.summary(status, overdue));
    }

    @Operation(summary = "Detalhe de um marcado, com as quitações")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "RECEIVABLE_NOT_FOUND", content = @Content)
    })
    @GetMapping("/receivables/{id}")
    @PreAuthorize("hasAuthority('RECEIVABLE_READ')")
    public ResponseEntity<ReceivableResponseDTO> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(toResponse(receivableUseCase.get(id)));
    }

    @Operation(summary = "Marcados do cliente (aba \"Marcados\" da ficha)", description = "Mais recentes primeiro.")
    @GetMapping("/crm/customers/{id}/receivables")
    @PreAuthorize("hasAuthority('RECEIVABLE_READ')")
    public ResponseEntity<List<ReceivableResponseDTO>> listByCustomer(@PathVariable("id") Long customerId) {
        return ResponseEntity.ok(receivableUseCase.listByCustomer(customerId).stream()
                .map(ReceivableController::toResponse).toList());
    }

    @Operation(summary = "Pré-checagem do \"Marcar\" para o PDV",
            description = "reasons: CUSTOMER_NOT_ELIGIBLE (sem tag VIP), ON_ACCOUNT_NOT_ALLOWED (operador sem "
                    + "PDV_SALE_ON_ACCOUNT), CUSTOMER_HAS_OVERDUE, CREDIT_LIMIT_EXCEEDED (nada disponível). "
                    + "creditLimit é o limite efetivo (individual ou o padrão da loja); defaultDueDate vem de "
                    + "pdv.on-account.default-due-days.")
    @GetMapping("/crm/customers/{id}/on-account-eligibility")
    @PreAuthorize("hasAnyAuthority('PDV_SALE_MANAGE', 'PDV_COMANDA_MANAGE', 'RECEIVABLE_READ')")
    public ResponseEntity<OnAccountEligibility> eligibility(@PathVariable("id") Long customerId,
            Authentication authentication) {
        return ResponseEntity.ok(receivableUseCase.eligibility(customerId, OnAccountGuard.mayMark(authentication)));
    }

    // ── Quitação ─────────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Recebe marcado no caixa de quem recebe",
            description = "Exige a sessão de caixa ABERTA, do operador e de hoje (as mesmas regras da venda). "
                    + "Sem receivableIds, abate do mais antigo (vencimento, depois criação); com eles, só neles "
                    + "e na ordem dada. Parcial deixa o marcado PARCIAL; zerado, QUITADO. Só DINHEIRO pode "
                    + "passar do saldo e gera troco; os demais acima do saldo dão 409 PAYMENT_EXCEEDS_BALANCE. "
                    + "O valor entra em payment-totals da sessão como receivableReceived e, em dinheiro, no "
                    + "esperado do fechamento. O cashback da parte marcada é creditado agora, proporcional.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recebido"),
            @ApiResponse(responseCode = "403", description = "Sessão de outro operador", content = @Content),
            @ApiResponse(responseCode = "409", description = "PAYMENT_EXCEEDS_BALANCE, RECEIVABLE_NOT_OPEN, "
                    + "sessão fechada ou de dia anterior", content = @Content)
    })
    @PostMapping("/receivables/payments")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<SettlementResult> pay(@RequestParam("sessionId") Long sessionId,
            @Valid @RequestBody ReceivablePaymentRequest request, Authentication authentication) {
        List<PaymentCommand> payments = orderConverter.toPaymentCommands(request.getPayments());
        SettlementResult result = receivableUseCase.pay(sessionId, authentication.getName(),
                request.getCustomerId(), payments, request.getReceivableIds());
        publisher.publishEvent(AuditEvent.of(EventType.RECEIVABLE_PAID, authentication.getName(),
                Map.of("customerId", request.getCustomerId(),
                        "sessionId", sessionId,
                        "batchId", result.batchId(),
                        "applied", result.applied().stream()
                                .map(a -> Map.of("receivableId", a.receivableId(), "amount", a.amount(),
                                        "statusAfter", a.statusAfter().name()))
                                .toList(),
                        "changeAmount", result.changeAmount(),
                        "openBalanceAfter", result.openBalanceAfter())));
        return ResponseEntity.ok(result);
    }

    // ── Manutenção ───────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Renegocia o vencimento de um marcado em aberto")
    @PatchMapping("/receivables/{id}/due-date")
    @PreAuthorize("hasAuthority('RECEIVABLE_MANAGE')")
    public ResponseEntity<ReceivableResponseDTO> changeDueDate(@PathVariable("id") Long id,
            @Valid @RequestBody ReceivableDueDateRequest request, Authentication authentication) {
        LocalDate before = receivableUseCase.get(id).receivable().dueDate();
        CustomerReceivable updated = receivableUseCase.changeDueDate(id, request.getDueDate(), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.RECEIVABLE_DUE_DATE_CHANGED, authentication.getName(),
                Map.of("receivableId", id, "customerId", updated.customerId(), "from", before.toString(),
                        "to", updated.dueDate().toString(), "reason", request.getReason())));
        return ResponseEntity.ok(toResponse(receivableUseCase.get(id)));
    }

    @Operation(summary = "Cancela um marcado em aberto (perdão ou erro de lançamento)",
            description = "Não mexe em estoque — para devolver a mercadoria, o caminho é o reembolso do pedido, "
                    + "que já cancela o marcado em aberto.")
    @PostMapping("/receivables/{id}/cancel")
    @PreAuthorize("hasAuthority('RECEIVABLE_MANAGE')")
    public ResponseEntity<ReceivableResponseDTO> cancel(@PathVariable("id") Long id,
            @Valid @RequestBody ReceivableCancelRequest request, Authentication authentication) {
        CustomerReceivable cancelled = receivableUseCase.cancel(id, request.getReason(), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.RECEIVABLE_CANCELLED, authentication.getName(),
                Map.of("receivableId", id, "customerId", cancelled.customerId(),
                        "amountOpen", cancelled.amountOpen(), "reason", request.getReason())));
        return ResponseEntity.ok(toResponse(receivableUseCase.get(id)));
    }

    @Operation(summary = "Define o limite de crédito do \"Marcar\" do cliente",
            description = "Endpoint próprio, e não campo do PUT /crm/customers/{id}: o limite é decisão do gerente "
                    + "e o PUT do cadastro regrava a ficha inteira. creditLimit null volta ao limite padrão.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Atualizado"),
            @ApiResponse(responseCode = "400", description = "Valor negativo", content = @Content),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = @Content)
    })
    @PutMapping("/crm/customers/{id}/credit-limit")
    @PreAuthorize("hasAuthority('RECEIVABLE_MANAGE')")
    public ResponseEntity<Void> setCreditLimit(@PathVariable("id") Long customerId,
            @Valid @RequestBody CreditLimitRequest request, Authentication authentication) {
        ReceivableUseCase.CreditLimitChange change = receivableUseCase.setCreditLimit(customerId,
                request.getCreditLimit(), authentication.getName());
        Map<String, Object> details = new HashMap<>();
        details.put("customerId", customerId);
        details.put("before", change.before());
        details.put("after", change.after());
        details.put("individual", request.getCreditLimit() != null);
        publisher.publishEvent(AuditEvent.of(EventType.CUSTOMER_CREDIT_LIMIT_CHANGED, authentication.getName(),
                details));
        return ResponseEntity.noContent().build();
    }

    // ── Mapeamento ───────────────────────────────────────────────────────────────────────────

    static ReceivableResponseDTO toResponse(ReceivableView view) {
        CustomerReceivable r = view.receivable();
        ReceivableResponseDTO dto = new ReceivableResponseDTO();
        dto.setId(r.id());
        dto.setCustomerId(r.customerId());
        dto.setCustomerName(view.customerName());
        dto.setOrderId(r.orderId());
        dto.setOrderNumber(view.orderNumber());
        dto.setComandaId(r.comandaId());
        dto.setTableLabel(view.tableLabel());
        dto.setItems(r.items().stream().map(i -> {
            ReceivableResponseDTO.Item item = new ReceivableResponseDTO.Item();
            item.setOrderItemId(i.orderItemId());
            item.setSku(i.sku());
            item.setProductName(i.productName());
            item.setQuantity(i.quantity());
            item.setSubtotal(i.subtotal());
            item.setMode(i.mode());
            return item;
        }).toList());
        dto.setAmount(r.amount());
        dto.setAmountPaid(r.amountPaid());
        dto.setAmountOpen(r.amountOpen());
        dto.setDueDate(r.dueDate());
        dto.setStatus(r.status().name());
        dto.setDaysOverdue(view.daysOverdue());
        dto.setCreatedAt(r.createdAt());
        dto.setCreatedBy(r.createdBy());
        dto.setSettledAt(r.settledAt());
        dto.setCancelReason(r.cancelReason());
        dto.setCancelledBy(r.cancelledBy());
        dto.setCancelledAt(r.cancelledAt());
        dto.setPayments(view.payments().stream().map(ReceivableController::toResponse).toList());
        return dto;
    }

    private static ReceivableResponseDTO.Payment toResponse(ReceivablePayment p) {
        ReceivableResponseDTO.Payment dto = new ReceivableResponseDTO.Payment();
        dto.setId(p.id());
        dto.setBatchId(p.batchId());
        dto.setAmount(p.amount());
        dto.setMethod(p.method().name());
        dto.setInstallments(p.installments());
        dto.setChannel(p.channel() == null ? null : p.channel().name());
        dto.setProvider(p.provider() == null ? null : p.provider().name());
        dto.setCashSessionId(p.cashSessionId());
        dto.setReceivedBy(p.receivedBy());
        dto.setReceivedAt(p.receivedAt());
        return dto;
    }
}
