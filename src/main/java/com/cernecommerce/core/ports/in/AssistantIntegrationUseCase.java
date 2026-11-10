package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.config.AssistantCredentials;
import com.cernecommerce.core.domain.model.config.AssistantSettings;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.ProvedorIa;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chaves de IA usadas pelo assistente do admin (Configurações › Integrações): uma por provedor
 * (Claude, ChatGPT, Gemini), um provedor ativo e, opcionalmente, um de reserva.
 */
public interface AssistantIntegrationUseCase {

    AssistantSettings get();

    AssistantSettings update(UpdateCommand command, String updatedBy);

    /** Chamada mínima ao provedor com a chave salva dele, mesmo desativado. {@code null} = o ativo. */
    IntegrationTestResult sendTest(ProvedorIa provider, String requestedBy);

    /**
     * Credenciais em ordem de uso — o ativo e, se tiver chave legível, a reserva. Vazio = assistente
     * não configurado (desativado ou o ativo sem chave).
     */
    Optional<List<AssistantCredentials>> activeCredentials();

    /** Modelo padrão de cada provedor (sem escolha da loja). */
    Map<ProvedorIa, String> defaultModels();

    /** Chave e modelo de um provedor. {@code apiKey} {@code null} mantém; em branco remove. {@code model} {@code null} mantém. */
    record ProviderCommand(String apiKey, String model) {

        @Override
        public String toString() {
            return "ProviderCommand[apiKey=" + (apiKey == null ? "null" : "***") + ", model=" + model + "]";
        }
    }

    /**
     * {@code provider} {@code null} mantém o ativo. {@code fallbackProvider} {@code null} remove a reserva
     * quando {@code fallbackInformado}. {@code apiKey}/{@code model} no topo são o formato antigo do PUT:
     * valem para o provedor ativo (depois de aplicado {@code provider}).
     */
    record UpdateCommand(boolean enabled, ProvedorIa provider, ProvedorIa fallbackProvider, boolean fallbackInformado,
            Map<ProvedorIa, ProviderCommand> providers, String apiKey, String model) {

        /** Formato antigo: só o Claude, sem escolher provedor. */
        public UpdateCommand(boolean enabled, String apiKey, String model) {
            this(enabled, null, null, false, Map.of(), apiKey, model);
        }

        @Override
        public String toString() {
            return "UpdateCommand[enabled=" + enabled + ", provider=" + provider + ", fallbackProvider="
                    + fallbackProvider + ", providers=" + providers + ", apiKey="
                    + (apiKey == null ? "null" : "***") + ", model=" + model + "]";
        }
    }
}
