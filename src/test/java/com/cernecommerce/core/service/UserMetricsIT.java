package com.cernecommerce.core.service;

import com.cernecommerce.adapter.out.persistence.entity.AuditLogEntity;
import com.cernecommerce.core.domain.exception.pedido.InvalidReportPeriodException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.usermetrics.UserMetrics;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.UserMetricsUseCase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Perfil > métricas contra banco real: as consultas de {@link UserMetricsService} e o {@code added_by}. */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
// Mesmo motivo de ComandaHistoryIT: o lançamento por SKU de catálogo é o caminho legado.
@TestPropertySource(properties = "pdv.sessao.legacy-enabled=true")
class UserMetricsIT {

    @Autowired UserMetricsUseCase metrics;
    @Autowired PdvUseCase pdvUseCase;
    @Autowired ComandaUseCase comandaUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired CrmUseCase crmUseCase;

    @PersistenceContext EntityManager em;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private static void loggedAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(username, null, "PDV_READ"));
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static List<PaymentCommand> dinheiro(String valor) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(valor), null));
    }

    private void audit(String username, String action, Instant at, String userAgent) {
        AuditLogEntity e = new AuditLogEntity();
        e.setUsername(username);
        e.setAction(action);
        e.setIpAddress("189.1.2.3");
        e.setTimestamp(at);
        e.setUserAgent(userAgent);
        em.persist(e);
    }

    /**
     * A abre a mesa, B lança um item nela e A lança outro e fecha. O item de B conta para B, mesmo
     * a comanda sendo de A; a venda e a comanda contam para A, dono do caixa.
     */
    @Test
    void performance_contaItemLancadoEmComandaDeOutro_eVendaDoCaixa() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String a = "caixa-" + suffix;
        String b = "garcom-" + suffix;
        String warehouseCode = "UM-" + suffix;
        String sku = "ESS-" + suffix;
        estoqueUseCase.createWarehouse(warehouseCode, "Lounge " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Essência " + suffix, "Essências", List.of(),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("50.000"),
                "carga inicial", a);
        Instant inicio = Instant.now().minus(1, ChronoUnit.MINUTES);
        CashRegisterSession session = pdvUseCase.openSession(a, BigDecimal.ZERO, warehouseCode);

        loggedAs(a);
        Comanda mesa = comandaUseCase.openComanda(session.id(), "Mesa 1", a);
        loggedAs(b);
        comandaUseCase.addItem(mesa.id(), sku, BigDecimal.ONE, b);
        loggedAs(a);
        comandaUseCase.addItem(mesa.id(), sku, BigDecimal.ONE, a);
        flushAndClear();
        comandaUseCase.closeComanda(mesa.id(), dinheiro("50.00"), null, false, null, a);
        flushAndClear();
        Instant fim = Instant.now().plus(1, ChronoUnit.MINUTES);

        String addedBy = (String) em.createQuery(
                "SELECT i.addedBy FROM ComandaItemEntity i WHERE i.comanda.id = :id ORDER BY i.id")
                .setParameter("id", mesa.id()).setMaxResults(1).getSingleResult();
        assertThat(addedBy).isEqualTo(b);

        UserMetrics.Performance doCaixa = metrics.performance(a, inicio, fim);
        assertThat(doCaixa.pdv().orders()).isEqualTo(1);
        assertThat(doCaixa.pdv().revenue()).isEqualByComparingTo("50.00");
        assertThat(doCaixa.pdv().averageTicket()).isEqualByComparingTo("50.00");
        assertThat(doCaixa.cash().opened()).isEqualTo(1);
        assertThat(doCaixa.cash().closed()).isZero();
        assertThat(doCaixa.tables().tabsOpened()).isEqualTo(1);
        assertThat(doCaixa.tables().tabsClosed()).isEqualTo(1);
        assertThat(doCaixa.tables().itemsLaunched()).isEqualTo(1);

        UserMetrics.Performance doGarcom = metrics.performance(b, inicio, fim);
        assertThat(doGarcom.tables().itemsLaunched()).isEqualTo(1);
        assertThat(doGarcom.tables().tabsOpened()).isZero();
        assertThat(doGarcom.pdv().orders()).isZero();
        assertThat(doGarcom.pdv().averageTicket()).isEqualByComparingTo("0");

        // Caixa aberto: totalSold é o parcial, difference é nula.
        PageResult<UserMetrics.CashSessionItem> caixas = metrics.cashSessions(a, 0, 5);
        assertThat(caixas.totalElements()).isEqualTo(1);
        UserMetrics.CashSessionItem aberto = caixas.content().get(0);
        assertThat(aberto.status()).isEqualTo("OPEN");
        assertThat(aberto.difference()).isNull();
        assertThat(aberto.totalSold()).isEqualByComparingTo("50.00");
    }

    /** Login às 23h30 em São Paulo é 02h30 UTC do dia seguinte e conta no dia local. */
    @Test
    void attendance_agrupaPeloDiaDaLoja() {
        String u = "freq-" + UUID.randomUUID().toString().substring(0, 8);
        audit(u, "USER_LOGGED_IN", Instant.parse("2026-11-06T12:00:00Z"), null);
        audit(u, "OAUTH_GOOGLE_LOGIN", Instant.parse("2026-11-06T15:00:00Z"), null);
        audit(u, "USER_LOGGED_OUT", Instant.parse("2026-11-06T20:00:00Z"), null);
        audit(u, "USER_LOGGED_IN", Instant.parse("2026-11-08T02:30:00Z"), null);   // 07/11 23h30 local
        audit(u, "LOGIN_FAILED", Instant.parse("2026-11-08T04:00:00Z"), null);
        audit("outro-" + u, "USER_LOGGED_IN", Instant.parse("2026-11-05T12:00:00Z"), null);
        flushAndClear();

        UserMetrics.Attendance att = metrics.attendance(u, Instant.parse("2026-11-01T03:00:00Z"),
                Instant.parse("2026-11-09T02:59:59Z"));

        assertThat(att.daysWithAccess()).isEqualTo(2);
        assertThat(att.days()).containsExactly(
                new UserMetrics.AttendanceDay(LocalDate.parse("2026-11-07"), Instant.parse("2026-11-08T02:30:00Z"),
                        null),
                new UserMetrics.AttendanceDay(LocalDate.parse("2026-11-06"), Instant.parse("2026-11-06T12:00:00Z"),
                        Instant.parse("2026-11-06T20:00:00Z")));
    }

    @Test
    void accessLog_mapeiaTiposEIgnoraOutrosEventos() {
        String u = "acesso-" + UUID.randomUUID().toString().substring(0, 8);
        audit(u, "USER_LOGGED_IN", Instant.parse("2026-11-06T12:00:00Z"), "Mozilla/5.0");
        audit(u, "USER_UPDATED", Instant.parse("2026-11-06T13:00:00Z"), null);
        audit(u, "OAUTH_GOOGLE_LOGIN", Instant.parse("2026-11-06T14:00:00Z"), null);
        audit(u, "USER_LOGGED_OUT", Instant.parse("2026-11-06T15:00:00Z"), null);
        audit(u, "LOGIN_FAILED", Instant.parse("2026-11-06T16:00:00Z"), null);
        flushAndClear();

        PageResult<UserMetrics.AccessLogItem> page = metrics.accessLog(u, null, null, 0, 20);

        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.content()).extracting(UserMetrics.AccessLogItem::type).containsExactly(
                UserMetrics.AccessType.LOGIN_FAILED, UserMetrics.AccessType.LOGOUT, UserMetrics.AccessType.LOGIN,
                UserMetrics.AccessType.LOGIN);
        assertThat(page.content().get(3).userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(page.content().get(3).ipAddress()).isEqualTo("189.1.2.3");

        assertThat(metrics.accessLog(u, Instant.parse("2026-11-06T13:30:00Z"), Instant.parse("2026-11-06T15:30:00Z"),
                0, 20).totalElements()).isEqualTo(2);
    }

    @Test
    void customerOrders_resumoDoPeriodo_eUltimoPedido() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String a = "caixa-" + suffix;
        String warehouseCode = "UMC-" + suffix;
        String sku = "ESS-" + suffix;
        estoqueUseCase.createWarehouse(warehouseCode, "Lounge " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Essência " + suffix, "Essências", List.of(),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("50.000"),
                "carga inicial", a);
        Long customerId = crmUseCase.createCustomer("Cliente " + suffix,
                "1198" + String.format("%07d", Math.abs(suffix.hashCode()) % 10_000_000), null, null, "PDV").id();
        Instant inicio = Instant.now().minus(1, ChronoUnit.MINUTES);
        CashRegisterSession session = pdvUseCase.openSession(a, BigDecimal.ZERO, warehouseCode);
        Comanda mesa = comandaUseCase.openComanda(session.id(), "Mesa 2", customerId, a);
        comandaUseCase.addItem(mesa.id(), sku, BigDecimal.ONE, a);
        comandaUseCase.addItem(mesa.id(), sku, BigDecimal.ONE, a);
        flushAndClear();
        comandaUseCase.closeComanda(mesa.id(), dinheiro("50.00"), null, false, null, a);
        flushAndClear();
        Instant fim = Instant.now().plus(1, ChronoUnit.MINUTES);

        UserMetrics.CustomerOrders result = metrics.customerOrders(customerId, inicio, fim, 0, 20);

        assertThat(result.summary().orders()).isEqualTo(1);
        assertThat(result.summary().totalSpent()).isEqualByComparingTo("50.00");
        assertThat(result.summary().lastOrderAt()).isNotNull();
        assertThat(result.page().totalElements()).isEqualTo(1);
        UserMetrics.CustomerOrderItem pedido = result.page().content().get(0);
        assertThat(pedido.total()).isEqualByComparingTo("50.00");
        assertThat(pedido.channel()).isEqualTo("MESA");
        assertThat(pedido.itemsCount()).isPositive();

        // Fora do período: resumo zerado, mas o último pedido continua visível.
        UserMetrics.CustomerOrders antes = metrics.customerOrders(customerId, inicio.minus(10, ChronoUnit.DAYS),
                inicio.minus(9, ChronoUnit.DAYS), 0, 20);
        assertThat(antes.summary().orders()).isZero();
        assertThat(antes.summary().lastOrderAt()).isEqualTo(result.summary().lastOrderAt());
        assertThat(antes.page().content()).isEmpty();
    }

    @Test
    void periodoAcimaDe366Dias_eRejeitado() {
        Instant from = Instant.parse("2025-01-01T03:00:00Z");
        Instant to = from.plus(368, ChronoUnit.DAYS);
        assertThatThrownBy(() -> metrics.performance("x", from, to)).isInstanceOf(InvalidReportPeriodException.class);
        assertThatThrownBy(() -> metrics.attendance("x", from, to)).isInstanceOf(InvalidReportPeriodException.class);
        assertThatThrownBy(() -> metrics.accessLog("x", from, to, 0, 20))
                .isInstanceOf(InvalidReportPeriodException.class);
        assertThatThrownBy(() -> metrics.customerOrders(1L, from, to, 0, 20))
                .isInstanceOf(InvalidReportPeriodException.class);
    }
}
