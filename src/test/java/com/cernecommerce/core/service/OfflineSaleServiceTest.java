package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.exception.pdv.OfflinePaymentNotAllowedException;
import com.cernecommerce.core.domain.exception.pdv.OfflineSoldAtOutOfWindowException;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.OfflineRejectionResolution;
import com.cernecommerce.core.domain.model.pdv.OfflineSaleItem;
import com.cernecommerce.core.domain.model.pdv.OfflineSalePayment;
import com.cernecommerce.core.domain.model.pdv.OfflineSaleRejection;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.OfflineSaleUseCase.OfflineSaleCommand;
import com.cernecommerce.core.ports.in.OfflineSaleUseCase.SyncResult;
import com.cernecommerce.core.ports.in.OfflineSaleUseCase.SyncStatus;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleRegistration;
import com.cernecommerce.core.ports.out.event.AuditEventPublisherPort;
import com.cernecommerce.core.ports.out.pdv.OfflineSaleRejectionRepository;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** PDV-F043 — a sincronização da fila offline e a revisão das recusadas. */
@ExtendWith(MockitoExtension.class)
class OfflineSaleServiceTest {

    @Mock PdvUseCase pdvUseCase;
    @Mock OfflineSaleRejectionRepository rejections;
    @Mock AuditEventPublisherPort auditEvents;

    OfflineSaleService service;

    private static final Instant ABERTURA = Instant.now().minusSeconds(4 * 3600);

    @BeforeEach
    void setUp() {
        service = new OfflineSaleService(pdvUseCase, rejections, auditEvents);
    }

    private static CashRegisterSession caixa(CashRegisterSession.Status status) {
        // Fechado exige os campos do fechamento — o domínio recusa CLOSED sem closedAt.
        return status == CashRegisterSession.Status.CLOSED
                ? CashRegisterSession.of(1L, "caixa1", ABERTURA, BigDecimal.TEN, "LOJA-01", Instant.now(), "caixa1",
                        BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, status)
                : CashRegisterSession.of(1L, "caixa1", ABERTURA, BigDecimal.TEN, "LOJA-01",
                        null, null, null, null, null, status);
    }

    private static OfflineSaleCommand venda(String chave, PaymentMethod metodo) {
        return new OfflineSaleCommand(chave, ABERTURA.plusSeconds(600), null,
                List.of(new OfflineSaleItem("LM-AZUL-MACO", BigDecimal.ONE, null, null)),
                List.of(new OfflineSalePayment(metodo, new BigDecimal("12.00"), null)));
    }

    private static Order pedido(long id) {
        return mock(Order.class, inv -> inv.getMethod().getName().equals("id") ? id : RETURNS_DEFAULTS.answer(inv));
    }

    private void givenCaixaAberto() {
        when(pdvUseCase.getSession(1L)).thenReturn(caixa(CashRegisterSession.Status.OPEN));
    }

    private void givenSaveAssignsId() {
        when(rejections.save(any())).thenAnswer(inv -> {
            OfflineSaleRejection r = inv.getArgument(0);
            return r.id() == null ? r.withId(70L) : r;
        });
    }

    /**
     * <b>O lote misto.</b> Cada venda roda sozinha: a que entra entra, a repetida volta como
     * duplicada, e a que falta estoque fica guardada para revisão — sem derrubar as outras.
     */
    @Test
    void sync_loteMisto_cadaVendaTemOSeuDestino() {
        givenCaixaAberto();
        givenSaveAssignsId();
        when(rejections.findByClientSaleId(any())).thenReturn(Optional.empty());
        when(pdvUseCase.registerSaleIdempotent(eq(1L), any(), any(), any(), eq("caixa1"), anyBoolean(), any(),
                eq("k-nova"), any())).thenReturn(new SaleRegistration(pedido(10L), false));
        when(pdvUseCase.registerSaleIdempotent(eq(1L), any(), any(), any(), eq("caixa1"), anyBoolean(), any(),
                eq("k-repetida"), any())).thenReturn(new SaleRegistration(pedido(11L), true));
        when(pdvUseCase.registerSaleIdempotent(eq(1L), any(), any(), any(), eq("caixa1"), anyBoolean(), any(),
                eq("k-sem-estoque"), any()))
                .thenThrow(new InsufficientStockException("LM-AZUL-MACO", 1L, BigDecimal.ZERO, BigDecimal.ONE));
        when(pdvUseCase.findOrderIdByClientSaleId("k-sem-estoque")).thenReturn(Optional.empty());

        List<SyncResult> results = service.sync(1L, List.of(venda("k-nova", PaymentMethod.DINHEIRO),
                venda("k-repetida", PaymentMethod.DEBITO), venda("k-sem-estoque", PaymentMethod.CREDITO)), "caixa1");

        assertThat(results).extracting(SyncResult::status)
                .containsExactly(SyncStatus.SYNCED, SyncStatus.DUPLICATE, SyncStatus.REJECTED);
        assertThat(results.get(0).orderId()).isEqualTo(10L);
        assertThat(results.get(2).errorCode()).isEqualTo("INSUFFICIENT_STOCK");
        assertThat(results.get(2).rejectionId()).isEqualTo(70L);
        verify(rejections).save(argThat(r -> r.clientSaleId().equals("k-sem-estoque") && r.isPending()
                && r.items().get(0).sku().equals("LM-AZUL-MACO")));
        verify(auditEvents).publish(argThat(e -> e.type() == AuditEvent.EventType.OFFLINE_SALE_REJECTED));
        verify(auditEvents).publish(argThat(e -> e.type() == AuditEvent.EventType.OFFLINE_SALES_SYNCED));
    }

    /** Venda que já foi recusada antes e chega de novo: devolve a mesma recusa, sem tentar de novo. */
    @Test
    void sync_vendaJaRecusada_devolveAMesmaRecusa() {
        givenCaixaAberto();
        OfflineSaleRejection anterior = OfflineSaleRejection.create(1L, "k-sem-estoque", ABERTURA, null, List.of(),
                List.of(), "INSUFFICIENT_STOCK", "sem saldo", "caixa1", ABERTURA).withId(70L);
        when(rejections.findByClientSaleId("k-sem-estoque")).thenReturn(Optional.of(anterior));

        List<SyncResult> results = service.sync(1L, List.of(venda("k-sem-estoque", PaymentMethod.DINHEIRO)), "caixa1");

        assertThat(results).singleElement().satisfies(r -> {
            assertThat(r.status()).isEqualTo(SyncStatus.REJECTED);
            assertThat(r.rejectionId()).isEqualTo(70L);
        });
        verify(pdvUseCase, never()).registerSaleIdempotent(any(), any(), any(), any(), any(), anyBoolean(), any(),
                any(), any());
    }

    /** PIX e marcado não existem sem rede: o lote inteiro é recusado antes de qualquer venda. */
    @Test
    void sync_comPix_recusaOLoteInteiro() {
        givenCaixaAberto();

        assertThatThrownBy(() -> service.sync(1L, List.of(venda("k-1", PaymentMethod.DINHEIRO),
                venda("k-2", PaymentMethod.PIX)), "caixa1"))
                .isInstanceOf(OfflinePaymentNotAllowedException.class);
        verify(pdvUseCase, never()).registerSaleIdempotent(any(), any(), any(), any(), any(), anyBoolean(), any(),
                any(), any());
    }

    @Test
    void sync_horarioAntesDaAberturaDoCaixa_eRecusado() {
        givenCaixaAberto();
        OfflineSaleCommand antes = new OfflineSaleCommand("k-1", ABERTURA.minusSeconds(60), null,
                venda("x", PaymentMethod.DINHEIRO).items(), venda("x", PaymentMethod.DINHEIRO).payments());

        assertThatThrownBy(() -> service.sync(1L, List.of(antes), "caixa1"))
                .isInstanceOf(OfflineSoldAtOutOfWindowException.class);
    }

    /** Caixa fechado não recebe sincronização: o frontend bloqueia o fechamento com fila pendente. */
    @Test
    void sync_caixaFechado_eRecusado() {
        when(pdvUseCase.getSession(1L)).thenReturn(caixa(CashRegisterSession.Status.CLOSED));

        assertThatThrownBy(() -> service.sync(1L, List.of(venda("k-1", PaymentMethod.DINHEIRO)), "caixa1"))
                .isInstanceOf(CashRegisterSessionClosedException.class);
    }

    @Test
    void sync_caixaDeOutroOperador_eRecusado() {
        givenCaixaAberto();

        assertThatThrownBy(() -> service.sync(1L, List.of(venda("k-1", PaymentMethod.DINHEIRO)), "outro"))
                .isInstanceOf(CashRegisterSessionNotOwnedException.class);
    }

    /** Reenvio depois do acerto, em nome do operador do caixa: vira pedido, e o revisor fica registrado. */
    @Test
    void retry_comSucesso_resolveComOPedido() {
        OfflineSaleRejection pendente = OfflineSaleRejection.create(1L, "k-1", ABERTURA, null,
                venda("k-1", PaymentMethod.DINHEIRO).items(), venda("k-1", PaymentMethod.DINHEIRO).payments(),
                "INSUFFICIENT_STOCK", "sem saldo", "caixa1", ABERTURA).withId(70L);
        when(rejections.findById(70L)).thenReturn(Optional.of(pendente));
        givenCaixaAberto();
        when(pdvUseCase.registerSaleIdempotent(eq(1L), any(), any(), any(), eq("caixa1"), anyBoolean(), any(),
                eq("k-1"), any())).thenReturn(new SaleRegistration(pedido(12L), false));
        when(rejections.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OfflineSaleRejection resolvida = service.retry(70L, "gerente");

        assertThat(resolvida.resolution()).isEqualTo(OfflineRejectionResolution.RETRIED);
        assertThat(resolvida.orderId()).isEqualTo(12L);
        assertThat(resolvida.resolvedBy()).isEqualTo("gerente");
    }

    /** Ainda sem estoque: continua pendente, com o motivo atualizado, e o caixa segue sem fechar. */
    @Test
    void retry_falhandoDeNovo_continuaPendente() {
        OfflineSaleRejection pendente = OfflineSaleRejection.create(1L, "k-1", ABERTURA, null,
                venda("k-1", PaymentMethod.DINHEIRO).items(), venda("k-1", PaymentMethod.DINHEIRO).payments(),
                "INSUFFICIENT_STOCK", "sem saldo", "caixa1", ABERTURA).withId(70L);
        when(rejections.findById(70L)).thenReturn(Optional.of(pendente));
        givenCaixaAberto();
        when(pdvUseCase.registerSaleIdempotent(any(), any(), any(), any(), any(), anyBoolean(), any(), any(), any()))
                .thenThrow(new InsufficientStockException("LM-AZUL-MACO", 1L, BigDecimal.ZERO, BigDecimal.ONE));
        when(pdvUseCase.findOrderIdByClientSaleId("k-1")).thenReturn(Optional.empty());
        when(rejections.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OfflineSaleRejection ainda = service.retry(70L, "gerente");

        assertThat(ainda.isPending()).isTrue();
        assertThat(ainda.errorCode()).isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    void discard_resolveComMotivo() {
        OfflineSaleRejection pendente = OfflineSaleRejection.create(1L, "k-1", ABERTURA, null, List.of(), List.of(),
                "INSUFFICIENT_STOCK", "sem saldo", "caixa1", ABERTURA).withId(70L);
        when(rejections.findById(70L)).thenReturn(Optional.of(pendente));
        when(rejections.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OfflineSaleRejection descartada = service.discard(70L, "Cliente devolveu", "gerente");

        assertThat(descartada.resolution()).isEqualTo(OfflineRejectionResolution.DISCARDED);
        verify(auditEvents).publish(argThat(e -> e.type() == AuditEvent.EventType.OFFLINE_SALE_DISCARDED));
    }
}
