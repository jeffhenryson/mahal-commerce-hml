package com.cernecommerce.core.domain.model.estoque;

/**
 * PDV-F061 — em quais modalidades novas da sessão de mesa uma essência pode ser vendida. As deluxe
 * ficam de fora simplesmente por não serem marcadas.
 *
 * @param compraEssencia o cliente compra o pacote fechado e a sessão sai com desconto
 * @param itensPessoais  a essência entra na sessão de quem trouxe o próprio narguilé
 */
public record SessionModalityFlags(boolean compraEssencia, boolean itensPessoais) {

    public static final SessionModalityFlags NONE = new SessionModalityFlags(false, false);

    /**
     * Padrão de produto novo (V192/V193): nas duas modalidades todas valem, e a marcação exclui (as
     * deluxe).
     */
    public static final SessionModalityFlags DEFAULT = new SessionModalityFlags(true, true);
}
