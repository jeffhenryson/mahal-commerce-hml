package com.cernecommerce.infra.notification;

import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationPreference;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DevAlertNotifierTest {

    private OperationalEmailDispatcher dispatcher;
    private NotificationUseCase notificationUseCase;
    private UserRepository userRepository;
    private MutableClock clock;
    private DevAlertNotifier notifier;

    @BeforeEach
    void setUp() {
        dispatcher = mock(OperationalEmailDispatcher.class);
        notificationUseCase = mock(NotificationUseCase.class);
        userRepository = mock(UserRepository.class);
        when(userRepository.findUsernamesByRole("ROLE_DEV")).thenReturn(Set.of("dev"));
        when(dispatcher.preference(anyString(), any()))
                .thenAnswer(inv -> NotificationPreference.defaultFor(inv.getArgument(0), inv.getArgument(1)));
        clock = new MutableClock(Instant.parse("2026-10-05T12:00:00Z"));
        notifier = new DevAlertNotifier(dispatcher, notificationUseCase, mock(NotificationSsePort.class),
                userRepository, Runnable::run, clock);
    }

    @Test
    void alerta_vai_por_email_e_in_app_para_a_role_dev() {
        notifier.alert("erro-500", "k", "Erro 500 em GET /x", Map.of("Rota", "GET /x"));

        ArgumentCaptor<NotificationEmail> email = ArgumentCaptor.forClass(NotificationEmail.class);
        verify(dispatcher).toRole(eq("ROLE_DEV"), eq(NotificationType.DEV), email.capture());
        assertThat(email.getValue().category()).isEqualTo("dev.erro-500");
        assertThat(email.getValue().tone()).isEqualTo(NotificationEmail.Tone.DANGER);
        verify(notificationUseCase).notify(eq("dev"), eq(NotificationType.DEV), eq("Erro 500 em GET /x"), contains("GET /x"));
    }

    @Test
    void mesma_chave_dentro_da_janela_sai_uma_vez_so() {
        notifier.alert("erro-500", "k", "s", Map.of());
        notifier.alert("erro-500", "k", "s", Map.of());
        clock.advanceMinutes(14);
        notifier.alert("erro-500", "k", "s", Map.of());

        verify(dispatcher, times(1)).toRole(any(), any(), any());

        clock.advanceMinutes(2);
        notifier.alert("erro-500", "k", "s", Map.of());

        verify(dispatcher, times(2)).toRole(any(), any(), any());
    }

    @Test
    void chaves_diferentes_nao_se_agrupam() {
        notifier.alert("erro-500", "a", "s", Map.of());
        notifier.alert("erro-500", "b", "s", Map.of());

        verify(dispatcher, times(2)).toRole(any(), any(), any());
    }

    @Test
    void falha_de_email_avisa_so_in_app() {
        notifier.emailDeliveryFailed("email.welcome", "x@y.com", "403 domain not verified");

        verify(notificationUseCase).notify(eq("dev"), eq(NotificationType.DEV), eq("Falha ao enviar e-mail"),
                contains("403 domain not verified"));
        verify(dispatcher, never()).toRole(any(), any(), any());
    }

    @Test
    void preferencia_in_app_desligada_e_respeitada() {
        when(dispatcher.preference("dev", NotificationType.DEV))
                .thenReturn(new NotificationPreference("dev", NotificationType.DEV, false, true));

        notifier.alert("bug-report", "1", "Novo bug report", Map.of());

        verifyNoInteractions(notificationUseCase);
        verify(dispatcher).toRole(eq("ROLE_DEV"), eq(NotificationType.DEV), any());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceMinutes(long minutes) {
            now = now.plusSeconds(minutes * 60);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
