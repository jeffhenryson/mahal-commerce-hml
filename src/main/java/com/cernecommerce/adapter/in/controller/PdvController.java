package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.CashRegisterDTOConverter;
import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.CashMovementRequest;
import com.cernecommerce.adapter.in.dtos.request.CloseCashRegisterSessionRequest;
import com.cernecommerce.adapter.in.dtos.request.OpenCashRegisterSessionRequest;
import com.cernecommerce.adapter.in.dtos.request.SaleRequest;
import com.cernecommerce.adapter.in.dtos.request.SettleOnlineOrderRequest;
import com.cernecommerce.adapter.in.dtos.response.CashMovementResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashRegisterSessionResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.PaymentTotalResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ReceiptEmailResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SessionSummaryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SaleReceiptResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSessionFilter;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import com.cernecommerce.core.ports.in.ReceiptEmailUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.net.URI;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

/**
 * Controller do domínio <b>vendas-balcao (PDV)</b>.
 *
 * <p>Fluxo hexagonal Controller → {@link PdvUseCase} → service. Os {@code AuditEvent} são publicados
 * aqui, e não no service, porque {@code HexagonalArchitectureTest} barra
 * {@code ApplicationEventPublisher} em {@code core/service}.</p>
 */
@RestController
@RequestMapping("/pdv")
@Tag(name = "PDV (Vendas Balcão)", description = "Frente de caixa")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class PdvController {

    private static final String DISCOUNT_AUTHORITY = "PDV_SALE_DISCOUNT";

    private final PdvUseCase pdvUseCase;
    private final OrderDTOConverter orderConverter;
    private final CashRegisterDTOConverter cashRegisterConverter;
    private final ApplicationEventPublisher publisher;
    private final ReceiptEmailUseCase receiptEmailUseCase;

    public PdvController(PdvUseCase pdvUseCase, OrderDTOConverter orderConverter,
            CashRegisterDTOConverter cashRegisterConverter, ApplicationEventPublisher publisher,
            ReceiptEmailUseCase receiptEmailUseCase) {
        this.pdvUseCase = pdvUseCase;
        this.receiptEmailUseCase = receiptEmailUseCase;
        this.orderConverter = orderConverter;
        this.cashRegisterConverter = cashRegisterConverter;
        this.publisher = publisher;
    }

    // ── Ciclo de caixa ───────────────────────────────────────────────────────────────────────

    @Operation(summary = "Lista sessões de caixa",
            description = "PDV-F026 — filtros opcionais: status (OPEN/CLOSED), operator (exato) e "
                    + "from/to sobre openedAt (ISO date-time, inclusivos). Ordenado por openedAt desc.")
    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<PageResult<CashRegisterSessionResponseDTO>> listSessions(
            @RequestParam(required = false) CashRegisterSession.Status status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String operator,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(cashRegisterConverter.toResponse(pdvUseCase.listSessions(
                new CashRegisterSessionFilter(status, from, to, operator), page, size)));
    }

    @Operation(summary = "Abre um caixa para o operador autenticado",
            description = "Uma sessão aberta por operador. O depósito informado aqui é o que todas "
                    + "as vendas deste caixa vão usar.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Aberto", content = @Content(schema = @Schema(implementation = CashRegisterSessionResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "O operador já tem caixa aberto", content = @Content),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content)
    })
    @PostMapping("/sessions")
    @PreAuthorize("hasAuthority('PDV_SESSION_MANAGE')")
    public ResponseEntity<CashRegisterSessionResponseDTO> openSession(
            @Valid @RequestBody OpenCashRegisterSessionRequest request, Authentication authentication) {
        CashRegisterSession session = pdvUseCase.openSession(authentication.getName(),
                request.getOpeningAmount(), request.getWarehouseCode());
        publisher.publishEvent(AuditEvent.of(EventType.CASH_SESSION_OPENED, authentication.getName(),
                Map.of("sessionId", session.id(),
                        "openingAmount", session.openingAmount(),
                        "warehouseCode", session.warehouseCode())));
        return ResponseEntity.created(URI.create("/pdv/sessions/" + session.id()))
                .body(cashRegisterConverter.toResponse(session));
    }

    @Operation(summary = "Caixa aberto do operador autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CashRegisterSessionResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "O operador não tem caixa aberto", content = @Content)
    })
    @GetMapping("/sessions/current")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<CashRegisterSessionResponseDTO> getCurrentSession(Authentication authentication) {
        return ResponseEntity.ok(
                cashRegisterConverter.toResponse(pdvUseCase.getCurrentSession(authentication.getName())));
    }

    @Operation(summary = "Consulta uma sessão de caixa")
    @GetMapping("/sessions/{id}")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<CashRegisterSessionResponseDTO> getSession(@PathVariable("id") Long sessionId) {
        return ResponseEntity.ok(cashRegisterConverter.toResponse(pdvUseCase.getSession(sessionId)));
    }

    @Operation(summary = "Registra sangria ou suprimento na sessão",
            description = "Exige que a sessão esteja aberta e pertença ao operador autenticado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registrado", content = @Content(schema = @Schema(implementation = CashMovementResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "A sessão é de outro operador", content = @Content),
            @ApiResponse(responseCode = "409", description = "Sessão já encerrada", content = @Content)
    })
    @PostMapping("/sessions/{id}/movements")
    @PreAuthorize("hasAuthority('PDV_SESSION_MANAGE')")
    public ResponseEntity<CashMovementResponseDTO> registerCashMovement(@PathVariable("id") Long sessionId,
            @Valid @RequestBody CashMovementRequest request, Authentication authentication) {
        CashMovement movement = pdvUseCase.registerCashMovement(sessionId, request.getType(),
                request.getAmount(), request.getReason(), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.CASH_MOVEMENT_REGISTERED, authentication.getName(),
                Map.of("sessionId", sessionId,
                        "type", movement.type().name(),
                        "amount", movement.amount(),
                        "reason", movement.reason())));
        return ResponseEntity.status(201).body(cashRegisterConverter.toResponse(movement));
    }

    @Operation(summary = "Lista os movimentos de caixa da sessão",
            description = "Paginado desde PDV-C012 — a rota devolvia a List inteira, sem teto, "
                    + "contra o resto do módulo. Ordem de lançamento (id crescente), que é a ordem "
                    + "em que os movimentos aconteceram.")
    @GetMapping("/sessions/{id}/movements")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<PageResult<CashMovementResponseDTO>> listCashMovements(
            @PathVariable("id") Long sessionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        PageResult<CashMovement> result = pdvUseCase.listCashMovements(sessionId, page, size);
        return ResponseEntity.ok(new PageResult<>(
                cashRegisterConverter.toMovementResponses(result.content()),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @Operation(summary = "Fecha o caixa confrontando o contado com o esperado",
            description = "Divergência NÃO impede o fechamento — é registrada, como no fechamento de "
                    + "um balanço de inventário. Admin e dev fecham o caixa de qualquer operador (a "
                    + "conferência costuma ser do gerente); os demais só o próprio. `notes` registra o "
                    + "motivo do fechamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fechado, com esperado × contado × diferença", content = @Content(schema = @Schema(implementation = CashRegisterSessionResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Caixa de outro operador, sem ser admin/dev", content = @Content),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Sessão já encerrada ou com mesas abertas", content = @Content)
    })
    @PostMapping("/sessions/{id}/close")
    @PreAuthorize("hasAuthority('PDV_SESSION_CLOSE')")
    public ResponseEntity<CashRegisterSessionResponseDTO> closeSession(@PathVariable("id") Long sessionId,
            @Valid @RequestBody CloseCashRegisterSessionRequest request, Authentication authentication) {
        CashRegisterSession session = pdvUseCase.closeSession(sessionId, request.getCountedAmount(),
                request.getNotes(), authentication.getName(), canCloseAnySession(authentication));
        Map<String, Object> details = new HashMap<>();
        details.put("sessionId", sessionId);
        details.put("expectedAmount", session.expectedAmount());
        details.put("countedAmount", session.countedAmount());
        details.put("differenceAmount", session.differenceAmount());
        details.put("diverges", session.diverges());
        details.put("operator", session.operator());
        if (session.closingNotes() != null) {
            details.put("notes", session.closingNotes());
        }
        publisher.publishEvent(
                AuditEvent.of(EventType.CASH_SESSION_CLOSED, authentication.getName(), details));
        return ResponseEntity.ok(cashRegisterConverter.toResponse(session));
    }

    // ── Venda ────────────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Registra uma venda de balcão na sessão, captura o pagamento e dá baixa automática no estoque",
            description = "O preço e o custo de cada item são resolvidos pelo servidor a partir do "
                    + "catálogo, e o depósito vem da sessão de caixa — o request informa apenas SKU, "
                    + "quantidade e, opcionalmente, desconto e cliente. Desconto maior que zero exige "
                    + "a permissão PDV_SALE_DISCOUNT. `payments` exige pelo menos uma linha; a soma "
                    + "tem que cobrir o líquido, e só DINHEIRO pode ser tendido a mais para gerar troco. "
                    + "`reserveForPickup=true` (PDV-F008) grava RESERVADO em vez de CONCLUIDO — mercadoria "
                    + "já baixada e pagamento já capturado, só a retirada fica pendente; marcar como "
                    + "retirado depois é `POST /orders/{id}/status` com `CONCLUIDO`. `delivery` (PDV-F022) "
                    + "registra RETIRADA ou ENTREGA — RETIRADA grava CONCLUIDO (ou RESERVADO com "
                    + "`reserveForPickup=true`); ENTREGA grava RESERVADO e segue "
                    + "RESERVADO → SEPARADO → ENVIADO → ENTREGUE, e `delivery.fee` entra no total a "
                    + "pagar (fora do líquido). `items[].note` grava a observação da linha. PDV-F037: caixa "
                    + "aberto num dia anterior continua vendendo (sem corte de meia-noite). "
                    + "CRM-F010: uma linha `MARCADO` (com `dueDate`) deixa parte ou tudo para o cliente VIP "
                    + "pagar depois — exige PDV_SALE_ON_ACCOUNT (403 ON_ACCOUNT_NOT_ALLOWED), cliente "
                    + "(400 CUSTOMER_REQUIRED_FOR_ON_ACCOUNT), tag VIP (403 CUSTOMER_NOT_ELIGIBLE), "
                    + "nenhum vencido (409 CUSTOMER_HAS_OVERDUE) e limite (409 CREDIT_LIMIT_EXCEEDED). "
                    + "A linha fica ON_ACCOUNT, fora do caixa; o pedido conclui normalmente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada", content = @Content(schema = @Schema(implementation = OrderResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Saldo ou pagamento insuficiente para a venda", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sessão de outro operador, ou desconto sem PDV_SALE_DISCOUNT", content = @Content),
            @ApiResponse(responseCode = "404", description = "Sessão de caixa ou SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Sessão encerrada, produto sem preço, desconto acima do teto ou pagamento não-dinheiro acima do total", content = @Content)
    })
    @PostMapping("/sessions/{id}/sales")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<OrderResponseDTO> registerSale(@PathVariable("id") Long sessionId,
            @Valid @RequestBody SaleRequest request, Authentication authentication) {
        List<SaleItemCommand> items = orderConverter.toCommands(request.getItems());
        List<PaymentCommand> payments = orderConverter.toPaymentCommands(request.getPayments());
        requireDiscountAuthority(items, authentication);
        OnAccountGuard.requireAuthorityIfOnAccount(payments, authentication);

        OrderDelivery delivery = orderConverter.toDelivery(request.getDelivery());
        Order order = pdvUseCase.registerSale(sessionId, request.getCustomerId(), items, payments,
                authentication.getName(), Boolean.TRUE.equals(request.getReserveForPickup()), delivery);

        // EST-C004: a venda é o caminho de maior volume de movimentação de estoque. É um evento por
        // operação (não por item) para não inundar a trilha numa venda com muitos itens.
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_MOVEMENT_REGISTERED, authentication.getName(),
                Map.of("origin", "PDV_SALE",
                        "sessionId", sessionId,
                        "orderNumber", order.orderNumber(),
                        "warehouseCode", order.warehouseCode(),
                        "type", MovementType.SAIDA.name(),
                        "skus", items.stream().map(SaleItemCommand::sku).toList(),
                        "itemCount", items.size())));
        publishCashbackEarnedIfAny(order, authentication.getName());
        OnAccountGuard.publishCreatedIfOnAccount(publisher, order, payments, authentication.getName());
        return ResponseEntity.status(201)
                .body(orderConverter.toResponse(order, pdvUseCase.getOrderPayments(order.id())));
    }

    @Operation(summary = "Consulta um pedido pelo id, com os pagamentos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OrderResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content)
    })
    @GetMapping("/sales/{id}")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<OrderResponseDTO> getOrder(@PathVariable("id") Long orderId) {
        Order order = pdvUseCase.getOrder(orderId);
        return ResponseEntity.ok(orderConverter.toResponse(order, pdvUseCase.getOrderPayments(orderId)));
    }

    @Operation(summary = "Envia o comprovante da compra para o e-mail do cliente vinculado",
            description = "Só quando o operador pede — balcão e mesa não mandam e-mail sozinhos. Vai "
                    + "para o e-mail do cliente vinculado ao pedido (não aceita endereço avulso). "
                    + "Envio assíncrono: 202 significa que saiu para o provedor, não que chegou.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Enviado; `sentTo` traz o destinatário mascarado", content = @Content(schema = @Schema(implementation = ReceiptEmailResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "422", description = "RECEIPT_EMAIL_UNAVAILABLE — pedido cancelado, sem cliente ou cliente sem e-mail", content = @Content)
    })
    @PostMapping("/sales/{id}/receipt/email")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<ReceiptEmailResponseDTO> emailSaleReceipt(@PathVariable("id") Long orderId) {
        return ResponseEntity.accepted().body(new ReceiptEmailResponseDTO(receiptEmailUseCase.sendPurchaseReceipt(orderId)));
    }

    @Operation(summary = "Comprovante interno da venda — não é documento fiscal",
            description = "Resumo para a loja imprimir/exportar (itens, valores, formas de "
                    + "pagamento). A NFC-e (Fatia 11) é quem substitui isto por um documento fiscal "
                    + "de verdade quando o emissor terceiro for integrado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SaleReceiptResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content)
    })
    @GetMapping("/sales/{id}/receipt")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<SaleReceiptResponseDTO> getSaleReceipt(@PathVariable("id") Long orderId) {
        Order order = pdvUseCase.getOrder(orderId);
        List<OrderPayment> payments = pdvUseCase.getOrderPayments(orderId);
        return ResponseEntity.ok(orderConverter.toReceipt(order, payments));
    }

    @Operation(summary = "Totais recebidos na sessão, por forma de pagamento",
            description = "Só pagamento CAPTURED conta. Devolve as quatro formas sempre, mesmo com "
                    + "total zero. Só DINHEIRO entra na conferência da gaveta no fechamento — "
                    + "débito, crédito e PIX se conferem contra a adquirente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada", content = @Content)
    })
    @GetMapping("/sessions/{id}/payment-totals")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<List<PaymentTotalResponseDTO>> getSessionPaymentTotals(
            @PathVariable("id") Long sessionId) {
        return ResponseEntity.ok(
                orderConverter.toPaymentTotalResponse(pdvUseCase.getSessionPaymentTotals(sessionId)));
    }

    @Operation(summary = "Resumo do caixa: total vendido e totais por forma de pagamento (PDV-F038)",
            description = "totalReceived soma o líquido de todas as formas (dinheiro sem troco, estornos "
                    + "descontados, quitações de marcado incluídas). O marcado vendido vem em totalOnAccount, "
                    + "fora do total. Serve de prévia antes de fechar e de relatório depois.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada", content = @Content)
    })
    @GetMapping("/sessions/{id}/summary")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<SessionSummaryResponseDTO> getSessionSummary(@PathVariable("id") Long sessionId) {
        return ResponseEntity.ok(orderConverter.toSessionSummaryResponse(pdvUseCase.getSessionSummary(sessionId)));
    }

    @Operation(summary = "Lista os pedidos de uma sessão de caixa, do mais recente para o mais antigo")
    @GetMapping("/sessions/{id}/sales")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<PageResult<OrderResponseDTO>> listSessionOrders(
            @PathVariable("id") Long sessionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(orderConverter.toResponse(pdvUseCase.listSessionOrders(sessionId, page, size)));
    }

    @Operation(summary = "Pedidos do aplicativo aguardando pagamento",
            description = "A lista que o caixa consulta quando o cliente chega à loja para retirar e "
                    + "pagar um pedido montado no app.")
    @GetMapping("/pending-online-orders")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<PageResult<OrderResponseDTO>> listPendingOnlineOrders(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(orderConverter.toResponse(pdvUseCase.listPendingOnlineOrders(page, size)));
    }

    @Operation(summary = "Liquida no balcão um pedido feito no aplicativo",
            description = "Recebe o pagamento, consome a reserva de estoque e conclui. O canal "
                    + "continua MARKETPLACE — o que muda é a sessão de caixa que passa a responder "
                    + "pelo dinheiro recebido. **PDV-C015: a rota passou a exigir corpo** — o "
                    + "pagamento recebido é registrado como em qualquer outra venda, e a cobrança "
                    + "de gateway aberta no checkout é encerrada. `payments` tem que somar "
                    + "EXATAMENTE o líquido: pedido de marketplace não tem onde guardar troco.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liquidado", content = @Content(schema = @Schema(implementation = OrderResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Pagamento insuficiente, ou acima do líquido (aqui não há troco)", content = @Content),
            @ApiResponse(responseCode = "403", description = "A sessão é de outro operador", content = @Content),
            @ApiResponse(responseCode = "404", description = "Sessão ou pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Sessão encerrada, pedido que não está aguardando pagamento, ou pagamento não-dinheiro acima do total", content = @Content)
    })
    @PostMapping("/sessions/{id}/orders/{orderId}/settle")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<OrderResponseDTO> settleOnlineOrder(@PathVariable("id") Long sessionId,
            @PathVariable("orderId") Long orderId,
            @Valid @RequestBody SettleOnlineOrderRequest request, Authentication authentication) {
        Order order = pdvUseCase.settleOnlineOrder(sessionId, orderId,
                orderConverter.toPaymentCommands(request.getPayments()), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_MOVEMENT_REGISTERED, authentication.getName(),
                Map.of("origin", "PDV_SETTLE_ONLINE",
                        "sessionId", sessionId,
                        "orderId", orderId,
                        "orderNumber", order.orderNumber(),
                        "warehouseCode", order.warehouseCode(),
                        "type", MovementType.SAIDA.name())));
        publishCashbackEarnedIfAny(order, authentication.getName());
        return ResponseEntity.ok(orderConverter.toResponse(order, pdvUseCase.getOrderPayments(order.id())));
    }

    /**
     * CRM-F003: um evento à parte do {@code STOCK_MOVEMENT_REGISTERED} da venda, e só quando há
     * ganho de verdade — venda anônima ou cliente sem CPF não gera nada, e não vale poluir a
     * trilha de auditoria com um evento de valor zero.
     */
    private void publishCashbackEarnedIfAny(Order order, String username) {
        if (order.totalCashbackEarned().signum() > 0) {
            publisher.publishEvent(AuditEvent.of(EventType.CASHBACK_EARNED, username,
                    Map.of("orderId", order.id(),
                            "orderNumber", order.orderNumber(),
                            "amount", order.totalCashbackEarned())));
        }
    }

    /**
     * Desconto exige permissão própria, e a checagem é programática porque depende do <b>corpo</b>
     * da requisição — {@code @PreAuthorize} decide antes de olhar o payload. Vender e abater não
     * são a mesma autorização: registrar venda é operação de caixa, conceder desconto é decisão
     * comercial.
     */
    private void requireDiscountAuthority(List<SaleItemCommand> items, Authentication authentication) {
        boolean hasDiscount = items.stream()
                .map(SaleItemCommand::discountAmount)
                .anyMatch(discount -> discount != null && discount.compareTo(BigDecimal.ZERO) > 0);
        if (!hasDiscount) {
            return;
        }
        boolean allowed = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(DISCOUNT_AUTHORITY::equals);
        if (!allowed) {
            throw new AccessDeniedException(
                    "Conceder desconto exige a permissão " + DISCOUNT_AUTHORITY + ".");
        }
    }

    /** Admin e dev encerram o caixa de qualquer operador; os demais, só o próprio. */
    private static boolean canCloseAnySession(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_DEV".equals(a.getAuthority()));
    }
}
