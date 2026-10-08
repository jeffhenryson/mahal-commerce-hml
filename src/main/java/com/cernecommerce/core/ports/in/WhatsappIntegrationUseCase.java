package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.WhatsappConnectionStatus;
import com.cernecommerce.core.domain.model.config.WhatsappCredentials;
import com.cernecommerce.core.domain.model.config.WhatsappIntegrationSettings;

import java.util.Optional;

/** Integração com a WhatsApp Cloud API (Configurações › Administração › Integrações). */
public interface WhatsappIntegrationUseCase {

    WhatsappIntegrationSettings get();

    /**
     * Substitui a configuração. {@code accessToken}/{@code verifyToken} {@code null} mantêm o valor
     * salvo; em branco o removem.
     */
    WhatsappIntegrationSettings update(UpdateCommand command, String updatedBy);

    /** Status do número com cache curto — alimenta {@code connected} e o badge do CRM. */
    WhatsappConnectionStatus connectionStatus();

    /**
     * Envia um template de teste com a configuração salva, mesmo desativada. Sem template, usa
     * {@code hello_world} em {@code en_US}.
     */
    IntegrationTestResult sendTest(String to, String template, String requestedBy);

    /** Credenciais quando a integração está ativa e completa; vazio = não envia por WhatsApp. */
    Optional<WhatsappCredentials> activeCredentials();

    /** Confere o {@code hub.verify_token} do handshake do webhook da Meta. */
    boolean matchesVerifyToken(String candidate);

    record UpdateCommand(boolean enabled, String phoneNumberId, String businessAccountId, String accessToken,
            String verifyToken) {

        @Override
        public String toString() {
            return "UpdateCommand[enabled=" + enabled + ", phoneNumberId=" + phoneNumberId
                    + ", businessAccountId=" + businessAccountId
                    + ", accessToken=" + (accessToken == null ? "null" : "***")
                    + ", verifyToken=" + (verifyToken == null ? "null" : "***") + "]";
        }
    }
}
