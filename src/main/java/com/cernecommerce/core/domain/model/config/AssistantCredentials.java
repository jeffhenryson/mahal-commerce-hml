package com.cernecommerce.core.domain.model.config;

/** Provedor, chave decifrada e modelo. Nunca vai para log nem para resposta HTTP. */
public record AssistantCredentials(ProvedorIa provedor, String apiKey, String model) {

    @Override
    public String toString() {
        return "AssistantCredentials[provedor=" + provedor + ", apiKey=***, model=" + model + "]";
    }
}
