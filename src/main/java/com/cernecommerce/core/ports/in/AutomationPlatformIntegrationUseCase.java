package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.config.AutomationPlatform;
import com.cernecommerce.core.domain.model.config.AutomationPlatformSettings;
import com.cernecommerce.core.domain.model.config.AutomationPlatformTarget;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;

import java.util.Optional;

/** Integração com a plataforma de automação — n8n ou Make (Configurações › Integrações). */
public interface AutomationPlatformIntegrationUseCase {

    AutomationPlatformSettings get();

    /** Substitui a configuração. {@code token} {@code null} mantém o salvo; em branco o remove. */
    AutomationPlatformSettings update(UpdateCommand command, String updatedBy);

    /** {@code POST {baseUrl}} com {@code {"ping": true}} e o token como Bearer (configuração salva). */
    IntegrationTestResult sendTest(String requestedBy);

    /** Destino dos workflows quando a integração está ativa; vazio = automação PLATAFORMA falha. */
    Optional<AutomationPlatformTarget> activeTarget();

    record UpdateCommand(boolean enabled, AutomationPlatform platform, String baseUrl, String token) {

        @Override
        public String toString() {
            return "UpdateCommand[enabled=" + enabled + ", platform=" + platform + ", baseUrl=" + baseUrl
                    + ", token=" + (token == null ? "null" : "***") + "]";
        }
    }
}
