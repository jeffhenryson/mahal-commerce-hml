package com.cernecommerce.core.ports.out.audit;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.model.AuditLogEntry;
import com.cernecommerce.core.domain.model.PageResult;

import java.time.Instant;
import java.util.Set;

public interface AuditLogRepository {
    void save(AuditEvent event, String ipAddress);
    PageResult<AuditLogEntry> findFiltered(String username, String action, Instant from, Instant to, int page, int size, Set<String> excludeActions);
    /** Remove entradas mais antigas que {@code cutoff}. Chamado pelo scheduler de retenção. */
    long deleteOlderThan(Instant cutoff);
    /** Quantas entradas {@code action} sobre {@code target} desde {@code since} — base de travas por contagem. */
    long countSince(String action, String target, Instant since);
}
