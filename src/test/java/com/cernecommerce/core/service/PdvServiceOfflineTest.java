package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.SessionHasPendingOfflineSalesException;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleRegistration;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.CashMovementRepository;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pdv.OfflineSaleRejectionRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PDV-F043 — o lado do {@link PdvService} da venda offline: a chave de venda que torna o reenvio
 * seguro, e o caixa que não fecha com venda offline esperando revisão.
 */
@ExtendWith(MockitoExtension.class)
class PdvServiceOfflineTest {

    private static final Pricing CARVAO = Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00"));
    private static final String CHAVE = "c0ffee00-0000-4000-8000-000000000001";

    @Mock CashRegisterRepository cashRegisterRepository;
    @Mock CashMovementRepository cashMovementRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock ComandaRepository comandaRepository;
    @Mock OfflineSaleRejectionRepository offlineRejections;
    ReceivableUseCase receivableUseCase = mock(ReceivableUseCase.class, invocation ->
            invocation.getMethod().getReturnType() == BigDecimal.class
                    ? BigDecimal.ZERO
                    : RETURNS_DEFAULTS.answer(invocation));

    PdvService pdvService;

    @BeforeEach
    void setUp() {
        pdvService = new PdvService(cashRegisterRepository, cashMovementRepository, orderRepository,
                orderPaymentRepository, estoqueUseCase, cashbackUseCase, comandaRepository, BigDecimal.TEN,
                Clock.systemUTC(), receivableUseCase, offlineRejections);
    }

    private static CashRegisterSession openSession() {
        return CashRegisterSession.of(1L, "caixa1", Instant.now().minusSeconds(3600), BigDecimal.TEN, "LOJA-01",
                null, null, null, null, null, CashRegisterSession.Status.OPEN);
    }

    private static List<SaleItemCommand> doisCarvoes() {
        return List.of(new SaleItemCommand("CARV-001", new BigDecimal("2.000"), null));
    }

    private static List<PaymentCommand> dinheiro(String amount) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(amount), null));
    }

    private void givenSaleCanBeSaved() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.nextOrderNumber()).thenReturn("000001000");
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order arg = inv.getArgument(0);
            return Order.of(100L, arg.orderNumber(), arg.channel(), arg.status(), arg.customerId(), arg.sessionId(),
                    arg.warehouseCode(), arg.items(), arg.grossAmount(), arg.discountAmount(), arg.cashbackRedeemed(),
                    arg.netAmount(), arg.changeAmount(), arg.cancelReason(), arg.createdAt(), arg.paidAt(),
                    arg.concludedAt(), arg.cancelledAt(), arg.refundedAt(), arg.reservedAt(), arg.separatedAt(),
                    arg.shippedAt(), arg.deliveredAt(), arg.version(), arg.comandaId(), arg.tableLabel(),
                    arg.serviceFeeAmount(), arg.delivery());
        });
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo("Carvão", CARVAO));
    }

    /**
     * <b>O ponto da idempotência.</b> A mesma venda chegando de novo — timeout seguido de reenvio, ou
     * a fila offline repetida — devolve o pedido já gravado: nem estoque, nem pedido, nem pagamento.
     */
    @Test
    void registerSaleIdempotent_comChaveJaGravada_devolveOPedidoSemTocarEmNada() {
        Order existente = Order.openBalcao(1L, "LOJA-01", null, List.of(
                        OrderItem.fromCatalog("CARV-001", new BigDecimal("2.000"), CARVAO, null, "Carvão")))
                .concluded("000000055", BigDecimal.ZERO, Instant.now());
        when(orderRepository.findIdByClientSaleId(CHAVE)).thenReturn(Optional.of(55L));
        when(orderRepository.findById(55L)).thenReturn(Optional.of(existente));

        SaleRegistration result = pdvService.registerSaleIdempotent(1L, null, doisCarvoes(), dinheiro("44.00"),
                "caixa1", false, null, CHAVE, null);

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().orderNumber()).isEqualTo("000000055");
        verifyNoInteractions(estoqueUseCase);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderPaymentRepository);
    }

    /** Venda nova com chave: grava a chave e a hora do balcão no pedido. */
    @Test
    void registerSaleIdempotent_vendaNova_carimbaAChaveEAHoraDoBalcao() {
        givenSaleCanBeSaved();
        when(orderRepository.findIdByClientSaleId(CHAVE)).thenReturn(Optional.empty());
        Instant noBalcao = Instant.now().minusSeconds(600);

        SaleRegistration result = pdvService.registerSaleIdempotent(1L, null, doisCarvoes(), dinheiro("44.00"),
                "caixa1", false, null, CHAVE, noBalcao);

        assertThat(result.replayed()).isFalse();
        verify(orderRepository).stampClientSale(100L, CHAVE, noBalcao);
    }

    /** Sem chave é a venda de sempre: nenhuma consulta nem carimbo a mais. */
    @Test
    void registerSale_semChave_naoConsultaNemCarimba() {
        givenSaleCanBeSaved();

        pdvService.registerSale(1L, null, doisCarvoes(), dinheiro("44.00"), "caixa1");

        verify(orderRepository, never()).findIdByClientSaleId(anyString());
        verify(orderRepository, never()).stampClientSale(any(), any(), any());
    }

    /**
     * Venda offline recusada esperando revisão barra o fechamento: fechado o caixa, ela não teria mais
     * onde entrar, e o dinheiro dela já está na gaveta.
     */
    @Test
    void closeSession_comVendaOfflinePendente_eRecusado() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(offlineRejections.findPendingIdsBySessionId(1L)).thenReturn(List.of(7L, 8L));

        assertThatThrownBy(() -> pdvService.closeSession(1L, BigDecimal.TEN, "caixa1"))
                .isInstanceOf(SessionHasPendingOfflineSalesException.class)
                .satisfies(e -> assertThat(((SessionHasPendingOfflineSalesException) e).getRejectionIds())
                        .containsExactly(7L, 8L));
        verify(cashRegisterRepository, never()).save(any());
    }
}
