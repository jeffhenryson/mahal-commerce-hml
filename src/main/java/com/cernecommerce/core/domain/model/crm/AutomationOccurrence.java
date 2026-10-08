package com.cernecommerce.core.domain.model.crm;

import java.util.Map;

/**
 * Uma ocorrência que pode disparar automações: um cliente entrou num estágio
 * ({@link CampaignTrigger#ENTRADA_ESTAGIO}) ou aconteceu um {@link AutomationEvent}.
 *
 * <p>{@code key} identifica a ocorrência (ex.: {@code PEDIDO:123}) e, somada ao cliente, vira o
 * {@code event_key} do log — é o que impede a mesma ocorrência de disparar duas vezes a mesma
 * automação, mesmo que o evento chegue repetido. {@code contexto} traz dados do evento que não
 * cabem em cliente/pedido (valores do caixa, SKU sem estoque, vencimento do fiado) e vai no
 * payload como está.</p>
 */
public record AutomationOccurrence(
        CampaignTrigger gatilho,
        AutomationEvent evento,
        CustomerStage estagio,
        Long customerId,
        Long orderId,
        String key,
        Map<String, Object> contexto) {

    public AutomationOccurrence {
        if (gatilho != CampaignTrigger.EVENTO && gatilho != CampaignTrigger.ENTRADA_ESTAGIO) {
            throw new IllegalArgumentException("ocorrência automática é EVENTO ou ENTRADA_ESTAGIO");
        }
        if (gatilho == CampaignTrigger.EVENTO && evento == null) {
            throw new IllegalArgumentException("evento é obrigatório");
        }
        if (gatilho == CampaignTrigger.ENTRADA_ESTAGIO && (estagio == null || customerId == null)) {
            throw new IllegalArgumentException("entrada de estágio exige cliente e estágio");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key é obrigatória");
        }
        contexto = contexto == null ? Map.of() : Map.copyOf(contexto);
    }

    public static AutomationOccurrence event(AutomationEvent evento, Long customerId, Long orderId, String key,
            Map<String, Object> contexto) {
        return new AutomationOccurrence(CampaignTrigger.EVENTO, evento, null, customerId, orderId, key, contexto);
    }

    public static AutomationOccurrence stageEntered(Long customerId, CustomerStage estagio, String key) {
        return new AutomationOccurrence(CampaignTrigger.ENTRADA_ESTAGIO, null, estagio, customerId, null, key, null);
    }

    /** Nome que vai em {@code evento} no payload: o evento, ou o próprio gatilho na entrada de estágio. */
    public String eventName() {
        return evento != null ? evento.name() : gatilho.name();
    }

    /** Se a ocorrência precisa de um cliente para disparar. */
    public boolean requiresCustomer() {
        return evento == null || evento.hasCustomer();
    }
}
