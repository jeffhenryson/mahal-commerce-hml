package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.pedido.SalesAnalytics;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.domain.model.pedido.SalesGoal;
import com.cernecommerce.core.ports.in.SalesAnalyticsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Análise de Vendas e meta mensal (pendência 9). Mesmos pedidos de {@code totalRevenueNet} do summary,
 * agrupados no fuso America/Sao_Paulo; período obrigatório de até 366 dias.
 */
@RestController
@Tag(name = "Análise de Vendas", description = "Agregados da aba Vendas › Análise e meta mensal")
@SecurityRequirement(name = "bearerAuth")
public class SalesAnalyticsController {

    private final SalesAnalyticsUseCase useCase;
    private final ApplicationEventPublisher publisher;

    public SalesAnalyticsController(SalesAnalyticsUseCase useCase, ApplicationEventPublisher publisher) {
        this.useCase = useCase;
        this.publisher = publisher;
    }

    @Operation(summary = "Mapa de calor: receita por dia da semana e hora",
            description = "weekday ISO (1 = segunda … 7 = domingo) e hour 0–23, no fuso America/Sao_Paulo. Só as "
                    + "células com venda.")
    @ApiResponse(responseCode = "400", description = "INVALID_REPORT_PERIOD", content = @Content)
    @GetMapping("/orders/analytics/heatmap")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public List<SalesAnalytics.HeatmapCell> heatmap(@RequestParam(required = false) SalesChannel channel,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return useCase.heatmap(channel, warehouseCode, from, to);
    }

    @Operation(summary = "Receita por forma de pagamento",
            description = "Linhas CAPTURED e Marcado (ON_ACCOUNT) por método, com o troco descontado do dinheiro; "
                    + "pedido pago com dois métodos entra em cada um pelo valor real. A soma bate com "
                    + "totalRevenueNet. `orders` conta pedidos distintos. Maior valor primeiro.")
    @ApiResponse(responseCode = "400", description = "INVALID_REPORT_PERIOD", content = @Content)
    @GetMapping("/orders/analytics/payment-methods")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public List<SalesAnalytics.PaymentMethodTotal> paymentMethods(@RequestParam(required = false) SalesChannel channel,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return useCase.paymentMethods(channel, warehouseCode, from, to);
    }

    @Operation(summary = "Receita por operador",
            description = "O operador é o do caixa que registrou/liquidou o pedido (o mesmo de "
                    + "OrderAdminResponseDto.operatorName). Pedidos sem caixa (pagos online) vêm numa linha com "
                    + "operatorUserId e operatorName nulos. Maior receita primeiro.")
    @ApiResponse(responseCode = "400", description = "INVALID_REPORT_PERIOD", content = @Content)
    @GetMapping("/orders/analytics/operators")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public List<SalesAnalytics.OperatorTotal> operators(@RequestParam(required = false) SalesChannel channel,
            @RequestParam(required = false) String warehouseCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return useCase.operators(channel, warehouseCode, from, to);
    }

    @Operation(summary = "Clientes do período, paginado",
            description = "sortBy = revenue | orders | lastPurchase (desc). size até 100. `anonymous` soma as vendas "
                    + "sem cliente identificado.")
    @ApiResponse(responseCode = "400", description = "INVALID_REPORT_PERIOD, sortBy ou size inválidos", content = @Content)
    @GetMapping("/orders/analytics/customers")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public SalesAnalytics.CustomerRanking customers(@RequestParam(required = false) SalesChannel channel,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "revenue") String sortBy) {
        return useCase.customers(channel, from, to, page, size, customerSort(sortBy));
    }

    @Operation(summary = "Encalhados: produtos com saldo que não venderam no período",
            description = "Produtos ativos, publicados e não-kit com saldo > 0 em warehouseCode e quantitySold <= "
                    + "maxSold no período, por valor parado (stock × unitCost) desc. unitCost: custo médio, senão o "
                    + "cadastrado. lastSoldAt é a última venda em qualquer data, nulo se nunca vendeu.")
    @ApiResponse(responseCode = "400", description = "warehouseCode ausente, limit fora de 1–100 ou período inválido",
            content = @Content)
    @GetMapping("/orders/analytics/slow-movers")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public List<SalesAnalytics.SlowMover> slowMovers(@RequestParam String warehouseCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") BigDecimal maxSold,
            @RequestParam(defaultValue = "20") int limit) {
        return useCase.slowMovers(warehouseCode, from, to, maxSold, limit);
    }

    @Operation(summary = "Meta de faturamento do mês")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Mês fora do formato yyyy-MM", content = @Content),
            @ApiResponse(responseCode = "404", description = "SALES_GOAL_NOT_FOUND — sem meta para o mês", content = @Content)
    })
    @GetMapping("/vendas/metas/{month}")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public GoalResponse getGoal(@PathVariable String month) {
        return GoalResponse.of(useCase.getGoal(month(month)));
    }

    @Operation(summary = "Define a meta de faturamento do mês (idempotente)",
            description = "amount > 0 com até 2 casas; senão 400 INVALID_AMOUNT. Exige SALES_GOAL_MANAGE. Auditado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "INVALID_AMOUNT ou mês inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PutMapping("/vendas/metas/{month}")
    @PreAuthorize("hasAuthority('SALES_GOAL_MANAGE')")
    public GoalResponse saveGoal(@PathVariable String month, @Valid @RequestBody GoalRequest request,
            Authentication authentication) {
        SalesGoal saved = useCase.saveGoal(month(month), request.amount(), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.SALES_GOAL_UPDATED, authentication.getName(),
                Map.of("month", saved.month().toString(), "amount", saved.amount().toPlainString())));
        return GoalResponse.of(saved);
    }

    public record GoalRequest(@NotNull @Schema(example = "15000.00") BigDecimal amount) {
    }

    public record GoalResponse(@Schema(example = "2026-10") String month, BigDecimal amount, String updatedBy,
            Instant updatedAt) {
        static GoalResponse of(SalesGoal goal) {
            return new GoalResponse(goal.month().toString(), goal.amount(), goal.updatedBy(), goal.updatedAt());
        }
    }

    private static YearMonth month(String raw) {
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Mês deve estar no formato yyyy-MM");
        }
    }

    private static SalesAnalytics.CustomerSort customerSort(String raw) {
        return switch (raw == null ? "revenue" : raw.toLowerCase(Locale.ROOT)) {
            case "revenue" -> SalesAnalytics.CustomerSort.REVENUE;
            case "orders" -> SalesAnalytics.CustomerSort.ORDERS;
            case "lastpurchase" -> SalesAnalytics.CustomerSort.LAST_PURCHASE;
            default -> throw new IllegalArgumentException("sortBy deve ser revenue, orders ou lastPurchase");
        };
    }
}
