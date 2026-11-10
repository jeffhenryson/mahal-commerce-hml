package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.integration.InvalidIntegrationException;
import com.cernecommerce.core.domain.model.config.AssistantCredentials;
import com.cernecommerce.core.domain.model.config.AssistantSettings;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.domain.model.config.ProvedorIa;
import com.cernecommerce.core.ports.in.AssistantIntegrationUseCase.ProviderCommand;
import com.cernecommerce.core.ports.in.AssistantIntegrationUseCase.UpdateCommand;
import com.cernecommerce.core.ports.out.SecretCipherPort;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssistantIntegrationServiceTest {

    private final Map<String, SystemConfig> store = new HashMap<>();
    private final SystemConfigPort configPort = new SystemConfigPort() {
        @Override public Optional<SystemConfig> findByKey(String key) { return Optional.ofNullable(store.get(key)); }
        @Override public List<SystemConfig> findAll() { return new ArrayList<>(store.values()); }
        @Override public SystemConfig save(SystemConfig c) { store.put(c.key(), c); return c; }
        @Override public boolean getBoolean(String key, boolean d) {
            return findByKey(key).map(SystemConfig::asBoolean).orElse(d);
        }
        @Override public int getInt(String key, int d) { return d; }
        @Override public BigDecimal getDecimal(String key, BigDecimal d) { return d; }
    };
    private final SecretCipherPort cipher = new SecretCipherPort() {
        @Override public String encrypt(String p) { return "enc(" + new StringBuilder(p).reverse() + ")"; }
        @Override public String decrypt(String c) {
            return new StringBuilder(c.substring(4, c.length() - 1)).reverse().toString();
        }
    };
    private AssistenteLlmPort llm;
    private AssistantIntegrationService service;

    @BeforeEach
    void setUp() {
        llm = mock(AssistenteLlmPort.class);
        service = new AssistantIntegrationService(configPort, cipher, llm, Map.of(
                ProvedorIa.ANTHROPIC, "claude-sonnet-5-5",
                ProvedorIa.OPENAI, "gpt-5.5",
                ProvedorIa.GEMINI, "gemini-3.5-flash"));
    }

    private static UpdateCommand novo(boolean enabled, ProvedorIa ativo, ProvedorIa reserva,
            Map<ProvedorIa, ProviderCommand> providers) {
        return new UpdateCommand(enabled, ativo, reserva, true, providers, null, null);
    }

    @Test
    void sem_configuracao_fica_desativada_no_claude_com_modelos_padrao() {
        AssistantSettings s = service.get();
        assertThat(s.enabled()).isFalse();
        assertThat(s.provider()).isEqualTo(ProvedorIa.ANTHROPIC);
        assertThat(s.fallbackProvider()).isNull();
        assertThat(s.apiKeyConfigured()).isFalse();
        assertThat(s.model()).isEqualTo("claude-sonnet-5-5");
        assertThat(s.doProvedor(ProvedorIa.OPENAI).model()).isEqualTo("gpt-5.5");
        assertThat(s.doProvedor(ProvedorIa.GEMINI).model()).isEqualTo("gemini-3.5-flash");
        assertThat(service.activeCredentials()).isEmpty();
    }

    @Test
    void formato_antigo_salva_a_chave_do_claude_cifrada() {
        AssistantSettings s = service.update(new UpdateCommand(true, "sk-ant-abc-1234", null), "admin");

        assertThat(s.enabled()).isTrue();
        assertThat(s.apiKeyLast4()).isEqualTo("1234");
        assertThat(store.get("integration.assistant.anthropic.api-key").value()).doesNotContain("sk-ant-abc-1234");
        assertThat(service.activeCredentials()).contains(List.of(
                new AssistantCredentials(ProvedorIa.ANTHROPIC, "sk-ant-abc-1234", "claude-sonnet-5-5")));
    }

    @Test
    void uma_chave_por_provedor_e_trocar_o_ativo_nao_apaga_as_outras() {
        service.update(novo(true, ProvedorIa.ANTHROPIC, null, Map.of(
                ProvedorIa.ANTHROPIC, new ProviderCommand("sk-ant-abc-1111", null),
                ProvedorIa.OPENAI, new ProviderCommand("sk-proj-xyz-2222", "gpt-5.4"),
                ProvedorIa.GEMINI, new ProviderCommand("AIza-3333", null))), "admin");

        AssistantSettings s = service.update(novo(true, ProvedorIa.OPENAI, null, Map.of()), "admin");

        assertThat(s.provider()).isEqualTo(ProvedorIa.OPENAI);
        assertThat(s.apiKeyLast4()).isEqualTo("2222");
        assertThat(s.model()).isEqualTo("gpt-5.4");
        assertThat(s.doProvedor(ProvedorIa.ANTHROPIC).apiKeyLast4()).isEqualTo("1111");
        assertThat(s.doProvedor(ProvedorIa.GEMINI).apiKeyLast4()).isEqualTo("3333");
        assertThat(service.activeCredentials()).contains(List.of(
                new AssistantCredentials(ProvedorIa.OPENAI, "sk-proj-xyz-2222", "gpt-5.4")));
    }

    @Test
    void formato_antigo_vale_para_o_provedor_ativo() {
        service.update(novo(false, ProvedorIa.GEMINI, null, Map.of()), "admin");
        service.update(new UpdateCommand(true, "AIza-9999", "gemini-3.5-flash"), "admin");

        assertThat(service.get().provider()).isEqualTo(ProvedorIa.GEMINI);
        assertThat(service.get().apiKeyLast4()).isEqualTo("9999");
        assertThat(service.get().doProvedor(ProvedorIa.ANTHROPIC).apiKeyConfigured()).isFalse();
    }

    @Test
    void cadeia_traz_o_ativo_e_depois_a_reserva() {
        service.update(novo(true, ProvedorIa.GEMINI, ProvedorIa.ANTHROPIC, Map.of(
                ProvedorIa.GEMINI, new ProviderCommand("AIza-1111", null),
                ProvedorIa.ANTHROPIC, new ProviderCommand("sk-ant-2222", null))), "admin");

        assertThat(service.activeCredentials()).contains(List.of(
                new AssistantCredentials(ProvedorIa.GEMINI, "AIza-1111", "gemini-3.5-flash"),
                new AssistantCredentials(ProvedorIa.ANTHROPIC, "sk-ant-2222", "claude-sonnet-5-5")));
    }

    @Test
    void reserva_sem_chave_ou_igual_ao_ativo_e_recusada() {
        Map<ProvedorIa, ProviderCommand> soClaude = Map.of(ProvedorIa.ANTHROPIC, new ProviderCommand("sk-ant-1", null));
        assertThatThrownBy(() -> service.update(novo(true, ProvedorIa.ANTHROPIC, ProvedorIa.OPENAI, soClaude), "admin"))
                .isInstanceOf(InvalidIntegrationException.class);
        assertThatThrownBy(() -> service.update(novo(true, ProvedorIa.ANTHROPIC, ProvedorIa.ANTHROPIC, soClaude),
                "admin")).isInstanceOf(InvalidIntegrationException.class);
    }

    @Test
    void ativar_sem_a_chave_do_ativo_recusa() {
        service.update(novo(false, ProvedorIa.ANTHROPIC, null,
                Map.of(ProvedorIa.ANTHROPIC, new ProviderCommand("sk-ant-1", null))), "admin");
        assertThatThrownBy(() -> service.update(novo(true, ProvedorIa.OPENAI, null, Map.of()), "admin"))
                .isInstanceOf(InvalidIntegrationException.class);
    }

    @Test
    void chave_vazia_remove_e_desativada_nao_libera_credenciais() {
        service.update(new UpdateCommand(true, "sk-ant-abc-1234", null), "admin");
        service.update(new UpdateCommand(false, "", null), "admin");
        assertThat(service.get().apiKeyConfigured()).isFalse();
        assertThat(service.activeCredentials()).isEmpty();
    }

    @Test
    void modelo_precisa_ser_do_provedor() {
        assertThatThrownBy(() -> service.update(novo(false, ProvedorIa.OPENAI, null,
                Map.of(ProvedorIa.OPENAI, new ProviderCommand(null, "gemini-3.5-flash"))), "admin"))
                .isInstanceOf(InvalidIntegrationException.class);
        assertThatThrownBy(() -> service.update(new UpdateCommand(false, null, "gpt-5"), "admin"))
                .isInstanceOf(InvalidIntegrationException.class);

        service.update(novo(false, ProvedorIa.GEMINI, null,
                Map.of(ProvedorIa.GEMINI, new ProviderCommand(null, "gemini-3.1-pro-preview"))), "admin");
        assertThat(service.get().model()).isEqualTo("gemini-3.1-pro-preview");
    }

    @Test
    void teste_usa_a_chave_do_provedor_pedido_e_guarda_o_resultado_nele() {
        service.update(novo(false, ProvedorIa.ANTHROPIC, null, Map.of(
                ProvedorIa.ANTHROPIC, new ProviderCommand("sk-ant-1", null),
                ProvedorIa.OPENAI, new ProviderCommand("sk-proj-2", null))), "admin");
        when(llm.testar(any())).thenReturn(IntegrationTestResult.ok("gpt-5.5"));

        IntegrationTestResult r = service.sendTest(ProvedorIa.OPENAI, "admin");

        assertThat(r.success()).isTrue();
        verify(llm).testar(new AssistantCredentials(ProvedorIa.OPENAI, "sk-proj-2", "gpt-5.5"));
        assertThat(service.get().doProvedor(ProvedorIa.OPENAI).statusDetail()).startsWith("OK");
        assertThat(service.get().doProvedor(ProvedorIa.ANTHROPIC).statusDetail()).isNull();
    }

    @Test
    void teste_sem_chave_recusa_sem_chamar_o_provedor() {
        assertThatThrownBy(() -> service.sendTest(ProvedorIa.GEMINI, "admin"))
                .isInstanceOf(InvalidIntegrationException.class);
        verifyNoInteractions(llm);
    }
}
