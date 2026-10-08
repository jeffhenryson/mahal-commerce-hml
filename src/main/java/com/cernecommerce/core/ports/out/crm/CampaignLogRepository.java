package com.cernecommerce.core.ports.out.crm;

import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para o log de disparos de automações de campanha do CRM.
 */
public interface CampaignLogRepository {

    CampaignLogEntry save(CampaignLogEntry entry);

    /**
     * Grava a reserva de disparo de uma ocorrência ({@link CampaignLogEntry#claim}) em transação
     * própria. Vazio quando {@code (automationId, eventKey)} já existe — a ocorrência já foi
     * disparada (ou está sendo) e não deve sair de novo.
     */
    Optional<CampaignLogEntry> claim(CampaignLogEntry entry);

    /** Lista as entradas de log de uma automação, mais recentes primeiro. */
    List<CampaignLogEntry> findByAutomationId(Long automationId);
}
