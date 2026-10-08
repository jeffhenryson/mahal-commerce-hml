package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.integration.InvalidIntegrationException;
import com.cernecommerce.core.domain.model.config.AutomationPlatform;
import com.cernecommerce.core.domain.model.config.AutomationPlatformSettings;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.domain.model.crm.WebhookDispatchResult;
import com.cernecommerce.core.ports.in.AutomationPlatformIntegrationUseCase.UpdateCommand;
import com.cernecommerce.core.ports.out.SecretCipherPort;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.crm.CampaignWebhookPort;
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

class AutomationPlatformIntegrationServiceTest {

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
    private CampaignWebhookPort webhookPort;

    @BeforeEach
    void setUp() {
        webhookPort = mock(CampaignWebhookPort.class);
    }

    private AutomationPlatformIntegrationService service(boolean allowHttp) {
        return new AutomationPlatformIntegrationService(configPort, cipher, webhookPort, allowHttp);
    }

    @Test
    void salva_tokenCifrado_ePadraoN8n() {
        AutomationPlatformSettings saved = service(false).update(
                new UpdateCommand(true, null, "https://n8n.loja.com/webhook", "tk-segredo-wxyz"), "admin");

        assertThat(saved.platform()).isEqualTo(AutomationPlatform.N8N);
        assertThat(saved.tokenLast4()).isEqualTo("wxyz");
        assertThat(store.values()).noneMatch(c -> c.value().contains("tk-segredo"));
    }

    @Test
    void http_soForaDeProd() {
        UpdateCommand http = new UpdateCommand(true, AutomationPlatform.MAKE, "http://make.local/hook", null);

        assertThatThrownBy(() -> service(false).update(http, "admin")).isInstanceOf(InvalidIntegrationException.class);
        assertThat(service(true).update(http, "admin").baseUrl()).isEqualTo("http://make.local/hook");
        assertThatThrownBy(() -> service(true).update(new UpdateCommand(true, null, "ftp://x", null), "admin"))
                .isInstanceOf(InvalidIntegrationException.class);
        assertThatThrownBy(() -> service(true).update(new UpdateCommand(true, null, null, null), "admin"))
                .isInstanceOf(InvalidIntegrationException.class);
    }

    @Test
    void teste_postaPingComBearer_eDevolveStatusHttp() {
        AutomationPlatformIntegrationService service = service(false);
        service.update(new UpdateCommand(false, null, "https://n8n.loja.com/webhook", "tk"), "admin");
        when(webhookPort.send(any(), any(), any())).thenReturn(WebhookDispatchResult.ok(200));

        IntegrationTestResult result = service.sendTest("admin");

        assertThat(result.success()).isTrue();
        assertThat(result.detail()).isEqualTo("HTTP 200");
        verify(webhookPort).send("https://n8n.loja.com/webhook", Map.of("Authorization", "Bearer tk"),
                Map.of("ping", true));
    }

    @Test
    void teste_falhaVoltaComErroEStatus() {
        AutomationPlatformIntegrationService service = service(false);
        service.update(new UpdateCommand(false, null, "https://n8n.loja.com/webhook", null), "admin");
        when(webhookPort.send(any(), any(), any())).thenReturn(WebhookDispatchResult.failure(404, "Not Found"));

        IntegrationTestResult result = service.sendTest("admin");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("Not Found");
        assertThat(result.detail()).isEqualTo("HTTP 404");
        verify(webhookPort).send(any(), org.mockito.ArgumentMatchers.eq(Map.of()), any());
    }

    @Test
    void destinoAtivo_soComIntegracaoAtiva() {
        AutomationPlatformIntegrationService service = service(false);
        assertThatThrownBy(() -> service.sendTest("admin")).isInstanceOf(InvalidIntegrationException.class);

        service.update(new UpdateCommand(false, null, "https://n8n.loja.com/webhook", "tk"), "admin");
        assertThat(service.activeTarget()).isEmpty();

        service.update(new UpdateCommand(true, null, "https://n8n.loja.com/webhook", null), "admin");
        assertThat(service.activeTarget()).hasValueSatisfying(t -> {
            assertThat(t.token()).isEqualTo("tk");
            assertThat(t.urlFor("/reativacao")).isEqualTo("https://n8n.loja.com/webhook/reativacao");
        });
    }
}
