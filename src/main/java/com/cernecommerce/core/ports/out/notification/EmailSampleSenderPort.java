package com.cernecommerce.core.ports.out.notification;

import com.cernecommerce.core.domain.model.config.EmailSample;
import com.cernecommerce.core.domain.model.config.EmailSenderConfig;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;

/**
 * Envia, de forma síncrona, um e-mail de exemplo com dados fictícios — é o que a tela de
 * Integrações usa para validar chave, remetente e templates de ponta a ponta. Pelo provedor da loja
 * ({@link #sendSample}) ou pelo do ambiente ({@link #sendEnvironmentSample}: log, Mailpit ou Resend
 * do env), este último para ver os e-mails na caixa do Mailpit em dev/hml.
 */
public interface EmailSampleSenderPort {

    /** @throws com.cernecommerce.core.domain.exception.email.EmailDeliveryException quando o provedor recusa. */
    void sendSample(EmailSenderConfig config, String to, EmailSample sample);

    /** @throws com.cernecommerce.core.domain.exception.email.EmailDeliveryException quando o provedor recusa. */
    void sendEnvironmentSample(String to, EmailSample sample);

    /** Provedor do ambiente ({@code email.provider}), ignorando a integração da loja. */
    EmailChannelStatus environmentChannel();
}
