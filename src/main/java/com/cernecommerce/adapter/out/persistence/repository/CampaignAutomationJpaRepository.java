package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CampaignAutomationEntity;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignAutomationJpaRepository extends JpaRepository<CampaignAutomationEntity, Long> {

    List<CampaignAutomationEntity> findByAtivaTrueAndGatilho(CampaignTrigger gatilho);

    List<CampaignAutomationEntity> findByAtivaTrueAndGatilhoAndEvento(CampaignTrigger gatilho, AutomationEvent evento);
}
