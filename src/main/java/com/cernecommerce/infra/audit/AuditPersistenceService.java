package com.cernecommerce.infra.audit;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.ports.out.audit.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Persiste eventos de auditoria de forma assíncrona para não bloquear a thread do request.
 * O IP é recebido como parâmetro porque o RequestContextHolder não está disponível
 * em threads do pool de tarefas assíncronas.
 */
@Service
public class AuditPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(AuditPersistenceService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditPersistenceService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async
    public void saveAsync(AuditEvent event, String ipAddress) {
        saveAsync(event, ipAddress, null);
    }

    @Async
    public void saveAsync(AuditEvent event, String ipAddress, String userAgent) {
        try {
            auditLogRepository.save(event, ipAddress, userAgent);
        } catch (Exception e) {
            log.error("audit.persist.failed type={} username={}", event.type(), event.username(), e);
        }
    }
}
