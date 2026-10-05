package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.exception.email.EmailDeliveryException;
import com.cernecommerce.core.domain.model.config.EmailSample;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmailSampleSenderAdapterTest {

    /** Registra os envios; no ambiente real é o Logging/Mailpit/Resend do env. */
    static class RecordingEmailAdapter extends LoggingEmailAdapter {
        final List<String> sent = new ArrayList<>();

        @Override
        public void sendTokenTheftAlert(String to, String username,
                com.cernecommerce.core.domain.model.notification.SecurityEventContext context) {
            sent.add("token-theft:" + to);
        }

        @Override
        public void sendOrderConfirmation(String to,
                com.cernecommerce.core.domain.model.notification.OrderEmailView order, String checkoutUrl) {
            sent.add("order-confirmation:" + to);
        }
    }

    @Test
    void sendEnvironmentSample_chama_o_alvo_e_nao_o_proxy_async() {
        RecordingEmailAdapter target = new RecordingEmailAdapter();
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        // Simula o proxy do @Async: se o envio passasse por ele, voltaria antes de terminar.
        factory.addAdvice((MethodInterceptor) inv -> {
            throw new IllegalStateException("passou pelo proxy");
        });
        EmailPort proxied = (EmailPort) factory.getProxy();

        EmailSampleSenderAdapter adapter = new EmailSampleSenderAdapter(mock(StoreResendAdapterFactory.class), proxied);
        adapter.sendEnvironmentSample("eu@x.com", EmailSample.TOKEN_THEFT);
        adapter.sendEnvironmentSample("eu@x.com", EmailSample.ORDER_CONFIRMATION);

        assertThat(target.sent).containsExactly("token-theft:eu@x.com", "order-confirmation:eu@x.com");
    }

    @Test
    void sendEnvironmentSample_pelo_mailpit_devolve_o_erro_do_envio() {
        ThymeleafEmailRenderer renderer = mock(ThymeleafEmailRenderer.class);
        when(renderer.render(anyString(), anyMap())).thenReturn("<html/>");
        MailpitEmailAdapter mailpit = new MailpitEmailAdapter(
                RestClient.builder().baseUrl("http://localhost:1/api/v1/send").build(),
                "noreply@mahal.local", 15, "s", "http://front", renderer, new SimpleMeterRegistry());

        EmailSampleSenderAdapter adapter = new EmailSampleSenderAdapter(mock(StoreResendAdapterFactory.class), mailpit);

        assertThatThrownBy(() -> adapter.sendEnvironmentSample("eu@x.com", EmailSample.PASSWORD_RESET))
                .isInstanceOf(EmailDeliveryException.class);
        assertThat(adapter.environmentChannel().provedor()).isEqualTo("MAILPIT");
    }
}
