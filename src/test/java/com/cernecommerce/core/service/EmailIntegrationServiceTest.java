package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.email.EmailDeliveryException;
import com.cernecommerce.core.domain.exception.email.InvalidEmailIntegrationException;
import com.cernecommerce.core.domain.model.config.EmailIntegrationSettings;
import com.cernecommerce.core.domain.model.config.EmailProvider;
import com.cernecommerce.core.domain.model.config.EmailSample;
import com.cernecommerce.core.domain.model.config.EmailSenderConfig;
import com.cernecommerce.core.domain.model.config.EmailTestResult;
import com.cernecommerce.core.domain.model.config.EmailTestTarget;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.ports.in.EmailIntegrationUseCase.UpdateCommand;
import com.cernecommerce.core.ports.out.SecretCipherPort;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.notification.EmailSampleSenderPort;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class EmailIntegrationServiceTest {

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
    /** Cifra fake reversível — o que importa é que o valor gravado não é a chave em claro. */
    private final SecretCipherPort cipher = new SecretCipherPort() {
        @Override public String encrypt(String p) { return "enc(" + new StringBuilder(p).reverse() + ")"; }
        @Override public String decrypt(String c) {
            return new StringBuilder(c.substring(4, c.length() - 1)).reverse().toString();
        }
    };
    private EmailSampleSenderPort sampleSender;
    private EmailIntegrationService service;

    @BeforeEach
    void setUp() {
        sampleSender = mock(EmailSampleSenderPort.class);
        service = new EmailIntegrationService(configPort, cipher, sampleSender,
                () -> com.cernecommerce.core.domain.model.notification.EmailChannelStatus.of(false, "LOG", "log"));
    }

    private static UpdateCommand cmd(boolean enabled, String from, String apiKey) {
        return new UpdateCommand(enabled, EmailProvider.RESEND, from, "Mahal", null, apiKey);
    }

    @Test
    void semConfiguracao_desativada_e_sem_remetente_ativo() {
        EmailIntegrationSettings s = service.get();

        assertThat(s.enabled()).isFalse();
        assertThat(s.apiKeyConfigured()).isFalse();
        assertThat(s.provider()).isEqualTo(EmailProvider.RESEND);
        assertThat(service.activeSenderConfig()).isEmpty();
    }

    @Test
    void update_grava_chave_cifrada_e_expoe_so_os_4_ultimos() {
        EmailIntegrationSettings s = service.update(cmd(true, "loja@mahal.com", "re_secret_ABCD"), "admin");

        assertThat(store.get(EmailIntegrationService.API_KEY).value())
                .isNotEqualTo("re_secret_ABCD").doesNotContain("re_secret");
        assertThat(s.apiKeyConfigured()).isTrue();
        assertThat(s.apiKeyLast4()).isEqualTo("ABCD");
        assertThat(s.updatedBy()).isEqualTo("admin");
        assertThat(service.activeSenderConfig()).contains(
                new EmailSenderConfig(EmailProvider.RESEND, "re_secret_ABCD", "loja@mahal.com", "Mahal", null));
    }

    @Test
    void update_com_apiKey_null_mantem_a_chave_salva() {
        service.update(cmd(false, "loja@mahal.com", "re_first_1111"), "admin");

        EmailIntegrationSettings s = service.update(cmd(true, "outro@mahal.com", null), "admin");

        assertThat(s.apiKeyLast4()).isEqualTo("1111");
        assertThat(service.activeSenderConfig()).get()
                .extracting(EmailSenderConfig::apiKey, EmailSenderConfig::fromEmail)
                .containsExactly("re_first_1111", "outro@mahal.com");
    }

    @Test
    void update_com_apiKey_vazia_remove_a_chave() {
        service.update(cmd(false, "loja@mahal.com", "re_first_1111"), "admin");

        EmailIntegrationSettings s = service.update(cmd(false, "loja@mahal.com", ""), "admin");

        assertThat(s.apiKeyConfigured()).isFalse();
        assertThat(s.apiKeyLast4()).isNull();
    }

    @Test
    void desativada_nao_e_usada_no_envio_mesmo_com_chave() {
        service.update(cmd(false, "loja@mahal.com", "re_key_2222"), "admin");

        assertThat(service.activeSenderConfig()).isEmpty();
    }

    @Test
    void ativar_sem_chave_ou_sem_remetente_e_recusado() {
        assertThatThrownBy(() -> service.update(cmd(true, "loja@mahal.com", null), "admin"))
                .isInstanceOf(InvalidEmailIntegrationException.class);
        assertThatThrownBy(() -> service.update(cmd(true, null, "re_key"), "admin"))
                .isInstanceOf(InvalidEmailIntegrationException.class);
    }

    @Test
    void remetente_invalido_e_recusado() {
        assertThatThrownBy(() -> service.update(cmd(false, "nao-e-email", null), "admin"))
                .isInstanceOf(InvalidEmailIntegrationException.class)
                .hasMessageContaining("remetente");
    }

    @Test
    void chave_que_nao_decifra_cai_no_provedor_do_ambiente() {
        service.update(cmd(true, "loja@mahal.com", "re_key_3333"), "admin");
        store.put(EmailIntegrationService.API_KEY, new SystemConfig(EmailIntegrationService.API_KEY, "lixo", null, null));

        assertThat(service.activeSenderConfig()).isEmpty();
    }

    @Test
    void sendTest_sem_sample_envia_todos_e_reporta_falha_individual() {
        service.update(cmd(false, "loja@mahal.com", "re_key_4444"), "admin");
        doThrow(new EmailDeliveryException("403 domain not verified"))
                .when(sampleSender).sendSample(any(), eq("eu@x.com"), eq(EmailSample.ORDER_CONFIRMATION));

        List<EmailTestResult> results = service.sendTest("eu@x.com", null, null, "admin");

        assertThat(results).hasSize(EmailSample.values().length);
        verify(sampleSender, times(EmailSample.values().length)).sendSample(any(), eq("eu@x.com"), any());
        assertThat(results).filteredOn(r -> !r.success()).singleElement()
                .satisfies(r -> {
                    assertThat(r.sample()).isEqualTo(EmailSample.ORDER_CONFIRMATION);
                    assertThat(r.error()).contains("domain not verified");
                });
    }

    @Test
    void sendTest_sem_chave_salva_e_recusado() {
        assertThatThrownBy(() -> service.sendTest("eu@x.com", EmailSample.PASSWORD_RESET, EmailTestTarget.STORE, "admin"))
                .isInstanceOf(InvalidEmailIntegrationException.class);
        verifyNoInteractions(sampleSender);
    }

    @Test
    void sendTest_pelo_ambiente_nao_exige_a_integracao_da_loja() {
        doThrow(new EmailDeliveryException("connection refused"))
                .when(sampleSender).sendEnvironmentSample("eu@x.com", EmailSample.TOKEN_THEFT);

        List<EmailTestResult> results = service.sendTest("eu@x.com", null, EmailTestTarget.ENVIRONMENT, "admin");

        assertThat(results).hasSize(EmailSample.values().length);
        verify(sampleSender, times(EmailSample.values().length)).sendEnvironmentSample(eq("eu@x.com"), any());
        verify(sampleSender, never()).sendSample(any(), any(), any());
        assertThat(results).filteredOn(r -> !r.success()).singleElement()
                .satisfies(r -> assertThat(r.sample()).isEqualTo(EmailSample.TOKEN_THEFT));
    }
}
