package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.AuditLogEntry;
import com.cernecommerce.core.domain.model.config.IntegrationHistoryEntry;
import com.cernecommerce.core.ports.in.IntegrationHistoryUseCase;
import com.cernecommerce.core.ports.out.audit.AuditLogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Lê o histórico das integrações do {@code audit_logs}: cada PUT publica
 * {@code INTEGRATION_UPDATED} com {@code integration=<tipo>}. Quando o evento traz
 * {@code historyAction}/{@code fields} usa os dois; senão deduz pelos marcadores {@code *Changed}.
 */
public class IntegrationHistoryService implements IntegrationHistoryUseCase {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_LIMIT = 50;

    private final AuditLogRepository auditLogRepository;

    public IntegrationHistoryService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public List<IntegrationHistoryEntry> history(String integration, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return auditLogRepository.findRecent(EventType.INTEGRATION_UPDATED.name(), "integration:" + integration,
                safeLimit).stream().map(IntegrationHistoryService::toEntry).toList();
    }

    private static IntegrationHistoryEntry toEntry(AuditLogEntry entry) {
        Map<String, Object> details = parse(entry.details());
        Object action = details.get("historyAction");
        Object fields = details.get("fields");
        List<String> fieldNames = fields instanceof List<?> list ? list.stream().map(String::valueOf).toList() : null;
        return new IntegrationHistoryEntry(entry.timestamp(), entry.username(),
                action == null ? deduce(details) : String.valueOf(action), fieldNames);
    }

    private static String deduce(Map<String, Object> details) {
        boolean secretChanged = details.entrySet().stream()
                .anyMatch(e -> e.getKey().endsWith("Changed") && Boolean.TRUE.equals(e.getValue()));
        return secretChanged ? "SECRET_SET" : "UPDATED";
    }

    private static Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() { });
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
