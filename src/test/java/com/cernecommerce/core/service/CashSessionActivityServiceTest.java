package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.CashSessionActivity;
import com.cernecommerce.core.domain.model.pdv.CashSessionActivityType;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pdv.CashSessionActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PDV-F047 — atividades do operador na sessão de caixa. */
@ExtendWith(MockitoExtension.class)
class CashSessionActivityServiceTest {

    @Mock CashRegisterRepository cashRegisterRepository;
    @Mock CashSessionActivityRepository activityRepository;

    private CashSessionActivityService service;

    @BeforeEach
    void setup() {
        service = new CashSessionActivityService(cashRegisterRepository, activityRepository);
    }

    private static CashRegisterSession session(CashRegisterSession.Status status) {
        boolean closed = status == CashRegisterSession.Status.CLOSED;
        return CashRegisterSession.of(1L, "caixa1", Instant.now(), BigDecimal.TEN, "LOJA-01",
                closed ? Instant.now() : null, closed ? "caixa1" : null, null,
                closed ? BigDecimal.TEN : null, null, status);
    }

    @Test
    void register_naPropriaSessaoAberta_grava() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(CashRegisterSession.Status.OPEN)));
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashSessionActivity result = service.register(1L, CashSessionActivityType.TROCAR_DINHEIRO,
                " Troquei 2 notas de 100 ", "caixa1", false);

        assertThat(result.type()).isEqualTo(CashSessionActivityType.TROCAR_DINHEIRO);
        assertThat(result.note()).isEqualTo("Troquei 2 notas de 100");
        assertThat(result.username()).isEqualTo("caixa1");
        verify(cashRegisterRepository).lockForShare(1L);
    }

    @Test
    void register_sessaoFechada_recusa() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(CashRegisterSession.Status.CLOSED)));

        assertThatThrownBy(() -> service.register(1L, CashSessionActivityType.VARRER_LOJA, null, "caixa1", false))
                .isInstanceOf(CashRegisterSessionClosedException.class);
        verify(activityRepository, never()).save(any());
    }

    @Test
    void register_sessaoDeOutroOperador_recusaSemCloseAny() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(CashRegisterSession.Status.OPEN)));

        assertThatThrownBy(() -> service.register(1L, CashSessionActivityType.VARRER_LOJA, null, "outro", false))
                .isInstanceOf(CashRegisterSessionNotOwnedException.class);
    }

    @Test
    void register_sessaoDeOutroOperador_aceitaComCloseAny() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(CashRegisterSession.Status.OPEN)));
        when(activityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashSessionActivity result = service.register(1L, CashSessionActivityType.REPOSICAO, null, "gerente", true);

        assertThat(result.username()).isEqualTo("gerente");
    }

    @Test
    void register_outroSemNota_recusaAntesDeTocarNaSessao() {
        assertThatThrownBy(() -> service.register(1L, CashSessionActivityType.OUTRO, "  ", "caixa1", false))
                .isInstanceOf(IllegalArgumentException.class);
        verify(cashRegisterRepository, never()).lockForShare(any());
    }

    @Test
    void register_notaAcimaDe280_recusa() {
        String longa = "x".repeat(281);

        assertThatThrownBy(() -> service.register(1L, CashSessionActivityType.OUTRO, longa, "caixa1", false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void list_sessaoInexistente_404() {
        when(cashRegisterRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(9L)).isInstanceOf(CashRegisterSessionNotFoundException.class);
    }

    @Test
    void list_delegaAoRepositorio() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(CashRegisterSession.Status.OPEN)));
        CashSessionActivity a = CashSessionActivity.of(5L, 1L, CashSessionActivityType.PASSAR_PANO, null, "caixa1",
                Instant.now());
        when(activityRepository.findBySessionId(1L)).thenReturn(List.of(a));

        assertThat(service.list(1L)).containsExactly(a);
    }
}
