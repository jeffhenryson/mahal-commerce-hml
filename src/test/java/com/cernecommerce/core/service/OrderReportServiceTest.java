package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pedido.InvalidReportPeriodException;
import com.cernecommerce.core.domain.model.pedido.MarginSummary;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
import com.cernecommerce.core.ports.out.pedido.OrderReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    @Mock OrderReportRepository orderReportRepository;

    OrderReportService orderReportService;

    @BeforeEach
    void setUp() {
        orderReportService = new OrderReportService(orderReportRepository);
    }

    private static OrderSummary emptySummary() {
        return new OrderSummary(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                Map.of(), Map.of(), BigDecimal.ZERO, List.of(), List.of());
    }

    @Test
    void getSummary_throwsWhenFromIsAfterTo() {
        Instant from = NOW;
        Instant to = NOW.minusSeconds(3600);

        assertThatThrownBy(() -> orderReportService.getSummary(null, null, null, from, to))
                .isInstanceOf(InvalidReportPeriodException.class);
    }

    @Test
    void getSummary_throwsWhenRangeExceedsMax() {
        Instant from = NOW.minus(400, java.time.temporal.ChronoUnit.DAYS);

        assertThatThrownBy(() -> orderReportService.getSummary(null, null, null, from, NOW))
                .isInstanceOf(InvalidReportPeriodException.class);
    }

    @Test
    void getSummary_delegatesToRepositoryWithTopProductsLimit() {
        Instant from = NOW.minusSeconds(3600);
        when(orderReportRepository.summarize(null, null, null, null, from, NOW, 10)).thenReturn(emptySummary());

        OrderSummary result = orderReportService.getSummary(null, null, null, from, NOW);

        assertThat(result.totalOrders()).isZero();
        verify(orderReportRepository).summarize(eq(null), eq(null), eq(null), eq(null), eq(from), eq(NOW), eq(10));
    }

    @Test
    void getTopProducts_delegatesWithExplicitPeriodAndSort() {
        Instant from = NOW.minusSeconds(3600);
        when(orderReportRepository.findTopProducts(null, null, null, from, NOW, 5, true))
                .thenReturn(List.of());

        orderReportService.getTopProducts(null, null, null, from, NOW, 5, true);

        verify(orderReportRepository).findTopProducts(null, null, null, from, NOW, 5, true);
    }

    @Test
    void getTopProducts_throwsWhenExplicitPeriodExceedsMax() {
        Instant from = NOW.minus(400, java.time.temporal.ChronoUnit.DAYS);

        assertThatThrownBy(() -> orderReportService.getTopProducts(null, null, null, from, NOW, 10, false))
                .isInstanceOf(InvalidReportPeriodException.class);
    }

    @Test
    void getTopProducts_throwsWhenExplicitFromIsAfterTo() {
        Instant from = NOW;
        Instant to = NOW.minusSeconds(3600);

        assertThatThrownBy(() -> orderReportService.getTopProducts(null, null, null, from, to, 10, false))
                .isInstanceOf(InvalidReportPeriodException.class);
    }

    @Test
    void getTopProducts_defaultsToFullHistoryWhenBothOmitted_withoutMaxRangeCheck() {
        when(orderReportRepository.findTopProducts(any(), any(), any(), any(), any(), anyInt(), anyBoolean()))
                .thenReturn(List.of());

        orderReportService.getTopProducts(null, null, null, null, null, 10, false);

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(orderReportRepository).findTopProducts(eq(null), eq(null), eq(null), fromCaptor.capture(),
                toCaptor.capture(), eq(10), eq(false));

        assertThat(fromCaptor.getValue()).isEqualTo(Instant.EPOCH);
        assertThat(toCaptor.getValue()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void getTopProducts_defaultsMissingSideWhenOnlyOneBoundInformed() {
        when(orderReportRepository.findTopProducts(any(), any(), any(), any(), any(), anyInt(), anyBoolean()))
                .thenReturn(List.of());

        orderReportService.getTopProducts(null, null, null, NOW, null, 10, false);

        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(orderReportRepository).findTopProducts(eq(null), eq(null), eq(null), eq(NOW),
                toCaptor.capture(), eq(10), eq(false));
        assertThat(toCaptor.getValue()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void getMarginReport_throwsWhenFromIsAfterTo() {
        Instant from = NOW;
        Instant to = NOW.minusSeconds(3600);

        assertThatThrownBy(() -> orderReportService.getMarginReport(null, null, from, to))
                .isInstanceOf(InvalidReportPeriodException.class);
    }

    @Test
    void getMarginReport_throwsWhenRangeExceedsMax() {
        Instant from = NOW.minus(400, ChronoUnit.DAYS);

        assertThatThrownBy(() -> orderReportService.getMarginReport(null, null, from, NOW))
                .isInstanceOf(InvalidReportPeriodException.class);
    }

    @Test
    void getMarginReport_delegatesToRepositoryWithTopProductsLimit() {
        Instant from = NOW.minusSeconds(3600);
        MarginSummary summary = new MarginSummary(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, List.of());
        when(orderReportRepository.summarizeMargin(null, "LOJA-01", from, NOW, 10)).thenReturn(summary);

        MarginSummary result = orderReportService.getMarginReport(null, "LOJA-01", from, NOW);

        assertThat(result.itemsConsidered()).isZero();
        verify(orderReportRepository).summarizeMargin(eq(null), eq("LOJA-01"), eq(from), eq(NOW), eq(10));
    }

    /** Pendência 9 — limit em topProductsByMargin, de 1 a 50. */
    @Test
    void getMarginReport_repassaLimitEValidaFaixa() {
        Instant from = NOW.minusSeconds(3600);
        MarginSummary summary = new MarginSummary(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, List.of());
        when(orderReportRepository.summarizeMargin(null, null, from, NOW, 50)).thenReturn(summary);

        orderReportService.getMarginReport(null, null, from, NOW, 50);

        verify(orderReportRepository).summarizeMargin(null, null, from, NOW, 50);
        assertThatThrownBy(() -> orderReportService.getMarginReport(null, null, from, NOW, 51))
                .isInstanceOf(InvalidReportPeriodException.class);
    }

    @Test
    void getSummary_repassaDeposito() {
        Instant from = NOW.minusSeconds(3600);
        when(orderReportRepository.summarize(null, null, null, "LOJA-01", from, NOW, 10)).thenReturn(emptySummary());

        orderReportService.getSummary(null, null, null, "LOJA-01", from, NOW);

        verify(orderReportRepository).summarize(null, null, null, "LOJA-01", from, NOW, 10);
    }
}
