package com.cernecommerce.core.domain.model.notification;

import java.time.Instant;

/**
 * E-mail que o provedor recusou, guardado para a equipe técnica ver e reenviar.
 *
 * @param lastResendStatus {@code SENT} ou {@code FAILED}; nulo antes do primeiro reenvio
 */
public record FailedEmail(Long id, String type, String recipient, String subject, String html, String error,
        Instant createdAt, int resendCount, Instant lastResendAt, String lastResendStatus, String lastResendError) {

    public static final String RESEND_SENT = "SENT";
    public static final String RESEND_FAILED = "FAILED";

    public static FailedEmail of(String type, String recipient, String subject, String html, String error,
            Instant createdAt) {
        return new FailedEmail(null, type, recipient, subject, html, error, createdAt, 0, null, null, null);
    }

    public FailedEmail afterResend(Instant at, String status, String resendError) {
        return new FailedEmail(id, type, recipient, subject, html, error, createdAt, resendCount + 1, at, status,
                resendError);
    }
}
