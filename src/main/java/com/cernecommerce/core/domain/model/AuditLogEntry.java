package com.cernecommerce.core.domain.model;

import java.time.Instant;

public record AuditLogEntry(
    Long id,
    String username,
    String action,
    String target,
    String details,
    String ipAddress,
    Instant timestamp,
    String userAgent
) {
    /** Entrada sem user agent — eventos anteriores à V190 ou gravados fora de um request. */
    public AuditLogEntry(Long id, String username, String action, String target, String details,
                         String ipAddress, Instant timestamp) {
        this(id, username, action, target, details, ipAddress, timestamp, null);
    }
}
