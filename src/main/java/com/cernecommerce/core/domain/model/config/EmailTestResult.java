package com.cernecommerce.core.domain.model.config;

/** Resultado do envio de um e-mail de exemplo: {@code error} traz a resposta do provedor quando falha. */
public record EmailTestResult(EmailSample sample, boolean success, String error) {

    public static EmailTestResult ok(EmailSample sample) {
        return new EmailTestResult(sample, true, null);
    }

    public static EmailTestResult failed(EmailSample sample, String error) {
        return new EmailTestResult(sample, false, error);
    }
}
