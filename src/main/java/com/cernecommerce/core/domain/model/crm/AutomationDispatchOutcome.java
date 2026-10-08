package com.cernecommerce.core.domain.model.crm;

import java.util.Map;

/** Resultado da entrega de um disparo: status do log, erro, status HTTP (quando houver) e o payload. */
public record AutomationDispatchOutcome(CampaignDispatchStatus status, String error, Integer statusCode,
        Map<String, Object> payload) {

    public static AutomationDispatchOutcome failed(String error, Integer statusCode, Map<String, Object> payload) {
        return new AutomationDispatchOutcome(CampaignDispatchStatus.FALHA, error, statusCode, payload);
    }
}
