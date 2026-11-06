package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.email.EmailDeliveryException;
import com.cernecommerce.core.domain.exception.notification.FailedEmailNotFoundException;
import com.cernecommerce.core.domain.exception.ratelimit.RateLimitExceededException;
import com.cernecommerce.core.domain.model.notification.FailedEmail;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.FailedEmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FailedEmailServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-05T12:00:00Z");

    private FailedEmailRepository repository;
    private EmailPort emailPort;
    private FailedEmailService service;

    @BeforeEach
    void setUp() {
        repository = mock(FailedEmailRepository.class);
        emailPort = mock(EmailPort.class);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new FailedEmailService(repository, emailPort, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private FailedEmail guardado(Instant ultimoReenvio) {
        FailedEmail email = new FailedEmail(1L, "email.welcome", "a@b.com", "Oi", "<p/>", "403", AGORA.minusSeconds(600),
                ultimoReenvio == null ? 0 : 1, ultimoReenvio, ultimoReenvio == null ? null : "FAILED", null);
        when(repository.findById(1L)).thenReturn(Optional.of(email));
        return email;
    }

    @Test
    void reenvio_aceito_registra_sucesso() {
        guardado(null);

        FailedEmail result = service.resend(1L);

        verify(emailPort).resendRendered("a@b.com", "Oi", "<p/>", "email.welcome");
        assertThat(result.lastResendStatus()).isEqualTo(FailedEmail.RESEND_SENT);
        assertThat(result.resendCount()).isEqualTo(1);
    }

    @Test
    void recusa_do_provedor_nao_e_erro_da_chamada() {
        guardado(null);
        doThrow(new EmailDeliveryException("403 domain")).when(emailPort).resendRendered(any(), any(), any(), any());

        FailedEmail result = service.resend(1L);

        assertThat(result.lastResendStatus()).isEqualTo(FailedEmail.RESEND_FAILED);
        assertThat(result.lastResendError()).contains("403 domain");
    }

    @Test
    void reenvio_logo_depois_do_anterior_e_recusado() {
        guardado(AGORA.minusSeconds(10));

        assertThatThrownBy(() -> service.resend(1L)).isInstanceOf(RateLimitExceededException.class);

        verify(emailPort, never()).resendRendered(any(), any(), any(), any());
    }

    @Test
    void email_expirado_responde_nao_encontrado() {
        when(repository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(2L)).isInstanceOf(FailedEmailNotFoundException.class);
    }
}
