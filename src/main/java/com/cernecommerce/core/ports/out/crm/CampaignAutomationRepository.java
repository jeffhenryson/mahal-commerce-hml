package com.cernecommerce.core.ports.out.crm;

import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de automações de campanha do CRM.
 */
public interface CampaignAutomationRepository {

    Optional<CampaignAutomation> findById(Long id);

    List<CampaignAutomation> findAll();

    /** Automações ativas com gatilho {@code ENTRADA_ESTAGIO}. */
    List<CampaignAutomation> findActiveStageEntry();

    /** Automações ativas com gatilho {@code EVENTO} para {@code evento}. */
    List<CampaignAutomation> findActiveByEvent(AutomationEvent evento);

    CampaignAutomation save(CampaignAutomation automation);

    void deleteById(Long id);
}
