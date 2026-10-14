package com.cernecommerce.core.domain.exception.pdv;

/**
 * Sessão com essência só em texto livre quando a casa exige o sabor do catálogo
 * ({@code pdv.sessao.essencia-livre-permitida=false}). Irmã de {@link SessionEssenceRequiredException}:
 * aquela é "faltou a essência", esta é "veio, mas sem SKU", e pedem correções diferentes na tela.
 *
 * <p>Sem SKU a sessão não baixa a lata do Salão, e o rendimento da lata deixa de bater com o que
 * saiu nas mesas. O histórico em texto continua legível: a trava vale só para lançamento novo.</p>
 */
public class SessionEssenceSkuRequiredException extends RuntimeException {
    public SessionEssenceSkuRequiredException() {
        super("Escolha a essência do catálogo: sessão com sabor só em texto não é mais aceita");
    }
}
