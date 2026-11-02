package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.exception.pedido.InvalidSalesGoalAmountException;
import com.cernecommerce.core.domain.exception.pedido.SalesGoalNotFoundException;
import com.cernecommerce.core.domain.model.pedido.SalesAnalytics;
import com.cernecommerce.core.domain.model.pedido.SalesGoal;
import com.cernecommerce.core.ports.in.SalesAnalyticsUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalesAnalyticsControllerTest {

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("gerente", null, List.of());

    SalesAnalyticsUseCase useCase;
    ApplicationEventPublisher publisher;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        useCase = mock(SalesAnalyticsUseCase.class);
        publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SalesAnalyticsController(useCase, publisher))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void heatmap_returns_cells() throws Exception {
        when(useCase.heatmap(any(), any(), any(), any())).thenReturn(List.of(
                new SalesAnalytics.HeatmapCell(5, 22, new BigDecimal("150.00"), 2)));

        mockMvc.perform(get("/orders/analytics/heatmap").param("from", "2026-10-01T03:00:00Z")
                        .param("to", "2026-11-01T02:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].weekday").value(5))
                .andExpect(jsonPath("$[0].hour").value(22));
    }

    @Test
    void customers_sortByInvalido_returns_400() throws Exception {
        mockMvc.perform(get("/orders/analytics/customers").param("from", "2026-10-01T03:00:00Z")
                        .param("to", "2026-11-01T02:59:59Z").param("sortBy", "nome"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customers_lastPurchase() throws Exception {
        when(useCase.customers(any(), any(), any(), eq(0), eq(20), eq(SalesAnalytics.CustomerSort.LAST_PURCHASE)))
                .thenReturn(new SalesAnalytics.CustomerRanking(List.of(), 0, 20, 0, 0,
                        new SalesAnalytics.AnonymousTotal(3, new BigDecimal("40.00"))));

        mockMvc.perform(get("/orders/analytics/customers").param("from", "2026-10-01T03:00:00Z")
                        .param("to", "2026-11-01T02:59:59Z").param("sortBy", "lastPurchase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymous.orders").value(3));
    }

    @Test
    void getGoal_semMeta_returns_404() throws Exception {
        when(useCase.getGoal(YearMonth.of(2026, 10))).thenThrow(new SalesGoalNotFoundException(YearMonth.of(2026, 10)));

        mockMvc.perform(get("/vendas/metas/2026-10"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SALES_GOAL_NOT_FOUND"));
    }

    @Test
    void getGoal_mesInvalido_returns_400() throws Exception {
        mockMvc.perform(get("/vendas/metas/2026-13"))
                .andExpect(status().isBadRequest());
        verify(useCase, never()).getGoal(any());
    }

    @Test
    void putGoal_gravaEAudita() throws Exception {
        when(useCase.saveGoal(YearMonth.of(2026, 10), new BigDecimal("15000.00"), "gerente"))
                .thenReturn(new SalesGoal(YearMonth.of(2026, 10), new BigDecimal("15000.00"), "gerente",
                        Instant.parse("2026-11-02T12:00:00Z")));

        mockMvc.perform(put("/vendas/metas/2026-10").principal(AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":15000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-10"))
                .andExpect(jsonPath("$.amount").value(15000.0));
        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    void putGoal_valorInvalido_returns_400_INVALID_AMOUNT() throws Exception {
        when(useCase.saveGoal(any(), any(), any())).thenThrow(new InvalidSalesGoalAmountException());

        mockMvc.perform(put("/vendas/metas/2026-10").principal(AUTH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_AMOUNT"));
    }
}
