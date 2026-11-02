package com.cernecommerce.adapter.in.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cernecommerce.adapter.in.converter.FinanceiroDTOConverter;
import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.pedido.InvalidReportPeriodException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.financeiro.CashFlowCategory;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry.Direction;
import com.cernecommerce.core.domain.model.financeiro.CashFlowStatus;
import com.cernecommerce.core.domain.model.financeiro.CashFlowSummary;
import com.cernecommerce.core.domain.model.financeiro.LinkedEntityType;
import com.cernecommerce.core.domain.model.pedido.MarginSummary;
import com.cernecommerce.core.ports.in.FinanceiroUseCase;
import com.cernecommerce.core.ports.in.OrderReportUseCase;
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
import java.time.LocalDate;
import java.util.List;

public class FinanceiroControllerTest {

    private MockMvc mockMvc;
    private FinanceiroUseCase financeiroUseCase;
    private OrderReportUseCase orderReportUseCase;
    private ApplicationEventPublisher publisher;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @BeforeEach
    void setup() {
        financeiroUseCase = mock(FinanceiroUseCase.class);
        orderReportUseCase = mock(OrderReportUseCase.class);
        publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FinanceiroController(financeiroUseCase, orderReportUseCase,
                        new FinanceiroDTOConverter(), new OrderDTOConverter(), publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private CashFlowEntry entry(Long id) {
        return new CashFlowEntry(id, LocalDate.now(), "Aluguel Loja Julho/2026", "Imobiliária Central RJ",
                CashFlowCategory.ALUGUEL, Direction.OUTFLOW, new BigDecimal("8500.00"), CashFlowStatus.PREVISTO,
                LocalDate.of(2026, 7, 10), null, LinkedEntityType.ORDER, 1042L, null);
    }

    @Test
    void listCashFlow_returns_200() throws Exception {
        when(financeiroUseCase.listCashFlow(0, 20))
                .thenReturn(new PageResult<>(List.of(entry(1L)), 0, 20, 1L, 1));

        mockMvc.perform(get("/financeiro/cash-flow").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Aluguel Loja Julho/2026"))
                .andExpect(jsonPath("$.content[0].category").value("ALUGUEL"))
                .andExpect(jsonPath("$.content[0].direction").value("OUTFLOW"))
                .andExpect(jsonPath("$.content[0].status").value("PREVISTO"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getCashFlowSummary_returns_200() throws Exception {
        when(financeiroUseCase.getCashFlowSummary(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(new CashFlowSummary(new BigDecimal("1000.00"), new BigDecimal("400.00"),
                        new BigDecimal("600.00"), 5));

        mockMvc.perform(get("/financeiro/cash-flow/summary")
                        .principal(AUTH)
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInflow").value(1000.00))
                .andExpect(jsonPath("$.totalOutflow").value(400.00))
                .andExpect(jsonPath("$.balance").value(600.00))
                .andExpect(jsonPath("$.entryCount").value(5));
    }

    @Test
    void createCashFlowEntry_returns_201_andPublishesAuditEvent() throws Exception {
        when(financeiroUseCase.createCashFlowEntry("Aluguel Loja Julho/2026", "Imobiliária Central RJ",
                CashFlowCategory.ALUGUEL, Direction.OUTFLOW, new BigDecimal("8500.0"),
                LocalDate.of(2026, 7, 10), LinkedEntityType.ORDER, 1042L))
                .thenReturn(entry(1L));

        mockMvc.perform(post("/financeiro/cash-flow")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Aluguel Loja Julho/2026",
                                  "entityName": "Imobiliária Central RJ",
                                  "category": "ALUGUEL",
                                  "direction": "OUTFLOW",
                                  "amount": 8500.0,
                                  "dueDate": "2026-07-10",
                                  "linkedEntityType": "ORDER",
                                  "linkedEntityId": 1042
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/financeiro/cash-flow/1"))
                .andExpect(jsonPath("$.status").value("PREVISTO"));

        verify(publisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    void createCashFlowEntry_blankDescription_returns_400() throws Exception {
        mockMvc.perform(post("/financeiro/cash-flow")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "",
                                  "category": "ALUGUEL",
                                  "direction": "OUTFLOW",
                                  "amount": 8500.0,
                                  "dueDate": "2026-07-10"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateCashFlowEntry_markPaid_returns_200() throws Exception {
        when(financeiroUseCase.updateCashFlowEntry(1L, null, null, null, null, null, null,
                CashFlowStatus.PAGO, LocalDate.of(2026, 7, 10), null, null))
                .thenReturn(new CashFlowEntry(1L, LocalDate.now(), "Aluguel Loja Julho/2026", "Imobiliária Central RJ",
                        CashFlowCategory.ALUGUEL, Direction.OUTFLOW, new BigDecimal("8500.00"), CashFlowStatus.PAGO,
                        LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 10), LinkedEntityType.ORDER, 1042L, null));

        mockMvc.perform(patch("/financeiro/cash-flow/1")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PAGO\",\"paymentDate\":\"2026-07-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGO"))
                .andExpect(jsonPath("$.paymentDate").value("2026-07-10"));

        verify(publisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    void deleteCashFlowEntry_returns_204_andPublishesAuditEvent() throws Exception {
        mockMvc.perform(delete("/financeiro/cash-flow/1").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(financeiroUseCase).deleteCashFlowEntry(1L);
        verify(publisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    void listCashFlow_withPagination_passesParamsThrough() throws Exception {
        when(financeiroUseCase.listCashFlow(1, 10))
                .thenReturn(new PageResult<>(List.of(), 1, 10, 0L, 0));

        mockMvc.perform(get("/financeiro/cash-flow")
                        .principal(AUTH)
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void getMarginReport_returns_200() throws Exception {
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-31T23:59:59Z");
        when(orderReportUseCase.getMarginReport(null, null, from, to, 10))
                .thenReturn(new MarginSummary(3, new BigDecimal("100.00"), new BigDecimal("60.00"),
                        new BigDecimal("40.00"), new BigDecimal("40.00"), List.of()));

        mockMvc.perform(get("/financeiro/margem")
                        .principal(AUTH)
                        .param("from", "2026-07-01T00:00:00Z")
                        .param("to", "2026-07-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemsConsidered").value(3))
                .andExpect(jsonPath("$.totalMargin").value(40.00))
                .andExpect(jsonPath("$.marginPercent").value(40.00));
    }

    @Test
    void getMarginReport_invalidPeriod_returns_400() throws Exception {
        Instant from = Instant.parse("2026-07-31T00:00:00Z");
        Instant to = Instant.parse("2026-07-01T00:00:00Z");
        when(orderReportUseCase.getMarginReport(null, null, from, to, 10))
                .thenThrow(new InvalidReportPeriodException("'from' não pode ser depois de 'to'"));

        mockMvc.perform(get("/financeiro/margem")
                        .principal(AUTH)
                        .param("from", "2026-07-31T00:00:00Z")
                        .param("to", "2026-07-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }
}
