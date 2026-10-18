package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.in.ComandaUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleComandaSweepServiceTest {

    @Mock
    ComandaUseCase comandaUseCase;

    @Test
    void sweep_resolvesEachCandidateAndThenAlerts_withConfiguredWindowAndBatchSize() {
        StaleComandaSweepService service = new StaleComandaSweepService(comandaUseCase, 12, 200);
        when(comandaUseCase.findStaleResolvableComandaIds(12, 200)).thenReturn(List.of(1L, 2L, 3L));
        when(comandaUseCase.resolveStaleComanda(1L)).thenReturn(ComandaUseCase.StaleComandaOutcome.CANCELLED);
        when(comandaUseCase.resolveStaleComanda(2L)).thenReturn(ComandaUseCase.StaleComandaOutcome.FINISHED);
        when(comandaUseCase.resolveStaleComanda(3L)).thenReturn(ComandaUseCase.StaleComandaOutcome.SKIPPED);
        when(comandaUseCase.alertStaleComandasWithConsumption(12, 200)).thenReturn(4);

        ComandaUseCase.StaleComandaSweepResult result = service.runOnce();

        assertThat(result).isEqualTo(new ComandaUseCase.StaleComandaSweepResult(1, 1, 4));
        // PDV-C054 — o alerta vem depois de resolver: nunca dentro da transação de uma mesa travada.
        InOrder order = inOrder(comandaUseCase);
        order.verify(comandaUseCase).resolveStaleComanda(3L);
        order.verify(comandaUseCase).alertStaleComandasWithConsumption(12, 200);
    }

    /** PDV-C054 — uma mesa que falha não derruba as outras nem o alerta. */
    @Test
    void sweep_aFailingComandaDoesNotStopTheOthers() {
        StaleComandaSweepService service = new StaleComandaSweepService(comandaUseCase, 12, 200);
        when(comandaUseCase.findStaleResolvableComandaIds(12, 200)).thenReturn(List.of(1L, 2L));
        when(comandaUseCase.resolveStaleComanda(1L)).thenThrow(new IllegalStateException("boom"));
        when(comandaUseCase.resolveStaleComanda(2L)).thenReturn(ComandaUseCase.StaleComandaOutcome.CANCELLED);

        assertThat(service.runOnce().cancelled()).isEqualTo(1);
        verify(comandaUseCase).alertStaleComandasWithConsumption(12, 200);
    }

    /**
     * A janela é configuração, não constante — um salão que vira a noite pode precisar de outra. Se
     * o scheduler ignorasse o valor injetado, a varredura alcançaria mesa em uso.
     */
    @Test
    void sweep_honoursANonDefaultWindow() {
        StaleComandaSweepService service = new StaleComandaSweepService(comandaUseCase, 24, 50);

        service.sweep();

        verify(comandaUseCase).findStaleResolvableComandaIds(eq(24), eq(50));
        verify(comandaUseCase).alertStaleComandasWithConsumption(eq(24), eq(50));
    }
}
