package com.cernecommerce.core.ports.out.audit;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.model.AuditLogEntry;
import com.cernecommerce.core.domain.model.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public interface AuditLogRepository {
    default void save(AuditEvent event, String ipAddress) {
        save(event, ipAddress, null);
    }
    void save(AuditEvent event, String ipAddress, String userAgent);
    PageResult<AuditLogEntry> findFiltered(String username, String action, Instant from, Instant to, int page, int size, Set<String> excludeActions);
    /** Remove entradas mais antigas que {@code cutoff}. Chamado pelo scheduler de retenção. */
    long deleteOlderThan(Instant cutoff);
    /** Quantas entradas {@code action} sobre {@code target} desde {@code since} — base de travas por contagem. */
    long countSince(String action, String target, Instant since);
    /** As {@code limit} entradas mais recentes de {@code action} sobre {@code target}. */
    List<AuditLogEntry> findRecent(String action, String target, int limit);
}
