package com.cernecommerce.core.domain.model.crm;

/**
 * Evento que dispara uma automação com gatilho {@link CampaignTrigger#EVENTO} — mesmo catálogo do
 * {@code AutomacaoEvento} do frontend-admin ({@code crm.models.ts}).
 *
 * <p>Os eventos marcados como Fase 2 já são aceitos no cadastro, mas ainda não têm origem no
 * backend (dependem de job diário, data de nascimento ou da tabela de "avise-me"): uma automação
 * com eles fica salva e simplesmente não dispara até a origem existir.</p>
 */
public enum AutomationEvent {
    CLIENTE_CRIADO(true),
    /** Fase 2 — job diário + data de nascimento no cliente. */
    ANIVERSARIO_CLIENTE(true),
    /** Fase 2 — job diário (sem compra há N dias). */
    CLIENTE_INATIVO(true),
    /** Fase 2 — job diário antes da expiração do cashback. */
    CASHBACK_EXPIRANDO(true),
    PEDIDO_CRIADO(true),
    PEDIDO_CONCLUIDO(true),
    PEDIDO_CANCELADO(true),
    PAGAMENTO_APROVADO(true),
    PAGAMENTO_RECUSADO(true),
    /** Fase 2 — job horário sobre o carrinho da loja online. */
    CARRINHO_ABANDONADO(true),
    VENDA_PDV_CONCLUIDA(true),
    COMANDA_FECHADA(true),
    /** Destinatário é a loja: o payload vai sem {@code cliente} e o segmento é ignorado. */
    CAIXA_FECHADO(false),
    MARCADO_VENCENDO(true),
    MARCADO_VENCIDO(true),
    /** Destinatário é a loja: o payload vai sem {@code cliente} e o segmento é ignorado. */
    ESTOQUE_BAIXO(false),
    /** Fase 2 — tabela de "avise-me" da loja online. */
    PRODUTO_DE_VOLTA(true);

    private final boolean hasCustomer;

    AutomationEvent(boolean hasCustomer) {
        this.hasCustomer = hasCustomer;
    }

    /** Se o evento tem um cliente destinatário — {@code false} nos eventos da loja (caixa, estoque). */
    public boolean hasCustomer() {
        return hasCustomer;
    }
}
