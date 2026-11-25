package com.cernecommerce.core.domain.model.pdv;

/**
 * PDV-F061 — como a sessão de mesa foi vendida.
 *
 * <ul>
 *   <li>{@link #PADRAO}: itens da casa, preço da faixa da essência.</li>
 *   <li>{@link #COMPRA_ESSENCIA}: itens da casa, mas a essência são pacotes comprados na loja (todos
 *   cobrados); a sessão sai com desconto.</li>
 *   <li>{@link #ITENS_PESSOAIS}: o cliente traz o narguilé; preço fixo, sem utensílio da casa, e 1
 *   pacote do sabor principal vem incluso; o resto dos pacotes é cobrado.</li>
 *   <li>{@link #PROMO_ESSENCIA} (PDV-F062): itens da casa e preço fechado
 *   ({@code SessionSettings#promoEssenciaPreco}), qualquer que seja a faixa, mas só com lata já aberta
 *   no Salão. Mescla vale; duplo não. Vaso grande, jumbo e adicionais somam por cima.</li>
 * </ul>
 *
 * <p>Na compra de essência e nos itens pessoais a sessão não gasta lata: a essência sai da loja como
 * pacote. A promo gasta 1 uso da lata aberta, como a padrão.</p>
 */
public enum SessionModality {
    PADRAO,
    COMPRA_ESSENCIA,
    ITENS_PESSOAIS,
    PROMO_ESSENCIA;

    /** O 2º rosh do duplo só existe na sessão padrão. */
    public boolean allowsDuplo() {
        return this == PADRAO;
    }

    /** O narguilé do cliente não troca de vaso. */
    public boolean allowsVasoGrande() {
        return this != ITENS_PESSOAIS;
    }

    /** Vaso e inclusos da casa ficam presos à sessão. */
    public boolean occupiesAssets() {
        return this != ITENS_PESSOAIS;
    }

    /** A essência são pacotes da loja, e não uso da lata da casa. */
    public boolean usesStorePackages() {
        return this == COMPRA_ESSENCIA || this == ITENS_PESSOAIS;
    }

    /** 1 pacote do sabor principal vem no kit, a R$ 0. */
    public boolean includesMainPackage() {
        return this == ITENS_PESSOAIS;
    }

    /** SKU da linha da sessão de itens pessoais sem faixa: o preço não depende dela. */
    public static final String ITENS_PESSOAIS_SKU = "SESS-PESSOAL";

    /** PDV-F062 — SKU da linha da promo cujo sabor não tem faixa: o preço também não depende dela. */
    public static final String PROMO_ESSENCIA_SKU = "SESS-PROMO";
}
