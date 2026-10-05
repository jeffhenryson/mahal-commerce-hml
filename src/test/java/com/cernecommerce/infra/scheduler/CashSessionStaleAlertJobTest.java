package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationPreference;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import com.cernecommerce.infra.notification.OperationalEmailDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CashSessionStaleAlertJobTest {

    private static final Instant NOW = Instant.parse("2026-10-05T22:00:00Z");

    private PdvUseCase pdvUseCase;
    private OperationalEmailDispatcher dispatcher;
    private CashSessionStaleAlertJob job;

    @BeforeEach
    void setUp() {
        pdvUseCase = mock(PdvUseCase.class);
        dispatcher = mock(OperationalEmailDispatcher.class);
        when(dispatcher.preference(anyString(), any()))
                .thenAnswer(inv -> NotificationPreference.defaultFor(inv.getArgument(0), inv.getArgument(1)));
        job = new CashSessionStaleAlertJob(pdvUseCase, dispatcher, mock(UserRepository.class), mock(EmailPort.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void avisa_so_quem_cruzou_as_12h_na_ultima_hora() {
        when(pdvUseCase.listSessions(any(com.cernecommerce.core.domain.model.pdv.CashRegisterSessionFilter.class), eq(0), anyInt()))
                .thenReturn(new PageResult<>(List.of(
                        open(1L, NOW.minusSeconds(12 * 3600 + 600)),   // 12h10 — cruzou agora
                        open(2L, NOW.minusSeconds(14 * 3600)),         // 14h — já foi avisado antes
                        open(3L, NOW.minusSeconds(13 * 3600))),        // exatamente 13h — fronteira de fora
                        0, 200, 3, 1));

        job.alert();

        ArgumentCaptor<NotificationEmail> email = ArgumentCaptor.forClass(NotificationEmail.class);
        verify(dispatcher, times(1)).toPermission(eq("FINANCEIRO_READ"), eq(NotificationType.CAIXA), email.capture(),
                eq("atendente"));
        assertThat(email.getValue().subject()).isEqualTo("Caixa #1 aberto há 12h por atendente");
    }

    private static CashRegisterSession open(Long id, Instant openedAt) {
        return new CashRegisterSession(id, "atendente", openedAt, BigDecimal.TEN, "LOJA-01", null, null, null,
                null, null, CashRegisterSession.Status.OPEN, null);
    }
}
