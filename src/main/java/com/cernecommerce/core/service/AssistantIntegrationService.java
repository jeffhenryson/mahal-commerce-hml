package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.integration.InvalidIntegrationException;
import com.cernecommerce.core.domain.model.config.AssistantCredentials;
import com.cernecommerce.core.domain.model.config.AssistantProviderSettings;
import com.cernecommerce.core.domain.model.config.AssistantSettings;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.ProvedorIa;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.integration.IntegrationConfigStore;
import com.cernecommerce.core.ports.in.AssistantIntegrationUseCase;
import com.cernecommerce.core.ports.out.SecretCipherPort;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chaves de IA do assistente em {@code system_config}:
 * <ul>
 *   <li>{@code integration.assistant.enabled|provider|fallback-provider} — globais;</li>
 *   <li>{@code integration.assistant.<anthropic|openai|gemini>.api-key|api-key-last4|model|last-test-detail}
 *       — uma chave por provedor, todas podem ficar salvas.</li>
 * </ul>
 * Sem fallback para variável de ambiente: o assistente usa só as chaves da loja.
 */
public class AssistantIntegrationService implements AssistantIntegrationUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssistantIntegrationService.class);

    static final String ENABLED = "integration.assistant.enabled";
    static final String PROVIDER = "integration.assistant.provider";
    static final String FALLBACK = "integration.assistant.fallback-provider";
    static final String MONTHLY_LIMIT = "integration.assistant.monthly-token-limit";

    private final IntegrationConfigStore store;
    private final AssistenteLlmPort llmPort;
    private final Map<ProvedorIa, String> defaultModels;

    public AssistantIntegrationService(SystemConfigPort configPort, SecretCipherPort cipherPort,
            AssistenteLlmPort llmPort, Map<ProvedorIa, String> defaultModels) {
        this.store = new IntegrationConfigStore(configPort, cipherPort);
        this.llmPort = llmPort;
        this.defaultModels = new EnumMap<>(defaultModels);
    }

    static String apiKey(ProvedorIa p) {
        return "integration.assistant." + p.chave() + ".api-key";
    }

    static String apiKeyLast4(ProvedorIa p) {
        return "integration.assistant." + p.chave() + ".api-key-last4";
    }

    static String model(ProvedorIa p) {
        return "integration.assistant." + p.chave() + ".model";
    }

    static String lastTest(ProvedorIa p) {
        return "integration.assistant." + p.chave() + ".last-test-detail";
    }

    @Override
    public AssistantSettings get() {
        Optional<SystemConfig> enabled = store.find(ENABLED);
        List<AssistantProviderSettings> providers = Arrays.stream(ProvedorIa.values())
                .map(p -> new AssistantProviderSettings(p,
                        store.read(apiKey(p)) == null ? null : store.read(apiKeyLast4(p)),
                        modelo(p),
                        store.read(lastTest(p))))
                .toList();
        return new AssistantSettings(
                enabled.map(SystemConfig::asBoolean).orElse(false),
                ativo(),
                reserva(),
                providers,
                enabled.map(SystemConfig::updatedAt).orElse(null),
                enabled.map(SystemConfig::updatedBy).orElse(null),
                monthlyTokenLimit().orElse(null));
    }

    @Override
    @Transactional
    public AssistantSettings update(UpdateCommand command, String updatedBy) {
        ProvedorIa ativo = command.provider() == null ? ativo() : command.provider();
        Map<ProvedorIa, ProviderCommand> comandos = new EnumMap<>(ProvedorIa.class);
        if (command.providers() != null) {
            comandos.putAll(command.providers());
        }
        // Formato antigo do PUT ({apiKey, model} no topo): vale para o ativo, se o mapa não o trouxe.
        if ((command.apiKey() != null || command.model() != null) && !comandos.containsKey(ativo)) {
            comandos.put(ativo, new ProviderCommand(command.apiKey(), command.model()));
        }

        Map<ProvedorIa, String> modelos = new EnumMap<>(ProvedorIa.class);
        comandos.forEach((p, c) -> {
            String m = IntegrationConfigStore.clean(c.model(), "Modelo");
            if (m != null) {
                if (!p.modeloValido(m)) {
                    throw new InvalidIntegrationException("Modelo inválido para " + p.rotulo() + ": " + m
                            + " (ex.: " + defaultModels.get(p) + ")");
                }
                modelos.put(p, m);
            }
        });

        if (command.enabled() && !teraChave(ativo, comandos)) {
            throw new InvalidIntegrationException("Para ativar o assistente informe a chave de " + ativo.rotulo());
        }
        ProvedorIa reserva = command.fallbackInformado() ? command.fallbackProvider() : reserva();
        if (reserva == ativo) {
            if (command.fallbackInformado()) {
                throw new InvalidIntegrationException("O provedor reserva precisa ser diferente do ativo");
            }
            reserva = null; // trocou o ativo para o que era a reserva
        }
        if (reserva != null && !teraChave(reserva, comandos)) {
            throw new InvalidIntegrationException("Cadastre a chave de " + reserva.rotulo()
                    + " antes de usá-lo como reserva");
        }

        Instant now = Instant.now();
        modelos.forEach((p, m) -> store.save(model(p), m, now, updatedBy));
        comandos.forEach((p, c) -> {
            if (store.applySecret(c.apiKey(), apiKey(p), apiKeyLast4(p), now, updatedBy)) {
                // Chave nova (ou removida): o resultado do último teste não vale mais.
                store.save(lastTest(p), null, now, updatedBy);
            }
        });
        store.save(PROVIDER, ativo.name(), now, updatedBy);
        store.save(FALLBACK, reserva == null ? null : reserva.name(), now, updatedBy);
        // Por último: é dele que vêm updatedAt/updatedBy da leitura.
        store.save(ENABLED, String.valueOf(command.enabled()), now, updatedBy);
        return get();
    }

    @Override
    public IntegrationTestResult sendTest(ProvedorIa provider, String requestedBy) {
        ProvedorIa p = provider == null ? ativo() : provider;
        if (store.read(apiKey(p)) == null) {
            throw new InvalidIntegrationException("Salve a chave de " + p.rotulo() + " antes de testar");
        }
        Optional<String> chave = store.decrypt(apiKey(p));
        IntegrationTestResult result = chave.isEmpty()
                ? IntegrationTestResult.failed("Não foi possível ler a chave salva. Cadastre-a de novo.", null)
                : llmPort.testar(new AssistantCredentials(p, chave.get(), modelo(p)));
        log.info("assistant.integration.test provider={} success={} by={}", p, result.success(), requestedBy);
        String detail = result.success()
                ? "OK" + (result.detail() == null ? "" : " — " + result.detail())
                : "Falhou: " + (result.error() == null ? "erro desconhecido" : result.error());
        store.save(lastTest(p), truncate(detail), Instant.now(), requestedBy);
        return result;
    }

    @Override
    public Optional<List<AssistantCredentials>> activeCredentials() {
        if (!store.readBoolean(ENABLED)) {
            return Optional.empty();
        }
        ProvedorIa ativo = ativo();
        Optional<AssistantCredentials> principal = credenciais(ativo);
        if (principal.isEmpty()) {
            return Optional.empty();
        }
        List<AssistantCredentials> cadeia = new ArrayList<>(List.of(principal.get()));
        ProvedorIa reserva = reserva();
        if (reserva != null && reserva != ativo) {
            credenciais(reserva).ifPresent(cadeia::add);
        }
        return Optional.of(List.copyOf(cadeia));
    }

    @Override
    public Optional<Long> monthlyTokenLimit() {
        String v = store.read(MONTHLY_LIMIT);
        if (v == null || v.isBlank()) {
            return Optional.empty();
        }
        try {
            long n = Long.parseLong(v.strip());
            return n > 0 ? Optional.of(n) : Optional.empty();
        } catch (NumberFormatException ex) {
            log.warn("assistant.integration.monthly-limit.invalido valor={}", v);
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public AssistantSettings updateMonthlyTokenLimit(Long limit, String updatedBy) {
        store.save(MONTHLY_LIMIT, limit == null || limit < 1 ? null : String.valueOf(limit), Instant.now(), updatedBy);
        return get();
    }

    @Override
    public Map<ProvedorIa, String> defaultModels() {
        return Map.copyOf(defaultModels);
    }

    private Optional<AssistantCredentials> credenciais(ProvedorIa p) {
        return store.decrypt(apiKey(p)).map(key -> new AssistantCredentials(p, key, modelo(p)));
    }

    private boolean teraChave(ProvedorIa p, Map<ProvedorIa, ProviderCommand> comandos) {
        ProviderCommand c = comandos.get(p);
        return store.willHaveSecret(c == null ? null : c.apiKey(), apiKey(p));
    }

    private ProvedorIa ativo() {
        ProvedorIa p = provedor(store.read(PROVIDER));
        return p == null ? ProvedorIa.ANTHROPIC : p;
    }

    private ProvedorIa reserva() {
        return provedor(store.read(FALLBACK));
    }

    private static ProvedorIa provedor(String valor) {
        try {
            return ProvedorIa.de(valor);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String modelo(ProvedorIa p) {
        String salvo = store.read(model(p));
        return salvo == null ? defaultModels.get(p) : salvo;
    }

    private static String truncate(String value) {
        return value.length() > IntegrationConfigStore.MAX_FIELD
                ? value.substring(0, IntegrationConfigStore.MAX_FIELD) : value;
    }
}
