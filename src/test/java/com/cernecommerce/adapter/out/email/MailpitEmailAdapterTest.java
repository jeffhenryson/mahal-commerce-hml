package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class MailpitEmailAdapterTest {

    @Test
    void channelStatus_reportsConnectedToMailpit() {
        MailpitEmailAdapter adapter = new MailpitEmailAdapter(
                RestClient.builder().baseUrl("http://mailpit-mahal:8025/api/v1/send").build(),
                "noreply@cernedsgn.xyz", 15, "Confirme seu cadastro",
                "http://localhost:4201/auth/verify-email", "http://localhost:4201/auth/confirm-email-change", null, new SimpleMeterRegistry());

        EmailChannelStatus status = adapter.channelStatus();

        assertThat(status.conectado()).isTrue();
        assertThat(status.provedor()).isEqualTo("MAILPIT");
    }
}
