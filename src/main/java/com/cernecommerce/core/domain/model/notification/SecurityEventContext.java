package com.cernecommerce.core.domain.model.notification;

import java.time.Instant;

/**
 * Quando e de onde partiu o evento de segurança, para o alerta dizer "foi às 14:32, deste IP, deste
 * navegador" — sem isso o usuário não tem como saber se foi ele. IP e user agent só existem quando o
 * evento nasce numa requisição HTTP; fora dela são {@code null} e o e-mail omite a linha.
 *
 * <p>{@code customer} diz se quem recebe é cliente do app: a recuperação dele fica lá, não no painel.</p>
 */
public record SecurityEventContext(Instant occurredAt, String ip, String userAgent, boolean customer) {

    public SecurityEventContext(Instant occurredAt, String ip, String userAgent) {
        this(occurredAt, ip, userAgent, false);
    }

    public SecurityEventContext forCustomer() {
        return new SecurityEventContext(occurredAt, ip, userAgent, true);
    }

    public static SecurityEventContext at(Instant occurredAt) {
        return new SecurityEventContext(occurredAt, null, null);
    }
}
