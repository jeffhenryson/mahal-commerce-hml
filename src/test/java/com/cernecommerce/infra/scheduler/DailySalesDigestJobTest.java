package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSessionFilter;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.domain.model.recebivel.ReceivableCustomerSummary;
import com.cernecommerce.core.ports.in.OrderReportUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.infra.notification.OperationalEmailDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class DailySalesDigestJobTest {

    private OrderReportUseCase orderReport;
    private PdvUseCase pdvUseCase;
    private ReceivableUseCase receivableUseCase;
    private DailySalesDigestJob job;

    @BeforeEach
    void setUp() {
        orderReport = mock(OrderReportUseCase.class);
        pdvUseCase = mock(PdvUseCase.class);
        receivableUseCase = mock(ReceivableUseCase.class);
        when(pdvUseCase.listSessions(any(CashRegisterSessionFilter.class), eq(0), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 50, 0, 0));
        when(receivableUseCase.summary(isNull(), eq(true))).thenReturn(List.of());
        Clock clock = Clock.fixed(Instant.parse("2026-10-05T11:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        job = new DailySalesDigestJob(orderReport, pdvUseCase, receivableUseCase,
                mock(OperationalEmailDispatcher.class), clock);
    }

    @Test
    void dia_sem_venda_ainda_gera_o_resumo() {
        when(orderReport.getSummary(any(), any(), any(), any(), any())).thenReturn(summary(0, "0"));

        NotificationEmail email = job.build();

        assertThat(email.subject()).isEqualTo("Resumo de 04/10/2026");
        assertThat(email.intro()).isEqualTo("Nenhuma venda ontem.");
    }

    @Test
    void resumo_traz_vendas_por_canal_mais_vendidos_e_fiado_vencido() {
        when(orderReport.getSummary(any(), any(), any(), any(), any())).thenReturn(summary(42, "3210.00"));
        when(receivableUseCase.summary(isNull(), eq(true))).thenReturn(List.of(
                new ReceivableCustomerSummary(1L, "Joana", new BigDecimal("80"), new BigDecimal("80"), null,
                        LocalDate.of(2026, 9, 30), 1, new BigDecimal("80"), BigDecimal.ZERO)));

        NotificationEmail email = job.build();

        assertThat(email.sections()).extracting(NotificationEmail.Section::title)
                .containsExactly("Vendas", "Mais vendidos", "Caixas abertos no dia", "Fiado vencido");
        assertThat(email.sections().get(0).rows()).extracting(NotificationEmail.Row::label)
                .contains("Balcão", "Mesa");
        assertThat(email.sections().get(3).rows()).extracting(NotificationEmail.Row::label)
                .containsExactly("Joana", "Total vencido");
    }

    @Test
    void periodo_e_o_dia_anterior_no_fuso_da_loja() {
        when(orderReport.getSummary(any(), any(), any(), any(), any())).thenReturn(summary(0, "0"));

        job.build();

        verify(orderReport).getSummary(isNull(), isNull(), isNull(),
                eq(Instant.parse("2026-10-04T03:00:00Z")), eq(Instant.parse("2026-10-05T02:59:59.999Z")));
    }

    private static OrderSummary summary(long orders, String net) {
        return new OrderSummary(orders, new BigDecimal(net), new BigDecimal(net),
                orders == 0 ? BigDecimal.ZERO : new BigDecimal(net).divide(BigDecimal.valueOf(orders), 2, java.math.RoundingMode.HALF_UP),
                orders == 0 ? Map.of() : Map.of(SalesChannel.BALCAO, new BigDecimal("2000"), SalesChannel.MESA, new BigDecimal("1210")),
                Map.of(), BigDecimal.ZERO, List.of(),
                orders == 0 ? List.of() : List.of(new OrderSummary.TopProduct("ESS-1", "Essência Menta", new BigDecimal("12"), new BigDecimal("480"))));
    }
}
