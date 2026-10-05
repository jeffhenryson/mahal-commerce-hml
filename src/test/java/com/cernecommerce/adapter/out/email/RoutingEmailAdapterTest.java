package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.config.EmailProvider;
import com.cernecommerce.core.domain.model.config.EmailSenderConfig;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.ports.in.EmailIntegrationUseCase;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RoutingEmailAdapterTest {

    private static final EmailSenderConfig CONFIG =
            new EmailSenderConfig(EmailProvider.RESEND, "re_key", "loja@mahal.com", "Mahal", null);

    private EmailPort fallback;
    private EmailIntegrationUseCase integration;
    private StoreResendAdapterFactory factory;
    private ResendEmailAdapter storeAdapter;
    private RoutingEmailAdapter router;

    @BeforeEach
    void setUp() {
        fallback = mock(EmailPort.class);
        integration = mock(EmailIntegrationUseCase.class);
        factory = mock(StoreResendAdapterFactory.class);
        storeAdapter = mock(ResendEmailAdapter.class);
        when(factory.adapterFor(CONFIG)).thenReturn(storeAdapter);
        // Executor síncrono: o teste verifica para onde vai, não a thread.
        router = new RoutingEmailAdapter(fallback, integration, factory, Runnable::run);
    }

    @Test
    void integracao_inativa_usa_o_provedor_do_ambiente() {
        when(integration.activeSenderConfig()).thenReturn(Optional.empty());

        router.sendPasswordResetLink("a@x.com", "alice", "http://reset", 15);

        verify(fallback).sendPasswordResetLink("a@x.com", "alice", "http://reset", 15);
        verifyNoInteractions(factory);
    }

    @Test
    void integracao_ativa_usa_o_resend_da_loja_e_ignora_o_ambiente() {
        when(integration.activeSenderConfig()).thenReturn(Optional.of(CONFIG));

        router.sendVerificationCode("a@x.com", "alice", "CODE");

        verify(storeAdapter).sendVerificationCode("a@x.com", "alice", "CODE");
        verifyNoInteractions(fallback);
    }

    @Test
    void falha_do_resend_da_loja_nao_vaza_para_quem_chamou() {
        when(integration.activeSenderConfig()).thenReturn(Optional.of(CONFIG));
        doThrow(new RuntimeException("boom")).when(storeAdapter).sendTokenTheftAlert(anyString(), anyString(), any());

        router.sendTokenTheftAlert("a@x.com", "alice", null);

        verify(storeAdapter).sendTokenTheftAlert("a@x.com", "alice", null);
    }

    @Test
    void erro_lendo_a_integracao_cai_no_ambiente() {
        when(integration.activeSenderConfig()).thenThrow(new IllegalStateException("db down"));

        router.sendAccountLockedAlert("a@x.com", "alice", null);

        verify(fallback).sendAccountLockedAlert("a@x.com", "alice", null);
    }

    @Test
    void channelStatus_reflete_o_provedor_em_uso() {
        when(fallback.channelStatus()).thenReturn(EmailChannelStatus.of(false, "LOG", "log"));
        when(integration.activeSenderConfig()).thenReturn(Optional.empty());
        assertThat(router.channelStatus().provedor()).isEqualTo("LOG");

        when(integration.activeSenderConfig()).thenReturn(Optional.of(CONFIG));
        EmailChannelStatus status = router.channelStatus();
        assertThat(status.provedor()).isEqualTo("RESEND");
        assertThat(status.conectado()).isTrue();
        assertThat(status.detalhe()).contains("loja@mahal.com");
    }

    @Test
    void factory_reaproveita_o_adapter_enquanto_a_config_nao_muda() {
        ThymeleafEmailRenderer renderer = mock(ThymeleafEmailRenderer.class);
        when(renderer.render(anyString(), anyMap())).thenReturn("<html/>");
        StoreResendAdapterFactory real = new StoreResendAdapterFactory("http://localhost:1/emails", 15, "s",
                "http://front", renderer, new SimpleMeterRegistry(), mock(ObjectProvider.class));

        ResendEmailAdapter a = real.adapterFor(CONFIG);

        assertThat(real.adapterFor(CONFIG)).isSameAs(a);
        assertThat(real.adapterFor(new EmailSenderConfig(EmailProvider.RESEND, "re_other", "loja@mahal.com",
                "Mahal", null))).isNotSameAs(a);
    }
}
