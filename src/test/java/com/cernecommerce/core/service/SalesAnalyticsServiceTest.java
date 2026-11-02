package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pedido.InvalidReportPeriodException;
import com.cernecommerce.core.domain.exception.pedido.InvalidSalesGoalAmountException;
import com.cernecommerce.core.domain.exception.pedido.SalesGoalNotFoundException;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.pedido.SalesAnalytics;
import com.cernecommerce.core.domain.model.pedido.SalesAnalytics.PaidOrder;
import com.cernecommerce.core.domain.model.pedido.SalesGoal;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.pedido.SalesAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesAnalyticsServiceTest {

    private static final Instant FROM = Instant.parse("2026-10-01T03:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-01T02:59:59Z");

    @Mock SalesAnalyticsRepository repository;
    @Mock CustomerRepository customerRepository;

    SalesAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new SalesAnalyticsService(repository, customerRepository,
                Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC));
    }

    private static PaidOrder order(long id, String soldAtUtc, String amount, Long customerId, Long sessionId) {
        return new PaidOrder(id, Instant.parse(soldAtUtc), new BigDecimal(amount), customerId, sessionId);
    }

    @Test
    void heatmap_agrupaNoFusoDaLoja() {
        // 2026-10-17T01:00Z é sexta, 22h em São Paulo (UTC-3) — não sábado 1h.
        when(repository.paidOrders(null, null, FROM, TO)).thenReturn(List.of(
                order(1, "2026-10-17T01:00:00Z", "100.00", null, null),
                order(2, "2026-10-17T01:30:00Z", "50.00", null, null),
                order(3, "2026-10-17T05:00:00Z", "10.00", null, null)));

        List<SalesAnalytics.HeatmapCell> cells = service.heatmap(null, null, FROM, TO);

        assertThat(cells).containsExactly(
                new SalesAnalytics.HeatmapCell(5, 22, new BigDecimal("150.00"), 2),
                new SalesAnalytics.HeatmapCell(6, 2, new BigDecimal("10.00"), 1));
    }

    @Test
    void periodoAcimaDe366Dias_e400() {
        assertThatThrownBy(() -> service.heatmap(null, null, Instant.parse("2025-01-01T00:00:00Z"), TO))
                .isInstanceOf(InvalidReportPeriodException.class);
        verify(repository, never()).paidOrders(any(), any(), any(), any());
    }

    @Test
    void operators_agrupaPorOperadorEDeixaOnlineSemOperador() {
        when(repository.paidOrders(null, "LOJA-01", FROM, TO)).thenReturn(List.of(
                order(1, "2026-10-10T15:00:00Z", "100.00", null, 10L),
                order(2, "2026-10-10T16:00:00Z", "50.00", null, 11L),
                order(3, "2026-10-10T17:00:00Z", "30.00", null, null)));
        when(repository.sessionOperators(any())).thenReturn(Map.of(10L, "ana", 11L, "ana"));
        when(repository.operatorUserIds(List.of("ana"))).thenReturn(Map.of("ana", 3L));

        List<SalesAnalytics.OperatorTotal> result = service.operators(null, "LOJA-01", FROM, TO);

        assertThat(result).containsExactly(
                new SalesAnalytics.OperatorTotal(3L, "ana", new BigDecimal("150.00"), 2, new BigDecimal("75.00")),
                new SalesAnalytics.OperatorTotal(null, null, new BigDecimal("30.00"), 1, new BigDecimal("30.00")));
    }

    @Test
    void customers_paginaOrdenaEAnonimo() {
        when(repository.paidOrders(null, null, FROM, TO)).thenReturn(List.of(
                order(1, "2026-10-10T15:00:00Z", "100.00", 7L, null),
                order(2, "2026-10-12T15:00:00Z", "20.00", 7L, null),
                order(3, "2026-10-11T15:00:00Z", "200.00", 8L, null),
                order(4, "2026-10-11T16:00:00Z", "40.00", null, null)));
        when(customerRepository.findByIds(List.of(8L))).thenReturn(List.of(
                Customer.of(8L, "Bia", "11999990000", null, null, null, Instant.now(), CustomerStage.NOVO_LEAD)));

        SalesAnalytics.CustomerRanking ranking = service.customers(null, FROM, TO, 0, 1,
                SalesAnalytics.CustomerSort.REVENUE);

        assertThat(ranking.totalElements()).isEqualTo(2);
        assertThat(ranking.totalPages()).isEqualTo(2);
        assertThat(ranking.content()).containsExactly(new SalesAnalytics.CustomerTotal(8L, "Bia", 1,
                new BigDecimal("200.00"), new BigDecimal("200.00"), Instant.parse("2026-10-11T15:00:00Z")));
        assertThat(ranking.anonymous()).isEqualTo(new SalesAnalytics.AnonymousTotal(1, new BigDecimal("40.00")));
    }

    @Test
    void customers_porPedidos() {
        when(repository.paidOrders(null, null, FROM, TO)).thenReturn(List.of(
                order(1, "2026-10-10T15:00:00Z", "100.00", 7L, null),
                order(2, "2026-10-12T15:00:00Z", "20.00", 7L, null),
                order(3, "2026-10-11T15:00:00Z", "200.00", 8L, null)));
        when(customerRepository.findByIds(any())).thenReturn(List.of());

        SalesAnalytics.CustomerRanking ranking = service.customers(null, FROM, TO, 0, 10,
                SalesAnalytics.CustomerSort.ORDERS);

        assertThat(ranking.content()).extracting(SalesAnalytics.CustomerTotal::customerId).containsExactly(7L, 8L);
    }

    @Test
    void slowMovers_exigeDepositoELimite() {
        assertThatThrownBy(() -> service.slowMovers(" ", FROM, TO, null, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.slowMovers("LOJA-01", FROM, TO, null, 101))
                .isInstanceOf(InvalidReportPeriodException.class);

        service.slowMovers("LOJA-01", FROM, TO, null, 20);
        verify(repository).slowMovers(eq("LOJA-01"), eq(FROM), eq(TO), eq(BigDecimal.ZERO), eq(20));
    }

    @Test
    void meta_semCadastro_e404() {
        when(repository.findGoal(YearMonth.of(2026, 10))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getGoal(YearMonth.of(2026, 10)))
                .isInstanceOf(SalesGoalNotFoundException.class);
    }

    @Test
    void meta_valorInvalido_e400() {
        assertThatThrownBy(() -> service.saveGoal(YearMonth.of(2026, 10), BigDecimal.ZERO, "gerente"))
                .isInstanceOf(InvalidSalesGoalAmountException.class);
        assertThatThrownBy(() -> service.saveGoal(YearMonth.of(2026, 10), new BigDecimal("10.001"), "gerente"))
                .isInstanceOf(InvalidSalesGoalAmountException.class);
        verify(repository, never()).saveGoal(any());
    }

    @Test
    void meta_gravaComQuemEQuando() {
        when(repository.saveGoal(any())).thenAnswer(inv -> inv.getArgument(0));

        SalesGoal goal = service.saveGoal(YearMonth.of(2026, 10), new BigDecimal("15000"), "gerente");

        assertThat(goal.amount()).isEqualByComparingTo("15000.00");
        assertThat(goal.amount().scale()).isEqualTo(2);
        assertThat(goal.updatedBy()).isEqualTo("gerente");
        assertThat(goal.updatedAt()).isEqualTo(Instant.parse("2026-11-02T12:00:00Z"));
    }
}
