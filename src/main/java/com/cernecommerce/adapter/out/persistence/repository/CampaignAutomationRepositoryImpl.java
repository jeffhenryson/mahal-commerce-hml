package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CampaignAutomationEntity;
import com.cernecommerce.core.domain.model.crm.AutomationDelivery;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationMetadata;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.ports.out.SecretCipherPort;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Persistência das automações. Os headers de autenticação do webhook ({@code webhook_headers}) são
 * gravados como JSON cifrado com o {@link SecretCipherPort} — o mesmo AES-GCM da chave do Resend.
 * Linhas anteriores à V144 guardam o JSON em claro (começa com {@code '{'}; o texto cifrado é
 * Base64Url e nunca começa assim): são lidas normalmente e recifradas no próximo save.
 */
@Repository
@Transactional
public class CampaignAutomationRepositoryImpl implements CampaignAutomationRepository {

    private static final Logger log = LoggerFactory.getLogger(CampaignAutomationRepositoryImpl.class);

    // Mapper estático — mesmo padrão de AuditLogRepositoryImpl: evita depender de um
    // ObjectMapper gerenciado pelo Spring, que pode faltar em contextos de teste leves.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CampaignAutomationJpaRepository campaignAutomationJpaRepository;
    private final SecretCipherPort cipherPort;

    public CampaignAutomationRepositoryImpl(CampaignAutomationJpaRepository campaignAutomationJpaRepository,
            SecretCipherPort cipherPort) {
        this.campaignAutomationJpaRepository = campaignAutomationJpaRepository;
        this.cipherPort = cipherPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CampaignAutomation> findById(Long id) {
        return campaignAutomationJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignAutomation> findAll() {
        return campaignAutomationJpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignAutomation> findActiveStageEntry() {
        return campaignAutomationJpaRepository.findByAtivaTrueAndGatilho(CampaignTrigger.ENTRADA_ESTAGIO).stream()
                .map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignAutomation> findActiveByEvent(AutomationEvent evento) {
        return campaignAutomationJpaRepository.findByAtivaTrueAndGatilhoAndEvento(CampaignTrigger.EVENTO, evento)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public CampaignAutomation save(CampaignAutomation automation) {
        AutomationDelivery entrega = automation.entrega();
        CampaignAutomationEntity entity = new CampaignAutomationEntity();
        entity.setId(automation.id());
        entity.setNome(automation.nome());
        entity.setGatilho(automation.gatilho());
        entity.setEvento(automation.evento());
        entity.setSegmentoAlvo(automation.segmentoAlvo());
        entity.setCanal(automation.canal());
        entity.setTemplate(automation.template());
        entity.setAtiva(automation.ativa());
        entity.setCriadoEm(automation.criadoEm());
        entity.setDestino(entrega.destino());
        entity.setWebhookUrl(entrega.webhookUrl());
        entity.setWebhookHeaders(encryptHeaders(entrega.webhookHeaders()));
        entity.setWorkflowPath(entrega.workflowPath());
        entity.setWhatsappTemplate(entrega.whatsappTemplate());
        entity.setWhatsappIdioma(entrega.whatsappIdioma());
        entity.setAuthTipo(entrega.authTipo());
        entity.setAuthHeaderNome(entrega.authHeaderNome());
        entity.setAuthLast4(entrega.authLast4());
        entity.setMetadados(automation.metadados().stream().map(Enum::name).collect(Collectors.joining(",")));
        CampaignAutomationEntity saved = campaignAutomationJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public void deleteById(Long id) {
        campaignAutomationJpaRepository.deleteById(id);
    }

    private CampaignAutomation toDomain(CampaignAutomationEntity e) {
        AutomationDelivery entrega = new AutomationDelivery(e.getDestino(), e.getWebhookUrl(), e.getWorkflowPath(),
                e.getWhatsappTemplate(), e.getWhatsappIdioma(), e.getAuthTipo(), e.getAuthHeaderNome(),
                e.getAuthLast4(), decryptHeaders(e.getId(), e.getWebhookHeaders()));
        return new CampaignAutomation(e.getId(), e.getNome(), e.getGatilho(), e.getEvento(), e.getSegmentoAlvo(),
                e.getCanal(), e.getTemplate(), e.isAtiva(), e.getCriadoEm(), entrega, parseMetadata(e.getMetadados()));
    }

    private String encryptHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return cipherPort.encrypt(MAPPER.writeValueAsString(headers));
        } catch (JsonProcessingException e) {
            log.warn("crm.automation.webhookHeaders.serialize.failed", e);
            return null;
        }
    }

    private Map<String, String> decryptHeaders(Long automationId, String stored) {
        if (stored == null || stored.isBlank()) {
            return Map.of();
        }
        String json;
        if (stored.startsWith("{")) {
            json = stored; // legado em claro (antes da V144)
        } else {
            try {
                json = cipherPort.decrypt(stored);
            } catch (Exception e) {
                // Chave de cifra trocada: a automação continua listável, só o disparo sai sem o segredo.
                log.error("crm.automation.webhookHeaders.decrypt.failed automationId={} error={}", automationId,
                        e.getMessage());
                return Map.of();
            }
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("crm.automation.webhookHeaders.deserialize.failed automationId={}", automationId);
            return Map.of();
        }
    }

    private static List<AutomationMetadata> parseMetadata(String csv) {
        if (csv == null || csv.isBlank()) {
            return null; // o domínio aplica o padrão
        }
        return Arrays.stream(csv.split(","))
                .map(String::strip)
                .filter(v -> !v.isEmpty())
                .flatMap(v -> {
                    try {
                        return java.util.stream.Stream.of(AutomationMetadata.valueOf(v));
                    } catch (IllegalArgumentException ex) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .toList();
    }
}
