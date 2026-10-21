package com.cernecommerce.core.domain.model.pdv;

/**
 * Quanto de cada sabor vai na sessão mesclada (PDV-F051): o 1º sabor é o principal. Não muda o
 * preço — a mescla cobra a faixa mais cara, qualquer que seja a proporção.
 */
public enum ProporcaoMistura {
    MEIO_A_MEIO(50),
    SETENTA_TRINTA(70),
    NOVENTA_DEZ(90);

    private final int principal;

    ProporcaoMistura(int principal) {
        this.principal = principal;
    }

    /** Percentual do 1º sabor. */
    public int principal() {
        return principal;
    }

    /** "Uva 70% + Menta 30%" — o que o preparador lê na linha da sessão. */
    public String rotulo(String sabor1, String sabor2) {
        return sabor1 + " " + principal + "% + " + sabor2 + " " + (100 - principal) + "%";
    }
}
