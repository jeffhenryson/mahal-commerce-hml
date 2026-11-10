package com.cernecommerce.infra.audit;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);
    private static final int USER_AGENT_MAX = 512;

    private final AuditPersistenceService persistenceService;
    private final SystemConfigPort systemConfig;

    public AuditEventListener(AuditPersistenceService persistenceService, SystemConfigPort systemConfig) {
        this.persistenceService = persistenceService;
        this.systemConfig = systemConfig;
    }

    @EventListener
    public void onAuditEvent(AuditEvent event) {
        log.info("audit type={} username={} details={}", event.type(), event.username(), event.details());
        if (!systemConfig.getBoolean("module.audit-logs.enabled", true)) {
            return;
        }
        // IP deve ser resolvido na thread do request — antes de passar para execução assíncrona.
        // Mesmo motivo para o User-Agent.
        HttpServletRequest request = currentRequest();
        String ip = request == null ? null : request.getRemoteAddr();
        String userAgent = userAgent(request);
        AuditOrigemContext.Origem origem = AuditOrigemContext.atual();
        if (origem != null) {
            // Ação feita pelo assistente de IA: marca a origem e, fora do request, usa o IP/UA dele.
            event = comOrigem(event, origem.nome());
            if (ip == null) {
                ip = origem.ip();
            }
            if (userAgent == null) {
                userAgent = origem.userAgent();
            }
        }
        persistenceService.saveAsync(event, ip, userAgent);
    }

    static AuditEvent comOrigem(AuditEvent event, String origem) {
        Map<String, Object> details = new HashMap<>(event.details());
        details.put("origem", origem);
        return new AuditEvent(event.type(), event.username(), event.timestamp(), Map.copyOf(details));
    }

    private static HttpServletRequest currentRequest() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                return sra.getRequest();
            }
        } catch (Exception ignored) {}
        return null;
    }

    static String userAgent(HttpServletRequest request) {
        if (request == null) return null;
        String ua = request.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) return null;
        return ua.length() > USER_AGENT_MAX ? ua.substring(0, USER_AGENT_MAX) : ua;
    }
}
