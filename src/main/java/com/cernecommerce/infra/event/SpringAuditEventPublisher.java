package com.cernecommerce.infra.event;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.ports.out.event.AuditEventPublisherPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** {@link AuditEventPublisherPort} sobre o barramento de eventos do Spring — o mesmo dos controllers. */
@Component
class SpringAuditEventPublisher implements AuditEventPublisherPort {

    private final ApplicationEventPublisher publisher;

    SpringAuditEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(AuditEvent event) {
        publisher.publishEvent(event);
    }
}
