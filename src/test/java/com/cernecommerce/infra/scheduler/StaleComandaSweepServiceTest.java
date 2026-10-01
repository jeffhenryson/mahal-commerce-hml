package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.in.ComandaUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaleComandaSweepServiceTest {

    @Mock
    ComandaUseCase comandaUseCase;

    @Test
    void sweep_delegatesToUseCase_withConfiguredWindowAndBatchSize() {
        StaleComandaSweepService service = new StaleComandaSweepService(comandaUseCase, 12, 200);
        when(comandaUseCase.sweepStaleComandas(12, 200))
                .thenReturn(new ComandaUseCase.StaleComandaSweepResult(2, 1, 1));

        service.sweep();

        verify(comandaUseCase).sweepStaleComandas(eq(12), eq(200));
    }

    /**
     * A janela é configuração, não constante — um salão que vira a noite pode precisar de outra. Se
     * o scheduler ignorasse o valor injetado, a varredura alcançaria mesa em uso.
     */
    @Test
    void sweep_honoursANonDefaultWindow() {
        StaleComandaSweepService service = new StaleComandaSweepService(comandaUseCase, 24, 50);
        when(comandaUseCase.sweepStaleComandas(24, 50))
                .thenReturn(new ComandaUseCase.StaleComandaSweepResult(0, 0, 0));

        service.sweep();

        verify(comandaUseCase).sweepStaleComandas(eq(24), eq(50));
    }
}
