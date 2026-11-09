package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.AuditLogEntry;
import java.time.Instant;

public record AuditLogResponseDTO(
    Long id,
    String who,
    String action,
    String target,
    String details,
    String ipAddress,
    Instant timestamp,
    String userAgent
) {
    public static AuditLogResponseDTO from(AuditLogEntry e) {
        return new AuditLogResponseDTO(e.id(), e.username(), e.action(), e.target(), e.details(), e.ipAddress(), e.timestamp(), e.userAgent());
    }
}
