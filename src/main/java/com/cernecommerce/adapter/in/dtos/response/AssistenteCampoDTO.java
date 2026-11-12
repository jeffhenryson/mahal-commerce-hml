package com.cernecommerce.adapter.in.dtos.response;

/** Campo do cartão de confirmação de uma ação. */
public record AssistenteCampoDTO(String rotulo, String valor, boolean sensivel) {

    public static AssistenteCampoDTO de(String rotulo, String valor) {
        return new AssistenteCampoDTO(rotulo, valor, false);
    }
}
