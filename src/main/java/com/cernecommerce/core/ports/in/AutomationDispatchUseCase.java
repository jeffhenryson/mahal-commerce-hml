package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.crm.AutomationOccurrence;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.domain.model.crm.WebhookTestResult;

import java.util.List;

/**
 * Ponto único de entrega das automações — disparo manual, teste e os gatilhos automáticos usam a
 * mesma montagem de mensagem/payload e a mesma entrega por destino.
 */
public interface AutomationDispatchUseCase {

    /** Disparo manual: um envio (e uma entrada de log) por cliente do {@code segmentoAlvo}. */
    List<CampaignLogEntry> dispatchManual(CampaignAutomation automation);

    /** Envio de teste com um cliente fictício — não grava log. */
    WebhookTestResult test(CampaignAutomation automation);

    /**
     * Dispara as automações ativas que casam com a ocorrência. Idempotente por automação +
     * ocorrência + cliente; falha de uma automação não impede as demais.
     */
    void dispatch(AutomationOccurrence occurrence);
}
