package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.OrderCancelRequest;
import com.cernecommerce.adapter.in.dtos.request.OrderRefundRequest;
import com.cernecommerce.adapter.in.dtos.request.DeliveryRequest;
import com.cernecommerce.adapter.in.dtos.request.OrderStatusRequest;
import com.cernecommerce.adapter.in.dtos.request.OrderBulkStatusRequest;
import com.cernecommerce.adapter.in.dtos.request.OrderPaymentCorrectionRequest;
import com.cernecommerce.adapter.in.dtos.request.RefundItemLotRequest;
import com.cernecommerce.adapter.in.dtos.response.OrderAdminResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderBulkStatusResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.PaymentCorrectionHistoryDTO;
import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.adapter.in.dtos.response.OrderSummaryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SaleReceiptResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TopProductResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pedido.OrderFilter;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.OrderReportUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.domain.model.crm.Customer;
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
import jakarta.validation.constraints.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Visão de <b>pedidos do administrador</b> — atravessa canais.
 *
 * <p>Separada de {@code PdvController} porque o recorte é outro: o PDV enxerga a operação de um
 * caixa, esta superfície enxerga o pedido independentemente de ele ter nascido no balcão ou no
 * site. Aqui também aparecem <b>custo e margem</b>, que o DTO do PDV omite de propósito —
 * {@code PDV_READ} é a permissão mais distribuída do módulo.</p>
 */
@RestController
@RequestMapping("/orders")
@Tag(name = "Pedidos", description = "Visão do administrador, de todos os canais")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class OrdersController {

    private final OrderUseCase orderUseCase;
    private final OrderReportUseCase orderReportUseCase;
    private final CrmUseCase crmUseCase;
    private final PdvUseCase pdvUseCase;
    private final OrderDTOConverter orderConverter;
    private final ApplicationEventPublisher publisher;
    private final ReceivableUseCase receivableUseCase;

    public OrdersController(OrderUseCase orderUseCase, OrderReportUseCase orderReportUseCase,
            CrmUseCase crmUseCase, PdvUseCase pdvUseCase, OrderDTOConverter orderConverter,
            ApplicationEventPublisher publisher, ReceivableUseCase receivableUseCase) {
        this.receivableUseCase = receivableUseCase;
        this.orderUseCase = orderUseCase;
        this.orderReportUseCase = orderReportUseCase;
        this.crmUseCase = crmUseCase;
        this.pdvUseCase = pdvUseCase;
        this.orderConverter = orderConverter;
        this.publisher = publisher;
    }

    /**
     * Resolve {@code customerName} em lote para os pedidos já convertidos — evita uma consulta por
     * linha numa página de até 100 pedidos (EST-Vendas: nome do cliente denormalizado).
     */
    private List<OrderAdminResponseDTO> enrichCustomerNames(List<OrderAdminResponseDTO> content) {
        List<Long> customerIds = content.stream()
                .map(OrderAdminResponseDTO::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (customerIds.isEmpty()) {
            return content;
        }
        Map<Long, String> names = crmUseCase.findCustomerNames(customerIds);
        content.forEach(dto -> dto.setCustomerName(names.get(dto.getCustomerId())));
        return content;
    }

    @Operation(summary = "Lista pedidos com filtros, do mais recente para o mais antigo",
            description = "Todo filtro é opcional. O período incide sobre a data de criação. PDV-F026: "
                    + "sessionId (caixa), comandaId (mesa) e orderNumber (exato); cada linha traz "
                    + "paymentMethods, os métodos CAPTURED do pedido.")
    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<PageResult<OrderAdminResponseDTO>> listOrders(
            @RequestParam(required = false) SalesChannel channel,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long comandaId,
            @RequestParam(required = false) String orderNumber,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<OrderAdminResponseDTO> result = orderConverter.toAdminResponse(orderUseCase.listOrders(
                new OrderFilter(channel, status, customerId, from, to, sessionId, comandaId, orderNumber), page, size));
        enrichCustomerNames(result.content());
        enrichPaymentMethods(result.content());
        enrichOperatorNames(result.content());
        return ResponseEntity.ok(result);
    }

    /** PED-F003 — o operador vem do caixa que liquidou o pedido; uma consulta por página. */
    private void enrichOperatorNames(List<OrderAdminResponseDTO> content) {
        List<Long> sessionIds = content.stream()
                .map(OrderAdminResponseDTO::getSessionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (sessionIds.isEmpty()) {
            return;
        }
        Map<Long, String> operators = pdvUseCase.getSessionOperators(sessionIds);
        content.stream().filter(dto -> dto.getSessionId() != null)
                .forEach(dto -> dto.setOperatorName(operators.get(dto.getSessionId())));
    }

    /** PDV-F026 — uma consulta por página, não um recibo por pedido. */
    private void enrichPaymentMethods(List<OrderAdminResponseDTO> content) {
        List<Long> ids = content.stream().map(OrderAdminResponseDTO::getId).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, List<PaymentMethod>> methods = orderUseCase.getCapturedPaymentMethods(ids);
        content.forEach(dto -> dto.setPaymentMethods(methods.getOrDefault(dto.getId(), List.of())));
    }

    @Operation(summary = "Resumo agregado de vendas do período",
            description = "from e to são obrigatórios (máx. 366 dias). Totais de receita e produtos "
                    + "mais vendidos só contam pedidos com pagamento confirmado (PAGO em diante, "
                    + "exceto REEMBOLSADO); ordersByStatus mostra a distribuição completa, incluindo "
                    + "CRIADO/AGUARDANDO_PAGAMENTO/CANCELADO.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OrderSummaryResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "from/to ausentes, from depois de to, ou intervalo maior que o teto permitido", content = @Content)
    })
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<OrderSummaryResponseDTO> getSummary(
            @RequestParam(required = false) SalesChannel channel,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(orderConverter.toSummaryResponse(
                orderReportUseCase.getSummary(channel, status, customerId, from, to)));
    }

    @Operation(summary = "Produtos mais vendidos do período, por receita ou por quantidade",
            description = "from/to são opcionais: omitidos, considera o histórico completo (sem "
                    + "o teto de 366 dias de /summary — pedir o histórico inteiro é o próprio "
                    + "caso de uso). Mesma regra de pagamento confirmado de /summary: só conta "
                    + "pedidos PAGO em diante, exceto REEMBOLSADO.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "from depois de to, intervalo maior que o teto (quando ambos informados), ou sortBy inválido", content = @Content)
    })
    @GetMapping("/analytics/top-products")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<List<TopProductResponseDTO>> getTopProducts(
            @RequestParam(required = false) SalesChannel channel,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            @RequestParam(defaultValue = "revenue") @Pattern(regexp = "revenue|quantity") String sortBy) {
        List<OrderSummary.TopProduct> topProducts = orderReportUseCase.getTopProducts(channel, null,
                customerId, from, to, limit, "quantity".equals(sortBy));
        return ResponseEntity.ok(orderConverter.toTopProductsResponse(topProducts));
    }

    @Operation(summary = "Detalha um pedido, com custo e margem por item")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OrderAdminResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content)
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<OrderAdminResponseDTO> getOrder(@PathVariable("id") Long orderId) {
        OrderAdminResponseDTO dto = orderConverter.toAdminResponse(orderUseCase.getOrder(orderId));
        enrichCustomerNames(List.of(dto));
        enrichOperatorNames(List.of(dto));
        // PDV-F026 — o detalhe deixa de precisar do recibo só para saber como o pedido foi pago.
        dto.setPayments(orderUseCase.getOrderPayments(orderId).stream().map(orderConverter::toResponse).toList());
        // CRM-F010 — situação do marcado, se houver.
        var receivable = receivableUseCase.findByOrderId(orderId).orElse(null);
        dto.setPaymentStatus(OrderDTOConverter.paymentStatus(receivable));
        dto.setReceivableId(receivable == null ? null : receivable.id());
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Corrige a forma de pagamento do pedido, sem perder o registro do erro (PDV-F030)",
            description = "As linhas CAPTURED vigentes passam a CORRECTED (ficam no pedido como lastro e "
                    + "saem de payment-totals e da conferência do caixa); as informadas nascem CAPTURED. "
                    + "A soma tem que ser exatamente totalPayable — sem troco, nem em DINHEIRO; se o "
                    + "pedido tinha troco, changeAmount vai a zero. Com o caixa do pedido aberto basta "
                    + "ORDER_PAYMENT_CORRECT; depois do fechamento exige ORDER_PAYMENT_CORRECT_CLOSED e a "
                    + "divergência por método fica registrada no caixa fechado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Corrigido", content = @Content(schema = @Schema(implementation = OrderAdminResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "PAYMENT_TOTAL_MISMATCH, REASON_REQUIRED ou INVALID_PAYMENT_METHOD", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "CASH_SESSION_CLOSED, ORDER_NOT_CORRECTABLE ou GATEWAY_PAYMENT_NOT_CORRECTABLE", content = @Content)
    })
    @PostMapping("/{id}/payments/correction")
    @PreAuthorize("hasAnyAuthority('ORDER_PAYMENT_CORRECT', 'ORDER_PAYMENT_CORRECT_CLOSED')")
    public ResponseEntity<OrderAdminResponseDTO> correctPayments(@PathVariable("id") Long orderId,
            @Valid @RequestBody OrderPaymentCorrectionRequest request, Authentication authentication) {
        boolean canCorrectClosed = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ORDER_PAYMENT_CORRECT_CLOSED"::equals);
        OrderUseCase.PaymentCorrectionResult result = orderUseCase.correctPayments(orderId,
                orderConverter.toPaymentCommands(request.getPayments()), request.getReason(),
                authentication.getName(), canCorrectClosed);

        publisher.publishEvent(AuditEvent.of(EventType.ORDER_PAYMENT_CORRECTED, authentication.getName(),
                Map.of("orderId", orderId,
                        "orderNumber", String.valueOf(result.order().orderNumber()),
                        "correctionId", result.correction().id(),
                        "sessionWasClosed", result.correction().sessionWasClosed(),
                        "before", result.before().stream().map(OrdersController::auditLine).toList(),
                        "after", result.after().stream().map(OrdersController::auditLine).toList(),
                        "reason", result.correction().reason())));

        OrderAdminResponseDTO dto = orderConverter.toAdminResponse(result.order());
        enrichCustomerNames(List.of(dto));
        dto.setPayments(orderUseCase.getOrderPayments(orderId).stream().map(orderConverter::toResponse).toList());
        return ResponseEntity.ok(dto);
    }

    private static Map<String, Object> auditLine(OrderPayment payment) {
        return Map.of("method", payment.method().name(), "amount", payment.amount());
    }

    @Operation(summary = "Histórico de correções da forma de pagamento (PDV-F030)",
            description = "Da mais antiga para a mais recente. Pedido sem correção responde [].")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content)
    })
    @GetMapping("/{id}/payment-history")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<List<PaymentCorrectionHistoryDTO>> getPaymentHistory(@PathVariable("id") Long orderId) {
        return ResponseEntity.ok(orderUseCase.getPaymentHistory(orderId).stream()
                .map(entry -> new PaymentCorrectionHistoryDTO(entry.correction().id(),
                        entry.correction().correctedAt(), entry.correction().correctedBy(),
                        entry.correction().reason(),
                        entry.before().stream().map(orderConverter::toResponse).toList(),
                        entry.after().stream().map(orderConverter::toResponse).toList()))
                .toList());
    }

    @Operation(summary = "Recibo do pedido — funciona para BALCAO, MESA e MARKETPLACE",
            description = "Equivalente a GET /pdv/sales/{id}/receipt, mas sem exigir PDV_READ: "
                    + "pedido de marketplace nunca passa por um caixa, e pedido de MESA nasce de "
                    + "um fechamento de comanda, não de um sale — o endpoint do PDV responderia "
                    + "404 para ele. O endpoint do PDV continua existindo, sem mudança, para o "
                    + "fluxo de balcão; o admin já migrou para esta rota nos três canais.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = SaleReceiptResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content)
    })
    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<SaleReceiptResponseDTO> getReceipt(@PathVariable("id") Long orderId) {
        Order order = orderUseCase.getOrder(orderId);
        List<OrderPayment> payments = orderUseCase.getOrderPayments(orderId);
        SaleReceiptResponseDTO receipt = orderConverter.toReceipt(order, payments);
        enrichReceipt(receipt);
        return ResponseEntity.ok(receipt);
    }

    /**
     * Cliente (nome, telefone, CPF) e operador do caixa para o cupom. São enfeites de impressão: se o
     * cliente foi removido do CRM ou a sessão não existe mais, o cupom sai sem eles em vez de falhar.
     */
    private void enrichReceipt(SaleReceiptResponseDTO receipt) {
        if (receipt.getCustomerId() != null) {
            try {
                Customer customer = crmUseCase.findCustomerById(receipt.getCustomerId());
                receipt.setCustomerName(customer.nome());
                receipt.setCustomerPhone(customer.contato());
                receipt.setCustomerDocument(customer.cpf());
            } catch (RuntimeException e) {
                // cliente fora do CRM — o cupom sai só com o id
            }
        }
        if (receipt.getSessionId() != null) {
            try {
                receipt.setOperatorName(pdvUseCase.getSession(receipt.getSessionId()).operator());
            } catch (RuntimeException e) {
                // sessão ausente — o cupom sai sem operador
            }
        }
    }

    @Operation(summary = "Avança o pedido na esteira de fulfillment",
            description = "SEPARADO → ENVIADO → ENTREGUE. Pular etapas é recusado com 409. Também cobre "
                    + "a retirada de venda de balcão reservada (PDV-F008): RESERVADO → CONCLUIDO, "
                    + "disparada pelo operador quando o cliente volta para levar a mercadoria.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OrderAdminResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Transição não permitida", content = @Content)
    })
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ORDER_FULFILL')")
    public ResponseEntity<OrderAdminResponseDTO> changeStatus(@PathVariable("id") Long orderId,
            @Valid @RequestBody OrderStatusRequest request, Authentication authentication) {
        Order before = orderUseCase.getOrder(orderId);
        Order order = orderUseCase.changeStatus(orderId, request.getStatus(), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.ORDER_STATUS_CHANGED, authentication.getName(),
                Map.of("orderId", orderId,
                        "orderNumber", String.valueOf(order.orderNumber()),
                        "from", before.status().name(),
                        "to", order.status().name())));
        return ResponseEntity.ok(orderConverter.toAdminResponse(order));
    }

    @Operation(summary = "Move vários pedidos para o mesmo estado (\"Liberar selecionados/todos\")",
            description = "Equivale a chamar POST /orders/{id}/status para cada id, na ordem dada: cada "
                    + "pedido segue a máquina de estados e é gravado na própria transação, então um "
                    + "recusado não desfaz os outros. Os recusados voltam em `failed` com o mesmo "
                    + "errorCode do endpoint unitário (ORDER_NOT_FOUND, INVALID_STATUS_TRANSITION, "
                    + "CONCURRENT_UPDATE), mais INVALID_ORDER_STATE para invariante de domínio do pedido. "
                    + "Só aceita a esteira (SEPARADO/ENVIADO/ENTREGUE) e a retirada RESERVADO → CONCLUIDO. "
                    + "Máximo de 200 ids.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Processado — ver ok/failed",
                    content = @Content(schema = @Schema(implementation = OrderBulkStatusResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Lista vazia, acima de 200 ou status ausente", content = @Content)
    })
    @PostMapping("/bulk-status")
    @PreAuthorize("hasAuthority('ORDER_FULFILL')")
    public ResponseEntity<OrderBulkStatusResponseDTO> changeStatusInBulk(
            @Valid @RequestBody OrderBulkStatusRequest request, Authentication authentication) {
        List<Long> ok = new ArrayList<>();
        List<OrderBulkStatusResponseDTO.Failure> failed = new ArrayList<>();
        for (Long orderId : request.getOrderIds().stream().distinct().toList()) {
            try {
                Order before = orderUseCase.getOrder(orderId);
                Order order = orderUseCase.changeStatus(orderId, request.getStatus(), authentication.getName());
                publisher.publishEvent(AuditEvent.of(EventType.ORDER_STATUS_CHANGED, authentication.getName(),
                        Map.of("orderId", orderId,
                                "orderNumber", String.valueOf(order.orderNumber()),
                                "from", before.status().name(),
                                "to", order.status().name(),
                                "bulk", true)));
                ok.add(orderId);
            } catch (OrderNotFoundException e) {
                failed.add(new OrderBulkStatusResponseDTO.Failure(orderId, "ORDER_NOT_FOUND", e.getMessage()));
            } catch (InvalidOrderStatusTransitionException e) {
                failed.add(new OrderBulkStatusResponseDTO.Failure(orderId, "INVALID_STATUS_TRANSITION", e.getMessage()));
            } catch (ObjectOptimisticLockingFailureException e) {
                failed.add(new OrderBulkStatusResponseDTO.Failure(orderId, "CONCURRENT_UPDATE",
                        "pedido alterado por outra operação, tente de novo"));
            } catch (IllegalArgumentException | IllegalStateException e) {
                // PED-C008 — invariante de domínio de UM pedido não pode parar o laço: os anteriores
                // já foram gravados e auditados, e o cliente ficaria sem saber quais. A mensagem do
                // domínio não sai (cita campo interno), como no handler global.
                failed.add(new OrderBulkStatusResponseDTO.Failure(orderId, "INVALID_ORDER_STATE",
                        "o pedido não está num estado que permita esta mudança"));
            }
        }
        return ResponseEntity.ok(new OrderBulkStatusResponseDTO(ok, failed));
    }

    @Operation(summary = "Edita a entrega do pedido depois da venda (PDV-F022)",
            description = "Mesmo corpo da entrega da venda, todos os campos opcionais: null mantém "
                    + "o valor atual, texto em branco apaga. Serve para preencher depois os códigos "
                    + "da 99 (pickupCode/dropoffCode), o rastreio dos Correios, o entregador ou "
                    + "corrigir o endereço. type e fee são congelados na venda — enviá-los com outro "
                    + "valor dá 400 INVALID_DELIVERY.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OrderAdminResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_DELIVERY — fee/type alterados ou endereço incompleto", content = @Content),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "ORDER_HAS_NO_DELIVERY, ou ORDER_DELIVERY_NOT_EDITABLE (cancelado/reembolsado)", content = @Content)
    })
    @PatchMapping("/{id}/delivery")
    @PreAuthorize("hasAuthority('ORDER_FULFILL')")
    public ResponseEntity<OrderAdminResponseDTO> updateDelivery(@PathVariable("id") Long orderId,
            @Valid @RequestBody DeliveryRequest request, Authentication authentication) {
        Order order = orderUseCase.updateDelivery(orderId, orderConverter.toDeliveryPatch(request),
                authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.ORDER_DELIVERY_UPDATED, authentication.getName(),
                Map.of("orderId", orderId, "orderNumber", String.valueOf(order.orderNumber()))));
        return ResponseEntity.ok(orderConverter.toAdminResponse(order));
    }

    @Operation(summary = "Cancela um pedido ANTES de pagamento confirmado e libera a reserva de estoque",
            description = "Só pedidos sem pagamento confirmado (CRIADO/AGUARDANDO_PAGAMENTO). Pedido "
                    + "com pagamento confirmado usa /refund — cancelar e reembolsar são eventos "
                    + "diferentes (PDV-F007).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelado", content = @Content(schema = @Schema(implementation = OrderAdminResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Transição não permitida: pagamento já confirmado (use /refund), ou já CANCELADO", content = @Content)
    })
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ORDER_CANCEL')")
    public ResponseEntity<OrderAdminResponseDTO> cancelOrder(@PathVariable("id") Long orderId,
            @Valid @RequestBody OrderCancelRequest request, Authentication authentication) {
        Order before = orderUseCase.getOrder(orderId);
        Order order = orderUseCase.cancelOrder(orderId, request.getReason(), authentication.getName());
        Map<String, Object> details = new HashMap<>();
        details.put("orderId", orderId);
        details.put("orderNumber", String.valueOf(order.orderNumber()));
        details.put("statusBefore", before.status().name());
        details.put("reason", request.getReason());
        details.put("skus", order.items().stream().map(item -> item.sku()).toList());
        publisher.publishEvent(
                AuditEvent.of(EventType.ORDER_CANCELLED, authentication.getName(), details));
        return ResponseEntity.ok(orderConverter.toAdminResponse(order));
    }

    @Operation(summary = "Reembolsa um pedido DEPOIS de pagamento confirmado",
            description = "Devolve a mercadoria ao estoque, estorna cada pagamento CAPTURED com uma "
                    + "linha REFUNDED do mesmo método e valor, e reverte no ledger de cashback todo "
                    + "ganho EARNED do pedido — tudo na mesma transação. Só pedidos com pagamento "
                    + "confirmado (PAGO em diante); pedido pré-pagamento usa /cancel.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reembolsado", content = @Content(schema = @Schema(implementation = OrderAdminResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Transição não permitida: sem pagamento confirmado (use /cancel), ou já CANCELADO/REEMBOLSADO", content = @Content)
    })
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('ORDER_REFUND')")
    public ResponseEntity<OrderAdminResponseDTO> refundOrder(@PathVariable("id") Long orderId,
            @Valid @RequestBody OrderRefundRequest request, Authentication authentication) {
        Order before = orderUseCase.getOrder(orderId);
        List<OrderUseCase.RefundItemLot> itemLots = request.getItemLots() == null ? List.of()
                : request.getItemLots().stream()
                        .map(l -> new OrderUseCase.RefundItemLot(l.getSku(), l.getLotCode(), l.getExpiryDate()))
                        .toList();
        Order order = orderUseCase.refundOrder(orderId, request.getReason(), authentication.getName(), itemLots);
        Map<String, Object> details = new HashMap<>();
        details.put("orderId", orderId);
        details.put("orderNumber", String.valueOf(order.orderNumber()));
        details.put("statusBefore", before.status().name());
        details.put("reason", request.getReason());
        details.put("skus", order.items().stream().map(item -> item.sku()).toList());
        publisher.publishEvent(
                AuditEvent.of(EventType.ORDER_REFUNDED, authentication.getName(), details));
        return ResponseEntity.ok(orderConverter.toAdminResponse(order));
    }
}
