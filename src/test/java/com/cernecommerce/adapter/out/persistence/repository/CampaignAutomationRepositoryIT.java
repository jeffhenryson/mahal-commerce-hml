package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CampaignAutomationEntity;
import com.cernecommerce.core.domain.model.crm.AutomationAuthType;
import com.cernecommerce.core.domain.model.crm.AutomationDelivery;
import com.cernecommerce.core.domain.model.crm.AutomationDestination;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationMetadata;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistência das automações contra banco real: o segredo do webhook gravado cifrado, a leitura
 * das linhas antigas (JSON em claro) e a chave única de {@code campaign_log} que torna o disparo
 * por evento idempotente.
 *
 * <p>Sem {@code @Transactional} de propósito: {@code claim} roda em transação própria
 * ({@code REQUIRES_NEW}), e é ela que precisa colidir. Os dados são removidos no fim.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
class CampaignAutomationRepositoryIT {

    @Autowired CampaignAutomationRepositoryImpl automationRepository;
    @Autowired CampaignAutomationJpaRepository automationJpaRepository;
    @Autowired CampaignLogRepositoryImpl logRepository;
    @Autowired CampaignLogJpaRepository logJpaRepository;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        created.forEach(id -> {
            logJpaRepository.findByAutomationIdOrderByDisparadoEmDesc(id).forEach(logJpaRepository::delete);
            automationJpaRepository.deleteById(id);
        });
    }

    private CampaignAutomation save(CampaignAutomation automation) {
        CampaignAutomation saved = automationRepository.save(automation);
        created.add(saved.id());
        return saved;
    }

    @Test
    void segredo_gravadoCifrado_eLidoDeVolta() {
        AutomationDelivery entrega = new AutomationDelivery(AutomationDestination.WEBHOOK, "https://x.com/h", null,
                null, null, AutomationAuthType.HEADER, "X-Api-Key", "9876", Map.of("X-Api-Key", "chave-9876"));
        CampaignAutomation saved = save(new CampaignAutomation(null, "Segredo", CampaignTrigger.EVENTO,
                AutomationEvent.PEDIDO_CRIADO, null, CampaignChannel.EMAIL, "Oi", true, Instant.now(), entrega,
                List.of(AutomationMetadata.PEDIDO)));

        CampaignAutomationEntity row = automationJpaRepository.findById(saved.id()).orElseThrow();
        assertThat(row.getWebhookHeaders()).isNotBlank().doesNotContain("chave-9876");

        CampaignAutomation read = automationRepository.findById(saved.id()).orElseThrow();
        assertThat(read.webhookHeaders()).containsEntry("X-Api-Key", "chave-9876");
        assertThat(read.entrega().authTipo()).isEqualTo(AutomationAuthType.HEADER);
        assertThat(read.entrega().authLast4()).isEqualTo("9876");
        assertThat(read.evento()).isEqualTo(AutomationEvent.PEDIDO_CRIADO);
        assertThat(read.segmentoAlvo()).isNull();
        assertThat(read.metadados()).containsExactly(AutomationMetadata.PEDIDO);
        assertThat(automationRepository.findActiveByEvent(AutomationEvent.PEDIDO_CRIADO))
                .extracting(CampaignAutomation::id).contains(saved.id());
    }

    @Test
    void linhaAntiga_comJsonEmClaro_eLidaNormalmente() {
        CampaignAutomation saved = save(CampaignAutomation.of(null, "Legado", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Oi", true, Instant.now(), "https://x.com/h", Map.of()));
        CampaignAutomationEntity row = automationJpaRepository.findById(saved.id()).orElseThrow();
        row.setWebhookHeaders("{\"Authorization\":\"Bearer legado-1234\"}");
        automationJpaRepository.save(row);

        CampaignAutomation read = automationRepository.findById(saved.id()).orElseThrow();

        assertThat(read.webhookHeaders()).containsEntry("Authorization", "Bearer legado-1234");
        assertThat(read.metadados()).isEqualTo(AutomationMetadata.DEFAULT);
    }

    @Test
    void claim_mesmaOcorrencia_soPassaUmaVez() {
        CampaignAutomation saved = save(CampaignAutomation.of(null, "Idempotente", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Oi", true, Instant.now(), "https://x.com/h", Map.of()));

        Optional<CampaignLogEntry> first = logRepository.claim(CampaignLogEntry.claim(saved.id(), 10L, "PEDIDO:1|c10"));
        Optional<CampaignLogEntry> again = logRepository.claim(CampaignLogEntry.claim(saved.id(), 10L, "PEDIDO:1|c10"));
        Optional<CampaignLogEntry> other = logRepository.claim(CampaignLogEntry.claim(saved.id(), null, "CAIXA:7"));

        assertThat(first).isPresent();
        assertThat(again).isEmpty();
        assertThat(other).hasValueSatisfying(e -> assertThat(e.customerId()).isNull());
        // Disparo manual (sem chave) nunca colide.
        logRepository.save(CampaignLogEntry.create(saved.id(), 10L));
        logRepository.save(CampaignLogEntry.create(saved.id(), 10L));
        assertThat(logRepository.findByAutomationId(saved.id())).hasSize(4);
    }
}
