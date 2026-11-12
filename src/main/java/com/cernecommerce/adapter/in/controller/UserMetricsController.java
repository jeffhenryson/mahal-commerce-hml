package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.exception.user.TargetProtectedException;
import com.cernecommerce.core.domain.exception.user.UserCustomerNotLinkedException;
import com.cernecommerce.core.domain.exception.user.UserNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.usermetrics.UserMetrics;
import com.cernecommerce.core.ports.in.UserMetricsUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Perfil > métricas. Cada assunto em duas formas com a mesma resposta: {@code /users/me/...} (o
 * próprio usuário, só autenticado) e {@code /users/{id}/...} (ficha do admin, {@code USER_READ} + a
 * permissão do assunto). Sem a permissão → 403, antes de procurar o usuário; {@code id} inexistente →
 * 404. Contas de dev ficam fora da ficha para quem não é dev, como em {@code GET /users/{id}}.
 *
 * <p>Os literais {@code /me/...} têm precedência sobre {@code /{id}/...} no mapeamento.</p>
 */
@RestController
@RequestMapping("/users")
@Tag(name = "Usuários — métricas", description = "Desempenho e atividade do próprio usuário e da ficha do admin")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class UserMetricsController {

    private final UserMetricsUseCase metrics;
    private final UserUseCase users;

    public UserMetricsController(UserMetricsUseCase metrics, UserUseCase users) {
        this.metrics = metrics;
        this.users = users;
    }

    // ── performance ────────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Desempenho do próprio usuário: vendas no PDV, caixas e comandas")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "INVALID_REPORT_PERIOD", content = @Content)
    @GetMapping("/me/performance")
    @PreAuthorize("isAuthenticated()") // self-service: só os dados do próprio token
    public UserMetrics.Performance myPerformance(Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return metrics.performance(me(auth).getUsername(), from, to);
    }

    @Operation(summary = "Desempenho de um usuário (ficha do admin)",
            description = "pdv: pedidos pagos nas sessões de caixa dele, mesma regra de /orders/analytics/operators. "
                    + "cash: sessões abertas/fechadas no período; totalSold soma o total recebido das fechadas. "
                    + "tables: comandas abertas por ele; itemsLaunched conta linhas lançadas por ele em qualquer "
                    + "comanda (só a partir da V190).")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "INVALID_REPORT_PERIOD", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sem USER_READ e ORDER_READ/PDV_READ", content = @Content)
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND", content = @Content)
    @GetMapping("/{id}/performance")
    @PreAuthorize("hasAuthority('USER_READ') and hasAnyAuthority('ORDER_READ', 'PDV_READ')")
    public UserMetrics.Performance performance(@PathVariable Long id, Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return metrics.performance(byId(id, auth).getUsername(), from, to);
    }

    // ── cash-sessions ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Últimas sessões de caixa do próprio usuário",
            description = "Ordem openedAt desc. No caixa aberto, totalSold é o parcial e difference é null.")
    @ApiResponse(responseCode = "200", description = "OK")
    @GetMapping("/me/cash-sessions")
    @PreAuthorize("isAuthenticated()") // self-service: só os dados do próprio token
    public PageResult<UserMetrics.CashSessionItem> myCashSessions(Authentication auth,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return metrics.cashSessions(me(auth).getUsername(), page, size);
    }

    @Operation(summary = "Últimas sessões de caixa de um usuário (ficha do admin)")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Sem USER_READ e PDV_SESSION_READ_ANY", content = @Content)
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND", content = @Content)
    @GetMapping("/{id}/cash-sessions")
    // Caixa de OUTRO operador: mesma permissão de GET /pdv/sessions/{id} (V163), não PDV_READ, que
    // o atendente tem.
    @PreAuthorize("hasAuthority('USER_READ') and hasAuthority('PDV_SESSION_READ_ANY')")
    public PageResult<UserMetrics.CashSessionItem> cashSessions(@PathVariable Long id, Authentication auth,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return metrics.cashSessions(byId(id, auth).getUsername(), page, size);
    }

    // ── access-log ─────────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Acessos do próprio usuário (login, logout, login falho)",
            description = "Ordem timestamp desc. from/to opcionais. userAgent é null nos eventos anteriores à V190.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "INVALID_REPORT_PERIOD", content = @Content)
    @GetMapping("/me/access-log")
    @PreAuthorize("isAuthenticated()") // self-service: só os dados do próprio token
    public PageResult<UserMetrics.AccessLogItem> myAccessLog(Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return metrics.accessLog(me(auth).getUsername(), from, to, page, size);
    }

    @Operation(summary = "Acessos de um usuário (ficha do admin)")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "INVALID_REPORT_PERIOD", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sem USER_READ e AUDIT_READ", content = @Content)
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND", content = @Content)
    @GetMapping("/{id}/access-log")
    @PreAuthorize("hasAuthority('USER_READ') and hasAuthority('AUDIT_READ')")
    public PageResult<UserMetrics.AccessLogItem> accessLog(@PathVariable Long id, Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return metrics.accessLog(byId(id, auth).getUsername(), from, to, page, size);
    }

    // ── attendance ─────────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Frequência do próprio usuário: dias com login",
            description = "date no fuso America/Sao_Paulo, do mais recente ao mais antigo. lastLogoutAt é null "
                    + "quando não houve logout no dia.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "INVALID_REPORT_PERIOD", content = @Content)
    @GetMapping("/me/attendance")
    @PreAuthorize("isAuthenticated()") // self-service: só os dados do próprio token
    public UserMetrics.Attendance myAttendance(Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return metrics.attendance(me(auth).getUsername(), from, to);
    }

    @Operation(summary = "Frequência de um usuário (ficha do admin)")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "INVALID_REPORT_PERIOD", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sem USER_READ e AUDIT_READ", content = @Content)
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND", content = @Content)
    @GetMapping("/{id}/attendance")
    @PreAuthorize("hasAuthority('USER_READ') and hasAuthority('AUDIT_READ')")
    public UserMetrics.Attendance attendance(@PathVariable Long id, Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return metrics.attendance(byId(id, auth).getUsername(), from, to);
    }

    // ── customer-orders ────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Compras do próprio usuário como cliente",
            description = "Usa o cliente vinculado (PLAT-F005). summary vale para o período inteiro e exclui "
                    + "pedidos não pagos, cancelados e reembolsados; lastOrderAt é o último pedido em qualquer data. "
                    + "Página em createdAt desc, com todos os status.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "INVALID_REPORT_PERIOD", content = @Content)
    @ApiResponse(responseCode = "404", description = "USER_CUSTOMER_NOT_LINKED", content = @Content)
    @GetMapping("/me/customer-orders")
    @PreAuthorize("isAuthenticated()") // self-service: só os dados do próprio token
    public UserMetrics.CustomerOrders myCustomerOrders(Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return metrics.customerOrders(customerId(me(auth)), from, to, page, size);
    }

    @Operation(summary = "Compras de um usuário como cliente (ficha do admin)")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "INVALID_REPORT_PERIOD", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sem USER_READ e ORDER_READ", content = @Content)
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND ou USER_CUSTOMER_NOT_LINKED",
            content = @Content)
    @GetMapping("/{id}/customer-orders")
    @PreAuthorize("hasAuthority('USER_READ') and hasAuthority('ORDER_READ')")
    public UserMetrics.CustomerOrders customerOrders(@PathVariable Long id, Authentication auth,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return metrics.customerOrders(customerId(byId(id, auth)), from, to, page, size);
    }

    // ── resolução do usuário ───────────────────────────────────────────────────────────────────

    private User me(Authentication auth) {
        return users.findByUsername(auth.getName()).orElseThrow(() -> new UserNotFoundException(auth.getName()));
    }

    private User byId(Long id, Authentication auth) {
        // Mesmo critério de GET /users/{id}: conta de dev não aparece para quem não é dev.
        if (users.isDevAccount(id) && !AuthUtils.isDevAccess(auth)) {
            throw new TargetProtectedException();
        }
        return users.getUserById(id);
    }

    private static Long customerId(User user) {
        if (user.getCustomerId() == null) {
            throw new UserCustomerNotLinkedException(user.getUsername());
        }
        return user.getCustomerId();
    }
}
