package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.integration.InvalidIntegrationException;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.domain.model.config.WhatsappConnectionStatus;
import com.cernecommerce.core.domain.model.config.WhatsappIntegrationSettings;
import com.cernecommerce.core.domain.model.config.WhatsappSendResult;
import com.cernecommerce.core.ports.in.WhatsappIntegrationUseCase.UpdateCommand;
import com.cernecommerce.core.ports.out.SecretCipherPort;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.notification.WhatsappPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WhatsappIntegrationServiceTest {

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
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-07T12:00:00Z"));
    private WhatsappPort whatsappPort;
    private WhatsappIntegrationService service;

    @BeforeEach
    void setUp() {
        whatsappPort = mock(WhatsappPort.class);
        service = new WhatsappIntegrationService(configPort, cipher, whatsappPort, clock, Duration.ofMinutes(5));
    }

    private static UpdateCommand cmd(boolean enabled, String phone, String token, String verify) {
        return new UpdateCommand(enabled, phone, "9876543210", token, verify);
    }

    @Test
    void salva_tokensCifrados_eSoDevolveOsUltimos4() {
        WhatsappIntegrationSettings saved = service.update(cmd(true, "1234567890", "EAAGsegredo-wxyz", "verif"), "admin");

        assertThat(saved.enabled()).isTrue();
        assertThat(saved.accessTokenLast4()).isEqualTo("wxyz");
        assertThat(saved.verifyTokenConfigured()).isTrue();
        assertThat(saved.updatedBy()).isEqualTo("admin");
        assertThat(store.values()).noneMatch(c -> c.value().contains("EAAGsegredo"));
    }

    @Test
    void tokenAusenteMantem_eVazioRemove() {
        service.update(cmd(false, "1234567890", "EAAG-1111", "verif"), "admin");

        assertThat(service.update(cmd(false, "1234567890", null, null), "admin").accessTokenLast4()).isEqualTo("1111");
        WhatsappIntegrationSettings removido = service.update(cmd(false, "1234567890", "", ""), "admin");
        assertThat(removido.accessTokenConfigured()).isFalse();
        assertThat(removido.verifyTokenConfigured()).isFalse();
    }

    @Test
    void ativarSemPhoneOuSemToken_eRecusado() {
        assertThatThrownBy(() -> service.update(cmd(true, null, "EAAG", null), "admin"))
                .isInstanceOf(InvalidIntegrationException.class);
        assertThatThrownBy(() -> service.update(cmd(true, "1234567890", null, null), "admin"))
                .isInstanceOf(InvalidIntegrationException.class);
        assertThatThrownBy(() -> service.update(cmd(false, "abc", null, null), "admin"))
                .isInstanceOf(InvalidIntegrationException.class);
    }

    @Test
    void status_desativado_naoChamaAMeta() {
        service.update(cmd(false, "1234567890", "EAAG", null), "admin");

        assertThat(service.connectionStatus().connected()).isFalse();
        verifyNoInteractions(whatsappPort);
    }

    @Test
    void status_usaCache_eInvalidaNoUpdate() {
        service.update(cmd(true, "1234567890", "EAAG", null), "admin");
        when(whatsappPort.checkPhoneNumber("1234567890", "EAAG")).thenReturn(WhatsappSendResult.ok(null));

        assertThat(service.connectionStatus()).isEqualTo(WhatsappConnectionStatus.up());
        service.connectionStatus();
        verify(whatsappPort, times(1)).checkPhoneNumber(any(), any());

        clock.advance(Duration.ofMinutes(6));
        service.connectionStatus();
        verify(whatsappPort, times(2)).checkPhoneNumber(any(), any());

        when(whatsappPort.checkPhoneNumber("1234567890", "EAAG")).thenReturn(WhatsappSendResult.failed("token expirado"));
        service.update(cmd(true, "1234567890", null, null), "admin");
        WhatsappConnectionStatus status = service.connectionStatus();
        assertThat(status.connected()).isFalse();
        assertThat(status.detail()).isEqualTo("token expirado");
    }

    @Test
    void teste_semTemplate_usaHelloWorld_mesmoDesativado() {
        service.update(cmd(false, "1234567890", "EAAG", null), "admin");
        when(whatsappPort.sendTemplate(any(), any(), any(), any(), any(), any())).thenReturn(WhatsappSendResult.ok("wamid.X"));

        IntegrationTestResult result = service.sendTest("+55 (85) 99999-9999", null, "admin");

        assertThat(result.success()).isTrue();
        assertThat(result.detail()).isEqualTo("wamid.X");
        verify(whatsappPort).sendTemplate("1234567890", "EAAG", "5585999999999", "hello_world", "en_US", List.of());
    }

    @Test
    void teste_recusaDaMeta_voltaComoFalha() {
        service.update(cmd(false, "1234567890", "EAAG", null), "admin");
        when(whatsappPort.sendTemplate(any(), any(), any(), any(), any(), any()))
                .thenReturn(WhatsappSendResult.failed("Template name does not exist"));

        IntegrationTestResult result = service.sendTest("5585999999999", "promo_cashback", "admin");

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isEqualTo("Template name does not exist");
    }

    @Test
    void teste_semConfiguracao_ouNumeroInvalido_eRecusado() {
        assertThatThrownBy(() -> service.sendTest("5585999999999", null, "admin"))
                .isInstanceOf(InvalidIntegrationException.class);
        service.update(cmd(false, "1234567890", "EAAG", null), "admin");
        assertThatThrownBy(() -> service.sendTest("123", null, "admin"))
                .isInstanceOf(InvalidIntegrationException.class);
    }

    @Test
    void credenciaisAtivas_soComIntegracaoAtiva() {
        service.update(cmd(false, "1234567890", "EAAG", null), "admin");
        assertThat(service.activeCredentials()).isEmpty();

        service.update(cmd(true, "1234567890", null, null), "admin");
        assertThat(service.activeCredentials()).hasValueSatisfying(c -> assertThat(c.accessToken()).isEqualTo("EAAG"));
    }

    @Test
    void verifyToken_conferido() {
        service.update(cmd(false, "1234567890", null, "meu-verify"), "admin");

        assertThat(service.matchesVerifyToken("meu-verify")).isTrue();
        assertThat(service.matchesVerifyToken("outro")).isFalse();
        assertThat(service.matchesVerifyToken(null)).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
