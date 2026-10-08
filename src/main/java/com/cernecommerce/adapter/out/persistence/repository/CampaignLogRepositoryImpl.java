package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CampaignLogEntryEntity;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.ports.out.crm.CampaignLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class CampaignLogRepositoryImpl implements CampaignLogRepository {

    private final CampaignLogJpaRepository campaignLogJpaRepository;

    public CampaignLogRepositoryImpl(CampaignLogJpaRepository campaignLogJpaRepository) {
        this.campaignLogJpaRepository = campaignLogJpaRepository;
    }

    @Override
    public CampaignLogEntry save(CampaignLogEntry entry) {
        return toDomain(campaignLogJpaRepository.save(toEntity(entry)));
    }

    /**
     * Transação própria: a violação da chave única invalida a transação em que acontece (no
     * PostgreSQL, todo comando seguinte falharia), e quem reserva não pode ser contaminado.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CampaignLogEntry> claim(CampaignLogEntry entry) {
        if (campaignLogJpaRepository.existsByAutomationIdAndEventKey(entry.automationId(), entry.eventKey())) {
            return Optional.empty();
        }
        try {
            return Optional.of(toDomain(campaignLogJpaRepository.saveAndFlush(toEntity(entry))));
        } catch (DataIntegrityViolationException ex) {
            // Corrida entre dois disparos da mesma ocorrência: o outro venceu.
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignLogEntry> findByAutomationId(Long automationId) {
        return campaignLogJpaRepository.findByAutomationIdOrderByDisparadoEmDesc(automationId).stream()
                .map(this::toDomain)
                .toList();
    }

    private static CampaignLogEntryEntity toEntity(CampaignLogEntry entry) {
        CampaignLogEntryEntity entity = new CampaignLogEntryEntity();
        entity.setId(entry.id());
        entity.setAutomationId(entry.automationId());
        entity.setCustomerId(entry.customerId());
        entity.setStatus(entry.status());
        entity.setDisparadoEm(entry.disparadoEm());
        entity.setConvertidoEm(entry.convertidoEm());
        entity.setErroDetalhe(entry.erroDetalhe());
        entity.setEventKey(entry.eventKey());
        return entity;
    }

    private CampaignLogEntry toDomain(CampaignLogEntryEntity e) {
        return new CampaignLogEntry(e.getId(), e.getAutomationId(), e.getCustomerId(), e.getStatus(),
                e.getDisparadoEm(), e.getConvertidoEm(), e.getErroDetalhe(), e.getEventKey());
    }
}
