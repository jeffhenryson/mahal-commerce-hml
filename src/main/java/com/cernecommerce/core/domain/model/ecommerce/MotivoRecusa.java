package com.cernecommerce.core.domain.model.ecommerce;

import com.cernecommerce.core.domain.exception.ecommerce.RecusaInvalidaException;

/**
 * Por que a loja recusou um pedido do app (ECM-F012). O código alimenta o indicador de recusas; o
 * rótulo, somado à nota do operador, é o que o cliente lê no motivo do cancelamento.
 */
public enum MotivoRecusa {
    SEM_ESTOQUE("Sem estoque"),
    FORA_DO_HORARIO("Fora do horário de atendimento"),
    FORA_DA_AREA("Endereço fora da área de entrega"),
    LOJA_SOBRECARREGADA("Loja sem condições de atender agora"),
    OUTRO("Outro motivo");

    /** Mesmo teto do motivo da solicitação do QR (V167). */
    public static final int NOTA_MAX = 200;

    private final String rotulo;

    MotivoRecusa(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }

    /**
     * Texto do motivo: o rótulo, mais a nota quando houver. Em {@link #OUTRO} a nota é o motivo, e é
     * obrigatória.
     */
    public String descrever(String nota) {
        String limpa = nota == null || nota.isBlank() ? null : nota.strip();
        if (limpa != null && limpa.length() > NOTA_MAX) {
            throw new RecusaInvalidaException("a nota da recusa tem no máximo " + NOTA_MAX + " caracteres");
        }
        if (this == OUTRO) {
            if (limpa == null) {
                throw new RecusaInvalidaException("informe a nota quando o motivo for OUTRO");
            }
            return limpa;
        }
        return limpa == null ? rotulo : rotulo + ": " + limpa;
    }
}
