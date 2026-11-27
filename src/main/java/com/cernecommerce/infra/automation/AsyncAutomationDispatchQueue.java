package com.cernecommerce.infra.automation;

import com.cernecommerce.core.domain.model.crm.AutomationOccurrence;
import com.cernecommerce.core.ports.in.AutomationDispatchUseCase;
import com.cernecommerce.core.ports.out.crm.AutomationDispatchQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link AutomationDispatchQueue} no {@code taskExecutor}, como o {@link AutomationEventListener}. Uma falha
 * numa ocorrência não impede as outras.
 */
@Component
public class AsyncAutomationDispatchQueue implements AutomationDispatchQueue {

    private static final Logger log = LoggerFactory.getLogger(AsyncAutomationDispatchQueue.class);

    private final AutomationDispatchUseCase dispatcher;

    public AsyncAutomationDispatchQueue(AutomationDispatchUseCase dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    @Async("taskExecutor")
    public void enqueue(List<AutomationOccurrence> occurrences) {
        for (AutomationOccurrence occurrence : occurrences) {
            try {
                dispatcher.dispatch(occurrence);
            } catch (Exception ex) {
                log.error("automation.queue.failed event={} key={} error={}", occurrence.eventName(),
                        occurrence.key(), ex.getMessage(), ex);
            }
        }
    }
}
