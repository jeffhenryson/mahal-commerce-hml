package com.cernecommerce.core.domain.model.crm;

/**
 * Gatilho de uma automação de campanha.
 * <ul>
 *   <li>{@link #MANUAL}: só pelo botão "Disparar" ({@code POST /crm/automacoes/{id}/disparar}), para
 *       todos os clientes do {@code segmentoAlvo}.</li>
 *   <li>{@link #ENTRADA_ESTAGIO}: quando um cliente muda para o {@code segmentoAlvo}
 *       ({@code AuditEvent CUSTOMER_STAGE_CHANGED}).</li>
 *   <li>{@link #EVENTO}: quando acontece o {@link AutomationEvent} escolhido (venda, pedido, caixa...).</li>
 * </ul>
 */
public enum CampaignTrigger {
    MANUAL,
    ENTRADA_ESTAGIO,
    EVENTO
}
