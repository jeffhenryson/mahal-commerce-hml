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
 * </ul>
 *
 * <p>Nas duas modalidades novas a sessão não gasta lata: a essência sai da loja como pacote.</p>
 */
public enum SessionModality {
    PADRAO,
    COMPRA_ESSENCIA,
    ITENS_PESSOAIS;

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
        return this != PADRAO;
    }

    /** 1 pacote do sabor principal vem no kit, a R$ 0. */
    public boolean includesMainPackage() {
        return this == ITENS_PESSOAIS;
    }

    /** SKU da linha da sessão de itens pessoais sem faixa: o preço não depende dela. */
    public static final String ITENS_PESSOAIS_SKU = "SESS-PESSOAL";
}
