package com.cernecommerce.core.domain.model.config;

/**
 * Resultado do botão "Testar" de uma integração. Recusa do provedor não é erro HTTP: volta como
 * {@code success = false} com a mensagem dele em {@code error}.
 */
public record IntegrationTestResult(boolean success, String error, String detail) {

    public static IntegrationTestResult ok(String detail) {
        return new IntegrationTestResult(true, null, detail);
    }

    public static IntegrationTestResult failed(String error, String detail) {
        return new IntegrationTestResult(false, error, detail);
    }
}
