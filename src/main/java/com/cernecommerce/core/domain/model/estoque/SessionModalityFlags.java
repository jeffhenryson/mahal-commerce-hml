package com.cernecommerce.core.domain.model.estoque;

/**
 * PDV-F061 — em quais modalidades novas da sessão de mesa uma essência pode ser vendida. As deluxe
 * ficam de fora simplesmente por não serem marcadas.
 *
 * @param compraEssencia o cliente compra o pacote fechado e a sessão sai com desconto
 * @param itensPessoais  a essência entra na sessão de quem trouxe o próprio narguilé
 * @param promoEssencia  PDV-F062 — a essência entra na "Promo de essência" (preço fechado, lata aberta)
 */
public record SessionModalityFlags(boolean compraEssencia, boolean itensPessoais, boolean promoEssencia) {

    public static final SessionModalityFlags NONE = new SessionModalityFlags(false, false, false);

    /**
     * Padrão de produto novo (V192/V193/V207): na compra de essência, nos itens pessoais e na promo
     * todas valem, e a marcação exclui (as deluxe).
     */
    public static final SessionModalityFlags DEFAULT = new SessionModalityFlags(true, true, true);
}
