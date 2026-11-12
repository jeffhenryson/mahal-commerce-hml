package com.cernecommerce.core.domain.model.assistente;

/**
 * Tokens de um turno, somados nas iterações. {@code entrada} é a entrada sem cache (no Claude, com a
 * escrita no cache), {@code cache} a lida do cache e {@code saida} inclui o raciocínio.
 */
public record UsoTokens(long entrada, long saida, long cache) {

    public static final UsoTokens ZERO = new UsoTokens(0, 0, 0);

    public UsoTokens mais(UsoTokens outro) {
        return new UsoTokens(entrada + outro.entrada, saida + outro.saida, cache + outro.cache);
    }

    /** O que conta no limite mensal: cache fica de fora (custa uma fração). */
    public long contaNoLimite() {
        return entrada + saida;
    }

    public boolean vazio() {
        return entrada == 0 && saida == 0 && cache == 0;
    }
}
