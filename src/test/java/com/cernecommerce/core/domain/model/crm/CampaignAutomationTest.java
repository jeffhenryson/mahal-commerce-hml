package com.cernecommerce.core.domain.model.crm;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignAutomationTest {

    @Test
    void create_buildsActiveAutomationWithoutId() {
        CampaignAutomation automation = CampaignAutomation.create("Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Ola {nome}, seu saldo e {saldo}");

        assertThat(automation.id()).isNull();
        assertThat(automation.nome()).isEqualTo("Boas-vindas");
        assertThat(automation.gatilho()).isEqualTo(CampaignTrigger.MANUAL);
        assertThat(automation.segmentoAlvo()).isEqualTo(CustomerStage.NOVO_LEAD);
        assertThat(automation.canal()).isEqualTo(CampaignChannel.EMAIL);
        assertThat(automation.ativa()).isTrue();
        assertThat(automation.criadoEm()).isNotNull();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant criadoEm = Instant.parse("2026-01-01T00:00:00Z");
        CampaignAutomation automation = CampaignAutomation.of(1L, "Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Ola {nome}", false, criadoEm, null, Map.of());

        assertThat(automation.id()).isEqualTo(1L);
        assertThat(automation.ativa()).isFalse();
        assertThat(automation.criadoEm()).isEqualTo(criadoEm);
        assertThat(automation.hasWebhook()).isFalse();
    }

    @Test
    void withDetails_returnsNewInstanceWithUpdatedFieldsAndWebhook() {
        CampaignAutomation automation = CampaignAutomation.create("Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Ola {nome}");

        CampaignAutomation updated = automation.withDetails("Novo nome", CampaignTrigger.MANUAL, null,
                CustomerStage.QUALIFICADO, CampaignChannel.WHATSAPP, "Novo template",
                AutomationDelivery.webhook("https://n8n.example.com/webhook/abc",
                        Map.of("Authorization", "Bearer token")), null);

        assertThat(updated.nome()).isEqualTo("Novo nome");
        assertThat(updated.segmentoAlvo()).isEqualTo(CustomerStage.QUALIFICADO);
        assertThat(updated.canal()).isEqualTo(CampaignChannel.WHATSAPP);
        assertThat(updated.template()).isEqualTo("Novo template");
        assertThat(updated.webhookUrl()).isEqualTo("https://n8n.example.com/webhook/abc");
        assertThat(updated.webhookHeaders()).containsEntry("Authorization", "Bearer token");
        assertThat(updated.hasWebhook()).isTrue();
        assertThat(automation.hasWebhook()).isFalse();
    }

    @Test
    void throwsWhenWebhookUrlIsMalformed() {
        assertThatThrownBy(() -> CampaignAutomation.of(1L, "Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Ola {nome}", true, Instant.now(),
                "http://[invalid", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withAtiva_returnsNewInstanceWithUpdatedFlag() {
        CampaignAutomation automation = CampaignAutomation.create("Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Ola {nome}");

        CampaignAutomation deactivated = automation.withAtiva(false);

        assertThat(deactivated.ativa()).isFalse();
        assertThat(automation.ativa()).isTrue();
    }

    @Test
    void throwsWhenNomeIsBlank() {
        assertThatThrownBy(() -> CampaignAutomation.create(" ", CampaignTrigger.MANUAL, CustomerStage.NOVO_LEAD,
                CampaignChannel.EMAIL, "Ola {nome}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenTemplateIsBlank() {
        assertThatThrownBy(() -> CampaignAutomation.create("Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenGatilhoSegmentoOuCanalSaoNulos() {
        assertThatThrownBy(() -> CampaignAutomation.create("Boas-vindas", null, CustomerStage.NOVO_LEAD,
                CampaignChannel.EMAIL, "Ola {nome}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CampaignAutomation.create("Boas-vindas", CampaignTrigger.MANUAL, null,
                CampaignChannel.EMAIL, "Ola {nome}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CampaignAutomation.create("Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, null, "Ola {nome}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── gatilho EVENTO, destinos e segredo ────────────────────────────────────

    @Test
    void evento_exigeEvento_eAceitaSegmentoNulo() {
        assertThatThrownBy(() -> new CampaignAutomation(null, "Pós-venda", CampaignTrigger.EVENTO, null, null,
                CampaignChannel.WHATSAPP, "Oi", true, Instant.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class);

        CampaignAutomation automation = new CampaignAutomation(null, "Pós-venda", CampaignTrigger.EVENTO,
                AutomationEvent.PEDIDO_CONCLUIDO, null, CampaignChannel.WHATSAPP, "Oi", true, Instant.now(), null, null);

        assertThat(automation.segmentoAlvo()).isNull();
        assertThat(automation.acceptsStage(CustomerStage.INATIVO)).isTrue();
    }

    @Test
    void manual_semSegmento_eRecusado_eEventoEDescartado() {
        assertThatThrownBy(() -> new CampaignAutomation(null, "Lote", CampaignTrigger.MANUAL, null, null,
                CampaignChannel.EMAIL, "Oi", true, Instant.now(), null, null))
                .isInstanceOf(IllegalArgumentException.class);

        CampaignAutomation manual = new CampaignAutomation(null, "Lote", CampaignTrigger.MANUAL,
                AutomationEvent.PEDIDO_CRIADO, CustomerStage.INATIVO, CampaignChannel.EMAIL, "Oi", true,
                Instant.now(), null, null);
        assertThat(manual.evento()).isNull();
    }

    @Test
    void metadados_padraoSemPedido_eSemDuplicatas() {
        CampaignAutomation semMetadados = CampaignAutomation.create("A", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Oi");
        assertThat(semMetadados.metadados()).containsExactly(AutomationMetadata.CLIENTE, AutomationMetadata.LOJA,
                AutomationMetadata.DATA, AutomationMetadata.AUTOMACAO);

        CampaignAutomation comPedido = new CampaignAutomation(null, "A", CampaignTrigger.MANUAL, null,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Oi", true, Instant.now(), null,
                java.util.List.of(AutomationMetadata.PEDIDO, AutomationMetadata.CLIENTE, AutomationMetadata.PEDIDO));
        assertThat(comPedido.metadados()).containsExactly(AutomationMetadata.CLIENTE, AutomationMetadata.PEDIDO);
    }

    @Test
    void entrega_exigeOCampoDeCadaDestino() {
        assertThat(new AutomationDelivery(AutomationDestination.WEBHOOK, null, null, null, null, null, null, null, null)
                .missingRequirement()).contains("webhookUrl");
        assertThat(new AutomationDelivery(AutomationDestination.PLATAFORMA, null, " ", null, null, null, null, null, null)
                .missingRequirement()).contains("workflowPath");
        assertThat(new AutomationDelivery(AutomationDestination.WHATSAPP_META, null, null, null, null, null, null, null, null)
                .missingRequirement()).contains("whatsappTemplate");
        assertThat(new AutomationDelivery(AutomationDestination.PLATAFORMA, null, "reativacao", null, null, null, null,
                null, null).missingRequirement()).isNull();
    }

    @Test
    void segredo_ausenteMantem_vazioRemove_eNoneDescarta() {
        AutomationDelivery salvo = new AutomationDelivery(AutomationDestination.WEBHOOK, "https://x.com/h", null, null,
                null, AutomationAuthType.BEARER, null, "abcd", Map.of("Authorization", "Bearer token-abcd"));

        AutomationDelivery mantido = salvo.withSecret(null);
        assertThat(mantido.webhookHeaders()).containsEntry("Authorization", "Bearer token-abcd");
        assertThat(mantido.authLast4()).isEqualTo("abcd");

        AutomationDelivery trocado = salvo.withSecret(Map.of("Authorization", "Bearer novo-wxyz"));
        assertThat(trocado.authLast4()).isEqualTo("wxyz");

        AutomationDelivery removido = salvo.withSecret(Map.of());
        assertThat(removido.webhookHeaders()).isEmpty();
        assertThat(removido.authLast4()).isNull();

        AutomationDelivery semAuth = new AutomationDelivery(AutomationDestination.WEBHOOK, "https://x.com/h", null,
                null, null, AutomationAuthType.NONE, null, "abcd", Map.of("Authorization", "Bearer token-abcd"))
                .withSecret(null);
        assertThat(semAuth.webhookHeaders()).isEmpty();
        assertThat(semAuth.authLast4()).isNull();
    }

    @Test
    void webhookLegado_inferirTipoDeAutenticacao() {
        assertThat(AutomationDelivery.webhook("https://x.com/h", Map.of("Authorization", "Bearer abc12345")).authTipo())
                .isEqualTo(AutomationAuthType.BEARER);
        AutomationDelivery header = AutomationDelivery.webhook("https://x.com/h", Map.of("X-Api-Key", "k-9876"));
        assertThat(header.authTipo()).isEqualTo(AutomationAuthType.HEADER);
        assertThat(header.authHeaderNome()).isEqualTo("X-Api-Key");
        assertThat(header.authLast4()).isEqualTo("9876");
        assertThat(header.toString()).doesNotContain("k-9876");
    }
}
