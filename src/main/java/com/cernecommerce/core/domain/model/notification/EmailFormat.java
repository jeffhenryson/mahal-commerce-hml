package com.cernecommerce.core.domain.model.notification;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatação pt-BR para o texto dos e-mails: dinheiro em R$ e horário de Brasília. */
public final class EmailFormat {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", PT_BR);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", PT_BR).withZone(ZONE);

    private EmailFormat() {
    }

    public static String money(Object value) {
        BigDecimal amount = toBigDecimal(value);
        return amount == null ? "—" : NumberFormat.getCurrencyInstance(PT_BR).format(amount);
    }

    public static String dateTime(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant);
    }

    public static String date(LocalDate date) {
        return date == null ? "—" : DATE.format(date);
    }

    /** Aceita BigDecimal, Number ou String — os {@code details} dos AuditEvents chegam nos três jeitos. */
    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            return new BigDecimal(value.toString().strip());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
