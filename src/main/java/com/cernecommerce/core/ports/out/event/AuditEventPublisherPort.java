package com.cernecommerce.core.ports.out.event;

import com.cernecommerce.core.domain.event.AuditEvent;

/**
 * Publica um {@link AuditEvent} a partir do core — para os fatos que nascem dentro de um serviço
 * (não num controller). Quem chama garante que é depois do commit (ex.: callback do
 * {@code AfterCommitExecutor}).
 */
public interface AuditEventPublisherPort {

    void publish(AuditEvent event);
}
