package com.cernecommerce.core.domain.model.crm;

import java.time.Instant;

/**
 * Entrada do log de disparo de uma automação. {@code erroDetalhe} só é preenchido quando
 * {@code status == FALHA}. {@code convertidoEm} fica sempre {@code null} nesta versão.
 *
 * <p>{@code customerId} é nulo nos eventos da loja (caixa fechado, estoque baixo), que não têm
 * cliente destinatário. {@code eventKey} identifica a ocorrência que disparou uma automação
 * automática — {@code (automationId, eventKey)} é único, e é isso que impede o mesmo evento de
 * disparar duas vezes. Disparo manual não tem chave.</p>
 */
public record CampaignLogEntry(
    Long id,
    Long automationId,
    Long customerId,
    CampaignDispatchStatus status,
    Instant disparadoEm,
    Instant convertidoEm,
    String erroDetalhe,
    String eventKey
) {

    public CampaignLogEntry {
        if (automationId == null) {
            throw new IllegalArgumentException("automationId é obrigatório");
        }
        if (customerId == null && eventKey == null) {
            throw new IllegalArgumentException("customerId é obrigatório fora dos disparos por evento");
        }
    }

    /** Cria uma nova entrada de log (sem id, status PENDENTE_INTEGRACAO, disparadoEm agora, sem conversão). */
    public static CampaignLogEntry create(Long automationId, Long customerId) {
        return new CampaignLogEntry(null, automationId, customerId, CampaignDispatchStatus.PENDENTE_INTEGRACAO,
                Instant.now(), null, null, null);
    }

    /** Cria uma entrada de log para um disparo real bem-sucedido (HTTP 2xx do webhook). */
    public static CampaignLogEntry enviado(Long automationId, Long customerId) {
        return new CampaignLogEntry(null, automationId, customerId, CampaignDispatchStatus.ENVIADO,
                Instant.now(), null, null, null);
    }

    /** Cria uma entrada de log para um disparo real que falhou — erro de rede, timeout ou HTTP não-2xx. */
    public static CampaignLogEntry falha(Long automationId, Long customerId, String erroDetalhe) {
        return new CampaignLogEntry(null, automationId, customerId, CampaignDispatchStatus.FALHA,
                Instant.now(), null, erroDetalhe, null);
    }

    /**
     * Reserva o disparo de uma ocorrência (gatilho automático), antes do envio. Gravar esta entrada
     * é o que garante "no máximo um disparo por automação + ocorrência".
     */
    public static CampaignLogEntry claim(Long automationId, Long customerId, String eventKey) {
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("eventKey é obrigatório no disparo por evento");
        }
        return new CampaignLogEntry(null, automationId, customerId, CampaignDispatchStatus.PENDENTE_INTEGRACAO,
                Instant.now(), null, null, eventKey);
    }

    /** Reconstitui uma entrada de log a partir de persistência (formato anterior às chaves de evento). */
    public static CampaignLogEntry of(Long id, Long automationId, Long customerId, CampaignDispatchStatus status,
            Instant disparadoEm, Instant convertidoEm, String erroDetalhe) {
        return new CampaignLogEntry(id, automationId, customerId, status, disparadoEm, convertidoEm, erroDetalhe,
                null);
    }

    /** Cópia com o resultado do envio (ENVIADO, ou FALHA com o motivo). */
    public CampaignLogEntry withResult(CampaignDispatchStatus novoStatus, String novoErroDetalhe) {
        return new CampaignLogEntry(id, automationId, customerId, novoStatus, disparadoEm, convertidoEm,
                novoErroDetalhe, eventKey);
    }
}
