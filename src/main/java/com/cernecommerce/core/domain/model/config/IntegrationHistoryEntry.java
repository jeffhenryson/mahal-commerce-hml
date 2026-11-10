package com.cernecommerce.core.domain.model.config;

import java.time.Instant;
import java.util.List;

/**
 * Uma alteração numa integração da loja, lida do audit log. Nunca traz valores, só os nomes dos
 * campos alterados. {@code action}: UPDATED, SECRET_SET, SECRET_REMOVED, ENABLED ou DISABLED.
 */
public record IntegrationHistoryEntry(Instant at, String user, String action, List<String> fields) {
}
