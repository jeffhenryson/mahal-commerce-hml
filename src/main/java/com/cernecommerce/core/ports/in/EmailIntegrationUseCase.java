package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.config.EmailIntegrationSettings;
import com.cernecommerce.core.domain.model.config.EmailProvider;
import com.cernecommerce.core.domain.model.config.EmailSample;
import com.cernecommerce.core.domain.model.config.EmailSenderConfig;
import com.cernecommerce.core.domain.model.config.EmailTestResult;
import com.cernecommerce.core.domain.model.config.EmailTestTarget;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;

import java.util.List;
import java.util.Optional;

/** Integração de e-mail da loja (Configurações > Dados da loja > Integrações). */
public interface EmailIntegrationUseCase {

    EmailIntegrationSettings get();

    /**
     * Substitui a configuração. {@code apiKey} {@code null} mantém a chave salva; em branco a remove.
     */
    EmailIntegrationSettings update(UpdateCommand command, String updatedBy);

    /** Provedor que está enviando agora — o da loja quando ativa, senão o do ambiente. */
    EmailChannelStatus activeChannel();

    /** Provedor do ambiente ({@code email.provider}), ignorando a integração da loja. */
    EmailChannelStatus environmentChannel();

    /** Credenciais da integração quando ela está ativa e completa; vazio = usar o provedor do ambiente. */
    Optional<EmailSenderConfig> activeSenderConfig();

    /**
     * Envia exemplos para {@code to}. {@link EmailTestTarget#STORE} usa a configuração salva (mesmo
     * desativada); {@link EmailTestTarget#ENVIRONMENT} usa o provedor do ambiente e não exige a da loja.
     * {@code sample} {@code null} envia todos. Falha de um não interrompe os demais.
     */
    List<EmailTestResult> sendTest(String to, EmailSample sample, EmailTestTarget target, String requestedBy);

    record UpdateCommand(boolean enabled, EmailProvider provider, String fromEmail, String fromName,
            String replyTo, String apiKey) {

        @Override
        public String toString() {
            return "UpdateCommand[enabled=" + enabled + ", provider=" + provider + ", fromEmail=" + fromEmail
                    + ", fromName=" + fromName + ", replyTo=" + replyTo + ", apiKey=" + (apiKey == null ? "null" : "***") + "]";
        }
    }
}
