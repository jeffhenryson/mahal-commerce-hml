package com.cernecommerce.adapter.in.dtos.response;

/** Coluna de tabela. {@code sensivel}: dinheiro ou quantidade de venda (some com o olho fechado). */
public record AssistenteColunaDTO(String rotulo, boolean sensivel) {

    public static AssistenteColunaDTO de(String rotulo) {
        return new AssistenteColunaDTO(rotulo, false);
    }

    public static AssistenteColunaDTO sensivel(String rotulo) {
        return new AssistenteColunaDTO(rotulo, true);
    }
}
