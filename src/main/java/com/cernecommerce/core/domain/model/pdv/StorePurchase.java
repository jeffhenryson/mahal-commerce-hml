package com.cernecommerce.core.domain.model.pdv;

import java.time.Instant;

/**
 * PDV-F036 — a resposta a "o cliente comprou algo na loja?", uma por mesa. Ausente (nulo onde é
 * usada) é "não respondido", diferente de {@code bought = false}: a conversão só conta as respondidas.
 */
public record StorePurchase(boolean bought, String answeredBy, Instant answeredAt) {
}
