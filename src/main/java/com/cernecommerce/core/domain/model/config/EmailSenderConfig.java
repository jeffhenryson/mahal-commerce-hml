package com.cernecommerce.core.domain.model.config;

/**
 * Credenciais já decifradas para enviar e-mail pelo provedor da loja. Nunca sai do backend: é o
 * que o adapter de envio recebe, não o que a API devolve.
 */
public record EmailSenderConfig(EmailProvider provider, String apiKey, String fromEmail, String fromName,
        String replyTo) {

    /** {@code "Nome <email>"} quando há nome; senão só o e-mail — formato aceito pelo Resend. */
    public String formattedFrom() {
        if (fromName == null || fromName.isBlank()) {
            return fromEmail;
        }
        return fromName.replace("\"", "").replace("<", "").replace(">", "") + " <" + fromEmail + ">";
    }

    @Override
    public String toString() {
        // Sem a chave: o record pode acabar num log.
        return "EmailSenderConfig[provider=" + provider + ", from=" + formattedFrom() + ", replyTo=" + replyTo + "]";
    }
}
