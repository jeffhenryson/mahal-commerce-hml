package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.exception.pdv.OffSystemReceiptNotFoundException;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.OffSystemReceipt;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pdv.OffSystemReceiptRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Valor recebido por fora: quem pode lançar e apagar, com o caixa aberto ou fechado. */
@ExtendWith(MockitoExtension.class)
class OffSystemReceiptServiceTest {

    @Mock CashRegisterRepository cashRegisterRepository;
    @Mock OffSystemReceiptRepository receiptRepository;

    private OffSystemReceiptService service;

    @BeforeEach
    void setup() {
        service = new OffSystemReceiptService(cashRegisterRepository, receiptRepository);
    }

    private static CashRegisterSession session(CashRegisterSession.Status status) {
        boolean closed = status == CashRegisterSession.Status.CLOSED;
        return CashRegisterSession.of(1L, "caixa1", Instant.now(), BigDecimal.TEN, "LOJA-01",
                closed ? Instant.now() : null, closed ? "caixa1" : null, null,
                closed ? BigDecimal.TEN : null, null, status);
    }

    private static OffSystemReceipt saved(long id) {
        return new OffSystemReceipt(id, 1L, new BigDecimal("35.00"), PaymentMethod.PIX, PaymentProvider.INFINITYPAY,
                null, "caixa1", Instant.now());
    }

    private OffSystemReceipt register(String username, boolean canAny) {
        return service.register(1L, new BigDecimal("35.00"), PaymentMethod.PIX, PaymentProvider.INFINITYPAY,
                "Pix na chave do dono", username, canAny);
    }

    @Test
    void register_donoComCaixaAberto_grava() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(CashRegisterSession.Status.OPEN)));
        when(receiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OffSystemReceipt result = register("caixa1", false);

        assertThat(result.amount()).isEqualByComparingTo("35.00");
        assertThat(result.provider()).isEqualTo(PaymentProvider.INFINITYPAY);
        assertThat(result.username()).isEqualTo("caixa1");
        verify(cashRegisterRepository).lockForShare(1L);
    }

    @Test
    void register_caixaFechadoSemCloseAny_recusa() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(CashRegisterSession.Status.CLOSED)));

        assertThatThrownBy(() -> register("caixa1", false)).isInstanceOf(CashRegisterSessionClosedException.class);
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void register_caixaFechadoComCloseAny_grava() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(CashRegisterSession.Status.CLOSED)));
        when(receiptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(register("gerente", true).username()).isEqualTo("gerente");
    }

    @Test
    void register_caixaDeOutroOperadorSemCloseAny_recusa() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(CashRegisterSession.Status.OPEN)));

        assertThatThrownBy(() -> register("outro", false)).isInstanceOf(CashRegisterSessionNotOwnedException.class);
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void register_corpoInvalido_recusaAntesDeTocarNaSessao() {
        assertThatThrownBy(() -> service.register(1L, BigDecimal.TEN, PaymentMethod.DINHEIRO, null, null,
                "caixa1", false)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(cashRegisterRepository, receiptRepository);
    }

    @Test
    void list_sessaoInexistente_404() {
        when(cashRegisterRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(9L)).isInstanceOf(CashRegisterSessionNotFoundException.class);
    }

    @Test
    void list_devolveOsDoRepositorio() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(CashRegisterSession.Status.CLOSED)));
        when(receiptRepository.findBySessionId(1L)).thenReturn(List.of(saved(2), saved(1)));

        assertThat(service.list(1L)).extracting(OffSystemReceipt::id).containsExactly(2L, 1L);
    }

    @Test
    void delete_donoComCaixaAberto_apagaLogicamente() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(CashRegisterSession.Status.OPEN)));
        when(receiptRepository.findActive(1L, 5L)).thenReturn(Optional.of(saved(5)));

        OffSystemReceipt result = service.delete(1L, 5L, "caixa1", false);

        assertThat(result.id()).isEqualTo(5L);
        verify(receiptRepository).markDeleted(eq(5L), eq("caixa1"), any(Instant.class));
    }

    @Test
    void delete_inexistenteOuJaApagado_404() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(CashRegisterSession.Status.OPEN)));
        when(receiptRepository.findActive(1L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 5L, "caixa1", false))
                .isInstanceOf(OffSystemReceiptNotFoundException.class);
        verify(receiptRepository, never()).markDeleted(anyLong(), anyString(), any());
    }

    @Test
    void delete_caixaFechadoSemCloseAny_recusa() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(session(CashRegisterSession.Status.CLOSED)));

        assertThatThrownBy(() -> service.delete(1L, 5L, "caixa1", false))
                .isInstanceOf(CashRegisterSessionClosedException.class);
        verify(receiptRepository, never()).markDeleted(anyLong(), anyString(), any());
    }
}
