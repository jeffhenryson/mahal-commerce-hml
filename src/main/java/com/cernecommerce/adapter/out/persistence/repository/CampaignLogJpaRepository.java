package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CampaignLogEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaignLogJpaRepository extends JpaRepository<CampaignLogEntryEntity, Long> {

    List<CampaignLogEntryEntity> findByAutomationIdOrderByDisparadoEmDesc(Long automationId);

    boolean existsByAutomationIdAndEventKey(Long automationId, String eventKey);
}
