package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.config.EmailSample;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

class EmailSamplesTest {

    /** Um valor novo no enum sem {@code case} em {@link EmailSamples} passaria em silêncio sem enviar nada. */
    @ParameterizedTest
    @EnumSource(EmailSample.class)
    void toda_amostra_dispara_exatamente_um_email(EmailSample sample) {
        EmailPort port = mock(EmailPort.class);

        EmailSamples.send(port, "eu@x.com", sample);

        assertThat(mockingDetails(port).getInvocations()).hasSize(1);
    }
}
